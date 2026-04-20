package com.example.dreamland_reception.data.model

import java.util.Date

data class Booking(
    val id: String = "",
    val hotelId: String = "",
    val hotelName: String = "",
    val userId: String = "",
    val userName: String = "",
    // Guest info (stored in Firestore as guestDetails map)
    val guestName: String = "",
    val guestPhone: String = "",
    // Room
    val roomCategoryId: String = "",
    val roomCategoryName: String = "",
    val roomInstanceId: String = "",
    val roomNumber: String = "",
    // Dates (stored in Firestore as checkInDate / checkOutDate)
    val checkIn: Date = Date(),
    val checkOut: Date = Date(),
    // Guests (stored in Firestore as guests map)
    val adults: Int = 1,
    val children: Int = 0,
    // Options (stored in Firestore as options map)
    val breakfastIncluded: Boolean = false,
    val breakfastPricePerDay: Double = 0.0,
    val earlyCheckInEnabled: Boolean = false,
    val earlyCheckInCharge: Double = 0.0,
    val lateCheckOutEnabled: Boolean = false,
    val lateCheckOutCharge: Double = 0.0,
    val status: String = "CONFIRMED",       // CONFIRMED | COMPLETED | CANCELLED | NO_SHOW
    val source: String = "APP",             // APP | WALK_IN
    val totalAmount: Double = 0.0,
    val advancePaidAmount: Double = 0.0,
    val notes: String = "",
    val createdAt: Date = Date(),
)
