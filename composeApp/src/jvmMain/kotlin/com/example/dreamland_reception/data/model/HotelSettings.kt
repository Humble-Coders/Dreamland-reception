package com.example.dreamland_reception.data.model

/** Mirrors the `settings/{hotelId}` Firestore document. */
data class HotelSettings(
    val hotelId: String = "",
    val hotelName: String = "",
    val currency: String = "INR",
    val contactInfo: String = "",

    // Billing
    val taxPercentage: Double = 18.0,
    val taxEnabled: Boolean = true,
    val defaultDiscountType: String = "PERCENTAGE",  // PERCENTAGE | FLAT
    val defaultDiscountValue: Double = 0.0,

    // Room rules
    val checkInTime: String = "12:00",
    val checkOutTime: String = "11:00",
    val autoAssignRoom: Boolean = true,

    // Room options
    val breakfastEnabled: Boolean = true,
    val breakfastPricePerPerson: Double = 200.0,
    val extraBedEnabled: Boolean = false,
    val extraBedPrice: Double = 500.0,
    val earlyCheckInEnabled: Boolean = false,
    val earlyCheckInCharge: Double = 300.0,
    val lateCheckOutEnabled: Boolean = false,
    val lateCheckOutCharge: Double = 400.0,
)
