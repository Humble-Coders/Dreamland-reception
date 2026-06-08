package com.example.dreamland_reception.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.dreamland_reception.DreamlandAppInitializer
import com.example.dreamland_reception.data.AppContext
import com.example.dreamland_reception.data.repository.FirestoreLiquidityRepository
import com.example.dreamland_reception.data.repository.LiquidityBalance
import com.example.dreamland_reception.data.repository.RegisterTransaction
import com.example.dreamland_reception.grc.GrcRenderer
import com.example.dreamland_reception.report.DailyBookReportRenderer
import com.example.dreamland_reception.report.DailyBookReportRow
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

data class DailyBookState(
    val selectedDate: Date = Date(),
    val isLoading: Boolean = true,
    val error: String? = null,
    val rows: List<RegisterTransaction> = emptyList(),
    // Day summary (per account + computed).
    val openingCash: Double = 0.0, val openingBank: Double = 0.0,
    val inCash: Double = 0.0, val inBank: Double = 0.0,
    val outCash: Double = 0.0, val outBank: Double = 0.0,
    val closingCash: Double = 0.0, val closingBank: Double = 0.0,
    // Print dialog state.
    val reportOpen: Boolean = false,
    val reportGenerating: Boolean = false,
    val reportPdf: ByteArray? = null,
    val reportPages: List<BufferedImage> = emptyList(),
    val reportPrinters: List<String> = emptyList(),
    val reportSelectedPrinter: String = "",
    val reportError: String? = null,
    val reportStatus: String? = null,
) {
    val openingTotal get() = openingCash + openingBank
    val inTotal get() = inCash + inBank
    val outTotal get() = outCash + outBank
    val closingTotal get() = closingCash + closingBank
}

/** Plain-language label for a register movement. */
fun categoryOf(t: RegisterTransaction): String = when {
    t.type == "MANUAL" && t.direction == "IN" -> "Cash added"
    t.type == "MANUAL" -> "Misc. expense"
    t.direction == "IN" -> "Money received"
    else -> "Money paid out"
}

