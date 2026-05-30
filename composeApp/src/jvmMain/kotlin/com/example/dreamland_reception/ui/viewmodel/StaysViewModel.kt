package com.example.dreamland_reception.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.dreamland_reception.data.AppContext
import com.example.dreamland_reception.data.model.Bill
import com.example.dreamland_reception.data.model.BillItem
import com.example.dreamland_reception.data.model.BillingInvoice
import com.example.dreamland_reception.data.model.Booking
import com.example.dreamland_reception.data.model.Complaint
import com.example.dreamland_reception.data.model.Order
import com.example.dreamland_reception.data.model.OrderItem
import com.example.dreamland_reception.data.model.Room
import com.example.dreamland_reception.data.model.RoomInstance
import com.example.dreamland_reception.data.model.GuestRecord
import com.example.dreamland_reception.data.model.Stay
import com.example.dreamland_reception.data.repository.BillRepository
import com.example.dreamland_reception.data.repository.BookingRepository
import com.example.dreamland_reception.data.repository.ComplaintRepository
import com.example.dreamland_reception.data.repository.FirestoreBillRepository
import com.example.dreamland_reception.data.repository.FirestoreBookingRepository
import com.example.dreamland_reception.data.repository.FirestoreComplaintRepository
import com.example.dreamland_reception.data.repository.FirestoreFoodItemRepository
import com.example.dreamland_reception.data.repository.FirestoreOrderRepository
import com.example.dreamland_reception.data.repository.FirestoreRoomInstanceRepository
import com.example.dreamland_reception.data.repository.FirestoreRoomRepository
import com.example.dreamland_reception.data.repository.FirestoreServiceRepository
import com.example.dreamland_reception.data.repository.FirestoreStayRepository
import com.example.dreamland_reception.data.repository.FoodItemRepository
import com.example.dreamland_reception.data.repository.OrderRepository
import com.example.dreamland_reception.data.repository.RoomInstanceRepository
import com.example.dreamland_reception.data.repository.RoomRepository
import com.example.dreamland_reception.data.repository.ServiceRepository
import com.example.dreamland_reception.data.model.BookingSource
import com.example.dreamland_reception.data.repository.FirestoreBookingSourceRepository
import com.example.dreamland_reception.data.repository.StayRepository
import com.example.dreamland_reception.DreamlandAppInitializer
import com.example.dreamland_reception.util.localTodayUtcMidnight
import com.example.dreamland_reception.util.toLocalDayUtcMidnight
import com.example.dreamland_reception.util.toMidnightUtc
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.temporal.ChronoUnit
import java.util.Calendar
import java.util.Date
import java.util.UUID

// ── List state ────────────────────────────────────────────────────────────────

data class StaysListState(
    val stays: List<Stay> = emptyList(),
    val isLoading: Boolean = false,
    val error: String? = null,
    val searchQuery: String = "",
    val showCompleted: Boolean = false,
    val selectedStayId: String? = null,
    val pendingOrdersByStay: Map<String, Int> = emptyMap(),
    val openComplaintsByStay: Map<String, Int> = emptyMap(),
    // categoryId → display name, loaded from `rooms` collection
    val categoryNames: Map<String, String> = emptyMap(),
) {
    val filtered: List<Stay> get() {
        val q = searchQuery.trim().lowercase()
        return stays.filter { stay ->
            val matchesStatus = if (showCompleted) true else stay.status == "ACTIVE"
            val matchesQuery = q.isEmpty() ||
                stay.guestName.lowercase().contains(q) ||
                stay.roomNumber.lowercase().contains(q)
            matchesStatus && matchesQuery
        }
    }
}

// ── Walk-in form state ────────────────────────────────────────────────────────

data class GuestEntry(
    val name: String = "",
    val phone: String = "",          // only shown for index 0 (primary guest)
    val idProofVerified: Boolean = false,
)

// ── Group check-in helpers ────────────────────────────────────────────────────

data class CategoryRequirement(
    val categoryId: String,
    val categoryName: String,
    val count: Int,
)

data class GroupChangeConfirmDialogState(
    val pendingInstanceId: String = "",
    val pendingAction: String = "",       // "ADD" | "REMOVE"
    val deviatingCategoryId: String = "",
    val availabilityMap: Map<String, Int> = emptyMap(),
    val categoryNames: Map<String, String> = emptyMap(), // categoryId → display name
    // When set, overrides state.categoryRequirements for the "Original booking:" line
    val originalRequirements: List<CategoryRequirement> = emptyList(),
)

data class CheckInMismatchLine(val categoryName: String, val count: Int)

data class CheckInMismatchConfirmState(
    val originalLines: List<CheckInMismatchLine> = emptyList(),
    val currentLines: List<CheckInMismatchLine> = emptyList(),
    val unmetBookingCount: Int = 0,         // how many group bookings won't be linked
)

data class WalkInState(
    val isOpen: Boolean = false,
    // primary guest info (synced from guestEntries[0])
    val guestName: String = "",
    val guestPhone: String = "",
    val guestEntries: List<GuestEntry> = listOf(GuestEntry()),
    val selectedCategoryId: String = "",
    val selectedCategoryName: String = "",
    val selectedCategoryBreakfastPrice: Double = 0.0,
    val selectedInstanceIds: Set<String> = emptySet(),
    // instanceId -> RoomInstance (for display in the selected-rooms panel)
    val selectedInstanceDetails: Map<String, RoomInstance> = emptyMap(),
    // When false, each room gets its own primary guest; true = shared guestEntries[0]
    val sameGuestForAllRooms: Boolean = true,
    // instanceId -> per-room primary guest (used when sameGuestForAllRooms = false)
    val roomGuestMap: Map<String, GuestEntry> = emptyMap(),
    val checkInTime: Date = Date(),
    val expectedCheckOut: Date? = null,
    val adults: Int = 1,
    val children: Int = 0,
    val breakfast: Boolean = false,
    val advancePayment: String = "",
    // All categories from Firestore (unfiltered — used when no checkout date)
    val categories: List<Room> = emptyList(),
    // Categories passing Steps 5-6 (set once checkout date + guests are known)
    val availableCategories: List<Room> = emptyList(),
    // categoryId -> available room count (Steps 1-5)
    val categoryAvailability: Map<String, Int> = emptyMap(),
    // categoryId -> effective price (seasonal or base)
    val categoryPrices: Map<String, Double> = emptyMap(),
    // Room instances for the currently viewed category
    val selectableInstances: List<RoomInstance> = emptyList(),   // normal, clickable
    val cleaningInstances: List<RoomInstance> = emptyList(),     // shown grayed, unclickable
    val dueOutInstances: List<RoomInstance> = emptyList(),       // expected checkout today, not yet out
    // Legacy alias — equals selectableInstances; kept for UI compatibility
    val availableInstances: List<RoomInstance> = emptyList(),
    val availableCount: Int = 0,
    val isLoadingAvailability: Boolean = false,
    val isSaving: Boolean = false,
    val error: String? = null,
    val sourceBooking: Booking? = null,
    val isBookingMode: Boolean = false,
    val source: String = "",
    // Group check-in fields
    // Booking mode: rooms reserved per category (categoryId → count); specific chips are optional extras
    val bookingRoomCountsByCategory: Map<String, Int> = emptyMap(),
    // Booking sources (loaded from bookingSources collection for the source dropdown)
    val bookingSources: List<com.example.dreamland_reception.data.model.BookingSource> = emptyList(),
    val selectedSourceId: String = "",
    val isGroupCheckIn: Boolean = false,
    val groupBookings: List<Booking> = emptyList(),
    val categoryRequirements: List<CategoryRequirement> = emptyList(),
    val instanceToBookingId: Map<String, String> = emptyMap(),
    val groupChangeConfirmDialog: GroupChangeConfirmDialogState? = null,
    val checkInMismatchConfirm: CheckInMismatchConfirmState? = null,
    // Cached from last computeAvailability() run — used by pre-flight check at submit time (no extra reads)
    val cachedConfirmedBookings: List<Booking> = emptyList(),
    val cachedActiveStays: List<com.example.dreamland_reception.data.model.Stay> = emptyList(),
)

// ── From-booking dialog state ─────────────────────────────────────────────────

data class FromBookingState(
    val isOpen: Boolean = false,
    val bookings: List<Booking> = emptyList(),
    val isLoading: Boolean = false,
)

// ── Detail panel state ────────────────────────────────────────────────────────

data class StayDetailState(
    val bill: Bill? = null,
    val orders: List<Order> = emptyList(),
    val complaints: List<Complaint> = emptyList(),
    val isLoading: Boolean = false,
)

// ── Add-order dialog state ────────────────────────────────────────────────────

data class CatalogItem(
    val id: String = "",
    val name: String,
    val price: Double,
    val category: String,
    val isAvailable: Boolean = true,
)

data class OrderItemEntry(
    val name: String = "",
    val quantity: Int = 1,
    val price: String = "",
    val category: String = "",
    val suggestions: List<CatalogItem> = emptyList(),
    val showSuggestions: Boolean = false,
)

data class AddOrderState(
    val isOpen: Boolean = false,
    val items: List<OrderItemEntry> = listOf(OrderItemEntry()),
    val notes: String = "",
    val isSaving: Boolean = false,
    val error: String? = null,
    val catalogItems: List<CatalogItem> = emptyList(),
    val isLoadingCatalog: Boolean = false,
) {
    val categories: List<String> get() =
        catalogItems.filter { it.isAvailable }
            .flatMap { it.category.split(",").map(String::trim) }
            .filter { it.isNotBlank() }.distinct()
}

// ── Add-complaint dialog state ────────────────────────────────────────────────

data class AddComplaintState(
    val isOpen: Boolean = false,
    val category: String = "",
    val description: String = "",
    val priority: String = "MEDIUM",
    val isSaving: Boolean = false,
    val error: String? = null,
)

// ── Check-out dialog state ────────────────────────────────────────────────────

data class CheckOutState(
    val isOpen: Boolean = false,
    val stay: Stay? = null,
    val bill: BillingInvoice? = null,
    val isProcessing: Boolean = false,
    val error: String? = null,
    val isLateCheckout: Boolean = false,
    val lateCheckoutCharge: Double = 0.0,
    val flatLateCheckoutFee: Double = 0.0,
    val roomPricePerNight: Double = 0.0,
    val lateChargeType: String = "FLAT",
    val customLateChargeInput: String = "",
    val hotelCheckOutTime: String = "11:00",
    val ordersTotal: Double = 0.0,
    val navigateToBilling: Boolean = false,
    val checkedOutStayId: String = "",
    // Orders that are not yet COMPLETED — shown with checkboxes at checkout
    val pendingOrders: List<Order> = emptyList(),
    val checkedOrderIds: Set<String> = emptySet(),
    // Group checkout — populated when the stay belongs to a group booking
    val groupStays: List<Stay> = emptyList(),           // all active stays in the group
    val groupBills: Map<String, BillingInvoice> = emptyMap(), // stayId → display bill
    val checkedGroupStayIds: Set<String> = emptySet(),  // stayIds selected for checkout
)

// ── Extend stay state ─────────────────────────────────────────────────────────

data class ExtendAvailableCategory(
    val categoryName: String,
    val availableCount: Int,
    val pricePerNight: Double,
)

data class ChangeRoomState(
    val isOpen: Boolean = false,
    val stay: Stay? = null,
    val selectableRooms: List<RoomInstance> = emptyList(),
    val cleaningRooms: List<RoomInstance> = emptyList(),
    val selectedInstance: RoomInstance? = null,
    val categoryNames: Map<String, String> = emptyMap(),
    val isLoading: Boolean = false,
    val isSaving: Boolean = false,
    val error: String? = null,
)

data class ExtendStayState(
    val isOpen: Boolean = false,
    val stay: Stay? = null,
    val newCheckOut: Date? = null,
    val isChecking: Boolean = false,
    val isSaving: Boolean = false,
    val roomAvailable: Boolean? = null,   // null = not checked yet
    val alternativeInstances: List<RoomInstance> = emptyList(),
    // All categories available for the extension window (shown when room/category not available)
    val availableCategoryOptions: List<ExtendAvailableCategory> = emptyList(),
    val error: String? = null,
)

// ── ViewModel ─────────────────────────────────────────────────────────────────

