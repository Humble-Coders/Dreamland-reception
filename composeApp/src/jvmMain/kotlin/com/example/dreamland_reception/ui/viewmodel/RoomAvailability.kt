package com.example.dreamland_reception.ui.viewmodel

import com.example.dreamland_reception.data.model.Booking
import com.example.dreamland_reception.data.model.RoomInstance
import java.util.Date

/**
 * Returns only those [instances] whose date range [checkIn, checkOut) does not conflict
 * with any CONFIRMED booking that already has a room assigned.
 *
 * [excludeBookingId] — the booking being edited, so its own assigned room stays selectable.
 */
internal fun dateConflictFilter(
    instances: List<RoomInstance>,
    confirmedBookings: List<Booking>,
    checkIn: Date,
    checkOut: Date,
    excludeBookingId: String = "",
): List<RoomInstance> {
    val conflictingIds = confirmedBookings
        .filter { it.id != excludeBookingId && it.roomInstanceId.isNotBlank() }
        .filter { it.checkIn.before(checkOut) && it.checkOut.after(checkIn) }
        .map { it.roomInstanceId }
        .toSet()
    return instances.filter { it.id !in conflictingIds }
}
