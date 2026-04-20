package com.example.dreamland_reception.data.repository

import com.example.dreamland_reception.data.model.ComplaintType
import com.google.cloud.firestore.Firestore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.Date

interface ComplaintTypeRepository {
    suspend fun getByHotel(hotelId: String): List<ComplaintType>
    suspend fun add(type: ComplaintType): String
    suspend fun update(type: ComplaintType)
    suspend fun toggleActive(id: String, isActive: Boolean)
    suspend fun delete(id: String)
}

object FirestoreComplaintTypeRepository : ComplaintTypeRepository {

    fun initialize(fs: Firestore) { FirestoreRepositorySupport.prime(fs) }

    private val col get() = FirestoreRepositorySupport.get().collection("complaintTypes")

    override suspend fun getByHotel(hotelId: String): List<ComplaintType> = withContext(Dispatchers.IO) {
        col.whereEqualTo("hotelId", hotelId).get().get().documents.mapNotNull { it.toComplaintType() }
    }

    override suspend fun add(type: ComplaintType): String = withContext(Dispatchers.IO) {
        col.add(type.toMap()).get().id
    }

    override suspend fun update(type: ComplaintType) = withContext(Dispatchers.IO) {
        col.document(type.id).set(type.toMap()).get(); Unit
    }

    override suspend fun toggleActive(id: String, isActive: Boolean) = withContext(Dispatchers.IO) {
        col.document(id).update("isActive", isActive).get(); Unit
    }

    override suspend fun delete(id: String) = withContext(Dispatchers.IO) {
        col.document(id).delete().get(); Unit
    }

    private fun com.google.cloud.firestore.DocumentSnapshot.toComplaintType() = runCatching {
        ComplaintType(
            id = id,
            hotelId = getString("hotelId") ?: "",
            name = getString("name") ?: "",
            type = getString("type") ?: "MAINTENANCE",
            description = getString("description") ?: "",
            isActive = getBoolean("isActive") ?: true,
            createdAt = getTimestamp("createdAt")?.toDate() ?: Date(),
        )
    }.getOrNull()

    private fun ComplaintType.toMap() = mapOf(
        "hotelId" to hotelId,
        "name" to name,
        "type" to type,
        "description" to description,
        "isActive" to isActive,
        "createdAt" to createdAt,
    )
}
