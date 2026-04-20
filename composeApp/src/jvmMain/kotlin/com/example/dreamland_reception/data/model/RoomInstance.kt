package com.example.dreamland_reception.data.model

import java.util.Date

data class RoomInstance(
    val id: String = "",
    val hotelId: String = "",
    val categoryId: String = "",
    val categoryName: String = "",
    val roomNumber: String = "",
    val status: String = "AVAILABLE",  // AVAILABLE | ASSIGNED | OCCUPIED | CLEANING | MAINTENANCE
    val currentStayId: String? = null,
    val createdAt: Date = Date(),
)
