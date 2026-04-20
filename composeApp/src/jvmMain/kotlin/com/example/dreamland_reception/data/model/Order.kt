package com.example.dreamland_reception.data.model

import java.util.Date

data class OrderItem(
    val name: String = "",
    val quantity: Int = 1,
    val price: Double = 0.0,
)

data class Order(
    val id: String = "",
    val hotelId: String = "",
    val stayId: String = "",
    val roomNumber: String = "",
    val guestName: String = "",
    val items: List<OrderItem> = emptyList(),
    val type: String = "ORDER",      // ROOM_SERVICE | ORDER | SERVICE
    val totalAmount: Double = 0.0,
    val status: String = "NEW",      // NEW | ASSIGNED | COMPLETED
    val notes: String = "",
    val orderedAt: Date = Date(),
    val assignedTo: String = "",
    val assignedToName: String = "",
)
