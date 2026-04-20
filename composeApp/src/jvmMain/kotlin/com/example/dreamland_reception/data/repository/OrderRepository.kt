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
    suspend fun getPending(): List<Order>
    suspend fun getByStay(stayId: String): List<Order>
    suspend fun getByHotel(hotelId: String): List<Order>
    suspend fun add(order: Order): String
    suspend fun updateStatus(id: String, status: String)
    suspend fun updateAssignment(id: String, staffId: String, staffName: String)
    fun listenByHotel(hotelId: String): Flow<List<Order>>
}

object FirestoreOrderRepository : OrderRepository {

    fun initialize(fs: Firestore) {
        FirestoreRepositorySupport.prime(fs)
    }

    private val col get() = FirestoreRepositorySupport.get().collection("orders")

    override suspend fun getAll(): List<Order> = withContext(Dispatchers.IO) {
        col.orderBy("orderedAt").get().get().documents.mapNotNull { it.toOrder() }
    }

    override suspend fun getPending(): List<Order> = withContext(Dispatchers.IO) {
        col.whereIn("status", listOf("NEW", "ASSIGNED"))
            .get().get().documents.mapNotNull { it.toOrder() }
    }

    override suspend fun getByStay(stayId: String): List<Order> = withContext(Dispatchers.IO) {
        col.whereEqualTo("stayId", stayId)
            .get().get().documents.mapNotNull { it.toOrder() }
            .sortedBy { it.orderedAt }
    }

    override suspend fun getByHotel(hotelId: String): List<Order> = withContext(Dispatchers.IO) {
        col.whereEqualTo("hotelId", hotelId)
            .get().get().documents.mapNotNull { it.toOrder() }
            .sortedByDescending { it.orderedAt }
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

    override fun listenByHotel(hotelId: String): Flow<List<Order>> = callbackFlow {
        val registration = col.whereEqualTo("hotelId", hotelId)
            .addSnapshotListener { snapshot, error ->
                if (error != null) { close(error); return@addSnapshotListener }
                val orders = snapshot?.documents?.mapNotNull { it.toOrder() }
                    ?.sortedByDescending { it.orderedAt } ?: emptyList()
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
            stayId = getString("stayId") ?: "",
            roomNumber = getString("roomNumber") ?: "",
            guestName = getString("guestName") ?: "",
            items = rawItems.map {
                OrderItem(
                    name = it["name"] as? String ?: "",
                    quantity = (it["quantity"] as? Long)?.toInt() ?: 1,
                    price = (it["price"] as? Double) ?: 0.0,
                )
            },
            type = getString("type") ?: "ORDER",
            totalAmount = getDouble("totalAmount") ?: 0.0,
            status = getString("status") ?: "NEW",
            notes = getString("notes") ?: "",
            orderedAt = getTimestamp("orderedAt")?.toDate() ?: Date(),
            assignedTo = getString("assignedTo") ?: "",
            assignedToName = getString("assignedToName") ?: "",
        )
    }.getOrNull()

    private fun Order.toMap() = mapOf(
        "hotelId" to hotelId,
        "stayId" to stayId,
        "roomNumber" to roomNumber,
        "guestName" to guestName,
        "items" to items.map { mapOf("name" to it.name, "quantity" to it.quantity, "price" to it.price) },
        "type" to type,
        "totalAmount" to totalAmount,
        "status" to status,
        "notes" to notes,
        "orderedAt" to orderedAt,
        "assignedTo" to assignedTo,
        "assignedToName" to assignedToName,
    )
}
