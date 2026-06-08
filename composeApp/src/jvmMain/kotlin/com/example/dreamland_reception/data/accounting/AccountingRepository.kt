package com.example.dreamland_reception.data.accounting

import com.example.dreamland_reception.data.AppContext
import com.example.dreamland_reception.data.model.Bill
import com.example.dreamland_reception.data.model.Expense
import com.example.dreamland_reception.data.model.Order
import com.example.dreamland_reception.data.model.Stay
import com.example.dreamland_reception.data.model.Transfer
import com.example.dreamland_reception.data.repository.FirestoreStayRepository
import com.example.dreamland_reception.util.normalizePhoneE164
import java.math.BigDecimal
import java.math.RoundingMode
import java.text.SimpleDateFormat
import java.time.LocalDate
import java.util.Date
import java.util.Locale

/**
 * Orchestrates the complete double-entry accounting settlement for a confirmed hotel bill.
 *
 * ## Advance liability flow
 *
 * When [Bill.advancePayment] > 0, the advance is treated as a **liability** (not a payment):
 *
 *   1. ADVANCE  (via POST /api/v1/transactions, postingType = ADVANCE)
 *      DR  Cash                        advanceAmount   ← cash was received at check-in
 *      CR  Advance Liability           advanceAmount   ← recorded as liability
 *
 *   2. SALE  (via POST /api/v1/sales, at checkout)
 *      DR  Accounts Receivable (AR)    total
 *      CR  Sales Revenue               subtotal
 *      CR  GST Payable                 tax              (when taxRate > 0)
 *
 *   3. ADVANCE APPLIED  (via POST /api/v1/payments, paymentAccountId = Advance Liability)
 *      DR  Advance Liability           advanceAmount   ← liability cleared
 *      CR  AR (customer sub-account)   advanceAmount   ← AR reduced by advance
 *      Routed through /payments (not a raw journal) so it records an
 *      InvoicePayment and the invoice can reach PAID.
 *
 *   4. PAYMENT  (via POST /api/v1/payments, one per checkout PaymentTransaction)
 *      DR  Cash | Bank                 txn.amount
 *      CR  AR (customer sub-account)   txn.amount
 *
 * The advance payment + all checkout PAYMENTs equal the invoice total exactly,
 * enforced by a penny adjustment on the last checkout payment.
 *
 * ## Advance lifecycle (deposit in once, adjusted once)
 * The advance is posted to the ledger AT CHECK-IN (`checkin_<stayId>_advance`) and is
 * NEVER reversed. At checkout, settle posts only the DIFFERENCE if the recorded advance
 * grew (`stay_<stayId>_advance_topup`), then applies it to the invoice
 * (`stay_<stayId>_advance_applied`). Any surplus held beyond the bill's advance stays as
 * customer credit.
 *
 * ## Idempotency
 * Every posting uses a deterministic sourceId:
 *   checkin_<stayId>_advance       ADVANCE (posted at check-in; never reversed)
 *   stay_<stayId>_advance_topup    ADVANCE (only the extra collected vs. check-in)
 *   stay_<stayId>_sale             SALE
 *   stay_<stayId>_advance_applied  ADVANCE_APPLIED
 *   stay_<stayId>_txn_<txnId>      PAYMENT (one per PaymentTransaction)
 */
/** A vendor's current Humble Ledger balance. [payable] > 0 = we owe them. */
data class VendorBalanceInfo(val payable: Double, val balanceType: String)

/** A guest's current Humble Ledger balance. [balance] > 0 = they owe the hotel; < 0 = credit. */
data class CustomerBalanceInfo(val balance: Double, val balanceType: String)

/** The hotel's current liquid balances from Humble Ledger. */
data class CashBankBalance(val cash: Double, val bank: Double) {
    val total: Double get() = cash + bank
}

object AccountingRepository {

    private val client = AccountingApiClient
    private val dateFmt = SimpleDateFormat("dd MMM", Locale.getDefault())

    /** In-memory cache for the "Guest Advances Received" liability account ID. */
    @Volatile
    private var advanceLiabilityAccountId: String? = null

    // Canonical Humble Ledger advance account. NOTE: the old name
    // "Guest Advances Received" is a deprecated alias that the ledger's
    // chart-of-accounts standardizer archives/deletes — using it would break
    // future advance postings. Always use the canonical name.
    private const val ADVANCE_ACCOUNT_NAME = "Advance Liability"
    private const val APP_ID = "dreamland"

    // Expense account that vendor food purchases are booked against.
    private const val FOOD_EXPENSE_ACCOUNT_NAME = "Food & Beverage Cost"

    /** In-memory cache for the food expense account ID. */
    @Volatile
    private var foodExpenseAccountId: String? = null

    // Default expense account for general hotel expenses (no per-expense category).
    private const val GENERAL_EXPENSE_ACCOUNT_NAME = "General Expense"

    @Volatile
    private var generalExpenseAccountId: String? = null

    // Account that booking-cancellation refunds are booked against (the debit side; the
    // online advance was never recorded in this ledger, so a dedicated account is used).
    private const val BOOKING_REFUNDS_ACCOUNT_NAME = "Booking Refunds"

    @Volatile
    private var bookingRefundsAccountId: String? = null

    /** Outcome of a successful settlement — carries the ledger invoice identity. */
    data class SettleResult(
        val invoiceId: String,
        val invoiceNumber: String,
    )

    // ─────────────────────────────────────────────────────────────────────────

