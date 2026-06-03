package com.example.dreamland_reception.data.accounting

import com.example.dreamland_reception.data.model.Bill
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
 *      CR  Guest Advances Received     advanceAmount   ← recorded as liability
 *
 *   2. SALE  (via POST /api/v1/sales, at checkout)
 *      DR  Accounts Receivable (AR)    total
 *      CR  Sales Revenue               subtotal
 *      CR  GST Payable                 tax              (when taxRate > 0)
 *
 *   3. ADVANCE_APPLIED  (via POST /api/v1/transactions, postingType = ADVANCE_APPLIED)
 *      DR  Guest Advances Received     advanceAmount   ← liability cleared
 *      CR  AR (customer sub-account)   advanceAmount   ← AR reduced by advance
 *
 *   4. PAYMENT  (via POST /api/v1/payments, one per checkout PaymentTransaction)
 *      DR  Cash | Bank                 txn.amount
 *      CR  AR (customer sub-account)   txn.amount
 *
 * The sum of ADVANCE_APPLIED + all PAYMENTs equals the invoice total exactly,
 * enforced by a penny adjustment on the last checkout payment.
 *
 * ## Idempotency
 * Every posting uses a deterministic sourceId:
 *   stay_<stayId>_advance_in      ADVANCE
 *   stay_<stayId>_sale            SALE
 *   stay_<stayId>_advance_applied ADVANCE_APPLIED
 *   stay_<stayId>_txn_<txnId>     PAYMENT (one per PaymentTransaction)
 */
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

    /** Outcome of a successful settlement — carries the ledger invoice identity. */
    data class SettleResult(
        val invoiceId: String,
        val invoiceNumber: String,
    )

    // ─────────────────────────────────────────────────────────────────────────

    suspend fun settle(bill: Bill, guestPhone: String): Result<SettleResult> = runCatching {

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
        log("Resolving customer '${bill.guestName}'")
        val customer       = resolveCustomer(token, bill.guestName, guestPhone)
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
                        RawEntryInput(account = "Cash", type = "DEBIT",  amount = advanceRounded),
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

        // ── 7. ADVANCE_APPLIED — DR Advance Liability / CR Customer AR ────────
        // Clears the advance liability and reduces the customer's AR balance.
        if (hasAdvance && advanceLiabilityId != null) {
            log("Posting ADVANCE_APPLIED — amount=$advanceRounded, " +
                "sourceId=stay_${bill.stayId}_advance_applied")
            client.postRawTransaction(
                token = token,
                req   = RawTransactionRequest(
                    appId       = APP_ID,
                    sourceId    = "stay_${bill.stayId}_advance_applied",
                    postingType = "ADVANCE_APPLIED",
                    description = "Advance applied — invoice ${saleResponse.invoice.invoiceNumber}",
                    date        = today,
                    entries     = listOf(
                        RawEntryInput(accountId = advanceLiabilityId, type = "DEBIT",  amount = advanceRounded),
                        RawEntryInput(accountId = customerArId,       type = "CREDIT", amount = advanceRounded),
                    ),
                ),
            )
            log("ADVANCE_APPLIED posted OK")
        }

        // ── 8. PAYMENT — one per checkout PaymentTransaction (only remaining) ──
        // Remaining AR after ADVANCE_APPLIED = invoiceTotal - advance.
        // Penny-adjust the last payment so sum equals the remaining exactly.
        val checkoutPayments = bill.transactions.map { txn ->
            PaymentEntry(
                amount      = roundAmount(txn.amount),
                method      = if (txn.method == "CASH") "CASH" else "BANK",
                sourceId    = "stay_${bill.stayId}_txn_${txn.id}",
                description = "Payment via ${txn.method}",
            )
        }.toMutableList()

        if (checkoutPayments.isNotEmpty()) {
            val remainingAr = roundAmount(invoiceTotal - advanceRounded)
            val checkoutSum = roundAmount(checkoutPayments.sumOf { it.amount })
            val diff        = roundAmount(remainingAr - checkoutSum)
            log("Checkout payments: remainingAR=$remainingAr, checkoutSum=$checkoutSum, diff=$diff")
            when {
                diff == 0.0 -> log("Payments match remainingAR exactly — no adjustment needed")
                Math.abs(diff) <= 0.01 -> {
                    // Pure floating-point rounding error — absorb into last payment
                    val last     = checkoutPayments.last()
                    val adjusted = roundAmount(last.amount + diff)
                    checkoutPayments[checkoutPayments.lastIndex] = last.copy(amount = adjusted)
                    log("Penny adjustment (rounding error) → '${last.sourceId}': ${last.amount} → $adjusted")
                }
                else -> {
                    // Genuine partial payment — post amounts as-is, leave AR outstanding
                    log("Partial payment detected (diff=$diff > 0.01) — posting actual amounts, " +
                        "${if (diff > 0) "₹$diff remains outstanding" else "₹${Math.abs(diff)} overpaid"}")
                }
            }
        }

        log("Checkout payment entries (${checkoutPayments.size}): " +
            checkoutPayments.joinToString { "${it.sourceId}=${it.amount}(${it.method})" })

        checkoutPayments.forEachIndexed { i, entry ->
            log("Posting PAYMENT[$i] — amount=${entry.amount}, method=${entry.method}, " +
                "invoiceId=$invoiceId, sourceId=${entry.sourceId}")
            client.postPayment(
                token = token,
                req   = PostPaymentRequest(
                    customerId  = customerId,
                    amount      = entry.amount,
                    method      = entry.method,
                    invoiceId   = invoiceId,
                    description = entry.description,
                    date        = today,
                    appId       = APP_ID,
                    sourceId    = entry.sourceId,
                ),
            )
            log("PAYMENT[$i] posted OK")
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
     * sub-account), resolving identity by PHONE first (the canonical guest key),
     * then falling back to an exact name match, and finally creating a new ledger
     * customer. Two different guests who happen to share a name get distinct
     * ledger customers because the server disambiguates the AR sub-account name.
     */
    private suspend fun resolveCustomer(
        token: String,
        guestName: String,
        guestPhone: String,
    ): CustomerData {
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

    private fun buildSaleDescription(bill: Bill): String = buildString {
        append("Hotel stay — Room ${bill.roomNumber}")
        if (bill.checkInDate != null && bill.checkOutDate != null) {
            append(", ${dateFmt.format(bill.checkInDate)} – ${dateFmt.format(bill.checkOutDate)}")
        }
        if (bill.guestName.isNotBlank()) append(" (${bill.guestName})")
    }

    private fun log(msg: String) = println("[Accounting] $msg")

    private data class PaymentEntry(
        val amount: Double,
        val method: String,
        val sourceId: String,
        val description: String,
    )
}
