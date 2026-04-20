package com.example.dreamland_reception.data.model

data class Room(
    val id: String = "",
    val hotelId: String = "",
    val number: String = "",
    val type: String = "",         // deluxe | suite | standard | villa
    val floor: Int = 1,
    val capacity: Int = 2,
    val pricePerNight: Double = 0.0,
    val breakfastPrice: Double = 0.0,   // per person per night; 0 = not offered
    val status: String = "available",   // available | occupied | maintenance | reserved
    val amenities: List<String> = emptyList(),
    val description: String = "",
)
