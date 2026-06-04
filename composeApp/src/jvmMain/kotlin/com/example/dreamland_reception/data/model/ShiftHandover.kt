package com.example.dreamland_reception.data.model

import java.util.Date

/**
 * A desk handover from one manager to another, stored in `shiftHandovers`. Captures
 * the Cash & Bank balances at the moment of handover so the desk can be reconciled
 * shift-by-shift. fromManager is blank for the first shift start of the device.
 */
data class ShiftHandover(
    val id: String = "",
    val hotelId: String = "",
    val fromManager: String = "",
    val toManager: String = "",
    val cashAtHandover: Double? = null,
    val bankAtHandover: Double? = null,
    val at: Date = Date(),
)
