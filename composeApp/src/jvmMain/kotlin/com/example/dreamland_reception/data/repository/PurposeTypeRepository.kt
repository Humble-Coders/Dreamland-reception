package com.example.dreamland_reception.data.repository

import com.example.dreamland_reception.data.model.PurposeType
import com.google.cloud.firestore.Firestore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.Date

interface PurposeTypeRepository {
    suspend fun getByHotel(hotelId: String): List<PurposeType>
    suspend fun add(hotelId: String, name: String): String
}

object FirestorePurposeTypeRepository : PurposeTypeRepository {

    fun initialize(fs: Firestore) { FirestoreRepositorySupport.prime(fs) }

    private val col get() = FirestoreRepositorySupport.get().collection("purposeType")

    override suspend fun getByHotel(hotelId: String): List<PurposeType> = withContext(Dispatchers.IO) {
        col.whereEqualTo("hotelId", hotelId).get().get().documents.mapNotNull { it.toPurposeType() }
            .sortedBy { it.name.lowercase() }
    }

    override suspend fun add(hotelId: String, name: String): String = withContext(Dispatchers.IO) {
        col.add(mapOf("hotelId" to hotelId, "name" to name.trim(), "createdAt" to Date())).get().id
    }

    private fun com.google.cloud.firestore.DocumentSnapshot.toPurposeType() = runCatching {
        PurposeType(
            id = id,
            hotelId = getString("hotelId") ?: "",
            name = getString("name") ?: "",
            createdAt = getTimestamp("createdAt")?.toDate() ?: Date(),
        )
    }.getOrNull()
}
