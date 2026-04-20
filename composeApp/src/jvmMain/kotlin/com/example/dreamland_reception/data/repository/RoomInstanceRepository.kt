package com.example.dreamland_reception.data.repository

import com.example.dreamland_reception.data.model.RoomInstance
import com.google.cloud.firestore.Firestore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.withContext
import java.util.Date

interface RoomInstanceRepository {
    suspend fun getAll(): List<RoomInstance>
    suspend fun getAvailable(): List<RoomInstance>
    suspend fun getByCategory(categoryId: String, hotelId: String, includeAssigned: Boolean = false): List<RoomInstance>
    suspend fun updateStatus(id: String, status: String, currentStayId: String? = null)
    fun listenByHotel(hotelId: String): Flow<List<RoomInstance>>
}

object FirestoreRoomInstanceRepository : RoomInstanceRepository {

    fun initialize(fs: Firestore) {
        FirestoreRepositorySupport.prime(fs)
    }

    private val col get() = FirestoreRepositorySupport.get().collection("roomInstances")

    override suspend fun getAll(): List<RoomInstance> = withContext(Dispatchers.IO) {
        col.orderBy("roomNumber").get().get().documents.mapNotNull { it.toRoomInstance() }
    }

    override suspend fun getAvailable(): List<RoomInstance> = withContext(Dispatchers.IO) {
        col.whereEqualTo("status", "AVAILABLE").get().get().documents.mapNotNull { it.toRoomInstance() }
    }

    override suspend fun getByCategory(categoryId: String, hotelId: String, includeAssigned: Boolean): List<RoomInstance> = withContext(Dispatchers.IO) {
        col.whereEqualTo("categoryId", categoryId)
            .get().get().documents.mapNotNull { it.toRoomInstance() }
            .filter { it.hotelId == hotelId && (it.status == "AVAILABLE" || (includeAssigned && it.status == "ASSIGNED")) }
    }

    override suspend fun updateStatus(id: String, status: String, currentStayId: String?) = withContext(Dispatchers.IO) {
        val updates = mutableMapOf<String, Any?>(
            "status" to status,
            "currentStayId" to currentStayId,
        )
        col.document(id).update(updates).get(); Unit
    }

    override fun listenByHotel(hotelId: String): Flow<List<RoomInstance>> = callbackFlow {
        val registration = col.whereEqualTo("hotelId", hotelId)
            .addSnapshotListener { snapshot, error ->
                if (error != null) { close(error); return@addSnapshotListener }
                val rooms = snapshot?.documents?.mapNotNull { it.toRoomInstance() } ?: emptyList()
                trySend(rooms)
            }
        awaitClose { registration.remove() }
    }

    private fun com.google.cloud.firestore.DocumentSnapshot.toRoomInstance() = runCatching {
        RoomInstance(
            id = id,
            hotelId = getString("hotelId") ?: "",
            categoryId = getString("categoryId") ?: "",
            categoryName = getString("categoryName") ?: "",
            roomNumber = getString("roomNumber") ?: "",
            status = getString("status") ?: "AVAILABLE",
            currentStayId = getString("currentStayId"),
            createdAt = getTimestamp("createdAt")?.toDate() ?: Date(),
        )
    }.getOrNull()
}
