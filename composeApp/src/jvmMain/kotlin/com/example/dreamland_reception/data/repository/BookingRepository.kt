package com.example.dreamland_reception.data.repository

import com.example.dreamland_reception.data.model.Booking
import com.google.cloud.firestore.Firestore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.withContext
import java.util.Date

interface BookingRepository {
    suspend fun getAll(): List<Booking>
    suspend fun getById(id: String): Booking?
    suspend fun getUpcoming(hotelId: String): List<Booking>
    suspend fun add(booking: Booking): String
    suspend fun update(booking: Booking)
    suspend fun delete(id: String)
    fun listenByHotel(hotelId: String): Flow<List<Booking>>
}

object FirestoreBookingRepository : BookingRepository {

    fun initialize(fs: Firestore) {
        FirestoreRepositorySupport.prime(fs)
    }

    private val col get() = FirestoreRepositorySupport.get().collection("bookings")

    override suspend fun getAll(): List<Booking> = withContext(Dispatchers.IO) {
        col.orderBy("checkIn").get().get().documents.mapNotNull { it.toBooking() }
    }

    override suspend fun getById(id: String): Booking? = withContext(Dispatchers.IO) {
        col.document(id).get().get().takeIf { it.exists() }?.toBooking()
    }

    /** Returns CONFIRMED bookings for [hotelId] with checkIn within ±2 days of today. */
    override suspend fun getUpcoming(hotelId: String): List<Booking> = withContext(Dispatchers.IO) {
        val cal = java.util.Calendar.getInstance()
        cal.add(java.util.Calendar.DAY_OF_YEAR, -1)
        val from = cal.time
        cal.add(java.util.Calendar.DAY_OF_YEAR, 3)
        val to = cal.time
        col.whereEqualTo("hotelId", hotelId)
            .get().get().documents.mapNotNull { it.toBooking() }
            .filter { it.status == "CONFIRMED" && it.checkIn >= from && it.checkIn <= to }
    }

    override suspend fun add(booking: Booking): String = withContext(Dispatchers.IO) {
        col.add(booking.toMap()).get().id
    }

    override suspend fun update(booking: Booking) = withContext(Dispatchers.IO) {
        col.document(booking.id).set(booking.toMap()).get(); Unit
    }

    override suspend fun delete(id: String) = withContext(Dispatchers.IO) {
        col.document(id).delete().get(); Unit
    }

    override fun listenByHotel(hotelId: String): Flow<List<Booking>> = callbackFlow {
        val registration = col.whereEqualTo("hotelId", hotelId)
            .addSnapshotListener { snapshot, error ->
                if (error != null) { close(error); return@addSnapshotListener }
                val bookings = snapshot?.documents?.mapNotNull { it.toBooking() } ?: emptyList()
                trySend(bookings)
            }
        awaitClose { registration.remove() }
    }

    private fun com.google.cloud.firestore.DocumentSnapshot.toBooking() = runCatching {
        val guestDetails = get("guestDetails") as? Map<*, *>
        val guests = get("guests") as? Map<*, *>
        val options = get("options") as? Map<*, *>
        val breakfast = options?.get("breakfast") as? Map<*, *>
        val earlyCheckIn = options?.get("earlyCheckIn") as? Map<*, *>
        val lateCheckOut = options?.get("lateCheckOut") as? Map<*, *>
        Booking(
            id = id,
            hotelId = getString("hotelId") ?: "",
            hotelName = getString("hotelName") ?: "",
            userId = getString("userId") ?: "",
            userName = getString("userName") ?: "",
            guestName = guestDetails?.get("name") as? String ?: "",
            guestPhone = guestDetails?.get("phone") as? String ?: "",
            roomCategoryId = getString("roomCategoryId") ?: "",
            roomCategoryName = getString("roomCategoryName") ?: "",
            roomInstanceId = getString("roomInstanceId") ?: "",
            roomNumber = getString("roomNumber") ?: "",
            checkIn = getTimestamp("checkInDate")?.toDate() ?: Date(),
            checkOut = getTimestamp("checkOutDate")?.toDate() ?: Date(),
            adults = (guests?.get("adults") as? Long)?.toInt() ?: 1,
            children = (guests?.get("children") as? Long)?.toInt() ?: 0,
            breakfastIncluded = breakfast?.get("included") as? Boolean ?: false,
            breakfastPricePerDay = (breakfast?.get("pricePerDay") as? Number)?.toDouble() ?: 0.0,
            earlyCheckInEnabled = earlyCheckIn?.get("enabled") as? Boolean ?: false,
            earlyCheckInCharge = (earlyCheckIn?.get("charge") as? Number)?.toDouble() ?: 0.0,
            lateCheckOutEnabled = lateCheckOut?.get("enabled") as? Boolean ?: false,
            lateCheckOutCharge = (lateCheckOut?.get("charge") as? Number)?.toDouble() ?: 0.0,
            status = getString("status") ?: "CONFIRMED",
            source = getString("source") ?: "APP",
            totalAmount = getDouble("totalAmount") ?: 0.0,
            advancePaidAmount = getDouble("advancePaidAmount") ?: 0.0,
            notes = getString("notes") ?: "",
            createdAt = getTimestamp("createdAt")?.toDate() ?: Date(),
        )
    }.getOrNull()

    private fun Booking.toMap() = mapOf(
        "hotelId" to hotelId,
        "hotelName" to hotelName,
        "userId" to userId,
        "userName" to userName,
        "guestDetails" to mapOf(
            "name" to guestName,
            "phone" to guestPhone,
        ),
        "guests" to mapOf(
            "adults" to adults,
            "children" to children,
        ),
        "options" to mapOf(
            "breakfast" to mapOf(
                "included" to breakfastIncluded,
                "pricePerDay" to breakfastPricePerDay,
            ),
            "earlyCheckIn" to mapOf(
                "enabled" to earlyCheckInEnabled,
                "charge" to earlyCheckInCharge,
            ),
            "lateCheckOut" to mapOf(
                "enabled" to lateCheckOutEnabled,
                "charge" to lateCheckOutCharge,
            ),
        ),
        "roomCategoryId" to roomCategoryId,
        "roomCategoryName" to roomCategoryName,
        "roomInstanceId" to roomInstanceId,
        "roomNumber" to roomNumber,
        "checkInDate" to checkIn,
        "checkOutDate" to checkOut,
        "status" to status,
        "source" to source,
        "totalAmount" to totalAmount,
        "advancePaidAmount" to advancePaidAmount,
        "notes" to notes,
        "createdAt" to createdAt,
    )
}
