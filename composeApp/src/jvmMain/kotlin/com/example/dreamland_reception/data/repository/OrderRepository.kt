package com.example.dreamland_reception.data.repository

import com.example.dreamland_reception.data.model.Order
import com.example.dreamland_reception.data.model.OrderItem
import com.google.cloud.firestore.Firestore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.withContext
import java.util.Date

interface OrderRepository {
    suspend fun getAll(): List<Order>
    suspend fun getById(orderId: String): Order?
    suspend fun getPending(): List<Order>
    suspend fun getByStay(stayId: String): List<Order>
    suspend fun getByHotel(hotelId: String): List<Order>
    suspend fun add(order: Order): String
    suspend fun updateStatus(id: String, status: String)
    suspend fun updateAssignment(id: String, staffId: String, staffName: String)
    suspend fun delete(id: String)
    fun listenByHotel(hotelId: String): Flow<List<Order>>
    fun listenByStay(stayId: String): Flow<List<Order>>
}

object FirestoreOrderRepository : OrderRepository {

    fun initialize(fs: Firestore) {
        FirestoreRepositorySupport.prime(fs)
    }

    private val col get() = FirestoreRepositorySupport.get().collection("orders")

    override suspend fun getById(orderId: String): Order? = withContext(Dispatchers.IO) {
        col.document(orderId).get().get().toOrder()
    }

    override suspend fun getAll(): List<Order> = withContext(Dispatchers.IO) {
        // Sort client-side so legacy docs (which used `orderedAt`) are not dropped by an
        // orderBy on a field they may not have.
        col.get().get().documents.mapNotNull { it.toOrder() }.sortedBy { it.createdAt }
    }

    override suspend fun getPending(): List<Order> = withContext(Dispatchers.IO) {
        col.whereIn("status", listOf("NEW", "ASSIGNED"))
            .get().get().documents.mapNotNull { it.toOrder() }
    }

    override suspend fun getByStay(stayId: String): List<Order> = withContext(Dispatchers.IO) {
        col.whereEqualTo("stayId", stayId)
            .get().get().documents.mapNotNull { it.toOrder() }
            .sortedBy { it.createdAt }
    }

    override suspend fun getByHotel(hotelId: String): List<Order> = withContext(Dispatchers.IO) {
        col.whereEqualTo("hotelId", hotelId)
            .get().get().documents.mapNotNull { it.toOrder() }
            .sortedByDescending { it.createdAt }
    }

    override suspend fun add(order: Order): String = withContext(Dispatchers.IO) {
        col.add(order.toMap()).get().id
    }

    override suspend fun updateStatus(id: String, status: String) = withContext(Dispatchers.IO) {
        col.document(id).update(mapOf("status" to status)).get(); Unit
    }

    override suspend fun updateAssignment(id: String, staffId: String, staffName: String) =
        withContext(Dispatchers.IO) {
            col.document(id).update(mapOf(
                "assignedTo" to staffId,
                "assignedToName" to staffName,
                "status" to "ASSIGNED",
            )).get(); Unit
        }

    override suspend fun delete(id: String) = withContext(Dispatchers.IO) {
        col.document(id).delete().get(); Unit
    }

    override fun listenByHotel(hotelId: String): Flow<List<Order>> = callbackFlow {
        val registration = col.whereEqualTo("hotelId", hotelId)
            .addSnapshotListener { snapshot, error ->
                if (error != null) { close(error); return@addSnapshotListener }
                val orders = snapshot?.documents?.mapNotNull { it.toOrder() }
                    ?.sortedByDescending { it.createdAt } ?: emptyList()
                trySend(orders)
            }
        awaitClose { registration.remove() }
    }

    override fun listenByStay(stayId: String): Flow<List<Order>> = callbackFlow {
        val registration = col.whereEqualTo("stayId", stayId)
            .addSnapshotListener { snapshot, error ->
                if (error != null) { close(error); return@addSnapshotListener }
                val orders = snapshot?.documents?.mapNotNull { it.toOrder() }
                    ?.sortedBy { it.createdAt } ?: emptyList()
                trySend(orders)
            }
        awaitClose { registration.remove() }
    }

