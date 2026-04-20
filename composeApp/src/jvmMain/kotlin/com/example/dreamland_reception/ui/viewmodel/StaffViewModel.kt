package com.example.dreamland_reception.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.dreamland_reception.data.AppContext
import com.example.dreamland_reception.data.model.Complaint
import com.example.dreamland_reception.data.model.Order
import com.example.dreamland_reception.data.model.StaffMember
import com.example.dreamland_reception.data.repository.ComplaintRepository
import com.example.dreamland_reception.data.repository.FirestoreComplaintRepository
import com.example.dreamland_reception.data.repository.FirestoreOrderRepository
import com.example.dreamland_reception.data.repository.FirestoreStaffRepository
import com.example.dreamland_reception.data.repository.OrderRepository
import com.example.dreamland_reception.data.repository.StaffRepository
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.Date

// ── Backward-compat sealed state (used by DashboardViewModel / refreshAllViewModels) ──────────

sealed interface StaffUiState {
    data object Loading : StaffUiState
    data class Success(val members: List<StaffMember>) : StaffUiState
    data class Error(val message: String) : StaffUiState
}

// ── Screen state ──────────────────────────────────────────────────────────────

data class StaffScreenState(
    val staff: List<StaffMember> = emptyList(),
    val isLoading: Boolean = false,
    val error: String? = null,
    val searchQuery: String = "",
    val roleFilter: String = "",         // "" | "HOUSEKEEPING" | "MAINTENANCE" | "RECEPTION"
    val availabilityFilter: String = "", // "" | "AVAILABLE" | "BUSY"
    val statusFilter: String = "",       // "" | "ACTIVE" | "INACTIVE"
) {
    val filtered: List<StaffMember>
        get() {
            val q = searchQuery.trim().lowercase()
            return staff.filter { m ->
                (q.isEmpty() || m.name.lowercase().contains(q) || m.phone.contains(q)) &&
                (roleFilter.isBlank() || m.role == roleFilter) &&
                (availabilityFilter.isBlank() ||
                    (availabilityFilter == "AVAILABLE" && m.isAvailable && m.isActive) ||
                    (availabilityFilter == "BUSY" && !m.isAvailable && m.isActive)) &&
                (statusFilter.isBlank() ||
                    (statusFilter == "ACTIVE" && m.isActive) ||
                    (statusFilter == "INACTIVE" && !m.isActive))
            }
        }

    val activeCount: Int get() = staff.count { it.isActive }
    val availableCount: Int get() = staff.count { it.isActive && it.isAvailable }
    val busyCount: Int get() = staff.count { it.isActive && !it.isAvailable }
    val inactiveCount: Int get() = staff.count { !it.isActive }
}

// ── Add / Edit form dialog state ──────────────────────────────────────────────

data class StaffFormDialogState(
    val isOpen: Boolean = false,
    val isEditing: Boolean = false,
    val staffId: String = "",
    val name: String = "",
    val phone: String = "",
    val role: String = "HOUSEKEEPING",
    val isActive: Boolean = true,
    val isSaving: Boolean = false,
    val error: String? = null,
)

// ── Assign Tasks dialog state ─────────────────────────────────────────────────

data class AssignTasksDialogState(
    val isOpen: Boolean = false,
    val staffMember: StaffMember? = null,
    val newOrders: List<Order> = emptyList(),
    val newComplaints: List<Complaint> = emptyList(),
    val selectedOrderIds: Set<String> = emptySet(),
    val selectedComplaintIds: Set<String> = emptySet(),
    val isLoading: Boolean = false,
    val isSaving: Boolean = false,
    val error: String? = null,
)

// ── ViewModel ─────────────────────────────────────────────────────────────────

