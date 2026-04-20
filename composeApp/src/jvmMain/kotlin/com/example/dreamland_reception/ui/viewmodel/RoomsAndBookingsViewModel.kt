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
    // Assign-room dialog
    val assignRoomDialogBooking: Booking? = null,
    val availableRoomsForAssign: List<RoomInstance> = emptyList(),
    val assignRoomLoading: Boolean = false,
    // Cancel confirmation
    val cancelConfirmBooking: Booking? = null,
    // General error / snackbar
    val error: String? = null,
    val operationMessage: String? = null,
) {
    val isInitialLoading: Boolean get() = roomsLoading && rooms.isEmpty() && bookingsLoading && bookings.isEmpty()

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
            }.sortedBy { it.checkIn }
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
    private val autoAssignAttempted = mutableSetOf<String>()

    init {
        startListeners()
        loadCategoryNames()
        loadHotel()
    }

    private fun loadHotel() {
        viewModelScope.launch {
            runCatching { hotelRepo.getById(AppContext.hotelId) }
                .onSuccess { hotel = it }
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
                    _uiState.update { it.copy(bookings = bookings, bookingsLoading = false) }
                    if (hotel?.autoAssignRoom == true) {
                        bookings
                            .filter { it.status == "CONFIRMED" && it.roomInstanceId.isBlank() && it.id !in autoAssignAttempted }
                            .forEach { booking ->
                                autoAssignAttempted.add(booking.id)
                                viewModelScope.launch {
                                    val assigned = autoAssignRoom(booking.id, booking)
                                    if (assigned != null) {
                                        _uiState.update { it.copy(operationMessage = "Room ${assigned.roomNumber} auto-assigned to ${booking.guestName}") }
                                    }
                                }
                            }
                    }
                }
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

    fun setTab(tab: Int) = _uiState.update { it.copy(selectedTab = tab, searchQuery = "") }
    fun setSearchQuery(q: String) = _uiState.update { it.copy(searchQuery = q) }
    fun setBookingStatusFilter(status: String?) = _uiState.update { it.copy(bookingStatusFilter = status) }
    fun setBookingDateFilter(filter: BookingDateFilter) = _uiState.update { it.copy(bookingDateFilter = filter) }
    fun clearError() = _uiState.update { it.copy(error = null) }
    fun clearMessage() = _uiState.update { it.copy(operationMessage = null) }

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
                roomInstanceRepo.getAvailable()
                    .filter { it.hotelId == AppContext.hotelId }
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
                bookingRepo.update(booking.copy(roomInstanceId = room.id, roomNumber = room.roomNumber))
                roomInstanceRepo.updateStatus(room.id, "ASSIGNED", null)
            }
                .onSuccess {
                    _uiState.update {
                        it.copy(
                            assignRoomDialogBooking = null,
                            availableRoomsForAssign = emptyList(),
                            operationMessage = "Room ${room.roomNumber} assigned to ${booking.guestName}",
                        )
                    }
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
            runCatching { bookingRepo.update(booking.copy(status = "CANCELLED")) }
                .onSuccess { _uiState.update { it.copy(cancelConfirmBooking = null, operationMessage = "Booking for ${booking.guestName} cancelled") } }
                .onFailure { e -> _uiState.update { it.copy(cancelConfirmBooking = null, error = e.message) } }
        }
    }

    // ── Dummy booking ─────────────────────────────────────────────────────────

    fun addDummyBooking() {
        launchWithGlobalLoading {
            val names = listOf("Rahul Sharma", "Priya Singh", "Amit Patel", "Neha Gupta", "Vikram Mehta", "Anjali Verma", "Sanjay Kumar", "Deepa Nair", "Arjun Reddy", "Kavitha Rao")
            val cal = Calendar.getInstance()
            val checkIn = cal.time
            cal.add(Calendar.DAY_OF_YEAR, (1..2).random())
            val checkOut = cal.time
            val total = (2000..8000).random().toDouble()

            // Pick a real category from the rooms collection so the booking can be assigned
            val categoryEntry = _uiState.value.categoryNames.entries.randomOrNull()
            val categoryId = categoryEntry?.key ?: ""
            val categoryName = categoryEntry?.value ?: ""

            val booking = Booking(
                hotelId = AppContext.hotelId,
                guestName = names.random(),
                guestPhone = "9${(100_000_000..999_999_999).random()}",
                roomCategoryId = categoryId,
                roomCategoryName = categoryName,
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
                .onSuccess { bookingId ->
                    val assignedRoom = autoAssignRoom(bookingId, booking)
                    val msg = if (assignedRoom != null)
                        "Dummy booking added for ${booking.guestName} — Room ${assignedRoom.roomNumber} assigned"
                    else
                        "Dummy booking added for ${booking.guestName} (no available room in category)"
                    _uiState.update { it.copy(operationMessage = msg) }
                }
                .onFailure { e -> _uiState.update { it.copy(error = "Failed to add booking: ${e.message}") } }
        }
    }

    // ── Auto-assign ───────────────────────────────────────────────────────────
    // Finds the first available room in the booking's category, links it to the
    // booking, and marks it ASSIGNED. Returns the assigned RoomInstance, or null
    // if no room was available (booking is left unassigned — not an error).

    suspend fun autoAssignRoom(bookingId: String, booking: Booking): RoomInstance? {
        if (booking.roomCategoryId.isBlank()) return null
        val room = runCatching {
            roomInstanceRepo.getByCategory(booking.roomCategoryId, AppContext.hotelId)
                .firstOrNull()
        }.getOrNull() ?: return null
        return runCatching {
            bookingRepo.update(booking.copy(id = bookingId, roomInstanceId = room.id, roomNumber = room.roomNumber))
            roomInstanceRepo.updateStatus(room.id, "ASSIGNED", null)
            room
        }.getOrNull()
    }
}
