package com.example.dreamland_reception.ui.viewmodel

import androidx.lifecycle.ViewModel
import com.example.dreamland_reception.data.repository.BillingRepository
import com.example.dreamland_reception.data.repository.BookingRepository
import com.example.dreamland_reception.data.repository.ComplaintRepository
import com.example.dreamland_reception.data.repository.FirestoreBillingRepository
import com.example.dreamland_reception.data.repository.FirestoreBookingRepository
import com.example.dreamland_reception.data.repository.FirestoreComplaintRepository
import com.example.dreamland_reception.data.repository.FirestoreOrderRepository
import com.example.dreamland_reception.data.repository.OrderRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class ReportSummary(
    val totalRevenue: Double = 0.0,
    val totalBookings: Int = 0,
    val totalOrders: Int = 0,
    val openComplaints: Int = 0,
    val unpaidInvoices: Int = 0,
)

sealed interface ReportsUiState {
    data object Loading : ReportsUiState
    data class Success(val summary: ReportSummary) : ReportsUiState
    data class Error(val message: String) : ReportsUiState
}

class ReportsViewModel(
    private val bookingRepo: BookingRepository = FirestoreBookingRepository,
    private val billingRepo: BillingRepository = FirestoreBillingRepository,
    private val orderRepo: OrderRepository = FirestoreOrderRepository,
    private val complaintRepo: ComplaintRepository = FirestoreComplaintRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow<ReportsUiState>(ReportsUiState.Loading)
    val uiState: StateFlow<ReportsUiState> = _uiState.asStateFlow()

    init {
        load()
    }

    fun load() {
        launchWithGlobalLoading {
            _uiState.value = ReportsUiState.Loading
            runCatching {
                val invoices = billingRepo.getAll()
                val bookings = bookingRepo.getAll()
                val orders = orderRepo.getAll()
                val complaints = complaintRepo.getOpen()
                ReportSummary(
                    totalRevenue = invoices.filter { it.status == "PAID" }.sumOf { it.totalAmount },
                    totalBookings = bookings.size,
                    totalOrders = orders.size,
                    openComplaints = complaints.size,
                    unpaidInvoices = invoices.count { it.status == "PENDING" },
                )
            }
                .onSuccess { _uiState.value = ReportsUiState.Success(it) }
                .onFailure { _uiState.value = ReportsUiState.Error(it.message ?: "Unknown error") }
        }
    }
}
