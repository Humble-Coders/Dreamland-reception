package com.example.dreamland_reception.ui.viewmodel

import androidx.lifecycle.ViewModel
import com.example.dreamland_reception.data.AppContext
import com.example.dreamland_reception.data.model.BillingInvoice
import com.example.dreamland_reception.data.model.Booking
import com.example.dreamland_reception.data.model.Complaint
import com.example.dreamland_reception.data.model.Order
import com.example.dreamland_reception.data.model.OrderItem
import com.example.dreamland_reception.data.model.Room
import com.example.dreamland_reception.data.model.RoomInstance
import com.example.dreamland_reception.data.model.Stay
import com.example.dreamland_reception.data.repository.BillingRepository
import com.example.dreamland_reception.data.repository.BookingRepository
import com.example.dreamland_reception.data.repository.ComplaintRepository
import com.example.dreamland_reception.data.repository.FirestoreBillingRepository
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
import com.example.dreamland_reception.data.repository.StayRepository
import com.example.dreamland_reception.DreamlandAppInitializer
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import java.time.temporal.ChronoUnit
import java.util.Calendar
import java.util.Date

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
    val checkInTime: Date = Date(),
    val expectedCheckOut: Date? = null,
    val adults: Int = 1,
    val children: Int = 0,
    val breakfast: Boolean = false,
    val advancePayment: String = "",
    val categories: List<Room> = emptyList(),
    val availableInstances: List<RoomInstance> = emptyList(),
    val availableCount: Int = 0,
    val isSaving: Boolean = false,
    val error: String? = null,
    val sourceBooking: Booking? = null,
)

// ── From-booking dialog state ─────────────────────────────────────────────────

data class FromBookingState(
    val isOpen: Boolean = false,
    val bookings: List<Booking> = emptyList(),
    val isLoading: Boolean = false,
)

// ── Detail panel state ────────────────────────────────────────────────────────

data class StayDetailState(
    val bill: BillingInvoice? = null,
    val orders: List<Order> = emptyList(),
    val complaints: List<Complaint> = emptyList(),
    val isLoading: Boolean = false,
)

// ── Add-order dialog state ────────────────────────────────────────────────────

data class CatalogItem(val name: String, val price: Double, val category: String)

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
        catalogItems.flatMap { it.category.split(",").map(String::trim) }
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
    val hotelCheckOutTime: String = "11:00",
    val navigateToBilling: Boolean = false,
    val checkedOutStayId: String = "",
)

// ── ViewModel ─────────────────────────────────────────────────────────────────

