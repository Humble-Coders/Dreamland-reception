package com.example.dreamland_reception

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.tooling.preview.Preview
import com.example.dreamland_reception.ui.splash.SplashScreenDesktop

@Composable
@Preview
fun App() {
    DreamlandTheme {
        var showSplash by remember { mutableStateOf(true) }
        if (showSplash) {
            SplashScreenDesktop(onFinished = { showSplash = false })
        } else {
            DreamlandShell()
        }
    }
}
