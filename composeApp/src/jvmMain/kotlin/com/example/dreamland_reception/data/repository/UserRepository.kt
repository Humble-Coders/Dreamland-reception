package com.example.dreamland_reception.data.repository

import com.example.dreamland_reception.data.model.Guest
import com.google.cloud.firestore.DocumentSnapshot
import com.google.cloud.firestore.Firestore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.Date

interface UserRepository {
    suspend fun getAllByHotel(hotelId: String): List<Guest>
    suspend fun getByIds(ids: Set<String>): List<Guest>
}

object FirestoreUserRepository : UserRepository {

    fun initialize(fs: Firestore) {
        FirestoreRepositorySupport.prime(fs)
    }

    private val col get() = FirestoreRepositorySupport.get().collection("users")

    override suspend fun getAllByHotel(hotelId: String): List<Guest> = withContext(Dispatchers.IO) {
        col.whereEqualTo("hotelId", hotelId)
            .get().get().documents
            .mapNotNull { it.toGuest() }
            .sortedBy { it.name }
    }

    override suspend fun getByIds(ids: Set<String>): List<Guest> = withContext(Dispatchers.IO) {
        if (ids.isEmpty()) return@withContext emptyList()
        ids.mapNotNull { id ->
            runCatching { col.document(id).get().get().takeIf { it.exists() }?.toGuest() }.getOrNull()
        }.sortedBy { it.name }
    }

    private fun DocumentSnapshot.toGuest() = runCatching {
        Guest(
            id = id,
            hotelId = getString("hotelId") ?: "",
            name = getString("name")?.ifBlank { null }
                ?: getString("displayName")?.ifBlank { null }
                ?: getString("fullName") ?: "",
            email = getString("email")?.ifBlank { null }
                ?: getString("emailAddress") ?: "",
            phone = getString("phone")?.ifBlank { null }
                ?: getString("phoneNumber")?.ifBlank { null }
                ?: getString("mobile") ?: "",
            idType = getString("idType") ?: "",
            idNumber = getString("idNumber") ?: "",
            nationality = getString("nationality") ?: "",
            address = getString("address") ?: "",
            totalStays = getLong("totalStays")?.toInt() ?: 0,
            createdAt = getTimestamp("createdAt")?.toDate() ?: Date(),
        )
    }.getOrNull()
}
