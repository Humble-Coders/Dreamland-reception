package com.example.dreamland_reception.data.repository

import com.example.dreamland_reception.data.model.Bill
import com.example.dreamland_reception.data.model.BillItem
import com.example.dreamland_reception.data.model.PaymentTransaction
import com.google.cloud.firestore.Firestore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.withContext
import java.util.Date

interface BillRepository {
    suspend fun getByStay(stayId: String): Bill?
    suspend fun getById(billId: String): Bill?
    suspend fun getByHotel(hotelId: String): List<Bill>
    fun listenByHotel(hotelId: String): Flow<List<Bill>>
    suspend fun createForStay(bill: Bill): String
    suspend fun updateItems(id: String, items: List<BillItem>, subtotal: Double, taxAmount: Double, discountAmount: Double, totalAmount: Double, pendingAmount: Double, status: String)
    suspend fun addTransaction(id: String, tx: PaymentTransaction, totalPaid: Double, pendingAmount: Double, status: String)
    suspend fun updateTransactions(id: String, transactions: List<PaymentTransaction>, totalPaid: Double, pendingAmount: Double, status: String)
    suspend fun updateTaxDiscount(id: String, taxEnabled: Boolean, taxPercentage: Double, discountType: String, discountValue: Double, subtotal: Double, taxAmount: Double, discountAmount: Double, totalAmount: Double, pendingAmount: Double, status: String)
    suspend fun updateDates(id: String, checkIn: Date, checkOut: Date)
    suspend fun updateGuestName(id: String, name: String)
    suspend fun updateAdvancePaid(id: String, advancePayment: Double, pendingAmount: Double, status: String)
    suspend fun finalizeTransaction(
        id: String, items: List<BillItem>,
        subtotal: Double, taxAmount: Double, discountAmount: Double, totalAmount: Double,
        transactions: List<PaymentTransaction>, totalPaid: Double, pendingAmount: Double, status: String,
    )
    suspend fun updateInvoiceUrl(id: String, url: String)
    suspend fun markLedgerSynced(id: String, invoiceId: String, invoiceNumber: String)
    suspend fun markLedgerSyncFailed(id: String, error: String)
    suspend fun getUnsyncedFinalizedByHotel(hotelId: String): List<Bill>
}

object FirestoreBillRepository : BillRepository {

    fun initialize(fs: Firestore) {
        FirestoreRepositorySupport.prime(fs)
    }

    private val col get() = FirestoreRepositorySupport.get().collection("bills")

    override suspend fun getByStay(stayId: String): Bill? = withContext(Dispatchers.IO) {
        // Try primary stayId first, then fall back to stayIds array (group bills)
        col.whereEqualTo("stayId", stayId).get().get().documents.firstOrNull()?.toBill()
            ?: col.whereArrayContains("stayIds", stayId).get().get().documents.firstOrNull()?.toBill()
    }

    override suspend fun getById(billId: String): Bill? = withContext(Dispatchers.IO) {
        col.document(billId).get().get().toBill()
    }

    override suspend fun getByHotel(hotelId: String): List<Bill> = withContext(Dispatchers.IO) {
        col.whereEqualTo("hotelId", hotelId).get().get()
            .documents.mapNotNull { it.toBill() }
            .sortedBy { it.createdAt }
    }

    override fun listenByHotel(hotelId: String): Flow<List<Bill>> = callbackFlow {
        val registration = col.whereEqualTo("hotelId", hotelId)
            .addSnapshotListener { snapshot, error ->
                if (error != null) { close(error); return@addSnapshotListener }
                val bills = snapshot?.documents?.mapNotNull { it.toBill() }
                    ?.sortedBy { it.createdAt } ?: emptyList()
                trySend(bills)
            }
        awaitClose { registration.remove() }
    }

    override suspend fun createForStay(bill: Bill): String = withContext(Dispatchers.IO) {
        col.add(bill.toMap()).get().id
    }

