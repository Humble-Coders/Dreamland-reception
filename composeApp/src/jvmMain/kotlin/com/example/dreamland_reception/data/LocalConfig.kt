package com.example.dreamland_reception.data

import java.util.prefs.Preferences

object LocalConfig {
    private val prefs = Preferences.userRoot().node("dreamland_reception")

    var senderEmail: String
        get() = prefs.get("senderEmail", "")
        set(v) { prefs.put("senderEmail", v) }

    var resendApiKey: String
        get() = prefs.get("resendApiKey", "")
        set(v) { prefs.put("resendApiKey", v) }
}
