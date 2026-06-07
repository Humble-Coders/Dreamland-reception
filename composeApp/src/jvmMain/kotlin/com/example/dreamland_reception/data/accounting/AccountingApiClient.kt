package com.example.dreamland_reception.data.accounting

import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.net.URI
import java.net.URLEncoder
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Duration

/**
 * Low-level HTTP client for the Humble Ledger API.
 *
 * All public methods are `suspend` and run on [Dispatchers.IO].
 * Token lifecycle (refresh → login fallback) is handled transparently inside
 * [ensureValidToken] — callers never need to manage tokens directly.
 */
internal object AccountingApiClient {

    private val http: HttpClient = HttpClient.newBuilder()
        .connectTimeout(Duration.ofSeconds(15))
        .build()

    private val gson = Gson()

    // Mutex ensures only one coroutine at a time performs a token refresh / login,
    // preventing a thundering herd of concurrent refreshes.
    private val tokenMutex = Mutex()

    // ── Token management ──────────────────────────────────────────────────────

    /**
     * Returns a valid access token, refreshing or re-logging-in as needed.
     * Throws if the credentials are invalid or the server is unreachable.
     */
    suspend fun ensureValidToken(): String = tokenMutex.withLock {
        val cfg = AccountingConfig
        val bufferMs = 60_000L
        val now = System.currentTimeMillis()

        // Fast path: token is still fresh
        if (cfg.accessToken.isNotBlank() && now < cfg.tokenExpiryMs - bufferMs) {
            log("Token cache HIT — expires in ${(cfg.tokenExpiryMs - now) / 1000}s")
            return@withLock cfg.accessToken
        }

        // Try refreshing with the stored refresh token
        if (cfg.refreshToken.isNotBlank()) {
            log("Token expired or missing — attempting refresh")
            runCatching {
                val tokens = refresh(cfg.refreshToken)
                cfg.saveTokens(tokens.accessToken, tokens.refreshToken)
                log("Token refreshed successfully")
                return@withLock tokens.accessToken
            }.onFailure { e ->
                log("Refresh failed (${e.message}) — falling back to full login")
            }
        } else {
            log("No refresh token — performing full login")
        }

        // Full re-login using stored credentials
        log("Logging in as ${cfg.email}")
        val tokens = login(cfg.email, cfg.password)
        cfg.saveTokens(tokens.accessToken, tokens.refreshToken)
        log("Login successful")
        tokens.accessToken
    }

    private fun log(msg: String) {
        println("[AccountingApiClient] $msg")
    }

    // ── Auth endpoints ────────────────────────────────────────────────────────

    suspend fun login(email: String, password: String): TokenData =
        withContext(Dispatchers.IO) {
            val envelope = post<AuthDataEnvelope>(
                path = "/api/v1/auth/login",
                body = LoginRequest(email, password),
                token = null,
            )
            TokenData(
                accessToken = envelope.accessToken ?: envelope.token
                    ?: error("Login response missing accessToken"),
                refreshToken = envelope.refreshToken
                    ?: error("Login response missing refreshToken"),
            )
        }

    suspend fun refresh(refreshToken: String): TokenData =
        withContext(Dispatchers.IO) {
            val envelope = post<AuthDataEnvelope>(
                path = "/api/v1/auth/refresh",
                body = RefreshRequest(refreshToken),
                token = null,
            )
            TokenData(
                accessToken = envelope.accessToken ?: envelope.token
                    ?: error("Refresh response missing accessToken"),
                refreshToken = envelope.refreshToken
                    ?: error("Refresh response missing refreshToken"),
            )
        }

    // ── Customer endpoints ────────────────────────────────────────────────────

    /**
     * Exact phone lookup — the canonical guest identity (Dreamland keys guests by
     * phone). Returns the matching ledger customer or null. Returns null on any
     * non-fatal error rather than throwing.
     */
    suspend fun findCustomerByPhone(token: String, phone: String): CustomerData? =
        withContext(Dispatchers.IO) {
            if (phone.isBlank()) return@withContext null
            val encoded = URLEncoder.encode(phone, "UTF-8")
            val raw = get(path = "/api/v1/customers?phone=$encoded&limit=1", token = token)
            runCatching {
                val type = object : TypeToken<ApiEnvelope<List<CustomerData>>>() {}.type
                val env: ApiEnvelope<List<CustomerData>> = gson.fromJson(raw, type)
                env.data?.firstOrNull()
            }.getOrNull()
        }

