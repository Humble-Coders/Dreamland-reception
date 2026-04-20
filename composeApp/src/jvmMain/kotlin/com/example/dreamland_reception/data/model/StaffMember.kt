package com.example.dreamland_reception.data.model

import java.util.Date

data class StaffMember(
    val id: String = "",
    val hotelId: String = "",
    val name: String = "",
    val email: String = "",
    val phone: String = "",
    val role: String = "",            // HOUSEKEEPING | MAINTENANCE | RECEPTION
    val department: String = "",
    val shift: String = "",           // morning | afternoon | night
    val joiningDate: Date = Date(),
    val isActive: Boolean = true,
    val isAvailable: Boolean = true,  // true = Available, false = Busy
    val salary: Double = 0.0,
)
