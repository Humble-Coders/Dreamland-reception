package com.example.dreamland_reception.data.repository

import com.example.dreamland_reception.data.model.ScratchCard
import com.google.cloud.firestore.DocumentSnapshot
import com.google.cloud.firestore.FieldValue
import com.google.cloud.firestore.Firestore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

interface ScratchCardRepository {
    /** Finds the card for [code] that belongs to [hotelId], or null. */
    suspend fun findByCode(code: String, hotelId: String): ScratchCard?

    /** Atomically claims a card: `SCRATCHED → REDEEMED`. Fails if it isn't currently SCRATCHED. */
    suspend fun redeem(cardId: String): Result<Unit>

    /** Reverts a mistaken redemption: `REDEEMED → SCRATCHED`. No-op if not REDEEMED. */
    suspend fun revertRedemption(cardId: String): Result<Unit>
}

object FirestoreScratchCardRepository : ScratchCardRepository {

    fun initialize(fs: Firestore) {
        FirestoreRepositorySupport.prime(fs)
    }

    private val col get() = FirestoreRepositorySupport.get().collection("scratchCards")

    override suspend fun findByCode(code: String, hotelId: String): ScratchCard? = withContext(Dispatchers.IO) {
        val normalized = code.trim().uppercase()
        if (normalized.isBlank()) return@withContext null
        // Query by the (effectively unique) code alone — a single-field index Firestore creates
        // automatically — then verify the hotel client-side. This avoids a composite index AND
        // doubles as a guard so one hotel can never redeem another hotel's card.
        col.whereEqualTo("redemptionCode", normalized).limit(5).get().get()
            .documents.mapNotNull { it.toScratchCard() }
            .firstOrNull { it.hotelId == hotelId }
    }

    override suspend fun redeem(cardId: String): Result<Unit> = withContext(Dispatchers.IO) {
        if (cardId.isBlank()) return@withContext Result.failure(IllegalArgumentException("Missing card id"))
        runCatching {
            val fs = FirestoreRepositorySupport.get()
            val ref = fs.collection("scratchCards").document(cardId)
            fs.runTransaction { txn ->
                val snap = txn.get(ref).get()
                if (!snap.exists()) throw IllegalStateException("Coupon no longer exists")
                val status = snap.getString("status") ?: ""
                if (status != "SCRATCHED") throw IllegalStateException("Coupon is not redeemable (status=$status)")
                txn.update(ref, mapOf(
                    "status" to "REDEEMED",
                    "redeemedAt" to FieldValue.serverTimestamp(),
                ))
                true
            }.get()
            Unit
        }
    }

    override suspend fun revertRedemption(cardId: String): Result<Unit> = withContext(Dispatchers.IO) {
        if (cardId.isBlank()) return@withContext Result.success(Unit)
        runCatching {
            val fs = FirestoreRepositorySupport.get()
            val ref = fs.collection("scratchCards").document(cardId)
            fs.runTransaction { txn ->
                val snap = txn.get(ref).get()
                if (snap.exists() && snap.getString("status") == "REDEEMED") {
                    txn.update(ref, mapOf("status" to "SCRATCHED", "redeemedAt" to 0L))
                }
                true
            }.get()
            Unit
        }
    }

    private fun DocumentSnapshot.toScratchCard(): ScratchCard? = runCatching {
        ScratchCard(
            id = id,
            bookingId = getString("bookingId") ?: "",
            hotelId = getString("hotelId") ?: "",
            userId = getString("userId") ?: "",
            roomCategoryId = getString("roomCategoryId") ?: "",
            categoryNameSnapshot = getString("categoryNameSnapshot") ?: "",
            rewardType = getString("rewardType") ?: "",
            rewardValuePaise = getLong("rewardValuePaise") ?: 0L,
            rewardValuePercent = getDouble("rewardValuePercent") ?: 0.0,
            rewardMaxPaise = getLong("rewardMaxPaise") ?: 0L,
            status = getString("status") ?: "",
            redemptionCode = getString("redemptionCode") ?: "",
        )
    }.getOrNull()
}
