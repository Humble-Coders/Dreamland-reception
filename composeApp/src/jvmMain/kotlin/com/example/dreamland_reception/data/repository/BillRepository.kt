package com.example.dreamland_reception.data.repository

import com.example.dreamland_reception.data.model.Bill
import com.example.dreamland_reception.data.model.BillItem
import com.example.dreamland_reception.data.model.PaymentTransaction
import com.google.cloud.firestore.Firestore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.Date

interface BillRepository {
    suspend fun getByStay(stayId: String): Bill?
    suspend fun getByHotel(hotelId: String): List<Bill>
    suspend fun createForStay(bill: Bill): String
    suspend fun updateItems(id: String, items: List<BillItem>, subtotal: Double, taxAmount: Double, discountAmount: Double, totalAmount: Double, pendingAmount: Double, status: String)
    suspend fun addTransaction(id: String, tx: PaymentTransaction, totalPaid: Double, pendingAmount: Double, status: String)
    suspend fun updateTaxDiscount(id: String, taxEnabled: Boolean, taxPercentage: Double, discountType: String, discountValue: Double, subtotal: Double, taxAmount: Double, discountAmount: Double, totalAmount: Double, pendingAmount: Double, status: String)
}

object FirestoreBillRepository : BillRepository {

    fun initialize(fs: Firestore) {
        FirestoreRepositorySupport.prime(fs)
    }

    private val col get() = FirestoreRepositorySupport.get().collection("bills")

    override suspend fun getByStay(stayId: String): Bill? = withContext(Dispatchers.IO) {
        col.whereEqualTo("stayId", stayId).get().get().documents.firstOrNull()?.toBill()
    }

    override suspend fun getByHotel(hotelId: String): List<Bill> = withContext(Dispatchers.IO) {
        col.whereEqualTo("hotelId", hotelId).get().get()
            .documents.mapNotNull { it.toBill() }
            .sortedBy { it.createdAt }
    }

    override suspend fun createForStay(bill: Bill): String = withContext(Dispatchers.IO) {
        col.add(bill.toMap()).get().id
    }

    override suspend fun updateItems(
        id: String, items: List<BillItem>,
        subtotal: Double, taxAmount: Double, discountAmount: Double,
        totalAmount: Double, pendingAmount: Double, status: String,
    ) = withContext(Dispatchers.IO) {
        col.document(id).update(mapOf(
            "items" to items.map { it.toMap() },
            "subtotal" to subtotal,
            "taxAmount" to taxAmount,
            "discountAmount" to discountAmount,
            "totalAmount" to totalAmount,
            "pendingAmount" to pendingAmount,
            "status" to status,
            "updatedAt" to Date(),
        )).get(); Unit
    }

    override suspend fun addTransaction(
        id: String, tx: PaymentTransaction,
        totalPaid: Double, pendingAmount: Double, status: String,
    ) = withContext(Dispatchers.IO) {
        val docRef = col.document(id)
        val existing = docRef.get().get().toBill() ?: return@withContext
        val updatedTxs = existing.transactions + tx
        docRef.update(mapOf(
            "transactions" to updatedTxs.map { it.toMap() },
            "totalPaid" to totalPaid,
            "pendingAmount" to pendingAmount,
            "status" to status,
            "updatedAt" to Date(),
        )).get(); Unit
    }

    override suspend fun updateTaxDiscount(
        id: String, taxEnabled: Boolean, taxPercentage: Double,
        discountType: String, discountValue: Double,
        subtotal: Double, taxAmount: Double, discountAmount: Double,
        totalAmount: Double, pendingAmount: Double, status: String,
    ) = withContext(Dispatchers.IO) {
        col.document(id).update(mapOf(
            "taxEnabled" to taxEnabled,
            "taxPercentage" to taxPercentage,
            "discountType" to discountType,
            "discountValue" to discountValue,
            "subtotal" to subtotal,
            "taxAmount" to taxAmount,
            "discountAmount" to discountAmount,
            "totalAmount" to totalAmount,
            "pendingAmount" to pendingAmount,
            "status" to status,
            "updatedAt" to Date(),
        )).get(); Unit
    }

