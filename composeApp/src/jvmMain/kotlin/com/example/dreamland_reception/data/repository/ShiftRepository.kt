package com.example.dreamland_reception.data.repository

import com.example.dreamland_reception.data.model.ReceptionManager
import com.example.dreamland_reception.data.model.ShiftHandover
import com.google.cloud.firestore.DocumentSnapshot
import com.google.cloud.firestore.Firestore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.security.MessageDigest
import java.util.Date

/** Salted SHA-256 of a manager's password. Verified client-side on handover. */
fun hashManagerPassword(name: String, password: String): String {
    val salted = "dreamland:${name.trim().lowercase()}:$password"
    return MessageDigest.getInstance("SHA-256")
        .digest(salted.toByteArray(Charsets.UTF_8))
        .joinToString("") { "%02x".format(it) }
}

interface ShiftRepository {
    suspend fun listManagers(hotelId: String): List<ReceptionManager>
    /** Creates a manager (storing only the password hash). Returns it with its id. */
    suspend fun addManager(hotelId: String, name: String, password: String): ReceptionManager
    suspend fun logHandover(handover: ShiftHandover): String
}

object FirestoreShiftRepository : ShiftRepository {

    fun initialize(fs: Firestore) {
        FirestoreRepositorySupport.prime(fs)
    }

    private val managersCol get() = FirestoreRepositorySupport.get().collection("receptionManagers")
    private val handoversCol get() = FirestoreRepositorySupport.get().collection("shiftHandovers")

    override suspend fun listManagers(hotelId: String): List<ReceptionManager> = withContext(Dispatchers.IO) {
        managersCol.whereEqualTo("hotelId", hotelId)
            .get().get().documents.mapNotNull { it.toManager() }
            .sortedBy { it.name.lowercase() }
    }

    override suspend fun addManager(hotelId: String, name: String, password: String): ReceptionManager =
        withContext(Dispatchers.IO) {
            val ref = managersCol.document()
            val manager = ReceptionManager(
                id = ref.id, hotelId = hotelId, name = name.trim(),
                passwordHash = hashManagerPassword(name, password),
            )
            ref.set(manager.toMap()).get()
            manager
        }

    override suspend fun logHandover(handover: ShiftHandover): String = withContext(Dispatchers.IO) {
        handoversCol.add(handover.toMap()).get().id
    }

    private fun DocumentSnapshot.toManager() = runCatching {
        ReceptionManager(
            id = id,
            hotelId = getString("hotelId") ?: "",
            name = getString("name") ?: "",
            passwordHash = getString("passwordHash") ?: "",
            createdAt = getTimestamp("createdAt")?.toDate() ?: Date(),
        )
    }.getOrNull()

    private fun ReceptionManager.toMap() = mapOf(
        "hotelId" to hotelId,
        "name" to name,
        "passwordHash" to passwordHash,
        "createdAt" to createdAt,
    )

    private fun ShiftHandover.toMap() = mapOf(
        "hotelId" to hotelId,
        "fromManager" to fromManager,
        "toManager" to toManager,
        "cashAtHandover" to cashAtHandover,
        "bankAtHandover" to bankAtHandover,
        "at" to at,
    )
}
