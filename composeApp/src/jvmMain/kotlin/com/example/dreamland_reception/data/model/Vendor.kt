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
    // Discount % the hotel gets off the guest's actual (pre-tax) price when buying from
    // this vendor. Used to pre-fill the vendor cost in the Mark Done dialog:
    //   vendorCost = actualPrice × (1 − discountPercent/100)
    val discountPercent: Double = 0.0,
    val createdAt: Date = Date(),
)
