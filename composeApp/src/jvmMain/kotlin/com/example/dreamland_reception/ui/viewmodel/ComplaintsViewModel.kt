package com.example.dreamland_reception.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.dreamland_reception.data.AppContext
import com.example.dreamland_reception.data.model.Complaint
import com.example.dreamland_reception.data.model.ComplaintType
import com.example.dreamland_reception.data.model.StaffMember
import com.example.dreamland_reception.data.model.Stay
import com.example.dreamland_reception.data.repository.ComplaintRepository
import com.example.dreamland_reception.data.repository.ComplaintTypeRepository
import com.example.dreamland_reception.data.repository.FirestoreComplaintRepository
import com.example.dreamland_reception.data.repository.FirestoreComplaintTypeRepository
import com.example.dreamland_reception.data.repository.FirestoreStaffRepository
import com.example.dreamland_reception.data.repository.FirestoreStayRepository
import com.example.dreamland_reception.data.repository.StaffRepository
import com.example.dreamland_reception.data.repository.StayRepository
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.awt.Toolkit
import java.util.Date

// ── Backward-compat sealed state ──────────────────────────────────────────────

sealed interface ComplaintsUiState {
    data object Loading : ComplaintsUiState
    data class Success(val complaints: List<Complaint>) : ComplaintsUiState
    data class Error(val message: String) : ComplaintsUiState
}

// ── Screen state ──────────────────────────────────────────────────────────────

private val PRIORITY_ORDER = mapOf("HIGH" to 0, "MEDIUM" to 1, "LOW" to 2)

data class ComplaintsScreenState(
    val complaints: List<Complaint> = emptyList(),
    val isLoading: Boolean = false,
    val error: String? = null,
    val selectedTab: Int = 0,       // 0=NEW, 1=ASSIGNED, 2=COMPLETED
    val searchQuery: String = "",
    val roomFilter: String = "",
    val staffFilter: String = "",
    val priorityFilter: String = "",
) {
    val newComplaints: List<Complaint>
        get() = complaints.filter { it.status == "NEW" }
            .sortedWith(compareBy({ PRIORITY_ORDER[it.priority] ?: 1 }, { it.reportedAt }))

    val assignedComplaints: List<Complaint>
        get() = complaints.filter { it.status == "ASSIGNED" }
            .sortedWith(compareBy({ PRIORITY_ORDER[it.priority] ?: 1 }, { it.reportedAt }))

    val completedComplaints: List<Complaint>
        get() = complaints.filter { it.status == "COMPLETED" }
            .sortedByDescending { it.resolvedAt }

    val activeTabComplaints: List<Complaint>
        get() = when (selectedTab) {
            0 -> newComplaints; 1 -> assignedComplaints; else -> completedComplaints
        }

    val filtered: List<Complaint>
        get() {
            val q = searchQuery.trim().lowercase()
            return activeTabComplaints.filter { c ->
                (q.isEmpty() || c.guestName.lowercase().contains(q) ||
                    c.roomNumber.lowercase().contains(q) ||
                    c.description.lowercase().contains(q) ||
                    c.type.lowercase().contains(q)) &&
                (roomFilter.isBlank() || c.roomNumber == roomFilter) &&
                (staffFilter.isBlank() || c.assignedTo == staffFilter) &&
                (priorityFilter.isBlank() || c.priority == priorityFilter)
            }
        }

    val uniqueRooms: List<String>
        get() = complaints.map { it.roomNumber }.distinct().sorted()

    val uniqueStaff: List<Pair<String, String>>
        get() = complaints.filter { it.assignedTo.isNotBlank() }
                          .map { it.assignedTo to it.assignedToName }
                          .distinctBy { it.first }
                          .sortedBy { it.second }
}

// ── Create Complaint dialog state ─────────────────────────────────────────────

data class CreateComplaintDialogState(
    val isOpen: Boolean = false,
    val selectedStayId: String = "",
    val selectedStayDisplay: String = "",
    val stayQuery: String = "",
    val activeStays: List<Stay> = emptyList(),
    val complaintTypes: List<ComplaintType> = emptyList(),
    val category: String = "",        // stores ComplaintType.name of selected chip
    val description: String = "",
    val priority: String = "MEDIUM",
    val isSaving: Boolean = false,
    val error: String? = null,
    val isLoadingStays: Boolean = false,
    val isLoadingTypes: Boolean = false,
) {
    val filteredStays: List<Stay>
        get() = if (stayQuery.isBlank()) activeStays
        else activeStays.filter {
            it.roomNumber.contains(stayQuery, ignoreCase = true) ||
                it.guestName.contains(stayQuery, ignoreCase = true)
        }
}

