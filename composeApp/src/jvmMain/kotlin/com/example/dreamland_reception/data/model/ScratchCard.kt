package com.example.dreamland_reception.data.model

/**
 * A reward "scratch card" coupon (collection `scratchCards/{cardId}`, doc id == bookingId).
 *
 * Cards are issued automatically server-side when a booking completes and scratched by the
 * guest in the mobile app. Reception only ever READS a card (by [redemptionCode]) and flips
 * [status] `SCRATCHED → REDEEMED` when applying the discount at billing time. All reward
 * fields are frozen at issuance, so the discount is computed from the snapshot on the card —
 * never from the room's current config.
 *
 * Only the fields reception needs (lookup + reward + status) are mapped here.
 */
data class ScratchCard(
    val id: String = "",
    val bookingId: String = "",
    val hotelId: String = "",
    val userId: String = "",
    val roomCategoryId: String = "",
    val categoryNameSnapshot: String = "",
    val rewardType: String = "",          // "FLAT" | "PERCENT" | "" (none)
    val rewardValuePaise: Long = 0,       // used when FLAT (5000 = ₹50)
    val rewardValuePercent: Double = 0.0, // used when PERCENT, 0–100
    val rewardMaxPaise: Long = 0,         // optional cap for PERCENT (0 = no cap)
    val status: String = "",              // "UNSCRATCHED" | "SCRATCHED" | "REDEEMED"
    val redemptionCode: String = "",      // "SC-XXXXXXXX"
)
