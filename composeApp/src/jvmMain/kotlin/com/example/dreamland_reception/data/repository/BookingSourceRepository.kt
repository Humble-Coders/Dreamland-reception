package com.example.dreamland_reception.data.repository

import com.example.dreamland_reception.data.model.BookingSource
import com.google.cloud.firestore.Firestore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.withContext
import java.util.Date

interface BookingSourceRepository {
    suspend fun getAll(): List<BookingSource>
    suspend fun add(name: String): String
    fun listen(): Flow<List<BookingSource>>
}

object FirestoreBookingSourceRepository : BookingSourceRepository {

    fun initialize(fs: Firestore) {
        FirestoreRepositorySupport.prime(fs)
    }

    private val col get() = FirestoreRepositorySupport.get().collection("bookingSources")

    override suspend fun getAll(): List<BookingSource> = withContext(Dispatchers.IO) {
        col.orderBy("name").get().get().documents.mapNotNull { it.toBookingSource() }
    }

    override suspend fun add(name: String): String = withContext(Dispatchers.IO) {
        col.add(mapOf("name" to name.trim(), "createdAt" to Date())).get().id
    }

    override fun listen(): Flow<List<BookingSource>> = callbackFlow {
        val registration = col.orderBy("name").addSnapshotListener { snapshot, error ->
            if (error != null) { close(error); return@addSnapshotListener }
            trySend(snapshot?.documents?.mapNotNull { it.toBookingSource() } ?: emptyList())
        }
        awaitClose { registration.remove() }
    }

    private fun com.google.cloud.firestore.DocumentSnapshot.toBookingSource() = runCatching {
        BookingSource(
            id = id,
            name = getString("name") ?: "",
            createdAt = getTimestamp("createdAt")?.toDate() ?: Date(),
        )
    }.getOrNull()
}
