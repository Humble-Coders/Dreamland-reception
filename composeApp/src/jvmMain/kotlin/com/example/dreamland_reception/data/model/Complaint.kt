package com.example.dreamland_reception.data.model

import java.util.Date

data class Complaint(
    val id: String = "",
    val hotelId: String = "",
    val stayId: String = "",
    val guestName: String = "",
    val roomNumber: String = "",
    val type: String = "",            // ref to complaintTypes doc ID; MAINTENANCE | HOUSEKEEPING | NOISE | FOOD | STAFF | OTHER
    val description: String = "",
    val priority: String = "MEDIUM",  // HIGH | MEDIUM | LOW
    val status: String = "NEW",       // NEW | ASSIGNED | COMPLETED
    val assignedTo: String = "",
    val assignedToName: String = "",
    val reportedAt: Date = Date(),
    val resolvedAt: Date? = null,
)