class StaffViewModel(
    private val repo: StaffRepository = FirestoreStaffRepository,
    private val orderRepo: OrderRepository = FirestoreOrderRepository,
    private val complaintRepo: ComplaintRepository = FirestoreComplaintRepository,
) : ViewModel() {

    private val _screenState = MutableStateFlow(StaffScreenState(isLoading = true))
    val screenState: StateFlow<StaffScreenState> = _screenState.asStateFlow()

    private val _formDialog = MutableStateFlow(StaffFormDialogState())
    val formDialog: StateFlow<StaffFormDialogState> = _formDialog.asStateFlow()

    private val _assignTasksDialog = MutableStateFlow(AssignTasksDialogState())
    val assignTasksDialog: StateFlow<AssignTasksDialogState> = _assignTasksDialog.asStateFlow()

    // Backward-compat sealed state
    private val _uiState = MutableStateFlow<StaffUiState>(StaffUiState.Loading)
    val uiState: StateFlow<StaffUiState> = _uiState.asStateFlow()

    private var listenerJob: Job? = null

    init { startListener() }

    // ── Real-time listener ────────────────────────────────────────────────────

    private fun startListener() {
        val hotelId = AppContext.hotelId
        if (hotelId.isBlank()) {
            _screenState.update { it.copy(isLoading = false) }
            return
        }
        listenerJob?.cancel()
        listenerJob = viewModelScope.launch {
            runCatching {
                repo.listenByHotel(hotelId).collect { staff ->
                    _screenState.update { it.copy(staff = staff, isLoading = false, error = null) }
                    _uiState.value = StaffUiState.Success(staff.filter { it.isActive })
                }
            }.onFailure { e ->
                _screenState.update { it.copy(isLoading = false, error = e.message ?: "Failed to load staff") }
                _uiState.value = StaffUiState.Error(e.message ?: "Failed to load staff")
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
        listenerJob?.cancel()
    }

    // Backward-compat shim — called by refreshAllViewModels()
    fun loadActive() { startListener() }

    // ── Filters ───────────────────────────────────────────────────────────────

    fun onSearch(q: String) = _screenState.update { it.copy(searchQuery = q) }
    fun onRoleFilter(r: String) = _screenState.update { it.copy(roleFilter = r) }
    fun onAvailabilityFilter(a: String) = _screenState.update { it.copy(availabilityFilter = a) }
    fun onStatusFilter(s: String) = _screenState.update { it.copy(statusFilter = s) }

    // ── Actions ───────────────────────────────────────────────────────────────

    fun toggleAvailability(member: StaffMember) {
        launchWithGlobalLoading {
            runCatching { repo.setAvailability(member.id, !member.isAvailable) }
                .onFailure { e -> _screenState.update { it.copy(error = e.message) } }
        }
    }

    fun toggleActive(member: StaffMember) {
        launchWithGlobalLoading {
            runCatching { repo.setActive(member.id, !member.isActive) }
                .onFailure { e -> _screenState.update { it.copy(error = e.message) } }
        }
    }

    // ── Form dialog ───────────────────────────────────────────────────────────

    fun openAddStaff() {
        _formDialog.value = StaffFormDialogState(isOpen = true)
    }

    fun openEditStaff(member: StaffMember) {
        _formDialog.value = StaffFormDialogState(
            isOpen = true,
            isEditing = true,
            staffId = member.id,
            name = member.name,
            phone = member.phone,
            role = member.role.ifBlank { "HOUSEKEEPING" },
            isActive = member.isActive,
        )
    }

    fun closeForm() { _formDialog.value = StaffFormDialogState() }

    fun onFormName(v: String) = _formDialog.update { it.copy(name = v, error = null) }
    fun onFormPhone(v: String) = _formDialog.update { it.copy(phone = v, error = null) }
    fun onFormRole(v: String) = _formDialog.update { it.copy(role = v) }
    fun onFormActive(v: Boolean) = _formDialog.update { it.copy(isActive = v) }

    // ── Assign Tasks dialog ───────────────────────────────────────────────────

    fun openAssignTasks(member: StaffMember) {
        _assignTasksDialog.value = AssignTasksDialogState(isOpen = true, staffMember = member, isLoading = true)
        launchWithGlobalLoading {
            val hotelId = AppContext.hotelId
            val orders = runCatching { orderRepo.getByHotel(hotelId) }.getOrElse { emptyList() }
                .filter { it.status == "NEW" }
            val complaints = runCatching { complaintRepo.getByHotel(hotelId) }.getOrElse { emptyList() }
                .filter { it.status == "NEW" }
            _assignTasksDialog.update { it.copy(newOrders = orders, newComplaints = complaints, isLoading = false) }
        }
    }

    fun closeAssignTasks() { _assignTasksDialog.value = AssignTasksDialogState() }

    fun toggleOrderSelection(orderId: String) {
        _assignTasksDialog.update { s ->
            val newSet = if (orderId in s.selectedOrderIds) s.selectedOrderIds - orderId
                         else s.selectedOrderIds + orderId
            s.copy(selectedOrderIds = newSet)
        }
    }

    fun toggleComplaintSelection(complaintId: String) {
        _assignTasksDialog.update { s ->
            val newSet = if (complaintId in s.selectedComplaintIds) s.selectedComplaintIds - complaintId
                         else s.selectedComplaintIds + complaintId
            s.copy(selectedComplaintIds = newSet)
        }
    }

    fun confirmAssignTasks() {
        val s = _assignTasksDialog.value
        val member = s.staffMember ?: return
        if (s.selectedOrderIds.isEmpty() && s.selectedComplaintIds.isEmpty()) {
            closeAssignTasks()
            return
        }
        _assignTasksDialog.update { it.copy(isSaving = true, error = null) }
        launchWithGlobalLoading {
            var failed = false
            s.selectedOrderIds.forEach { orderId ->
                runCatching { orderRepo.updateAssignment(orderId, member.id, member.name) }
                    .onFailure { failed = true }
            }
            s.selectedComplaintIds.forEach { complaintId ->
                runCatching { complaintRepo.updateAssignment(complaintId, member.id, member.name) }
                    .onFailure { failed = true }
            }
            if (!failed) {
                runCatching { repo.setAvailability(member.id, false) }
                _assignTasksDialog.value = AssignTasksDialogState()
            } else {
                _assignTasksDialog.update { it.copy(isSaving = false, error = "Some assignments failed. Please retry.") }
            }
        }
    }

    fun submitForm() {
        val s = _formDialog.value
        if (s.name.isBlank()) {
            _formDialog.update { it.copy(error = "Name is required") }
            return
        }
        if (s.phone.isBlank()) {
            _formDialog.update { it.copy(error = "Phone number is required") }
            return
        }
        _formDialog.update { it.copy(isSaving = true, error = null) }
        launchWithGlobalLoading {
            val hotelId = AppContext.hotelId
            if (s.isEditing) {
                val existing = _screenState.value.staff.find { it.id == s.staffId }
                runCatching {
                    repo.update(
                        StaffMember(
                            id = s.staffId,
                            hotelId = hotelId,
                            name = s.name.trim(),
                            phone = s.phone.trim(),
                            role = s.role,
                            email = existing?.email ?: "",
                            department = existing?.department ?: "",
                            shift = existing?.shift ?: "",
                            joiningDate = existing?.joiningDate ?: Date(),
                            isActive = s.isActive,
                            isAvailable = existing?.isAvailable ?: true,
                            salary = existing?.salary ?: 0.0,
                        )
                    )
                }
                    .onSuccess { _formDialog.value = StaffFormDialogState() }
                    .onFailure { e ->
                        _formDialog.update { it.copy(isSaving = false, error = e.message ?: "Failed to update staff") }
                    }
            } else {
                runCatching {
                    repo.add(
                        StaffMember(
                            hotelId = hotelId,
                            name = s.name.trim(),
                            phone = s.phone.trim(),
                            role = s.role,
                            isActive = s.isActive,
                            isAvailable = true,
                        )
                    )
                }
                    .onSuccess { _formDialog.value = StaffFormDialogState() }
                    .onFailure { e ->
                        _formDialog.update { it.copy(isSaving = false, error = e.message ?: "Failed to add staff") }
                    }
            }
        }
    }
}
