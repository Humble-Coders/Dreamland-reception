package com.example.dreamland_reception.data.model

import java.util.Date

data class Service(
    val id: String = "",
    val hotelId: String = "",
    val name: String = "",
    val price: Double = 0.0,
    val isActive: Boolean = true,
    val createdAt: Date = Date(),
)
