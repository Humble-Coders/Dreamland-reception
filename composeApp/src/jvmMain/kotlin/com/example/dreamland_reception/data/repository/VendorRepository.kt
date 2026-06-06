package com.example.dreamland_reception.data.repository

import com.example.dreamland_reception.data.model.Vendor
import com.google.cloud.firestore.DocumentSnapshot
import com.google.cloud.firestore.Firestore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.Date

interface VendorRepository {
    /** All vendors for the hotel, name-sorted — feeds the "Mark Done" vendor dropdown. */
    suspend fun listByHotel(hotelId: String): List<Vendor>
    suspend fun getById(id: String): Vendor?
    /** Creates a vendor and returns its new doc id (used as the Humble Ledger externalId). */
    suspend fun add(vendor: Vendor): String
}

object FirestoreVendorRepository : VendorRepository {

    fun initialize(fs: Firestore) {
        FirestoreRepositorySupport.prime(fs)
    }

    private val col get() = FirestoreRepositorySupport.get().collection("vendors")

    override suspend fun listByHotel(hotelId: String): List<Vendor> = withContext(Dispatchers.IO) {
        col.whereEqualTo("hotelId", hotelId)
            .get().get().documents.mapNotNull { it.toVendor() }
            .sortedBy { it.name.lowercase() }
    }

    override suspend fun getById(id: String): Vendor? = withContext(Dispatchers.IO) {
        if (id.isBlank()) return@withContext null
        runCatching { col.document(id).get().get().takeIf { it.exists() }?.toVendor() }.getOrNull()
    }

    override suspend fun add(vendor: Vendor): String = withContext(Dispatchers.IO) {
        val ref = col.document() // Firestore-generated id
        ref.set(vendor.copy(id = ref.id).toMap()).get()
        ref.id
    }

    private fun DocumentSnapshot.toVendor() = runCatching {
        Vendor(
            id = id,
            hotelId = getString("hotelId") ?: "",
            name = getString("name") ?: "",
            phone = getString("phone") ?: "",
            email = getString("email") ?: "",
            gstin = getString("gstin") ?: "",
            discountPercent = getDouble("discountPercent") ?: 0.0,
            createdAt = getTimestamp("createdAt")?.toDate() ?: Date(),
        )
    }.getOrNull()

    private fun Vendor.toMap() = mapOf(
        "hotelId" to hotelId,
        "name" to name,
        "phone" to phone,
        "email" to email,
        "gstin" to gstin,
        "discountPercent" to discountPercent,
        "createdAt" to createdAt,
    )
}
