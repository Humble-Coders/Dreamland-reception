package com.example.dreamland_reception.data.repository

import com.example.dreamland_reception.data.firebase.CloudFunctionClient
import com.example.dreamland_reception.data.model.Booking
import com.google.cloud.firestore.Firestore
import com.google.gson.JsonParser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.withContext
import java.util.Date

/** Reception's chosen refund amount source for a cancellation. */
enum class ReceptionRefundMode { POLICY, FULL, FIXED }

/** Decoded response from cancelBookingByReceptionHttp. */
data class CancelBookingResult(
    val groupBookingId: String,
    val totalRefundPaise: Long,
    val refundId: String,
    val finalRefundStatus: String, // "" | "INITIATED" | "COMPLETED" | "FAILED"
    val perRoom: List<PerRoom>,
) {
    data class PerRoom(val bookingId: String, val refundPaise: Long, val bucket: String)
}

interface BookingRepository {
    suspend fun getAll(): List<Booking>
    suspend fun getAllByHotel(hotelId: String): List<Booking>
    suspend fun getById(id: String): Booking?
    suspend fun getUpcoming(hotelId: String): List<Booking>
    suspend fun getConfirmedByHotel(hotelId: String): List<Booking>
    suspend fun add(booking: Booking): String
    suspend fun update(booking: Booking)
    suspend fun delete(id: String)
    suspend fun assignRoomTransaction(bookingId: String, roomInstanceId: String, roomNumber: String)
    suspend fun markNoShow(bookingId: String, markedAt: Date, refundStatus: String, refundNote: String)
    /**
     * Calls cancelBookingByReceptionHttp to cancel every booking in the group + trigger
     * the Razorpay refund according to [refundMode]. Throws [CloudFunctionClient.CancelByReceptionException]
     * on any non-2xx response. Idempotent on [groupBookingId].
     */
    suspend fun cancelByReception(
        userId: String,
        groupBookingId: String,
        reason: String,
        refundMode: ReceptionRefundMode,
        fixedRefundPaise: Long?,
        cancelledByReceptionUserId: String,
    ): CancelBookingResult
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

    override suspend fun getAllByHotel(hotelId: String): List<Booking> = withContext(Dispatchers.IO) {
        col.whereEqualTo("hotelId", hotelId).get().get().documents.mapNotNull { it.toBooking() }
    }

    override suspend fun getById(id: String): Booking? = withContext(Dispatchers.IO) {
        col.document(id).get().get().takeIf { it.exists() }?.toBooking()
    }

    override suspend fun getConfirmedByHotel(hotelId: String): List<Booking> = withContext(Dispatchers.IO) {
        col.whereEqualTo("hotelId", hotelId).get().get()
            .documents.mapNotNull { it.toBooking() }
            .filter { it.status == "CONFIRMED" || it.status == "PENDING_PAYMENT" }
    }

    /** Returns CONFIRMED/PENDING_PAYMENT bookings for [hotelId] with checkIn within ±2 days of today. */
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

    override suspend fun assignRoomTransaction(
        bookingId: String,
        roomInstanceId: String,
        roomNumber: String,
    ) = withContext(Dispatchers.IO) {
        val fs = FirestoreRepositorySupport.get()
        fs.runTransaction { txn ->
            val bookingRef = col.document(bookingId)
            val bookingDoc = txn.get(bookingRef).get()
            if (bookingDoc.getString("status") == "CANCELLED")
                throw Exception("Booking is cancelled")
            val roomRef = fs.collection("roomInstances").document(roomInstanceId)
            val roomDoc = txn.get(roomRef).get()
            if (roomDoc.getString("status") == "OCCUPIED")
                throw Exception("Room $roomNumber is currently occupied")
            txn.update(bookingRef, mapOf("roomInstanceId" to roomInstanceId, "roomNumber" to roomNumber))
        }.get(); Unit
    }

    override suspend fun markNoShow(
        bookingId: String,
        markedAt: Date,
        refundStatus: String,
        refundNote: String,
    ) = withContext(Dispatchers.IO) {
        col.document(bookingId).update(
            mapOf(
                "status" to "NO_SHOW",
                "noShowMarkedAt" to markedAt,
                "noShowRefundStatus" to refundStatus,
                "noShowRefundNote" to refundNote,
            ),
        ).get(); Unit
    }

