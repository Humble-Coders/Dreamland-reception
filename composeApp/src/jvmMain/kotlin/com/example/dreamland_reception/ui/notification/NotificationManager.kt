package com.example.dreamland_reception.ui.notification

import java.awt.SystemTray
import java.awt.Toolkit
import java.awt.TrayIcon
import java.awt.image.BufferedImage
import javax.sound.sampled.AudioSystem

object NotificationManager {

    private var trayIcon: TrayIcon? = null

    fun playSound() {
        try {
            val stream = NotificationManager::class.java
                .getResourceAsStream("/sounds/notification.wav") ?: return
            val audioInput = AudioSystem.getAudioInputStream(stream.buffered())
            val clip = AudioSystem.getClip()
            clip.open(audioInput)
            clip.start()
        } catch (_: Exception) {
            // No audio device or file missing — degrade gracefully
        }
    }

    fun showTray(title: String, message: String) {
        try {
            if (!SystemTray.isSupported()) return
            val tray = SystemTray.getSystemTray()
            if (trayIcon == null) {
                // Simple gold-circle icon — no external file needed
                val img = BufferedImage(32, 32, BufferedImage.TYPE_INT_ARGB).apply {
                    val g = createGraphics()
                    g.color = java.awt.Color(0xD4, 0xAF, 0x37)   // DreamlandGold
                    g.fillOval(0, 0, 32, 32)
                    g.dispose()
                }
                trayIcon = TrayIcon(img, "Dreamland Reception").also {
                    it.isImageAutoSize = true
                    tray.add(it)
                }
            }
            trayIcon?.displayMessage(title, message, TrayIcon.MessageType.INFO)
        } catch (_: Exception) {
            // System tray unavailable on this platform — degrade gracefully
        }
    }
}