    suspend fun settle(bill: Bill, guestPhone: String, guestUid: String = ""): Result<SettleResult> = runCatching {

        log("─────────────────────────────────────────")
        log("settle() START — stayId=${bill.stayId}, guest='${bill.guestName}'")
        log("Raw bill — subtotal=${bill.subtotal}, taxPct=${bill.taxPercentage}, " +
            "taxEnabled=${bill.taxEnabled}, advance=${bill.advancePayment}, " +
            "txns=${bill.transactions.size} ${bill.transactions.map { "${it.method}:${it.amount}" }}")

        if (!AccountingConfig.isConfigured()) {
            log("SKIP — not configured. Set email + password in ~/.dreamland/accounting.json")
            // Sentinel: blank invoiceId signals "skipped, not synced" so the caller
            // neither marks the bill synced nor surfaces a false failure.
            return@runCatching SettleResult(invoiceId = "", invoiceNumber = "")
        }

        // ── 1. Rounded monetary values — single source of truth ───────────────
        // The ledger records REVENUE NET OF DISCOUNT and the EXACT tax amount.
        //   netRevenue = subtotal − discount   → CR Sales Revenue
        //   tax        = bill.taxAmount        → CR GST Payable (sent verbatim)
        //   total      = netRevenue + tax      → DR Accounts Receivable
        // This makes the ledger total equal the bill total the guest actually pays,
        // so payments reconcile exactly (no discount drift, no rate-rounding drift).
        val subtotal       = roundAmount(bill.subtotal)
        val discount       = roundAmount(bill.discountAmount)
        val tax            = roundAmount(if (bill.taxEnabled) bill.taxAmount else 0.0)
        val netRevenue     = roundAmount((subtotal - discount).coerceAtLeast(0.0))
        // Effective rate is display metadata only — the server uses `tax` verbatim.
        val taxRate        = if (bill.taxEnabled && netRevenue > 0.0) Math.round(tax / netRevenue * 10000.0) / 10000.0 else 0.0
        val total          = roundAmount(netRevenue + tax)
        val advanceRounded = roundAmount(bill.advancePayment)
        val hasAdvance     = advanceRounded > 0.01

        log("FINAL VALUES → subtotal=$subtotal, discount=$discount, netRevenue=$netRevenue, tax=$tax, total=$total, taxRate=$taxRate")
        log("Advance → $advanceRounded (hasAdvance=$hasAdvance)")

        // ── 2. Authenticate ───────────────────────────────────────────────────
        val token = client.ensureValidToken()
        log("Token OK (first 20): ${token.take(20)}…")

        val today       = LocalDate.now().toString()
        val checkInDate = bill.checkInDate
            ?.let { runCatching { java.text.SimpleDateFormat("yyyy-MM-dd").format(it) }.getOrNull() }
            ?: today

        // ── 3. Resolve customer (need accountId for ADVANCE_APPLIED) ──────────
        log("Resolving customer '${bill.guestName}' (uid='$guestUid')")
        val customer       = resolveCustomer(token, bill.guestName, guestPhone, guestUid, bill.guestGstin)
        val customerId     = customer.id
        val customerArId   = customer.accountId   // AR sub-account for ADVANCE_APPLIED credit leg
        log("Customer — id=$customerId, arAccountId=$customerArId")

        // ── 4. Ensure liability account + measure advance already held from check-in ──
        // The advance posted AT CHECK-IN stays in the ledger — it is NOT reversed (that's
        // not how a real deposit works). Here we only measure how much is already held so
        // step 5 can post the DIFFERENCE if the recorded advance changed, and step 7
        // applies it to the invoice. Deposit comes in once, is adjusted once.
        val alreadyHeldAtCheckIn = resolveCheckInAdvanceHeld(bill)
        val advanceLiabilityId: String? =
            if (hasAdvance || alreadyHeldAtCheckIn > 0.01) {
                val id = ensureAdvanceLiabilityAccount(token)
                log("Advance liability account id=$id (alreadyHeldAtCheckIn=$alreadyHeldAtCheckIn)")
                id
            } else null

        // ── 5. ADVANCE TOP-UP — only the part not already held from check-in ──────
        // The check-in advance already sits in the liability, so we post a fresh cash-in
        // only for the DIFFERENCE: when more was collected than was posted at check-in,
        // or when nothing was posted there (legacy stays, or an advance entered only at
        // checkout). A *reduction* is never removed here — when step 7 applies only the
        // bill's advance, any surplus simply stays as the guest's credit.
        if (hasAdvance && advanceLiabilityId != null) {
            val topUp = roundAmount(advanceRounded - alreadyHeldAtCheckIn)
            if (topUp > 0.01) {
                log("Posting ADVANCE TOP-UP — amount=$topUp (bill=$advanceRounded, held=$alreadyHeldAtCheckIn), " +
                    "date=$checkInDate, sourceId=stay_${bill.stayId}_advance_topup")
                client.postRawTransaction(
                    token = token,
                    req   = RawTransactionRequest(
                        appId       = APP_ID,
                        sourceId    = "stay_${bill.stayId}_advance_topup",
                        postingType = "ADVANCE",
                        description = stampManager("Advance received — Room ${bill.roomNumber} (${bill.guestName})"),
                        date        = checkInDate,
                        entries     = listOf(
                            RawEntryInput(account = if (bill.advancePaymentMethod == "BANK") "Bank" else "Cash", type = "DEBIT", amount = topUp),
                            RawEntryInput(accountId = advanceLiabilityId, type = "CREDIT", amount = topUp),
                        ),
                    ),
                )
                log("ADVANCE TOP-UP posted OK")
            } else {
                log("No advance top-up needed (bill=$advanceRounded, held=$alreadyHeldAtCheckIn)")
            }
        }

        // ── 6. SALE — DR AR / CR Revenue (+ CR GST Payable) ──────────────────
        val saleDescription = buildSaleDescription(bill)
        log("Posting SALE — subtotal=$subtotal, taxRate=$taxRate, " +
            "sourceId=stay_${bill.stayId}_sale, desc='$saleDescription'")

        val saleResponse = client.postSale(
            token = token,
            req   = PostSaleRequest(
                customerId  = customerId,
                amount      = netRevenue,
                taxRate     = taxRate,
                taxAmount   = tax,
                description = stampManager(saleDescription),
                date        = today,
                appId       = APP_ID,
                sourceId    = "stay_${bill.stayId}_sale",
            ),
        )
        val invoiceId    = saleResponse.invoice.id
        val invoiceTotal = roundAmount(saleResponse.invoice.total.toDoubleOrNull() ?: total)
        log("SALE posted — invoiceId=$invoiceId, invoiceNo=${saleResponse.invoice.invoiceNumber}, " +
            "invoiceTotal=$invoiceTotal (our computed=$total)")

        // How much of THIS invoice still needs settling. The advance, then each
        // checkout tender, is applied against this; anything beyond it is posted
        // as a payment-on-account (no invoiceId) that reduces the customer's
        // overall AR — i.e. it clears a balance carried over from an earlier stay,
        // or leaves a credit if the guest overpaid. This is what lets a guest pay
        // more than this bill (to also settle older dues) without the server
        // rejecting it as an invoice overpayment.
        var invoiceRemaining = invoiceTotal

        // ── 7. ADVANCE APPLIED — settle the advance against the invoice ───────
        // Posted through the payments endpoint with paymentAccountId = Advance
        // Liability. The ledger effect is a raw ADVANCE_APPLIED journal (DR Advance
        // Liability / CR customer AR), but routing it through /payments also records
        // an InvoicePayment so the invoice can reach PAID. The part exceeding the
        // bill (advance > total) becomes customer credit. Idempotent on the sourceId.
        if (hasAdvance && advanceLiabilityId != null) {
            val src       = "stay_${bill.stayId}_advance_applied"
            val toInvoice = roundAmount(minOf(advanceRounded, invoiceRemaining).coerceAtLeast(0.0))
            val toAccount = roundAmount(advanceRounded - toInvoice)
            if (toInvoice > 0.0) {
                log("Applying advance to invoice — amount=$toInvoice, invoiceId=$invoiceId, sourceId=$src")
                client.postPayment(
                    token = token,
                    req   = PostPaymentRequest(
                        customerId       = customerId,
                        amount           = toInvoice,
                        method           = "CASH",            // ignored: paymentAccountId overrides it
                        paymentAccountId = advanceLiabilityId,
                        invoiceId        = invoiceId,
                        description      = stampManager("Advance applied — invoice ${saleResponse.invoice.invoiceNumber}"),
                        date             = today,
                        appId            = APP_ID,
                        sourceId         = src,
                    ),
                )
                invoiceRemaining = roundAmount(invoiceRemaining - toInvoice)
            }
            if (toAccount > 0.0) {
                // Advance exceeds this bill — the surplus reduces overall AR (credit).
                log("Advance surplus to account — amount=$toAccount, sourceId=${src}_acct")
                client.postPayment(
                    token = token,
                    req   = PostPaymentRequest(
                        customerId       = customerId,
                        amount           = toAccount,
                        method           = "CASH",
                        paymentAccountId = advanceLiabilityId,
                        invoiceId        = null,
                        description      = stampManager("Advance surplus (credit) — ${saleResponse.invoice.invoiceNumber}"),
                        date             = today,
                        appId            = APP_ID,
                        sourceId         = "${src}_acct",
                    ),
                )
            }
            log("Advance applied OK")
        }

        // ── 8. CHECKOUT PAYMENTS — settle the invoice first, then clear prior dues ──
        // Each tender is split: the part that fits the invoice's remaining is posted
        // against it (so it can reach PAID); any overflow is posted with no invoiceId,
        // reducing the customer's overall AR (clears a carried-over balance, or leaves
        // a credit). Deterministic sourceIds keep both legs idempotent on retry.
        log("Checkout: invoiceRemaining=$invoiceRemaining, tenders=" +
            bill.transactions.joinToString { "${it.method}:${roundAmount(it.amount)}" })

        bill.transactions.forEach { txn ->
            val amount = roundAmount(txn.amount)
            if (amount <= 0.0) return@forEach
            val method    = if (txn.method == "CASH") "CASH" else "BANK"
            val toInvoice = roundAmount(minOf(amount, invoiceRemaining).coerceAtLeast(0.0))
            val toAccount = roundAmount(amount - toInvoice)
            val base      = "stay_${bill.stayId}_txn_${txn.id}"

            if (toInvoice > 0.0) {
                log("Posting PAYMENT (invoice) — amount=$toInvoice, method=$method, sourceId=$base")
                client.postPayment(
                    token = token,
                    req   = PostPaymentRequest(
                        customerId  = customerId,
                        amount      = toInvoice,
                        method      = method,
                        invoiceId   = invoiceId,
                        description = stampManager("Payment via ${txn.method}"),
                        date        = today,
                        appId       = APP_ID,
                        sourceId    = base,
                    ),
                )
                invoiceRemaining = roundAmount(invoiceRemaining - toInvoice)
            }
            if (toAccount > 0.0) {
                log("Posting PAYMENT (on account) — amount=$toAccount, method=$method, sourceId=${base}_acct")
                client.postPayment(
                    token = token,
                    req   = PostPaymentRequest(
                        customerId  = customerId,
                        amount      = toAccount,
                        method      = method,
                        invoiceId   = null,
                        description = stampManager("Payment via ${txn.method} (prior balance)"),
                        date        = today,
                        appId       = APP_ID,
                        sourceId    = "${base}_acct",
                    ),
                )
            }
        }

        log("settle() COMPLETE — stayId=${bill.stayId}, invoiceId=$invoiceId")
        log("─────────────────────────────────────────")

        SettleResult(invoiceId = invoiceId, invoiceNumber = saleResponse.invoice.invoiceNumber)

    }.also { result ->
        result.onFailure { e ->
            log("settle() FAILED — ${e::class.simpleName}: ${e.message}")
            log("─────────────────────────────────────────")
        }
    }

