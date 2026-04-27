package com.example.dreamland_reception.data.model

import java.util.Date
import java.util.UUID

data class BillItem(
    val id: String = UUID.randomUUID().toString(),
    val name: String = "",
    val type: String = "CUSTOM",   // ROOM | ORDER | SERVICE | CUSTOM
    val quantity: Int = 1,
    val unitPrice: Double = 0.0,
    val total: Double = unitPrice * quantity,
    val refId: String = "",
    val notes: String = "",
)

data class PaymentTransaction(
    val id: String = UUID.randomUUID().toString(),
    val amount: Double = 0.0,
    val method: String = "CASH",   // CASH | UPI | CARD
    val status: String = "PAID",
    val createdAt: Date = Date(),
)

data class Bill(
    val id: String = "",
    val hotelId: String = "",
    val stayId: String = "",
    val guestName: String = "",
    val roomNumber: String = "",
    val checkInDate: Date? = null,
    val checkOutDate: Date? = null,
    val items: List<BillItem> = emptyList(),
    val taxEnabled: Boolean = false,
    val taxPercentage: Double = 18.0,
    val discountType: String = "FLAT",   // FLAT | PERCENT
    val discountValue: Double = 0.0,
    val subtotal: Double = 0.0,
    val taxAmount: Double = 0.0,
    val discountAmount: Double = 0.0,
    val totalAmount: Double = 0.0,
    val transactions: List<PaymentTransaction> = emptyList(),
    val totalPaid: Double = 0.0,
    val advancePayment: Double = 0.0,
    val pendingAmount: Double = 0.0,
    val status: String = "PENDING",   // PENDING | PARTIAL | PAID
    val createdAt: Date = Date(),
    val updatedAt: Date = Date(),
)
