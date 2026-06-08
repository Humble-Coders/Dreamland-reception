package com.example.dreamland_reception.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.dreamland_reception.data.AppContext
import com.example.dreamland_reception.data.model.Room
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
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.Calendar
import java.util.Date

data class AvailableCategory(
    val room: Room,
    val availableCount: Int,
    val availableForBookingCount: Int,
    val pricePerNight: Double,
)

data class AvailabilityUiState(
    val checkIn: Date = Calendar.getInstance().apply {
        set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0)
        set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
    }.time,
    val checkOut: Date = Calendar.getInstance().apply {
        add(Calendar.DAY_OF_YEAR, 1)
        set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0)
        set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
    }.time,
    val guests: Int = 1,
    val results: List<AvailableCategory> = emptyList(),
    val loading: Boolean = false,
    val error: String? = null,
    val searched: Boolean = false,
)

class AvailabilityViewModel(
    private val bookingRepo: BookingRepository = FirestoreBookingRepository,
    private val stayRepo: StayRepository = FirestoreStayRepository,
    private val roomRepo: RoomRepository = FirestoreRoomRepository,
    private val roomInstanceRepo: RoomInstanceRepository = FirestoreRoomInstanceRepository,
    private val hotelRepo: HotelRepository = FirestoreHotelRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(AvailabilityUiState())
    val uiState: StateFlow<AvailabilityUiState> = _uiState.asStateFlow()

    private var hotelCheckInTime: String = ""
    private var hotelCheckOutTime: String = ""

    // Tracks the 3 parallel real-time listeners; cancelled on new search or screen exit
    private var searchJob: Job? = null

    init {
        loadHotelTimes()
    }

    private fun loadHotelTimes() {
        viewModelScope.launch {
            runCatching { hotelRepo.getById(AppContext.hotelId) }
                .onSuccess { hotel ->
                    if (hotel == null) return@onSuccess
                    hotelCheckInTime  = hotel.checkInTime
                    hotelCheckOutTime = hotel.checkOutTime
                    _uiState.update { it.copy(
                        checkIn  = buildDate(hotel.checkInTime,  0),
                        checkOut = buildDate(hotel.checkOutTime, 1),
                    ) }
                }
        }
    }

    private fun buildDate(timeStr: String, daysOffset: Int): Date {
        val parts = timeStr.split(":")
        val h = parts.getOrNull(0)?.toIntOrNull() ?: 0
        val m = parts.getOrNull(1)?.toIntOrNull() ?: 0
        return Calendar.getInstance().apply {
            if (daysOffset != 0) add(Calendar.DAY_OF_YEAR, daysOffset)
            set(Calendar.HOUR_OF_DAY, h); set(Calendar.MINUTE, m)
            set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
        }.time
    }

    fun setCheckIn(d: Date)  = _uiState.update { it.copy(checkIn  = d, searched = false) }
    fun setCheckOut(d: Date) = _uiState.update { it.copy(checkOut = d, searched = false) }
    fun setGuests(n: Int)    = _uiState.update { it.copy(guests   = n.coerceAtLeast(1), searched = false) }
    fun clearError()         = _uiState.update { it.copy(error = null) }

    fun reset() {
        searchJob?.cancel()
        searchJob = null
        _uiState.update { it.copy(
            results = emptyList(), searched = false, error = null,
            checkIn  = buildDate(hotelCheckInTime.ifBlank  { "00:00" }, 0),
            checkOut = buildDate(hotelCheckOutTime.ifBlank { "00:00" }, 1),
            guests = 1,
        ) }
    }

    /**
     * Starts (or restarts) 3 parallel real-time Firestore listeners.
     * Availability recalculates reactively whenever any of the 3 streams emits.
     * The previous search's listeners are cancelled before the new ones start.
     */
    fun search() {
        val state = _uiState.value
        if (!state.checkOut.after(state.checkIn)) {
            _uiState.update { it.copy(error = "Check-out must be after check-in") }
            return
        }

        searchJob?.cancel()
        _uiState.update { it.copy(loading = true, error = null, results = emptyList(), searched = false) }

        val hotelId = AppContext.hotelId
        val checkIn  = state.checkIn
        val checkOut = state.checkOut
        val guests   = state.guests

        searchJob = viewModelScope.launch {
            // One-shot: room categories (rarely change, no need for real-time)
            val allCategories = runCatching { roomRepo.getByHotel(hotelId) }.getOrElse { emptyList() }

            // Refresh hotel check-in/out times synchronously before the combine below runs —
            // init's loadHotelTimes() is fire-and-forget and may not have resolved yet, and
            // since these are plain vars (not a Flow), a stale/blank value would never
            // retrigger a recompute. atHotelTime() silently falls back to raw timestamps when
            // blank, which misjudges same-day-turnover overlaps and undercounts bookable rooms.
            runCatching { hotelRepo.getById(hotelId) }.getOrNull()?.let { hotel ->
                hotelCheckInTime  = hotel.checkInTime
                hotelCheckOutTime = hotel.checkOutTime
            }

            // 3 parallel real-time listeners
            combine(
                // Stream 1: confirmed bookings for this hotel
                bookingRepo.listenByHotel(hotelId),
                // Stream 2: active stays for this hotel
                stayRepo.listenActive(hotelId),
                // Stream 3: room instances for this hotel
                roomInstanceRepo.listenByHotel(hotelId),
            ) { bookings, stays, instances ->
                // Bookings that have already been checked in have an active stay;
                // exclude them from the booking count to avoid double-counting.
                val checkedInBookingIds = stays.map { it.bookingId }.filter { it.isNotBlank() }.toSet()

                // Step 1 — confirmed bookings overlapping [checkIn, checkOut), not yet checked in
                val bookedByCat = bookings
                    .filter { it.status == "CONFIRMED" && it.id !in checkedInBookingIds && it.checkIn.before(checkOut) && it.checkOut.after(checkIn) }
                    .groupingBy { it.roomCategoryId }.eachCount()

                // Step 2 — active stays overlapping (expectedCheckOut > checkIn)
                val staysByCat = stays
                    .filter { it.expectedCheckOut.after(checkIn) }
                    .groupingBy { it.roomCategoryId }.eachCount()

                // Step 4 — usable instances. Only MAINTENANCE is excluded from capacity;
                // CLEANING is transient (the room is still real capacity for a date-ranged
                // stay), so counting it avoids deflating availability while a room is being
                // cleaned. This matches the walk-in check-in availability logic.
                val usable = instances.filter {
                    it.hotelId == hotelId && it.status != "MAINTENANCE"
                }
                val usableByCat = usable.groupingBy { it.categoryId }.eachCount()

                // New-booking availability — shares availableForNewBookingByCategory with the
                // Add Booking wizard (StaysViewModel) so this panel's "bookable" count can never
                // drift from the wizard's "avail." count. Unassigned confirmed/pending bookings
                // reduce the count on both surfaces.
                val availableForBookingByCat = availableForNewBookingByCategory(
                    usable, bookings, stays, checkIn, checkOut, hotelCheckInTime, hotelCheckOutTime,
                )

                // Steps 5-6 — availability per category, filter by guest count + active flag
                allCategories.mapNotNull { cat ->
                    val committed = (bookedByCat[cat.id] ?: 0) + (staysByCat[cat.id] ?: 0)
                    val usableCount = usableByCat[cat.id] ?: 0
                    val available = usableCount - committed
                    val availableForBooking = availableForBookingByCat[cat.id] ?: 0
                    if (available > 0 && cat.capacity >= guests && cat.available) {
                        AvailableCategory(
                            room = cat,
                            availableCount = available,
                            availableForBookingCount = availableForBooking,
                            pricePerNight = cat.pricePerNight,
                        )
                    } else null
                }.sortedByDescending { it.availableCount }
            }
            .catch { e -> _uiState.update { it.copy(loading = false, error = e.message) } }
            .collect { results ->
                _uiState.update { it.copy(loading = false, results = results, searched = true) }
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
        searchJob?.cancel()
    }
}
