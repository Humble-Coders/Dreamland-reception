package com.example.dreamland_reception.data.model

import java.util.Date

data class ComplaintType(
    val id: String = "",
    val hotelId: String = "",
    val name: String = "",
    val description: String = "",
    val isActive: Boolean = true,
    val createdAt: Date = Date(),
)
