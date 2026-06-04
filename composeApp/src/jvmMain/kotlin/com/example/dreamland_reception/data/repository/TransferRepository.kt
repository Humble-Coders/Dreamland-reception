package com.example.dreamland_reception.data.repository

import com.example.dreamland_reception.data.model.Transfer
import com.google.cloud.firestore.DocumentSnapshot
import com.google.cloud.firestore.Firestore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.withContext
import java.util.Date

interface TransferRepository {
    fun listenByHotel(hotelId: String): Flow<List<Transfer>>
    suspend fun add(transfer: Transfer): String
    suspend fun markSynced(id: String)
    suspend fun markSyncFailed(id: String, error: String)
    suspend fun getUnsynced(hotelId: String): List<Transfer>
}

object FirestoreTransferRepository : TransferRepository {

    fun initialize(fs: Firestore) {
        FirestoreRepositorySupport.prime(fs)
    }

    private val col get() = FirestoreRepositorySupport.get().collection("transfers")

    override fun listenByHotel(hotelId: String): Flow<List<Transfer>> = callbackFlow {
        val registration = col.whereEqualTo("hotelId", hotelId)
            .addSnapshotListener { snapshot, error ->
                if (error != null) { close(error); return@addSnapshotListener }
                val list = snapshot?.documents?.mapNotNull { it.toTransfer() }
                    ?.sortedByDescending { it.createdAt } ?: emptyList()
                trySend(list)
            }
        awaitClose { registration.remove() }
    }

    override suspend fun add(transfer: Transfer): String = withContext(Dispatchers.IO) {
        col.add(transfer.toMap()).get().id
    }

    override suspend fun markSynced(id: String) = withContext(Dispatchers.IO) {
        col.document(id).update(mapOf("synced" to true, "syncError" to "")).get(); Unit
    }

    override suspend fun markSyncFailed(id: String, error: String) = withContext(Dispatchers.IO) {
        col.document(id).update(mapOf("synced" to false, "syncError" to error.take(500))).get(); Unit
    }

    override suspend fun getUnsynced(hotelId: String): List<Transfer> = withContext(Dispatchers.IO) {
        col.whereEqualTo("hotelId", hotelId).whereEqualTo("synced", false)
            .get().get().documents.mapNotNull { it.toTransfer() }.filter { it.id.isNotBlank() }
    }

    private fun DocumentSnapshot.toTransfer() = runCatching {
        Transfer(
            id = id,
            hotelId = getString("hotelId") ?: "",
            fromKind = getString("fromKind") ?: "",
            fromRefId = getString("fromRefId") ?: "",
            fromName = getString("fromName") ?: "",
            fromPhone = getString("fromPhone") ?: "",
            toKind = getString("toKind") ?: "",
            toRefId = getString("toRefId") ?: "",
            toName = getString("toName") ?: "",
            toPhone = getString("toPhone") ?: "",
            amount = getDouble("amount") ?: 0.0,
            notes = getString("notes") ?: "",
            createdAt = getTimestamp("createdAt")?.toDate() ?: Date(),
            synced = getBoolean("synced") ?: false,
            syncError = getString("syncError") ?: "",
        )
    }.getOrNull()

    private fun Transfer.toMap() = mapOf(
        "hotelId" to hotelId,
        "fromKind" to fromKind, "fromRefId" to fromRefId, "fromName" to fromName, "fromPhone" to fromPhone,
        "toKind" to toKind, "toRefId" to toRefId, "toName" to toName, "toPhone" to toPhone,
        "amount" to amount, "notes" to notes,
        "createdAt" to createdAt, "synced" to synced, "syncError" to syncError,
    )
}