    // ── Check-in advance (live cash/bank at check-in) ───────────────────────────

    /**
     * Posts a guest's advance to Humble Ledger AT CHECK-IN so the hotel's live
     * cash/bank reflects it immediately (rather than only at checkout):
     *   DR Cash | Bank      advance   ← cash/bank received now
     *   CR Advance Liability advance  ← held as a liability until the invoice
     *
     * Idempotent on `checkin_<stayId>_advance`. At checkout, [settle] reverses this
     * exact posting and re-posts the authoritative advance (which may have been
     * edited, or aggregated for a group), so this can never double-count.
     *
     * Returns success(true) when posted (or nothing to post), success(false) when
     * accounting isn't configured, failure(e) on a real error (caller may retry).
     */
    suspend fun postCheckInAdvance(
        stayId: String,
        amount: Double,
        method: String,
        checkInDate: Date,
        roomNumber: String,
        guestName: String,
    ): Result<Boolean> = runCatching {
        if (!AccountingConfig.isConfigured()) {
            log("postCheckInAdvance SKIP — not configured (stay $stayId)")
            return@runCatching false
        }
        val amt = roundAmount(amount)
        if (stayId.isBlank() || amt <= 0.01) return@runCatching true
        val token = client.ensureValidToken()
        val advanceLiabilityId = ensureAdvanceLiabilityAccount(token)
        val date = runCatching { SimpleDateFormat("yyyy-MM-dd").format(checkInDate) }.getOrNull()
            ?: LocalDate.now().toString()
        log("postCheckInAdvance — stay=$stayId, amount=$amt, method=$method, date=$date")
        client.postRawTransaction(
            token = token,
            req   = RawTransactionRequest(
                appId       = APP_ID,
                sourceId    = "checkin_${stayId}_advance",
                postingType = "ADVANCE",
                description = stampManager("Advance received at check-in — Room $roomNumber ($guestName)"),
                date        = date,
                entries     = listOf(
                    RawEntryInput(account = if (method == "BANK") "Bank" else "Cash", type = "DEBIT", amount = amt),
                    RawEntryInput(accountId = advanceLiabilityId, type = "CREDIT", amount = amt),
                ),
            ),
        )
        log("postCheckInAdvance OK — stay=$stayId")
        true
    }.also { result ->
        result.onFailure { e -> log("postCheckInAdvance FAILED — stay $stayId: ${e::class.simpleName}: ${e.message}") }
    }

