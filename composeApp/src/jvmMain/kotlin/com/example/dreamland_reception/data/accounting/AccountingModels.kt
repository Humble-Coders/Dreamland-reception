package com.example.dreamland_reception.data.accounting

// ── Auth request / response ───────────────────────────────────────────────────

internal data class LoginRequest(
    val email: String,
    val password: String,
)

internal data class RefreshRequest(
    val refreshToken: String,
)

/** Raw wrapper around the `data` envelope returned by /auth/login and /auth/refresh. */
internal data class AuthDataEnvelope(
    val accessToken: String? = null,
    val refreshToken: String? = null,
    val token: String? = null,         // legacy alias for accessToken
)

internal data class TokenData(
    val accessToken: String,
    val refreshToken: String,
)

// ── Customer request / response ───────────────────────────────────────────────

internal data class CreateCustomerRequest(
    val name: String,
    val phone: String? = null,
    val email: String? = null,
    // Dreamland's stable guest UID. Humble Ledger create is idempotent on this:
    // sending the same externalId returns the existing customer (balance intact).
    val externalId: String? = null,
)

internal data class CustomerData(
    val id: String = "",
    val accountId: String = "",
    val name: String = "",
    val phone: String? = null,
    val externalId: String? = null,
    val outstanding: String? = null,
)

internal data class CustomerListEnvelope(
    val data: List<CustomerData> = emptyList(),
)

// ── Sale request / response ───────────────────────────────────────────────────

internal data class PostSaleRequest(
    val customerId: String,
    val amount: Double,          // net revenue (subtotal − discount), ex-tax
    val taxRate: Double,         // effective rate — display metadata only
    val taxAmount: Double,       // exact tax; authoritative on the server
    val description: String,
    val date: String,            // "YYYY-MM-DD"
    val appId: String,
    val sourceId: String,
)

internal data class InvoiceData(
    val id: String = "",
    val invoiceNumber: String = "",
    val status: String = "",
    val total: String = "0",
    val amountPaid: String = "0",
)

internal data class SaleResponseData(
    val invoice: InvoiceData = InvoiceData(),
)

// ── Payment request ───────────────────────────────────────────────────────────

internal data class PostPaymentRequest(
    val customerId: String,
    val amount: Double,
    val method: String,          // "CASH" | "BANK" — ignored when paymentAccountId is set
    // Overrides [method] with an explicit account to debit. Used to apply a guest
    // advance against the invoice by debiting the Advance Liability account, which
    // also records an InvoicePayment so the invoice can reach PAID.
    val paymentAccountId: String? = null,
    val invoiceId: String? = null,
    val description: String? = null,
    val date: String,            // "YYYY-MM-DD"
    val appId: String,
    val sourceId: String,
)

// ── Account endpoints ─────────────────────────────────────────────────────────

internal data class AccountData(
    val id: String = "",
    val name: String = "",
    val type: String = "",   // ASSET | LIABILITY | EQUITY | INCOME | EXPENSE
    val code: String? = null,
    val isActive: Boolean = true,
)

internal data class CreateAccountRequest(
    val name: String,
    val type: String,
    val code: String? = null,
)

// ── Raw double-entry transaction ──────────────────────────────────────────────

/**
 * One leg of a raw transaction.
 * Supply [accountId] (preferred UUID) OR [account] (legacy seeded name), not both.
 */
internal data class RawEntryInput(
    val accountId: String? = null,
    val account: String? = null,
    val type: String,            // "DEBIT" | "CREDIT"
    val amount: Double,
)

internal data class RawTransactionRequest(
    val appId: String,
    val sourceId: String,
    val postingType: String,     // ADVANCE | ADVANCE_APPLIED | JOURNAL | …
    val description: String,
    val date: String,            // "YYYY-MM-DD"
    val entries: List<RawEntryInput>,
)

// ── Vendors (Accounts Payable side) ───────────────────────────────────────────

internal data class CreateVendorRequest(
    val name: String,
    val phone: String? = null,
    val email: String? = null,
    // Firestore vendors/{id} — the stable cross-system key. Humble Ledger create is
    // idempotent on this: same externalId returns the same ledger vendor.
    val externalId: String? = null,
)

internal data class VendorData(
    val id: String = "",
    val accountId: String = "",
    val name: String = "",
    val externalId: String? = null,
    val payable: String? = null,        // positive = we owe them
    val currentBalance: Double? = null, // same value as a number
    val balanceType: String? = null,    // PAYABLE | CREDIT | SETTLED
)

// Purchase/bill from a vendor: DR expense / CR vendor AP.
internal data class PostPurchaseRequest(
    val vendorId: String,
    val amount: Double,          // subtotal (ex-tax)
    val taxRate: Double = 0.0,
    val taxAmount: Double? = null,
    val expenseAccountId: String,
    val description: String,
    val date: String,            // "YYYY-MM-DD"
    val appId: String,
    val sourceId: String,
)

// Direct expense (no vendor): DR expense / CR Cash|Bank|Payable.
internal data class PostExpenseRequest(
    val amount: Double,
    val expenseAccountId: String,
    val paymentMethod: String,   // "CASH" | "BANK" | "PAYABLE"
    val description: String,
    val date: String,            // "YYYY-MM-DD"
    val appId: String,
    val sourceId: String,
)

// Pay a vendor: DR vendor AP / CR Cash|Bank.
internal data class PostVendorPaymentRequest(
    val vendorId: String,
    val amount: Double,
    val method: String,          // "CASH" | "BANK"
    val description: String? = null,
    val date: String,            // "YYYY-MM-DD"
    val appId: String,
    val sourceId: String,
)

// ── Balance sheet (for the live Cash & Bank widget) ───────────────────────────

internal data class AssetLine(
    val accountId: String = "",
    val name: String = "",
    val balance: String? = null,   // positive = asset balance
)

internal data class BalanceSheetData(
    val assets: List<AssetLine> = emptyList(),
)

// ── Generic API envelope ──────────────────────────────────────────────────────

/**
 * Top-level wrapper for every Humble Ledger response:
 * `{ "success": true, "data": { ... } }` or `{ "success": false, "error": "...", "code": "..." }`.
 *
 * [T] is the type of the `data` field (use [Any] / [Map] when only checking `success`).
 */
internal data class ApiEnvelope<T>(
    val success: Boolean = false,
    val data: T? = null,
    val error: String? = null,
    val code: String? = null,
)
