package com.example.dreamland_reception.data.repository

import com.example.dreamland_reception.data.firebase.FirebaseManager
import com.google.cloud.firestore.Firestore

/**
 * Single cached [Firestore] for all `Firestore*Repository` objects.
 * [prime] is called from [DreamlandAppInitializer]; if that never runs or fails,
 * [get] falls back to [FirebaseManager.requireFirestore] so `lateinit` is never required.
 */
internal object FirestoreRepositorySupport {

    @Volatile
    private var cached: Firestore? = null

    private val lock = Any()

    fun prime(fs: Firestore) {
        synchronized(lock) {
            cached = fs
        }
    }

    fun get(): Firestore = synchronized(lock) {
        cached ?: FirebaseManager.requireFirestore().also { cached = it }
    }
}
