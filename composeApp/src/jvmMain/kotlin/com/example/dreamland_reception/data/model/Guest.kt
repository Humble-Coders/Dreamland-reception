package com.example.dreamland_reception.data.model

import java.util.Date

data class Guest(
    val id: String = "",
    val name: String = "",
    val email: String = "",
    val phone: String = "",
    val idType: String = "",        // passport | aadhaar | driving_licence
    val idNumber: String = "",
    val nationality: String = "",
    val address: String = "",
    val totalStays: Int = 0,
    val createdAt: Date = Date(),
)
