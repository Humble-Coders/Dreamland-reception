package com.example.dreamland_reception.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.dreamland_reception.data.AppContext
import com.example.dreamland_reception.data.model.BillingInvoice
import com.example.dreamland_reception.data.model.Complaint
import com.example.dreamland_reception.data.model.Order
import com.example.dreamland_reception.data.model.RoomInstance
import com.example.dreamland_reception.data.model.Stay
import com.example.dreamland_reception.data.repository.BillingRepository
import com.example.dreamland_reception.data.repository.ComplaintRepository
import com.example.dreamland_reception.data.repository.FirestoreBillingRepository
import com.example.dreamland_reception.data.repository.FirestoreComplaintRepository
import com.example.dreamland_reception.data.repository.FirestoreOrderRepository
import com.example.dreamland_reception.data.repository.FirestoreRoomInstanceRepository
import com.example.dreamland_reception.data.repository.FirestoreStayRepository
import com.example.dreamland_reception.data.repository.OrderRepository
import com.example.dreamland_reception.data.repository.RoomInstanceRepository
import com.example.dreamland_reception.data.repository.StayRepository
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.Calendar
import java.util.Date

// ── Helpers ────────────────────────────────────────────────────────────────────

fun todayMidnight(): Date = Calendar.getInstance().apply {
    set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0)
    set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
}.time

// ── Data classes ──────────────────────────────────────────────────────────────

data class DayTrendPoint(
    val label: String,
    val date: Date,
    val revenue: Double,
    val occupancyRate: Float,
)

data class RoomStatusBreakdown(
    val total: Int = 0,
    val available: Int = 0,
    val occupied: Int = 0,
    val assigned: Int = 0,
    val cleaning: Int = 0,
    val maintenance: Int = 0,
)

enum class AlertType { NEW_ORDER, HIGH_COMPLAINT, CLEANING_ROOM, PENDING_BILL }

data class DashboardAlert(
    val id: String,
    val type: AlertType,
    val title: String,
    val subtitle: String,
    val priority: String = "MEDIUM",
)

data class ActiveStayRow(
    val stayId: String,
    val roomNumber: String,
    val guestName: String,
    val expectedCheckOut: Date,
    val isOverdue: Boolean,
)

data class DashboardState(
    val isLoading: Boolean = true,
    val error: String? = null,
    val selectedDate: Date = todayMidnight(),
    val isToday: Boolean = true,
    // KPIs
    val revenueToday: Double = 0.0,
    val occupancyRate: Float = 0f,
    val checkInsCount: Int = 0,
    val checkOutsCount: Int = 0,
    val pendingPaymentsCount: Int = 0,
    val pendingPaymentsAmount: Double = 0.0,
    val activeComplaintsCount: Int = 0,
    // Room breakdown
    val roomStatus: RoomStatusBreakdown = RoomStatusBreakdown(),
    // Alerts & activity
    val alerts: List<DashboardAlert> = emptyList(),
    // Active stays snapshot
    val activeStays: List<ActiveStayRow> = emptyList(),
    // Trends
    val trendPoints: List<DayTrendPoint> = emptyList(),
)

// ── ViewModel ─────────────────────────────────────────────────────────────────