class StaysViewModel(
    private val stayRepo: StayRepository = FirestoreStayRepository,
    private val bookingRepo: BookingRepository = FirestoreBookingRepository,
    private val roomRepo: RoomRepository = FirestoreRoomRepository,
    private val instanceRepo: RoomInstanceRepository = FirestoreRoomInstanceRepository,
    private val billRepo: BillRepository = FirestoreBillRepository,
    private val orderRepo: OrderRepository = FirestoreOrderRepository,
    private val complaintRepo: ComplaintRepository = FirestoreComplaintRepository,
    private val foodItemRepo: FoodItemRepository = FirestoreFoodItemRepository,
    private val serviceRepo: ServiceRepository = FirestoreServiceRepository,
) : ViewModel() {

    private val _listState = MutableStateFlow(StaysListState(isLoading = true))
    val listState: StateFlow<StaysListState> = _listState.asStateFlow()

    // Real-time stays listener (started/stopped by the screen via startListeningStays/stopListeningStays)
    private var staysListenerJob: Job? = null

    // Real-time booking listener active while the walk-in dialog is open
    private var walkInAvailabilityJob: Job? = null

    private val _walkInState = MutableStateFlow(WalkInState())
    val walkInState: StateFlow<WalkInState> = _walkInState.asStateFlow()

    private val _fromBookingState = MutableStateFlow(FromBookingState())
    val fromBookingState: StateFlow<FromBookingState> = _fromBookingState.asStateFlow()

    private val _detailState = MutableStateFlow(StayDetailState())
    val detailState: StateFlow<StayDetailState> = _detailState.asStateFlow()

    private val _checkOutState = MutableStateFlow(CheckOutState())
    val checkOutState: StateFlow<CheckOutState> = _checkOutState.asStateFlow()

    private val _extendStayState = MutableStateFlow(ExtendStayState())
    val extendStayState: StateFlow<ExtendStayState> = _extendStayState.asStateFlow()

    private val _changeRoomState = MutableStateFlow(ChangeRoomState())
    val changeRoomState: StateFlow<ChangeRoomState> = _changeRoomState.asStateFlow()

    private val _addOrderState = MutableStateFlow(AddOrderState())
    val addOrderState: StateFlow<AddOrderState> = _addOrderState.asStateFlow()

    private val _addComplaintState = MutableStateFlow(AddComplaintState())
    val addComplaintState: StateFlow<AddComplaintState> = _addComplaintState.asStateFlow()

    init {
        loadActive()
    }


    // ── List ──────────────────────────────────────────────────────────────────

    fun loadActive() {
        val hotelId = AppContext.hotelId
        launchWithGlobalLoading {
            _listState.update { it.copy(isLoading = true, error = null) }
            // Build categoryId → name from the bookings collection (most reliable source)
            val catNames = runCatching { bookingRepo.getAllByHotel(hotelId) }
                .getOrElse { emptyList() }
                .filter { it.roomCategoryId.isNotBlank() && it.roomCategoryName.isNotBlank() }
                .associate { it.roomCategoryId to it.roomCategoryName }
            runCatching { stayRepo.getActive(hotelId) }
                .onSuccess { stays ->
                    _listState.update { it.copy(stays = stays, isLoading = false, categoryNames = catNames) }
                }
                .onFailure { e ->
                    _listState.update { it.copy(isLoading = false, error = e.message ?: "Failed to load stays") }
                }
        }
    }

    /**
     * Starts a real-time Firestore listener for active stays.
     * Call from [StaysScreen] via [DisposableEffect] when the screen enters composition.
     */
    fun startListeningStays() {
        val hotelId = AppContext.hotelId
        staysListenerJob?.cancel()
        staysListenerJob = viewModelScope.launch {
            stayRepo.listenActive(hotelId)
                .catch { e -> _listState.update { it.copy(error = e.message ?: "Stay listener error") } }
                .collect { stays ->
                    _listState.update { it.copy(stays = stays, isLoading = false) }
                    pollBadges()
                }
        }
    }

    /** Cancels the real-time listener. Call when the screen leaves composition. */
    fun stopListeningStays() {
        staysListenerJob?.cancel()
        staysListenerJob = null
    }

    override fun onCleared() {
        super.onCleared()
        staysListenerJob?.cancel()
        walkInAvailabilityJob?.cancel()
    }

    fun loadAll() {
        val hotelId = AppContext.hotelId
        launchWithGlobalLoading {
            _listState.update { it.copy(isLoading = true, error = null) }
            runCatching { stayRepo.getAll(hotelId) }
                .onSuccess { stays ->
                    _listState.update { it.copy(stays = stays, isLoading = false) }
                }
                .onFailure { e ->
                    _listState.update { it.copy(isLoading = false, error = e.message ?: "Failed to load stays") }
                }
        }
    }

    fun loadCompleted() {
        val hotelId = AppContext.hotelId
        launchWithGlobalLoading {
            _listState.update { it.copy(isLoading = true, error = null) }
            runCatching { stayRepo.getCompleted(hotelId) }
                .onSuccess { stays ->
                    _listState.update { it.copy(stays = stays, isLoading = false) }
                }
                .onFailure { e ->
                    _listState.update { it.copy(isLoading = false, error = e.message ?: "Failed to load stays") }
                }
        }
    }

    fun onSearch(query: String) {
        _listState.update { it.copy(searchQuery = query) }
    }

    fun toggleShowCompleted(show: Boolean) {
        _listState.update { it.copy(showCompleted = show) }
        if (show) loadCompleted() else loadActive()
    }

    fun selectStay(stayId: String?) {
        _listState.update { it.copy(selectedStayId = stayId) }
        if (stayId != null) loadDetailForStay(stayId)
        else _detailState.value = StayDetailState()
    }

    fun pollBadges() {
        // Filter badges to only stays belonging to the current hotel (already in listState)
        val hotelStayIds = _listState.value.stays.map { it.id }.toSet()
        viewModelScope.launch {
            val pendingOrders = runCatching { orderRepo.getPending() }.getOrElse { emptyList() }
                .filter { it.stayId in hotelStayIds }
            val openComplaints = runCatching { complaintRepo.getOpen() }.getOrElse { emptyList() }
                .filter { it.stayId in hotelStayIds }
            val orderBadges = pendingOrders.groupingBy { it.stayId }.eachCount()
            val complaintBadges = openComplaints.groupingBy { it.stayId }.eachCount()
            _listState.update { it.copy(pendingOrdersByStay = orderBadges, openComplaintsByStay = complaintBadges) }
        }
    }

    // ── Detail ────────────────────────────────────────────────────────────────

    private fun loadDetailForStay(stayId: String) {
        launchWithGlobalLoading {
            _detailState.update { it.copy(isLoading = true) }
            val bill = runCatching { billRepo.getByStay(stayId) }.getOrNull()
            val orders = runCatching { orderRepo.getByStay(stayId) }.getOrElse { emptyList() }
            val complaints = runCatching { complaintRepo.getByStay(stayId) }.getOrElse { emptyList() }
            _detailState.value = StayDetailState(bill = bill, orders = orders, complaints = complaints, isLoading = false)
        }
    }

    fun refreshDetail() {
        _listState.value.selectedStayId?.let { loadDetailForStay(it) }
    }

    // ── Walk-in ───────────────────────────────────────────────────────────────

    fun openWalkIn() {
        val hotelId = AppContext.hotelId
        launchWithGlobalLoading {
            val categories = runCatching { roomRepo.getByHotel(hotelId) }.getOrElse { emptyList() }
            _walkInState.value = WalkInState(isOpen = true, categories = categories, checkInTime = localTodayUtcMidnight())
        }
        startWalkInAvailabilityListener()
    }

    fun closeWalkIn() {
        walkInAvailabilityJob?.cancel()
        walkInAvailabilityJob = null
        _walkInState.value = WalkInState()
    }

    fun openWalkInAsBooking() {
        val hotelId = AppContext.hotelId
        launchWithGlobalLoading {
            val categories = runCatching { roomRepo.getByHotel(hotelId) }.getOrElse { emptyList() }
            val sources = runCatching { FirestoreBookingSourceRepository.getAll() }.getOrElse { emptyList() }
            _walkInState.value = WalkInState(isOpen = true, isBookingMode = true, categories = categories, bookingSources = sources, checkInTime = localTodayUtcMidnight())
        }
        startWalkInAvailabilityListener()
    }

    private fun startWalkInAvailabilityListener() {
        walkInAvailabilityJob?.cancel()
        val hotelId = AppContext.hotelId
        walkInAvailabilityJob = viewModelScope.launch {
            merge(
                bookingRepo.listenByHotel(hotelId).map { },
                stayRepo.listenActive(hotelId).map { },
            ).collect {
                if (_walkInState.value.expectedCheckOut != null) computeAvailability()
            }
        }
    }

    fun onBookingRoomCountForCategory(categoryId: String, count: Int) = _walkInState.update { ws ->
        val clamped = count.coerceIn(0, 50)
        val newMap = if (clamped == 0) ws.bookingRoomCountsByCategory - categoryId
                     else ws.bookingRoomCountsByCategory + (categoryId to clamped)
        ws.copy(bookingRoomCountsByCategory = newMap)
    }

    fun onWalkInSourceSelected(id: String, name: String) =
        _walkInState.update { it.copy(source = name, selectedSourceId = id) }

    fun addWalkInBookingSource(name: String) {
        launchWithGlobalLoading {
            val id = runCatching { FirestoreBookingSourceRepository.add(name) }.getOrElse { return@launchWithGlobalLoading }
            val newSource = BookingSource(id = id, name = name)
            _walkInState.update { ws ->
                ws.copy(
                    bookingSources = (ws.bookingSources + newSource).sortedBy { it.name },
                    source = name,
                    selectedSourceId = id,
                )
            }
        }
    }

    fun onWalkInSource(value: String) = _walkInState.update { it.copy(source = value) }

    fun submitAsBooking() {
        val ws = _walkInState.value
        val primaryName = ws.guestEntries.firstOrNull()?.name?.trim()?.ifBlank { ws.guestName.trim() } ?: ws.guestName.trim()
        if (primaryName.isBlank()) { _walkInState.update { it.copy(error = "Primary guest name is required") }; return }
        val totalRooms = ws.bookingRoomCountsByCategory.values.sum() + ws.selectedInstanceIds.count { id ->
            val catId = ws.selectedInstanceDetails[id]?.categoryId ?: ""
            catId !in ws.bookingRoomCountsByCategory
        }
        if (totalRooms == 0) {
            _walkInState.update { it.copy(error = "Please select at least one room") }; return
        }
        if (ws.expectedCheckOut == null) { _walkInState.update { it.copy(error = "Please set expected check-out date") }; return }
        if (!ws.expectedCheckOut.after(ws.checkInTime)) {
            _walkInState.update { it.copy(error = "Check-out must be after check-in") }; return
        }
        if (ws.source.isBlank()) {
            _walkInState.update { it.copy(error = "Booking source is required") }; return
        }
        _walkInState.update { it.copy(isSaving = true, error = null) }
        launchWithGlobalLoading {
            runCatching {
                val checkIn = ws.checkInTime.toMidnightUtc()
                val checkOut = ws.expectedCheckOut.toMidnightUtc()
                val primary = ws.guestEntries.firstOrNull() ?: GuestEntry()
                val primaryPhone = primary.phone.trim().ifBlank { ws.guestPhone.trim() }
                val advance = ws.advancePayment.toDoubleOrNull() ?: 0.0
                val nights = java.time.temporal.ChronoUnit.DAYS.between(checkIn.toInstant(), checkOut.toInstant()).coerceAtLeast(1)
                val sourceName = ws.source.trim()
                val sourceId = ws.selectedSourceId

                // Gather all category IDs that have either a count or specific instances
                val allCatIds = (ws.bookingRoomCountsByCategory.keys +
                    ws.selectedInstanceDetails.values.map { it.categoryId }).distinct()

                val allRooms = allCatIds.sumOf { catId ->
                    (ws.bookingRoomCountsByCategory[catId] ?: 0)
                        .coerceAtLeast(ws.selectedInstanceIds.count { ws.selectedInstanceDetails[it]?.categoryId == catId })
                }
                val advancePerRoom = if (allRooms > 0) advance / allRooms else 0.0
                val groupId = if (allRooms > 1) UUID.randomUUID().toString() else ""

                for (catId in allCatIds) {
                    val cat = ws.categories.find { it.id == catId }
                    val catName = cat?.type ?: ws.selectedInstanceDetails.values.find { it.categoryId == catId }?.categoryName ?: catId
                    val pricePerNight = ws.categoryPrices[catId] ?: effectivePrice(cat ?: Room(), checkIn)
                    val breakfastCharge = if (ws.breakfast) (cat?.breakfastPrice ?: 0.0) * ws.adults * nights else 0.0
                    val total = pricePerNight * nights + breakfastCharge

                    // Specific instances for this category
                    val specificIds = ws.selectedInstanceIds.filter { ws.selectedInstanceDetails[it]?.categoryId == catId }
                    specificIds.forEach { instanceId ->
                        val inst = ws.selectedInstanceDetails[instanceId]
                        bookingRepo.add(Booking(
                            hotelId = AppContext.hotelId, hotelName = AppContext.hotelName,
                            guestName = primaryName, guestPhone = primaryPhone,
                            roomCategoryId = catId, roomCategoryName = catName,
                            roomInstanceId = instanceId, roomNumber = inst?.roomNumber ?: "",
                            checkIn = checkIn, checkOut = checkOut,
                            adults = ws.adults, children = ws.children,
                            status = "CONFIRMED", source = sourceName, sourceId = sourceId,
                            totalAmount = total, advancePaidAmount = advancePerRoom,
                            groupBookingId = groupId, createdAt = Date(),
                        ))
                    }

                    // Remaining unassigned rooms for this category (count minus specific rooms)
                    val reserved = ws.bookingRoomCountsByCategory[catId] ?: 0
                    val unassigned = (reserved - specificIds.size).coerceAtLeast(0)
                    repeat(unassigned) {
                        bookingRepo.add(Booking(
                            hotelId = AppContext.hotelId, hotelName = AppContext.hotelName,
                            guestName = primaryName, guestPhone = primaryPhone,
                            roomCategoryId = catId, roomCategoryName = catName,
                            roomInstanceId = "", roomNumber = "",
                            checkIn = checkIn, checkOut = checkOut,
                            adults = ws.adults, children = ws.children,
                            status = "CONFIRMED", source = sourceName, sourceId = sourceId,
                            totalAmount = total, advancePaidAmount = advancePerRoom,
                            groupBookingId = groupId, createdAt = Date(),
                        ))
                    }
                }
            }.onSuccess {
                _walkInState.value = WalkInState()
            }.onFailure { e ->
                _walkInState.update { it.copy(isSaving = false, error = e.message ?: "Failed to add booking") }
            }
        }
    }

    fun onWalkInAdults(count: Int) {
        val n = count.coerceAtLeast(1)
        _walkInState.update { ws ->
            // grow or shrink guestEntries to match adult count
            val current = ws.guestEntries
            val updated = when {
                n > current.size -> current + List(n - current.size) { GuestEntry() }
                n < current.size -> current.take(n)
                else -> current
            }
            ws.copy(adults = n, guestEntries = updated)
        }
        if (_walkInState.value.expectedCheckOut != null) computeAvailability()
    }
    fun onWalkInChildren(count: Int) = _walkInState.update { it.copy(children = count.coerceAtLeast(0)) }
    fun onWalkInCheckInTime(date: Date) {
        _walkInState.update { it.copy(checkInTime = date) }
        if (_walkInState.value.expectedCheckOut != null) computeAvailability()
    }

    fun onWalkInExpectedCheckOut(date: Date?) {
        _walkInState.update { it.copy(expectedCheckOut = date) }
        if (date != null) computeAvailability()
    }
    fun onWalkInBreakfast(v: Boolean) = _walkInState.update { it.copy(breakfast = v) }
    fun onWalkInAdvancePayment(v: String) = _walkInState.update { it.copy(advancePayment = v.filter { c -> c.isDigit() || c == '.' }) }

    fun onGuestName(index: Int, name: String) = _walkInState.update { ws ->
        val updated = ws.guestEntries.toMutableList().also { it[index] = it[index].copy(name = name) }
        ws.copy(
            guestEntries = updated,
            guestName = if (index == 0) name else ws.guestName,
        )
    }
    fun onGuestPhone(index: Int, phone: String) = _walkInState.update { ws ->
        val updated = ws.guestEntries.toMutableList().also { it[index] = it[index].copy(phone = phone) }
        ws.copy(
            guestEntries = updated,
            guestPhone = if (index == 0) phone else ws.guestPhone,
        )
    }
    fun onGuestIdProof(index: Int, verified: Boolean) = _walkInState.update { ws ->
        val updated = ws.guestEntries.toMutableList().also { it[index] = it[index].copy(idProofVerified = verified) }
        ws.copy(guestEntries = updated)
    }
    fun addGuest() = _walkInState.update { ws ->
        ws.copy(guestEntries = ws.guestEntries + GuestEntry(), adults = ws.adults + 1)
    }
    fun removeGuest(index: Int) = _walkInState.update { ws ->
        if (ws.guestEntries.size <= 1) return@update ws
        val updated = ws.guestEntries.toMutableList().also { it.removeAt(index) }
        ws.copy(guestEntries = updated, adults = updated.size)
    }

    fun onCategorySelected(categoryId: String) {
        val cat = _walkInState.value.categories.find { it.id == categoryId } ?: return
        _walkInState.update { ws -> ws.copy(
            selectedCategoryId = categoryId,
            selectedCategoryName = cat.type,
            selectedCategoryBreakfastPrice = cat.breakfastPrice,
            // Keep selections from other categories; clear only if re-selecting same category
            selectableInstances = emptyList(),
            cleaningInstances = emptyList(),
            dueOutInstances = emptyList(),
            availableInstances = emptyList(),
            // Show cached count while instances load; loadInstancesForCategory will replace with selectable.size
            availableCount = ws.categoryAvailability[categoryId] ?: 0,
        ) }
        if (_walkInState.value.expectedCheckOut != null) {
            loadInstancesForCategory(categoryId)
            // For group check-in: if the new category is not in requirements, show a note
            // (the deviation dialog fires later if/when the user actually selects a room)
        }
    }

    // ── Availability computation (Steps 1-6) ──────────────────────────────────

    private fun computeAvailability() {
        val ws = _walkInState.value
        val checkOut = ws.expectedCheckOut ?: return
        val checkIn = ws.checkInTime
        val hotelId = AppContext.hotelId
        val guests = ws.adults

        _walkInState.update { it.copy(isLoadingAvailability = true) }
        launchWithGlobalLoading {
            // Step 2 — active stays overlapping (expectedCheckOut > checkIn); treat overdue stays as ending now
            val now = Date()
            val activeStays = runCatching { stayRepo.getActive(hotelId) }.getOrElse { emptyList() }
            val staysPerCat = activeStays
                .filter { maxOf(it.expectedCheckOut, now).after(checkIn) }
                .groupingBy { it.roomCategoryId }.eachCount()

            // Step 1 — confirmed bookings overlapping [checkIn, checkOut), excluding those
            // already checked in (have an active stay) to avoid double-counting.
            val checkedInBookingIds = activeStays.map { it.bookingId }.filter { it.isNotBlank() }.toSet()
            val confirmedBookings = runCatching { bookingRepo.getConfirmedByHotel(hotelId) }.getOrElse { emptyList() }
            // Exclude the source booking(s) being checked in — they should not count as competing demand.
            val sourceBookingIds = buildSet<String> {
                ws.sourceBooking?.id?.let { add(it) }
                ws.groupBookings.forEach { add(it.id) }
            }
            val bookingsPerCat = confirmedBookings
                .filter { it.id !in checkedInBookingIds && it.id !in sourceBookingIds && it.checkIn.before(checkOut) && it.checkOut.after(checkIn) }
                .groupingBy { it.roomCategoryId }.eachCount()

            // Step 4 — usable physical rooms per category (not MAINTENANCE, not CLEANING)
            val allInstances = runCatching { instanceRepo.getAll() }.getOrElse { emptyList() }
                .filter { it.hotelId == hotelId && it.status !in setOf("MAINTENANCE", "CLEANING") }
            val usablePerCat = allInstances.groupingBy { it.categoryId }.eachCount()

            // Steps 5-6 — compute availability for all categories; always populate the map
            // so the UI can show "N avail." counts regardless of capacity/available filters.
            val availabilityMap = mutableMapOf<String, Int>()
            val pricesMap = mutableMapOf<String, Double>()
            ws.categories.forEach { cat ->
                val committed = (bookingsPerCat[cat.id] ?: 0) + (staysPerCat[cat.id] ?: 0)
                val usable = usablePerCat[cat.id] ?: 0
                availabilityMap[cat.id] = (usable - committed).coerceAtLeast(0)
                pricesMap[cat.id] = effectivePrice(cat, checkIn)
            }
            val availableCats = ws.categories.filter { cat ->
                val avail = availabilityMap[cat.id] ?: 0
                avail > 0 && cat.capacity >= guests && cat.available
            }

            _walkInState.update { s -> s.copy(
                availableCategories = availableCats,
                categoryAvailability = availabilityMap,
                categoryPrices = pricesMap,
                isLoadingAvailability = false,
                availableCount = availabilityMap[s.selectedCategoryId] ?: s.availableCount,
                cachedConfirmedBookings = confirmedBookings,
                cachedActiveStays = activeStays,
            ) }

            if (ws.selectedCategoryId.isNotBlank()) {
                loadInstancesForCategory(ws.selectedCategoryId)
            }
        }
    }

    private fun effectivePrice(cat: Room, checkIn: Date): Double {
        val cal = Calendar.getInstance().apply { time = checkIn }
        val mmdd = "%02d-%02d".format(cal.get(Calendar.MONTH) + 1, cal.get(Calendar.DAY_OF_MONTH))
        return cat.seasonalPricing.firstOrNull { s -> s.from.isNotBlank() && s.to.isNotBlank() && mmdd >= s.from && mmdd <= s.to }?.price
            ?: cat.pricePerNight
    }

    private fun loadInstancesForCategory(categoryId: String) {
        val ws = _walkInState.value
        val checkOut = ws.expectedCheckOut ?: return
        val checkIn = ws.checkInTime
        val hotelId = AppContext.hotelId

        launchWithGlobalLoading {
            // All instances including CLEANING (excludes MAINTENANCE)
            val allInstances = runCatching {
                instanceRepo.getByCategory(categoryId, hotelId, includeAssigned = true, includeCleaning = true)
            }.getOrElse { emptyList() }

            // Confirmed bookings with an assigned room that overlap the dates.
            // Exclude source booking(s) — they are being checked in now, not competing demand.
            val confirmedBookings = runCatching { bookingRepo.getConfirmedByHotel(hotelId) }.getOrElse { emptyList() }
            val sourceBookingIds = buildSet<String> {
                ws.sourceBooking?.id?.let { add(it) }
                ws.groupBookings.forEach { add(it.id) }
            }
            val bookedIds = confirmedBookings
                .filter { it.id !in sourceBookingIds && it.checkIn.before(checkOut) && it.checkOut.after(checkIn) && it.roomInstanceId.isNotBlank() }
                .map { it.roomInstanceId }.toSet()

            // Active stays occupying rooms in the date range; treat overdue stays as ending now
            val now = Date()
            val activeStays = runCatching { stayRepo.getActive(hotelId) }.getOrElse { emptyList() }
            val occupiedIds = activeStays
                .filter { it.checkInActual.before(checkOut) && maxOf(it.expectedCheckOut, now).after(checkIn) }
                .map { it.roomInstanceId }.toSet()

            // Rooms whose expected checkout is today but are still active (not yet checked out)
            val today = Calendar.getInstance()
            val dueOutIds = activeStays
                .filter { stay ->
                    val cal = Calendar.getInstance().apply { time = stay.expectedCheckOut }
                    cal.get(Calendar.YEAR) == today.get(Calendar.YEAR) &&
                    cal.get(Calendar.DAY_OF_YEAR) == today.get(Calendar.DAY_OF_YEAR)
                }
                .map { it.roomInstanceId }
                .filter { it.isNotBlank() }
                .toSet()

            val selectable = mutableListOf<RoomInstance>()
            val cleaning = mutableListOf<RoomInstance>()
            val dueOut = mutableListOf<RoomInstance>()
            for (inst in allInstances) {
                when {
                    inst.id in dueOutIds  -> dueOut.add(inst)   // expected checkout today — show marked
                    inst.id in occupiedIds -> continue           // occupied — hide
                    inst.id in bookedIds -> continue             // confirmed booking — hide
                    inst.status == "CLEANING" -> cleaning.add(inst)
                    else -> selectable.add(inst)
                }
            }
            val roomSort = compareBy<RoomInstance>({ it.roomNumber.toIntOrNull() ?: Int.MAX_VALUE }, { it.roomNumber })
            selectable.sortWith(roomSort)
            cleaning.sortWith(roomSort)
            dueOut.sortWith(roomSort)

            _walkInState.update { s ->
                // Guard: user may have switched categories while this fetch was in-flight; ignore stale results.
                if (s.selectedCategoryId != categoryId) return@update s
                val cap = s.categoryAvailability[categoryId] ?: selectable.size

                // Drop any selections in this category that are no longer selectable (e.g. after checkout date change)
                val newSelectableIds = selectable.map { it.id }.toSet()
                val categoryInstanceIds = allInstances.map { it.id }.toSet()
                val prunedIds = s.selectedInstanceIds.filter { id ->
                    id !in categoryInstanceIds || id in newSelectableIds
                }.toSet()
                val prunedDetails = s.selectedInstanceDetails.filter { (id, _) -> id in prunedIds }
                val prunedRoomGuestMap = s.roomGuestMap.filter { (id, _) -> id in prunedIds }

                // Prune selections that exceed the available cap due to new unassigned bookings.
                // These reduce category capacity without blocking any specific room instance,
                // so prunedIds alone won't remove them.
                val selectedInThisCat = prunedIds.filter { id -> id in categoryInstanceIds }
                val overflow = selectedInThisCat.size - cap
                val finalPrunedIds = if (overflow > 0) {
                    val toRemove = selectedInThisCat.takeLast(overflow).toSet()
                    prunedIds - toRemove
                } else prunedIds
                val finalPrunedDetails = prunedDetails.filter { (id, _) -> id in finalPrunedIds }
                val finalPrunedRoomGuestMap = prunedRoomGuestMap.filter { (id, _) -> id in finalPrunedIds }

                s.copy(
                    selectableInstances = selectable,
                    cleaningInstances = cleaning,
                    dueOutInstances = dueOut,
                    availableInstances = selectable,
                    availableCount = cap,
                    selectedInstanceIds = finalPrunedIds,
                    selectedInstanceDetails = finalPrunedDetails,
                    roomGuestMap = finalPrunedRoomGuestMap,
                )
            }
        }
    }

    fun onInstanceToggled(instanceId: String) {
        val ws = _walkInState.value
        val ids = ws.selectedInstanceIds
        val isRemoving = instanceId in ids

        // In single-booking check-in, detect deviation (extra rooms or different category)
        if (!ws.isGroupCheckIn && ws.sourceBooking != null && !isRemoving) {
            val inst = (ws.selectableInstances + ws.cleaningInstances + ws.selectedInstanceDetails.values).find { it.id == instanceId }
            val instCatId = inst?.categoryId ?: ws.selectedCategoryId
            val bookedCatId = ws.sourceBooking.roomCategoryId
            val categoryMismatch = instCatId != bookedCatId
            val extraRoom = ids.isNotEmpty()  // already have at least 1 room; adding another
            if (categoryMismatch || extraRoom) {
                val catIds = listOfNotNull(bookedCatId, instCatId).distinct()
                val availabilityMap = catIds.associateWith { catId ->
                    ws.categoryAvailability[catId] ?: if (catId == ws.selectedCategoryId) ws.availableCount else 0
                }
                val categoryNames = catIds.associateWith { catId ->
                    ws.categories.find { it.id == catId }?.type ?: catId
                }
                val bookedCatName = ws.sourceBooking.roomCategoryName.ifBlank {
                    ws.categories.find { it.id == bookedCatId }?.type ?: bookedCatId
                }
                _walkInState.update { it.copy(
                    groupChangeConfirmDialog = GroupChangeConfirmDialogState(
                        pendingInstanceId = instanceId,
                        pendingAction = "ADD",
                        deviatingCategoryId = instCatId,
                        availabilityMap = availabilityMap,
                        categoryNames = categoryNames,
                        originalRequirements = listOf(CategoryRequirement(bookedCatId, bookedCatName, 1)),
                    ),
                ) }
                return
            }
        }

        // In group check-in mode, detect deviation before applying the toggle
        if (ws.isGroupCheckIn) {
            val inst = (ws.selectableInstances + ws.cleaningInstances + ws.selectedInstanceDetails.values).find { it.id == instanceId }
            val instCatId = inst?.categoryId ?: ws.selectedCategoryId
            val requiredCount = ws.categoryRequirements.find { it.categoryId == instCatId }?.count ?: 0
            val currentCount = ids.count { id -> ws.selectedInstanceDetails[id]?.categoryId == instCatId }
            val isDeviation = when {
                isRemoving && currentCount <= requiredCount -> true  // would go below requirement
                !isRemoving && currentCount >= requiredCount -> true // would exceed requirement
                !isRemoving && requiredCount == 0 -> true           // category not in requirements
                else -> false
            }
            if (isDeviation) {
                val catIds = (ws.categoryRequirements.map { it.categoryId } + instCatId).distinct()
                val availabilityMap = catIds.associateWith { catId ->
                    ws.categoryAvailability[catId]
                        // For the currently-viewed category (e.g. Suite in group mode), categoryAvailability
                        // may be null because only booking-required categories are pre-populated.
                        // Use availableCount which loadInstancesForCategory already resolved correctly.
                        ?: if (catId == ws.selectedCategoryId) ws.availableCount else 0
                }
                // Build display names: requirement names first, then fall back to categories list
                val categoryNames = catIds.associateWith { catId ->
                    ws.categoryRequirements.find { it.categoryId == catId }?.categoryName
                        ?: ws.categories.find { it.id == catId }?.type
                        ?: catId
                }
                _walkInState.update { it.copy(
                    groupChangeConfirmDialog = GroupChangeConfirmDialogState(
                        pendingInstanceId = instanceId,
                        pendingAction = if (isRemoving) "REMOVE" else "ADD",
                        deviatingCategoryId = instCatId,
                        availabilityMap = availabilityMap,
                        categoryNames = categoryNames,
                    ),
                ) }
                return
            }
        }

        _walkInState.update { s ->
            val current = s.selectedInstanceIds
            if (instanceId in current) {
                s.copy(
                    selectedInstanceIds = current - instanceId,
                    selectedInstanceDetails = s.selectedInstanceDetails - instanceId,
                    roomGuestMap = s.roomGuestMap - instanceId,
                    instanceToBookingId = s.instanceToBookingId - instanceId,
                )
            } else {
                val currentCatIds = (s.selectableInstances + s.cleaningInstances).map { it.id }.toSet()
                val alreadySelectedInCat = current.count { it in currentCatIds }
                // Fall back to availableCount (= selectableInstances.size) if categoryAvailability
                // isn't populated yet for this category (e.g., instances still loading)
                val maxForCat = s.categoryAvailability[s.selectedCategoryId] ?: s.availableCount
                if (alreadySelectedInCat >= maxForCat) {
                    s.copy(error = "Maximum ${maxForCat} room${if (maxForCat != 1) "s" else ""} available in this category")
                } else {
                    val inst = (s.selectableInstances + s.cleaningInstances).find { it.id == instanceId }
                    val newDetails = if (inst != null) s.selectedInstanceDetails + (instanceId to inst) else s.selectedInstanceDetails
                    val defaultGuest = s.guestEntries.firstOrNull() ?: GuestEntry()
                    val newGuestMap = s.roomGuestMap + (instanceId to (s.roomGuestMap[instanceId] ?: defaultGuest))
                    // Auto-assign bookingId for newly added room in group mode (best-effort by category)
                    val newInstanceToBookingId = if (s.isGroupCheckIn) {
                        val instCatId = inst?.categoryId ?: s.selectedCategoryId
                        val alreadyMapped = s.instanceToBookingId.values.toSet()
                        val matchingBooking = s.groupBookings.find { b ->
                            b.roomCategoryId == instCatId && b.id !in alreadyMapped
                        }
                        if (matchingBooking != null) s.instanceToBookingId + (instanceId to matchingBooking.id)
                        else s.instanceToBookingId
                    } else s.instanceToBookingId
                    s.copy(
                        selectedInstanceIds = current + instanceId,
                        selectedInstanceDetails = newDetails,
                        roomGuestMap = newGuestMap,
                        instanceToBookingId = newInstanceToBookingId,
                    )
                }
            }
        }
    }

    fun onGroupDeviationConfirmed() {
        val dialog = _walkInState.value.groupChangeConfirmDialog ?: return
        _walkInState.update { it.copy(groupChangeConfirmDialog = null) }
        // Apply the pending toggle without deviation check (already confirmed)
        val instanceId = dialog.pendingInstanceId
        val isRemoving = dialog.pendingAction == "REMOVE"
        _walkInState.update { s ->
            if (isRemoving) {
                s.copy(
                    selectedInstanceIds = s.selectedInstanceIds - instanceId,
                    selectedInstanceDetails = s.selectedInstanceDetails - instanceId,
                    roomGuestMap = s.roomGuestMap - instanceId,
                    instanceToBookingId = s.instanceToBookingId - instanceId,
                )
            } else {
                val inst = (s.selectableInstances + s.cleaningInstances).find { it.id == instanceId }
                val newDetails = if (inst != null) s.selectedInstanceDetails + (instanceId to inst) else s.selectedInstanceDetails
                val defaultGuest = s.guestEntries.firstOrNull() ?: GuestEntry()
                val newGuestMap = s.roomGuestMap + (instanceId to (s.roomGuestMap[instanceId] ?: defaultGuest))
                s.copy(
                    selectedInstanceIds = s.selectedInstanceIds + instanceId,
                    selectedInstanceDetails = newDetails,
                    roomGuestMap = newGuestMap,
                )
            }
        }
    }

    fun dismissGroupDeviation() = _walkInState.update { it.copy(groupChangeConfirmDialog = null) }

    fun onToggleSameGuestForAll(same: Boolean) = _walkInState.update { ws ->
        val newMap = if (!same) {
            val default = ws.guestEntries.firstOrNull() ?: GuestEntry()
            ws.selectedInstanceIds.associateWith { id -> ws.roomGuestMap[id] ?: default }
        } else ws.roomGuestMap
        ws.copy(sameGuestForAllRooms = same, roomGuestMap = newMap)
    }

    fun onRoomGuestName(instanceId: String, name: String) = _walkInState.update { ws ->
        val updated = (ws.roomGuestMap[instanceId] ?: GuestEntry()).copy(name = name)
        ws.copy(roomGuestMap = ws.roomGuestMap + (instanceId to updated))
    }
    fun onRoomGuestPhone(instanceId: String, phone: String) = _walkInState.update { ws ->
        val updated = (ws.roomGuestMap[instanceId] ?: GuestEntry()).copy(phone = phone)
        ws.copy(roomGuestMap = ws.roomGuestMap + (instanceId to updated))
    }
    fun onRoomGuestIdProof(instanceId: String, verified: Boolean) = _walkInState.update { ws ->
        val updated = (ws.roomGuestMap[instanceId] ?: GuestEntry()).copy(idProofVerified = verified)
        ws.copy(roomGuestMap = ws.roomGuestMap + (instanceId to updated))
    }

    // ── Check-in mismatch confirmation ────────────────────────────────────────

    fun requestSubmitWalkIn() {
        val ws = _walkInState.value
        val selectionsByCat = ws.selectedInstanceDetails.values.groupBy { it.categoryId }

        // ── Group check-in: compare against categoryRequirements ─────────────
        if (ws.isGroupCheckIn && ws.categoryRequirements.isNotEmpty()) {
            val reqCatIds = ws.categoryRequirements.map { it.categoryId }.toSet()
            val hasUnmet = ws.categoryRequirements.any { req ->
                (selectionsByCat[req.categoryId]?.size ?: 0) != req.count
            }
            val hasExtra = selectionsByCat.any { (catId, _) -> catId !in reqCatIds }
            if (hasUnmet || hasExtra) {
                val originalLines = ws.categoryRequirements.map { CheckInMismatchLine(it.categoryName, it.count) }
                val currentLines = selectionsByCat.map { (catId, insts) ->
                    val name = insts.firstOrNull()?.categoryName?.ifBlank { null }
                        ?: ws.categories.find { it.id == catId }?.type
                        ?: catId
                    CheckInMismatchLine(name, insts.size)
                }
                val linkedBookingIds = ws.instanceToBookingId.values.toSet()
                val unmetBookingCount = ws.groupBookings.count { it.id !in linkedBookingIds }
                _walkInState.update { it.copy(
                    checkInMismatchConfirm = CheckInMismatchConfirmState(
                        originalLines = originalLines,
                        currentLines = currentLines,
                        unmetBookingCount = unmetBookingCount,
                    ),
                ) }
                return
            }
        }

        // ── Single-booking check-in: compare against sourceBooking ───────────
        val srcBooking = ws.sourceBooking
        if (srcBooking != null && !ws.isGroupCheckIn && ws.selectedInstanceDetails.isNotEmpty()) {
            val bookedCatId = srcBooking.roomCategoryId
            val categoryMismatch = ws.selectedInstanceDetails.values.any { it.categoryId != bookedCatId }
            val extraRooms = ws.selectedInstanceIds.size > 1
            if (categoryMismatch || extraRooms) {
                val bookedCatName = srcBooking.roomCategoryName.ifBlank {
                    ws.categories.find { it.id == bookedCatId }?.type ?: bookedCatId
                }
                val originalLines = listOf(CheckInMismatchLine(bookedCatName, 1))
                val currentLines = selectionsByCat.map { (catId, insts) ->
                    val name = insts.firstOrNull()?.categoryName?.ifBlank { null }
                        ?: ws.categories.find { it.id == catId }?.type
                        ?: catId
                    CheckInMismatchLine(name, insts.size)
                }
                _walkInState.update { it.copy(
                    checkInMismatchConfirm = CheckInMismatchConfirmState(
                        originalLines = originalLines,
                        currentLines = currentLines,
                        unmetBookingCount = 1,  // source booking won't be fulfilled in its original category
                    ),
                ) }
                return
            }
        }

        submitWalkIn()
    }

    fun confirmCheckInMismatch(cancelUnmet: Boolean) {
        _walkInState.update { it.copy(checkInMismatchConfirm = null) }
        submitWalkIn(cancelUnmetBookings = cancelUnmet)
    }

    fun dismissCheckInMismatch() = _walkInState.update { it.copy(checkInMismatchConfirm = null) }

    fun submitWalkIn(cancelUnmetBookings: Boolean = false) {
        val ws = _walkInState.value
        val primaryName = ws.guestEntries.firstOrNull()?.name?.trim()?.ifBlank { ws.guestName.trim() } ?: ws.guestName.trim()
        if (primaryName.isBlank()) { _walkInState.update { it.copy(error = "Primary guest name is required") }; return }
        if (ws.selectedInstanceIds.isEmpty()) { _walkInState.update { it.copy(error = "Please select at least one room") }; return }
        if (ws.expectedCheckOut == null) { _walkInState.update { it.copy(error = "Please set expected check-out date") }; return }
        if (!ws.expectedCheckOut.after(ws.checkInTime)) {
            _walkInState.update { it.copy(error = "Check-out must be after check-in") }
            return
        }

        _walkInState.update { it.copy(isSaving = true, error = null) }
        launchWithGlobalLoading {
            runCatching {
                // ── Pre-flight availability re-check ──────────────────────────────
                // Use data already cached by the last computeAvailability() run (triggered
                // by the real-time listener) — zero extra Firestore reads.
                val hotelId = AppContext.hotelId
                val checkInNorm  = ws.checkInTime.toMidnightUtc()
                val checkOutNorm = ws.expectedCheckOut!!.toMidnightUtc()
                val nowPreflight = Date()
                val freshBookings = ws.cachedConfirmedBookings
                val freshStays    = ws.cachedActiveStays
                val sourceIds = buildSet<String> {
                    ws.sourceBooking?.id?.let { add(it) }
                    ws.groupBookings.forEach { add(it.id) }
                }
                val freshBookedRoomIds = freshBookings
                    .filter { it.id !in sourceIds && it.roomInstanceId.isNotBlank()
                        && it.checkIn.before(checkOutNorm) && it.checkOut.after(checkInNorm) }
                    .map { it.roomInstanceId }.toSet()
                val freshOccupiedRoomIds = freshStays
                    .filter { it.checkInActual.before(checkOutNorm)
                        && maxOf(it.expectedCheckOut, nowPreflight).after(checkInNorm) }
                    .map { it.roomInstanceId }.toSet()
                val conflicted = ws.selectedInstanceIds.filter { id ->
                    id in freshBookedRoomIds || id in freshOccupiedRoomIds
                }
                if (conflicted.isNotEmpty()) {
                    val nums = conflicted.mapNotNull { ws.selectedInstanceDetails[it]?.roomNumber }.joinToString(", ")
                    // Refresh availability so the UI reflects the new state
                    computeAvailability()
                    throw Exception("Room${if (conflicted.size > 1) "s" else ""} $nums ${if (conflicted.size > 1) "are" else "is"} no longer available — a new booking arrived while you were checking in. Go back to Step 1 and reselect.")
                }

                // ── Category-level pre-flight ─────────────────────────────────────
                // Instance-level check above only catches bookings with an explicit roomInstanceId.
                // Unassigned bookings still consume category capacity — check that here with fresh reads.
                val selectedByCat = ws.selectedInstanceDetails.values.groupBy { it.categoryId }
                if (selectedByCat.isNotEmpty()) {
                    val freshCatBookings = runCatching {
                        bookingRepo.getConfirmedByHotel(hotelId)
                    }.getOrElse { freshBookings }
                    val freshCatStays = runCatching {
                        stayRepo.getActive(hotelId)
                    }.getOrElse { freshStays }
                    val freshAllInstances = runCatching {
                        instanceRepo.getAll()
                    }.getOrElse { emptyList() }
                    val checkedInBkIds = freshCatStays.map { it.bookingId }.filter { it.isNotBlank() }.toSet()
                    for ((catId, selectedInsts) in selectedByCat) {
                        val usable = freshAllInstances.count {
                            it.hotelId == hotelId && it.categoryId == catId &&
                            it.status !in setOf("MAINTENANCE", "CLEANING")
                        }
                        val committedBookings = freshCatBookings.count { b ->
                            b.id !in sourceIds && b.id !in checkedInBkIds &&
                            b.roomCategoryId == catId &&
                            b.checkIn.before(checkOutNorm) && b.checkOut.after(checkInNorm)
                        }
                        val committedStays = freshCatStays.count { s ->
                            s.roomCategoryId == catId &&
                            s.checkInActual.before(checkOutNorm) &&
                            maxOf(s.expectedCheckOut, nowPreflight).after(checkInNorm)
                        }
                        val available = usable - committedBookings - committedStays
                        if (selectedInsts.size > available) {
                            computeAvailability()
                            val catName = selectedInsts.first().categoryName.ifBlank { catId }
                            throw Exception(
                                "Not enough $catName rooms available — availability changed while " +
                                "you were checking in. Go back to Step 1 and reselect."
                            )
                        }
                    }
                }
                // ── End pre-flight ────────────────────────────────────────────────

                // Normalize dates to midnight UTC — stored as date-only, not wall-clock time
                val now = checkInNorm
                val primary = ws.guestEntries.firstOrNull() ?: GuestEntry()
                val primaryName = primary.name.trim().ifBlank { ws.guestName.trim() }
                val primaryPhone = primary.phone.trim().ifBlank { ws.guestPhone.trim() }
                val hotel = DreamlandAppInitializer.getSettingsViewModel().state.value.selectedHotel
                val isEarlyCheckIn = if (hotel != null && hotel.earlyCheckInAllowed) {
                    val parts = hotel.checkInTime.split(":")
                    val hotelHour = parts.getOrNull(0)?.toIntOrNull() ?: 12
                    val hotelMin = parts.getOrNull(1)?.toIntOrNull() ?: 0
                    val nowCal = Calendar.getInstance()
                    val nowMinutes = nowCal.get(Calendar.HOUR_OF_DAY) * 60 + nowCal.get(Calendar.MINUTE)
                    nowMinutes < hotelHour * 60 + hotelMin
                } else false
                val earlyCheckInCharge = if (isEarlyCheckIn && hotel != null) hotel.earlyCheckInPrice else 0.0
                val advance = ws.advancePayment.toDoubleOrNull() ?: 0.0
                val roomCount = ws.selectedInstanceIds.size
                // Split advance equally across all rooms
                val advancePerRoom = if (roomCount > 0) advance / roomCount else 0.0
                // Shared ID linking all rooms in a multi-room walk-in; empty for single-room stays
                val walkInGroupId = if (roomCount > 1) UUID.randomUUID().toString() else ""

                val firstStayId = ws.selectedInstanceIds.mapIndexed { roomIndex, instanceId ->
                    // Risk 4: always derive category from Firestore doc
                    val roomInstance = instanceRepo.getById(instanceId)
                        ?: throw Exception("Room instance $instanceId not found")
                    val cat = ws.categories.find { it.id == roomInstance.categoryId }
                    val checkOutNormalized = checkOutNorm
                    val nights = ChronoUnit.DAYS.between(now.toInstant(), checkOutNormalized.toInstant()).coerceAtLeast(1)
                    // Use effective (seasonal) price if available
                    val pricePerNight = ws.categoryPrices[roomInstance.categoryId]
                        ?: effectivePrice(cat ?: Room(), now)
                    val roomCharge = pricePerNight * nights
                    val breakfastCharge = if (ws.breakfast) ws.selectedCategoryBreakfastPrice * ws.adults * nights else 0.0
                    // Early check-in charge only on first room
                    val earlyCharge = if (roomIndex == 0) earlyCheckInCharge else 0.0
                    val total = roomCharge + breakfastCharge + earlyCharge

                    val roomPrimary = if (!ws.sameGuestForAllRooms)
                        ws.roomGuestMap[instanceId] ?: ws.guestEntries.firstOrNull() ?: GuestEntry()
                    else
                        ws.guestEntries.firstOrNull() ?: GuestEntry()
                    val roomGuestName = roomPrimary.name.trim().ifBlank { primaryName }
                    val roomGuestPhone = roomPrimary.phone.trim().ifBlank { primaryPhone }
                    val guestRecords = if (!ws.sameGuestForAllRooms) {
                        listOf(GuestRecord(name = roomGuestName, phone = roomGuestPhone, idProofVerified = roomPrimary.idProofVerified)) +
                            ws.guestEntries.drop(1).map { e -> GuestRecord(name = e.name.trim(), phone = e.phone.trim(), idProofVerified = e.idProofVerified) }
                    } else {
                        ws.guestEntries.mapIndexed { i, entry ->
                            GuestRecord(
                                name = if (i == 0) entry.name.trim().ifBlank { primaryName } else entry.name.trim(),
                                phone = if (i == 0) entry.phone.trim().ifBlank { primaryPhone } else entry.phone.trim(),
                                idProofVerified = entry.idProofVerified,
                            )
                        }
                    }
                    val stay = Stay(
                        hotelId = AppContext.hotelId,
                        bookingId = when {
                            ws.isGroupCheckIn -> ws.instanceToBookingId[instanceId] ?: ""
                            roomIndex == 0 -> ws.sourceBooking?.id ?: ""
                            else -> ""
                        },
                        guestName = roomGuestName,
                        guestPhone = roomGuestPhone,
                        roomInstanceId = instanceId,
                        roomNumber = roomInstance.roomNumber,
                        roomCategoryId = roomInstance.categoryId,
                        roomCategoryName = roomInstance.categoryName,
                        checkInActual = now,
                        expectedCheckOut = checkOutNormalized,
                        status = "ACTIVE",
                        adults = ws.adults,
                        children = ws.children,
                        breakfast = ws.breakfast,
                        earlyCheckIn = isEarlyCheckIn && roomIndex == 0,
                        earlyCheckInCharge = earlyCharge,
                        advanceAmount = advancePerRoom,
                        createdAt = now,
                        guests = guestRecords,
                        groupStayId = walkInGroupId,
                    )
                    val stayId = stayRepo.checkInBatch(stay, instanceId)

                    if (ws.isGroupCheckIn) {
                        // Link each room to its original group booking (by instanceToBookingId map)
                        val origBookingId = ws.instanceToBookingId[instanceId]
                        if (!origBookingId.isNullOrBlank()) {
                            val origBooking = ws.groupBookings.find { it.id == origBookingId }
                            if (origBooking != null) {
                                bookingRepo.update(origBooking.copy(
                                    roomInstanceId = instanceId,
                                    roomNumber = roomInstance.roomNumber,
                                    status = "COMPLETED",
                                ))
                            }
                        }
                    } else if (roomIndex == 0) {
                        val srcBooking = ws.sourceBooking
                        if (srcBooking != null) {
                            bookingRepo.update(srcBooking.copy(
                                roomInstanceId = instanceId,
                                roomNumber = roomInstance.roomNumber,
                                status = "COMPLETED",
                            ))
                        }
                    }
                    stayId
                }.first()
                firstStayId
            }.onSuccess { newStayId ->
                // Cancel bookings that weren't linked to any selected room (user chose this option)
                if (cancelUnmetBookings && ws.isGroupCheckIn) {
                    val linkedIds = ws.instanceToBookingId.values.toSet()
                    ws.groupBookings.filter { it.id !in linkedIds }.forEach { b ->
                        runCatching { bookingRepo.update(b.copy(status = "CANCELLED")) }
                    }
                }
                if (cancelUnmetBookings && !ws.isGroupCheckIn) {
                    ws.sourceBooking?.let { src ->
                        runCatching { bookingRepo.update(src.copy(status = "CANCELLED")) }
                    }
                }
                _walkInState.value = WalkInState()
                loadActive()
                _listState.update { it.copy(selectedStayId = newStayId) }
                loadDetailForStay(newStayId)
            }.onFailure { e ->
                _walkInState.update { it.copy(isSaving = false, error = e.message ?: "Check-in failed") }
            }
        }
    }

    // ── From booking ──────────────────────────────────────────────────────────

    fun openFromBooking() {
        val hotelId = AppContext.hotelId
        _fromBookingState.value = FromBookingState(isOpen = true, isLoading = true)
        launchWithGlobalLoading {
            val bookings = runCatching { bookingRepo.getUpcoming(hotelId) }.getOrElse { emptyList() }
            _fromBookingState.update { it.copy(bookings = bookings, isLoading = false) }
        }
    }

    fun closeFromBooking() {
        _fromBookingState.value = FromBookingState()
    }

    fun prefillGroupBooking(group: List<Booking>) {
        if (group.isEmpty()) return
        _fromBookingState.value = FromBookingState()
        launchWithGlobalLoading {
            val hotelId = AppContext.hotelId
            val categories = runCatching { roomRepo.getByHotel(hotelId) }.getOrElse { emptyList() }
            val primaryBooking = group[0]
            val today = localTodayUtcMidnight()
            val allCategoryIds = group.map { it.roomCategoryId }.distinct()
            val confirmed = runCatching { bookingRepo.getConfirmedByHotel(hotelId) }.getOrElse { emptyList() }
            val activeStays = runCatching { stayRepo.getActive(hotelId) }.getOrElse { emptyList() }
            // Exclude sibling bookings so they don't appear as conflicts
            val confirmedExSiblings = confirmed.filter { b ->
                primaryBooking.groupBookingId.isBlank() || b.groupBookingId != primaryBooking.groupBookingId
            }
            val roomSort = compareBy<RoomInstance>({ it.roomNumber.toIntOrNull() ?: Int.MAX_VALUE }, { it.roomNumber })

            // Load selectable / cleaning instances for every category in the group
            data class CatInstances(val selectable: List<RoomInstance>, val cleaning: List<RoomInstance>)
            val instsByCat = mutableMapOf<String, CatInstances>()
            for (catId in allCategoryIds) {
                val all = runCatching {
                    instanceRepo.getByCategory(catId, hotelId, includeAssigned = true, includeCleaning = true)
                }.getOrElse { emptyList() }
                val noConflict = dateConflictFilter(all, confirmedExSiblings, today, primaryBooking.checkOut.toLocalDayUtcMidnight())
                val candidates = noConflict.filter { inst ->
                    activeStays.none { s ->
                        s.roomInstanceId == inst.id &&
                            s.checkInActual.before(primaryBooking.checkOut) &&
                            s.expectedCheckOut.after(today)
                    }
                }
                instsByCat[catId] = CatInstances(
                    selectable = candidates.filter { it.status != "CLEANING" }.sortedWith(roomSort),
                    cleaning   = candidates.filter { it.status == "CLEANING" }.sortedWith(roomSort),
                )
            }

            // Build category requirements
            val requirements = group.groupBy { it.roomCategoryId }.map { (catId, bookings) ->
                val catName = categories.find { it.id == catId }?.type ?: bookings[0].roomCategoryName
                CategoryRequirement(catId, catName, bookings.size)
            }

            // Pre-select already-assigned rooms
            val preSelected = mutableSetOf<String>()
            val instanceDetails = mutableMapOf<String, RoomInstance>()
            val instanceToBooking = mutableMapOf<String, String>()
            val primaryGuest = GuestEntry(name = primaryBooking.guestName, phone = primaryBooking.guestPhone)
            for (booking in group) {
                if (booking.roomInstanceId.isNotBlank()) {
                    val inst = instsByCat[booking.roomCategoryId]?.selectable?.find { it.id == booking.roomInstanceId }
                    if (inst != null) {
                        preSelected.add(inst.id)
                        instanceDetails[inst.id] = inst
                        instanceToBooking[inst.id] = booking.id
                    }
                }
            }
            val roomGuestMap = preSelected.associateWith { primaryGuest }
            val categoryAvailability = instsByCat.mapValues { (_, v) -> v.selectable.size }

            val primaryCatId = primaryBooking.roomCategoryId
            val primaryInsts = instsByCat[primaryCatId]
            val entries = List(primaryBooking.adults.coerceAtLeast(1)) { i ->
                if (i == 0) primaryGuest else GuestEntry()
            }

            _walkInState.value = WalkInState(
                isOpen = true,
                isGroupCheckIn = true,
                groupBookings = group,
                categoryRequirements = requirements,
                instanceToBookingId = instanceToBooking,
                guestName = primaryBooking.guestName,
                guestPhone = primaryBooking.guestPhone,
                guestEntries = entries,
                selectedInstanceIds = preSelected,
                selectedInstanceDetails = instanceDetails,
                roomGuestMap = roomGuestMap,
                selectedCategoryId = primaryCatId,
                selectedCategoryName = primaryBooking.roomCategoryName,
                selectedCategoryBreakfastPrice = categories.find { it.id == primaryCatId }?.breakfastPrice ?: 0.0,
                expectedCheckOut = primaryBooking.checkOut.toLocalDayUtcMidnight(),
                checkInTime = today,
                adults = primaryBooking.adults.coerceAtLeast(1),
                children = primaryBooking.children,
                categories = categories,
                selectableInstances = primaryInsts?.selectable ?: emptyList(),
                cleaningInstances = primaryInsts?.cleaning ?: emptyList(),
                availableInstances = primaryInsts?.selectable ?: emptyList(),
                availableCount = categoryAvailability[primaryCatId] ?: primaryInsts?.selectable?.size ?: 0,
                categoryAvailability = categoryAvailability,
            )
            // Populate full category availability map for all categories
            computeAvailability()
        }
    }

    fun prefillFromBooking(booking: Booking) {
        val hotelId = AppContext.hotelId
        _fromBookingState.value = FromBookingState()
        launchWithGlobalLoading {
            val today = localTodayUtcMidnight()
            val categories = runCatching { roomRepo.getByHotel(hotelId) }.getOrElse { emptyList() }
            // Include CLEANING rooms so they appear grayed-out (same as walk-in dialog)
            val allInstances = if (booking.roomCategoryId.isNotBlank()) {
                runCatching {
                    instanceRepo.getByCategory(
                        booking.roomCategoryId, hotelId,
                        includeAssigned = true, includeCleaning = true,
                    )
                }.getOrElse { emptyList() }
            } else emptyList()
            val confirmed = runCatching { bookingRepo.getConfirmedByHotel(hotelId) }.getOrElse { emptyList() }
            val noConflict = dateConflictFilter(allInstances, confirmed, today, booking.checkOut.toLocalDayUtcMidnight(), excludeBookingId = booking.id)
            // Exclude rooms occupied by active stays overlapping today → checkout window
            val activeStays = runCatching { stayRepo.getActive(hotelId) }.getOrElse { emptyList() }
            val candidates = noConflict.filter { inst ->
                activeStays.none { stay ->
                    stay.roomInstanceId == inst.id &&
                        stay.checkInActual.before(booking.checkOut) &&
                        stay.expectedCheckOut.after(today)
                }
            }
            // Split into selectable (clickable) and cleaning (shown grayed, unclickable)
            val roomSort = compareBy<RoomInstance>({ it.roomNumber.toIntOrNull() ?: Int.MAX_VALUE }, { it.roomNumber })
            val selectable = candidates.filter { it.status != "CLEANING" }.sortedWith(roomSort)
            val cleaning   = candidates.filter { it.status == "CLEANING" }.sortedWith(roomSort)

            val cat = categories.find { it.id == booking.roomCategoryId }
            val entries = List(booking.adults.coerceAtLeast(1)) { i ->
                if (i == 0) GuestEntry(name = booking.guestName, phone = booking.guestPhone)
                else GuestEntry()
            }
            // Pre-select the assigned room if the booking already has one
            val preAssignedInstance = selectable.find { it.id == booking.roomInstanceId }
            val primaryGuest = entries.firstOrNull() ?: GuestEntry()
            _walkInState.value = WalkInState(
                isOpen = true,
                guestName = booking.guestName,
                guestPhone = booking.guestPhone,
                guestEntries = entries,
                selectedCategoryId = booking.roomCategoryId,
                selectedCategoryName = booking.roomCategoryName,
                selectedCategoryBreakfastPrice = cat?.breakfastPrice ?: 0.0,
                selectedInstanceIds = if (preAssignedInstance != null) setOf(preAssignedInstance.id) else emptySet(),
                selectedInstanceDetails = if (preAssignedInstance != null) mapOf(preAssignedInstance.id to preAssignedInstance) else emptyMap(),
                roomGuestMap = if (preAssignedInstance != null) mapOf(preAssignedInstance.id to primaryGuest) else emptyMap(),
                checkInTime = today,
                expectedCheckOut = booking.checkOut.toLocalDayUtcMidnight(),
                adults = booking.adults,
                children = booking.children,
                categories = categories,
                selectableInstances = selectable,
                cleaningInstances   = cleaning,
                availableInstances  = selectable,
                categoryAvailability = if (booking.roomCategoryId.isNotBlank())
                    mapOf(booking.roomCategoryId to selectable.size)
                else emptyMap(),
                availableCount = selectable.size,
                sourceBooking = booking,
            )
            // Populate full category availability map for all categories
            computeAvailability()
        }
    }

    // ── Check-out ─────────────────────────────────────────────────────────────

    fun openCheckOut(stayId: String) {
        val stay = _listState.value.stays.find { it.id == stayId } ?: return
        launchWithGlobalLoading {
            val hotelId = AppContext.hotelId
            val orders = runCatching { orderRepo.getByStay(stayId) }.getOrElse { emptyList() }
            val ordersTotal = orders.sumOf { it.totalAmount }
            val pendingOrders = orders.filter { it.status != "COMPLETED" }
            val hotel = DreamlandAppInitializer.getSettingsViewModel().state.value.selectedHotel

            // Room price from rooms collection (source of truth)
            val room = runCatching { roomRepo.getById(stay.roomCategoryId) }.getOrNull()
            val roomPricePerNight = room?.pricePerNight ?: 0.0

            // Calculate room charges based on actual checkout date (today)
            val now = Date()
            val actualNights = ChronoUnit.DAYS.between(stay.checkInActual.toInstant(), now.toInstant()).coerceAtLeast(1)
            val roomCharges = roomPricePerNight * actualNights
            val breakfastCharge = if (stay.breakfast) {
                (room?.breakfastPrice ?: 0.0) * stay.adults * actualNights
            } else 0.0

            val isLateCheckout = if (hotel != null) {
                val parts = hotel.checkOutTime.split(":")
                val hotelHour = parts.getOrNull(0)?.toIntOrNull() ?: 11
                val hotelMin = parts.getOrNull(1)?.toIntOrNull() ?: 0
                val nowCal = Calendar.getInstance()
                val expectedDateOnly = Calendar.getInstance().apply {
                    time = stay.expectedCheckOut
                    set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
                }
                val todayDateOnly = Calendar.getInstance().apply {
                    set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
                }
                val nowMinutes = nowCal.get(Calendar.HOUR_OF_DAY) * 60 + nowCal.get(Calendar.MINUTE)
                todayDateOnly.after(expectedDateOnly) ||
                    (todayDateOnly.timeInMillis == expectedDateOnly.timeInMillis && nowMinutes > hotelHour * 60 + hotelMin)
            } else false
            val flatFee = if (isLateCheckout && hotel?.lateCheckOutAllowed == true) hotel.lateCheckOutPrice else 0.0

            // Build an in-memory bill for display in the checkout dialog (not persisted)
            val displayBill = BillingInvoice(
                stayId = stayId,
                guestName = stay.guestName,
                roomNumber = stay.roomNumber,
                roomCharges = roomCharges,
                serviceCharges = breakfastCharge,
                earlyCheckInCharge = stay.earlyCheckInCharge,
                totalAmount = roomCharges + breakfastCharge + stay.earlyCheckInCharge,
                amountPaid = stay.advanceAmount,
            )
            // ── Group checkout detection ──────────────────────────────────
            var groupStays = emptyList<Stay>()
            var groupBills = emptyMap<String, BillingInvoice>()
            if (stay.bookingId.isNotBlank()) {
                val booking = runCatching { bookingRepo.getById(stay.bookingId) }.getOrNull()
                val groupId = booking?.groupBookingId ?: ""
                if (groupId.isNotBlank()) {
                    val allActiveStays = runCatching { stayRepo.getActive(hotelId) }.getOrElse { emptyList() }
                    val allGroupBookings = runCatching { bookingRepo.getAllByHotel(hotelId) }.getOrElse { emptyList() }
                        .filter { it.groupBookingId == groupId }
                    val groupBookingIds = allGroupBookings.map { it.id }.toSet()
                    groupStays = allActiveStays.filter { s -> s.bookingId in groupBookingIds && s.status == "ACTIVE" }
                    // Build a display bill for each group stay
                    val bills = mutableMapOf<String, BillingInvoice>()
                    for (gs in groupStays) {
                        val r = runCatching { roomRepo.getById(gs.roomCategoryId) }.getOrNull()
                        val rPrice = r?.pricePerNight ?: 0.0
                        val nights = ChronoUnit.DAYS.between(gs.checkInActual.toInstant(), now.toInstant()).coerceAtLeast(1)
                        val rc = rPrice * nights
                        val bc = if (gs.breakfast) (r?.breakfastPrice ?: 0.0) * gs.adults * nights else 0.0
                        bills[gs.id] = BillingInvoice(
                            stayId = gs.id,
                            guestName = gs.guestName,
                            roomNumber = gs.roomNumber,
                            roomCharges = rc,
                            serviceCharges = bc,
                            earlyCheckInCharge = gs.earlyCheckInCharge,
                            totalAmount = rc + bc + gs.earlyCheckInCharge,
                            amountPaid = gs.advanceAmount,
                        )
                    }
                    groupBills = bills
                }
            }
            // Walk-in multi-room group: detect via groupStayId when no booking link exists
            if (groupStays.isEmpty() && stay.groupStayId.isNotBlank()) {
                val allActiveStays = runCatching { stayRepo.getActive(hotelId) }.getOrElse { emptyList() }
                val siblings = allActiveStays.filter { it.groupStayId == stay.groupStayId && it.status == "ACTIVE" }
                if (siblings.size > 1) {
                    groupStays = siblings
                    val bills = mutableMapOf<String, BillingInvoice>()
                    for (gs in siblings) {
                        val r = runCatching { roomRepo.getById(gs.roomCategoryId) }.getOrNull()
                        val rPrice = r?.pricePerNight ?: 0.0
                        val nights = ChronoUnit.DAYS.between(gs.checkInActual.toInstant(), now.toInstant()).coerceAtLeast(1)
                        val rc = rPrice * nights
                        val bc = if (gs.breakfast) (r?.breakfastPrice ?: 0.0) * gs.adults * nights else 0.0
                        bills[gs.id] = BillingInvoice(
                            stayId = gs.id,
                            guestName = gs.guestName,
                            roomNumber = gs.roomNumber,
                            roomCharges = rc,
                            serviceCharges = bc,
                            earlyCheckInCharge = gs.earlyCheckInCharge,
                            totalAmount = rc + bc + gs.earlyCheckInCharge,
                            amountPaid = gs.advanceAmount,
                        )
                    }
                    groupBills = bills
                }
            }

            _checkOutState.value = CheckOutState(
                isOpen = true, stay = stay, bill = displayBill,
                isLateCheckout = isLateCheckout,
                lateCheckoutCharge = flatFee,
                flatLateCheckoutFee = flatFee,
                roomPricePerNight = roomPricePerNight,
                lateChargeType = "FLAT",
                customLateChargeInput = if (flatFee > 0) flatFee.toLong().toString() else "",
                hotelCheckOutTime = hotel?.checkOutTime ?: "11:00",
                ordersTotal = ordersTotal,
                pendingOrders = pendingOrders,
                groupStays = groupStays,
                groupBills = groupBills,
                checkedGroupStayIds = groupStays.map { it.id }.toSet(), // all pre-checked
            )
        }
    }

    fun closeCheckOut() {
        _checkOutState.value = CheckOutState()
    }

    fun onLateChargeType(type: String) {
        _checkOutState.update { cos ->
            val charge = when (type) {
                "FLAT" -> cos.flatLateCheckoutFee
                "ROOM_RATE" -> cos.roomPricePerNight
                else -> 0.0
            }
            cos.copy(
                lateChargeType = type,
                lateCheckoutCharge = charge,
                customLateChargeInput = if (charge > 0) charge.toLong().toString() else "",
            )
        }
    }

    fun onLateChargeCustomInput(value: String) {
        _checkOutState.update { cos ->
            val parsed = value.toDoubleOrNull() ?: 0.0
            cos.copy(customLateChargeInput = value, lateCheckoutCharge = parsed)
        }
    }

    fun toggleOrderCheck(orderId: String) {
        _checkOutState.update { cos ->
            val updated = if (orderId in cos.checkedOrderIds) cos.checkedOrderIds - orderId else cos.checkedOrderIds + orderId
            cos.copy(checkedOrderIds = updated)
        }
    }

    fun toggleGroupStayCheck(stayId: String) {
        _checkOutState.update { cos ->
            val updated = if (stayId in cos.checkedGroupStayIds) cos.checkedGroupStayIds - stayId else cos.checkedGroupStayIds + stayId
            cos.copy(checkedGroupStayIds = updated)
        }
    }

    fun confirmCheckOut() {
        val cos = _checkOutState.value
        val primaryStay = cos.stay ?: return
        if (cos.groupStays.isNotEmpty() && cos.checkedGroupStayIds.isEmpty()) {
            _checkOutState.update { it.copy(error = "Select at least one room to check out") }
            return
        }
        _checkOutState.update { it.copy(isProcessing = true, error = null) }
        launchWithGlobalLoading {
            runCatching {
                val checkoutTime = Date()
                // Mark checked pending orders as COMPLETED before checking out
                cos.pendingOrders
                    .filter { it.id in cos.checkedOrderIds }
                    .forEach { order -> orderRepo.updateStatus(order.id, "COMPLETED") }

                // Determine which stays to check out
                val staysToCheckOut = if (cos.groupStays.isNotEmpty())
                    cos.groupStays.filter { it.id in cos.checkedGroupStayIds }
                else
                    listOf(primaryStay)

                for (stay in staysToCheckOut) {
                    stayRepo.checkOut(stay.id, checkoutTime, cos.lateCheckoutCharge)
                    instanceRepo.updateStatus(stay.roomInstanceId, "CLEANING", currentStayId = null)
                    if (stay.bookingId.isNotBlank()) {
                        val booking = runCatching { bookingRepo.getById(stay.bookingId) }.getOrNull()
                        if (booking != null) bookingRepo.update(booking.copy(status = "COMPLETED"))
                    }
                }
                // Create ONE combined Bill for all checked-out stays (group or single)
                runCatching { createCheckoutBill(staysToCheckOut, checkoutTime, cos.lateCheckoutCharge) }
            }.onSuccess {
                _checkOutState.value = CheckOutState(navigateToBilling = true, checkedOutStayId = primaryStay.id)
                _listState.update { it.copy(selectedStayId = null) }
                _detailState.value = StayDetailState()
                loadActive()
            }.onFailure { e ->
                _checkOutState.update { it.copy(isProcessing = false, error = e.message ?: "Check-out failed") }
            }
        }
    }

    fun onNavigateToBillingHandled() {
        _checkOutState.update { it.copy(navigateToBilling = false) }
    }

    private suspend fun createCheckoutBill(stays: List<Stay>, checkoutTime: Date, lateCheckoutCharge: Double) {
        if (stays.isEmpty()) return
        val primary = stays[0]
        // Skip if a bill already exists for the primary stay (re-checkout edge case)
        if (billRepo.getByStay(primary.id) != null) return

        val isGroup = stays.size > 1
        val allItems = mutableListOf<BillItem>()
        var totalAdvance = 0.0
        // Use the tax rate from the first room's category; consistent for group
        val firstRoom = runCatching { roomRepo.getById(primary.roomCategoryId) }.getOrNull()
        val taxPercentage = firstRoom?.taxPercentage ?: 0.0

        for (stay in stays) {
            val prefix = if (isGroup) "Room ${stay.roomNumber} — " else ""
            val room = runCatching { roomRepo.getById(stay.roomCategoryId) }.getOrNull()
            val roomPricePerNight = room?.pricePerNight ?: 0.0
            val nights = java.time.temporal.ChronoUnit.DAYS.between(
                stay.checkInActual.toInstant(), checkoutTime.toInstant()
            ).coerceAtLeast(1)
            val roomCharges = roomPricePerNight * nights
            val breakfastCharge = if (stay.breakfast) (room?.breakfastPrice ?: 0.0) * stay.adults * nights else 0.0
            val orders = runCatching { orderRepo.getByStay(stay.id) }.getOrElse { emptyList() }

            if (roomCharges > 0) allItems.add(BillItem(
                name = "${prefix}Room Charges ($nights night${if (nights > 1L) "s" else ""} × ₹${roomPricePerNight.toLong()})",
                type = "ROOM", quantity = nights.toInt(), unitPrice = roomPricePerNight, total = roomCharges,
            ))
            if (breakfastCharge > 0) allItems.add(BillItem(
                name = "${prefix}Breakfast",
                type = "SERVICE", quantity = 1, unitPrice = breakfastCharge, total = breakfastCharge,
            ))
            if (stay.earlyCheckInCharge > 0) allItems.add(BillItem(
                name = "${prefix}Early Check-in",
                type = "SERVICE", quantity = 1, unitPrice = stay.earlyCheckInCharge, total = stay.earlyCheckInCharge,
            ))
            if (lateCheckoutCharge > 0) allItems.add(BillItem(
                name = "${prefix}Late Check-out",
                type = "SERVICE", quantity = 1, unitPrice = lateCheckoutCharge, total = lateCheckoutCharge,
            ))
            for (order in orders) {
                if (order.totalAmount > 0) allItems.add(BillItem(
                    name = "$prefix${order.items.joinToString(", ") { it.name }.ifBlank { "Order" }}",
                    type = "ORDER", quantity = 1, unitPrice = order.totalAmount, total = order.totalAmount, refId = order.id,
                ))
            }
            totalAdvance += stay.advanceAmount
        }

        val subtotal = allItems.sumOf { it.total }
        val taxEnabled = taxPercentage > 0
        val taxAmount = if (taxEnabled) Math.round(subtotal * taxPercentage / 100.0).toDouble() else 0.0
        val total = subtotal + taxAmount
        val pending = (total - totalAdvance).coerceAtLeast(0.0)
        val status = when {
            pending <= 0 && total > 0 -> "PAID"
            totalAdvance > 0 -> "PARTIAL"
            else -> "PENDING"
        }
        val allRoomNumbers = stays.map { it.roomNumber }
        billRepo.createForStay(Bill(
            hotelId = AppContext.hotelId,
            stayId = primary.id,
            stayIds = stays.map { it.id },
            guestName = primary.guestName,
            roomNumber = allRoomNumbers.joinToString(", "),
            roomNumbers = allRoomNumbers,
            checkInDate = primary.checkInActual,
            checkOutDate = checkoutTime,
            items = allItems,
            taxEnabled = taxEnabled,
            taxPercentage = taxPercentage,
            subtotal = subtotal,
            taxAmount = taxAmount,
            totalAmount = total,
            advancePayment = totalAdvance,
            pendingAmount = pending,
            status = status,
            createdAt = checkoutTime,
        ))
    }

    // ── Add Order ─────────────────────────────────────────────────────────────

    fun openAddOrder() {
        val hotelId = AppContext.hotelId
        _addOrderState.value = AddOrderState(isOpen = true, isLoadingCatalog = true)
        launchWithGlobalLoading {
            val foodItems = runCatching { foodItemRepo.getByHotel(hotelId) }.getOrDefault(emptyList())
            val services = runCatching { serviceRepo.getByHotel(hotelId) }.getOrDefault(emptyList())
            val catalog = foodItems
                .map { CatalogItem(id = it.id, name = it.name, price = it.price, category = it.category.ifBlank { "Food" }, isAvailable = it.isAvailable) } +
                services
                .map { CatalogItem(id = it.id, name = it.name, price = it.price, category = "Services", isAvailable = it.isActive) }
            val firstCategory = catalog.filter { it.isAvailable }.map { it.category }.distinct().firstOrNull() ?: ""
            _addOrderState.update { it.copy(
                isLoadingCatalog = false,
                catalogItems = catalog,
                items = listOf(OrderItemEntry(category = firstCategory)),
            ) }
        }
    }

    fun closeAddOrder() { _addOrderState.value = AddOrderState() }

    fun refreshAddOrderCatalog() {
        val hotelId = AppContext.hotelId
        if (hotelId.isBlank() || !_addOrderState.value.isOpen) return
        launchWithGlobalLoading {
            val foodItems = runCatching { foodItemRepo.getByHotel(hotelId) }.getOrDefault(emptyList())
            val services = runCatching { serviceRepo.getByHotel(hotelId) }.getOrDefault(emptyList())
            val catalog = foodItems
                .map { CatalogItem(id = it.id, name = it.name, price = it.price, category = it.category.ifBlank { "Food" }, isAvailable = it.isAvailable) } +
                services
                .map { CatalogItem(id = it.id, name = it.name, price = it.price, category = "Services", isAvailable = it.isActive) }
            // Re-filter suggestions for all current items so "Add new" disappears immediately
            _addOrderState.update { s ->
                val updatedItems = s.items.map { entry ->
                    if (entry.name.isBlank()) entry
                    else {
                        val q = entry.name.trim().lowercase()
                        val newSuggestions = catalog.filter { item ->
                            item.name.lowercase().contains(q) &&
                                (entry.category.isBlank() || item.category.split(",").map(String::trim).any { it == entry.category })
                        }.take(6)
                        entry.copy(suggestions = newSuggestions, showSuggestions = newSuggestions.isNotEmpty())
                    }
                }
                s.copy(catalogItems = catalog, items = updatedItems)
            }
        }
    }

    fun onAddOrderItemCategory(index: Int, v: String) = _addOrderState.update { s ->
        s.copy(items = s.items.toMutableList().also {
            it[index] = it[index].copy(category = v, name = "", suggestions = emptyList(), showSuggestions = false)
        })
    }
    fun onAddOrderNotes(v: String) = _addOrderState.update { it.copy(notes = v) }

    fun onAddOrderItemName(index: Int, v: String) = _addOrderState.update { s ->
        val itemCategory = s.items.getOrNull(index)?.category ?: ""
        val suggestions = if (v.isBlank()) emptyList() else {
            val query = v.trim().lowercase()
            s.catalogItems.filter { item ->
                item.name.lowercase().contains(query) &&
                    (itemCategory.isBlank() || item.category.split(",").map(String::trim).any { it == itemCategory })
            }.take(6)
        }
        // Auto-fill price when typed name exactly matches an available catalog item and price is blank
        val exactMatch = s.catalogItems.find { it.name.equals(v.trim(), ignoreCase = true) && it.isAvailable }
        val currentEntry = s.items[index]
        val autoPrice = if (exactMatch != null && currentEntry.price.isBlank() && exactMatch.price > 0)
            exactMatch.price.toLong().toString() else currentEntry.price
        s.copy(items = s.items.toMutableList().also {
            it[index] = it[index].copy(
                name = v,
                price = autoPrice,
                suggestions = suggestions,
                showSuggestions = suggestions.isNotEmpty(),
            )
        })
    }

    fun selectOrderItemSuggestion(index: Int, suggestion: CatalogItem) = _addOrderState.update { s ->
        s.copy(items = s.items.toMutableList().also {
            it[index] = it[index].copy(
                name = suggestion.name,
                price = if (suggestion.price > 0) suggestion.price.toLong().toString() else "",
                suggestions = emptyList(),
                showSuggestions = false,
            )
        })
    }

    fun dismissOrderItemSuggestions(index: Int) = _addOrderState.update { s ->
        s.copy(items = s.items.toMutableList().also {
            it[index] = it[index].copy(showSuggestions = false)
        })
    }

    fun onAddOrderItemQty(index: Int, qty: Int) = _addOrderState.update { s ->
        s.copy(items = s.items.toMutableList().also { it[index] = it[index].copy(quantity = qty.coerceAtLeast(1)) })
    }
    fun onAddOrderItemPrice(index: Int, v: String) = _addOrderState.update { s ->
        s.copy(items = s.items.toMutableList().also { it[index] = it[index].copy(price = v.filter { c -> c.isDigit() || c == '.' }) })
    }
    fun addOrderItem() = _addOrderState.update { s ->
        val firstCategory = s.categories.firstOrNull() ?: ""
        s.copy(items = s.items + OrderItemEntry(category = firstCategory))
    }
    fun removeOrderItem(index: Int) = _addOrderState.update { s ->
        if (s.items.size <= 1) return@update s
        s.copy(items = s.items.toMutableList().also { it.removeAt(index) })
    }

    fun submitAddOrder() {
        val s = _addOrderState.value
        val stay = _listState.value.stays.find { it.id == _listState.value.selectedStayId } ?: return
        val validItems = s.items.filter { it.name.isNotBlank() }
        if (validItems.isEmpty()) {
            _addOrderState.update { it.copy(error = "Add at least one item") }
            return
        }
        _addOrderState.update { it.copy(isSaving = true, error = null) }
        launchWithGlobalLoading {
            val orderItems = validItems.map { OrderItem(name = it.name.trim(), quantity = it.quantity, price = it.price.toDoubleOrNull() ?: 0.0) }
            val total = orderItems.sumOf { it.price * it.quantity }
            val orderType = if (validItems.any { it.category == "Services" }) "SERVICE" else "ROOM_SERVICE"
            runCatching {
                orderRepo.add(Order(
                    hotelId = AppContext.hotelId,
                    stayId = stay.id,
                    roomNumber = stay.roomNumber,
                    guestName = stay.guestName,
                    items = orderItems,
                    type = orderType,
                    totalAmount = total,
                    notes = s.notes.trim(),
                    orderedAt = Date(),
                ))
            }.onSuccess {
                _addOrderState.value = AddOrderState()
                refreshDetail()
                pollBadges()
            }.onFailure { e ->
                _addOrderState.update { it.copy(isSaving = false, error = e.message ?: "Failed to add order") }
            }
        }
    }

    // ── Add Complaint ─────────────────────────────────────────────────────────

    fun openAddComplaint() { _addComplaintState.value = AddComplaintState(isOpen = true) }
    fun closeAddComplaint() { _addComplaintState.value = AddComplaintState() }

    fun onAddComplaintCategory(v: String) = _addComplaintState.update { it.copy(category = v) }
    fun onAddComplaintDescription(v: String) = _addComplaintState.update { it.copy(description = v) }
    fun onAddComplaintPriority(v: String) = _addComplaintState.update { it.copy(priority = v) }

    fun submitAddComplaint() {
        val s = _addComplaintState.value
        val stay = _listState.value.stays.find { it.id == _listState.value.selectedStayId } ?: return
        if (s.description.isBlank()) {
            _addComplaintState.update { it.copy(error = "Description is required") }
            return
        }
        _addComplaintState.update { it.copy(isSaving = true, error = null) }
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
                _addComplaintState.value = AddComplaintState()
                refreshDetail()
                pollBadges()
            }.onFailure { e ->
                _addComplaintState.update { it.copy(isSaving = false, error = e.message ?: "Failed to add complaint") }
            }
        }
    }

    // ── Extend Stay ───────────────────────────────────────────────────────────

    fun openExtendStay(stayId: String) {
        val stay = _listState.value.stays.find { it.id == stayId } ?: return
        _extendStayState.value = ExtendStayState(isOpen = true, stay = stay)
    }

    fun closeExtendStay() {
        _extendStayState.value = ExtendStayState()
    }

    fun onExtendNewCheckOut(date: Date) {
        val stay = _extendStayState.value.stay ?: return
        // New checkout must be after current expectedCheckOut
        if (!date.after(stay.expectedCheckOut)) return
        _extendStayState.update { it.copy(newCheckOut = date, isChecking = true, roomAvailable = null, alternativeInstances = emptyList(), error = null) }
        viewModelScope.launch {
            checkExtendAvailability(stay, date)
        }
    }

    private suspend fun checkExtendAvailability(stay: Stay, newCheckOut: Date) {
        val extStart = stay.expectedCheckOut   // extension window: currentCheckOut → newCheckOut
        val extEnd = newCheckOut
        val hotelId = AppContext.hotelId

        runCatching {
            val bookings = bookingRepo.getAllByHotel(hotelId)
            val activeStays = stayRepo.getActive(hotelId)
            // Load ALL instances once — used for both the category check and all-category options
            val allInstances = instanceRepo.getAll().filter { it.hotelId == hotelId }

            // Pre-compute sets used in multiple steps.
            // Exclude bookings that are already checked in (active stay exists) to avoid double-counting.
            val checkedInBookingIds = activeStays.map { it.bookingId }.filter { it.isNotBlank() }.toSet()
            val confirmedInWindow = bookings.filter { b ->
                b.status == "CONFIRMED" && b.id !in checkedInBookingIds && b.checkIn < extEnd && b.checkOut > extStart
            }
            val activeInWindow = activeStays.filter { s ->
                s.id != stay.id && s.checkInActual < extEnd && s.expectedCheckOut > extStart
            }

            // ── Step 1: Category-level capacity check (same algorithm as walk-in) ──
            val catBookingCount = confirmedInWindow.count { it.roomCategoryId == stay.roomCategoryId }
            val catStayCount = activeInWindow.count { it.roomCategoryId == stay.roomCategoryId }
            val usableCatInstances = allInstances.filter {
                it.categoryId == stay.roomCategoryId && it.status !in setOf("MAINTENANCE", "CLEANING")
            }
            val categoryAvail = usableCatInstances.size - (catBookingCount + catStayCount)

            // Helper: compute all-category availability for the extension window
            suspend fun computeAllCategoryOptions(): List<ExtendAvailableCategory> {
                val categories = runCatching { roomRepo.getByHotel(hotelId) }.getOrElse { emptyList() }
                return categories.mapNotNull { cat ->
                    val bookingCount = confirmedInWindow.count { it.roomCategoryId == cat.id }
                    val stayCount = activeInWindow.count { it.roomCategoryId == cat.id }
                    val usable = allInstances.count { it.categoryId == cat.id && it.status !in setOf("MAINTENANCE", "CLEANING") }
                    val avail = usable - bookingCount - stayCount
                    if (avail > 0 && cat.available) ExtendAvailableCategory(cat.type, avail, cat.pricePerNight) else null
                }.sortedByDescending { it.availableCount }
            }

            if (categoryAvail <= 0) {
                // Category is fully booked — show all other available categories
                val catOptions = computeAllCategoryOptions()
                _extendStayState.update {
                    it.copy(isChecking = false, roomAvailable = false, alternativeInstances = emptyList(), availableCategoryOptions = catOptions)
                }
                return
            }

            // ── Step 2: Instance-level check for the specific room ────────────────
            val bookedInstanceIds = confirmedInWindow.filter { it.roomInstanceId.isNotBlank() }.map { it.roomInstanceId }.toSet()
            val occupiedInstanceIds = activeInWindow.map { it.roomInstanceId }.toSet()

            val conflictingBooking = stay.roomInstanceId.isNotBlank() && stay.roomInstanceId in bookedInstanceIds
            val conflictingStay = stay.roomInstanceId in occupiedInstanceIds
            val roomFree = !conflictingBooking && !conflictingStay

            // ── Step 3: Alternatives within same category if specific room is taken ─
            val alternatives = if (!roomFree) {
                usableCatInstances.filter { inst ->
                    inst.id != stay.roomInstanceId &&
                    inst.id !in bookedInstanceIds &&
                    inst.id !in occupiedInstanceIds
                }
            } else emptyList()

            // Also compute all-category options when specific room isn't free
            val catOptions = if (!roomFree) computeAllCategoryOptions() else emptyList()

            _extendStayState.update { it.copy(isChecking = false, roomAvailable = roomFree, alternativeInstances = alternatives, availableCategoryOptions = catOptions) }
        }.onFailure { e ->
            _extendStayState.update { it.copy(isChecking = false, error = e.message ?: "Availability check failed") }
        }
    }

    fun confirmExtend() {
        val es = _extendStayState.value
        val stay = es.stay ?: return
        val newCheckOut = es.newCheckOut ?: return
        if (es.roomAvailable != true) return
        _extendStayState.update { it.copy(isSaving = true, error = null) }
        viewModelScope.launch {
            runCatching { stayRepo.updateExpectedCheckOut(stay.id, newCheckOut.toMidnightUtc()) }
                .onSuccess {
                    _extendStayState.value = ExtendStayState()
                    // Refresh the list so the updated checkout shows immediately
                    _listState.update { ls ->
                        ls.copy(stays = ls.stays.map { s ->
                            if (s.id == stay.id) s.copy(expectedCheckOut = newCheckOut.toMidnightUtc()) else s
                        })
                    }
                }
                .onFailure { e -> _extendStayState.update { it.copy(isSaving = false, error = e.message ?: "Failed to extend stay") } }
        }
    }

    // ── Change Room ───────────────────────────────────────────────────────────

    fun openChangeRoom(stayId: String) {
        val stay = _listState.value.stays.find { it.id == stayId } ?: return
        _changeRoomState.value = ChangeRoomState(isOpen = true, stay = stay, isLoading = true)
        val hotelId = AppContext.hotelId
        val now = Date()
        val checkOut = stay.expectedCheckOut
        launchWithGlobalLoading {
            val categoryNames = runCatching { roomRepo.getByHotel(hotelId) }
                .getOrElse { emptyList() }.associate { it.id to it.type }

            val allInstances = runCatching { instanceRepo.listenByHotel(hotelId).first() }
                .getOrElse { emptyList() }
                .filter { it.status != "MAINTENANCE" && it.id != stay.roomInstanceId }

            val confirmedBookings = runCatching { bookingRepo.getConfirmedByHotel(hotelId) }.getOrElse { emptyList() }
            val bookedIds = confirmedBookings
                .filter { it.roomInstanceId.isNotBlank() && it.checkIn.before(checkOut) && it.checkOut.after(now) }
                .map { it.roomInstanceId }.toSet()

            val activeStays = runCatching { stayRepo.getActive(hotelId) }.getOrElse { emptyList() }
            val occupiedIds = activeStays
                .filter { it.id != stayId }
                .map { it.roomInstanceId }.toSet()

            val roomSort = compareBy<RoomInstance>({ it.roomNumber.toIntOrNull() ?: Int.MAX_VALUE }, { it.roomNumber })
            val selectable = mutableListOf<RoomInstance>()
            val cleaning = mutableListOf<RoomInstance>()
            for (inst in allInstances) {
                when {
                    inst.id in occupiedIds -> continue
                    inst.id in bookedIds -> continue
                    inst.status == "CLEANING" -> cleaning.add(inst)
                    else -> selectable.add(inst)
                }
            }
            selectable.sortWith(roomSort)
            cleaning.sortWith(roomSort)

            _changeRoomState.update { it.copy(selectableRooms = selectable, cleaningRooms = cleaning, categoryNames = categoryNames, isLoading = false) }
        }
    }

    fun closeChangeRoom() {
        _changeRoomState.value = ChangeRoomState()
    }

    fun onChangeRoomSelected(instance: RoomInstance) {
        _changeRoomState.update { it.copy(selectedInstance = instance) }
    }

    fun confirmChangeRoom() {
        val cs = _changeRoomState.value
        val stay = cs.stay ?: return
        val newRoom = cs.selectedInstance ?: return
        _changeRoomState.update { it.copy(isSaving = true, error = null) }
        launchWithGlobalLoading {
            runCatching {
                stayRepo.changeRoom(stay.id, stay.roomInstanceId, newRoom.id, newRoom.roomNumber, newRoom.categoryId, newRoom.categoryName)
            }.onSuccess {
                _changeRoomState.value = ChangeRoomState()
            }.onFailure { e ->
                _changeRoomState.update { it.copy(isSaving = false, error = e.message ?: "Failed to change room") }
            }
        }
    }
}