class DailyBookViewModel(
    private val repo: FirestoreLiquidityRepository = FirestoreLiquidityRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(DailyBookState())
    val state: StateFlow<DailyBookState> = _state.asStateFlow()

    init { load() }

    fun setDate(date: Date) {
        _state.update { it.copy(selectedDate = date) }
        load()
    }

    fun shiftDay(deltaDays: Int) {
        val cal = Calendar.getInstance().apply { time = _state.value.selectedDate; add(Calendar.DAY_OF_YEAR, deltaDays) }
        setDate(cal.time)
    }

    fun load() {
        val day = _state.value.selectedDate
        _state.update { it.copy(isLoading = true, error = null) }
        viewModelScope.launch {
            val start = startOfDay(day)
            val end = startOfDay(addDays(day, 1))   // exclusive upper bound
            val hotelId = AppContext.hotelId
            val txns = runCatching { repo.getRegisterTransactions(start, end, hotelId) }
                .getOrElse {
                    _state.update { s -> s.copy(isLoading = false, error = it.message ?: "Failed to load") }
                    return@launch
                }
            val liq = runCatching { repo.getBalance() }.getOrNull() ?: LiquidityBalance()
            val isToday = sameDay(day, Date())

            val inCash = txns.filter { it.account == "cash" && it.direction == "IN" }.sumOf { it.amount }
            val outCash = txns.filter { it.account == "cash" && it.direction == "OUT" }.sumOf { it.amount }
            val inBank = txns.filter { it.account == "bank" && it.direction == "IN" }.sumOf { it.amount }
            val outBank = txns.filter { it.account == "bank" && it.direction == "OUT" }.sumOf { it.amount }

            // Closing = the live till for today (the authoritative real balance), else the day's last
            // running balance. Opening is derived so opening + in − out = closing exactly.
            val closingCash = if (isToday) liq.cash else txns.lastOrNull { it.account == "cash" }?.balanceAfter ?: 0.0
            val closingBank = if (isToday) liq.bank else txns.lastOrNull { it.account == "bank" }?.balanceAfter ?: 0.0
            val openingCash = closingCash - (inCash - outCash)
            val openingBank = closingBank - (inBank - outBank)

            _state.update {
                it.copy(
                    isLoading = false, error = null, rows = txns,
                    openingCash = openingCash, openingBank = openingBank,
                    inCash = inCash, inBank = inBank, outCash = outCash, outBank = outBank,
                    closingCash = closingCash, closingBank = closingBank,
                )
            }
        }
    }

    // ── Print ───────────────────────────────────────────────────────────────────

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

    fun selectReportPrinter(name: String) = _state.update { it.copy(reportSelectedPrinter = name) }

    fun generateReport() {
        val s = _state.value
        _state.update { it.copy(reportGenerating = true, reportError = null, reportStatus = null, reportPages = emptyList(), reportPdf = null) }
        viewModelScope.launch {
            runCatching {
                val hotel = DreamlandAppInitializer.getSettingsViewModel().state.value.selectedHotel
                val tFmt = SimpleDateFormat("hh:mm a", Locale.getDefault())
                val dFmt = SimpleDateFormat("EEEE, dd MMM yyyy", Locale.getDefault())
                val genFmt = SimpleDateFormat("dd MMM yyyy, hh:mm a", Locale.getDefault())
                val rows = s.rows.map { t ->
                    DailyBookReportRow(
                        time = tFmt.format(t.createdAt),
                        particulars = t.description.ifBlank { "—" },
                        category = categoryOf(t),
                        by = t.manager,
                        account = if (t.account == "bank") "Bank" else "Cash",
                        inAmount = if (t.direction == "IN") t.amount else 0.0,
                        outAmount = if (t.direction == "OUT") t.amount else 0.0,
                        balance = t.balanceAfter,
                    )
                }
                val pdf = DailyBookReportRenderer.renderPdf(
                    hotelName = hotel?.name?.takeIf { it.isNotBlank() } ?: AppContext.hotelName,
                    hotelAddress = listOfNotNull(hotel?.address?.takeIf { it.isNotBlank() }, hotel?.city?.takeIf { it.isNotBlank() }).joinToString(", "),
                    hotelPhone = hotel?.contactPhone ?: "",
                    dateLabel = dFmt.format(s.selectedDate),
                    generatedAt = genFmt.format(Date()),
                    openingCash = s.openingCash, openingBank = s.openingBank,
                    inCash = s.inCash, inBank = s.inBank,
                    outCash = s.outCash, outBank = s.outBank,
                    closingCash = s.closingCash, closingBank = s.closingBank,
                    rows = rows,
                )
                pdf to GrcRenderer.pagesOf(pdf)
            }.onSuccess { (pdf, pages) ->
                _state.update { it.copy(reportGenerating = false, reportPdf = pdf, reportPages = pages) }
            }.onFailure { e ->
                _state.update { it.copy(reportGenerating = false, reportError = e.message ?: "Failed to generate") }
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
                val file = java.io.File(dir, "DailyBook-${System.currentTimeMillis()}.pdf")
                file.writeBytes(pdf)
                runCatching { java.awt.Desktop.getDesktop().open(file) }
                file.absolutePath
            }.onSuccess { path -> _state.update { it.copy(reportStatus = "Saved: $path") } }
                .onFailure { e -> _state.update { it.copy(reportStatus = e.message ?: "Save failed") } }
        }
    }

    private fun startOfDay(d: Date): Date = Calendar.getInstance().apply {
        time = d; set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
    }.time

    private fun addDays(d: Date, days: Int): Date = Calendar.getInstance().apply { time = d; add(Calendar.DAY_OF_YEAR, days) }.time

    private fun sameDay(a: Date, b: Date): Boolean {
        val ca = Calendar.getInstance().apply { time = a }
        val cb = Calendar.getInstance().apply { time = b }
        return ca.get(Calendar.YEAR) == cb.get(Calendar.YEAR) && ca.get(Calendar.DAY_OF_YEAR) == cb.get(Calendar.DAY_OF_YEAR)
    }
}
