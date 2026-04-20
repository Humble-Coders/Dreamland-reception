package com.example.dreamland_reception.data.repository

import com.example.dreamland_reception.data.model.Complaint
import com.google.cloud.firestore.Firestore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.withContext
import java.util.Date

interface ComplaintRepository {
    suspend fun getAll(): List<Complaint>
    suspend fun getOpen(): List<Complaint>
    suspend fun getByStay(stayId: String): List<Complaint>
    suspend fun getByHotel(hotelId: String): List<Complaint>
    suspend fun add(complaint: Complaint): String
    suspend fun resolve(id: String)
    suspend fun updateAssignment(id: String, staffId: String, staffName: String)
    suspend fun updateStatus(id: String, status: String)
    fun listenByHotel(hotelId: String): Flow<List<Complaint>>
}

object FirestoreComplaintRepository : ComplaintRepository {

    fun initialize(fs: Firestore) {
        FirestoreRepositorySupport.prime(fs)
    }

    private val col get() = FirestoreRepositorySupport.get().collection("complaints")

    override suspend fun getAll(): List<Complaint> = withContext(Dispatchers.IO) {
        col.get().get().documents.mapNotNull { it.toComplaint() }
            .sortedByDescending { it.reportedAt }
    }

    override suspend fun getOpen(): List<Complaint> = withContext(Dispatchers.IO) {
        col.whereIn("status", listOf("NEW", "ASSIGNED"))
            .get().get().documents.mapNotNull { it.toComplaint() }
            .sortedByDescending { it.reportedAt }
    }

    override suspend fun getByStay(stayId: String): List<Complaint> = withContext(Dispatchers.IO) {
        col.whereEqualTo("stayId", stayId)
            .get().get().documents.mapNotNull { it.toComplaint() }
            .sortedBy { it.reportedAt }
    }

    override suspend fun getByHotel(hotelId: String): List<Complaint> = withContext(Dispatchers.IO) {
        col.whereEqualTo("hotelId", hotelId).get().get()
            .documents.mapNotNull { it.toComplaint() }
            .sortedByDescending { it.reportedAt }
    }

    override suspend fun add(complaint: Complaint): String = withContext(Dispatchers.IO) {
        col.add(complaint.toMap()).get().id
    }

    override suspend fun resolve(id: String) = withContext(Dispatchers.IO) {
        col.document(id).update(
            mapOf("status" to "COMPLETED", "resolvedAt" to Date()),
        ).get(); Unit
    }

    override suspend fun updateAssignment(id: String, staffId: String, staffName: String) =
        withContext(Dispatchers.IO) {
            col.document(id).update(mapOf(
                "assignedTo" to staffId,
                "assignedToName" to staffName,
                "status" to "ASSIGNED",
            )).get(); Unit
        }

    override suspend fun updateStatus(id: String, status: String) = withContext(Dispatchers.IO) {
        val fields = mutableMapOf<String, Any?>("status" to status)
        if (status == "COMPLETED") fields["resolvedAt"] = Date()
        col.document(id).update(fields).get(); Unit
    }

    override fun listenByHotel(hotelId: String): Flow<List<Complaint>> = callbackFlow {
        val registration = col.whereEqualTo("hotelId", hotelId)
            .addSnapshotListener { snapshot, error ->
                if (error != null) { close(error); return@addSnapshotListener }
                val complaints = snapshot?.documents?.mapNotNull { it.toComplaint() }
                    ?.sortedByDescending { it.reportedAt } ?: emptyList()
                trySend(complaints)
            }
        awaitClose { registration.remove() }
    }

    private fun com.google.cloud.firestore.DocumentSnapshot.toComplaint() = runCatching {
        Complaint(
            id = id,
            hotelId = getString("hotelId") ?: "",
            stayId = getString("stayId") ?: "",
            guestName = getString("guestName") ?: "",
            roomNumber = getString("roomNumber") ?: "",
            type = getString("type") ?: "",
            description = getString("description") ?: "",
            priority = getString("priority") ?: "MEDIUM",
            status = getString("status") ?: "NEW",
            assignedTo = getString("assignedTo") ?: "",
            assignedToName = getString("assignedToName") ?: "",
            reportedAt = getTimestamp("reportedAt")?.toDate() ?: Date(),
            resolvedAt = getTimestamp("resolvedAt")?.toDate(),
        )
    }.getOrNull()

    private fun Complaint.toMap() = mapOf(
        "hotelId" to hotelId,
        "stayId" to stayId,
        "guestName" to guestName,
        "roomNumber" to roomNumber,
        "type" to type,
        "description" to description,
        "priority" to priority,
        "status" to status,
        "assignedTo" to assignedTo,
        "assignedToName" to assignedToName,
        "reportedAt" to reportedAt,
        "resolvedAt" to resolvedAt,
    )
}
