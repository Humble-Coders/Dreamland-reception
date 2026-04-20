package com.example.dreamland_reception.data.repository

import com.example.dreamland_reception.data.model.FoodItem
import com.google.cloud.firestore.Firestore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.Date

interface FoodItemRepository {
    suspend fun getByHotel(hotelId: String): List<FoodItem>
    suspend fun add(item: FoodItem): String
    suspend fun update(item: FoodItem)
    suspend fun toggleAvailable(id: String, isAvailable: Boolean)
    suspend fun delete(id: String)
}

object FirestoreFoodItemRepository : FoodItemRepository {

    fun initialize(fs: Firestore) { FirestoreRepositorySupport.prime(fs) }

    private val col get() = FirestoreRepositorySupport.get().collection("foodItems")

    override suspend fun getByHotel(hotelId: String): List<FoodItem> = withContext(Dispatchers.IO) {
        col.whereEqualTo("hotelId", hotelId).get().get().documents.mapNotNull { it.toFoodItem() }
    }

    override suspend fun add(item: FoodItem): String = withContext(Dispatchers.IO) {
        col.add(item.toMap()).get().id
    }

    override suspend fun update(item: FoodItem) = withContext(Dispatchers.IO) {
        col.document(item.id).set(item.toMap()).get(); Unit
    }

    override suspend fun toggleAvailable(id: String, isAvailable: Boolean) = withContext(Dispatchers.IO) {
        col.document(id).update("isAvailable", isAvailable).get(); Unit
    }

    override suspend fun delete(id: String) = withContext(Dispatchers.IO) {
        col.document(id).delete().get(); Unit
    }

    private fun com.google.cloud.firestore.DocumentSnapshot.toFoodItem() = runCatching {
        FoodItem(
            id = id,
            hotelId = getString("hotelId") ?: "",
            name = getString("name") ?: "",
            price = getDouble("price") ?: 0.0,
            category = getString("category") ?: "",
            isAvailable = getBoolean("isAvailable") ?: true,
            createdAt = getTimestamp("createdAt")?.toDate() ?: Date(),
        )
    }.getOrNull()

    private fun FoodItem.toMap() = mapOf(
        "hotelId" to hotelId,
        "name" to name,
        "price" to price,
        "category" to category,
        "isAvailable" to isAvailable,
        "createdAt" to createdAt,
    )
}
