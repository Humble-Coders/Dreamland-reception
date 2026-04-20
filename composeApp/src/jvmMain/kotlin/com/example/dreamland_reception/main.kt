package com.example.dreamland_reception

import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import com.example.dreamland_reception.data.AppContext
import com.example.dreamland_reception.data.config.AppConfig

fun main() {
    // Load persisted config (selected hotel, etc.) before any ViewModel initialises.
    AppConfig.load()
    AppContext.syncFromConfig()

    DreamlandAppInitializer.initialize()

    application {
        Window(
            onCloseRequest = ::exitApplication,
            title = "Dreamland Reception",
            state = rememberWindowState(width = 1280.dp, height = 800.dp),
        ) {
            App()
        }
    }
}