    private fun com.google.cloud.firestore.DocumentSnapshot.toBill(): Bill? = runCatching {
        @Suppress("UNCHECKED_CAST")
        val itemsList = get("items") as? List<Map<String, Any>> ?: emptyList()
        @Suppress("UNCHECKED_CAST")
        val txList = get("transactions") as? List<Map<String, Any>> ?: emptyList()
        Bill(
            id = id,
            hotelId = getString("hotelId") ?: "",
            stayId = getString("stayId") ?: "",
            guestName = getString("guestName") ?: "",
            roomNumber = getString("roomNumber") ?: "",
            checkInDate = getTimestamp("checkInDate")?.toDate(),
            checkOutDate = getTimestamp("checkOutDate")?.toDate(),
            items = itemsList.map { it.toBillItem() },
            taxEnabled = getBoolean("taxEnabled") ?: false,
            taxPercentage = getDouble("taxPercentage") ?: 18.0,
            discountType = getString("discountType") ?: "FLAT",
            discountValue = getDouble("discountValue") ?: 0.0,
            subtotal = getDouble("subtotal") ?: 0.0,
            taxAmount = getDouble("taxAmount") ?: 0.0,
            discountAmount = getDouble("discountAmount") ?: 0.0,
            totalAmount = getDouble("totalAmount") ?: 0.0,
            transactions = txList.map { it.toPaymentTransaction() },
            totalPaid = getDouble("totalPaid") ?: 0.0,
            advancePayment = getDouble("advancePayment") ?: 0.0,
            pendingAmount = getDouble("pendingAmount") ?: 0.0,
            status = getString("status") ?: "PENDING",
            createdAt = getTimestamp("createdAt")?.toDate() ?: Date(),
            updatedAt = getTimestamp("updatedAt")?.toDate() ?: Date(),
        )
    }.getOrNull()

    private fun Map<String, Any>.toBillItem() = BillItem(
        id = get("id") as? String ?: "",
        name = get("name") as? String ?: "",
        type = get("type") as? String ?: "CUSTOM",
        quantity = (get("quantity") as? Long)?.toInt() ?: 1,
        unitPrice = (get("unitPrice") as? Double) ?: 0.0,
        total = (get("total") as? Double) ?: 0.0,
        refId = get("refId") as? String ?: "",
        notes = get("notes") as? String ?: "",
    )

    private fun Map<String, Any>.toPaymentTransaction() = PaymentTransaction(
        id = get("id") as? String ?: "",
        amount = (get("amount") as? Double) ?: 0.0,
        method = get("method") as? String ?: "CASH",
        status = get("status") as? String ?: "PAID",
        createdAt = (get("createdAt") as? com.google.cloud.Timestamp)?.toDate() ?: (get("createdAt") as? Date) ?: Date(),
    )

    private fun Bill.toMap() = mapOf(
        "hotelId" to hotelId,
        "stayId" to stayId,
        "guestName" to guestName,
        "roomNumber" to roomNumber,
        "checkInDate" to checkInDate,
        "checkOutDate" to checkOutDate,
        "items" to items.map { it.toMap() },
        "taxEnabled" to taxEnabled,
        "taxPercentage" to taxPercentage,
        "discountType" to discountType,
        "discountValue" to discountValue,
        "subtotal" to subtotal,
        "taxAmount" to taxAmount,
        "discountAmount" to discountAmount,
        "totalAmount" to totalAmount,
        "transactions" to transactions.map { it.toMap() },
        "totalPaid" to totalPaid,
        "advancePayment" to advancePayment,
        "pendingAmount" to pendingAmount,
        "status" to status,
        "createdAt" to createdAt,
        "updatedAt" to updatedAt,
    )

    private fun BillItem.toMap() = mapOf(
        "id" to id,
        "name" to name,
        "type" to type,
        "quantity" to quantity,
        "unitPrice" to unitPrice,
        "total" to total,
        "refId" to refId,
        "notes" to notes,
    )

    private fun PaymentTransaction.toMap() = mapOf(
        "id" to id,
        "amount" to amount,
        "method" to method,
        "status" to status,
        "createdAt" to createdAt,
    )
}
