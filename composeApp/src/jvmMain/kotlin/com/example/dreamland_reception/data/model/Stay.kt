package com.example.dreamland_reception.data.model

import java.util.Date

data class Stay(
    val id: String = "",
    val hotelId: String = "",
    val bookingId: String = "",
    val guestName: String = "",
    val guestPhone: String = "",
    val roomInstanceId: String = "",
    val roomNumber: String = "",
    val roomCategoryId: String = "",
    val roomCategoryName: String = "",
    val checkInActual: Date = Date(),
    val expectedCheckOut: Date = Date(),
    val checkOutActual: Date? = null,
    val status: String = "ACTIVE",   // ACTIVE | COMPLETED
    val adults: Int = 1,
    val children: Int = 0,
    val breakfast: Boolean = false,
    val extraBed: Boolean = false,
    val earlyCheckIn: Boolean = false,
    val lateCheckOut: Boolean = false,
    val totalBilled: Double = 0.0,
    val specialRequests: String = "",
    val createdAt: Date = Date(),
)
