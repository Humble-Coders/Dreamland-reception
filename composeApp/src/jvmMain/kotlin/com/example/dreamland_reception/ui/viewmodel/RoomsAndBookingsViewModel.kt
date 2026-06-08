package com.example.dreamland_reception.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.dreamland_reception.data.AppContext
import com.example.dreamland_reception.data.accounting.AccountingRepository
import com.example.dreamland_reception.ui.notification.SpeechAnnouncer
import com.example.dreamland_reception.data.model.Booking
import com.example.dreamland_reception.data.model.BookingSource
import com.example.dreamland_reception.data.model.Hotel
import com.example.dreamland_reception.data.model.Room
import com.example.dreamland_reception.data.model.RoomInstance
import com.example.dreamland_reception.data.model.Stay
import com.example.dreamland_reception.data.firebase.CloudFunctionClient
import com.example.dreamland_reception.data.repository.BookingRepository
import com.example.dreamland_reception.data.repository.BookingSourceRepository
import com.example.dreamland_reception.data.repository.CancelBookingResult
import com.example.dreamland_reception.data.repository.FirestoreBookingRepository
import com.example.dreamland_reception.data.repository.ManualRefundMethod
import com.example.dreamland_reception.data.repository.ReceptionRefundMode
import com.example.dreamland_reception.data.repository.FirestoreBookingSourceRepository
import com.example.dreamland_reception.data.repository.FirestoreHotelRepository
import com.example.dreamland_reception.data.repository.FirestoreRoomInstanceRepository
import com.example.dreamland_reception.data.repository.FirestoreRoomRepository
import com.example.dreamland_reception.data.repository.FirestoreStayRepository
import com.example.dreamland_reception.data.repository.HotelRepository
import com.example.dreamland_reception.data.repository.RoomInstanceRepository
import com.example.dreamland_reception.data.repository.RoomRepository
import com.example.dreamland_reception.data.repository.StayRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import com.example.dreamland_reception.util.atHotelTime
import com.example.dreamland_reception.util.localTodayUtcMidnight
import com.example.dreamland_reception.util.normalizePhoneE164
import com.example.dreamland_reception.util.toLocalDayUtcMidnight
import com.example.dreamland_reception.util.toMidnightUtc
import java.util.Calendar
import java.util.Date
import java.util.UUID

// ── Filter enums ──────────────────────────────────────────────────────────────

enum class BookingDateFilter { ALL, TODAY, UPCOMING }

data class NoShowRefundDialogState(
    val booking: Booking,
    val refundStatus: String = "PENDING",   // PENDING | REFUNDED | FORFEITED | PARTIAL
    val note: String = "",
    val isSaving: Boolean = false,
    val error: String? = null,
)

/** Surfaced inline in the cancel dialog (error banner). */
data class CancelDialogError(
    val title: String,
    val message: String,
    val retrySafe: Boolean,
)

/** Drives the new reception cancel + refund dialog. */
data class CancelDialogState(
    val primary: Booking,                          // used for guest name, dates, etc.
    val groupBookings: List<Booking>,              // all bookings sharing primary.groupBookingId (or just [primary])
    val reason: String = "",
    val refundMode: ReceptionRefundMode = ReceptionRefundMode.POLICY,
    /**
     * Free-text ₹ amount field.
     *   - Razorpay flow: only used (and only shown) for FIXED mode.
     *   - Manual flow: ALWAYS shown. Auto-filled by the ViewModel when POLICY
     *     or FULL is selected; blank when FIXED is selected.
     */
    val refundAmountRupeesInput: String = "",
    /**
     * Manual-refund payout method. `null` until reception picks one.
     * Only relevant when [isManual] is true; ignored on the Razorpay flow.
     */
    val paidVia: ManualRefundMethod? = null,
    // App-booking (Razorpay) refunds: how the refund is split across Cash/Bank for the LEDGER +
    // Firestore till recording (independent of the Razorpay online refund, which is untouched).
    // Free-text ₹ amounts; empty = 0. Must sum to the effective refund amount.
    val refundCashInput: String = "",
    val refundBankInput: String = "",
    val isLoading: Boolean = false,
    val error: CancelDialogError? = null,
    /**
     * Computed POLICY-mode refund preview (in paise).
     *   null   → still loading the room policies from Firestore
     *   value  → final amount, even if 0 (no refund per policy)
     */
    val policyPreviewPaise: Long? = null,
) {
    /** Sum of advances across the group, falling back to rupees Double if the paise field isn't populated (walk-in bookings). */
    val totalAdvancePaise: Long get() = groupBookings.sumOf {
        if (it.advancePaidAmountPaise > 0L) it.advancePaidAmountPaise
        else (it.advancePaidAmount * 100.0).toLong()
    }

    /**
     * `true` when this booking group was created hotel-side (no Razorpay payment).
     * Drives the entire manual-refund branch: shows the info banner, the
     * Refund-amount field on all modes, and the Paid-via CASH/BANK toggle.
     */
    val isManual: Boolean get() = groupBookings.all { it.paymentOrderId.isBlank() }

    /**
     * The refund amount (paise) implied by the selected mode — what the Cash/Bank split must total
     * for the ledger + till recording. POLICY → policy preview, FULL → whole advance, FIXED → typed.
     */
    val effectiveRefundPaise: Long
        get() = when (refundMode) {
            ReceptionRefundMode.POLICY -> policyPreviewPaise ?: 0L
            ReceptionRefundMode.FULL -> totalAdvancePaise
            ReceptionRefundMode.FIXED -> refundAmountRupeesInput.toDoubleOrNull()?.let { (it * 100.0).toLong() } ?: 0L
        }
}

data class RoomCategoryEntry(
    val categoryId: String = "",
    val categoryName: String = "",
    val selectedInstanceId: String = "",
    val selectedRoomNumber: String = "",
)

// ── UI state ──────────────────────────────────────────────────────────────────

