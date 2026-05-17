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
import java.util.Date

data class CreateBillDialogState(
    val show: Boolean = false,
    val guestName: String = "",
    val guestPhone: String = "",
    val roomNumber: String = "",
    val checkInDate: Date? = null,
    val checkOutDate: Date? = null,
    val isSaving: Boolean = false,
    val error: String? = null,
    val createdBillId: String? = null,
)

data class BillingScreenState(
    val bills: List<Bill> = emptyList(),
    val isLoading: Boolean = false,
    val error: String? = null,
    val selectedBillId: String? = null,
    val statusFilter: String = "ALL",   // ALL | PAID | PARTIAL | PENDING
    val sortOrder: String = "NEWEST",   // NEWEST | OLDEST
    val createBillDialog: CreateBillDialogState = CreateBillDialogState(),
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

    // ── Create standalone bill ────────────────────────────────────────────────

    fun openCreateBillDialog() = _state.update { it.copy(createBillDialog = CreateBillDialogState(show = true)) }

    fun dismissCreateBillDialog() = _state.update { it.copy(createBillDialog = CreateBillDialogState()) }

    fun onCreateBillGuestName(v: String) = _state.update { it.copy(createBillDialog = it.createBillDialog.copy(guestName = v, error = null)) }

    fun onCreateBillGuestPhone(v: String) = _state.update { it.copy(createBillDialog = it.createBillDialog.copy(guestPhone = v)) }

    fun onCreateBillRoomNumber(v: String) = _state.update { it.copy(createBillDialog = it.createBillDialog.copy(roomNumber = v)) }

    fun onCreateBillCheckIn(date: Date?) = _state.update { it.copy(createBillDialog = it.createBillDialog.copy(checkInDate = date)) }

    fun onCreateBillCheckOut(date: Date?) = _state.update { it.copy(createBillDialog = it.createBillDialog.copy(checkOutDate = date)) }

    fun clearCreatedBillId() = _state.update { it.copy(createBillDialog = it.createBillDialog.copy(createdBillId = null)) }

    fun submitCreateBill() {
        val dlg = _state.value.createBillDialog
        if (dlg.guestName.isBlank()) {
            _state.update { it.copy(createBillDialog = it.createBillDialog.copy(error = "Guest name is required")) }
            return
        }
        _state.update { it.copy(createBillDialog = it.createBillDialog.copy(isSaving = true, error = null)) }
        launchWithGlobalLoading {
            val bill = Bill(
                hotelId = AppContext.hotelId,
                stayId = "",
                guestName = dlg.guestName.trim(),
                roomNumber = dlg.roomNumber.trim(),
                checkInDate = dlg.checkInDate,
                checkOutDate = dlg.checkOutDate,
                status = "PENDING",
                createdAt = Date(),
                updatedAt = Date(),
            )
            runCatching { billRepo.createForStay(bill) }
                .onSuccess { billId ->
                    load()
                    _state.update { it.copy(createBillDialog = CreateBillDialogState(createdBillId = billId)) }
                }
                .onFailure { e ->
                    _state.update { it.copy(createBillDialog = it.createBillDialog.copy(isSaving = false, error = e.message ?: "Failed to create bill")) }
                }
        }
    }
}
