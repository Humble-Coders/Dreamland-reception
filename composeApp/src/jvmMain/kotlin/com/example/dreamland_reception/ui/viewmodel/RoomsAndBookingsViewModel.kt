package com.example.dreamland_reception.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.dreamland_reception.data.AppContext
import com.example.dreamland_reception.data.model.Booking
import com.example.dreamland_reception.data.model.Hotel
import com.example.dreamland_reception.data.model.RoomInstance
import com.example.dreamland_reception.data.model.Stay
import com.example.dreamland_reception.data.repository.BookingRepository
import com.example.dreamland_reception.data.repository.FirestoreBookingRepository
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
import java.util.Calendar
import java.util.Date

// ── Filter enums ──────────────────────────────────────────────────────────────

enum class BookingDateFilter { ALL, TODAY, UPCOMING }

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
    // Room categories: categoryId -> display name (loaded from `rooms` collection)
    val categoryNames: Map<String, String> = emptyMap(),
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
    // Cancel confirmation
    val cancelConfirmBooking: Booking? = null,
    // General error / snackbar
    val error: String? = null,
    val operationMessage: String? = null,
    // Room panel filters
    val roomCategoryFilter: String = "",
    val roomStatusFilter: String = "",
    // Hotel times — populated from Firestore on init; empty until loaded
    val hotelCheckInTime: String = "",
    val hotelCheckOutTime: String = "",
    // No-show confirmation
    val noShowConfirmBooking: Booking? = null,
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
            bookings.filter { it.roomInstanceId == id && it.status == "CONFIRMED" }
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
            val todayStart = Calendar.getInstance().apply {
                set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0)
                set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
            }.time
            val tomorrowStart = Calendar.getInstance().apply {
                add(Calendar.DAY_OF_YEAR, 1)
                set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0)
                set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
            }.time

            return bookings.filter { booking ->
                val matchesSearch = q.isEmpty() || booking.guestName.lowercase().contains(q)
                val matchesStatus = bookingStatusFilter == null || booking.status == bookingStatusFilter
                val matchesDate = when (bookingDateFilter) {
                    BookingDateFilter.TODAY -> booking.checkIn >= todayStart && booking.checkIn < tomorrowStart
                    BookingDateFilter.UPCOMING -> booking.checkIn >= todayStart
                    BookingDateFilter.ALL -> true
                }
                matchesSearch && matchesStatus && matchesDate
            }.let { list ->
                if (bookingSortOrder == "NEWEST") list.sortedByDescending { it.checkIn }
                else list.sortedBy { it.checkIn }
            }
        }

    fun isCheckInToday(booking: Booking): Boolean {
        val todayStart = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
        }.time
        val tomorrowStart = Calendar.getInstance().apply {
            add(Calendar.DAY_OF_YEAR, 1)
            set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
        }.time
        return booking.checkIn >= todayStart && booking.checkIn < tomorrowStart
    }
}

// ── ViewModel ─────────────────────────────────────────────────────────────────