    @Suppress("UNCHECKED_CAST")
    private fun com.google.cloud.firestore.DocumentSnapshot.toOrder() = runCatching {
        val rawItems = (get("items") as? List<Map<String, Any>>) ?: emptyList()
        Order(
            id = id,
            hotelId = getString("hotelId") ?: "",
            userId = getString("userId") ?: "",
            stayId = getString("stayId") ?: "",
            groupStayId = getString("groupStayId") ?: "",
            roomInstanceId = getString("roomInstanceId") ?: "",
            roomNumber = getString("roomNumber") ?: "",
            guestName = getString("guestName") ?: "",
            guestPhone = getString("guestPhone") ?: "",
            items = rawItems.map { it.toOrderItem() },
            type = getString("type") ?: "ORDER",
            subtotalAmount = getDouble("subtotalAmount") ?: 0.0,
            totalTaxAmount = getDouble("totalTaxAmount") ?: 0.0,
            totalAmount = getDouble("totalAmount") ?: 0.0,
            status = getString("status") ?: "NEW",
            notes = getString("notes") ?: "",
            // `orderedAt` is the legacy field name; fall back to it for pre-rename docs.
            createdAt = getTimestamp("createdAt")?.toDate() ?: getTimestamp("orderedAt")?.toDate() ?: Date(),
            assignedTo = getString("assignedTo") ?: "",
            assignedToName = getString("assignedToName") ?: "",
        )
    }.getOrNull()

    /**
     * Maps a Firestore item map to [OrderItem], synthesising the tax breakdown for legacy
     * docs that only stored `{itemId?, name, price, quantity}` (per schema legacy-compat note):
     * `price → basePrice`, `taxedPrice = basePrice`, `taxAmount = 0`, `subtotal = total = basePrice × qty`.
     */
    private fun Map<String, Any>.toOrderItem(): OrderItem {
        val quantity = (get("quantity") as? Number)?.toInt() ?: 1
        val legacyPrice = (get("price") as? Number)?.toDouble()
        val basePrice = (get("basePrice") as? Number)?.toDouble() ?: legacyPrice ?: 0.0
        val taxPercentage = (get("taxPercentage") as? Number)?.toDouble() ?: 0.0
        val taxedPrice = (get("taxedPrice") as? Number)?.toDouble() ?: (basePrice * (1 + taxPercentage / 100.0))
        return OrderItem(
            itemId = get("itemId") as? String ?: "",
            name = get("name") as? String ?: "",
            quantity = quantity,
            basePrice = basePrice,
            taxPercentage = taxPercentage,
            taxedPrice = taxedPrice,
            taxAmount = (get("taxAmount") as? Number)?.toDouble() ?: ((taxedPrice - basePrice) * quantity),
            subtotal = (get("subtotal") as? Number)?.toDouble() ?: (basePrice * quantity),
            total = (get("total") as? Number)?.toDouble() ?: (taxedPrice * quantity),
        )
    }

    private fun Order.toMap() = mapOf(
        "hotelId" to hotelId,
        "userId" to userId,
        "stayId" to stayId,
        "groupStayId" to groupStayId,
        "roomInstanceId" to roomInstanceId,
        "roomNumber" to roomNumber,
        "guestName" to guestName,
        "guestPhone" to guestPhone,
        "items" to items.map { it.toMap() },
        "type" to type,
        "subtotalAmount" to subtotalAmount,
        "totalTaxAmount" to totalTaxAmount,
        "totalAmount" to totalAmount,
        "status" to status,
        "notes" to notes,
        "createdAt" to createdAt,
        "assignedTo" to assignedTo,
        "assignedToName" to assignedToName,
    )

    private fun OrderItem.toMap() = mapOf(
        "itemId" to itemId,
        "name" to name,
        "quantity" to quantity,
        "basePrice" to basePrice,
        "taxPercentage" to taxPercentage,
        "taxedPrice" to taxedPrice,
        "taxAmount" to taxAmount,
        "subtotal" to subtotal,
        "total" to total,
    )
}
