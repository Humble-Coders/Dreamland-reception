package com.example.dreamland_reception.data.repository

import com.example.dreamland_reception.data.model.Hotel
import com.google.cloud.firestore.Firestore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

interface HotelRepository {
    suspend fun getAll(): List<Hotel>
    suspend fun getById(id: String): Hotel?
    suspend fun update(hotel: Hotel)
}

object FirestoreHotelRepository : HotelRepository {

    fun initialize(fs: Firestore) { FirestoreRepositorySupport.prime(fs) }

    private val col get() = FirestoreRepositorySupport.get().collection("hotels")

    override suspend fun getAll(): List<Hotel> = withContext(Dispatchers.IO) {
        col.whereEqualTo("status", "ACTIVE").get().get().documents.mapNotNull { it.toHotel() }
    }

    override suspend fun getById(id: String): Hotel? = withContext(Dispatchers.IO) {
        col.document(id).get().get().takeIf { it.exists() }?.toHotel()
    }

    override suspend fun update(hotel: Hotel) = withContext(Dispatchers.IO) {
        col.document(hotel.id).set(hotel.toMap()).get(); Unit
    }

    private fun com.google.cloud.firestore.DocumentSnapshot.toHotel() = runCatching {
        Hotel(
            id = id,
            // General
            name = getString("name") ?: "",
            address = getString("address") ?: "",
            city = getString("city") ?: "",
            country = getString("country") ?: "",
            contactPhone = getString("contactPhone") ?: "",
            currency = getString("currency") ?: "INR",
            contactInfo = getString("contactInfo") ?: "",
            isActive = getString("status") == "active",
            // Billing
            taxEnabled = getBoolean("taxEnabled") ?: true,
            taxPercentage = getDouble("taxPercentage") ?: 18.0,
            defaultDiscountType = getString("defaultDiscountType") ?: "PERCENTAGE",
            defaultDiscountValue = getDouble("defaultDiscountValue") ?: 0.0,
            // Room rules
            checkInTime = getString("checkInTime") ?: "12:00",
            checkOutTime = getString("checkOutTime") ?: "11:00",
            autoAssignRoom = getBoolean("autoAssignRoom") ?: true,
            // Room options
            breakfastEnabled = getBoolean("breakfastEnabled") ?: true,
            breakfastPricePerPerson = getDouble("breakfastPricePerPerson") ?: 200.0,
            earlyCheckInAllowed = getBoolean("earlyCheckInAllowed") ?: false,
            earlyCheckInPrice = getDouble("earlyCheckInPrice") ?: 300.0,
            lateCheckOutAllowed = getBoolean("lateCheckOutAllowed") ?: false,
            lateCheckOutPrice = getDouble("lateCheckOutPrice") ?: 200.0,
        )
    }.getOrNull()

    private fun Hotel.toMap() = mapOf(
        "name" to name,
        "address" to address,
        "city" to city,
        "country" to country,
        "contactPhone" to contactPhone,
        "currency" to currency,
        "contactInfo" to contactInfo,
        "status" to if (isActive) "active" else "inactive",
        "taxEnabled" to taxEnabled,
        "taxPercentage" to taxPercentage,
        "defaultDiscountType" to defaultDiscountType,
        "defaultDiscountValue" to defaultDiscountValue,
        "checkInTime" to checkInTime,
        "checkOutTime" to checkOutTime,
        "autoAssignRoom" to autoAssignRoom,
        "breakfastEnabled" to breakfastEnabled,
        "breakfastPricePerPerson" to breakfastPricePerPerson,
        "earlyCheckInAllowed" to earlyCheckInAllowed,
        "earlyCheckInPrice" to earlyCheckInPrice,
        "lateCheckOutAllowed" to lateCheckOutAllowed,
        "lateCheckOutPrice" to lateCheckOutPrice,
    )
}
