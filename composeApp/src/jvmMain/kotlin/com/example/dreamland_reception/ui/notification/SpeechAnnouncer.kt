package com.example.dreamland_reception.ui.notification

import java.util.concurrent.Executors

/**
 * Speaks short notifications aloud through the OS text-to-speech engine
 * (macOS `say`, Windows `System.Speech`, Linux `spd-say`).
 *
 * All speech runs on a single background worker so announcements are spoken one after another
 * and never overlap, and the UI thread is never blocked. Every failure (no TTS engine, missing
 * command, no audio device) degrades silently — speech is a nicety, never a hard dependency.
 */
object SpeechAnnouncer {

    private val osName = System.getProperty("os.name")?.lowercase().orEmpty()

    // One daemon worker → announcements are serialized; the JVM can still exit freely.
    private val worker = Executors.newSingleThreadExecutor { r ->
        Thread(r, "speech-announcer").apply { isDaemon = true }
    }

    /** Queues [text] to be spoken aloud [times] times (default twice). No-op for blank text. */
    fun announce(text: String, times: Int = 2) {
        val clean = text.trim()
        if (clean.isEmpty()) return
        val repeats = times.coerceIn(1, 5)
        runCatching {
            worker.execute {
                repeat(repeats) { i ->
                    runCatching { speakBlocking(clean) }
                    if (i < repeats - 1) runCatching { Thread.sleep(250) }  // small gap between repeats
                }
            }
        }
    }

    private fun speakBlocking(text: String) {
        // A packaged macOS app launched from Finder inherits a stripped PATH, so a bare command
        // name (e.g. "say") can't be resolved. Prefer absolute paths; fall back to the bare name.
        val candidates: List<List<String>> = when {
            osName.contains("mac") || osName.contains("darwin") -> listOf(
                listOf("/usr/bin/say", text),
                listOf("say", text),
            )
            osName.contains("win") -> {
                val script = "Add-Type -AssemblyName System.Speech; " +
                    "(New-Object System.Speech.Synthesis.SpeechSynthesizer).Speak('${text.replace("'", "''")}')"
                listOf(listOf("powershell", "-NoProfile", "-Command", script))
            }
            else -> listOf(  // Linux: try speech-dispatcher, then espeak
                listOf("spd-say", "--wait", text),
                listOf("espeak", text),
            )
        }
        for (command in candidates) {
            val ok = runCatching {
                ProcessBuilder(command).redirectErrorStream(true).start().waitFor() == 0
            }.getOrDefault(false)
            if (ok) return
        }
    }
}
