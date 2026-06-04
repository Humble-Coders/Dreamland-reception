package com.example.dreamland_reception.data.model

import java.util.Date

/**
 * A reception desk manager. Stored in the `receptionManagers` Firestore collection.
 * The password is never stored in plaintext — only its salted SHA-256 hash, which
 * is what the handover dialog verifies against.
 */
data class ReceptionManager(
    val id: String = "",
    val hotelId: String = "",
    val name: String = "",
    val passwordHash: String = "",
    val createdAt: Date = Date(),
)
