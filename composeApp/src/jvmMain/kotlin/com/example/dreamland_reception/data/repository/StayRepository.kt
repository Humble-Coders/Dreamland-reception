package com.example.dreamland_reception.data.repository

import com.example.dreamland_reception.data.model.Stay
import com.google.cloud.firestore.Firestore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.withContext
import java.util.Date

interface StayRepository {
    suspend fun getActive(hotelId: String): List<Stay>
    suspend fun getAll(hotelId: String): List<Stay>
    suspend fun add(stay: Stay): String
    suspend fun checkOut(id: String, checkOutTime: Date)
    suspend fun getCompleted(hotelId: String): List<Stay>
    fun listenActive(hotelId: String): Flow<List<Stay>>
}

object FirestoreStayRepository : StayRepository {

    fun initialize(fs: Firestore) {
        FirestoreRepositorySupport.prime(fs)
    }

    private val col get() = FirestoreRepositorySupport.get().collection("stays")

    override suspend fun getActive(hotelId: String): List<Stay> = withContext(Dispatchers.IO) {
        col.whereEqualTo("hotelId", hotelId)
            .get().get().documents.mapNotNull { it.toStay() }
            .filter { it.status == "ACTIVE" }
    }

    override suspend fun getCompleted(hotelId: String): List<Stay> = withContext(Dispatchers.IO) {
        col.whereEqualTo("hotelId", hotelId)
            .get().get().documents.mapNotNull { it.toStay() }
            .filter { it.status == "COMPLETED" }
    }

    override suspend fun getAll(hotelId: String): List<Stay> = withContext(Dispatchers.IO) {
        col.whereEqualTo("hotelId", hotelId)
            .get().get().documents.mapNotNull { it.toStay() }
            .sortedBy { it.checkInActual }
    }

    override suspend fun add(stay: Stay): String = withContext(Dispatchers.IO) {
        col.add(stay.toMap()).get().id
    }

    override suspend fun checkOut(id: String, checkOutTime: Date) = withContext(Dispatchers.IO) {
        col.document(id).update(
            mapOf("status" to "COMPLETED", "checkOutActual" to checkOutTime),
        ).get(); Unit
    }

    override fun listenActive(hotelId: String): Flow<List<Stay>> = callbackFlow {
        val registration = col.whereEqualTo("hotelId", hotelId)
            .whereEqualTo("status", "ACTIVE")
            .addSnapshotListener { snapshot, error ->
                if (error != null) { close(error); return@addSnapshotListener }
                val stays = snapshot?.documents?.mapNotNull { it.toStay() } ?: emptyList()
                trySend(stays)
            }
        awaitClose { registration.remove() }
    }

    private fun com.google.cloud.firestore.DocumentSnapshot.toStay() = runCatching {
        Stay(
            id = id,
            hotelId = getString("hotelId") ?: "",
            bookingId = getString("bookingId") ?: "",
            guestName = getString("guestName") ?: "",
            guestPhone = getString("guestPhone") ?: "",
            roomInstanceId = getString("roomInstanceId") ?: "",
            roomNumber = getString("roomNumber") ?: "",
            roomCategoryId = getString("roomCategoryId") ?: "",
            roomCategoryName = getString("roomCategoryName") ?: "",
            checkInActual = getTimestamp("checkInActual")?.toDate() ?: Date(),
            expectedCheckOut = getTimestamp("expectedCheckOut")?.toDate() ?: Date(),
            checkOutActual = getTimestamp("checkOutActual")?.toDate(),
            status = getString("status") ?: "ACTIVE",
            adults = getLong("adults")?.toInt() ?: 1,
            children = getLong("children")?.toInt() ?: 0,
            breakfast = getBoolean("breakfast") ?: false,
            extraBed = getBoolean("extraBed") ?: false,
            earlyCheckIn = getBoolean("earlyCheckIn") ?: false,
            lateCheckOut = getBoolean("lateCheckOut") ?: false,
            totalBilled = getDouble("totalBilled") ?: 0.0,
            specialRequests = getString("specialRequests") ?: "",
            createdAt = getTimestamp("createdAt")?.toDate() ?: Date(),
        )
    }.getOrNull()

    private fun Stay.toMap() = mapOf(
        "hotelId" to hotelId,
        "bookingId" to bookingId,
        "guestName" to guestName,
        "guestPhone" to guestPhone,
        "roomInstanceId" to roomInstanceId,
        "roomNumber" to roomNumber,
        "roomCategoryId" to roomCategoryId,
        "roomCategoryName" to roomCategoryName,
        "checkInActual" to checkInActual,
        "expectedCheckOut" to expectedCheckOut,
        "checkOutActual" to checkOutActual,
        "status" to status,
        "adults" to adults,
        "children" to children,
        "breakfast" to breakfast,
        "extraBed" to extraBed,
        "earlyCheckIn" to earlyCheckIn,
        "lateCheckOut" to lateCheckOut,
        "totalBilled" to totalBilled,
        "specialRequests" to specialRequests,
        "createdAt" to createdAt,
    )
}