data class RoomsAndBookingsUiState(
    // Rooms (real-time)
    val rooms: List<RoomInstance> = emptyList(),
    val roomsLoading: Boolean = true,
    // Bookings (real-time)
    val bookings: List<Booking> = emptyList(),
    val bookingsLoading: Boolean = true,
    // Active stays keyed by roomNumber for guest-name display
    val activeStaysByRoom: Map<String, Stay> = emptyMap(),
    // Same active stays as a flat list — use this (not activeStaysByRoom.values) for any
    // overlap/availability math: roomNumber can collide across categories, so the map silently
    // drops stays and undercounts occupancy.
    val activeStays: List<Stay> = emptyList(),
    // Room categories: categoryId -> display name (loaded from `rooms` collection)
    val categoryNames: Map<String, String> = emptyMap(),
    // Full room category objects (for price, capacity, etc. in Add Booking dialog)
    val roomCategories: List<Room> = emptyList(),
    // Tab: 0 = Rooms, 1 = Bookings
    val selectedTab: Int = 0,
    // Search / filter
    val searchQuery: String = "",
    val bookingStatusFilter: String? = null, // null = All
    val bookingDateFilter: BookingDateFilter = BookingDateFilter.ALL,
    val bookingSortOrder: String = "NEWEST", // NEWEST | OLDEST
    // Assign-room dialog
    val assignRoomDialogBooking: Booking? = null,
    val availableRoomsForAssign: List<RoomInstance> = emptyList(),
    val assignRoomLoading: Boolean = false,
    // Cancel + refund dialog (single AND group flows share this state)
    val cancelDialog: CancelDialogState? = null,
    // General error / snackbar
    val error: String? = null,
    val operationMessage: String? = null,
    // Room panel filters
    val roomCategoryFilter: String = "",
    val roomStatusFilter: String = "",
    // Hotel times — populated from Firestore on init; empty until loaded
    val hotelCheckInTime: String = "",
    val hotelCheckOutTime: String = "",
    // No-show confirmation + refund outcome
    val noShowConfirmBooking: Booking? = null,
    val noShowRefundDialog: NoShowRefundDialogState? = null,
    // Group no-show (mark all bookings in a group at once)
    val groupNoShowBookings: List<Booking>? = null,
    val groupNoShowSelectedIds: Set<String> = emptySet(),
    // (Group cancel is now folded into cancelDialog above — the dialog handles 1..N bookings.)
    // Add booking dialog
    val showAddBookingDialog: Boolean = false,
    val bookingSources: List<BookingSource> = emptyList(),
    val addBookingLoading: Boolean = false,
    val newBookingAvailableRooms: List<RoomInstance> = emptyList(),
    val newBookingCategoryAvailability: Map<String, Int> = emptyMap(),
    // Room detail
    val selectedRoomId: String? = null,
    val roomDetailStays: List<Stay> = emptyList(),
    val roomDetailLoading: Boolean = false,
) {
    val isInitialLoading: Boolean get() = roomsLoading && rooms.isEmpty() && bookingsLoading && bookings.isEmpty()

    val roomsForPanel: List<RoomInstance>
        get() {
            var base = rooms
            val q = searchQuery.trim().lowercase()
            if (q.isNotEmpty()) base = base.filter { it.roomNumber.lowercase().contains(q) }
            if (roomCategoryFilter.isNotBlank()) base = base.filter { it.categoryId == roomCategoryFilter }
            // Note: roomStatusFilter is applied in the UI after computing the derived displayStatus
            return base.sortedWith(compareBy({ it.roomNumber.length }, { it.roomNumber }))
        }

    val selectedRoom: RoomInstance?
        get() = selectedRoomId?.let { id -> rooms.find { it.id == id } }

    val selectedRoomCurrentStay: Stay?
        get() = selectedRoom?.let { room -> activeStaysByRoom[room.roomNumber] }

    val selectedRoomUpcomingBookings: List<Booking>
        get() = selectedRoomId?.let { id ->
            bookings.filter { it.roomInstanceId == id && (it.status == "CONFIRMED" || it.status == "PENDING_PAYMENT") }
                .sortedBy { it.checkIn }
        } ?: emptyList()

    val filteredRooms: List<RoomInstance>
        get() {
            val q = searchQuery.trim().lowercase()
            val base = if (q.isEmpty()) rooms else rooms.filter { it.roomNumber.lowercase().contains(q) }
            return base.sortedWith(compareBy({ it.roomNumber.length }, { it.roomNumber }))
        }

    val filteredBookings: List<Booking>
        get() {
            val q = searchQuery.trim().lowercase()
            val todayStart   = localTodayUtcMidnight()
            val tomorrowStart = Date(todayStart.time + 86_400_000L)

            return bookings.filter { booking ->
                if (booking.source == "WALK_IN") return@filter false
                val matchesSearch = q.isEmpty() || booking.guestName.lowercase().contains(q)
                val matchesStatus = bookingStatusFilter == null || booking.status == bookingStatusFilter
                val matchesDate = when (bookingDateFilter) {
                    BookingDateFilter.TODAY -> booking.checkIn >= todayStart && booking.checkIn < tomorrowStart
                    // Keep future bookings AND any CONFIRMED booking whose check-in has already passed
                    // (overdue bookings must not disappear from the list until actioned)
                    BookingDateFilter.UPCOMING -> booking.checkIn >= todayStart || booking.status == "CONFIRMED" || booking.status == "PENDING_PAYMENT"
                    BookingDateFilter.ALL -> true
                }
                matchesSearch && matchesStatus && matchesDate
            }.let { list ->
                if (bookingSortOrder == "NEWEST") list.sortedByDescending { it.checkIn }
                else list.sortedBy { it.checkIn }
            }
        }

    // Groups bookings by groupBookingId; solo bookings get their own single-element list.
    // Order follows filteredBookings sort — a group appears where its first member appears.
    val filteredBookingGroups: List<List<Booking>>
        get() {
            val all = filteredBookings
            val byGroup = all.filter { it.groupBookingId.isNotBlank() }.groupBy { it.groupBookingId }
            val seenGroups = mutableSetOf<String>()
            return buildList {
                for (booking in all) {
                    if (booking.groupBookingId.isBlank()) {
                        add(listOf(booking))
                    } else if (byGroup[booking.groupBookingId] != null && seenGroups.add(booking.groupBookingId)) {
                        add(byGroup.getValue(booking.groupBookingId))
                    }
                }
            }
        }

    fun isCheckInToday(booking: Booking): Boolean {
        val today    = localTodayUtcMidnight()
        val tomorrow = Date(today.time + 86_400_000L)
        return !booking.checkIn.before(today) && booking.checkIn.before(tomorrow)
    }

    fun isCheckInTodayOrPassed(booking: Booking): Boolean {
        val tomorrow = Date(localTodayUtcMidnight().time + 86_400_000L)
        return booking.checkIn.before(tomorrow)
    }
}

// ── ViewModel ─────────────────────────────────────────────────────────────────