class DashboardViewModel(
    private val stayRepo: StayRepository = FirestoreStayRepository,
    private val orderRepo: OrderRepository = FirestoreOrderRepository,
    private val complaintRepo: ComplaintRepository = FirestoreComplaintRepository,
    private val roomInstanceRepo: RoomInstanceRepository = FirestoreRoomInstanceRepository,
    private val billingRepo: BillingRepository = FirestoreBillingRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(DashboardState())
    val state: StateFlow<DashboardState> = _state.asStateFlow()

    // Internal snapshot flows
    private val _stays = MutableStateFlow<List<Stay>>(emptyList())
    private val _orders = MutableStateFlow<List<Order>>(emptyList())
    private val _complaints = MutableStateFlow<List<Complaint>>(emptyList())
    private val _rooms = MutableStateFlow<List<RoomInstance>>(emptyList())
    private val _allInvoices = MutableStateFlow<List<BillingInvoice>>(emptyList())
    private val _hotelInvoices = MutableStateFlow<List<BillingInvoice>>(emptyList())

    // Listener job refs (cancelled on date change)
    private var staysJob: Job? = null
    private var ordersJob: Job? = null
    private var complaintsJob: Job? = null
    private var roomsJob: Job? = null

    init {
        load()
    }

    fun load() {
        cancelAllListeners()
        val date = _state.value.selectedDate
        val isToday = isSameDay(date, todayMidnight())
        _state.update { it.copy(isLoading = true, isToday = isToday) }
        if (isToday) startRealtimeListeners() else fetchForDate(date)
    }

    fun onDateSelected(date: Date) {
        val midnight = Calendar.getInstance().apply {
            time = date
            set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
        }.time
        _state.update { it.copy(selectedDate = midnight) }
        load()
    }

    fun clearError() = _state.update { it.copy(error = null) }

    // ── Listener management ───────────────────────────────────────────────────

    private fun cancelAllListeners() {
        staysJob?.cancel(); staysJob = null
        ordersJob?.cancel(); ordersJob = null
        complaintsJob?.cancel(); complaintsJob = null
        roomsJob?.cancel(); roomsJob = null
    }

    private fun startRealtimeListeners() {
        val hotelId = AppContext.hotelId
        val date = _state.value.selectedDate

        staysJob = viewModelScope.launch {
            stayRepo.listenActive(hotelId).collect { stays ->
                _stays.value = stays
                recomputeHotelInvoices()
                recompute(date, isToday = true)
            }
        }
        ordersJob = viewModelScope.launch {
            orderRepo.listenByHotel(hotelId).collect { orders ->
                _orders.value = orders
                recompute(date, isToday = true)
            }
        }
        complaintsJob = viewModelScope.launch {
            complaintRepo.listenByHotel(hotelId).collect { complaints ->
                _complaints.value = complaints
                recompute(date, isToday = true)
            }
        }
        roomsJob = viewModelScope.launch {
            roomInstanceRepo.listenByHotel(hotelId).collect { rooms ->
                _rooms.value = rooms
                recompute(date, isToday = true)
            }
        }
        // Billing: no real-time listener available — fetch once on load
        viewModelScope.launch {
            runCatching { billingRepo.getAll() }
                .onSuccess { invoices ->
                    _allInvoices.value = invoices
                    recomputeHotelInvoices()
                    recompute(date, isToday = true)
                }
                .onFailure { e -> _state.update { it.copy(error = e.message) } }
        }
    }

    private fun fetchForDate(date: Date) {
        val hotelId = AppContext.hotelId
        launchWithGlobalLoading {
            runCatching {
                _stays.value = stayRepo.getAll(hotelId)
                _orders.value = orderRepo.getByHotel(hotelId)
                _complaints.value = complaintRepo.getByHotel(hotelId)
                _rooms.value = roomInstanceRepo.listenByHotel(hotelId).first()
                _allInvoices.value = billingRepo.getAll()
            }
            .onSuccess {
                recomputeHotelInvoices()
                recompute(date, isToday = false)
            }
            .onFailure { e -> _state.update { it.copy(error = e.message, isLoading = false) } }
        }
    }

    // ── Derivation helpers ────────────────────────────────────────────────────

    private fun recomputeHotelInvoices() {
        val stayIds = _stays.value.map { it.id }.toSet()
        _hotelInvoices.value = _allInvoices.value.filter { it.stayId in stayIds }
    }

    private fun recompute(date: Date, isToday: Boolean) {
        val stays = _stays.value
        val orders = _orders.value
        val complaints = _complaints.value
        val rooms = _rooms.value
        val hotelInvoices = _hotelInvoices.value
        val now = Date()

        val revenueToday = hotelInvoices.filter { isSameDay(it.issuedAt, date) }.sumOf { it.totalAmount }

        val occupancyRate = if (rooms.isEmpty()) 0f else {
            if (isToday) {
                rooms.count { it.status == "OCCUPIED" }.toFloat() / rooms.size
            } else {
                val noonDate = Calendar.getInstance().apply {
                    time = date; set(Calendar.HOUR_OF_DAY, 12)
                }.time
                stays.count { stay ->
                    stay.checkInActual <= noonDate &&
                        (stay.checkOutActual == null || stay.checkOutActual > noonDate)
                }.toFloat() / rooms.size
            }
        }

        val checkInsCount = stays.count { isSameDay(it.checkInActual, date) }
        val checkOutsCount = stays.count { it.checkOutActual != null && isSameDay(it.checkOutActual, date) }

        val pendingInvoices = hotelInvoices.filter { it.status in listOf("PENDING", "PARTIAL") }

        val roomStatus = RoomStatusBreakdown(
            total = rooms.size,
            available = rooms.count { it.status == "AVAILABLE" },
            occupied = rooms.count { it.status == "OCCUPIED" },
            assigned = rooms.count { it.status == "ASSIGNED" },
            cleaning = rooms.count { it.status == "CLEANING" },
            maintenance = rooms.count { it.status == "MAINTENANCE" },
        )

        val noonForStays = Calendar.getInstance().apply {
            time = date; set(Calendar.HOUR_OF_DAY, 12)
        }.time
        val activeStayRows = if (isToday) {
            stays.map { stay ->
                ActiveStayRow(
                    stayId = stay.id,
                    roomNumber = stay.roomNumber,
                    guestName = stay.guestName,
                    expectedCheckOut = stay.expectedCheckOut,
                    isOverdue = stay.expectedCheckOut.before(now),
                )
            }.sortedBy { it.expectedCheckOut }
        } else {
            stays.filter { stay ->
                stay.checkInActual <= noonForStays &&
                    (stay.checkOutActual == null || stay.checkOutActual!! > noonForStays)
            }.map { stay ->
                ActiveStayRow(
                    stayId = stay.id,
                    roomNumber = stay.roomNumber,
                    guestName = stay.guestName,
                    expectedCheckOut = stay.expectedCheckOut,
                    isOverdue = stay.expectedCheckOut.before(date),
                )
            }.sortedBy { it.expectedCheckOut }
        }

        _state.update {
            it.copy(
                isLoading = false,
                revenueToday = revenueToday,
                occupancyRate = occupancyRate,
                checkInsCount = checkInsCount,
                checkOutsCount = checkOutsCount,
                pendingPaymentsCount = pendingInvoices.size,
                pendingPaymentsAmount = pendingInvoices.sumOf { inv -> inv.totalAmount - inv.amountPaid },
                activeComplaintsCount = complaints.count { it.status in listOf("NEW", "ASSIGNED") },
                roomStatus = roomStatus,
                alerts = buildAlerts(orders, complaints, rooms, pendingInvoices),
                activeStays = activeStayRows,
                trendPoints = computeTrendPoints(),
            )
        }
    }

    private fun buildAlerts(
        orders: List<Order>,
        complaints: List<Complaint>,
        rooms: List<RoomInstance>,
        pendingInvoices: List<BillingInvoice>,
    ): List<DashboardAlert> {
        val alerts = mutableListOf<DashboardAlert>()

        complaints.filter { it.status in listOf("NEW", "ASSIGNED") && it.priority == "HIGH" }
            .take(3).forEach { c ->
                alerts.add(DashboardAlert(
                    id = c.id, type = AlertType.HIGH_COMPLAINT,
                    title = "High Priority Complaint",
                    subtitle = "Room ${c.roomNumber} — ${c.description.take(60).ifBlank { c.type }}",
                    priority = "HIGH",
                ))
            }

        orders.filter { it.status == "NEW" }.take(3).forEach { o ->
            alerts.add(DashboardAlert(
                id = o.id, type = AlertType.NEW_ORDER,
                title = "New Order — Room ${o.roomNumber}",
                subtitle = "${o.guestName} · ₹${o.totalAmount.toLong()}",
                priority = "MEDIUM",
            ))
        }

        val cleaningCount = rooms.count { it.status == "CLEANING" }
        if (cleaningCount > 0) {
            alerts.add(DashboardAlert(
                id = "cleaning_rooms", type = AlertType.CLEANING_ROOM,
                title = "$cleaningCount Room${if (cleaningCount > 1) "s" else ""} Awaiting Cleaning",
                subtitle = "Mark as available once housekeeping is done",
                priority = "LOW",
            ))
        }

        if (pendingInvoices.isNotEmpty()) {
            val pendingAmt = pendingInvoices.sumOf { it.totalAmount - it.amountPaid }
            alerts.add(DashboardAlert(
                id = "pending_bills", type = AlertType.PENDING_BILL,
                title = "${pendingInvoices.size} Pending Payment${if (pendingInvoices.size > 1) "s" else ""}",
                subtitle = "Outstanding: ₹${pendingAmt.toLong()}",
                priority = "MEDIUM",
            ))
        }

        return alerts.take(5)
    }

    private fun computeTrendPoints(): List<DayTrendPoint> {
        return (6 downTo 0).map { daysAgo ->
            val cal = Calendar.getInstance().apply {
                add(Calendar.DAY_OF_YEAR, -daysAgo)
                set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0)
                set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
            }
            val date = cal.time
            val label = when (cal.get(Calendar.DAY_OF_WEEK)) {
                Calendar.MONDAY -> "Mon"; Calendar.TUESDAY -> "Tue"
                Calendar.WEDNESDAY -> "Wed"; Calendar.THURSDAY -> "Thu"
                Calendar.FRIDAY -> "Fri"; Calendar.SATURDAY -> "Sat"
                else -> "Sun"
            }
            val revenue = _hotelInvoices.value.filter { isSameDay(it.issuedAt, date) }.sumOf { it.totalAmount }
            val noonDate = Calendar.getInstance().apply {
                time = date; set(Calendar.HOUR_OF_DAY, 12)
            }.time
            val occupancyRate = if (_rooms.value.isEmpty()) 0f else {
                _stays.value.count { stay ->
                    stay.checkInActual <= noonDate &&
                        (stay.checkOutActual == null || stay.checkOutActual > noonDate)
                }.toFloat() / _rooms.value.size
            }
            DayTrendPoint(label = label, date = date, revenue = revenue, occupancyRate = occupancyRate)
        }
    }

    private fun isSameDay(a: Date, b: Date): Boolean {
        val calA = Calendar.getInstance().apply { time = a }
        val calB = Calendar.getInstance().apply { time = b }
        return calA.get(Calendar.YEAR) == calB.get(Calendar.YEAR) &&
            calA.get(Calendar.DAY_OF_YEAR) == calB.get(Calendar.DAY_OF_YEAR)
    }
}