    override suspend fun updateItems(
        id: String, items: List<BillItem>,
        subtotal: Double, taxAmount: Double, discountAmount: Double,
        totalAmount: Double, pendingAmount: Double, status: String,
    ) = withContext(Dispatchers.IO) {
        val effectiveTaxPct = if (subtotal > 0.0) Math.round(taxAmount / subtotal * 10000.0) / 100.0 else 0.0
        col.document(id).update(mapOf(
            "items" to items.map { it.toMap() },
            "subtotal" to subtotal,
            "taxAmount" to taxAmount,
            "taxPercentage" to effectiveTaxPct,
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

    override suspend fun updateTransactions(
        id: String,
        transactions: List<PaymentTransaction>,
        totalPaid: Double,
        pendingAmount: Double,
        status: String,
    ) = withContext(Dispatchers.IO) {
        col.document(id).update(mapOf(
            "transactions" to transactions.map { it.toMap() },
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
        @Suppress("UNCHECKED_CAST")
        val stayIdsList = get("stayIds") as? List<String> ?: emptyList()
        @Suppress("UNCHECKED_CAST")
        val roomNumbersList = get("roomNumbers") as? List<String> ?: emptyList()
        Bill(
            id = id,
            hotelId = getString("hotelId") ?: "",
            stayId = getString("stayId") ?: "",
            stayIds = stayIdsList,
            guestName = getString("guestName") ?: "",
            roomNumber = getString("roomNumber") ?: "",
            roomNumbers = roomNumbersList,
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
            invoiceUrl = getString("invoiceUrl") ?: "",
            ledgerSynced = getBoolean("ledgerSynced") ?: false,
            ledgerSyncError = getString("ledgerSyncError") ?: "",
            ledgerInvoiceId = getString("ledgerInvoiceId") ?: "",
            ledgerInvoiceNumber = getString("ledgerInvoiceNumber") ?: "",
            createdAt = getTimestamp("createdAt")?.toDate() ?: Date(),
            updatedAt = getTimestamp("updatedAt")?.toDate() ?: Date(),
        )
    }.getOrNull()

    private fun Map<String, Any>.toBillItem() = BillItem(
        id = get("id") as? String ?: "",
        name = get("name") as? String ?: "",
        type = get("type") as? String ?: "CUSTOM",
        quantity = (get("quantity") as? Long)?.toInt() ?: 1,
        unitPrice = (get("unitPrice") as? Number)?.toDouble() ?: 0.0,
        total = (get("total") as? Number)?.toDouble() ?: 0.0,
        taxPercentage = (get("taxPercentage") as? Number)?.toDouble() ?: 0.0,
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

    override suspend fun updateDates(id: String, checkIn: Date, checkOut: Date) = withContext(Dispatchers.IO) {
        col.document(id).update(mapOf(
            "checkInDate" to checkIn,
            "checkOutDate" to checkOut,
            "updatedAt" to Date(),
        )).get(); Unit
    }

    override suspend fun updateGuestName(id: String, name: String) = withContext(Dispatchers.IO) {
        col.document(id).update(mapOf("guestName" to name, "updatedAt" to Date())).get(); Unit
    }

    override suspend fun updateAdvancePaid(id: String, advancePayment: Double, pendingAmount: Double, status: String) = withContext(Dispatchers.IO) {
        col.document(id).update(mapOf(
            "advancePayment" to advancePayment,
            "pendingAmount" to pendingAmount,
            "status" to status,
            "updatedAt" to Date(),
        )).get(); Unit
    }

    override suspend fun finalizeTransaction(
        id: String, items: List<BillItem>,
        subtotal: Double, taxAmount: Double, discountAmount: Double, totalAmount: Double,
        transactions: List<PaymentTransaction>, totalPaid: Double, pendingAmount: Double, status: String,
    ) = withContext(Dispatchers.IO) {
        val fs = FirestoreRepositorySupport.get()
        val effectiveTaxPct = if (subtotal > 0.0) Math.round(taxAmount / subtotal * 10000.0) / 100.0 else 0.0
        fs.runTransaction { txn ->
            val ref = col.document(id)
            txn.update(ref, mapOf(
                "items"         to items.map { it.toMap() },
                "subtotal"      to subtotal,
                "taxAmount"     to taxAmount,
                "taxPercentage" to effectiveTaxPct,
                "discountAmount" to discountAmount,
                "totalAmount"   to totalAmount,
                "transactions"  to transactions.map { it.toMap() },
                "totalPaid"     to totalPaid,
                "pendingAmount" to pendingAmount,
                "status"        to status,
                "updatedAt"     to Date(),
            ))
        }.get(); Unit
    }

    override suspend fun updateInvoiceUrl(id: String, url: String) = withContext(Dispatchers.IO) {
        col.document(id).update(mapOf("invoiceUrl" to url, "updatedAt" to Date())).get(); Unit
    }

    override suspend fun markLedgerSynced(id: String, invoiceId: String, invoiceNumber: String) = withContext(Dispatchers.IO) {
        col.document(id).update(mapOf(
            "ledgerSynced" to true,
            "ledgerSyncError" to "",
            "ledgerInvoiceId" to invoiceId,
            "ledgerInvoiceNumber" to invoiceNumber,
            "updatedAt" to Date(),
        )).get(); Unit
    }

    override suspend fun markLedgerSyncFailed(id: String, error: String) = withContext(Dispatchers.IO) {
        col.document(id).update(mapOf(
            "ledgerSynced" to false,
            "ledgerSyncError" to error.take(500),
            "updatedAt" to Date(),
        )).get(); Unit
    }

    override suspend fun getUnsyncedFinalizedByHotel(hotelId: String): List<Bill> = withContext(Dispatchers.IO) {
        // Finalized bills (have payment activity) that never synced to the ledger.
        // Re-running settle() for these is safe — sourceIds are deterministic, so
        // Humble Ledger dedupes any leg that already landed.
        col.whereEqualTo("hotelId", hotelId)
            .whereEqualTo("ledgerSynced", false)
            .get().get()
            .documents.mapNotNull { it.toBill() }
            .filter { it.id.isNotBlank() && it.status != "PENDING" }
    }

    private fun Bill.toMap() = mapOf(
        "hotelId" to hotelId,
        "stayId" to stayId,
        "stayIds" to stayIds,
        "guestName" to guestName,
        "roomNumber" to roomNumber,
        "roomNumbers" to roomNumbers,
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
        "invoiceUrl" to invoiceUrl,
        "ledgerSynced" to ledgerSynced,
        "ledgerSyncError" to ledgerSyncError,
        "ledgerInvoiceId" to ledgerInvoiceId,
        "ledgerInvoiceNumber" to ledgerInvoiceNumber,
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
        "taxPercentage" to taxPercentage,
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
