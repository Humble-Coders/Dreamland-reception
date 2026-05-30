package com.example.dreamland_reception.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.dreamland_reception.data.model.Booking
import com.example.dreamland_reception.data.repository.BookingRepository
import com.example.dreamland_reception.data.repository.FirestoreBookingRepository
import com.example.dreamland_reception.ui.notification.NotificationManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.launch
import java.util.Date

data class BookingNotification(
    val bookingId: String,
    val guestName: String,
    val roomCategory: String,
    val checkIn: Date,
    val checkOut: Date,
)

/**
 * App-level ViewModel: listens for new bookings created after app start and dispatches
 * in-app banner, sound, and system tray notifications.
 */
class AppViewModel(
    private val bookingRepo: BookingRepository = FirestoreBookingRepository,
) : ViewModel() {

    private val appStartTime = Date()

    // IDs seen in the first listener emission (existing bookings) — never notified
    private val seenIds = mutableSetOf<String>()
    private var initialized = false
    private var listenerJob: Job? = null

    private val _notificationFlow = MutableSharedFlow<BookingNotification>(extraBufferCapacity = 10)
    val notificationFlow: SharedFlow<BookingNotification> = _notificationFlow.asSharedFlow()

    /**
     * Starts (or restarts) the booking notification listener for [hotelId].
     * Call at app launch and again whenever the selected hotel changes.
     */
    fun startListening(hotelId: String) {
        if (hotelId.isBlank()) return
        listenerJob?.cancel()
        initialized = false
        seenIds.clear()

        listenerJob = viewModelScope.launch {
            bookingRepo.listenByHotel(hotelId)
                .catch { /* listener error — ignore, Firestore will retry */ }
                .collect { bookings ->
                    if (!initialized) {
                        // First emission contains all existing docs — record them, don't notify
                        seenIds.addAll(bookings.map { it.id })
                        initialized = true
                    } else {
                        val newBookings = bookings.filter {
                            it.id !in seenIds && it.createdAt.after(appStartTime)
                        }
                        seenIds.addAll(newBookings.map { it.id })
                        newBookings.forEach { dispatch(it) }
                    }
                }
        }
    }

    private fun dispatch(booking: Booking) {
        // Sound — background thread, non-blocking
        viewModelScope.launch(Dispatchers.IO) {
            NotificationManager.playSound()
        }
        // In-app banner — emit to UI
        viewModelScope.launch {
            _notificationFlow.emit(
                BookingNotification(
                    bookingId = booking.id,
                    guestName = booking.guestName,
                    roomCategory = booking.roomCategoryName,
                    checkIn = booking.checkIn,
                    checkOut = booking.checkOut,
                ),
            )
        }
    }

    override fun onCleared() {
        super.onCleared()
        listenerJob?.cancel()
    }
}
