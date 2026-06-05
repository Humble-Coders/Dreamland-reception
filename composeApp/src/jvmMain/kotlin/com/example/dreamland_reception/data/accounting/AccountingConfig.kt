package com.example.dreamland_reception.data.accounting

import java.io.File

/**
 * Persists Humble Ledger credentials and JWT tokens to `~/.dreamland/accounting.json`.
 *
 * Self-initialises on first access via `init { load() }`.
 * Call [saveTokens] after every successful login or token refresh.
 *
 * The file can be created / edited by hand:
 * ```json
 * {
 *   "baseUrl": "http://68.183.86.89/api-server",
 *   "email": "your@email.com",
 *   "password": "yourpassword",
 *   "accessToken": "",
 *   "refreshToken": "",
 *   "tokenExpiryMs": 0
 * }
 * ```
 */
object AccountingConfig {

    // Built-in defaults so a fresh install (e.g. a new PC via MSI) logs into
    // accounting automatically with no manual setup. A `~/.dreamland/accounting.json`
    // file always overrides these. Tokens auto-refresh and re-login on expiry, so the
    // accounting connection never silently logs out as long as these stay valid.
    private const val DEFAULT_BASE_URL = "http://68.183.86.89/api-server"
    private const val DEFAULT_EMAIL = "dreamland@gmail.com"
    private const val DEFAULT_PASSWORD = "114198"

    private val configFile: File by lazy {
        val dir = File(System.getProperty("user.home"), ".dreamland")
        dir.mkdirs()
        File(dir, "accounting.json")
    }

    var baseUrl: String = DEFAULT_BASE_URL
        private set
    var email: String = ""
        private set
    var password: String = ""
        private set
    var accessToken: String = ""
        private set
    var refreshToken: String = ""
        private set
    var tokenExpiryMs: Long = 0L
        private set

    init {
        load()
    }

    /** Returns true only when both email and password are present — a prerequisite for any API call. */
    fun isConfigured(): Boolean = email.isNotBlank() && password.isNotBlank()

    /**
     * Stores a fresh token pair and persists to disk.
     * [expiryMs] defaults to 14 minutes from now (the actual TTL is 15 min; 1 min buffer).
     */
    fun saveTokens(
        access: String,
        refresh: String,
        expiryMs: Long = System.currentTimeMillis() + 14 * 60 * 1_000L,
    ) {
        accessToken = access
        refreshToken = refresh
        tokenExpiryMs = expiryMs
        write()
    }

    // ── Internal ──────────────────────────────────────────────────────────────

    fun load() {
        if (configFile.exists()) {
            runCatching {
                val text = configFile.readText()
                baseUrl = parseString(text, "baseUrl") ?: baseUrl
                email = parseString(text, "email") ?: ""
                password = parseString(text, "password") ?: ""
                accessToken = parseString(text, "accessToken") ?: ""
                refreshToken = parseString(text, "refreshToken") ?: ""
                tokenExpiryMs = parseLong(text, "tokenExpiryMs") ?: 0L
            }
        }
        // Seed built-in defaults when the file is absent or missing fields, so the app
        // is configured out-of-the-box on a fresh machine. The file (if present) wins.
        if (baseUrl.isBlank()) baseUrl = DEFAULT_BASE_URL
        if (email.isBlank()) email = DEFAULT_EMAIL
        if (password.isBlank()) password = DEFAULT_PASSWORD
    }

    private fun write() {
        runCatching {
            configFile.writeText(
                "{\n" +
                "  \"baseUrl\": \"${baseUrl.esc()}\",\n" +
                "  \"email\": \"${email.esc()}\",\n" +
                "  \"password\": \"${password.esc()}\",\n" +
                "  \"accessToken\": \"${accessToken.esc()}\",\n" +
                "  \"refreshToken\": \"${refreshToken.esc()}\",\n" +
                "  \"tokenExpiryMs\": $tokenExpiryMs\n" +
                "}\n"
            )
        }
    }

    private fun parseString(json: String, key: String): String? =
        Regex("\"${Regex.escape(key)}\"\\s*:\\s*\"((?:[^\"\\\\]|\\\\.)*)\"")
            .find(json)?.groupValues?.get(1)?.unesc()

    private fun parseLong(json: String, key: String): Long? =
        Regex("\"${Regex.escape(key)}\"\\s*:\\s*(\\d+)")
            .find(json)?.groupValues?.get(1)?.toLongOrNull()

    private fun String.esc() = replace("\\", "\\\\").replace("\"", "\\\"")
    private fun String.unesc() = replace("\\\"", "\"").replace("\\\\", "\\")
}
