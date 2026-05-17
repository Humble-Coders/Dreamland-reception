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
)

internal data class CustomerData(
    val id: String = "",
    val accountId: String = "",
    val name: String = "",
    val phone: String? = null,
    val outstanding: String? = null,
)

internal data class CustomerListEnvelope(
    val data: List<CustomerData> = emptyList(),
)

// ── Sale request / response ───────────────────────────────────────────────────

internal data class PostSaleRequest(
    val customerId: String,
    val amount: Double,
    val taxRate: Double,
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
    val method: String,          // "CASH" | "BANK"
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
