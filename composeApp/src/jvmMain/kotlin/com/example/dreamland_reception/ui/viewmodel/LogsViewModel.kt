package com.example.dreamland_reception.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.dreamland_reception.DreamlandAppInitializer
import com.example.dreamland_reception.data.AppContext
import com.example.dreamland_reception.data.billing.HumbleBillEngine
import com.example.dreamland_reception.data.model.Bill
import com.example.dreamland_reception.data.model.GuestRecord
import com.example.dreamland_reception.data.model.Stay
import com.example.dreamland_reception.data.repository.BillRepository
import com.example.dreamland_reception.data.repository.FirestoreBillRepository
import com.example.dreamland_reception.data.repository.FirestoreStayRepository
import com.example.dreamland_reception.data.repository.StayRepository
import com.example.dreamland_reception.grc.GrcRenderer
import com.example.dreamland_reception.report.LogsReportRenderer
import com.example.dreamland_reception.report.LogsReportRow
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.awt.image.BufferedImage
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

private fun startOfDay(d: Date): Date = Calendar.getInstance().apply {
    time = d; set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
}.time

private fun endOfDay(d: Date): Date = Calendar.getInstance().apply {
    time = d; set(Calendar.HOUR_OF_DAY, 23); set(Calendar.MINUTE, 59); set(Calendar.SECOND, 59); set(Calendar.MILLISECOND, 999)
}.time

private fun daysAgo(n: Int): Date = Calendar.getInstance().apply { add(Calendar.DAY_OF_YEAR, -n) }.time

/** Guest in/out logs — a read-only view over the `stays` collection, with drill-down to the stay's bill. */
data class LogsState(
    val isLoading: Boolean = true,
    val error: String? = null,
    val stays: List<Stay> = emptyList(),
    val searchQuery: String = "",

    // Stay detail dialog
    val selectedStay: Stay? = null,

    // Bill (financial summary shown inside the stay detail; loaded when a stay opens)
    val billLoading: Boolean = false,
    val billError: String? = null,
    val bill: Bill? = null,

    // Guest detail dialog (from the stay's own guest records)
    val selectedGuest: GuestRecord? = null,

    // Invoice PDF viewer (stored invoiceUrl rendered on demand)
    val invoiceOpen: Boolean = false,
    val invoiceLoading: Boolean = false,
    val invoiceError: String? = null,
    val invoicePages: List<BufferedImage> = emptyList(),

    // Print-logs report (landscape guest register)
    val reportOpen: Boolean = false,
    val reportFrom: Date = startOfDay(daysAgo(7)),
    val reportTo: Date = endOfDay(Date()),
    val reportGenerating: Boolean = false,
    val reportError: String? = null,
    val reportPages: List<BufferedImage> = emptyList(),
    val reportPdf: ByteArray? = null,
    val reportPrinters: List<String> = emptyList(),
    val reportSelectedPrinter: String = "",
    val reportStatus: String? = null,
) {
    val filtered: List<Stay>
        get() = if (searchQuery.isBlank()) stays
        else stays.filter {
            it.guestName.contains(searchQuery, ignoreCase = true) ||
                it.roomNumber.contains(searchQuery, ignoreCase = true) ||
                it.guestPhone.contains(searchQuery, ignoreCase = true)
        }
}

