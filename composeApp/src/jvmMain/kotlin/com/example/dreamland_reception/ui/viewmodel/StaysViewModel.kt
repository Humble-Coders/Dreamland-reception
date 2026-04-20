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
    val selectedInstanceId: String = "",
    val selectedInstanceNumber: String = "",
    val expectedCheckOut: Date? = null,
    val adults: Int = 1,
    val children: Int = 0,
    val breakfast: Boolean = false,
    val advancePayment: String = "",
    val categories: List<Room> = emptyList(),
    val availableInstances: List<RoomInstance> = emptyList(),
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
    val suggestions: List<CatalogItem> = emptyList(),
    val showSuggestions: Boolean = false,
)

data class AddOrderState(
    val isOpen: Boolean = false,
    val category: String = "",
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
    val navigateToBilling: Boolean = false,
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
    fun onWalkInExpectedCheckOut(date: Date?) = _walkInState.update { it.copy(expectedCheckOut = date) }
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
            selectedInstanceId = "",
            selectedInstanceNumber = "",
            availableInstances = emptyList(),
        ) }
        launchWithGlobalLoading {
            val instances = runCatching { instanceRepo.getByCategory(categoryId, AppContext.hotelId) }.getOrElse { emptyList() }
            _walkInState.update { it.copy(availableInstances = instances) }
        }
    }

    fun onInstanceSelected(instanceId: String) {
        val inst = _walkInState.value.availableInstances.find { it.id == instanceId } ?: return
        _walkInState.update { it.copy(selectedInstanceId = instanceId, selectedInstanceNumber = inst.roomNumber) }
    }

    fun submitWalkIn() {
        val ws = _walkInState.value
        val primaryName = ws.guestEntries.firstOrNull()?.name?.trim()?.ifBlank { ws.guestName.trim() } ?: ws.guestName.trim()
        if (primaryName.isBlank()) { _walkInState.update { it.copy(error = "Primary guest name is required") }; return }
        if (ws.selectedInstanceId.isBlank()) { _walkInState.update { it.copy(error = "Please select a room") }; return }
        if (ws.expectedCheckOut == null) { _walkInState.update { it.copy(error = "Please set expected check-out date") }; return }

        _walkInState.update { it.copy(isSaving = true, error = null) }
        launchWithGlobalLoading {
            runCatching {
                val now = Date()
                val primary = ws.guestEntries.firstOrNull() ?: GuestEntry()
                val primaryName = primary.name.trim().ifBlank { ws.guestName.trim() }
                val primaryPhone = primary.phone.trim().ifBlank { ws.guestPhone.trim() }
                // Detect early check-in
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
                val stay = Stay(
                    hotelId = AppContext.hotelId,
                    bookingId = ws.sourceBooking?.id ?: "",
                    guestName = primaryName,
                    guestPhone = primaryPhone,
                    roomInstanceId = ws.selectedInstanceId,
                    roomNumber = ws.selectedInstanceNumber,
                    roomCategoryId = ws.selectedCategoryId,
                    roomCategoryName = ws.selectedCategoryName,
                    checkInActual = now,
                    expectedCheckOut = ws.expectedCheckOut,
                    status = "ACTIVE",
                    adults = ws.adults,
                    children = ws.children,
                    breakfast = ws.breakfast,
                    earlyCheckIn = isEarlyCheckIn,
                    createdAt = now,
                )
                // 1. Create stay
                val stayId = stayRepo.add(stay)
                // 2. Mark room as occupied
                instanceRepo.updateStatus(ws.selectedInstanceId, "OCCUPIED", currentStayId = stayId)
                // 3. Create initial bill
                val cat = ws.categories.find { it.id == ws.selectedCategoryId }
                val nights = ChronoUnit.DAYS.between(
                    now.toInstant(), ws.expectedCheckOut.toInstant(),
                ).coerceAtLeast(1)
                val roomCharge = (cat?.pricePerNight ?: 0.0) * nights
                val breakfastCharge = if (ws.breakfast) ws.selectedCategoryBreakfastPrice * ws.adults * nights else 0.0
                val total = roomCharge + breakfastCharge + earlyCheckInCharge
                val advance = ws.advancePayment.toDoubleOrNull() ?: 0.0
                billingRepo.add(BillingInvoice(
                    stayId = stayId,
                    guestName = primaryName,
                    roomNumber = ws.selectedInstanceNumber,
                    roomCharges = roomCharge,
                    serviceCharges = breakfastCharge,
                    earlyCheckInCharge = earlyCheckInCharge,
                    totalAmount = total,
                    amountPaid = advance,
                    status = if (advance >= total) "PAID" else if (advance > 0) "PARTIAL" else "PENDING",
                    issuedAt = now,
                ))
                // 4. Handle booking
                val srcBooking = ws.sourceBooking
                if (srcBooking != null) {
                    bookingRepo.update(srcBooking.copy(
                        roomInstanceId = ws.selectedInstanceId,
                        roomNumber = ws.selectedInstanceNumber,
                    ))
                } else {
                    bookingRepo.add(Booking(
                        hotelId = AppContext.hotelId,
                        guestName = primaryName,
                        guestPhone = primaryPhone,
                        roomCategoryId = ws.selectedCategoryId,
                        roomCategoryName = ws.selectedCategoryName,
                        roomInstanceId = ws.selectedInstanceId,
                        roomNumber = ws.selectedInstanceNumber,
                        checkIn = now,
                        checkOut = ws.expectedCheckOut,
                        adults = ws.adults,
                        children = ws.children,
                        status = "COMPLETED",
                        source = "WALK_IN",
                        totalAmount = total,
                        advancePaidAmount = advance,
                        createdAt = now,
                    ))
                }
                stayId
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
            val instances = if (booking.roomCategoryId.isNotBlank()) {
                runCatching { instanceRepo.getByCategory(booking.roomCategoryId, hotelId, includeAssigned = true) }.getOrElse { emptyList() }
            } else emptyList()
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
                selectedInstanceId = preAssignedInstance?.id ?: "",
                selectedInstanceNumber = preAssignedInstance?.roomNumber ?: "",
                expectedCheckOut = booking.checkOut,
                adults = booking.adults,
                children = booking.children,
                categories = categories,
                availableInstances = instances,
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
            val lateCheckoutCharge = if (isLateCheckout && hotel?.lateCheckOutAllowed == true) hotel.lateCheckOutPrice else 0.0
            _checkOutState.value = CheckOutState(
                isOpen = true, stay = stay, bill = bill,
                isLateCheckout = isLateCheckout, lateCheckoutCharge = lateCheckoutCharge,
            )
        }
    }

    fun closeCheckOut() {
        _checkOutState.value = CheckOutState()
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
                _checkOutState.value = CheckOutState(navigateToBilling = true)
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
            _addOrderState.update { it.copy(isLoadingCatalog = false, catalogItems = catalog, category = firstCategory) }
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
                                (s.category.isBlank() || item.category.split(",").map(String::trim).any { it == s.category })
                        }.take(6)
                        entry.copy(suggestions = newSuggestions, showSuggestions = newSuggestions.isNotEmpty())
                    }
                }
                s.copy(catalogItems = catalog, items = updatedItems)
            }
        }
    }

    fun onAddOrderCategory(v: String) = _addOrderState.update { s ->
        // Clear suggestions when category changes
        s.copy(category = v, items = s.items.map { it.copy(suggestions = emptyList(), showSuggestions = false) })
    }
    fun onAddOrderNotes(v: String) = _addOrderState.update { it.copy(notes = v) }

    fun onAddOrderItemName(index: Int, v: String) = _addOrderState.update { s ->
        val suggestions = if (v.isBlank()) emptyList() else {
            val query = v.trim().lowercase()
            s.catalogItems.filter { item ->
                item.name.lowercase().contains(query) &&
                    (s.category.isBlank() || item.category.split(",").map(String::trim).any { it == s.category })
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
    fun addOrderItem() = _addOrderState.update { it.copy(items = it.items + OrderItemEntry()) }
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
            val orderType = if (s.category == "Services") "SERVICE" else "ROOM_SERVICE"
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
