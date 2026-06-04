package com.example.dreamland_reception.data.model

import java.util.Date

data class OrderItem(
    val itemId: String = "",                 // source doc id — foodItems/{id} or services/{id}
    val name: String = "",
    val quantity: Int = 1,
    val basePrice: Double = 0.0,             // per-unit pre-tax price
    val taxPercentage: Double = 0.0,         // per-unit tax %; 0 = tax-inclusive
    val taxedPrice: Double = basePrice * (1 + taxPercentage / 100.0),  // per-unit post-tax
    val taxAmount: Double = (taxedPrice - basePrice) * quantity,       // line tax
    val subtotal: Double = basePrice * quantity,                       // line pre-tax
    val total: Double = taxedPrice * quantity,                         // line post-tax
)

data class Order(
    val id: String = "",
    val hotelId: String = "",
    val userId: String = "",         // opaque user doc id of the guest who placed the order
    val stayId: String = "",
    val groupStayId: String = "",    // copied from stays.groupStayId; empty for single-room
    val roomInstanceId: String = "",
    val roomNumber: String = "",
    val guestName: String = "",
    val guestPhone: String = "",
    val items: List<OrderItem> = emptyList(),
    val type: String = "ORDER",      // ROOM_SERVICE | ORDER | SERVICE
    val subtotalAmount: Double = 0.0,   // Σ items[].subtotal (pre-tax)
    val totalTaxAmount: Double = 0.0,   // Σ items[].taxAmount
    val totalAmount: Double = 0.0,      // Σ items[].total (post-tax)
    val status: String = "NEW",      // NEW | ASSIGNED | COMPLETED
    val notes: String = "",
    val createdAt: Date = Date(),
    val assignedTo: String = "",
    val assignedToName: String = "",
)