class RoomsAndBookingsViewModel(
    private val bookingRepo: BookingRepository = FirestoreBookingRepository,
    private val roomInstanceRepo: RoomInstanceRepository = FirestoreRoomInstanceRepository,
    private val roomRepo: RoomRepository = FirestoreRoomRepository,
    private val stayRepo: StayRepository = FirestoreStayRepository,
    private val hotelRepo: HotelRepository = FirestoreHotelRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(RoomsAndBookingsUiState())
    val uiState: StateFlow<RoomsAndBookingsUiState> = _uiState.asStateFlow()

    private var hotel: Hotel? = null

    init {
        startListeners()
        loadCategoryNames()
        loadHotel()
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
                .collect { bookings -> _uiState.update { it.copy(bookings = bookings, bookingsLoading = false) } }
        }
        viewModelScope.launch {
            stayRepo.listenActive(hotelId)
                .catch { e -> _uiState.update { it.copy(error = "Stays: ${e.message}") } }
                .collect { stays ->
                    _uiState.update { it.copy(activeStaysByRoom = stays.associateBy { s -> s.roomNumber }) }
                }
        }
    }

    private fun loadCategoryNames() {
        viewModelScope.launch {
            runCatching { roomRepo.getByHotel(AppContext.hotelId) }
                .onSuccess { rooms ->
                    // Room.id = categoryId, Room.type = display name (mapped from Firestore "name" field)
                    val names = rooms.associate { it.id to it.type.ifBlank { "Room" } }
                    _uiState.update { it.copy(categoryNames = names) }
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

    fun markCleaningComplete(room: RoomInstance) {
        launchWithGlobalLoading {
            runCatching { roomInstanceRepo.updateStatus(room.id, "AVAILABLE", null) }
                .onSuccess { _uiState.update { it.copy(operationMessage = "Room ${room.roomNumber} marked as Available") } }
                .onFailure { e -> _uiState.update { it.copy(error = e.message) } }
        }
    }

    // ── Assign room ───────────────────────────────────────────────────────────

    fun openAssignRoom(booking: Booking) {
        viewModelScope.launch {
            _uiState.update { it.copy(assignRoomDialogBooking = booking, assignRoomLoading = true, availableRoomsForAssign = emptyList()) }
            runCatching {
                val candidates = roomInstanceRepo.getAll()
                    .filter { it.hotelId == AppContext.hotelId && it.status == "AVAILABLE" }
                val confirmedBookings = _uiState.value.bookings.filter { it.status == "CONFIRMED" }
                dateConflictFilter(candidates, confirmedBookings, booking.checkIn, booking.checkOut, excludeBookingId = booking.id)
                    .sortedWith(compareBy({ it.roomNumber.length }, { it.roomNumber }))
            }
            .onSuccess { rooms -> _uiState.update { it.copy(availableRoomsForAssign = rooms, assignRoomLoading = false) } }
            .onFailure { e -> _uiState.update { it.copy(error = e.message, assignRoomLoading = false, assignRoomDialogBooking = null) } }
        }
    }

    fun closeAssignRoom() = _uiState.update { it.copy(assignRoomDialogBooking = null, availableRoomsForAssign = emptyList()) }

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

    // ── Cancel booking ────────────────────────────────────────────────────────

    fun promptCancelBooking(booking: Booking) = _uiState.update { it.copy(cancelConfirmBooking = booking) }
    fun dismissCancelBooking() = _uiState.update { it.copy(cancelConfirmBooking = null) }

    fun confirmCancelBooking() {
        val booking = _uiState.value.cancelConfirmBooking ?: return
        launchWithGlobalLoading {
            runCatching {
                val active = stayRepo.getActive(AppContext.hotelId)
                if (active.any { it.roomInstanceId == booking.roomInstanceId && it.status == "ACTIVE" }) {
                    throw Exception("Guest is currently checked in — check out the guest first")
                }
                bookingRepo.cancelWithTransaction(booking.id)
            }
            .onSuccess { _uiState.update { it.copy(cancelConfirmBooking = null, operationMessage = "Booking for ${booking.guestName} cancelled") } }
            .onFailure { e -> _uiState.update { it.copy(cancelConfirmBooking = null, error = e.message) } }
        }
    }

    // ── No-show ───────────────────────────────────────────────────────────────

    fun promptMarkNoShow(booking: Booking) = _uiState.update { it.copy(noShowConfirmBooking = booking) }
    fun dismissNoShow() = _uiState.update { it.copy(noShowConfirmBooking = null) }

    fun confirmNoShow() {
        val booking = _uiState.value.noShowConfirmBooking ?: return
        launchWithGlobalLoading {
            runCatching { bookingRepo.update(booking.copy(status = "NO_SHOW")) }
            .onSuccess { _uiState.update { it.copy(noShowConfirmBooking = null, operationMessage = "Booking for ${booking.guestName} marked as no-show") } }
            .onFailure { e -> _uiState.update { it.copy(noShowConfirmBooking = null, error = e.message) } }
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