    /**
     * Posts an advance collected DURING a stay (from the reception desk) to Humble Ledger:
     *   DR Cash | Bank        amount
     *   CR Advance Liability  amount
     *
     * Idempotent on `stayadv_<stayId>_<chargeId>` ([chargeId] must be unique per advance, so
     * multiple advances on the same stay each post once). Because the caller also bumps
     * `stay.advancePaidAmount` and marks the stay's advance as posted, checkout settle counts
     * this as already-held and never re-posts it (top-up = 0).
     *
     * Returns success(true) posted, success(false) not configured, failure(e) on a real error.
     */
    suspend fun postStayAdvance(
        stayId: String,
        chargeId: String,
        amount: Double,
        method: String,
        roomNumber: String,
        guestName: String,
    ): Result<Boolean> = runCatching {
        if (!AccountingConfig.isConfigured()) {
            log("postStayAdvance SKIP — not configured (stay $stayId)")
            return@runCatching false
        }
        val amt = roundAmount(amount)
        if (stayId.isBlank() || chargeId.isBlank() || amt <= 0.01) return@runCatching true
        val token = client.ensureValidToken()
        val advanceLiabilityId = ensureAdvanceLiabilityAccount(token)
        log("postStayAdvance — stay=$stayId, charge=$chargeId, amount=$amt, method=$method")
        client.postRawTransaction(
            token = token,
            req   = RawTransactionRequest(
                appId       = APP_ID,
                sourceId    = "stayadv_${stayId}_${chargeId}",
                postingType = "ADVANCE",
                description = stampManager("Advance received — Room $roomNumber ($guestName)"),
                date        = LocalDate.now().toString(),
                entries     = listOf(
                    RawEntryInput(account = if (method == "BANK") "Bank" else "Cash", type = "DEBIT", amount = amt),
                    RawEntryInput(accountId = advanceLiabilityId, type = "CREDIT", amount = amt),
                ),
            ),
        )
        log("postStayAdvance OK — stay=$stayId, charge=$chargeId")
        true
    }.also { result ->
        result.onFailure { e -> log("postStayAdvance FAILED — stay $stayId: ${e::class.simpleName}: ${e.message}") }
    }

    /**
     * Posts a booking-cancellation refund to Humble Ledger as one balanced journal:
     *   DR Booking Refunds            (cash + bank)
     *   CR Cash                       cashAmount   (when > 0)
     *   CR Bank                       bankAmount   (when > 0)
     *
     * The CR Cash/Bank legs are auto-mirrored to the Firestore till by the API client
     * ([FirestoreLiquidityRepository]). Idempotent on `bookingrefund_<refundKey>` — the ledger
     * dedupes and the till mirror is keyed on the same sourceId, so a retry never double-counts.
     * The caller guarantees cashAmount + bankAmount equals the actual refund amount.
     *
     * Returns success(true) posted, success(false) not configured / nothing to post, failure(e) on error.
     */
    suspend fun postBookingRefund(
        refundKey: String,
        cashAmount: Double,
        bankAmount: Double,
        reason: String,
        guestName: String,
    ): Result<Boolean> = runCatching {
        if (!AccountingConfig.isConfigured()) {
            log("postBookingRefund SKIP — not configured (refund $refundKey)")
            return@runCatching false
        }
        val cash = roundAmount(cashAmount).coerceAtLeast(0.0)
        val bank = roundAmount(bankAmount).coerceAtLeast(0.0)
        val total = roundAmount(cash + bank)
        if (refundKey.isBlank() || total <= 0.01) return@runCatching true
        val token = client.ensureValidToken()
        val refundsAccountId = ensureBookingRefundsAccount(token)
        log("postBookingRefund — key=$refundKey, cash=$cash, bank=$bank, total=$total")
        val entries = buildList {
            add(RawEntryInput(accountId = refundsAccountId, type = "DEBIT", amount = total))
            if (cash > 0.0) add(RawEntryInput(account = "Cash", type = "CREDIT", amount = cash))
            if (bank > 0.0) add(RawEntryInput(account = "Bank", type = "CREDIT", amount = bank))
        }
        client.postRawTransaction(
            token = token,
            req   = RawTransactionRequest(
                appId       = APP_ID,
                sourceId    = "bookingrefund_$refundKey",
                postingType = "REFUND",
                description = stampManager("Booking refund — $guestName${if (reason.isNotBlank()) ": $reason" else ""}"),
                date        = LocalDate.now().toString(),
                entries     = entries,
            ),
        )
        log("postBookingRefund OK — key=$refundKey")
        true
    }.also { result ->
        result.onFailure { e -> log("postBookingRefund FAILED — refund $refundKey: ${e::class.simpleName}: ${e.message}") }
    }

