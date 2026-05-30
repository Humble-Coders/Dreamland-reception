package com.example.dreamland_reception.ui.notification

import javax.sound.sampled.AudioFormat
import javax.sound.sampled.AudioSystem
import javax.sound.sampled.DataLine
import javax.sound.sampled.SourceDataLine
import kotlin.math.PI
import kotlin.math.sin

object NotificationManager {

    fun playSound() {
        try {
            val sampleRate = 44100f
            val format = AudioFormat(sampleRate, 16, 1, true, false)
            val info = DataLine.Info(SourceDataLine::class.java, format)
            val line = AudioSystem.getLine(info) as SourceDataLine
            line.open(format)
            line.start()

            // Two-tone ascending chime: 880 Hz → 1100 Hz
            writeTone(line, sampleRate, frequency = 880.0, durationMs = 120, amplitude = 0.45)
            writeTone(line, sampleRate, frequency = 1100.0, durationMs = 180, amplitude = 0.35)

            line.drain()
            line.close()
        } catch (_: Exception) {
            // No audio device available — degrade gracefully
        }
    }

    private fun writeTone(
        line: SourceDataLine,
        sampleRate: Float,
        frequency: Double,
        durationMs: Int,
        amplitude: Double,
    ) {
        val samples = (sampleRate * durationMs / 1000).toInt()
        val buffer = ByteArray(samples * 2)
        for (i in 0 until samples) {
            // Fade out over last 20% of the tone to avoid clicks
            val fade = if (i > samples * 0.8) 1.0 - (i - samples * 0.8) / (samples * 0.2) else 1.0
            val value = (sin(2.0 * PI * i * frequency / sampleRate) * amplitude * fade * Short.MAX_VALUE).toInt().toShort()
            buffer[i * 2]     = (value.toInt() and 0xff).toByte()
            buffer[i * 2 + 1] = (value.toInt() shr 8 and 0xff).toByte()
        }
        line.write(buffer, 0, buffer.size)
    }
}