    /** Look up a ledger customer (incl. its current balance) by externalId. Null if none. */
    suspend fun findCustomerByExternalId(token: String, externalId: String): CustomerData? =
        withContext(Dispatchers.IO) {
            if (externalId.isBlank()) return@withContext null
            val encoded = URLEncoder.encode(externalId, "UTF-8")
            val raw = get(path = "/api/v1/customers?externalId=$encoded&limit=1", token = token)
            runCatching {
                val type = object : TypeToken<ApiEnvelope<List<CustomerData>>>() {}.type
                val env: ApiEnvelope<List<CustomerData>> = gson.fromJson(raw, type)
                env.data?.firstOrNull()
            }.getOrNull()
        }

    /**
     * Searches for customers whose name contains [name] (server-side substring match).
     * Returns an empty list on any non-fatal error rather than throwing.
     */
    suspend fun searchCustomers(token: String, name: String): List<CustomerData> =
        withContext(Dispatchers.IO) {
            val encoded = URLEncoder.encode(name, "UTF-8")
            val raw = get(path = "/api/v1/customers?search=$encoded&limit=10", token = token)
            runCatching {
                val type = object : TypeToken<ApiEnvelope<List<CustomerData>>>() {}.type
                val env: ApiEnvelope<List<CustomerData>> = gson.fromJson(raw, type)
                env.data ?: emptyList()
            }.getOrDefault(emptyList())
        }

    suspend fun createCustomer(token: String, req: CreateCustomerRequest): CustomerData =
        withContext(Dispatchers.IO) {
            post<CustomerData>(path = "/api/v1/customers", body = req, token = token)
        }

    // ── Accounts ──────────────────────────────────────────────────────────────

    /** Returns all active accounts (up to 500). */
    suspend fun getAccounts(token: String): List<AccountData> =
        withContext(Dispatchers.IO) {
            val raw = get(path = "/api/v1/accounts?limit=500", token = token)
            runCatching {
                val type = object : TypeToken<ApiEnvelope<List<AccountData>>>() {}.type
                val env: ApiEnvelope<List<AccountData>> = gson.fromJson(raw, type)
                env.data ?: emptyList()
            }.getOrDefault(emptyList())
        }

    suspend fun createAccount(token: String, req: CreateAccountRequest): AccountData =
        withContext(Dispatchers.IO) {
            post<AccountData>(path = "/api/v1/accounts", body = req, token = token)
        }

    /** Balance sheet as of [date] — used to read current Cash & Bank balances. */
    suspend fun getBalanceSheet(token: String, date: String): BalanceSheetData? =
        withContext(Dispatchers.IO) {
            val raw = get(path = "/api/v1/reports/balance-sheet?date=$date", token = token)
            runCatching {
                val type = object : TypeToken<ApiEnvelope<BalanceSheetData>>() {}.type
                val env: ApiEnvelope<BalanceSheetData> = gson.fromJson(raw, type)
                env.data
            }.getOrNull()
        }

    // ── Raw double-entry transactions ─────────────────────────────────────────

    /** Posts a raw double-entry journal entry. Used for ADVANCE and ADVANCE_APPLIED types. */
    suspend fun postRawTransaction(token: String, req: RawTransactionRequest) =
        withContext(Dispatchers.IO) {
            post<Map<*, *>>(path = "/api/v1/transactions", body = req, token = token)
            Unit
        }

    // ── Sales & Payments ──────────────────────────────────────────────────────

    suspend fun postSale(token: String, req: PostSaleRequest): SaleResponseData =
        withContext(Dispatchers.IO) {
            post<SaleResponseData>(path = "/api/v1/sales", body = req, token = token)
        }

    suspend fun postPayment(token: String, req: PostPaymentRequest) =
        withContext(Dispatchers.IO) {
            // Response data is not needed; we only care that it succeeded.
            post<Map<*, *>>(path = "/api/v1/payments", body = req, token = token)
            Unit
        }

    // ── Vendors & Purchases (Accounts Payable) ────────────────────────────────

    /** Create-or-return a vendor (idempotent on externalId). Returns the ledger vendor. */
    suspend fun createVendor(token: String, req: CreateVendorRequest): VendorData =
        withContext(Dispatchers.IO) {
            post<VendorData>(path = "/api/v1/vendors", body = req, token = token)
        }

