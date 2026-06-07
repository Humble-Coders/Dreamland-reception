package com.example.dreamland_reception.ui.viewmodel

import com.example.dreamland_reception.data.model.Booking
import com.example.dreamland_reception.data.model.RoomInstance
import com.example.dreamland_reception.data.model.Stay
import com.example.dreamland_reception.util.atHotelTime
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

/**
 * Room instances bookable for a NEW reservation over [checkIn, checkOut): not under
 * maintenance, flagged bookable, and free of any CONFIRMED/PENDING_PAYMENT booking or
 * active stay overlapping the range (half-open overlap, hotel-time aligned).
 *
 * CLEANING rooms are intentionally KEPT here — they remain selectable when assigning a
 * room to a future booking. Callers that count *availability* should additionally drop
 * CLEANING (`.filter { it.status != "CLEANING" }`) before grouping, which both current
 * call sites do. This is the single source of truth so the Add Booking dialog and the
 * Check Availability panel can never report different counts again.
 */
internal fun bookableInstancesForNewBooking(
    instances: List<RoomInstance>,
    bookings: List<Booking>,
    activeStays: List<Stay>,
    checkIn: Date,
    checkOut: Date,
    hotelCheckInTime: String,
    hotelCheckOutTime: String,
): List<RoomInstance> {
    val bookedIds = bookings
        .filter { (it.status == "CONFIRMED" || it.status == "PENDING_PAYMENT") && it.roomInstanceId.isNotBlank() }
        .filter { it.checkIn.atHotelTime(hotelCheckInTime) < checkOut.atHotelTime(hotelCheckOutTime) &&
                  it.checkOut.atHotelTime(hotelCheckOutTime) > checkIn.atHotelTime(hotelCheckInTime) }
        .map { it.roomInstanceId }.toSet()
    val occupiedIds = activeStays
        .filter { it.checkInActual.atHotelTime(hotelCheckInTime) < checkOut.atHotelTime(hotelCheckOutTime) &&
                  it.expectedCheckOut.atHotelTime(hotelCheckOutTime) > checkIn.atHotelTime(hotelCheckInTime) }
        .map { it.roomInstanceId }.toSet()
    return instances.filter {
        it.status != "MAINTENANCE" && it.isAvailableForBooking &&
        it.id !in bookedIds && it.id !in occupiedIds
    }
}

/**
 * Per-category count of rooms that can still be sold for a NEW reservation over
 * [checkIn, checkOut). The single source of truth shared by the Add Booking wizard and the
 * Dashboard's Check Availability panel, so their counts can never diverge.
 *
 * = free bookable rooms ([bookableInstancesForNewBooking], excluding CLEANING)
 *   − confirmed/pending bookings for the category that have **no room assigned yet**
 *     (an unassigned reservation still consumes category inventory, so it reduces what's
 *     left to sell).
 */
internal fun availableForNewBookingByCategory(
    instances: List<RoomInstance>,
    bookings: List<Booking>,
    activeStays: List<Stay>,
    checkIn: Date,
    checkOut: Date,
    hotelCheckInTime: String,
    hotelCheckOutTime: String,
): Map<String, Int> {
    val freeBookableByCat = bookableInstancesForNewBooking(
        instances, bookings, activeStays, checkIn, checkOut, hotelCheckInTime, hotelCheckOutTime,
    ).filter { it.status != "CLEANING" }
        .groupingBy { it.categoryId }.eachCount()

    val unassignedDemandByCat = bookings
        .filter { (it.status == "CONFIRMED" || it.status == "PENDING_PAYMENT") && it.roomInstanceId.isBlank() }
        .filter { it.checkIn.atHotelTime(hotelCheckInTime) < checkOut.atHotelTime(hotelCheckOutTime) &&
                  it.checkOut.atHotelTime(hotelCheckOutTime) > checkIn.atHotelTime(hotelCheckInTime) }
        .groupingBy { it.roomCategoryId }.eachCount()

    return (freeBookableByCat.keys + unassignedDemandByCat.keys).associateWith { catId ->
        ((freeBookableByCat[catId] ?: 0) - (unassignedDemandByCat[catId] ?: 0)).coerceAtLeast(0)
    }
}
