package com.example.dreamland_reception.data.repository

import com.example.dreamland_reception.data.model.Service
import com.google.cloud.firestore.Firestore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.Date

interface ServiceRepository {
    suspend fun getByHotel(hotelId: String): List<Service>
    suspend fun add(service: Service): String
    suspend fun update(service: Service)
    suspend fun toggleActive(id: String, isActive: Boolean)
    suspend fun delete(id: String)
}

object FirestoreServiceRepository : ServiceRepository {

    fun initialize(fs: Firestore) { FirestoreRepositorySupport.prime(fs) }

    private val col get() = FirestoreRepositorySupport.get().collection("services")

    override suspend fun getByHotel(hotelId: String): List<Service> = withContext(Dispatchers.IO) {
        col.whereEqualTo("hotelId", hotelId).get().get().documents.mapNotNull { it.toService() }
    }

    override suspend fun add(service: Service): String = withContext(Dispatchers.IO) {
        col.add(service.toMap()).get().id
    }

    override suspend fun update(service: Service) = withContext(Dispatchers.IO) {
        col.document(service.id).set(service.toMap()).get(); Unit
    }

    override suspend fun toggleActive(id: String, isActive: Boolean) = withContext(Dispatchers.IO) {
        col.document(id).update("isActive", isActive).get(); Unit
    }

    override suspend fun delete(id: String) = withContext(Dispatchers.IO) {
        col.document(id).delete().get(); Unit
    }

    private fun com.google.cloud.firestore.DocumentSnapshot.toService() = runCatching {
        Service(
            id = id,
            hotelId = getString("hotelId") ?: "",
            name = getString("name") ?: "",
            price = getDouble("price") ?: 0.0,
            isActive = getBoolean("isActive") ?: true,
            createdAt = getTimestamp("createdAt")?.toDate() ?: Date(),
        )
    }.getOrNull()

    private fun Service.toMap() = mapOf(
        "hotelId" to hotelId,
        "name" to name,
        "price" to price,
        "isActive" to isActive,
        "createdAt" to createdAt,
    )
}
