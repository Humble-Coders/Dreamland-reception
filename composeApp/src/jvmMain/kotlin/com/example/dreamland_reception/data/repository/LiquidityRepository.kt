package com.example.dreamland_reception.data.repository

import com.google.cloud.firestore.Firestore
import com.google.cloud.firestore.SetOptions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.Date

/**
 * Mirrors every cash/bank movement in the Humble Ledger into Firestore so the hotel's live till
 * always equals the ledger's Cash/Bank balance:
 *
 *  - `Liquidity/cash.amount` / `Liquidity/bank.amount` are adjusted UP (receipts) or DOWN (payouts).
 *  - one `registerTransactions/{id}` row is written recording the movement (direction IN / OUT).
 *
 * Both writes happen in a SINGLE Firestore transaction — they always land together or not at all.
 * The transaction is keyed on the ledger posting's deterministic `sourceId` (+ account + direction),
 * so calling it again for the same posting (e.g. a settle retry) is a safe no-op: never double-counted.
 */
object FirestoreLiquidityRepository {

    fun initialize(fs: Firestore) {
        FirestoreRepositorySupport.prime(fs)
    }

    private val db get() = FirestoreRepositorySupport.get()

    /**
     * Records one cash/bank movement against the live till.
     * [account] is "cash" or "bank" (case-insensitive); [direction] is "IN" (money received,
     * increments the till) or "OUT" (money paid out, decrements it); [sourceId] is the ledger
     * posting's idempotency key. Idempotent and atomic. No-op for blank/zero/unknown inputs.
     */
    suspend fun recordMovement(
        account: String,
        amount: Double,
        direction: String,
        sourceId: String,
        description: String,
        hotelId: String,
    ) = withContext(Dispatchers.IO) {
        val acct = account.trim().lowercase()
        val dir = direction.trim().uppercase()
        if (acct != "cash" && acct != "bank") return@withContext
        if (dir != "IN" && dir != "OUT") return@withContext
        if (amount <= 0.0 || sourceId.isBlank()) return@withContext

        val signed = if (dir == "IN") amount else -amount
        // One register row per (posting, account, direction) so a single transaction touching both
        // cash and bank — or both directions — records each leg exactly once.
        val regId = "${sourceId.replace('/', '_')}__${acct}__${dir.lowercase()}"
        val liquidityRef = db.collection("Liquidity").document(acct)
        val registerRef = db.collection("registerTransactions").document(regId)

        db.runTransaction { txn ->
            // All reads first (Firestore transaction rule).
            val alreadyRecorded = txn.get(registerRef).get().exists()
            if (alreadyRecorded) return@runTransaction false
            val current = txn.get(liquidityRef).get().getDouble("amount") ?: 0.0
            val newBalance = current + signed
            val now = Date()

            // Both writes commit together — the till and its audit row are never out of sync.
            txn.set(
                liquidityRef,
                mapOf("amount" to newBalance, "updatedAt" to now),
                SetOptions.merge(),
            )
            txn.set(
                registerRef,
                mapOf(
                    "account" to acct,
                    "amount" to amount,        // always positive; `direction` carries the sign
                    "direction" to dir,        // "IN" | "OUT"
                    "sourceId" to sourceId,
                    "description" to description,
                    "hotelId" to hotelId,
                    "balanceAfter" to newBalance,
                    "createdAt" to now,
                ),
            )
            true
        }.get()
        Unit
    }
}
