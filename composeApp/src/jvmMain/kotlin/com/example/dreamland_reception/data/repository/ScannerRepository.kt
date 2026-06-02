package com.example.dreamland_reception.data.repository

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class ScannerDocument(
    val id: String = "",
    val name: String = "",
    val age: Int = 0,
    val dob: String = "",
    val gender: String = "",
    val govIdNumber: String = "",
    val govIdPictures: List<String> = emptyList(),
)

interface ScannerRepository {
    suspend fun fetchFirstAndDelete(): ScannerDocument?
}

object FirestoreScannerRepository : ScannerRepository {

    private val col get() = FirestoreRepositorySupport.get().collection("scanner")

    override suspend fun fetchFirstAndDelete(): ScannerDocument? = withContext(Dispatchers.IO) {
        val docs = col.limit(1).get().get().documents
        val doc = docs.firstOrNull() ?: return@withContext null
        val result = ScannerDocument(
            id = doc.id,
            name = doc.getString("name") ?: "",
            age = (doc.get("age") as? Number)?.toInt() ?: 0,
            dob = doc.getString("dob") ?: "",
            gender = doc.getString("gender") ?: "",
            govIdNumber = doc.getString("govIdNumber") ?: "",
            govIdPictures = (doc.get("govIdPictures") as? List<*>)?.filterIsInstance<String>() ?: emptyList(),
        )
        doc.reference.delete().get()
        result
    }
}
