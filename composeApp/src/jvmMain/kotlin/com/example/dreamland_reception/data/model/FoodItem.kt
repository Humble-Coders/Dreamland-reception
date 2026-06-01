package com.example.dreamland_reception.data.model

import java.util.Date

data class FoodItem(
    val id: String = "",
    val hotelId: String = "",
    val name: String = "",
    val price: Double = 0.0,
    val category: String = "",
    val taxPercentage: Double = 0.0,
    val isAvailable: Boolean = true,
    val createdAt: Date = Date(),
)
