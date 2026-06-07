package com.example.dreamland_reception.data.firebase

import com.google.auth.oauth2.IdTokenCredentials
import com.google.auth.oauth2.IdTokenProvider
import com.google.gson.JsonParser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Duration

/**
 * Calls the reception cancel + refund Cloud Function (HTTPS sibling) from the
 * JVM reception desktop app.
 *
 * Auth strategy — uses the **same** Firebase Admin SDK service-account key the
 * reception app already loads for Firestore (FirebaseManager.serviceAccountCredentials).
 * No extra setup, no Web API Key, no custom claims, no env vars:
 *
 *   1. Mint a Google-signed ID token with audience = function URL.
 *   2. POST to the HTTPS sibling with `Authorization: Bearer <idToken>`.
 *   3. Server verifies the token, checks the verified email ends with
 *      `@<projectId>.iam.gserviceaccount.com` (any SA inside this Firebase
 *      project — automatic, no allowlist to maintain).
 *
 * The ID token is cached in-memory until ~5 minutes before its 1-hour expiry,
 * so the typical call doesn't pay the mint cost.
 */
object CloudFunctionClient {

    private const val FUNCTION_URL =
        "https://asia-south1-dreamland-baba0.cloudfunctions.net/cancelBookingByReceptionHttp"

    private val http: HttpClient by lazy {
        HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build()
    }

    // ── Token cache ───────────────────────────────────────────────────────
    @Volatile private var cachedToken: String? = null
    @Volatile private var cachedExpiryMs: Long = 0L

    /**
     * POSTs [bodyJson] to cancelBookingByReceptionHttp and returns the
     * response body on 2xx. Throws [CancelByReceptionException] on any non-2xx,
     * preserving the server's `{code, message}` JSON for the ViewModel.
     */
    suspend fun callCancelByReception(bodyJson: String): String = withContext(Dispatchers.IO) {
        val token = idToken()
        val req = HttpRequest.newBuilder()
            .uri(URI.create(FUNCTION_URL))
            .timeout(Duration.ofSeconds(60))
            .header("Authorization", "Bearer $token")
            .header("Content-Type", "application/json")
            .POST(HttpRequest.BodyPublishers.ofString(bodyJson))
            .build()
        val res = http.send(req, HttpResponse.BodyHandlers.ofString())
        val body = res.body() ?: ""
        if (res.statusCode() !in 200..299) {
            throw CancelByReceptionException(res.statusCode(), body)
        }
        body
    }

    private fun idToken(): String {
        val now = System.currentTimeMillis()
        cachedToken?.let { if (now < cachedExpiryMs) return it }

        val source = FirebaseManager.serviceAccountCredentials()
        require(source is IdTokenProvider) {
            "Loaded credentials cannot mint ID tokens — service-account JSON required."
        }
        val creds = IdTokenCredentials.newBuilder()
            .setIdTokenProvider(source)
            .setTargetAudience(FUNCTION_URL)
            .build()
        creds.refreshIfExpired()
        val token = creds.idToken.tokenValue

        cachedToken = token
        cachedExpiryMs = now + Duration.ofMinutes(55).toMillis()
        return token
    }

    /**
     * Optional sanity check for response parsing — keeps gson on the class path
     * even if the rest of the file changes. (Used implicitly by BookingRepository.)
     */
    @Suppress("unused")
    internal fun parseAny(json: String) = JsonParser.parseString(json)

    /**
     * Thrown when the function returns a non-2xx status. [body] is the raw
     * response body — typically `{ "code": "...", "message": "..." }`.
     * The ViewModel maps [httpStatus] + parsed code into a user-friendly error.
     */
    class CancelByReceptionException(
        val httpStatus: Int,
        val body: String,
    ) : RuntimeException("cancelBookingByReceptionHttp returned $httpStatus: $body")
}
