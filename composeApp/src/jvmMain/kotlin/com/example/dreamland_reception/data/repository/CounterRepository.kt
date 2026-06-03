package com.example.dreamland_reception.data.repository

import com.google.cloud.firestore.Firestore
import com.google.cloud.firestore.SetOptions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Monotonic counters stored one document per hotel in `counters/{hotelId}`, with one numeric field
 * per sequence (e.g. `grcSeq_2026-27`). Increments run in a Firestore transaction so concurrent
 * check-ins can never hand out the same number.
 */
interface CounterRepository {
    /** Atomically increments `counters/{docId}.{field}` and returns the new value (starts at 1). */
    suspend fun next(docId: String, field: String): Long
}

object FirestoreCounterRepository : CounterRepository {

    fun initialize(fs: Firestore) { FirestoreRepositorySupport.prime(fs) }

    private val col get() = FirestoreRepositorySupport.get().collection("counters")

    override suspend fun next(docId: String, field: String): Long = withContext(Dispatchers.IO) {
        val fs = FirestoreRepositorySupport.get()
        val ref = col.document(docId)
        fs.runTransaction { txn ->
            val current = (txn.get(ref).get().get(field) as? Number)?.toLong() ?: 0L
            val nextVal = current + 1
            // Map key is a literal field name (not a path), so the FY hyphen is safe here.
            txn.set(ref, mapOf(field to nextVal), SetOptions.merge())
            nextVal
        }.get()
    }
}