    /**
     * Sums the advance already sitting in the Advance Liability account from CHECK-IN
     * for the stays on [bill] (those flagged [Stay.ledgerAdvancePostedAtCheckIn]).
     *
     * The checkout flow keeps those postings and tops up only the difference, so this
     * must be accurate: a stay that cannot be loaded throws, which fails settle so it
     * is retried — never guessing 0, which would double-post the advance.
     */
    private suspend fun resolveCheckInAdvanceHeld(bill: Bill): Double {
        val stayIds = bill.stayIds.ifEmpty { listOf(bill.stayId) }.filter { it.isNotBlank() }.distinct()
        if (stayIds.isEmpty()) return 0.0
        var held = 0.0
        for (sid in stayIds) {
            val stay = FirestoreStayRepository.getById(sid)
                ?: error("Could not load stay $sid to measure its check-in advance")
            if (stay.ledgerAdvancePostedAtCheckIn) held += roundAmount(stay.advancePaidAmount)
        }
        return roundAmount(held)
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    /**
     * Rounds to 2 decimal places using HALF_UP — standard accounting rounding mode.
     * Applied to every monetary value before it reaches the API.
     */
    private fun roundAmount(value: Double): Double =
        BigDecimal(value).setScale(2, RoundingMode.HALF_UP).toDouble()

    /**
     * Returns the full [CustomerData] (including [CustomerData.accountId] for the AR
     * sub-account) for the bill's guest.
     *
     * Preferred path: when [guestUid] is present it is sent as the ledger
     * `externalId`. Humble Ledger's create is idempotent on externalId, so this
     * returns the SAME ledger customer on every visit (balance carried over) or
     * creates one the first time — no phone/name guessing, no duplicates.
     *
     * Fallback (no UID — e.g. a walk-in with no phone on record): resolve by phone,
     * then an exact name match, then create. Kept so the flow degrades gracefully.
     */
    private suspend fun resolveCustomer(
        token: String,
        guestName: String,
        guestPhone: String,
        guestUid: String,
        guestGstin: String = "",
    ): CustomerData {
        val gstin = guestGstin.takeIf { it.isNotBlank() }
        // 0. Stable cross-system UID — the canonical identity. Idempotent on the server.
        if (guestUid.isNotBlank()) {
            val c = client.createCustomer(
                token = token,
                req   = CreateCustomerRequest(
                    name       = guestName,
                    phone      = guestPhone.takeIf { it.isNotBlank() },
                    gstin      = gstin,
                    externalId = guestUid,
                ),
            )
            log("Resolved customer by UID '$guestUid' — id=${c.id}, arAccountId=${c.accountId}")
            return c
        }

        // 1. Phone is the unique identity (matches how Firestore keys guests).
        if (guestPhone.isNotBlank()) {
            val byPhone = client.findCustomerByPhone(token, guestPhone)
            if (byPhone != null) {
                log("Found customer by phone '$guestPhone' — id=${byPhone.id}, arAccountId=${byPhone.accountId}")
                return byPhone
            }
        }

        // 2. Fallback: exact name match (legacy guests created before phone was sent).
        //    Only reuse when there is no phone to key on, to avoid merging two
        //    different same-named guests.
        if (guestPhone.isBlank()) {
            val results  = client.searchCustomers(token, guestName)
            log("Customer search '$guestName' → ${results.size} result(s): ${results.map { it.name }}")
            val existing = results.firstOrNull { it.name.equals(guestName, ignoreCase = true) }
            if (existing != null) {
                log("Found existing customer by name — id=${existing.id}, arAccountId=${existing.accountId}")
                return existing
            }
        }

        log("No match — creating new customer '$guestName' (phone='$guestPhone')")
        val created = client.createCustomer(
            token = token,
            req   = CreateCustomerRequest(
                name  = guestName,
                phone = guestPhone.takeIf { it.isNotBlank() },
                gstin = gstin,
            ),
        )
        log("Customer created — id=${created.id}, arAccountId=${created.accountId}")
        return created
    }

    /**
     * Finds or creates the "$ADVANCE_ACCOUNT_NAME" liability account and returns its UUID.
     * The result is cached in [advanceLiabilityAccountId] for the lifetime of the process.
     */
    private suspend fun ensureAdvanceLiabilityAccount(token: String): String {
        advanceLiabilityAccountId?.let { return it }

        val accounts = client.getAccounts(token)
        val existing = accounts.firstOrNull { it.name.equals(ADVANCE_ACCOUNT_NAME, ignoreCase = true) }
        if (existing != null) {
            log("Found '$ADVANCE_ACCOUNT_NAME' account — id=${existing.id}")
            advanceLiabilityAccountId = existing.id
            return existing.id
        }

        log("'$ADVANCE_ACCOUNT_NAME' account not found — creating it")
        val created = client.createAccount(
            token = token,
            req   = CreateAccountRequest(name = ADVANCE_ACCOUNT_NAME, type = "LIABILITY"),
        )
        log("'$ADVANCE_ACCOUNT_NAME' account created — id=${created.id}")
        advanceLiabilityAccountId = created.id
        return created.id
    }

    /** Finds or creates the "$BOOKING_REFUNDS_ACCOUNT_NAME" expense account and returns its UUID (cached). */
    private suspend fun ensureBookingRefundsAccount(token: String): String {
        bookingRefundsAccountId?.let { return it }
        val accounts = client.getAccounts(token)
        val existing = accounts.firstOrNull { it.name.equals(BOOKING_REFUNDS_ACCOUNT_NAME, ignoreCase = true) }
        val id = existing?.id
            ?: client.createAccount(token, CreateAccountRequest(name = BOOKING_REFUNDS_ACCOUNT_NAME, type = "EXPENSE")).id
        bookingRefundsAccountId = id
        return id
    }

    // ── Vendor settlement (food bought from an outside supplier) ────────────────

    /**
     * Posts the vendor side of a completed order to Humble Ledger:
     *   1. resolve/create the ledger vendor (idempotent on the Firestore vendor id),
     *   2. book the purchase   — DR Food expense / CR vendor AP  (we now owe the vendor),
     *   3. book payments       — DR vendor AP / CR Cash|Bank      (one per method paid now).
     *
     * Paying less than the cost leaves the vendor PAYABLE (we owe them); paying more
     * leaves a CREDIT (prepaid). Every leg uses a deterministic sourceId so retries
     * are idempotent.
     *
     * Returns:
     *   success(true)  — posted to the ledger,
     *   success(false) — skipped (accounting not configured, or no vendor / in-house),
     *   failure(e)     — a real error; the caller should persist it for retry.
     */
    suspend fun settleOrderVendor(order: Order): Result<Boolean> = runCatching {
        if (order.vendorId.isBlank()) {
            log("settleOrderVendor SKIP — no vendor (in-house) for order ${order.id}")
            return@runCatching false
        }
        if (!AccountingConfig.isConfigured()) {
            log("settleOrderVendor SKIP — accounting not configured (order ${order.id})")
            return@runCatching false
        }

        val cost = roundAmount(order.vendorCost)
        val cash = roundAmount(order.vendorCashPaid)
        val bank = roundAmount(order.vendorBankPaid)
        val today = LocalDate.now().toString()
        val token = client.ensureValidToken()

        log("settleOrderVendor — order=${order.id}, vendor='${order.vendorName}' (uid=${order.vendorId}), " +
            "cost=$cost, cash=$cash, bank=$bank")

        // 1. Resolve/create the ledger vendor (externalId = Firestore vendors/{id}).
        val hlVendor = client.createVendor(
            token = token,
            req   = CreateVendorRequest(name = order.vendorName.ifBlank { "Vendor" }, externalId = order.vendorId),
        )
        log("Vendor resolved — ledgerId=${hlVendor.id}")

        // 2. Purchase (DR Food expense / CR vendor AP).
        if (cost > 0.0) {
            val foodExpenseId = ensureFoodExpenseAccount(token)
            client.postPurchase(
                token = token,
                req   = PostPurchaseRequest(
                    vendorId         = hlVendor.id,
                    amount           = cost,
                    expenseAccountId = foodExpenseId,
                    description      = stampManager("Food order — Room ${order.roomNumber} (${order.guestName})"),
                    date             = today,
                    appId            = APP_ID,
                    sourceId         = "order_${order.id}_purchase",
                ),
            )
            log("Purchase posted — $cost")
        }

        // 3. Payments (one per method actually paid).
        if (cash > 0.0) {
            client.postVendorPayment(
                token = token,
                req   = PostVendorPaymentRequest(
                    vendorId    = hlVendor.id,
                    amount      = cash,
                    method      = "CASH",
                    description = stampManager("Vendor payment (cash) — ${order.vendorName.ifBlank { "Vendor" }} · Room ${order.roomNumber}"),
                    date        = today,
                    appId       = APP_ID,
                    sourceId    = "order_${order.id}_pay_cash",
                ),
            )
            log("Vendor cash payment posted — $cash")
        }
        if (bank > 0.0) {
            client.postVendorPayment(
                token = token,
                req   = PostVendorPaymentRequest(
                    vendorId    = hlVendor.id,
                    amount      = bank,
                    method      = "BANK",
                    description = stampManager("Vendor payment (bank) — ${order.vendorName.ifBlank { "Vendor" }} · Room ${order.roomNumber}"),
                    date        = today,
                    appId       = APP_ID,
                    sourceId    = "order_${order.id}_pay_bank",
                ),
            )
            log("Vendor bank payment posted — $bank")
        }

        log("settleOrderVendor COMPLETE — order ${order.id}")
        true
    }.also { result ->
        result.onFailure { e -> log("settleOrderVendor FAILED — order ${order.id}: ${e::class.simpleName}: ${e.message}") }
    }

    /**
     * Fetches the hotel's current Cash & Bank balances from Humble Ledger. Uses a
     * far-future as-of date so it reflects every posted entry (the true current
     * balance), immune to timezone/date edges. Bank folds in the clearing accounts.
     * Returns null when accounting isn't configured or on any non-fatal error.
     */
    suspend fun fetchCashBankBalance(): CashBankBalance? {
        if (!AccountingConfig.isConfigured()) return null
        return runCatching {
            val token = client.ensureValidToken()
            client.getBalanceSheet(token, "2999-12-31")?.let { bs ->
                fun sumNamed(vararg names: String): Double {
                    val wanted = names.map { it.lowercase() }
                    return bs.assets
                        .filter { it.name.lowercase() in wanted }
                        .sumOf { it.balance?.toDoubleOrNull() ?: 0.0 }
                }
                CashBankBalance(
                    cash = sumNamed("Cash"),
                    bank = sumNamed("Bank", "Card Clearing", "UPI Clearing"),
                )
            }
        }.getOrNull()
    }

    /**
     * Fetches a vendor's current balance from Humble Ledger by its Firestore id
     * (externalId). Returns null when accounting isn't configured, the vendor isn't
     * in the ledger yet (brand-new), or on any non-fatal error.
     */
    suspend fun fetchVendorBalance(externalId: String): VendorBalanceInfo? {
        if (externalId.isBlank() || !AccountingConfig.isConfigured()) return null
        return runCatching {
            val token = client.ensureValidToken()
            client.getVendorByExternalId(token, externalId)?.let {
                VendorBalanceInfo(
                    payable = it.currentBalance ?: (it.payable?.toDoubleOrNull() ?: 0.0),
                    balanceType = it.balanceType ?: "SETTLED",
                )
            }
        }.getOrNull()
    }

    /**
     * Fetches a guest's current Humble Ledger balance by phone (matched to the ledger
     * customer). `balance` is positive when the guest owes the hotel (RECEIVABLE) and
     * negative when they have credit. Returns null when accounting isn't configured, the
     * phone is blank, the guest isn't in the ledger yet, or on any non-fatal error.
     */
    suspend fun fetchCustomerBalance(phone: String): CustomerBalanceInfo? {
        val normalized = normalizePhoneE164(phone) ?: phone.trim()
        if (normalized.isBlank() || !AccountingConfig.isConfigured()) return null
        return runCatching {
            val token = client.ensureValidToken()
            client.findCustomerByPhone(token, normalized)?.let {
                CustomerBalanceInfo(
                    balance = it.currentBalance ?: (it.outstanding?.toDoubleOrNull() ?: 0.0),
                    balanceType = it.balanceType ?: "SETTLED",
                )
            }
        }.getOrNull()
    }

    /**
     * Current balance for a guest looked up by their ledger account UID (externalId).
     * Used when the guest was identified by NAME (their new phone may not be in the ledger),
     * so the balance still resolves to the correct account. Null on any non-fatal error.
     */
    suspend fun fetchCustomerBalanceByUid(uid: String): CustomerBalanceInfo? {
        if (uid.isBlank() || !AccountingConfig.isConfigured()) return null
        return runCatching {
            val token = client.ensureValidToken()
            client.findCustomerByExternalId(token, uid)?.let {
                CustomerBalanceInfo(
                    balance = it.currentBalance ?: (it.outstanding?.toDoubleOrNull() ?: 0.0),
                    balanceType = it.balanceType ?: "SETTLED",
                )
            }
        }.getOrNull()
    }

    /**
     * Finds or creates the "$FOOD_EXPENSE_ACCOUNT_NAME" expense account and returns its UUID.
     * Cached in [foodExpenseAccountId] for the process lifetime.
     */
    private suspend fun ensureFoodExpenseAccount(token: String): String {
        foodExpenseAccountId?.let { return it }
        val accounts = client.getAccounts(token)
        val existing = accounts.firstOrNull { it.name.equals(FOOD_EXPENSE_ACCOUNT_NAME, ignoreCase = true) }
        if (existing != null) {
            foodExpenseAccountId = existing.id
            return existing.id
        }
        log("'$FOOD_EXPENSE_ACCOUNT_NAME' account not found — creating it")
        val created = client.createAccount(
            token = token,
            req   = CreateAccountRequest(name = FOOD_EXPENSE_ACCOUNT_NAME, type = "EXPENSE"),
        )
        foodExpenseAccountId = created.id
        return created.id
    }

    // ── Expense settlement (hotel costs, optionally tied to a vendor) ───────────

    /**
     * Posts a hotel expense to Humble Ledger against the "General Expense" account.
     *   - With a vendor: purchase (DR expense / CR vendor AP) + cash/bank payments,
     *     so the vendor's balance tracks pay-later / overpay (same as orders).
     *   - Without a vendor: a direct expense (DR expense / CR Cash|Bank) per method.
     * Deterministic sourceIds keep every leg idempotent on retry.
     *
     * Returns success(true) when posted, success(false) when skipped (not configured),
     * failure(e) on a real error (caller persists it for retry).
     */
    suspend fun settleExpense(expense: Expense): Result<Boolean> = runCatching {
        if (!AccountingConfig.isConfigured()) {
            log("settleExpense SKIP — accounting not configured (expense ${expense.id})")
            return@runCatching false
        }
        val amount = roundAmount(expense.amount)
        val cash = roundAmount(expense.cashPaid)
        val bank = roundAmount(expense.bankPaid)
        val today = LocalDate.now().toString()
        val token = client.ensureValidToken()
        val expenseAccountId = ensureGeneralExpenseAccount(token)
        val desc = stampManager(buildString {
            append(expense.title.ifBlank { if (expense.vendorName.isNotBlank()) "Expense — ${expense.vendorName}" else "Expense" })
            if (expense.notes.isNotBlank()) append(" — ${expense.notes}")
        })
        log("settleExpense — id=${expense.id}, vendor='${expense.vendorName}' (uid=${expense.vendorId}), " +
            "amount=$amount, cash=$cash, bank=$bank")

        if (expense.vendorId.isNotBlank()) {
            // Vendor expense → Accounts Payable flow (DR General Expense / CR vendor AP).
            val hlVendor = client.createVendor(
                token = token,
                req   = CreateVendorRequest(name = expense.vendorName.ifBlank { "Vendor" }, externalId = expense.vendorId),
            )
            if (amount > 0.0) {
                client.postPurchase(
                    token = token,
                    req   = PostPurchaseRequest(
                        vendorId = hlVendor.id, amount = amount, expenseAccountId = expenseAccountId,
                        description = desc, date = today, appId = APP_ID, sourceId = "expense_${expense.id}_purchase",
                    ),
                )
            }
            // Use the full expense description (title + notes) on the cash/bank leg too, so the
            // title shows in both the Humble Ledger and the daily book — not just an opaque id.
            if (cash > 0.0) client.postVendorPayment(
                token, PostVendorPaymentRequest(hlVendor.id, cash, "CASH", desc, today, APP_ID, "expense_${expense.id}_pay_cash"),
            )
            if (bank > 0.0) client.postVendorPayment(
                token, PostVendorPaymentRequest(hlVendor.id, bank, "BANK", desc, today, APP_ID, "expense_${expense.id}_pay_bank"),
            )
        } else {
            // No vendor → direct expense paid from cash/bank (must be fully paid).
            if (cash > 0.0) client.postExpense(
                token, PostExpenseRequest(cash, expenseAccountId, "CASH", desc, today, APP_ID, "expense_${expense.id}_cash"),
            )
            if (bank > 0.0) client.postExpense(
                token, PostExpenseRequest(bank, expenseAccountId, "BANK", desc, today, APP_ID, "expense_${expense.id}_bank"),
            )
        }
        log("settleExpense COMPLETE — expense ${expense.id}")
        true
    }.also { result ->
        result.onFailure { e -> log("settleExpense FAILED — expense ${expense.id}: ${e::class.simpleName}: ${e.message}") }
    }

    private suspend fun ensureGeneralExpenseAccount(token: String): String {
        generalExpenseAccountId?.let { return it }
        val accounts = client.getAccounts(token)
        val existing = accounts.firstOrNull { it.name.equals(GENERAL_EXPENSE_ACCOUNT_NAME, ignoreCase = true) }
        if (existing != null) {
            generalExpenseAccountId = existing.id
            return existing.id
        }
        log("'$GENERAL_EXPENSE_ACCOUNT_NAME' account not found — creating it")
        val created = client.createAccount(token, CreateAccountRequest(name = GENERAL_EXPENSE_ACCOUNT_NAME, type = "EXPENSE"))
        generalExpenseAccountId = created.id
        return created.id
    }

    // ── Money transfer (from → to: DR the destination / CR the source) ──────────

    /**
     * Posts a money transfer to Humble Ledger as one balanced journal entry:
     * `DR(to) / CR(from)`. Either side may be Cash, Bank, a Customer (AR sub-account)
     * or a Vendor (AP sub-account). This single rule is correct for any pair because
     * each account's type handles the sign. Idempotent on the transfer id.
     */
    suspend fun settleTransfer(transfer: Transfer): Result<Boolean> = runCatching {
        if (!AccountingConfig.isConfigured()) {
            log("settleTransfer SKIP — accounting not configured (transfer ${transfer.id})")
            return@runCatching false
        }
        val amount = roundAmount(transfer.amount)
        if (amount <= 0.0) return@runCatching false
        val token = client.ensureValidToken()
        val today = LocalDate.now().toString()

        val toEntry = resolveTransferEntry(token, transfer.toKind, transfer.toRefId, transfer.toName, transfer.toPhone, "DEBIT", amount)
        val fromEntry = resolveTransferEntry(token, transfer.fromKind, transfer.fromRefId, transfer.fromName, transfer.fromPhone, "CREDIT", amount)

        val desc = stampManager(buildString {
            append("Transfer — ${transfer.fromName} → ${transfer.toName}")
            if (transfer.notes.isNotBlank()) append(" · ${transfer.notes}")
        })
        log("settleTransfer — id=${transfer.id}, ${transfer.fromName} → ${transfer.toName}, amount=$amount")
        client.postRawTransaction(
            token = token,
            req   = RawTransactionRequest(
                appId = APP_ID, sourceId = "transfer_${transfer.id}", postingType = "JOURNAL",
                description = desc, date = today, entries = listOf(toEntry, fromEntry),
            ),
        )
        log("settleTransfer COMPLETE — transfer ${transfer.id}")
        true
    }.also { result ->
        result.onFailure { e -> log("settleTransfer FAILED — transfer ${transfer.id}: ${e::class.simpleName}: ${e.message}") }
    }

    /** Resolves one side of a transfer to a ledger entry (by account name or sub-account id). */
    private suspend fun resolveTransferEntry(
        token: String, kind: String, refId: String, name: String, phone: String, type: String, amount: Double,
    ): RawEntryInput = when (kind) {
        "CASH" -> RawEntryInput(account = "Cash", type = type, amount = amount)
        "BANK" -> RawEntryInput(account = "Bank", type = type, amount = amount)
        "CUSTOMER" -> {
            // Reuse billing's canonical resolution (uid → phone → name) so a transfer
            // hits the SAME ledger customer that an invoice would.
            val c = resolveCustomer(token, name.ifBlank { "Guest" }, phone, refId)
            RawEntryInput(accountId = c.accountId, type = type, amount = amount)
        }
        "VENDOR" -> {
            val v = client.createVendor(token, CreateVendorRequest(name = name.ifBlank { "Vendor" }, externalId = refId))
            RawEntryInput(accountId = v.accountId, type = type, amount = amount)
        }
        else -> error("Unknown transfer party kind: $kind")
    }

    private fun buildSaleDescription(bill: Bill): String = buildString {
        append("Hotel stay — Room ${bill.roomNumber}")
        if (bill.checkInDate != null && bill.checkOutDate != null) {
            append(", ${dateFmt.format(bill.checkInDate)} – ${dateFmt.format(bill.checkOutDate)}")
        }
        if (bill.guestName.isNotBlank()) append(" (${bill.guestName})")
    }

    private fun log(msg: String) = println("[Accounting] $msg")

    /**
     * Appends the on-duty reception manager to a ledger description, so every entry
     * records who was at the desk. Returns the description unchanged when no manager
     * is set (nothing breaks before the handover system is used).
     */
    private fun stampManager(desc: String): String {
        val mgr = AppContext.currentManager.trim()
        return if (mgr.isNotEmpty()) "$desc · by $mgr" else desc
    }
}
