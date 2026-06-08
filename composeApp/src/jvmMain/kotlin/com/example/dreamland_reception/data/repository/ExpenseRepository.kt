package com.example.dreamland_reception.data.repository

import com.example.dreamland_reception.data.model.Expense
import com.google.cloud.firestore.DocumentSnapshot
import com.google.cloud.firestore.Firestore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.withContext
import java.util.Date

interface ExpenseRepository {
    fun listenByHotel(hotelId: String): Flow<List<Expense>>
    suspend fun add(expense: Expense): String
    suspend fun markSynced(id: String)
    suspend fun markSyncFailed(id: String, error: String)
    /** Expenses that have a vendor/payment but never synced — retried on load. */
    suspend fun getUnsynced(hotelId: String): List<Expense>
    /** Permanently removes the expense document. */
    suspend fun delete(id: String)
}

object FirestoreExpenseRepository : ExpenseRepository {

    fun initialize(fs: Firestore) {
        FirestoreRepositorySupport.prime(fs)
    }

    private val col get() = FirestoreRepositorySupport.get().collection("expenses")

    override fun listenByHotel(hotelId: String): Flow<List<Expense>> = callbackFlow {
        val registration = col.whereEqualTo("hotelId", hotelId)
            .addSnapshotListener { snapshot, error ->
                if (error != null) { close(error); return@addSnapshotListener }
                val list = snapshot?.documents?.mapNotNull { it.toExpense() }
                    ?.sortedByDescending { it.createdAt } ?: emptyList()
                trySend(list)
            }
        awaitClose { registration.remove() }
    }

    override suspend fun add(expense: Expense): String = withContext(Dispatchers.IO) {
        col.add(expense.toMap()).get().id
    }

    override suspend fun markSynced(id: String) = withContext(Dispatchers.IO) {
        col.document(id).update(mapOf("synced" to true, "syncError" to "")).get(); Unit
    }

    override suspend fun markSyncFailed(id: String, error: String) = withContext(Dispatchers.IO) {
        col.document(id).update(mapOf("synced" to false, "syncError" to error.take(500))).get(); Unit
    }

    override suspend fun getUnsynced(hotelId: String): List<Expense> = withContext(Dispatchers.IO) {
        // Re-running settle is safe — deterministic sourceIds make Humble Ledger dedupe.
        col.whereEqualTo("hotelId", hotelId)
            .whereEqualTo("synced", false)
            .get().get().documents.mapNotNull { it.toExpense() }
            .filter { it.id.isNotBlank() }
    }

    override suspend fun delete(id: String) = withContext(Dispatchers.IO) {
        if (id.isBlank()) return@withContext
        col.document(id).delete().get(); Unit
    }

    private fun DocumentSnapshot.toExpense() = runCatching {
        Expense(
            id = id,
            hotelId = getString("hotelId") ?: "",
            title = getString("title") ?: "",
            notes = getString("notes") ?: "",
            amount = getDouble("amount") ?: 0.0,
            vendorId = getString("vendorId") ?: "",
            vendorName = getString("vendorName") ?: "",
            cashPaid = getDouble("cashPaid") ?: 0.0,
            bankPaid = getDouble("bankPaid") ?: 0.0,
            createdAt = getTimestamp("createdAt")?.toDate() ?: Date(),
            synced = getBoolean("synced") ?: false,
            syncError = getString("syncError") ?: "",
        )
    }.getOrNull()

    private fun Expense.toMap() = mapOf(
        "hotelId" to hotelId,
        "title" to title,
        "notes" to notes,
        "amount" to amount,
        "vendorId" to vendorId,
        "vendorName" to vendorName,
        "cashPaid" to cashPaid,
        "bankPaid" to bankPaid,
        "createdAt" to createdAt,
        "synced" to synced,
        "syncError" to syncError,
    )
}
