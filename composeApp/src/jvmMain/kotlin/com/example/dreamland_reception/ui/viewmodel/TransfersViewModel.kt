package com.example.dreamland_reception.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.dreamland_reception.data.AppContext
import com.example.dreamland_reception.data.accounting.AccountingRepository
import com.example.dreamland_reception.data.model.Transfer
import com.example.dreamland_reception.data.model.Vendor
import com.example.dreamland_reception.data.repository.FirestoreTransferRepository
import com.example.dreamland_reception.data.repository.FirestoreUserRepository
import com.example.dreamland_reception.data.repository.FirestoreVendorRepository
import com.example.dreamland_reception.data.repository.TransferRepository
import com.example.dreamland_reception.data.repository.UserRepository
import com.example.dreamland_reception.data.repository.VendorRepository
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** One selectable party in the from/to picker. */
data class TransferParty(
    val kind: String,         // CASH | BANK | CUSTOMER | VENDOR
    val refId: String = "",   // guest/vendor id; blank for Cash/Bank
    val name: String,
    val phone: String = "",
) {
    val label: String get() = when (kind) {
        "CASH" -> "Cash"
        "BANK" -> "Bank"
        else   -> if (phone.isNotBlank()) "$name · $phone" else name
    }

    val groupLabel: String get() = when (kind) {
        "CASH", "BANK" -> "Accounts"
        "CUSTOMER"     -> "Customers"
        else           -> "Vendors"
    }

    /** Filterable by name AND phone (and by the words "cash"/"bank"). */
    fun matches(query: String): Boolean {
        val q = query.trim().lowercase()
        if (q.isEmpty()) return true
        if (name.lowercase().contains(q)) return true
        if (phone.lowercase().contains(q)) return true
        if (kind == "CASH" && "cash".contains(q)) return true
        if (kind == "BANK" && "bank".contains(q)) return true
        return false
    }

    companion object {
        val CASH = TransferParty(kind = "CASH", name = "Cash")
        val BANK = TransferParty(kind = "BANK", name = "Bank")
    }
}

data class TransfersScreenState(
    val transfers: List<Transfer> = emptyList(),
    val parties: List<TransferParty> = listOf(TransferParty.CASH, TransferParty.BANK),
    val isLoading: Boolean = true,
    val isSubmitting: Boolean = false,
    val error: String? = null,
    val searchQuery: String = "",
) {
    val filtered: List<Transfer> get() {
        val q = searchQuery.trim().lowercase()
        return if (q.isEmpty()) transfers else transfers.filter {
            it.fromName.lowercase().contains(q) || it.toName.lowercase().contains(q) || it.notes.lowercase().contains(q)
        }
    }
}

