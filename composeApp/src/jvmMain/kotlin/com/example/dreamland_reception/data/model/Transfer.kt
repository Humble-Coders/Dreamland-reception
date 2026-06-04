package com.example.dreamland_reception.data.model

import java.util.Date

/**
 * A money movement from one party to another: `DR(to) / CR(from)` in Humble Ledger.
 * Either side can be Cash, Bank, a Customer (guest), or a Vendor. Stored in the
 * `transfers` Firestore collection and synced to the ledger durably.
 *
 * kind: CASH | BANK | CUSTOMER | VENDOR. refId is the guest/vendor id for those
 * kinds (blank for Cash/Bank).
 */
data class Transfer(
    val id: String = "",
    val hotelId: String = "",
    val fromKind: String = "",
    val fromRefId: String = "",
    val fromName: String = "",
    val toKind: String = "",
    val toRefId: String = "",
    val toName: String = "",
    val amount: Double = 0.0,
    val notes: String = "",
    val createdAt: Date = Date(),
    val synced: Boolean = false,
    val syncError: String = "",
)
