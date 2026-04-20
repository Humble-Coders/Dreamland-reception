package com.example.dreamland_reception.data.model

/**
 * Mirrors the `hotels/{id}` Firestore document.
 * All hotel configuration lives here — general info, billing rules, room options.
 * Missing fields in Firestore get the defaults declared below.
 */
data class Hotel(
    val id: String = "",

    // ── General ───────────────────────────────────────────────────────────────
    val name: String = "",
    val address: String = "",
    val city: String = "",
    val country: String = "",
    val contactPhone: String = "",
    val currency: String = "INR",
    val contactInfo: String = "",
    val isActive: Boolean = true,

    // ── Billing ───────────────────────────────────────────────────────────────
    val taxEnabled: Boolean = true,
    val taxPercentage: Double = 18.0,
    val defaultDiscountType: String = "PERCENTAGE",   // PERCENTAGE | FLAT
    val defaultDiscountValue: Double = 0.0,

    // ── Room rules ────────────────────────────────────────────────────────────
    val checkInTime: String = "12:00",
    val checkOutTime: String = "11:00",
    val autoAssignRoom: Boolean = true,

    // ── Room options ──────────────────────────────────────────────────────────
    val breakfastEnabled: Boolean = true,
    val breakfastPricePerPerson: Double = 200.0,
    val earlyCheckInAllowed: Boolean = false,
    val earlyCheckInPrice: Double = 300.0,
    val lateCheckOutAllowed: Boolean = false,
    val lateCheckOutPrice: Double = 200.0,
)
