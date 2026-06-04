package com.example.dreamland_reception.data.repository

import com.google.cloud.firestore.Firestore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Publishes invoice links so they can be shared via QR. Each share is a new
 * auto-id document in the `sharedQrCodes` collection with a single `invoiceUrl`
 * field, written when the user opens the QR for a bill.
 */
interface SharedQrCodeRepository {
    suspend fun share(invoiceUrl: String): String
}

object FirestoreSharedQrCodeRepository : SharedQrCodeRepository {

    fun initialize(fs: Firestore) {
        FirestoreRepositorySupport.prime(fs)
    }

    private val col get() = FirestoreRepositorySupport.get().collection("sharedQrCodes")

    override suspend fun share(invoiceUrl: String): String = withContext(Dispatchers.IO) {
        col.add(mapOf("invoiceUrl" to invoiceUrl)).get().id
    }
}
