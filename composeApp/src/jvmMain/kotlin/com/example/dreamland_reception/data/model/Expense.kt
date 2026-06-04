package com.example.dreamland_reception.data.model

import java.util.Date

/**
 * A hotel expense (not billed to a guest). Optionally tied to a vendor — exactly
 * like an order's vendor purchase, but it's a cost the hotel bears rather than a
 * recharge. Stored in the `expenses` Firestore collection.
 *
 * - vendorId blank  → direct expense paid straight from cash/bank (no vendor balance);
 *   [title] is required so it's identifiable.
 * - vendorId set    → posts to the vendor's Accounts Payable (pay-later / overpay
 *   supported); [title] is optional.
 *
 * The amount books to a single "General Expense" account in Humble Ledger (no
 * per-expense category). Synced durably; [synced] flips true once it posts.
 */
data class Expense(
    val id: String = "",
    val hotelId: String = "",
    val title: String = "",
    val notes: String = "",
    val amount: Double = 0.0,        // total cost of the expense
    val vendorId: String = "",       // firestore vendors/{id}; blank = no vendor
    val vendorName: String = "",
    val cashPaid: Double = 0.0,
    val bankPaid: Double = 0.0,
    val createdAt: Date = Date(),
    val synced: Boolean = false,
    val syncError: String = "",
)
