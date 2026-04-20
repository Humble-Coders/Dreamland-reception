package com.example.dreamland_reception.data.repository

import com.example.dreamland_reception.data.model.HotelSettings
import com.google.cloud.firestore.Firestore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

interface HotelSettingsRepository {
    suspend fun getByHotel(hotelId: String): HotelSettings?
    suspend fun upsert(settings: HotelSettings)
}

object FirestoreHotelSettingsRepository : HotelSettingsRepository {

    fun initialize(fs: Firestore) { FirestoreRepositorySupport.prime(fs) }

    /** Document ID = hotelId — matches schema `settings/{hotelId}`. */
    private val col get() = FirestoreRepositorySupport.get().collection("settings")

    override suspend fun getByHotel(hotelId: String): HotelSettings? = withContext(Dispatchers.IO) {
        col.document(hotelId).get().get().takeIf { it.exists() }?.toSettings(hotelId)
    }

    override suspend fun upsert(settings: HotelSettings) = withContext(Dispatchers.IO) {
        col.document(settings.hotelId).set(settings.toMap()).get(); Unit
    }

    private fun com.google.cloud.firestore.DocumentSnapshot.toSettings(hotelId: String) = runCatching {
        HotelSettings(
            hotelId = hotelId,
            hotelName = getString("hotelName") ?: "",
            currency = getString("currency") ?: "INR",
            contactInfo = getString("contactInfo") ?: "",
            taxPercentage = getDouble("taxPercentage") ?: 18.0,
            taxEnabled = getBoolean("taxEnabled") ?: true,
            defaultDiscountType = getString("defaultDiscountType") ?: "PERCENTAGE",
            defaultDiscountValue = getDouble("defaultDiscountValue") ?: 0.0,
            checkInTime = getString("checkInTime") ?: "12:00",
            checkOutTime = getString("checkOutTime") ?: "11:00",
            autoAssignRoom = getBoolean("autoAssignRoom") ?: true,
            breakfastEnabled = getBoolean("breakfastEnabled") ?: true,
            breakfastPricePerPerson = getDouble("breakfastPricePerPerson") ?: 200.0,
            extraBedEnabled = getBoolean("extraBedEnabled") ?: false,
            extraBedPrice = getDouble("extraBedPrice") ?: 500.0,
            earlyCheckInEnabled = getBoolean("earlyCheckInEnabled") ?: false,
            earlyCheckInCharge = getDouble("earlyCheckInCharge") ?: 300.0,
            lateCheckOutEnabled = getBoolean("lateCheckOutEnabled") ?: false,
            lateCheckOutCharge = getDouble("lateCheckOutCharge") ?: 400.0,
        )
    }.getOrNull()

    private fun HotelSettings.toMap() = mapOf(
        "hotelId" to hotelId,
        "hotelName" to hotelName,
        "currency" to currency,
        "contactInfo" to contactInfo,
        "taxPercentage" to taxPercentage,
        "taxEnabled" to taxEnabled,
        "defaultDiscountType" to defaultDiscountType,
        "defaultDiscountValue" to defaultDiscountValue,
        "checkInTime" to checkInTime,
        "checkOutTime" to checkOutTime,
        "autoAssignRoom" to autoAssignRoom,
        "breakfastEnabled" to breakfastEnabled,
        "breakfastPricePerPerson" to breakfastPricePerPerson,
        "extraBedEnabled" to extraBedEnabled,
        "extraBedPrice" to extraBedPrice,
        "earlyCheckInEnabled" to earlyCheckInEnabled,
        "earlyCheckInCharge" to earlyCheckInCharge,
        "lateCheckOutEnabled" to lateCheckOutEnabled,
        "lateCheckOutCharge" to lateCheckOutCharge,
    )
}
