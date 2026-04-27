package com.example.dreamland_reception.data.model

data class SeasonalPricing(
    val label: String = "",
    val from: String = "",   // "MM-DD"
    val to: String = "",     // "MM-DD"
    val price: Double = 0.0,
)

data class Room(
    val id: String = "",
    val hotelId: String = "",
    val number: String = "",
    val type: String = "",
    val floor: Int = 1,
    val capacity: Int = 2,
    val pricePerNight: Double = 0.0,
    val breakfastPrice: Double = 0.0,
    val status: String = "available",
    val available: Boolean = true,
    val amenities: List<String> = emptyList(),
    val description: String = "",
    val seasonalPricing: List<SeasonalPricing> = emptyList(),
)