class LogsViewModel(
    private val stayRepo: StayRepository = FirestoreStayRepository,
    private val billRepo: BillRepository = FirestoreBillRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(LogsState())
    val state: StateFlow<LogsState> = _state.asStateFlow()

    init { load() }

    fun load() {
        viewModelScope.launch {
            _state.update { it.copy(isLoading = true, error = null) }
            runCatching { stayRepo.getAll(AppContext.hotelId) }
                .onSuccess { list ->
                    val sorted = list.sortedByDescending { it.trueCheckIn ?: it.checkInActual }
                    _state.update { it.copy(isLoading = false, stays = sorted) }
                }
                .onFailure { e -> _state.update { it.copy(isLoading = false, error = e.message ?: "Failed to load logs") } }
        }
    }

    fun onSearch(q: String) = _state.update { it.copy(searchQuery = q) }

    /** Opens the stay detail and eagerly loads its bill so the financial summary shows inline. */
    fun openStay(stay: Stay) {
        _state.update { it.copy(selectedStay = stay, bill = null, billError = null, billLoading = true) }
        viewModelScope.launch {
            val bill = runCatching { billRepo.getByStay(stay.id) }.getOrNull()
            _state.update {
                it.copy(billLoading = false, bill = bill, billError = if (bill == null) "No bill found for this stay yet." else null)
            }
        }
    }

    fun closeStay() = _state.update {
        it.copy(selectedStay = null, bill = null, billError = null, billLoading = false, selectedGuest = null)
    }

    fun openGuest(guest: GuestRecord) = _state.update { it.copy(selectedGuest = guest) }
    fun closeGuest() = _state.update { it.copy(selectedGuest = null) }

    /** Opens the invoice PDF viewer and renders the stored invoiceUrl (never regenerates it). */
    fun openInvoice() {
        val bill = _state.value.bill ?: return
        _state.update { it.copy(invoiceOpen = true, invoiceError = null, invoicePages = emptyList(), invoiceLoading = bill.invoiceUrl.isNotBlank()) }
        if (bill.invoiceUrl.isBlank()) return
        viewModelScope.launch {
            runCatching { HumbleBillEngine.renderPdfPages(bill.invoiceUrl) }
                .onSuccess { pages -> _state.update { it.copy(invoiceLoading = false, invoicePages = pages) } }
                .onFailure { e -> _state.update { it.copy(invoiceLoading = false, invoiceError = e.message ?: "Could not load the invoice PDF") } }
        }
    }

    fun closeInvoice() = _state.update {
        it.copy(invoiceOpen = false, invoiceLoading = false, invoiceError = null, invoicePages = emptyList())
    }

    // ── Print logs report ──────────────────────────────────────────────────────

    fun openReport() {
        _state.update { it.copy(reportOpen = true, reportError = null, reportStatus = null) }
        viewModelScope.launch(Dispatchers.IO) {
            val names = javax.print.PrintServiceLookup.lookupPrintServices(null, null).map { it.name }
            _state.update { it.copy(reportPrinters = names, reportSelectedPrinter = it.reportSelectedPrinter.ifBlank { names.firstOrNull() ?: "" }) }
        }
    }

    fun closeReport() = _state.update {
        it.copy(reportOpen = false, reportGenerating = false, reportError = null, reportStatus = null, reportPages = emptyList(), reportPdf = null)
    }

    fun setReportFrom(d: Date) = _state.update { it.copy(reportFrom = startOfDay(d), reportPages = emptyList(), reportPdf = null, reportStatus = null) }
    fun setReportTo(d: Date) = _state.update { it.copy(reportTo = endOfDay(d), reportPages = emptyList(), reportPdf = null, reportStatus = null) }
    fun selectReportPrinter(name: String) = _state.update { it.copy(reportSelectedPrinter = name) }

    /** Builds the guest-register PDF (one row per guest) for the selected date range and renders a preview. */
    fun generateReport() {
        val s = _state.value
        val from = s.reportFrom
        val to = s.reportTo
        _state.update { it.copy(reportGenerating = true, reportError = null, reportStatus = null, reportPages = emptyList(), reportPdf = null) }
        viewModelScope.launch {
            runCatching {
                val hotel = DreamlandAppInitializer.getSettingsViewModel().state.value.selectedHotel
                val dtFull = SimpleDateFormat("dd MMM yyyy, hh:mm a", Locale.getDefault())
                val dOnly = SimpleDateFormat("dd MMM yyyy", Locale.getDefault())
                val inRange = s.stays
                    .filter { val t = it.trueCheckIn ?: it.checkInActual; !t.before(from) && !t.after(to) }
                    .sortedBy { it.trueCheckIn ?: it.checkInActual }
                val rows = inRange.flatMap { stay ->
                    val checkIn = dtFull.format(stay.trueCheckIn ?: stay.checkInActual)
                    val checkOut = stay.checkOutActual?.let { dtFull.format(it) } ?: "In-house"
                    val guests = stay.guests.ifEmpty { listOf(GuestRecord(name = stay.guestName, phone = stay.guestPhone)) }
                    guests.map { g ->
                        LogsReportRow(
                            room = stay.roomNumber,
                            guestName = g.name.ifBlank { stay.guestName },
                            phone = g.phone.ifBlank { stay.guestPhone },
                            gender = g.gender,
                            dobAge = listOfNotNull(g.dob.takeIf { it.isNotBlank() }, g.age.takeIf { it > 0 }?.let { "Age $it" }).joinToString(" · "),
                            idType = g.idType,
                            idNumber = g.govIdNumber,
                            purpose = g.purpose,
                            address = g.address,
                            checkIn = checkIn,
                            checkOut = checkOut,
                        )
                    }
                }
                val pdf = LogsReportRenderer.renderPdf(
                    hotelName = hotel?.name?.takeIf { it.isNotBlank() } ?: AppContext.hotelName,
                    hotelAddress = listOfNotNull(hotel?.address?.takeIf { it.isNotBlank() }, hotel?.city?.takeIf { it.isNotBlank() }).joinToString(", "),
                    hotelPhone = hotel?.contactPhone ?: "",
                    rangeLabel = "${dOnly.format(from)} – ${dOnly.format(to)}",
                    generatedAt = dtFull.format(Date()),
                    rows = rows,
                )
                pdf to GrcRenderer.pagesOf(pdf)
            }.onSuccess { (pdf, pages) ->
                _state.update { it.copy(reportGenerating = false, reportPdf = pdf, reportPages = pages) }
            }.onFailure { e ->
                _state.update { it.copy(reportGenerating = false, reportError = e.message ?: "Failed to generate report") }
            }
        }
    }

    fun printReport() {
        val s = _state.value
        val pdf = s.reportPdf ?: return
        if (s.reportSelectedPrinter.isBlank()) { _state.update { it.copy(reportStatus = "Select a printer first") }; return }
        viewModelScope.launch(Dispatchers.IO) {
            runCatching { GrcRenderer.print(pdf, s.reportSelectedPrinter) }
                .onSuccess { _state.update { it.copy(reportStatus = "Sent to printer ✓") } }
                .onFailure { e -> _state.update { it.copy(reportStatus = e.message ?: "Print failed") } }
        }
    }

    fun saveReport() {
        val pdf = _state.value.reportPdf ?: return
        viewModelScope.launch(Dispatchers.IO) {
            runCatching {
                val downloads = java.io.File(System.getProperty("user.home"), "Downloads")
                val dir = if (downloads.isDirectory) downloads else java.io.File(System.getProperty("java.io.tmpdir"))
                val file = java.io.File(dir, "GuestLogs-${System.currentTimeMillis()}.pdf")
                file.writeBytes(pdf)
                runCatching { java.awt.Desktop.getDesktop().open(file) }
                file.absolutePath
            }.onSuccess { path -> _state.update { it.copy(reportStatus = "Saved: $path") } }
                .onFailure { e -> _state.update { it.copy(reportStatus = e.message ?: "Save failed") } }
        }
    }
}