    override suspend fun cancelByReception(
        userId: String,
        groupBookingId: String,
        reason: String,
        refundMode: ReceptionRefundMode,
        fixedRefundPaise: Long?,
        cancelledByReceptionUserId: String,
    ): CancelBookingResult = withContext(Dispatchers.IO) {
        val body = buildString {
            append("{")
            append("\"userId\":${esc(userId)},")
            append("\"groupBookingId\":${esc(groupBookingId)},")
            append("\"reason\":${esc(reason)},")
            append("\"refundMode\":${esc(refundMode.name)},")
            if (refundMode == ReceptionRefundMode.FIXED && fixedRefundPaise != null) {
                append("\"fixedRefundPaise\":$fixedRefundPaise,")
            }
            append("\"cancelledByReceptionUserId\":${esc(cancelledByReceptionUserId)}")
            append("}")
        }
        val responseJson = CloudFunctionClient.callCancelByReception(body)
        parseCancelBookingResult(responseJson)
    }

    private fun esc(s: String): String {
        val sb = StringBuilder("\"")
        for (c in s) {
            when (c) {
                '\\' -> sb.append("\\\\")
                '"'  -> sb.append("\\\"")
                '\n' -> sb.append("\\n")
                '\r' -> sb.append("\\r")
                '\t' -> sb.append("\\t")
                '\b' -> sb.append("\\b")
                else -> if (c.code < 0x20) sb.append("\\u%04x".format(c.code)) else sb.append(c)
            }
        }
        sb.append("\"")
        return sb.toString()
    }

    private fun parseCancelBookingResult(json: String): CancelBookingResult {
        val root = JsonParser.parseString(json).asJsonObject
        val perRoom = root.getAsJsonArray("perRoom")?.map {
            val o = it.asJsonObject
            CancelBookingResult.PerRoom(
                bookingId   = o.get("bookingId")?.asString.orEmpty(),
                refundPaise = o.get("refundPaise")?.asLong ?: 0L,
                bucket      = o.get("bucket")?.asString.orEmpty(),
            )
        } ?: emptyList()
        return CancelBookingResult(
            groupBookingId    = root.get("groupBookingId")?.asString.orEmpty(),
            totalRefundPaise  = root.get("totalRefundPaise")?.asLong ?: 0L,
            refundId          = root.get("refundId")?.asString.orEmpty(),
            finalRefundStatus = root.get("finalRefundStatus")?.asString.orEmpty(),
            perRoom           = perRoom,
        )
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
        val allGuests = (get("allGuests") as? List<*>)?.mapNotNull { entry ->
            (entry as? Map<*, *>)?.let {
                com.example.dreamland_reception.data.model.GuestDetail(
                    name = it["name"] as? String ?: "",
                    phone = it["phone"] as? String ?: "",
                    idProofVerified = it["idProofVerified"] as? Boolean ?: false,
                    gender = it["gender"] as? String ?: "",
                    govIdNumber = it["govIdNumber"] as? String ?: "",
                    address = it["address"] as? String ?: "",
                    dob = it["dob"] as? String ?: "",
                    age = (it["age"] as? Number)?.toInt() ?: 0,
                )
            }
        } ?: emptyList()
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
            allGuestDetails = allGuests,
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
            sourceId = getString("sourceId") ?: "",
            totalAmount = getDouble("totalAmount") ?: 0.0,
            advancePaidAmount = getDouble("advancePaidAmount") ?: 0.0,
            advancePaymentMethod = getString("advancePaymentMethod") ?: "CASH",
            notes = getString("notes") ?: "",
            groupBookingId = getString("groupBookingId") ?: "",
            createdAt = getTimestamp("createdAt")?.toDate() ?: Date(),
            noShowMarkedAt = getTimestamp("noShowMarkedAt")?.toDate(),
            noShowRefundStatus = getString("noShowRefundStatus") ?: "",
            noShowRefundNote = getString("noShowRefundNote") ?: "",
            paymentOrderId = getString("paymentOrderId") ?: "",
            cancellationLockedAt = getTimestamp("cancellationLockedAt")?.toDate(),
            advancePaidAmountPaise = (get("advancePaidAmountPaise") as? Number)?.toLong() ?: 0L,
            cancellationSource = getString("cancellationSource") ?: "",
            cancellationReason = getString("cancellationReason") ?: "",
            cancelledByReceptionUserId = getString("cancelledByReceptionUserId") ?: "",
            cancellationRefundId = getString("cancellationRefundId") ?: "",
            cancellationRefundAmountPaise = (get("cancellationRefundAmountPaise") as? Number)?.toLong() ?: 0L,
            cancellationRefundStatus = getString("cancellationRefundStatus") ?: "",
            cancellationRefundMode = getString("cancellationRefundMode") ?: "",
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
        "sourceId" to sourceId,
        "totalAmount" to totalAmount,
        "advancePaidAmount" to advancePaidAmount,
        "advancePaymentMethod" to advancePaymentMethod,
        "notes" to notes,
        "groupBookingId" to groupBookingId,
        "createdAt" to createdAt,
        "noShowMarkedAt" to noShowMarkedAt,
        "noShowRefundStatus" to noShowRefundStatus,
        "noShowRefundNote" to noShowRefundNote,
    )
}
