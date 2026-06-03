package com.example.dreamland_reception.data.repository

import com.example.dreamland_reception.data.model.GuestRecord
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
    suspend fun getById(id: String): Stay?
    suspend fun add(stay: Stay): String
    suspend fun checkInBatch(stay: Stay, roomInstanceId: String): String
    suspend fun checkOut(id: String, checkOutTime: Date, lateCheckOutCharge: Double = 0.0)
    suspend fun updateExpectedCheckOut(id: String, newCheckOut: Date)
    suspend fun changeRoom(
        stayId: String, oldInstanceId: String,
        newInstanceId: String, newRoomNumber: String,
        newCategoryId: String, newCategoryName: String,
    )
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

    override suspend fun getById(id: String): Stay? = withContext(Dispatchers.IO) {
        col.document(id).get().get().toStay()
    }

    override suspend fun add(stay: Stay): String = withContext(Dispatchers.IO) {
        col.add(stay.toMap()).get().id
    }

    override suspend fun checkInBatch(stay: Stay, roomInstanceId: String): String = withContext(Dispatchers.IO) {
        val fs = FirestoreRepositorySupport.get()
        val stayRef = col.document()
        val stayId = stayRef.id
        val batch = fs.batch()
        batch.set(stayRef, stay.copy(id = stayId).toMap())
        // OCCUPIED is derived client-side from active stays; only track the link via currentStayId
        batch.update(
            fs.collection("roomInstances").document(roomInstanceId),
            mapOf("currentStayId" to stayId),
        )
        batch.commit().get()
        stayId
    }

    override suspend fun checkOut(id: String, checkOutTime: Date, lateCheckOutCharge: Double) = withContext(Dispatchers.IO) {
        col.document(id).update(
            mapOf(
                "status" to "COMPLETED",
                "checkOutActual" to checkOutTime,
                "lateCheckOutCharge" to lateCheckOutCharge,
                "updatedAt" to Date(),
            ),
        ).get(); Unit
    }

    override suspend fun updateExpectedCheckOut(id: String, newCheckOut: Date) = withContext(Dispatchers.IO) {
        col.document(id).update(mapOf("expectedCheckOut" to newCheckOut, "updatedAt" to Date())).get(); Unit
    }

    override suspend fun changeRoom(
        stayId: String, oldInstanceId: String,
        newInstanceId: String, newRoomNumber: String,
        newCategoryId: String, newCategoryName: String,
    ) = withContext(Dispatchers.IO) {
        val fs = FirestoreRepositorySupport.get()
        val batch = fs.batch()
        batch.update(col.document(stayId), mapOf(
            "roomInstanceId" to newInstanceId,
            "roomNumber" to newRoomNumber,
            "roomCategoryId" to newCategoryId,
            "roomCategoryName" to newCategoryName,
            "updatedAt" to Date(),
        ))
        batch.update(fs.collection("roomInstances").document(oldInstanceId), mapOf("currentStayId" to null))
        batch.update(fs.collection("roomInstances").document(newInstanceId), mapOf("currentStayId" to stayId))
        batch.commit().get()
        Unit
    }

    override fun listenActive(hotelId: String): Flow<List<Stay>> = callbackFlow {
        val registration = col.whereEqualTo("hotelId", hotelId)
            .addSnapshotListener { snapshot, error ->
                if (error != null) { close(error); return@addSnapshotListener }
                val stays = snapshot?.documents?.mapNotNull { it.toStay() }
                    ?.filter { it.status == "ACTIVE" } ?: emptyList()
                trySend(stays)
            }
        awaitClose { registration.remove() }
    }

    private fun com.google.cloud.firestore.DocumentSnapshot.toStay() = runCatching {
        Stay(
            id = id,
            hotelId = getString("hotelId") ?: "",
            bookingId = getString("bookingId") ?: "",
            userId = getString("userId") ?: "",
            userName = getString("userName") ?: "",
            guestName = getString("guestName") ?: "",
            guestPhone = getString("guestPhone") ?: "",
            roomInstanceId = getString("roomInstanceId") ?: "",
            roomNumber = getString("roomNumber") ?: "",
            roomCategoryId = getString("roomCategoryId") ?: "",
            roomCategoryName = getString("roomCategoryName") ?: "",
            checkInActual = getTimestamp("checkInActual")?.toDate() ?: Date(),
            trueCheckIn = getTimestamp("trueCheckIn")?.toDate(),
            expectedCheckOut = getTimestamp("expectedCheckOut")?.toDate() ?: Date(),
            checkOutActual = getTimestamp("checkOutActual")?.toDate(),
            status = getString("status") ?: "ACTIVE",
            adults = getLong("adults")?.toInt() ?: 1,
            children = getLong("children")?.toInt() ?: 0,
            breakfast = getBoolean("breakfast") ?: false,
            extraBed = getBoolean("extraBed") ?: false,
            earlyCheckIn = getBoolean("earlyCheckIn") ?: false,
            lateCheckOut = getBoolean("lateCheckOut") ?: false,
            earlyCheckInCharge = getDouble("earlyCheckInCharge") ?: 0.0,
            lateCheckOutCharge = getDouble("lateCheckOutCharge") ?: 0.0,
            advancePaidAmount = getDouble("advancePaidAmount") ?: getDouble("advanceAmount") ?: 0.0,
            totalBilled = getDouble("totalBilled") ?: 0.0,
            createdAt = getTimestamp("createdAt")?.toDate() ?: Date(),
            updatedAt = getTimestamp("updatedAt")?.toDate() ?: Date(),
            guests = (get("guests") as? List<*>)?.mapNotNull { entry ->
                (entry as? Map<*, *>)?.let {
                    GuestRecord(
                        name = it["name"] as? String ?: "",
                        phone = it["phone"] as? String ?: "",
                        idProofVerified = it["idProofVerified"] as? Boolean ?: false,
                        gender = it["gender"] as? String ?: "",
                        govIdNumber = it["govIdNumber"] as? String ?: "",
                        govIdPictures = (it["govIdPictures"] as? List<*>)?.filterIsInstance<String>() ?: emptyList(),
                        address = it["address"] as? String ?: "",
                        dob = it["dob"] as? String ?: "",
                        age = (it["age"] as? Number)?.toInt() ?: 0,
                        grcNumber = it["grcNumber"] as? String ?: "",
                    )
                }
            } ?: emptyList(),
            groupStayId = getString("groupStayId") ?: "",
        )
    }.getOrNull()

    private fun Stay.toMap() = mapOf(
        "hotelId" to hotelId,
        "bookingId" to bookingId,
        "userId" to userId,
        "userName" to userName,
        "guestName" to guestName,
        "guestPhone" to guestPhone,
        "roomInstanceId" to roomInstanceId,
        "roomNumber" to roomNumber,
        "roomCategoryId" to roomCategoryId,
        "roomCategoryName" to roomCategoryName,
        "checkInActual" to checkInActual,
        "trueCheckIn" to trueCheckIn,
        "expectedCheckOut" to expectedCheckOut,
        "checkOutActual" to checkOutActual,
        "status" to status,
        "adults" to adults,
        "children" to children,
        "breakfast" to breakfast,
        "extraBed" to extraBed,
        "earlyCheckIn" to earlyCheckIn,
        "lateCheckOut" to lateCheckOut,
        "earlyCheckInCharge" to earlyCheckInCharge,
        "lateCheckOutCharge" to lateCheckOutCharge,
        "advancePaidAmount" to advancePaidAmount,
        "totalBilled" to totalBilled,
        "createdAt" to createdAt,
        "updatedAt" to updatedAt,
        "guests" to guests.map { g ->
            mapOf("name" to g.name, "phone" to g.phone, "idProofVerified" to g.idProofVerified,
                "gender" to g.gender, "govIdNumber" to g.govIdNumber, "govIdPictures" to g.govIdPictures,
                "address" to g.address, "dob" to g.dob, "age" to g.age, "grcNumber" to g.grcNumber)
        },
        "groupStayId" to groupStayId,
    )
}
