package com.example.dreamland_reception.data.firebase

import com.google.auth.oauth2.GoogleCredentials
import com.google.cloud.firestore.Firestore
import com.google.firebase.FirebaseApp
import com.google.firebase.FirebaseOptions
import com.google.firebase.cloud.FirestoreClient
import java.io.File
import java.nio.file.Paths

/**
 * Firebase Admin SDK + Firestore for the JVM (desktop) target.
 *
 * Credential resolution order (first match wins):
 * 1. [initialize] `explicitPath` argument
 * 2. `DREAMLAND_FIREBASE_CREDENTIALS` — path to service account JSON
 * 3. `~/.dreamland/service-account.json`
 * 4. `GOOGLE_APPLICATION_CREDENTIALS` — path to JSON (standard Google env)
 * 5. `{user.dir}/firebase-credentials.json` (same convention as Gagan Jewellers desktop `Main.kt`)
 * 6. `{user.dir}/composeApp/firebase/service-account.json`
 * 7. `{user.dir}/composeApp/firebase/firebase-credentials.json`
 *
 * **Project ID** is read from the JSON when a file is used. If only application-default
 * credentials apply, set `GOOGLE_CLOUD_PROJECT` or a fallback default is used.
 *
 * Download keys: Firebase Console → Project settings → Service accounts → Generate new private key.
 */
object FirebaseManager {

    private const val DEFAULT_PROJECT_ID = "dreamland-baba0"

    val legacyHomeCredentialsFile: File = File(
        System.getProperty("user.home"),
        ".dreamland/service-account.json",
    )

    /** @see legacyHomeCredentialsFile */
    val serviceAccountFile: File get() = legacyHomeCredentialsFile

    var initError: String? = null
        private set

    val isInitialized: Boolean
        get() = FirebaseApp.getApps().isNotEmpty()

    val isConnected: Boolean
        get() = isInitialized && initError == null

    fun initialize(explicitPath: String? = null) {
        if (isInitialized) return
        initError = null

        runCatching {
            val (credentials, projectId) = resolveCredentials(explicitPath)
            FirebaseApp.initializeApp(
                FirebaseOptions.builder()
                    .setCredentials(credentials)
                    .setProjectId(projectId)
                    .build(),
            )
        }.onFailure { e ->
            initError = e.message ?: e.toString()
        }
    }

    fun firestore(): Firestore? {
        if (!isInitialized) return null
        return runCatching { FirestoreClient.getFirestore() }.getOrNull()
    }

    fun requireFirestore(): Firestore = firestore()
        ?: error(
            initError ?: "Firebase not connected.\n\n" + credentialHelpText(),
        )

    private fun credentialHelpText(): String = buildString {
        appendLine("NOTE: google-services.json is the Android client config — it cannot be used here.")
        appendLine("You need a SERVICE ACCOUNT key JSON:")
        appendLine("  Firebase Console → Project Settings → Service Accounts → Generate new private key")
        appendLine()
        appendLine("Place the downloaded file at one of:")
        appendLine("  • composeApp/firebase/service-account.json")
        appendLine("  • composeApp/firebase/firebase-credentials.json")
        appendLine("  • Project root: firebase-credentials.json")
        appendLine("  • ${legacyHomeCredentialsFile.absolutePath}")
        appendLine("Or set DREAMLAND_FIREBASE_CREDENTIALS or GOOGLE_APPLICATION_CREDENTIALS.")
    }

    private fun resolveCredentials(explicitPath: String?): Pair<GoogleCredentials, String> {
        val tried = mutableListOf<String>()

        fun tryFile(path: String): Pair<GoogleCredentials, String>? {
            val f = File(path)
            tried.add(f.absolutePath)
            if (!f.isFile) return null
            val text = f.readText()
            // Skip Android google-services.json — it has no private_key and cannot be used with Admin SDK
            if (text.contains("\"project_info\"") && !text.contains("\"private_key\"")) return null
            val projectId = runCatching { extractProjectId(text) }.getOrElse { DEFAULT_PROJECT_ID }
            val creds = runCatching { GoogleCredentials.fromStream(f.inputStream()) }.getOrNull() ?: return null
            return creds to projectId
        }

        if (explicitPath != null) {
            return tryFile(explicitPath)
                ?: throw IllegalArgumentException("Firebase credentials file not found at: $explicitPath")
        }

        System.getenv("DREAMLAND_FIREBASE_CREDENTIALS")?.let { env ->
            return tryFile(env)
                ?: throw IllegalArgumentException("DREAMLAND_FIREBASE_CREDENTIALS file not found: $env")
        }

        tryFile(legacyHomeCredentialsFile.absolutePath)?.let { return it }

        System.getenv("GOOGLE_APPLICATION_CREDENTIALS")?.let { tryFile(it) }?.let { return it }

        val userDir = System.getProperty("user.dir") ?: "."
        // user.dir = project root (Gradle run)
        tryFile(Paths.get(userDir, "firebase-credentials.json").toString())?.let { return it }
        tryFile(Paths.get(userDir, "composeApp", "firebase", "service-account.json").toString())?.let { return it }
        tryFile(Paths.get(userDir, "composeApp", "firebase", "firebase-credentials.json").toString())?.let { return it }
        tryFile(Paths.get(userDir, "composeApp", "firebase", "google-services.json").toString())?.let { return it }
        // user.dir = composeApp/ (IDE run configuration)
        tryFile(Paths.get(userDir, "firebase", "service-account.json").toString())?.let { return it }
        tryFile(Paths.get(userDir, "firebase", "firebase-credentials.json").toString())?.let { return it }
        // Absolute known path (always works regardless of working directory)
        tryFile("/Users/sharnyagoel/AndroidStudioProjects/Dreamlandreception/composeApp/firebase/service-account.json")?.let { return it }

        val creds = runCatching { GoogleCredentials.getApplicationDefault() }.getOrElse { cause ->
            throw IllegalArgumentException(
                "No valid Firebase credentials found. Checked paths:\n" +
                    tried.joinToString("\n") { "  • $it" } +
                    "\n\n" + credentialHelpText(),
                cause,
            )
        }
        val projectId = System.getenv("GOOGLE_CLOUD_PROJECT")?.takeIf { it.isNotBlank() }
            ?: DEFAULT_PROJECT_ID
        return creds to projectId
    }

    private fun extractProjectId(jsonContent: String): String {
        // Service account JSON: top-level "project_id"
        val topLevel = """"project_id"\s*:\s*"([^"]+)"""".toRegex().find(jsonContent)
        if (topLevel != null) return topLevel.groupValues[1]
        // google-services.json: nested under "project_info" → "project_id"
        val nested = """"project_info"[^}]*"project_id"\s*:\s*"([^"]+)"""".toRegex(RegexOption.DOT_MATCHES_ALL).find(jsonContent)
        if (nested != null) return nested.groupValues[1]
        return DEFAULT_PROJECT_ID
    }
}
