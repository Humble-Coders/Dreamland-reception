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
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.Calendar
import java.util.Date

data class AvailableCategory(
    val room: Room,
    val availableCount: Int,
    val pricePerNight: Double,
)

data class AvailabilityUiState(
    // Dates initialised to midnight; updated to actual hotel times once hotel is fetched
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

    // Hotel times fetched from Firestore on init; used for default check-in/out window
    private var hotelCheckInTime: String = ""
    private var hotelCheckOutTime: String = ""

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
                    // Update default dates to reflect actual hotel policy
                    _uiState.update { it.copy(
                        checkIn  = buildDate(hotel.checkInTime,  0),
                        checkOut = buildDate(hotel.checkOutTime, 1),
                    )}
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

    fun setCheckIn(d: Date) = _uiState.update { it.copy(checkIn = d, searched = false) }
    fun setCheckOut(d: Date) = _uiState.update { it.copy(checkOut = d, searched = false) }
    fun setGuests(n: Int) = _uiState.update { it.copy(guests = n.coerceAtLeast(1), searched = false) }
    fun clearError() = _uiState.update { it.copy(error = null) }

    fun reset() = _uiState.update { it.copy(
        results = emptyList(), searched = false, error = null,
        checkIn  = buildDate(hotelCheckInTime.ifBlank { "00:00" },  0),
        checkOut = buildDate(hotelCheckOutTime.ifBlank { "00:00" }, 1),
        guests = 1,
    )}

    fun search() {
        val state = _uiState.value
        if (!state.checkOut.after(state.checkIn)) {
            _uiState.update { it.copy(error = "Check-out must be after check-in") }
            return
        }
        _uiState.update { it.copy(loading = true, error = null, results = emptyList()) }
        launchWithGlobalLoading {
            runCatching {
                val hotelId = AppContext.hotelId
                val checkIn  = state.checkIn
                val checkOut = state.checkOut
                val guests   = state.guests

                // Step 1: CONFIRMED bookings overlapping the range, grouped by categoryId
                val confirmedBookings = bookingRepo.getConfirmedByHotel(hotelId)
                val bookedByCategory = confirmedBookings
                    .filter { b -> b.checkIn.before(checkOut) && b.checkOut.after(checkIn) }
                    .groupBy { it.roomCategoryId }
                    .mapValues { it.value.size }

                // Step 2: ACTIVE stays overlapping the range, grouped by categoryId
                val activeStays = stayRepo.getActive(hotelId)
                val occupiedByCategory = activeStays
                    .filter { s -> s.expectedCheckOut.after(checkIn) }
                    .groupBy { it.roomCategoryId }
                    .mapValues { it.value.size }

                // Step 3+4: count usable room instances per category
                val allCategories = roomRepo.getByHotel(hotelId)
                val allInstances  = roomInstanceRepo.getAll().filter { it.hotelId == hotelId }

                allCategories
                    .filter { cat ->
                        val usable = allInstances.count { inst ->
                            inst.categoryId == cat.id &&
                            inst.status !in setOf("MAINTENANCE", "CLEANING")
                        }
                        val committed = (bookedByCategory[cat.id] ?: 0) + (occupiedByCategory[cat.id] ?: 0)
                        val available = usable - committed
                        // Step 5+6: available > 0, capacity >= guests, category not in maintenance
                        available > 0 && cat.capacity >= guests && cat.status.lowercase() != "maintenance"
                    }
                    .map { cat ->
                        val usable = allInstances.count { inst ->
                            inst.categoryId == cat.id &&
                            inst.status !in setOf("MAINTENANCE", "CLEANING")
                        }
                        val committed = (bookedByCategory[cat.id] ?: 0) + (occupiedByCategory[cat.id] ?: 0)
                        AvailableCategory(
                            room = cat,
                            availableCount = usable - committed,
                            pricePerNight = cat.pricePerNight,
                        )
                    }
                    .sortedByDescending { it.availableCount }
            }
            .onSuccess { results -> _uiState.update { it.copy(loading = false, results = results, searched = true) } }
            .onFailure { e -> _uiState.update { it.copy(loading = false, error = e.message) } }
        }
    }
}
