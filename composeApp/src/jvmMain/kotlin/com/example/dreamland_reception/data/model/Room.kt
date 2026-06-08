package com.example.dreamland_reception.data.model

data class SeasonalPricing(
    val label: String = "",
    val from: String = "",   // "MM-DD"
    val to: String = "",     // "MM-DD"
    val price: Double = 0.0,
)

data class Room(
    val id: String = "",
    val hotelId: String = "",
    val number: String = "",
    val type: String = "",
    val floor: Int = 1,
    val capacity: Int = 2,
    val pricePerNight: Double = 0.0,
    // Walk-in (offline) rate, stored beside `price` in Firestore as `offlinePrice`.
    // Used as the default for offline walk-in check-ins; 0 means "not set" → the app
    // falls back to [pricePerNight]. Bookings never use this.
    val offlinePrice: Double = 0.0,
    val breakfastPrice: Double = 0.0,
    val status: String = "available",
    val available: Boolean = true,
    val amenities: List<String> = emptyList(),
    val description: String = "",
    val seasonalPricing: List<SeasonalPricing> = emptyList(),
    val taxPercentage: Double = 0.0,
    // ── Scratch-card reward for this category (read by the issuance Cloud Function) ──
    // rewardType "" = this category issues no cards. Values are stored in PAISE (5000 = ₹50).
    val rewardType: String = "",           // "" | "FLAT" | "PERCENT"
    val rewardValuePaise: Long = 0,        // used when FLAT
    val rewardValuePercent: Double = 0.0,  // used when PERCENT, 0–100
    val rewardMaxPaise: Long = 0,          // optional cap for PERCENT (0 = no cap)
)