// ── Assign Staff dialog state (complaints) ────────────────────────────────────

data class ComplaintAssignStaffDialogState(
    val isOpen: Boolean = false,
    val complaintId: String = "",
    val activeStaff: List<StaffMember> = emptyList(),
    val isLoading: Boolean = false,
    val error: String? = null,
)

// ── ViewModel ─────────────────────────────────────────────────────────────────

class ComplaintsViewModel(
    private val complaintRepo: ComplaintRepository = FirestoreComplaintRepository,
    private val complaintTypeRepo: ComplaintTypeRepository = FirestoreComplaintTypeRepository,
    private val staffRepo: StaffRepository = FirestoreStaffRepository,
    private val stayRepo: StayRepository = FirestoreStayRepository,
) : ViewModel() {

    private val _screenState = MutableStateFlow(ComplaintsScreenState(isLoading = true))
    val screenState: StateFlow<ComplaintsScreenState> = _screenState.asStateFlow()

    private val _createDialog = MutableStateFlow(CreateComplaintDialogState())
    val createDialog: StateFlow<CreateComplaintDialogState> = _createDialog.asStateFlow()

    private val _assignDialog = MutableStateFlow(ComplaintAssignStaffDialogState())
    val assignDialog: StateFlow<ComplaintAssignStaffDialogState> = _assignDialog.asStateFlow()

    // Backward-compat sealed state
    private val _uiState = MutableStateFlow<ComplaintsUiState>(ComplaintsUiState.Loading)
    val uiState: StateFlow<ComplaintsUiState> = _uiState.asStateFlow()

    private var listenerJob: Job? = null
    private var previousComplaintIds: Set<String> = emptySet()

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
                complaintRepo.listenByHotel(hotelId).collect { complaints ->
                    val currentIds = complaints.map { it.id }.toSet()
                    val firstEmit = previousComplaintIds.isEmpty() && _screenState.value.complaints.isEmpty()
                    val newArrivals = currentIds - previousComplaintIds
                    if (!firstEmit && newArrivals.isNotEmpty()) {
                        runCatching { Toolkit.getDefaultToolkit().beep() }
                    }
                    previousComplaintIds = currentIds
                    _screenState.update { it.copy(complaints = complaints, isLoading = false, error = null) }
                    _uiState.value = ComplaintsUiState.Success(complaints.filter { it.status != "COMPLETED" })
                }
            }.onFailure { e ->
                _screenState.update { it.copy(isLoading = false, error = e.message ?: "Failed to load complaints") }
                _uiState.value = ComplaintsUiState.Error(e.message ?: "Failed to load complaints")
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
        listenerJob?.cancel()
    }

    // Backward-compat shim — called by refreshAllViewModels()
    fun loadOpen() { startListener() }

    // ── Tab / filter ──────────────────────────────────────────────────────────

    fun onTabSelected(i: Int) = _screenState.update { it.copy(selectedTab = i) }
    fun onSearch(q: String) = _screenState.update { it.copy(searchQuery = q) }
    fun onRoomFilter(r: String) = _screenState.update { it.copy(roomFilter = r) }
    fun onStaffFilter(s: String) = _screenState.update { it.copy(staffFilter = s) }
    fun onPriorityFilter(p: String) = _screenState.update { it.copy(priorityFilter = p) }

    // ── Status update ─────────────────────────────────────────────────────────

    fun updateStatus(complaintId: String, status: String) {
        launchWithGlobalLoading {
            val complaint = _screenState.value.complaints.find { it.id == complaintId }
            runCatching { complaintRepo.updateStatus(complaintId, status) }
                .onSuccess {
                    if (status == "COMPLETED" && complaint?.assignedTo?.isNotBlank() == true) {
                        runCatching { staffRepo.setAvailability(complaint.assignedTo, true) }
                    }
                }
                .onFailure { e -> _screenState.update { it.copy(error = e.message) } }
        }
    }

    fun resolve(complaintId: String) {
        launchWithGlobalLoading {
            val complaint = _screenState.value.complaints.find { it.id == complaintId }
            runCatching { complaintRepo.resolve(complaintId) }
                .onSuccess {
                    // Free up the staff member when complaint is resolved
                    if (complaint?.assignedTo?.isNotBlank() == true) {
                        runCatching { staffRepo.setAvailability(complaint.assignedTo, true) }
                    }
                }
                .onFailure { e -> _screenState.update { it.copy(error = e.message) } }
        }
    }

    // ── Create Complaint dialog ───────────────────────────────────────────────

    fun openCreateComplaint() {
        val hotelId = AppContext.hotelId
        _createDialog.value = CreateComplaintDialogState(
            isOpen = true, isLoadingStays = true, isLoadingTypes = true,
        )
        launchWithGlobalLoading {
            val stays = runCatching { stayRepo.getActive(hotelId) }.getOrElse { emptyList() }
            val types = runCatching { complaintTypeRepo.getByHotel(hotelId) }.getOrElse { emptyList() }
                .filter { it.isActive }
            _createDialog.update { it.copy(
                activeStays = stays,
                complaintTypes = types,
                isLoadingStays = false,
                isLoadingTypes = false,
            ) }
        }
    }

    fun closeCreateComplaint() { _createDialog.value = CreateComplaintDialogState() }

    fun onCreateStayQuery(q: String) = _createDialog.update { s ->
        s.copy(
            stayQuery = q,
            selectedStayId = if (q != s.selectedStayDisplay) "" else s.selectedStayId,
            selectedStayDisplay = if (q != s.selectedStayDisplay) "" else s.selectedStayDisplay,
        )
    }

    fun onCreateStaySelected(stayId: String) {
        val stay = _createDialog.value.activeStays.find { it.id == stayId } ?: return
        val display = "Room ${stay.roomNumber} — ${stay.guestName}"
        _createDialog.update { it.copy(
            selectedStayId = stayId,
            selectedStayDisplay = display,
            stayQuery = display,
        ) }
    }

    fun onCreateCategory(v: String) = _createDialog.update { it.copy(category = v) }
    fun onCreateDescription(v: String) = _createDialog.update { it.copy(description = v) }
    fun onCreatePriority(v: String) = _createDialog.update { it.copy(priority = v) }

    fun refreshCreateComplaintTypes() {
        val hotelId = AppContext.hotelId
        if (!_createDialog.value.isOpen) return
        launchWithGlobalLoading {
            val types = runCatching { complaintTypeRepo.getByHotel(hotelId) }.getOrElse { return@launchWithGlobalLoading }
                .filter { it.isActive }
            _createDialog.update { it.copy(complaintTypes = types) }
        }
    }

    fun submitCreateComplaint() {
        val s = _createDialog.value
        if (s.selectedStayId.isBlank()) {
            _createDialog.update { it.copy(error = "Please select a stay") }
            return
        }
        if (s.description.isBlank()) {
            _createDialog.update { it.copy(error = "Please describe the complaint") }
            return
        }
        val stay = s.activeStays.find { it.id == s.selectedStayId } ?: return
        _createDialog.update { it.copy(isSaving = true, error = null) }
        launchWithGlobalLoading {
            runCatching {
                complaintRepo.add(Complaint(
                    hotelId = AppContext.hotelId,
                    stayId = stay.id,
                    guestName = stay.guestName,
                    roomNumber = stay.roomNumber,
                    type = s.category,
                    description = s.description.trim(),
                    priority = s.priority,
                    reportedAt = Date(),
                ))
            }.onSuccess {
                _createDialog.value = CreateComplaintDialogState()
            }.onFailure { e ->
                _createDialog.update { it.copy(isSaving = false, error = e.message ?: "Failed to submit complaint") }
            }
        }
    }

    // ── Assign Staff dialog ───────────────────────────────────────────────────

    fun openAssignStaff(complaintId: String) {
        _assignDialog.value = ComplaintAssignStaffDialogState(isOpen = true, complaintId = complaintId, isLoading = true)
        launchWithGlobalLoading {
            // Only show active + available staff for assignment
            val staff = runCatching { staffRepo.getActive() }.getOrElse { emptyList() }
                .filter { it.isAvailable }
            _assignDialog.update { it.copy(activeStaff = staff, isLoading = false) }
        }
    }

    fun closeAssignStaff() { _assignDialog.value = ComplaintAssignStaffDialogState() }

    fun assignStaff(staffId: String, staffName: String) {
        val complaintId = _assignDialog.value.complaintId
        launchWithGlobalLoading {
            runCatching { complaintRepo.updateAssignment(complaintId, staffId, staffName) }
                .onSuccess {
                    // Mark the staff member as busy
                    runCatching { staffRepo.setAvailability(staffId, false) }
                    _assignDialog.value = ComplaintAssignStaffDialogState()
                }
                .onFailure { e -> _assignDialog.update { it.copy(error = e.message) } }
        }
    }
}
