package com.example.dreamland_reception.data.accounting

import com.example.dreamland_reception.data.model.Bill
import com.example.dreamland_reception.data.model.Order
import java.math.BigDecimal
import java.math.RoundingMode
import java.text.SimpleDateFormat
import java.time.LocalDate
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
 * ## Idempotency
 * Every posting uses a deterministic sourceId:
 *   stay_<stayId>_advance_in      ADVANCE
 *   stay_<stayId>_sale            SALE
 *   stay_<stayId>_advance_applied ADVANCE_APPLIED
 *   stay_<stayId>_txn_<txnId>     PAYMENT (one per PaymentTransaction)
 */
/** A vendor's current Humble Ledger balance. [payable] > 0 = we owe them. */
data class VendorBalanceInfo(val payable: Double, val balanceType: String)

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
        val customer       = resolveCustomer(token, bill.guestName, guestPhone, guestUid)
        val customerId     = customer.id
        val customerArId   = customer.accountId   // AR sub-account for ADVANCE_APPLIED credit leg
        log("Customer — id=$customerId, arAccountId=$customerArId")

        // ── 4. Ensure the canonical "Advance Liability" account exists ─────────
        val advanceLiabilityId: String? = if (hasAdvance) {
            val id = ensureAdvanceLiabilityAccount(token)
            log("Advance liability account id=$id")
            id
        } else null

        // ── 5. ADVANCE — DR Cash / CR Advance Liability ───────────────────────
        // Posted with the check-in date because cash was received at check-in.
        if (hasAdvance && advanceLiabilityId != null) {
            log("Posting ADVANCE — amount=$advanceRounded, date=$checkInDate, " +
                "sourceId=stay_${bill.stayId}_advance_in")
            client.postRawTransaction(
                token = token,
                req   = RawTransactionRequest(
                    appId       = APP_ID,
                    sourceId    = "stay_${bill.stayId}_advance_in",
                    postingType = "ADVANCE",
                    description = "Advance received — Room ${bill.roomNumber} (${bill.guestName})",
                    date        = checkInDate,
                    entries     = listOf(
                        RawEntryInput(account = if (bill.advancePaymentMethod == "BANK") "Bank" else "Cash", type = "DEBIT", amount = advanceRounded),
                        RawEntryInput(accountId = advanceLiabilityId, type = "CREDIT", amount = advanceRounded),
                    ),
                ),
            )
            log("ADVANCE posted OK")
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
                description = saleDescription,
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
                        description      = "Advance applied — invoice ${saleResponse.invoice.invoiceNumber}",
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
                        description      = "Advance surplus (credit) — ${saleResponse.invoice.invoiceNumber}",
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
                        description = "Payment via ${txn.method}",
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
                        description = "Payment via ${txn.method} (prior balance)",
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
    ): CustomerData {
        // 0. Stable cross-system UID — the canonical identity. Idempotent on the server.
        if (guestUid.isNotBlank()) {
            val c = client.createCustomer(
                token = token,
                req   = CreateCustomerRequest(
                    name       = guestName,
                    phone      = guestPhone.takeIf { it.isNotBlank() },
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
                    description      = "Food order — Room ${order.roomNumber} (${order.guestName})",
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
                    description = "Vendor payment (cash) — order ${order.id}",
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
                    description = "Vendor payment (bank) — order ${order.id}",
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

    private fun buildSaleDescription(bill: Bill): String = buildString {
        append("Hotel stay — Room ${bill.roomNumber}")
        if (bill.checkInDate != null && bill.checkOutDate != null) {
            append(", ${dateFmt.format(bill.checkInDate)} – ${dateFmt.format(bill.checkOutDate)}")
        }
        if (bill.guestName.isNotBlank()) append(" (${bill.guestName})")
    }

    private fun log(msg: String) = println("[Accounting] $msg")
}
