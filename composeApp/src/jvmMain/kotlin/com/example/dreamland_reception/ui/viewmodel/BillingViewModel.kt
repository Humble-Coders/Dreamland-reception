package com.example.dreamland_reception.ui.viewmodel

import androidx.lifecycle.ViewModel
import com.example.dreamland_reception.data.AppContext
import com.example.dreamland_reception.data.model.Bill
import com.example.dreamland_reception.data.repository.BillRepository
import com.example.dreamland_reception.data.repository.FirestoreBillRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

data class BillingScreenState(
    val bills: List<Bill> = emptyList(),
    val isLoading: Boolean = false,
    val error: String? = null,
    val selectedBillId: String? = null,
    val statusFilter: String = "ALL",   // ALL | PAID | PARTIAL | PENDING
    val sortOrder: String = "NEWEST",   // NEWEST | OLDEST
) {
    val filteredSorted: List<Bill>
        get() {
            val base = if (statusFilter == "ALL") bills else bills.filter { it.status == statusFilter }
            return if (sortOrder == "NEWEST") base.sortedByDescending { it.createdAt }
            else base.sortedBy { it.createdAt }
        }

    val selectedBill: Bill? get() = bills.find { it.id == selectedBillId }

    val totalRevenue: Double get() = bills.sumOf { it.totalAmount }
    val totalCollected: Double get() = bills.sumOf { it.totalPaid + it.advancePayment }
    val totalOutstanding: Double get() = bills.sumOf { it.pendingAmount }
    val paidCount: Int get() = bills.count { it.status == "PAID" }
    val partialCount: Int get() = bills.count { it.status == "PARTIAL" }
    val pendingCount: Int get() = bills.count { it.status == "PENDING" }
}

class BillingViewModel(
    private val billRepo: BillRepository = FirestoreBillRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(BillingScreenState(isLoading = true))
    val state: StateFlow<BillingScreenState> = _state.asStateFlow()

    init {
        load()
    }

    fun load() {
        launchWithGlobalLoading {
            _state.update { it.copy(isLoading = true, error = null) }
            runCatching { billRepo.getByHotel(AppContext.hotelId) }
                .onSuccess { bills -> _state.update { it.copy(bills = bills, isLoading = false) } }
                .onFailure { e -> _state.update { it.copy(isLoading = false, error = e.message ?: "Failed to load") } }
        }
    }

    fun selectBill(id: String?) = _state.update { it.copy(selectedBillId = id) }

    fun onStatusFilter(filter: String) = _state.update { it.copy(statusFilter = filter) }

    fun onSortOrder(order: String) = _state.update { it.copy(sortOrder = order) }
}
