package com.example.dreamland_reception.data.model

import java.util.Date

/** A reusable "purpose of visit" option, stored one document per purpose in `purposeType`. */
data class PurposeType(
    val id: String = "",
    val hotelId: String = "",
    val name: String = "",
    val createdAt: Date = Date(),
)