class RoomsAndBookingsViewModel(
    private val bookingRepo: BookingRepository = FirestoreBookingRepository,
    private val roomInstanceRepo: RoomInstanceRepository = FirestoreRoomInstanceRepository,
    private val roomRepo: RoomRepository = FirestoreRoomRepository,
    private val stayRepo: StayRepository = FirestoreStayRepository,
    private val hotelRepo: HotelRepository = FirestoreHotelRepository,
    private val bookingSourceRepo: BookingSourceRepository = FirestoreBookingSourceRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(RoomsAndBookingsUiState())
    val uiState: StateFlow<RoomsAndBookingsUiState> = _uiState.asStateFlow()

    private var hotel: Hotel? = null
    // Booking ids seen on the previous snapshot — used to detect newly-arrived bookings to announce.
    private var previousBookingIds: Set<String> = emptySet()

    init {
        startListeners()
        loadCategoryNames()
        loadHotel()
        loadBookingSources()
    }

    private fun loadHotel() {
        viewModelScope.launch {
            runCatching { hotelRepo.getById(AppContext.hotelId) }
                .onSuccess { h ->
                    hotel = h
                    if (h != null) {
                        _uiState.update { it.copy(
                            hotelCheckInTime = h.checkInTime,
                            hotelCheckOutTime = h.checkOutTime,
                        )}
                    }
                }
        }
    }

    // ── Real-time listeners ───────────────────────────────────────────────────

    private fun startListeners() {
        val hotelId = AppContext.hotelId
        viewModelScope.launch {
            roomInstanceRepo.listenByHotel(hotelId)
                .catch { e -> _uiState.update { it.copy(roomsLoading = false, error = "Rooms: ${e.message}") } }
                .collect { rooms -> _uiState.update { it.copy(rooms = rooms, roomsLoading = false) } }
        }
        viewModelScope.launch {
            bookingRepo.listenByHotel(hotelId)
                .catch { e -> _uiState.update { it.copy(bookingsLoading = false, error = "Bookings: ${e.message}") } }
                .collect { bookings ->
                    // Announce genuinely-new mobile-app bookings aloud (twice). Skip the initial
                    // load and any reception-created (walk-in / agent) bookings.
                    val currentIds = bookings.map { it.id }.toSet()
                    val firstEmit = previousBookingIds.isEmpty() && _uiState.value.bookings.isEmpty()
                    val newArrivals = currentIds - previousBookingIds
                    if (!firstEmit && newArrivals.isNotEmpty() &&
                        bookings.any { it.id in newArrivals && it.source.equals("APP", ignoreCase = true) }
                    ) {
                        runCatching { SpeechAnnouncer.announce("New Booking from Mobile App") }
                    }
                    previousBookingIds = currentIds
                    _uiState.update { it.copy(bookings = bookings, bookingsLoading = false) }
                }
        }
        viewModelScope.launch {
            stayRepo.listenActive(hotelId)
                .catch { e -> _uiState.update { it.copy(error = "Stays: ${e.message}") } }
                .collect { stays ->
                    _uiState.update { it.copy(
                        activeStays = stays,
                        activeStaysByRoom = stays.associateBy { s -> s.roomNumber },
                    ) }
                }
        }
    }

    private fun loadCategoryNames() {
        viewModelScope.launch {
            runCatching { roomRepo.getByHotel(AppContext.hotelId) }
                .onSuccess { rooms ->
                    // Room.id = categoryId, Room.type = display name (mapped from Firestore "name" field)
                    val names = rooms.associate { it.id to it.type.ifBlank { "Room" } }
                    _uiState.update { it.copy(categoryNames = names, roomCategories = rooms) }
                }
        }
    }

    fun refresh() {
        _uiState.update { it.copy(roomsLoading = true, bookingsLoading = true) }
        startListeners()
        loadCategoryNames()
    }

    // ── UI state setters ──────────────────────────────────────────────────────

    fun setTab(tab: Int) = _uiState.update { it.copy(selectedTab = tab, searchQuery = "", selectedRoomId = null) }
    fun setSearchQuery(q: String) = _uiState.update { it.copy(searchQuery = q) }
    fun setBookingStatusFilter(status: String?) = _uiState.update { it.copy(bookingStatusFilter = status) }
    fun setBookingDateFilter(filter: BookingDateFilter) = _uiState.update { it.copy(bookingDateFilter = filter) }
    fun setBookingSortOrder(order: String) = _uiState.update { it.copy(bookingSortOrder = order) }
    fun clearError() = _uiState.update { it.copy(error = null) }
    fun clearMessage() = _uiState.update { it.copy(operationMessage = null) }
    fun setRoomCategoryFilter(id: String) = _uiState.update { it.copy(roomCategoryFilter = id) }
    fun setRoomStatusFilter(status: String) = _uiState.update { it.copy(roomStatusFilter = status) }

    fun selectRoom(id: String?) {
        _uiState.update { it.copy(selectedRoomId = id, roomDetailStays = emptyList(), roomDetailLoading = id != null) }
        if (id == null) return
        viewModelScope.launch {
            runCatching { stayRepo.getAll(AppContext.hotelId) }
                .onSuccess { allStays ->
                    val roomStays = allStays.filter { it.roomInstanceId == id }.sortedByDescending { it.checkInActual }
                    _uiState.update { it.copy(roomDetailStays = roomStays, roomDetailLoading = false) }
                }
                .onFailure { _uiState.update { it.copy(roomDetailLoading = false) } }
        }
    }

    // ── Room actions ──────────────────────────────────────────────────────────

    fun toggleMaintenance(room: RoomInstance) {
        launchWithGlobalLoading {
            val newStatus = if (room.status == "MAINTENANCE") "AVAILABLE" else "MAINTENANCE"
            runCatching { roomInstanceRepo.updateStatus(room.id, newStatus, null) }
                .onSuccess { _uiState.update { it.copy(operationMessage = "Room ${room.roomNumber} set to ${newStatus.lowercase().replaceFirstChar { it.uppercaseChar() }}") } }
                .onFailure { e -> _uiState.update { it.copy(error = e.message) } }
        }
    }

    fun markCleaningComplete(room: RoomInstance) {
        launchWithGlobalLoading {
            runCatching {
                // Housekeeping done — return the CLEANING room to AVAILABLE.
                roomInstanceRepo.updateStatus(room.id, "AVAILABLE", null)
            }
                .onSuccess { _uiState.update { it.copy(operationMessage = "Room ${room.roomNumber} marked as available") } }
                .onFailure { e -> _uiState.update { it.copy(error = e.message) } }
        }
    }

    // ── Assign room ───────────────────────────────────────────────────────────

    fun openAssignRoom(booking: Booking) {
        viewModelScope.launch {
            _uiState.update { it.copy(assignRoomDialogBooking = booking, assignRoomLoading = true, availableRoomsForAssign = emptyList()) }
            runCatching {
                val hotelId = AppContext.hotelId
                // Exclude MAINTENANCE, CLEANING; status=="AVAILABLE" is unreliable since checkInBatch
                // never updates status — use currentStayId to detect current occupancy
                val candidates = roomInstanceRepo.getAll()
                    .filter { it.hotelId == hotelId && it.status !in setOf("MAINTENANCE", "CLEANING") }
                val confirmedBookings = _uiState.value.bookings.filter { it.status == "CONFIRMED" || it.status == "PENDING_PAYMENT" }
                val noConflict = dateConflictFilter(candidates, confirmedBookings, booking.checkIn.toLocalDayUtcMidnight(), booking.checkOut.toLocalDayUtcMidnight(), excludeBookingId = booking.id)
                // Also exclude rooms occupied by active stays overlapping the booking period.
                // Use fresh Firestore data — cached activeStaysByRoom may be stale.
                val ciTime = _uiState.value.hotelCheckInTime
                val coTime = _uiState.value.hotelCheckOutTime
                val activeStays = runCatching { stayRepo.getActive(hotelId) }.getOrElse { emptyList() }
                val now = Date()
                noConflict.filter { room ->
                    activeStays.none { stay ->
                        stay.roomInstanceId == room.id &&
                            stay.checkInActual.atHotelTime(ciTime) < booking.checkOut.atHotelTime(coTime) &&
                            maxOf(stay.expectedCheckOut, now).atHotelTime(coTime) > booking.checkIn.atHotelTime(ciTime)
                    }
                }.sortedWith(compareBy({ it.roomNumber.length }, { it.roomNumber }))
            }
            .onSuccess { rooms -> _uiState.update { it.copy(availableRoomsForAssign = rooms, assignRoomLoading = false) } }
            .onFailure { e -> _uiState.update { it.copy(error = e.message, assignRoomLoading = false, assignRoomDialogBooking = null) } }
        }
    }

    fun closeAssignRoom() = _uiState.update { it.copy(assignRoomDialogBooking = null, availableRoomsForAssign = emptyList()) }

    fun unassignRoom() {
        val booking = _uiState.value.assignRoomDialogBooking ?: return
        launchWithGlobalLoading {
            runCatching { bookingRepo.update(booking.copy(roomInstanceId = "", roomNumber = "")) }
                .onSuccess { _uiState.update { it.copy(
                    assignRoomDialogBooking = null,
                    availableRoomsForAssign = emptyList(),
                    operationMessage = "Room unassigned from ${booking.guestName}",
                ) } }
                .onFailure { e -> _uiState.update { it.copy(error = e.message) } }
        }
    }

    fun confirmAssignRoom(room: RoomInstance) {
        val booking = _uiState.value.assignRoomDialogBooking ?: return
        launchWithGlobalLoading {
            runCatching {
                bookingRepo.assignRoomTransaction(booking.id, room.id, room.roomNumber)
            }
            .onSuccess {
                _uiState.update { it.copy(
                    assignRoomDialogBooking = null,
                    availableRoomsForAssign = emptyList(),
                    operationMessage = "Room ${room.roomNumber} assigned to ${booking.guestName}",
                )}
            }
            .onFailure { e -> _uiState.update { it.copy(error = e.message) } }
        }
    }

    // ── Cancel + refund (via cancelBookingByReceptionHttp Cloud Function) ─────

    fun promptCancelBooking(booking: Booking) {
        openCancelDialogFor(booking, fromGroupEntry = false)
    }

    /** Group-level Cancel button on a multi-room booking row. */
    fun promptCancelGroupBooking(group: List<Booking>) {
        val primary = group.firstOrNull { it.status == "CONFIRMED" } ?: return
        openCancelDialogFor(primary, fromGroupEntry = true)
    }

    fun dismissCancelDialog() {
        _uiState.update { it.copy(cancelDialog = null) }
    }

    fun onCancelReasonChanged(reason: String) {
        _uiState.update { s ->
            val d = s.cancelDialog ?: return@update s
            s.copy(cancelDialog = d.copy(reason = reason.take(500), error = null))
        }
    }

    fun onCancelRefundModeChanged(mode: ReceptionRefundMode) {
        _uiState.update { s ->
            val d = s.cancelDialog ?: return@update s
            // Compute the new amount-field content based on the picked mode + whether
            // this is the manual flow. POLICY → policy preview; FULL → total advance;
            // FIXED → blank so reception types it.
            val newInput = when {
                d.isManual && mode == ReceptionRefundMode.POLICY ->
                    d.policyPreviewPaise?.let { paiseToRupeesField(it) } ?: ""
                d.isManual && mode == ReceptionRefundMode.FULL ->
                    paiseToRupeesField(d.totalAdvancePaise)
                d.isManual && mode == ReceptionRefundMode.FIXED -> ""
                // Razorpay flow: only FIXED uses the field — clear it on mode switch.
                mode != ReceptionRefundMode.FIXED -> ""
                else -> d.refundAmountRupeesInput
            }
            // Changing the mode changes the target total → clear the Cash/Bank split so it can't
            // silently disagree with the new amount.
            s.copy(cancelDialog = d.copy(
                refundMode = mode, refundAmountRupeesInput = newInput,
                refundCashInput = "", refundBankInput = "", error = null,
            ))
        }
    }

    fun onCancelRefundAmountChanged(text: String) {
        // Accept digits + at most one '.' + max 2 decimal places.
        val sanitized = buildString {
            var seenDot = false
            var decimals = 0
            for (c in text) {
                when {
                    c.isDigit() -> {
                        if (seenDot) {
                            if (decimals < 2) { append(c); decimals++ }
                        } else append(c)
                    }
                    c == '.' && !seenDot -> { append(c); seenDot = true }
                }
            }
        }
        _uiState.update { s ->
            val d = s.cancelDialog ?: return@update s
            // FIXED amount drives the target total → clear the split when it changes.
            s.copy(cancelDialog = d.copy(refundAmountRupeesInput = sanitized, refundCashInput = "", refundBankInput = "", error = null))
        }
    }

    private fun sanitizeMoney(text: String): String = buildString {
        var seenDot = false
        var decimals = 0
        for (c in text) when {
            c.isDigit() -> if (seenDot) { if (decimals < 2) { append(c); decimals++ } } else append(c)
            c == '.' && !seenDot -> { append(c); seenDot = true }
        }
    }

    /** App-booking refund: Cash leg of the ledger/till split. */
    fun onRefundCashChanged(text: String) = _uiState.update { s ->
        val d = s.cancelDialog ?: return@update s
        s.copy(cancelDialog = d.copy(refundCashInput = sanitizeMoney(text), error = null))
    }

    /** App-booking refund: Bank leg of the ledger/till split. */
    fun onRefundBankChanged(text: String) = _uiState.update { s ->
        val d = s.cancelDialog ?: return@update s
        s.copy(cancelDialog = d.copy(refundBankInput = sanitizeMoney(text), error = null))
    }

    /** Reception picks how the cash/bank transfer happens (manual flow only). */
    fun onPaidViaChanged(method: ManualRefundMethod) {
        _uiState.update { s ->
            val d = s.cancelDialog ?: return@update s
            s.copy(cancelDialog = d.copy(paidVia = method, error = null))
        }
    }

    private fun paiseToRupeesField(paise: Long): String =
        if (paise % 100L == 0L) (paise / 100L).toString() else "%.2f".format(paise / 100.0)

    fun confirmCancelByReception() {
        val dialog = _uiState.value.cancelDialog ?: return
        val booking = dialog.primary

        // Guard: reason length.
        if (dialog.reason.trim().length !in 10..500) {
            _uiState.update { it.copy(cancelDialog = dialog.copy(
                error = CancelDialogError("Reason required", "Please enter at least 10 characters.", retrySafe = false)
            ))}
            return
        }

        val staffId = AppContext.currentManager.ifBlank { "reception_unknown" }

        if (dialog.isManual) {
            confirmManualRefund(dialog, staffId)
        } else {
            confirmRazorpayRefund(dialog, staffId)
        }
    }

    private fun confirmRazorpayRefund(dialog: CancelDialogState, staffId: String) {
        // Razorpay flow: only FIXED mode uses the amount field; POLICY/FULL are server-computed.
        var fixedPaise: Long? = null
        if (dialog.refundMode == ReceptionRefundMode.FIXED) {
            val paise = rupeesInputToPaise(dialog.refundAmountRupeesInput)
            if (paise == null || paise <= 0L) {
                _uiState.update { it.copy(cancelDialog = dialog.copy(
                    error = CancelDialogError("Invalid amount", "Enter an amount greater than ₹0.", retrySafe = false)
                ))}
                return
            }
            if (paise > dialog.totalAdvancePaise) {
                _uiState.update { it.copy(cancelDialog = dialog.copy(
                    error = CancelDialogError(
                        "Amount too high",
                        "Must be at most ₹${"%.2f".format(dialog.totalAdvancePaise / 100.0)}.",
                        retrySafe = false,
                    )
                ))}
                return
            }
            fixedPaise = paise
        }

        // Cash/Bank split for the LEDGER + Firestore till recording. The Razorpay online refund
        // below is untouched — this only mirrors the refund into our books. Must total exactly the
        // refund amount (paise-exact) when a refund is due.
        val refundPaise = dialog.effectiveRefundPaise
        val cashPaise = dialog.refundCashInput.toDoubleOrNull()?.let { Math.round(it * 100.0) } ?: 0L
        val bankPaise = dialog.refundBankInput.toDoubleOrNull()?.let { Math.round(it * 100.0) } ?: 0L
        if (refundPaise > 0L && cashPaise + bankPaise != refundPaise) {
            _uiState.update { it.copy(cancelDialog = dialog.copy(
                error = CancelDialogError(
                    "Split doesn't match",
                    "Cash + Bank must total ₹${"%.2f".format(refundPaise / 100.0)} (the refund amount).",
                    retrySafe = false,
                )
            ))}
            return
        }

        val booking = dialog.primary
        val refundKey = booking.groupBookingId.ifBlank { booking.id }
        val reason = dialog.reason.trim()
        val guestName = booking.guestName
        _uiState.update { it.copy(cancelDialog = dialog.copy(isLoading = true, error = null)) }

        viewModelScope.launch {
            val result = runCatching {
                bookingRepo.cancelByReception(
                    userId = booking.userId,
                    groupBookingId = booking.groupBookingId.ifBlank { booking.id },
                    reason = reason,
                    refundMode = dialog.refundMode,
                    fixedRefundPaise = fixedPaise,
                    cancelledByReceptionUserId = staffId,
                )
            }
            result.onSuccess { res ->
                // Mirror the refund into our ledger + Firestore till, but ONLY when the server
                // actually refunded the amount the split was built against (so the numbers match).
                var ledgerWarning = false
                if (refundPaise > 0L) {
                    if (res.totalRefundPaise == refundPaise) {
                        runCatching {
                            AccountingRepository.postBookingRefund(
                                refundKey = refundKey,
                                cashAmount = cashPaise / 100.0,
                                bankAmount = bankPaise / 100.0,
                                reason = reason,
                                guestName = guestName,
                            )
                        }.onFailure { ledgerWarning = true }
                            .onSuccess { if (it.isFailure) ledgerWarning = true }
                    } else {
                        ledgerWarning = true   // server refunded a different amount than the split
                    }
                }
                _uiState.update {
                    it.copy(
                        cancelDialog = null,
                        operationMessage = buildSuccessMessage(res, guestName) +
                            if (ledgerWarning) " (Ledger/till refund not recorded — record it manually.)" else "",
                    )
                }
            }.onFailure { e ->
                val err = mapCancelError(e)
                _uiState.update { s ->
                    val d = s.cancelDialog ?: return@update s
                    s.copy(cancelDialog = d.copy(isLoading = false, error = err))
                }
            }
        }
    }

    private fun confirmManualRefund(dialog: CancelDialogState, staffId: String) {
        // Manual flow: no refund amount, no payout method — refund is handled
        // entirely off-system at the desk. We only record the cancellation + reason.
        val booking = dialog.primary
        _uiState.update { it.copy(cancelDialog = dialog.copy(isLoading = true, error = null)) }

        viewModelScope.launch {
            runCatching {
                bookingRepo.cancelByReceptionManual(
                    bookingIds = dialog.groupBookings.map { it.id },
                    reason = dialog.reason.trim(),
                    cancelledByReceptionUserId = staffId,
                )
            }.onSuccess {
                _uiState.update {
                    it.copy(
                        cancelDialog = null,
                        operationMessage = "Booking for ${booking.guestName} cancelled. Handle the refund manually at the desk.",
                    )
                }
            }.onFailure { e ->
                _uiState.update { s ->
                    val d = s.cancelDialog ?: return@update s
                    s.copy(cancelDialog = d.copy(
                        isLoading = false,
                        error = CancelDialogError("Couldn't cancel", e.message ?: "Firestore write failed.", retrySafe = true),
                    ))
                }
            }
        }
    }

    private fun rupeesInputToPaise(input: String): Long? {
        val rupees = input.toDoubleOrNull() ?: return null
        return (rupees * 100.0).toLong()
    }

    private fun openCancelDialogFor(booking: Booking, fromGroupEntry: Boolean) {
        // Resolve the full group (the Cloud Function will cancel every booking sharing this groupBookingId).
        val all = _uiState.value.bookings
        val group = if (booking.groupBookingId.isNotBlank()) {
            all.filter { it.groupBookingId == booking.groupBookingId && it.status == "CONFIRMED" }
                .ifEmpty { listOf(booking) }
        } else {
            listOf(booking)
        }

        // Cheap synchronous checks first — show the dialog optimistically while the
        // suspend active-stay check runs in the background. Late "checked-in" failure
        // surfaces as a dialog error before the function fires.
        val syncError = syncPreflight(group)
        if (syncError != null) {
            _uiState.update { it.copy(operationMessage = null, error = syncError) }
            return
        }

        _uiState.update {
            it.copy(cancelDialog = CancelDialogState(
                primary = booking,
                groupBookings = group,
            ))
        }

        viewModelScope.launch {
            val active = runCatching { stayRepo.getActive(AppContext.hotelId) }.getOrElse { emptyList() }
            if (group.any { b -> active.any { it.bookingId == b.id && it.status == "ACTIVE" } }) {
                _uiState.update {
                    val d = it.cancelDialog ?: return@update it
                    it.copy(cancelDialog = d.copy(error = CancelDialogError(
                        "Cannot cancel",
                        "Guest is already checked in — check out the guest first.",
                        retrySafe = false,
                    )))
                }
            }
        }

        // Compute the POLICY-mode refund preview in the background so staff can
        // see the amount before committing. Mirrors functions/src/cancellationRefundCalculator.ts.
        viewModelScope.launch {
            val previewPaise = runCatching { computePolicyRefundPaise(group) }.getOrDefault(0L)
            _uiState.update { s ->
                val d = s.cancelDialog ?: return@update s
                // On the manual flow, when POLICY is still the active selection and the
                // amount field is empty, auto-fill it now that the preview has loaded.
                val autoFill = d.isManual
                    && d.refundMode == ReceptionRefundMode.POLICY
                    && d.refundAmountRupeesInput.isBlank()
                val newInput = if (autoFill) paiseToRupeesField(previewPaise) else d.refundAmountRupeesInput
                s.copy(cancelDialog = d.copy(
                    policyPreviewPaise = previewPaise,
                    refundAmountRupeesInput = newInput,
                ))
            }
        }
    }

    /**
     * Client-side port of `functions/src/cancellationRefundCalculator.ts` —
     * computes the POLICY-mode refund total in paise across [group]. One
     * Firestore read per unique room category. Result is a *preview only*; the
     * server recomputes against the live policies at refund time.
     */
    private suspend fun computePolicyRefundPaise(group: List<com.example.dreamland_reception.data.model.Booking>): Long =
        kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            val fs = com.example.dreamland_reception.data.repository.FirestoreRepositorySupport.get()
            val now = System.currentTimeMillis()
            val hourMs = 3_600_000L
            // Fetch each unique roomCategoryId once.
            val categoryIds = group.map { it.roomCategoryId }.toSet()
            val policies: Map<String, Triple<Boolean, Long, Double>> = categoryIds.mapNotNull { id ->
                val snap = runCatching { fs.collection("rooms").document(id).get().get() }.getOrNull()
                if (snap == null || !snap.exists()) return@mapNotNull null
                val freeCancellation = snap.getBoolean("freeCancellation") ?: false
                val policy = snap.get("cancellationPolicy") as? Map<*, *>
                val freeBefore = (policy?.get("freeBefore") as? Number)?.toLong() ?: 0L
                val refundPercent = (policy?.get("refundPercent") as? Number)?.toDouble() ?: 0.0
                id to Triple(freeCancellation, freeBefore, refundPercent)
            }.toMap()

            var total = 0L
            for (b in group) {
                // Walk-in bookings store advance as Double rupees; fall back so the
                // calculator works for both app-paid and hotel-side bookings.
                val advance = if (b.advancePaidAmountPaise > 0L) b.advancePaidAmountPaise
                              else (b.advancePaidAmount * 100.0).toLong()
                if (advance <= 0L) continue
                val p = policies[b.roomCategoryId] ?: continue
                val (freeCancellation, freeBefore, refundPercent) = p
                val hoursUntilCheckIn = (b.checkIn.time - now) / hourMs
                val refund = when {
                    freeCancellation && hoursUntilCheckIn >= freeBefore -> advance
                    refundPercent > 0.0 -> kotlin.math.floor(advance * refundPercent / 100.0).toLong()
                    else -> 0L
                }
                total += refund
            }
            total
        }

    private fun syncPreflight(group: List<Booking>): String? {
        if (group.any { it.status != "CONFIRMED" }) {
            return "Only CONFIRMED bookings can be cancelled."
        }
        // Note: a missing paymentOrderId is NOT a blocker — those bookings are hotel-side
        // (walk-in) and route to the manual-refund branch of the cancel dialog.
        val now = System.currentTimeMillis()
        val graceMs = 10L * 60L * 1000L
        if (group.any { b ->
                val locked = b.cancellationLockedAt?.time ?: 0L
                locked > 0L && now - locked < graceMs
            }) {
            return "Guest is currently cancelling from the app. Wait 10 minutes and retry."
        }
        return null
    }

    private fun buildSuccessMessage(res: CancelBookingResult, guestName: String): String {
        val rupees = "%.2f".format(res.totalRefundPaise / 100.0)
        return when {
            res.totalRefundPaise > 0 && res.refundId.isNotBlank() ->
                "Cancelled for $guestName. Refund of ₹$rupees initiated (Razorpay: ${res.refundId})."
            res.totalRefundPaise > 0 ->
                "Cancelled for $guestName. Refund of ₹$rupees initiated."
            else ->
                "Cancelled for $guestName. No refund per policy."
        }
    }

    private fun mapCancelError(e: Throwable): CancelDialogError {
        if (e !is CloudFunctionClient.CancelByReceptionException) {
            return CancelDialogError("Network error", e.message ?: "Could not reach server.", retrySafe = true)
        }
        // Parse {code, message} from the server. Fall back to raw body on parse failure.
        val (code, msg) = parseCodeAndMessage(e.body)
        return when (e.httpStatus) {
            401 -> CancelDialogError("Auth failed", "Service account token rejected. Contact admin.", retrySafe = false)
            403 -> CancelDialogError("Not allowed", "This reception machine is not authorized for cancellations.", retrySafe = false)
            400 -> CancelDialogError("Invalid input", msg.ifBlank { "Check the request fields." }, retrySafe = false)
            404 -> CancelDialogError("Not found", "Booking or payment record missing.", retrySafe = false)
            412 -> CancelDialogError("Cannot cancel", humanizeBlock(msg, code), retrySafe = false)
            429 -> CancelDialogError("Slow down", "Too many cancellations this minute. Wait 60s.", retrySafe = true)
            500, 502, 503, 504 -> CancelDialogError("Refund failed", "Razorpay rejected the refund. Safe to retry.", retrySafe = true)
            else -> CancelDialogError("Error ${e.httpStatus}", msg.ifBlank { "Unexpected error." }, retrySafe = false)
        }
    }

    private fun parseCodeAndMessage(body: String): Pair<String, String> {
        return runCatching {
            val o = com.google.gson.JsonParser.parseString(body).asJsonObject
            (o.get("code")?.asString.orEmpty()) to (o.get("message")?.asString.orEmpty())
        }.getOrElse { "" to body }
    }

    private fun humanizeBlock(message: String, code: String): String = when (message) {
        "ALREADY_CHECKED_IN" -> "Guest is already checked in. Check out first."
        "ALREADY_CANCELLED"  -> "Booking is already cancelled."
        "PAST_CHECKIN"       -> "Check-in date has passed. Pick Full or Custom refund mode to override."
        "LOCK_HELD"          -> "Guest is cancelling from the app. Wait 10 minutes."
        "no_payment_link"    -> "No online payment on this booking — handle refund manually."
        "PENDING_PAYMENT"    -> "Booking payment hasn't completed yet."
        else -> message.ifBlank { code }
    }

    // ── No-show ───────────────────────────────────────────────────────────────

    fun promptMarkNoShow(booking: Booking) = _uiState.update { it.copy(noShowConfirmBooking = booking) }
    fun dismissNoShow() = _uiState.update { it.copy(noShowConfirmBooking = null) }

    fun promptMarkGroupNoShow(group: List<Booking>) {
        val checkedInIds = _uiState.value.activeStaysByRoom.values
            .map { it.bookingId }.filter { it.isNotBlank() }.toSet()
        val tomorrowStart = Date(localTodayUtcMidnight().time + 86_400_000L)
        val toMark = group.filter { b ->
            b.status == "CONFIRMED" && b.id !in checkedInIds && b.checkIn.before(tomorrowStart)
        }
        if (toMark.isEmpty()) return
        _uiState.update { it.copy(groupNoShowBookings = toMark, groupNoShowSelectedIds = toMark.map { it.id }.toSet()) }
    }

    fun dismissGroupNoShow() = _uiState.update { it.copy(groupNoShowBookings = null, groupNoShowSelectedIds = emptySet()) }

    fun toggleGroupNoShowBooking(bookingId: String) {
        _uiState.update { s ->
            val ids = s.groupNoShowSelectedIds
            s.copy(groupNoShowSelectedIds = if (bookingId in ids) ids - bookingId else ids + bookingId)
        }
    }

    fun confirmGroupNoShow() {
        val group = _uiState.value.groupNoShowBookings ?: return
        val selectedIds = _uiState.value.groupNoShowSelectedIds
        val toMark = group.filter { it.id in selectedIds }
        if (toMark.isEmpty()) { _uiState.update { it.copy(groupNoShowBookings = null, groupNoShowSelectedIds = emptySet()) }; return }
        _uiState.update { it.copy(groupNoShowBookings = null, groupNoShowSelectedIds = emptySet()) }
        viewModelScope.launch {
            runCatching {
                val markedAt = Date()
                toMark.forEach { booking ->
                    bookingRepo.markNoShow(booking.id, markedAt, if (booking.advancePaidAmount > 0) "PENDING" else "", "")
                }
                val primary = toMark.first()
                val totalAdvance = toMark.sumOf { it.advancePaidAmount }
                if (totalAdvance > 0) {
                    // Show ONE refund dialog for the combined advance across all marked bookings
                    _uiState.update { it.copy(
                        noShowRefundDialog = NoShowRefundDialogState(
                            booking = primary.copy(advancePaidAmount = totalAdvance, noShowMarkedAt = markedAt, noShowRefundStatus = "PENDING"),
                        ),
                        operationMessage = "${toMark.size} bookings for ${primary.guestName} marked as no-show",
                    ) }
                } else {
                    _uiState.update { it.copy(operationMessage = "${toMark.size} bookings for ${primary.guestName} marked as no-show") }
                }
            }.onFailure { e ->
                _uiState.update { it.copy(error = e.message ?: "Failed to mark group as no-show") }
            }
        }
    }

    fun confirmNoShow() {
        val booking = _uiState.value.noShowConfirmBooking ?: return
        _uiState.update { it.copy(noShowConfirmBooking = null) }
        viewModelScope.launch {
            runCatching {
                val markedAt = Date()
                if (booking.advancePaidAmount <= 0) {
                    bookingRepo.markNoShow(booking.id, markedAt, "", "")
                    _uiState.update { it.copy(operationMessage = "Booking for ${booking.guestName} marked as no-show") }
                } else {
                    // Write NO_SHOW immediately, then surface the refund-outcome dialog
                    bookingRepo.markNoShow(booking.id, markedAt, "PENDING", "")
                    _uiState.update {
                        it.copy(
                            noShowRefundDialog = NoShowRefundDialogState(
                                booking = booking.copy(noShowMarkedAt = markedAt, noShowRefundStatus = "PENDING"),
                            ),
                            operationMessage = "Booking for ${booking.guestName} marked as no-show",
                        )
                    }
                }
            }.onFailure { e ->
                _uiState.update { it.copy(error = e.message ?: "Failed to mark as no-show") }
            }
        }
    }

    fun onNoShowRefundStatus(status: String) = _uiState.update { s ->
        s.copy(noShowRefundDialog = s.noShowRefundDialog?.copy(refundStatus = status))
    }
    fun onNoShowRefundNote(note: String) = _uiState.update { s ->
        s.copy(noShowRefundDialog = s.noShowRefundDialog?.copy(note = note))
    }
    fun dismissNoShowRefundDialog() = _uiState.update { it.copy(noShowRefundDialog = null) }

    fun submitNoShowRefundOutcome() {
        val d = _uiState.value.noShowRefundDialog ?: return
        _uiState.update { it.copy(noShowRefundDialog = d.copy(isSaving = true, error = null)) }
        viewModelScope.launch {
            runCatching {
                bookingRepo.markNoShow(d.booking.id, d.booking.noShowMarkedAt ?: Date(), d.refundStatus, d.note.trim())
            }.onSuccess {
                _uiState.update { it.copy(noShowRefundDialog = null, operationMessage = "Refund outcome recorded for ${d.booking.guestName}") }
            }.onFailure { e ->
                _uiState.update { s -> s.copy(noShowRefundDialog = d.copy(isSaving = false, error = e.message ?: "Failed to save")) }
            }
        }
    }

    // ── Add booking dialog ────────────────────────────────────────────────────

    fun openAddBookingDialog() = _uiState.update { it.copy(showAddBookingDialog = true) }
    fun closeAddBookingDialog() = _uiState.update { it.copy(
        showAddBookingDialog = false,
        addBookingLoading = false,
        newBookingAvailableRooms = emptyList(),
        newBookingCategoryAvailability = emptyMap(),
    ) }

    fun computeAvailableRoomsForNewBooking(checkIn: Date, checkOut: Date) {
        val ciTime = _uiState.value.hotelCheckInTime
        val coTime = _uiState.value.hotelCheckOutTime
        val available = bookableInstancesForNewBooking(
            _uiState.value.rooms, _uiState.value.bookings, _uiState.value.activeStays,
            checkIn, checkOut, ciTime, coTime,
        )
        val allCatIds = _uiState.value.rooms.map { it.categoryId }.filter { it.isNotBlank() }.toSet()
        val perCat = available.filter { it.status != "CLEANING" }.groupingBy { it.categoryId }.eachCount()
        val availabilityMap = allCatIds.associateWith { catId -> perCat[catId] ?: 0 }
        _uiState.update { it.copy(
            newBookingAvailableRooms = available,
            newBookingCategoryAvailability = availabilityMap,
        ) }
    }

    private fun loadBookingSources() {
        viewModelScope.launch {
            bookingSourceRepo.listen()
                .catch { e -> _uiState.update { it.copy(error = "Sources: ${e.message}") } }
                .collect { sources -> _uiState.update { it.copy(bookingSources = sources) } }
        }
    }

    fun addBookingSource(name: String) {
        viewModelScope.launch {
            runCatching { bookingSourceRepo.add(name) }
                .onFailure { e -> _uiState.update { it.copy(error = "Failed to add source: ${e.message}") } }
        }
    }

    fun addBooking(
        guestName: String,
        guestPhone: String,
        rooms: List<RoomCategoryEntry>,
        checkIn: Date,
        checkOut: Date,
        adults: Int,
        children: Int,
        totalAmount: Double,
        advancePaidAmount: Double,
        advancePaymentMethod: String = "CASH",
        source: String,
        notes: String,
        breakfastIncluded: Boolean = false,
        breakfastPricePerDay: Double = 0.0,
    ) {
        _uiState.update { it.copy(addBookingLoading = true) }
        launchWithGlobalLoading {
            val groupId = if (rooms.size > 1) UUID.randomUUID().toString() else ""
            val bookings = rooms.map { entry ->
                Booking(
                    hotelId = AppContext.hotelId,
                    hotelName = AppContext.hotelName,
                    guestName = guestName.trim(),
                    guestPhone = guestPhone.trim().let { normalizePhoneE164(it) ?: it },
                    roomCategoryId = entry.categoryId,
                    roomCategoryName = entry.categoryName,
                    roomInstanceId = entry.selectedInstanceId,
                    roomNumber = entry.selectedRoomNumber,
                    checkIn = checkIn.toMidnightUtc(),
                    checkOut = checkOut.toMidnightUtc(),
                    adults = adults,
                    children = children,
                    status = "CONFIRMED",
                    source = source.trim().ifBlank { "APP" },
                    totalAmount = totalAmount,
                    advancePaidAmount = advancePaidAmount,
                    advancePaymentMethod = advancePaymentMethod,
                    breakfastIncluded = breakfastIncluded,
                    breakfastPricePerDay = breakfastPricePerDay,
                    notes = notes.trim(),
                    groupBookingId = groupId,
                    createdAt = Date(),
                )
            }
            runCatching { bookings.forEach { bookingRepo.add(it) } }
                .onSuccess {
                    _uiState.update {
                        it.copy(
                            showAddBookingDialog = false,
                            addBookingLoading = false,
                            newBookingAvailableRooms = emptyList(),
                            newBookingCategoryAvailability = emptyMap(),
                            operationMessage = if (bookings.size == 1) "Booking added for ${guestName.trim()}"
                                              else "${bookings.size} bookings added for ${guestName.trim()}",
                        )
                    }
                }
                .onFailure { e -> _uiState.update { it.copy(addBookingLoading = false, error = e.message) } }
        }
    }

    // ── Dummy booking (no auto-assign) ────────────────────────────────────────

    fun addDummyBooking() {
        launchWithGlobalLoading {
            val names = listOf("Rahul Sharma", "Priya Singh", "Amit Patel", "Neha Gupta", "Vikram Mehta", "Anjali Verma", "Sanjay Kumar", "Deepa Nair", "Arjun Reddy", "Kavitha Rao")
            val cal = Calendar.getInstance()
            val checkIn = cal.time
            cal.add(Calendar.DAY_OF_YEAR, (1..2).random())
            val checkOut = cal.time
            val total = (2000..8000).random().toDouble()
            val categoryEntry = _uiState.value.categoryNames.entries.randomOrNull()
            val booking = Booking(
                hotelId = AppContext.hotelId,
                guestName = names.random(),
                guestPhone = "9${(100_000_000..999_999_999).random()}",
                roomCategoryId = categoryEntry?.key ?: "",
                roomCategoryName = categoryEntry?.value ?: "",
                checkIn = checkIn,
                checkOut = checkOut,
                adults = (1..3).random(),
                children = (0..1).random(),
                status = "CONFIRMED",
                source = "APP",
                totalAmount = total,
                advancePaidAmount = (500..minOf(2000, total.toInt())).random().toDouble(),
                createdAt = Date(),
            )
            runCatching { bookingRepo.add(booking) }
                .onSuccess { _uiState.update { it.copy(operationMessage = "Dummy booking added for ${booking.guestName}") } }
                .onFailure { e -> _uiState.update { it.copy(error = "Failed to add booking: ${e.message}") } }
        }
    }
}
