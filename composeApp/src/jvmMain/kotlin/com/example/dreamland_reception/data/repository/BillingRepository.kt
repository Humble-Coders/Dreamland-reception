package com.example.dreamland_reception.data.repository

import com.example.dreamland_reception.data.model.BillingInvoice
import com.google.cloud.firestore.Firestore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.Date

interface BillingRepository {
    suspend fun getAll(): List<BillingInvoice>
    suspend fun getByBooking(bookingId: String): BillingInvoice?
    suspend fun getByStay(stayId: String): BillingInvoice?
    suspend fun add(invoice: BillingInvoice): String
    suspend fun markPaid(id: String, method: String)
    suspend fun updateCharges(id: String, earlyCheckInCharge: Double, lateCheckOutCharge: Double, totalAmount: Double)
}

object FirestoreBillingRepository : BillingRepository {

    fun initialize(fs: Firestore) {
        FirestoreRepositorySupport.prime(fs)
    }

    private val col get() = FirestoreRepositorySupport.get().collection("billing")

    override suspend fun getAll(): List<BillingInvoice> = withContext(Dispatchers.IO) {
        col.orderBy("issuedAt").get().get().documents.mapNotNull { it.toInvoice() }
    }

    override suspend fun getByBooking(bookingId: String): BillingInvoice? = withContext(Dispatchers.IO) {
        col.whereEqualTo("bookingId", bookingId).get().get()
            .documents.firstOrNull()?.toInvoice()
    }

    override suspend fun getByStay(stayId: String): BillingInvoice? = withContext(Dispatchers.IO) {
        col.whereEqualTo("stayId", stayId).get().get()
            .documents.firstOrNull()?.toInvoice()
    }

    override suspend fun add(invoice: BillingInvoice): String = withContext(Dispatchers.IO) {
        col.add(invoice.toMap()).get().id
    }

    override suspend fun markPaid(id: String, method: String) = withContext(Dispatchers.IO) {
        col.document(id).update(
            mapOf("status" to "PAID", "paymentMethod" to method, "paidAt" to Date()),
        ).get(); Unit
    }

    override suspend fun updateCharges(
        id: String,
        earlyCheckInCharge: Double,
        lateCheckOutCharge: Double,
        totalAmount: Double,
    ) = withContext(Dispatchers.IO) {
        col.document(id).update(mapOf(
            "earlyCheckInCharge" to earlyCheckInCharge,
            "lateCheckOutCharge" to lateCheckOutCharge,
            "totalAmount" to totalAmount,
        )).get(); Unit
    }

    private fun com.google.cloud.firestore.DocumentSnapshot.toInvoice() = runCatching {
        BillingInvoice(
            id = id,
            bookingId = getString("bookingId") ?: "",
            stayId = getString("stayId") ?: "",
            guestName = getString("guestName") ?: "",
            roomNumber = getString("roomNumber") ?: "",
            roomCharges = getDouble("roomCharges") ?: 0.0,
            serviceCharges = getDouble("serviceCharges") ?: 0.0,
            earlyCheckInCharge = getDouble("earlyCheckInCharge") ?: 0.0,
            lateCheckOutCharge = getDouble("lateCheckOutCharge") ?: 0.0,
            tax = getDouble("tax") ?: 0.0,
            discount = getDouble("discount") ?: 0.0,
            totalAmount = getDouble("totalAmount") ?: 0.0,
            amountPaid = getDouble("amountPaid") ?: 0.0,
            paymentMethod = getString("paymentMethod") ?: "",
            status = getString("status") ?: "PENDING",
            issuedAt = getTimestamp("issuedAt")?.toDate() ?: Date(),
            paidAt = getTimestamp("paidAt")?.toDate(),
        )
    }.getOrNull()

    private fun BillingInvoice.toMap() = mapOf(
        "bookingId" to bookingId,
        "stayId" to stayId,
        "guestName" to guestName,
        "roomNumber" to roomNumber,
        "roomCharges" to roomCharges,
        "serviceCharges" to serviceCharges,
        "earlyCheckInCharge" to earlyCheckInCharge,
        "lateCheckOutCharge" to lateCheckOutCharge,
        "tax" to tax,
        "discount" to discount,
        "totalAmount" to totalAmount,
        "amountPaid" to amountPaid,
        "paymentMethod" to paymentMethod,
        "status" to status,
        "issuedAt" to issuedAt,
        "paidAt" to paidAt,
    )
}
