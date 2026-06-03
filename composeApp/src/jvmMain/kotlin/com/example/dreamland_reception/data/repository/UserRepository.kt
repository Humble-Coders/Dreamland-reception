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
    suspend fun markCheckedIn(userId: String, checkedIn: Boolean)
    /** Returns the doc id of the user whose `phoneNumber` exactly matches, or null. */
    suspend fun findIdByPhone(phoneNumber: String): String?
    /** Returns the display name of the user whose `phoneNumber` exactly matches, or null. */
    suspend fun findNameByPhone(phoneNumber: String): String?
    /** Creates a reception-owned guest user (auto id stored in `uid`, empty `fireAuthId`). Returns the id. */
    suspend fun createGuestUser(displayName: String, phoneNumber: String): String
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

    override suspend fun markCheckedIn(userId: String, checkedIn: Boolean) = withContext(Dispatchers.IO) {
        if (userId.isBlank()) return@withContext
        col.document(userId).update(mapOf("isCheckedIn" to checkedIn, "updatedAt" to Date())).get()
        Unit
    }

    override suspend fun findIdByPhone(phoneNumber: String): String? = withContext(Dispatchers.IO) {
        if (phoneNumber.isBlank()) return@withContext null
        col.whereEqualTo("phoneNumber", phoneNumber).limit(1).get().get()
            .documents.firstOrNull()?.id
    }

    override suspend fun findNameByPhone(phoneNumber: String): String? = withContext(Dispatchers.IO) {
        if (phoneNumber.isBlank()) return@withContext null
        val doc = col.whereEqualTo("phoneNumber", phoneNumber).limit(1).get().get()
            .documents.firstOrNull() ?: return@withContext null
        doc.getString("displayName")?.ifBlank { null }
            ?: doc.getString("name")?.ifBlank { null }
    }

    override suspend fun createGuestUser(displayName: String, phoneNumber: String): String = withContext(Dispatchers.IO) {
        val ref = col.document()   // Firestore-generated auto id
        val id = ref.id
        val now = Date()
        ref.set(
            mapOf(
                "uid" to id,
                "fireAuthId" to "",            // filled by the mobile app on OTP linking
                "displayName" to displayName,
                "phoneNumber" to phoneNumber,
                "isCheckedIn" to true,
                "providers" to emptyList<String>(),
                "createdAt" to now,
                "updatedAt" to now,
            ),
        ).get()
        id
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
