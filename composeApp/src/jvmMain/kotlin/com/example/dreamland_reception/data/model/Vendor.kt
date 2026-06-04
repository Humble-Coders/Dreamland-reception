package com.example.dreamland_reception.data.model

import java.util.Date

/**
 * An outside entity the hotel buys from (e.g. a food supplier). Stored in its own
 * `vendors` Firestore collection — deliberately separate from guests (`users`), since
 * a vendor is the opposite side of the books (Accounts Payable, not Receivable).
 *
 * The Firestore doc [id] is the stable cross-system key sent to Humble Ledger as the
 * vendor `externalId`, so the same vendor always maps to the same ledger account.
 * Phone is optional for vendors.
 */
data class Vendor(
    val id: String = "",
    val hotelId: String = "",
    val name: String = "",
    val phone: String = "",
    val email: String = "",
    val gstin: String = "",
    val createdAt: Date = Date(),
)
