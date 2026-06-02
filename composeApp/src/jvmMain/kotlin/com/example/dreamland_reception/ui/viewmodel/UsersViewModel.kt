package com.example.dreamland_reception.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.dreamland_reception.data.AppContext
import com.example.dreamland_reception.data.model.Booking
import com.example.dreamland_reception.data.model.Guest
import com.example.dreamland_reception.data.model.Stay
import com.example.dreamland_reception.data.repository.BookingRepository
import com.example.dreamland_reception.data.repository.FirestoreBookingRepository
import com.example.dreamland_reception.data.repository.FirestoreStayRepository
import com.example.dreamland_reception.data.repository.FirestoreUserRepository
import com.example.dreamland_reception.data.repository.StayRepository
import com.example.dreamland_reception.data.repository.UserRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class UsersUiState(
    val users: List<Guest> = emptyList(),
    // Cached for detail filtering — not exposed to UI directly
    internal val allBookings: List<Booking> = emptyList(),
    val isLoading: Boolean = false,
    val error: String? = null,
    val searchQuery: String = "",
    val selectedUserId: String? = null,
    val userBookings: List<Booking> = emptyList(),
    val userStays: List<Stay> = emptyList(),
    val isDetailLoading: Boolean = false,
) {
    val filteredUsers: List<Guest> get() {
        val q = searchQuery.trim().lowercase()
        return if (q.isEmpty()) users
        else users.filter {
            it.name.lowercase().contains(q) || it.phone.contains(searchQuery.trim())
        }
    }
    val selectedUser: Guest? get() = users.find { it.id == selectedUserId }
}

class UsersViewModel(
    private val userRepo: UserRepository = FirestoreUserRepository,
    private val bookingRepo: BookingRepository = FirestoreBookingRepository,
    private val stayRepo: StayRepository = FirestoreStayRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(UsersUiState(isLoading = true))
    val state: StateFlow<UsersUiState> = _state.asStateFlow()

    init { startListeners() }

    private fun startListeners() {
        val hotelId = AppContext.hotelId
        viewModelScope.launch {
            combine(
                bookingRepo.listenByHotel(hotelId),
                stayRepo.listenActive(hotelId),
            ) { bookings, stays -> bookings to stays }
                .catch { e -> _state.update { it.copy(isLoading = false, error = e.message) } }
                .collect { (bookings, stays) -> deriveUsers(bookings, stays) }
        }
    }

    private suspend fun deriveUsers(allBookings: List<Booking>, allStays: List<Stay>) {
        _state.update { it.copy(isLoading = true) }
        val bookingIdToUserId = allBookings
            .filter { it.userId.isNotBlank() }
            .associate { it.id to it.userId }
        val userIds = buildSet<String> {
            allBookings.forEach { if (it.userId.isNotBlank()) add(it.userId) }
            allStays.forEach { stay -> bookingIdToUserId[stay.bookingId]?.let { add(it) } }
            // Walk-in stays carry a userId directly (no booking) — include them too.
            allStays.forEach { if (it.userId.isNotBlank()) add(it.userId) }
        }
        val rawUsers = runCatching { userRepo.getByIds(userIds) }.getOrElse { emptyList() }
        // Enrich guests whose name/phone is blank with data from their most recent booking
        val users = rawUsers.map { guest ->
            val booking = allBookings.lastOrNull { it.userId == guest.id }
            guest.copy(
                name = guest.name.ifBlank { booking?.userName ?: "" },
                phone = guest.phone.ifBlank { booking?.guestPhone ?: "" },
            )
        }
        _state.update { it.copy(users = users, allBookings = allBookings, isLoading = false, error = null) }
    }

    // Called by refreshAllViewModels — no Firestore reads, just re-collects from live state
    fun loadAll() {
        // Listeners are always running; this is a no-op unless hotel changes
        startListeners()
    }

    fun selectUser(guest: Guest?) {
        _state.update { it.copy(selectedUserId = guest?.id, userBookings = emptyList(), userStays = emptyList()) }
        if (guest != null) loadUserDetail(guest)
    }

    fun setSearch(query: String) = _state.update { it.copy(searchQuery = query) }

    private fun loadUserDetail(guest: Guest) {
        val hotelId = AppContext.hotelId
        viewModelScope.launch {
            _state.update { it.copy(isDetailLoading = true) }
            // Bookings: filtered from cached state — 0 Firestore reads
            val userBookings = _state.value.allBookings
                .filter { it.userId == guest.id }
                .sortedByDescending { it.createdAt }
            val userBookingIds = userBookings.map { it.id }.toSet()
            // Stays: one-shot to include completed stays not in the active listener
            val allStays = runCatching { stayRepo.getAll(hotelId) }.getOrElse { emptyList() }
            val userStays = allStays
                .filter { it.bookingId in userBookingIds }
                .sortedByDescending { it.checkInActual }
            _state.update { it.copy(userBookings = userBookings, userStays = userStays, isDetailLoading = false) }
        }
    }
}
