package com.example.dreamland_reception.data.repository

import com.example.dreamland_reception.data.model.Room
import com.example.dreamland_reception.data.model.SeasonalPricing
import com.google.cloud.firestore.Firestore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

interface RoomRepository {
    suspend fun getAll(): List<Room>
    suspend fun getByHotel(hotelId: String): List<Room>
    suspend fun getById(id: String): Room?
    suspend fun add(room: Room): String
    suspend fun update(room: Room)
    suspend fun updateStatus(id: String, status: String)
}

object FirestoreRoomRepository : RoomRepository {

    fun initialize(fs: Firestore) {
        FirestoreRepositorySupport.prime(fs)
    }

    private val col get() = FirestoreRepositorySupport.get().collection("rooms")

    override suspend fun getAll(): List<Room> = withContext(Dispatchers.IO) {
        col.orderBy("number").get().get().documents.mapNotNull { it.toRoom() }
    }

    override suspend fun getByHotel(hotelId: String): List<Room> = withContext(Dispatchers.IO) {
        col.whereEqualTo("hotelId", hotelId).get().get().documents.mapNotNull { it.toRoom() }
    }

    override suspend fun getById(id: String): Room? = withContext(Dispatchers.IO) {
        col.document(id).get().get().takeIf { it.exists() }?.toRoom()
    }

    override suspend fun add(room: Room): String = withContext(Dispatchers.IO) {
        col.add(room.toMap()).get().id
    }

    override suspend fun update(room: Room) = withContext(Dispatchers.IO) {
        col.document(room.id).set(room.toMap()).get(); Unit
    }

    override suspend fun updateStatus(id: String, status: String) = withContext(Dispatchers.IO) {
        col.document(id).update("status", status).get(); Unit
    }

    @Suppress("UNCHECKED_CAST")
    private fun com.google.cloud.firestore.DocumentSnapshot.toRoom() = runCatching {
        Room(
            id = id,
            hotelId = getString("hotelId") ?: "",
            number = getString("number") ?: "",
            // Firestore stores room category name in "name" field
            type = getString("name") ?: getString("type") ?: "",
            // "floor" may be stored as a string ("2") or a number — handle both
            floor = when (val f = get("floor")) {
                is Long -> f.toInt()
                is Number -> f.toInt()
                is String -> f.toIntOrNull() ?: 1
                else -> 1
            },
            // "maxOccupancy" is the Firestore field; fall back to "capacity"
            capacity = (getLong("maxOccupancy") ?: getLong("capacity"))?.toInt() ?: 2,
            // "price" is the Firestore field; fall back to "pricePerNight"
            pricePerNight = getDouble("price") ?: getDouble("pricePerNight") ?: 0.0,
            breakfastPrice = getDouble("breakfastPrice") ?: 0.0,
            status = getString("status") ?: "available",
            amenities = (get("amenities") as? List<String>) ?: emptyList(),
            description = getString("description") ?: "",
        )
    }.getOrNull()

    private fun Room.toMap() = mapOf(
        "hotelId" to hotelId,
        "number" to number,
        "name" to type,
        "floor" to floor.toString(),
        "maxOccupancy" to capacity,
        "price" to pricePerNight,
        "breakfastPrice" to breakfastPrice,
        "status" to status,
        "amenities" to amenities,
        "description" to description,
    )
}
