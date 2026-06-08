package com.example.dreamland_reception.data.repository

import com.google.cloud.firestore.Firestore
import com.google.cloud.firestore.SetOptions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.withContext
import java.util.Date

/** Live cash + bank balance from the `Liquidity` collection. */
data class LiquidityBalance(val cash: Double = 0.0, val bank: Double = 0.0)

/** One row from the `registerTransactions` audit log (a single cash/bank movement). */
data class RegisterTransaction(
    val id: String = "",
    val account: String = "",        // "cash" | "bank"
    val amount: Double = 0.0,        // always positive; sign carried by [direction]
    val direction: String = "",      // "IN" | "OUT"
    val type: String = "",           // "LEDGER" (mirror of a ledger posting) | "MANUAL" (till-only)
    val description: String = "",
    val manager: String = "",
    val sourceId: String = "",
    val hotelId: String = "",
    val balanceAfter: Double = 0.0,  // that account's running balance after this movement
    val createdAt: Date = Date(),
)

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

    /** Live stream of the cash + bank till balances (Firestore snapshot listener). */
    fun listen(): Flow<LiquidityBalance> = callbackFlow {
        val registration = db.collection("Liquidity").addSnapshotListener { snapshot, error ->
            if (error != null) return@addSnapshotListener   // keep last value; don't kill the stream
            if (snapshot != null) {
                var cash = 0.0
                var bank = 0.0
                for (doc in snapshot.documents) {
                    val amount = doc.getDouble("amount") ?: 0.0
                    when (doc.id.lowercase()) {
                        "cash" -> cash = amount
                        "bank" -> bank = amount
                    }
                }
                trySend(LiquidityBalance(cash, bank))
            }
        }
        awaitClose { registration.remove() }
    }

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
        manager: String = "",
        type: String = "LEDGER",   // "LEDGER" = mirror of a ledger posting; "MANUAL" = till-only adjustment
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
                    "manager" to manager,
                    "type" to type,
                    "balanceAfter" to newBalance,
                    "createdAt" to now,
                ),
            )
            true
        }.get()
        Unit
    }

    /** One-shot read of the current cash + bank till balance. */
    suspend fun getBalance(): LiquidityBalance = withContext(Dispatchers.IO) {
        val snap = db.collection("Liquidity").get().get()
        var cash = 0.0
        var bank = 0.0
        for (doc in snap.documents) {
            val amount = doc.getDouble("amount") ?: 0.0
            when (doc.id.lowercase()) {
                "cash" -> cash = amount
                "bank" -> bank = amount
            }
        }
        LiquidityBalance(cash, bank)
    }

    /**
     * Loads all register movements in `[dayStart, dayEnd)` (a range query on the auto-indexed
     * `createdAt`; the hotel is filtered client-side so no composite index is needed), oldest first.
     */
    suspend fun getRegisterTransactions(dayStart: Date, dayEnd: Date, hotelId: String): List<RegisterTransaction> =
        withContext(Dispatchers.IO) {
            db.collection("registerTransactions")
                .whereGreaterThanOrEqualTo("createdAt", dayStart)
                .whereLessThan("createdAt", dayEnd)
                .orderBy("createdAt")
                .get().get().documents
                .mapNotNull { doc ->
                    runCatching {
                        RegisterTransaction(
                            id = doc.id,
                            account = doc.getString("account") ?: "",
                            amount = doc.getDouble("amount") ?: 0.0,
                            direction = doc.getString("direction") ?: "",
                            type = doc.getString("type") ?: "LEDGER",
                            description = doc.getString("description") ?: "",
                            manager = doc.getString("manager") ?: "",
                            sourceId = doc.getString("sourceId") ?: "",
                            hotelId = doc.getString("hotelId") ?: "",
                            balanceAfter = doc.getDouble("balanceAfter") ?: 0.0,
                            createdAt = doc.getTimestamp("createdAt")?.toDate() ?: Date(),
                        )
                    }.getOrNull()
                }
                .filter { hotelId.isBlank() || it.hotelId == hotelId }
        }

    /**
     * Records a MANUAL till adjustment (miscellaneous cash/bank in or out) — Firestore only, the
     * ledger is intentionally NOT touched. Adjusts `Liquidity/{account}.amount` and writes a
     * `registerTransactions` row tagged `type = "MANUAL"` with the on-duty [manager] and [note].
     * Each call uses a fresh sourceId so every adjustment is recorded. Returns Result for the UI.
     */
    suspend fun recordManualAdjustment(
        account: String,
        amount: Double,
        isAdd: Boolean,
        note: String,
        manager: String,
        hotelId: String,
    ): Result<Unit> = runCatching {
        recordMovement(
            account = account,
            amount = amount,
            direction = if (isAdd) "IN" else "OUT",
            sourceId = "manual_${java.util.UUID.randomUUID()}",
            description = note,
            hotelId = hotelId,
            manager = manager,
            type = "MANUAL",
        )
    }
}
