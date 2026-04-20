package com.example.dreamland_reception.data.repository

import com.example.dreamland_reception.data.model.StaffMember
import com.google.cloud.firestore.Firestore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.withContext
import java.util.Date

interface StaffRepository {
    suspend fun getAll(): List<StaffMember>
    suspend fun getActive(): List<StaffMember>
    suspend fun add(member: StaffMember): String
    suspend fun update(member: StaffMember)
    suspend fun setActive(id: String, active: Boolean)
    suspend fun setAvailability(id: String, available: Boolean)
    fun listenByHotel(hotelId: String): Flow<List<StaffMember>>
}

object FirestoreStaffRepository : StaffRepository {

    fun initialize(fs: Firestore) {
        FirestoreRepositorySupport.prime(fs)
    }

    private val col get() = FirestoreRepositorySupport.get().collection("staff")

    override suspend fun getAll(): List<StaffMember> = withContext(Dispatchers.IO) {
        col.orderBy("name").get().get().documents.mapNotNull { it.toStaff() }
    }

    override suspend fun getActive(): List<StaffMember> = withContext(Dispatchers.IO) {
        col.whereEqualTo("isActive", true).get().get().documents.mapNotNull { it.toStaff() }
    }

    override suspend fun add(member: StaffMember): String = withContext(Dispatchers.IO) {
        col.add(member.toMap()).get().id
    }

    override suspend fun update(member: StaffMember) = withContext(Dispatchers.IO) {
        col.document(member.id).set(member.toMap()).get(); Unit
    }

    override suspend fun setActive(id: String, active: Boolean) = withContext(Dispatchers.IO) {
        col.document(id).update("isActive", active).get(); Unit
    }

    override suspend fun setAvailability(id: String, available: Boolean) = withContext(Dispatchers.IO) {
        col.document(id).update("isAvailable", available).get(); Unit
    }

    override fun listenByHotel(hotelId: String): Flow<List<StaffMember>> = callbackFlow {
        val registration = col.whereEqualTo("hotelId", hotelId)
            .addSnapshotListener { snapshot, error ->
                if (error != null) { close(error); return@addSnapshotListener }
                val staff = snapshot?.documents?.mapNotNull { it.toStaff() }
                    ?.sortedWith(compareBy({ !it.isActive }, { !it.isAvailable }, { it.name }))
                    ?: emptyList()
                trySend(staff)
            }
        awaitClose { registration.remove() }
    }

    private fun com.google.cloud.firestore.DocumentSnapshot.toStaff() = runCatching {
        StaffMember(
            id = id,
            hotelId = getString("hotelId") ?: "",
            name = getString("name") ?: "",
            email = getString("email") ?: "",
            phone = getString("phone") ?: "",
            role = getString("role") ?: "",
            department = getString("department") ?: "",
            shift = getString("shift") ?: "",
            joiningDate = getTimestamp("joiningDate")?.toDate() ?: Date(),
            isActive = getBoolean("isActive") ?: true,
            isAvailable = getBoolean("isAvailable") ?: true,
            salary = getDouble("salary") ?: 0.0,
        )
    }.getOrNull()

    private fun StaffMember.toMap() = mapOf(
        "hotelId" to hotelId,
        "name" to name,
        "email" to email,
        "phone" to phone,
        "role" to role,
        "department" to department,
        "shift" to shift,
        "joiningDate" to joiningDate,
        "isActive" to isActive,
        "isAvailable" to isAvailable,
        "salary" to salary,
    )
}
