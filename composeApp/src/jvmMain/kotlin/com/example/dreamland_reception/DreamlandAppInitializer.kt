package com.example.dreamland_reception

import com.example.dreamland_reception.data.firebase.FirebaseManager
import com.example.dreamland_reception.data.repository.FirestoreBillRepository
import com.example.dreamland_reception.data.repository.FirestoreBillingRepository
import com.example.dreamland_reception.data.repository.FirestoreBookingRepository
import com.example.dreamland_reception.data.repository.FirestoreComplaintRepository
import com.example.dreamland_reception.data.repository.FirestoreComplaintTypeRepository
import com.example.dreamland_reception.data.repository.FirestoreFoodItemRepository
import com.example.dreamland_reception.data.repository.FirestoreHotelRepository
import com.example.dreamland_reception.data.repository.FirestoreHotelSettingsRepository
import com.example.dreamland_reception.data.repository.FirestoreOrderRepository
import com.example.dreamland_reception.data.repository.FirestoreRoomInstanceRepository
import com.example.dreamland_reception.data.repository.FirestoreRoomRepository
import com.example.dreamland_reception.data.repository.FirestoreServiceRepository
import com.example.dreamland_reception.data.repository.FirestoreStaffRepository
import com.example.dreamland_reception.data.repository.FirestoreStayRepository
import com.example.dreamland_reception.ui.viewmodel.AvailabilityViewModel
import com.example.dreamland_reception.ui.viewmodel.BillingViewModel
import com.example.dreamland_reception.ui.viewmodel.BookingsViewModel
import com.example.dreamland_reception.ui.viewmodel.StayBillingViewModel
import com.example.dreamland_reception.ui.viewmodel.ComplaintsViewModel
import com.example.dreamland_reception.ui.viewmodel.DashboardViewModel
import com.example.dreamland_reception.ui.viewmodel.OrdersViewModel
import com.example.dreamland_reception.ui.viewmodel.ReportsViewModel
import com.example.dreamland_reception.ui.viewmodel.RoomsAndBookingsViewModel
import com.example.dreamland_reception.ui.viewmodel.SettingsViewModel
import com.example.dreamland_reception.ui.viewmodel.StaffViewModel
import com.example.dreamland_reception.ui.viewmodel.StaysViewModel

/**
 * Application bootstrap (same role as Gagan Jewellers `JewelryAppInitializer` under `jewellery/`).
 * Wires Firestore into singleton repository objects, then exposes app-wide ViewModel instances.
 */
object DreamlandAppInitializer {

    @Volatile
    private var reposWired = false

    @Volatile
    private var dashboardVm: DashboardViewModel? = null

    @Volatile
    private var bookingsVm: BookingsViewModel? = null

    @Volatile
    private var roomsAndBookingsVm: RoomsAndBookingsViewModel? = null

    @Volatile
    private var staysVm: StaysViewModel? = null

    @Volatile
    private var billingVm: BillingViewModel? = null

    @Volatile
    private var stayBillingVm: StayBillingViewModel? = null

    @Volatile
    private var ordersVm: OrdersViewModel? = null

    @Volatile
    private var complaintsVm: ComplaintsViewModel? = null

    @Volatile
    private var staffVm: StaffViewModel? = null

    @Volatile
    private var reportsVm: ReportsViewModel? = null

    @Volatile
    private var settingsVm: SettingsViewModel? = null

    @Volatile
    private var availabilityVm: AvailabilityViewModel? = null

    /** @param serviceAccountJsonPath Optional path to service account JSON (see `claude.md`). */
    fun initialize(serviceAccountJsonPath: String? = null) {
        FirebaseManager.initialize(serviceAccountJsonPath)
        synchronized(this) {
            if (reposWired) return
            runCatching {
                val fs = FirebaseManager.requireFirestore()
                FirestoreBookingRepository.initialize(fs)
                FirestoreRoomRepository.initialize(fs)
                FirestoreRoomInstanceRepository.initialize(fs)
                FirestoreStayRepository.initialize(fs)
                FirestoreBillingRepository.initialize(fs)
                FirestoreBillRepository.initialize(fs)
                FirestoreOrderRepository.initialize(fs)
                FirestoreComplaintRepository.initialize(fs)
                FirestoreStaffRepository.initialize(fs)
                FirestoreHotelRepository.initialize(fs)
                FirestoreHotelSettingsRepository.initialize(fs)
                FirestoreServiceRepository.initialize(fs)
                FirestoreFoodItemRepository.initialize(fs)
                FirestoreComplaintTypeRepository.initialize(fs)
            }.onSuccess {
                reposWired = true
            }
        }
    }

    fun refreshAllViewModels() {
        getDashboardViewModel().load()
        getBookingsViewModel().load()
        getRoomsAndBookingsViewModel().refresh()
        getStaysViewModel().loadActive()
        getBillingViewModel().load()
        getOrdersViewModel().loadPending()
        getComplaintsViewModel().loadOpen()
        getStaffViewModel().loadActive()
        getReportsViewModel().load()
        getSettingsViewModel().refresh()
    }

    fun getDashboardViewModel(): DashboardViewModel =
        dashboardVm ?: synchronized(this) {
            dashboardVm ?: DashboardViewModel().also { dashboardVm = it }
        }

    fun getBookingsViewModel(): BookingsViewModel =
        bookingsVm ?: synchronized(this) {
            bookingsVm ?: BookingsViewModel().also { bookingsVm = it }
        }

    fun getRoomsAndBookingsViewModel(): RoomsAndBookingsViewModel =
        roomsAndBookingsVm ?: synchronized(this) {
            roomsAndBookingsVm ?: RoomsAndBookingsViewModel().also { roomsAndBookingsVm = it }
        }

    fun getStaysViewModel(): StaysViewModel =
        staysVm ?: synchronized(this) {
            staysVm ?: StaysViewModel().also { staysVm = it }
        }

    fun getBillingViewModel(): BillingViewModel =
        billingVm ?: synchronized(this) {
            billingVm ?: BillingViewModel().also { billingVm = it }
        }

    fun getStayBillingViewModel(): StayBillingViewModel =
        stayBillingVm ?: synchronized(this) {
            stayBillingVm ?: StayBillingViewModel().also { stayBillingVm = it }
        }

    fun getOrdersViewModel(): OrdersViewModel =
        ordersVm ?: synchronized(this) {
            ordersVm ?: OrdersViewModel().also { ordersVm = it }
        }

    fun getComplaintsViewModel(): ComplaintsViewModel =
        complaintsVm ?: synchronized(this) {
            complaintsVm ?: ComplaintsViewModel().also { complaintsVm = it }
        }

    fun getStaffViewModel(): StaffViewModel =
        staffVm ?: synchronized(this) {
            staffVm ?: StaffViewModel().also { staffVm = it }
        }

    fun getReportsViewModel(): ReportsViewModel =
        reportsVm ?: synchronized(this) {
            reportsVm ?: ReportsViewModel().also { reportsVm = it }
        }

    fun getSettingsViewModel(): SettingsViewModel =
        settingsVm ?: synchronized(this) {
            settingsVm ?: SettingsViewModel().also { settingsVm = it }
        }

    fun getAvailabilityViewModel(): AvailabilityViewModel =
        availabilityVm ?: synchronized(this) {
            availabilityVm ?: AvailabilityViewModel().also { availabilityVm = it }
        }
}
