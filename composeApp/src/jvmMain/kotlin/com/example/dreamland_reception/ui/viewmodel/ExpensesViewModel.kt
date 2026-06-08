package com.example.dreamland_reception.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.dreamland_reception.data.AppContext
import com.example.dreamland_reception.data.accounting.AccountingRepository
import com.example.dreamland_reception.data.accounting.VendorBalanceInfo
import com.example.dreamland_reception.data.model.Expense
import com.example.dreamland_reception.data.model.Vendor
import com.example.dreamland_reception.data.repository.ExpenseRepository
import com.example.dreamland_reception.data.repository.FirestoreExpenseRepository
import com.example.dreamland_reception.data.repository.FirestoreVendorRepository
import com.example.dreamland_reception.data.repository.VendorRepository
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class ExpensesScreenState(
    val expenses: List<Expense> = emptyList(),
    val isLoading: Boolean = true,
    val error: String? = null,
    val searchQuery: String = "",
    val deletingId: String? = null,   // expense currently being deleted (for row spinner/disable)
) {
    val filtered: List<Expense> get() {
        val q = searchQuery.trim().lowercase()
        return if (q.isEmpty()) expenses else expenses.filter {
            it.title.lowercase().contains(q) || it.vendorName.lowercase().contains(q) || it.notes.lowercase().contains(q)
        }
    }
    val totalSpent: Double get() = expenses.sumOf { it.amount }
}

class ExpensesViewModel(
    private val expenseRepo: ExpenseRepository = FirestoreExpenseRepository,
    private val vendorRepo: VendorRepository = FirestoreVendorRepository,
    private val accountingRepo: AccountingRepository = AccountingRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(ExpensesScreenState())
    val state: StateFlow<ExpensesScreenState> = _state.asStateFlow()

    // Vendors for the Add Expense dropdown (shared `vendors` collection).
    private val _vendors = MutableStateFlow<List<Vendor>>(emptyList())
    val vendors: StateFlow<List<Vendor>> = _vendors.asStateFlow()

    private var listenerJob: Job? = null

    init {
        startListener()
        loadVendors()
        viewModelScope.launch { retryUnsynced() }
    }

    private fun startListener() {
        val hotelId = AppContext.hotelId
        if (hotelId.isBlank()) { _state.update { it.copy(isLoading = false) }; return }
        listenerJob?.cancel()
        listenerJob = viewModelScope.launch {
            runCatching {
                expenseRepo.listenByHotel(hotelId).collect { list ->
                    _state.update { it.copy(expenses = list, isLoading = false, error = null) }
                }
            }.onFailure { e -> _state.update { it.copy(isLoading = false, error = e.message ?: "Failed to load expenses") } }
        }
    }

    override fun onCleared() { super.onCleared(); listenerJob?.cancel() }

    fun onSearch(q: String) = _state.update { it.copy(searchQuery = q) }

    fun loadVendors() {
        val hotelId = AppContext.hotelId
        if (hotelId.isBlank()) return
        viewModelScope.launch {
            runCatching { vendorRepo.listByHotel(hotelId) }.onSuccess { _vendors.value = it }
        }
    }

    fun addVendor(name: String, phone: String = "", onCreated: (Vendor) -> Unit) {
        val hotelId = AppContext.hotelId
        val trimmed = name.trim()
        if (hotelId.isBlank() || trimmed.isBlank()) return
        viewModelScope.launch {
            runCatching {
                val v = Vendor(hotelId = hotelId, name = trimmed, phone = phone.trim())
                v.copy(id = vendorRepo.add(v))
            }.onSuccess { created ->
                _vendors.update { list -> (list + created).sortedBy { it.name.lowercase() } }
                onCreated(created)
            }.onFailure { e -> _state.update { it.copy(error = e.message ?: "Failed to add vendor") } }
        }
    }

    suspend fun vendorBalance(externalId: String): VendorBalanceInfo? = accountingRepo.fetchVendorBalance(externalId)

    /** Records the expense and posts it to Humble Ledger durably (success/failure persisted). */
    fun createExpense(
        title: String, notes: String, amount: Double,
        vendorId: String, vendorName: String, cashPaid: Double, bankPaid: Double,
    ) {
        val hotelId = AppContext.hotelId
        if (hotelId.isBlank()) return
        viewModelScope.launch {
            val expense = Expense(
                hotelId = hotelId, title = title.trim(), notes = notes.trim(), amount = amount,
                vendorId = vendorId, vendorName = vendorName, cashPaid = cashPaid, bankPaid = bankPaid,
            )
            runCatching { expenseRepo.add(expense) }
                .onSuccess { id -> syncExpense(expense.copy(id = id)) }
                .onFailure { e -> _state.update { it.copy(error = e.message ?: "Failed to record expense") } }
        }
    }

    private suspend fun syncExpense(expense: Expense) {
        accountingRepo.settleExpense(expense)
            .onSuccess { posted -> if (posted) runCatching { expenseRepo.markSynced(expense.id) } }
            .onFailure { e -> runCatching { expenseRepo.markSyncFailed(expense.id, e.message ?: "sync failed") } }
    }

    /**
     * Deletes an expense. If it was synced to the ledger, its accounting is REVERSED first (which
     * also returns the cash/bank to the Firestore till), and only on success is the document
     * removed — so we never erase a record while the ledger still holds it. Unsynced expenses had
     * no ledger/till effect, so they're simply removed.
     */
    fun deleteExpense(id: String) {
        val expense = _state.value.expenses.find { it.id == id } ?: return
        if (_state.value.deletingId == id) return
        _state.update { it.copy(deletingId = id, error = null) }
        viewModelScope.launch {
            if (expense.synced) {
                val reversed = accountingRepo.reverseExpense(expense)
                if (reversed.isFailure) {
                    _state.update { it.copy(deletingId = null, error = "Couldn't reverse the expense in the ledger — not deleted. Try again.") }
                    return@launch
                }
            }
            runCatching { expenseRepo.delete(id) }
                .onSuccess { _state.update { it.copy(deletingId = null) } }   // live listener drops the row
                .onFailure { e -> _state.update { it.copy(deletingId = null, error = e.message ?: "Failed to delete expense") } }
        }
    }

    private suspend fun retryUnsynced() {
        val hotelId = AppContext.hotelId
        if (hotelId.isBlank()) return
        val pending = runCatching { expenseRepo.getUnsynced(hotelId) }.getOrElse { emptyList() }
        for (e in pending) syncExpense(e)
    }
}