class StaysViewModel(
    private val stayRepo: StayRepository = FirestoreStayRepository,
    private val bookingRepo: BookingRepository = FirestoreBookingRepository,
    private val roomRepo: RoomRepository = FirestoreRoomRepository,
    private val instanceRepo: RoomInstanceRepository = FirestoreRoomInstanceRepository,
    private val billingRepo: BillingRepository = FirestoreBillingRepository,
    private val orderRepo: OrderRepository = FirestoreOrderRepository,
    private val complaintRepo: ComplaintRepository = FirestoreComplaintRepository,
    private val foodItemRepo: FoodItemRepository = FirestoreFoodItemRepository,
    private val serviceRepo: ServiceRepository = FirestoreServiceRepository,
) : ViewModel() {

    private val _listState = MutableStateFlow(StaysListState(isLoading = true))
    val listState: StateFlow<StaysListState> = _listState.asStateFlow()

    private val _walkInState = MutableStateFlow(WalkInState())
    val walkInState: StateFlow<WalkInState> = _walkInState.asStateFlow()

    private val _fromBookingState = MutableStateFlow(FromBookingState())
    val fromBookingState: StateFlow<FromBookingState> = _fromBookingState.asStateFlow()

    private val _detailState = MutableStateFlow(StayDetailState())
    val detailState: StateFlow<StayDetailState> = _detailState.asStateFlow()

    private val _checkOutState = MutableStateFlow(CheckOutState())
    val checkOutState: StateFlow<CheckOutState> = _checkOutState.asStateFlow()

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
            runCatching { stayRepo.getActive(hotelId) }
                .onSuccess { stays ->
                    _listState.update { it.copy(stays = stays, isLoading = false) }
                }
                .onFailure { e ->
                    _listState.update { it.copy(isLoading = false, error = e.message ?: "Failed to load stays") }
                }
        }
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
        launchWithGlobalLoading {
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
            val bill = runCatching { billingRepo.getByStay(stayId) }.getOrNull()
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
            _walkInState.value = WalkInState(isOpen = true, categories = categories)
        }
    }

    fun closeWalkIn() {
        _walkInState.value = WalkInState()
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
    }
    fun onWalkInChildren(count: Int) = _walkInState.update { it.copy(children = count.coerceAtLeast(0)) }
    fun onWalkInCheckInTime(date: Date) = _walkInState.update { it.copy(checkInTime = date) }

    fun onWalkInExpectedCheckOut(date: Date?) {
        _walkInState.update { it.copy(expectedCheckOut = date) }
        val categoryId = _walkInState.value.selectedCategoryId
        if (categoryId.isNotBlank()) onCategorySelected(categoryId)
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
        _walkInState.update { it.copy(
            selectedCategoryId = categoryId,
            selectedCategoryName = cat.type,
            selectedCategoryBreakfastPrice = cat.breakfastPrice,
            selectedInstanceIds = emptySet(),
            availableInstances = emptyList(),
            availableCount = 0,
        ) }
        launchWithGlobalLoading {
            val hotelId = AppContext.hotelId
            val checkOut = _walkInState.value.expectedCheckOut
            val checkIn = Date()

            // All usable instances in this category (not maintenance/cleaning)
            val allInstances = runCatching {
                instanceRepo.getByCategory(categoryId, hotelId, includeAssigned = false)
            }.getOrElse { emptyList() }

            val available = if (checkOut != null) {
                // Remove instances with CONFIRMED booking date conflicts
                val confirmed = runCatching { bookingRepo.getConfirmedByHotel(hotelId) }.getOrElse { emptyList() }
                val noConflict = dateConflictFilter(allInstances, confirmed, checkIn, checkOut)
                // Remove instances occupied by active stays overlapping the range
                val activeStays = runCatching { stayRepo.getActive(hotelId) }.getOrElse { emptyList() }
                noConflict.filter { instance ->
                    activeStays.none { stay ->
                        stay.roomInstanceId == instance.id && stay.expectedCheckOut.after(checkIn)
                    }
                }
            } else {
                allInstances.filter { it.status == "AVAILABLE" }
            }
            _walkInState.update { it.copy(availableInstances = available, availableCount = available.size) }
        }
    }

    fun onInstanceToggled(instanceId: String) {
        _walkInState.update { ws ->
            val ids = ws.selectedInstanceIds
            ws.copy(selectedInstanceIds = if (instanceId in ids) ids - instanceId else ids + instanceId)
        }
    }

    fun submitWalkIn() {
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
                val now = ws.checkInTime
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

                val firstStayId = ws.selectedInstanceIds.mapIndexed { roomIndex, instanceId ->
                    // Risk 4: always derive category from Firestore doc
                    val roomInstance = instanceRepo.getById(instanceId)
                        ?: throw Exception("Room instance $instanceId not found")
                    val cat = ws.categories.find { it.id == roomInstance.categoryId }
                    val nights = ChronoUnit.DAYS.between(now.toInstant(), ws.expectedCheckOut.toInstant()).coerceAtLeast(1)
                    val roomCharge = (cat?.pricePerNight ?: 0.0) * nights
                    val breakfastCharge = if (ws.breakfast) ws.selectedCategoryBreakfastPrice * ws.adults * nights else 0.0
                    // Early check-in charge only on first room
                    val earlyCharge = if (roomIndex == 0) earlyCheckInCharge else 0.0
                    val total = roomCharge + breakfastCharge + earlyCharge

                    val stay = Stay(
                        hotelId = AppContext.hotelId,
                        bookingId = if (roomIndex == 0) ws.sourceBooking?.id ?: "" else "",
                        guestName = primaryName,
                        guestPhone = primaryPhone,
                        roomInstanceId = instanceId,
                        roomNumber = roomInstance.roomNumber,
                        roomCategoryId = roomInstance.categoryId,
                        roomCategoryName = roomInstance.categoryName,
                        checkInActual = now,
                        expectedCheckOut = ws.expectedCheckOut,
                        status = "ACTIVE",
                        adults = ws.adults,
                        children = ws.children,
                        breakfast = ws.breakfast,
                        earlyCheckIn = isEarlyCheckIn && roomIndex == 0,
                        createdAt = now,
                    )
                    val stayId = stayRepo.checkInBatch(stay, instanceId)
                    billingRepo.add(BillingInvoice(
                        stayId = stayId,
                        guestName = primaryName,
                        roomNumber = roomInstance.roomNumber,
                        roomCharges = roomCharge,
                        serviceCharges = breakfastCharge,
                        earlyCheckInCharge = earlyCharge,
                        totalAmount = total,
                        amountPaid = advancePerRoom,
                        status = if (advancePerRoom >= total) "PAID" else if (advancePerRoom > 0) "PARTIAL" else "PENDING",
                        issuedAt = now,
                    ))
                    // Only link booking for single-room or first room of walk-in
                    if (roomIndex == 0) {
                        val srcBooking = ws.sourceBooking
                        if (srcBooking != null) {
                            bookingRepo.update(srcBooking.copy(roomInstanceId = instanceId, roomNumber = roomInstance.roomNumber))
                        } else {
                            bookingRepo.add(Booking(
                                hotelId = AppContext.hotelId,
                                guestName = primaryName,
                                guestPhone = primaryPhone,
                                roomCategoryId = roomInstance.categoryId,
                                roomCategoryName = roomInstance.categoryName,
                                roomInstanceId = instanceId,
                                roomNumber = roomInstance.roomNumber,
                                checkIn = now,
                                checkOut = ws.expectedCheckOut,
                                adults = ws.adults,
                                children = ws.children,
                                status = "COMPLETED",
                                source = "WALK_IN",
                                totalAmount = total * roomCount,
                                advancePaidAmount = advance,
                                createdAt = now,
                            ))
                        }
                    }
                    stayId
                }.first()
                firstStayId
            }.onSuccess { newStayId ->
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

    fun prefillFromBooking(booking: Booking) {
        val hotelId = AppContext.hotelId
        _fromBookingState.value = FromBookingState()
        launchWithGlobalLoading {
            val categories = runCatching { roomRepo.getByHotel(hotelId) }.getOrElse { emptyList() }
            val allInstances = if (booking.roomCategoryId.isNotBlank()) {
                runCatching { instanceRepo.getByCategory(booking.roomCategoryId, hotelId, includeAssigned = true) }.getOrElse { emptyList() }
            } else emptyList()
            val confirmed = runCatching { bookingRepo.getConfirmedByHotel(hotelId) }.getOrElse { emptyList() }
            val instances = dateConflictFilter(allInstances, confirmed, booking.checkIn, booking.checkOut, excludeBookingId = booking.id)
            val cat = categories.find { it.id == booking.roomCategoryId }
            val entries = List(booking.adults.coerceAtLeast(1)) { i ->
                if (i == 0) GuestEntry(name = booking.guestName, phone = booking.guestPhone)
                else GuestEntry()
            }
            // Pre-select the assigned room if the booking already has one
            val preAssignedInstance = instances.find { it.id == booking.roomInstanceId }
            _walkInState.value = WalkInState(
                isOpen = true,
                guestName = booking.guestName,
                guestPhone = booking.guestPhone,
                guestEntries = entries,
                selectedCategoryId = booking.roomCategoryId,
                selectedCategoryName = booking.roomCategoryName,
                selectedCategoryBreakfastPrice = cat?.breakfastPrice ?: 0.0,
                selectedInstanceIds = if (preAssignedInstance != null) setOf(preAssignedInstance.id) else emptySet(),
                expectedCheckOut = booking.checkOut,
                adults = booking.adults,
                children = booking.children,
                categories = categories,
                availableInstances = instances,
                availableCount = instances.size,
                sourceBooking = booking,
            )
        }
    }

    // ── Check-out ─────────────────────────────────────────────────────────────

    fun openCheckOut(stayId: String) {
        val stay = _listState.value.stays.find { it.id == stayId } ?: return
        launchWithGlobalLoading {
            val bill = runCatching { billingRepo.getByStay(stayId) }.getOrNull()
            val hotel = DreamlandAppInitializer.getSettingsViewModel().state.value.selectedHotel
            val isLateCheckout = if (hotel != null) {
                val parts = hotel.checkOutTime.split(":")
                val hotelHour = parts.getOrNull(0)?.toIntOrNull() ?: 11
                val hotelMin = parts.getOrNull(1)?.toIntOrNull() ?: 0
                val now = Calendar.getInstance()
                val expectedDateOnly = Calendar.getInstance().apply {
                    time = stay.expectedCheckOut
                    set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
                }
                val todayDateOnly = Calendar.getInstance().apply {
                    set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
                }
                val nowMinutes = now.get(Calendar.HOUR_OF_DAY) * 60 + now.get(Calendar.MINUTE)
                todayDateOnly.after(expectedDateOnly) ||
                    (todayDateOnly.timeInMillis == expectedDateOnly.timeInMillis && nowMinutes > hotelHour * 60 + hotelMin)
            } else false
            val flatFee = if (isLateCheckout && hotel?.lateCheckOutAllowed == true) hotel.lateCheckOutPrice else 0.0
            val nights = ChronoUnit.DAYS.between(
                stay.checkInActual.toInstant(), stay.expectedCheckOut.toInstant()
            ).coerceAtLeast(1)
            val roomPricePerNight = if (bill != null) bill.roomCharges / nights else 0.0
            _checkOutState.value = CheckOutState(
                isOpen = true, stay = stay, bill = bill,
                isLateCheckout = isLateCheckout,
                lateCheckoutCharge = flatFee,
                flatLateCheckoutFee = flatFee,
                roomPricePerNight = roomPricePerNight,
                lateChargeType = "FLAT",
                hotelCheckOutTime = hotel?.checkOutTime ?: "11:00",
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
            cos.copy(lateChargeType = type, lateCheckoutCharge = charge)
        }
    }

    fun confirmCheckOut() {
        val cos = _checkOutState.value
        val stay = cos.stay ?: return
        val bill = cos.bill ?: return
        _checkOutState.update { it.copy(isProcessing = true, error = null) }
        launchWithGlobalLoading {
            runCatching {
                // Apply late checkout charge to bill if applicable
                if (cos.lateCheckoutCharge > 0 && bill.id.isNotBlank()) {
                    val newTotal = bill.totalAmount + cos.lateCheckoutCharge
                    billingRepo.updateCharges(bill.id, bill.earlyCheckInCharge, cos.lateCheckoutCharge, newTotal)
                }
                stayRepo.checkOut(stay.id, Date())
                instanceRepo.updateStatus(stay.roomInstanceId, "CLEANING", currentStayId = null)
                if (stay.bookingId.isNotBlank()) {
                    val booking = runCatching { bookingRepo.getById(stay.bookingId) }.getOrNull()
                    if (booking != null) bookingRepo.update(booking.copy(status = "COMPLETED"))
                }
            }.onSuccess {
                _checkOutState.value = CheckOutState(navigateToBilling = true, checkedOutStayId = stay.id)
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

    // ── Add Order ─────────────────────────────────────────────────────────────

    fun openAddOrder() {
        val hotelId = AppContext.hotelId
        _addOrderState.value = AddOrderState(isOpen = true, isLoadingCatalog = true)
        launchWithGlobalLoading {
            val foodItems = runCatching { foodItemRepo.getByHotel(hotelId) }.getOrDefault(emptyList())
            val services = runCatching { serviceRepo.getByHotel(hotelId) }.getOrDefault(emptyList())
            val catalog = foodItems
                .filter { it.isAvailable }
                .map { CatalogItem(name = it.name, price = it.price, category = it.category.ifBlank { "Food" }) } +
                services
                .filter { it.isActive }
                .map { CatalogItem(name = it.name, price = it.price, category = "Services") }
            val firstCategory = catalog.map { it.category }.distinct().firstOrNull() ?: ""
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
            val catalog = foodItems.filter { it.isAvailable }
                .map { CatalogItem(name = it.name, price = it.price, category = it.category.ifBlank { "Food" }) } +
                services.filter { it.isActive }
                .map { CatalogItem(name = it.name, price = it.price, category = "Services") }
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
        // Auto-fill price when typed name exactly matches a catalog item and price is still blank
        val exactMatch = s.catalogItems.find { it.name.equals(v.trim(), ignoreCase = true) }
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
}