class TransfersViewModel(
    private val transferRepo: TransferRepository = FirestoreTransferRepository,
    private val userRepo: UserRepository = FirestoreUserRepository,
    private val vendorRepo: VendorRepository = FirestoreVendorRepository,
    private val accountingRepo: AccountingRepository = AccountingRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(TransfersScreenState())
    val state: StateFlow<TransfersScreenState> = _state.asStateFlow()

    private var listenerJob: Job? = null

    init {
        startListener()
        loadParties()
        viewModelScope.launch { retryUnsynced() }
    }

    private fun startListener() {
        val hotelId = AppContext.hotelId
        if (hotelId.isBlank()) { _state.update { it.copy(isLoading = false) }; return }
        listenerJob?.cancel()
        listenerJob = viewModelScope.launch {
            runCatching {
                transferRepo.listenByHotel(hotelId).collect { list ->
                    _state.update { it.copy(transfers = list, isLoading = false, error = null) }
                }
            }.onFailure { e -> _state.update { it.copy(isLoading = false, error = e.message ?: "Failed to load transfers") } }
        }
    }

    override fun onCleared() { super.onCleared(); listenerJob?.cancel() }

    fun onSearch(q: String) = _state.update { it.copy(searchQuery = q) }

    /** Builds the from/to picker list: Cash, Bank, all guests, all vendors. */
    fun loadParties() {
        val hotelId = AppContext.hotelId
        if (hotelId.isBlank()) return
        viewModelScope.launch {
            val guests = runCatching { userRepo.getAllByHotel(hotelId) }.getOrElse { emptyList() }
            val vendors = runCatching { vendorRepo.listByHotel(hotelId) }.getOrElse { emptyList() }
            val customerParties = guests
                .filter { it.name.isNotBlank() || it.phone.isNotBlank() }
                .map { TransferParty("CUSTOMER", it.id, it.name.ifBlank { "Guest" }, it.phone) }
                .sortedBy { it.name.lowercase() }
            val vendorParties = vendors
                .map { TransferParty("VENDOR", it.id, it.name, it.phone) }
                .sortedBy { it.name.lowercase() }
            val parties = listOf(TransferParty.CASH, TransferParty.BANK) + customerParties + vendorParties
            _state.update { it.copy(parties = parties) }
        }
    }

    /** Inline-create a guest, returning the new party via callback. */
    fun addCustomer(name: String, phone: String, onCreated: (TransferParty) -> Unit) {
        val trimmedName = name.trim(); val trimmedPhone = phone.trim()
        if (trimmedName.isBlank()) return
        viewModelScope.launch {
            runCatching { userRepo.createGuestUser(trimmedName, trimmedPhone) }
                .onSuccess { id ->
                    val party = TransferParty("CUSTOMER", id, trimmedName, trimmedPhone)
                    _state.update { it.copy(parties = (it.parties + party)) }
                    onCreated(party)
                }
                .onFailure { e -> _state.update { it.copy(error = e.message ?: "Failed to add customer") } }
        }
    }

    /** Inline-create a vendor, returning the new party via callback. */
    fun addVendor(name: String, phone: String, onCreated: (TransferParty) -> Unit) {
        val hotelId = AppContext.hotelId
        val trimmedName = name.trim(); val trimmedPhone = phone.trim()
        if (hotelId.isBlank() || trimmedName.isBlank()) return
        viewModelScope.launch {
            runCatching {
                val v = Vendor(hotelId = hotelId, name = trimmedName, phone = trimmedPhone)
                v.copy(id = vendorRepo.add(v))
            }.onSuccess { created ->
                val party = TransferParty("VENDOR", created.id, created.name, created.phone)
                _state.update { it.copy(parties = (it.parties + party)) }
                onCreated(party)
            }.onFailure { e -> _state.update { it.copy(error = e.message ?: "Failed to add vendor") } }
        }
    }

    /** Records the transfer and posts it to Humble Ledger durably. */
    fun createTransfer(
        from: TransferParty, to: TransferParty, amount: Double, notes: String,
        onDone: (Boolean) -> Unit = {},
    ) {
        val hotelId = AppContext.hotelId
        if (hotelId.isBlank()) { _state.update { it.copy(error = "No hotel selected") }; onDone(false); return }
        if (amount <= 0.0) { _state.update { it.copy(error = "Enter an amount greater than zero") }; onDone(false); return }
        if (from.kind == to.kind && from.refId == to.refId) {
            _state.update { it.copy(error = "From and To cannot be the same") }; onDone(false); return
        }
        _state.update { it.copy(isSubmitting = true, error = null) }
        viewModelScope.launch {
            val transfer = Transfer(
                hotelId = hotelId,
                fromKind = from.kind, fromRefId = from.refId, fromName = from.label,
                toKind = to.kind, toRefId = to.refId, toName = to.label,
                amount = amount, notes = notes.trim(),
            )
            runCatching { transferRepo.add(transfer) }
                .onSuccess { id ->
                    syncTransfer(transfer.copy(id = id))
                    _state.update { it.copy(isSubmitting = false) }
                    onDone(true)
                }
                .onFailure { e ->
                    _state.update { it.copy(isSubmitting = false, error = e.message ?: "Failed to record transfer") }
                    onDone(false)
                }
        }
    }

    private suspend fun syncTransfer(transfer: Transfer) {
        accountingRepo.settleTransfer(transfer)
            .onSuccess { posted -> if (posted) runCatching { transferRepo.markSynced(transfer.id) } }
            .onFailure { e -> runCatching { transferRepo.markSyncFailed(transfer.id, e.message ?: "sync failed") } }
    }

    private suspend fun retryUnsynced() {
        val hotelId = AppContext.hotelId
        if (hotelId.isBlank()) return
        val pending = runCatching { transferRepo.getUnsynced(hotelId) }.getOrElse { emptyList() }
        for (t in pending) syncTransfer(t)
    }
}