    /** Look up a ledger vendor (incl. its current balance) by externalId. Null if none. */
    suspend fun getVendorByExternalId(token: String, externalId: String): VendorData? =
        withContext(Dispatchers.IO) {
            if (externalId.isBlank()) return@withContext null
            val encoded = URLEncoder.encode(externalId, "UTF-8")
            val raw = get(path = "/api/v1/vendors?externalId=$encoded&limit=1", token = token)
            runCatching {
                val type = object : TypeToken<ApiEnvelope<List<VendorData>>>() {}.type
                val env: ApiEnvelope<List<VendorData>> = gson.fromJson(raw, type)
                env.data?.firstOrNull()
            }.getOrNull()
        }

    /** Record a purchase/bill from a vendor (DR expense / CR vendor AP). */
    suspend fun postPurchase(token: String, req: PostPurchaseRequest) =
        withContext(Dispatchers.IO) {
            post<Map<*, *>>(path = "/api/v1/purchases", body = req, token = token)
            Unit
        }

    /** Pay a vendor (DR vendor AP / CR Cash|Bank). */
    suspend fun postVendorPayment(token: String, req: PostVendorPaymentRequest) =
        withContext(Dispatchers.IO) {
            post<Map<*, *>>(path = "/api/v1/vendor-payments", body = req, token = token)
            Unit
        }

    /** Record a direct expense (DR expense / CR Cash|Bank|Payable). */
    suspend fun postExpense(token: String, req: PostExpenseRequest) =
        withContext(Dispatchers.IO) {
            post<Map<*, *>>(path = "/api/v1/expenses", body = req, token = token)
            Unit
        }

    // ── HTTP primitives ───────────────────────────────────────────────────────

    /**
     * Executes a GET request and returns the raw response body string.
     * Throws [AccountingApiException] on non-2xx status.
     */
    private fun get(path: String, token: String): String {
        val request = HttpRequest.newBuilder()
            .uri(URI.create("${AccountingConfig.baseUrl}$path"))
            .header("Accept", "application/json")
            .header("Authorization", "Bearer $token")
            .GET()
            .timeout(Duration.ofSeconds(20))
            .build()
        val response = http.send(request, HttpResponse.BodyHandlers.ofString())
        checkStatus(response)
        return response.body()
    }

    /**
     * Executes a POST request, deserialises the `data` field of the response envelope into [T],
     * and returns it. Throws [AccountingApiException] on non-2xx status or API-level failure.
     */
    private inline fun <reified T> post(path: String, body: Any, token: String?): T {
        val requestBody = gson.toJson(body)
        val builder = HttpRequest.newBuilder()
            .uri(URI.create("${AccountingConfig.baseUrl}$path"))
            .header("Content-Type", "application/json")
            .header("Accept", "application/json")
            .POST(HttpRequest.BodyPublishers.ofString(requestBody))
            .timeout(Duration.ofSeconds(20))
        if (token != null) builder.header("Authorization", "Bearer $token")

        val response = http.send(builder.build(), HttpResponse.BodyHandlers.ofString())
        checkStatus(response)

        // Parse envelope and extract data
        val type = object : TypeToken<ApiEnvelope<T>>() {}.type
        val envelope: ApiEnvelope<T> = gson.fromJson(response.body(), type)
        if (!envelope.success) {
            throw AccountingApiException(
                statusCode = response.statusCode(),
                apiCode = envelope.code ?: "UNKNOWN",
                apiMessage = envelope.error ?: "API returned success=false",
            )
        }
        return envelope.data ?: error("API returned success=true but data was null (path=$path)")
    }

    private fun checkStatus(response: HttpResponse<String>) {
        if (response.statusCode() !in 200..299) {
            // Try to extract a human-readable error from the response body
            val errMsg = runCatching {
                val env: ApiEnvelope<Any> = gson.fromJson(response.body(), object : TypeToken<ApiEnvelope<Any>>() {}.type)
                env.error ?: response.body()
            }.getOrDefault(response.body())
            throw AccountingApiException(
                statusCode = response.statusCode(),
                apiCode = "HTTP_${response.statusCode()}",
                apiMessage = errMsg,
            )
        }
    }
}

/** Thrown when the Humble Ledger API returns a non-2xx status or `success = false`. */
class AccountingApiException(
    val statusCode: Int,
    val apiCode: String,
    apiMessage: String,
) : Exception("[$statusCode / $apiCode] $apiMessage")
