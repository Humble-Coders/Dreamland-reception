package com.example.dreamland_reception.data.model

import java.util.Date

data class GuestRecord(
    val name: String = "",
    val phone: String = "",
    val idProofVerified: Boolean = false,
    val gender: String = "",
    val govIdNumber: String = "",
    val govIdPictures: List<String> = emptyList(),   // URLs of the scanned ID images
    val address: String = "",
    val dob: String = "",
    val age: Int = 0,
    val grcNumber: String = "",                      // GRC serial issued for this guest (blank if not printed)
)

data class Stay(
    val id: String = "",
    val hotelId: String = "",
    val bookingId: String = "",
    val userId: String = "",
    val userName: String = "",
    val guestName: String = "",
    val guestPhone: String = "",
    val roomInstanceId: String = "",
    val roomNumber: String = "",
    val roomCategoryId: String = "",
    val roomCategoryName: String = "",
    val checkInActual: Date = Date(),            // normalized check-in date (date-only, for night math)
    val trueCheckIn: Date? = null,               // actual system time when "Check-in Guest" was clicked
    val expectedCheckOut: Date = Date(),
    val checkOutActual: Date? = null,
    val status: String = "ACTIVE",   // ACTIVE | COMPLETED
    val adults: Int = 1,
    val children: Int = 0,
    val breakfast: Boolean = false,
    val extraBed: Boolean = false,
    val earlyCheckIn: Boolean = false,
    val lateCheckOut: Boolean = false,
    val earlyCheckInCharge: Double = 0.0,
    val lateCheckOutCharge: Double = 0.0,
    val advancePaidAmount: Double = 0.0,
    val totalBilled: Double = 0.0,
    val createdAt: Date = Date(),
    val updatedAt: Date = Date(),
    val guests: List<GuestRecord> = emptyList(),
    val groupStayId: String = "",
)
