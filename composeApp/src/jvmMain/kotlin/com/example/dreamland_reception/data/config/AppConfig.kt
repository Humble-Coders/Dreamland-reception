package com.example.dreamland_reception.data.config

import java.io.File

/**
 * Thin config-file layer that persists app preferences to `~/.dreamland/config.json`.
 *
 * Call [load] once at startup (before any ViewModel initialises).
 * Call [saveHotelSelection] whenever the user picks a hotel.
 * The file is plain JSON so it can be inspected / edited by hand if needed.
 */
object AppConfig {

    private val configFile: File by lazy {
        val dir = File(System.getProperty("user.home"), ".dreamland")
        dir.mkdirs()
        File(dir, "config.json")
    }

    // In-memory cache — populated by [load]
    var selectedHotelId: String = ""
        private set

    var selectedHotelName: String = ""
        private set

    // ── Public API ────────────────────────────────────────────────────────────

    /** Read config from disk. Call once at app startup before any ViewModel initialises. */
    fun load() {
        if (!configFile.exists()) return
        runCatching {
            val text = configFile.readText()
            selectedHotelId = parseString(text, "selectedHotelId") ?: ""
            selectedHotelName = parseString(text, "selectedHotelName") ?: ""
        }
    }

    /** Persist a new hotel selection immediately. */
    fun saveHotelSelection(id: String, name: String) {
        selectedHotelId = id
        selectedHotelName = name
        write()
    }

    /** Clear the hotel selection (e.g. hotel deleted). */
    fun clearHotelSelection() {
        selectedHotelId = ""
        selectedHotelName = ""
        write()
    }

    // ── Internal ──────────────────────────────────────────────────────────────

    private fun write() {
        runCatching {
            configFile.writeText(
                "{\n" +
                "  \"selectedHotelId\": \"${selectedHotelId.esc()}\",\n" +
                "  \"selectedHotelName\": \"${selectedHotelName.esc()}\"\n" +
                "}\n"
            )
        }
    }

    /** Minimal JSON string extractor — avoids pulling in a JSON library for two fields. */
    private fun parseString(json: String, key: String): String? =
        Regex("\"${Regex.escape(key)}\"\\s*:\\s*\"((?:[^\"\\\\]|\\\\.)*)\"")
            .find(json)?.groupValues?.get(1)?.unesc()

    private fun String.esc() = replace("\\", "\\\\").replace("\"", "\\\"")
    private fun String.unesc() = replace("\\\"", "\"").replace("\\\\", "\\")
}
