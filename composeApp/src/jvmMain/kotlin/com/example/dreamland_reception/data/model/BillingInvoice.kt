package com.example.dreamland_reception.data.model

import java.util.Date

data class BillingInvoice(
    val id: String = "",
    val bookingId: String = "",
    val stayId: String = "",
    val guestName: String = "",
    val roomNumber: String = "",
    val roomCharges: Double = 0.0,
    val serviceCharges: Double = 0.0,
    val earlyCheckInCharge: Double = 0.0,
    val lateCheckOutCharge: Double = 0.0,
    val tax: Double = 0.0,
    val discount: Double = 0.0,
    val totalAmount: Double = 0.0,
    val amountPaid: Double = 0.0,
    val paymentMethod: String = "",  // cash | card | upi | bank_transfer
    val status: String = "PENDING",  // PENDING | PARTIAL | PAID
    val issuedAt: Date = Date(),
    val paidAt: Date? = null,
)
