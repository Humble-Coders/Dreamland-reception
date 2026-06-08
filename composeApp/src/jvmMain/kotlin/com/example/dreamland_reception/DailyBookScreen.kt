@file:OptIn(ExperimentalMaterial3Api::class)

package com.example.dreamland_reception

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Print
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toComposeImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.dreamland_reception.data.repository.RegisterTransaction
import com.example.dreamland_reception.stays.SimpleDatePickerDialog
import com.example.dreamland_reception.ui.viewmodel.DailyBookState
import com.example.dreamland_reception.ui.viewmodel.DailyBookViewModel
import com.example.dreamland_reception.ui.viewmodel.categoryOf
import java.text.SimpleDateFormat
import java.util.Locale

private val timeFmt = SimpleDateFormat("hh:mm a", Locale.getDefault())
private val dayFmt = SimpleDateFormat("EEE, dd MMM yyyy", Locale.getDefault())

private val GREEN = Color(0xFF4CAF50)
private val RED = Color(0xFFEF5350)

private fun money(v: Double): String {
    val r = Math.round(v * 100.0) / 100.0
    return "₹" + if (r == r.toLong().toDouble()) "%,d".format(r.toLong()) else "%,.2f".format(r)
}

@Composable
fun DailyBookScreen(vm: DailyBookViewModel = DreamlandAppInitializer.getDailyBookViewModel()) {
    val state by vm.state.collectAsStateWithLifecycle()
    var showDatePicker by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) { vm.load() }

    Column(Modifier.fillMaxSize().background(DreamlandForest)) {
        // ── Header ──────────────────────────────────────────────────────────────
        Row(
            modifier = Modifier.fillMaxWidth().background(DreamlandForestSurface).padding(horizontal = 24.dp, vertical = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Column {
                Text("CASH & BANK", style = MaterialTheme.typography.labelSmall, color = DreamlandGold, letterSpacing = 2.sp)
                Text("Daily Book", style = MaterialTheme.typography.headlineSmall, color = DreamlandOnDark, fontWeight = FontWeight.Bold)
                Text("${state.rows.size} entr${if (state.rows.size == 1) "y" else "ies"}", style = MaterialTheme.typography.labelMedium, color = DreamlandMuted)
            }
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                // Date stepper
                Row(
                    Modifier.clip(RoundedCornerShape(8.dp)).border(1.dp, DreamlandGold.copy(alpha = 0.4f), RoundedCornerShape(8.dp)),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    IconButton(onClick = { vm.shiftDay(-1) }) { Icon(Icons.Filled.ChevronLeft, "Previous day", tint = DreamlandGold) }
                    Text(
                        dayFmt.format(state.selectedDate),
                        color = DreamlandOnDark, fontWeight = FontWeight.SemiBold, fontSize = 13.sp,
                        modifier = Modifier.clickable { showDatePicker = true }.padding(horizontal = 6.dp),
                    )
                    IconButton(onClick = { vm.shiftDay(1) }) { Icon(Icons.Filled.ChevronRight, "Next day", tint = DreamlandGold) }
                }
                IconButton(onClick = { vm.load() }) { Icon(Icons.Filled.Refresh, "Refresh", tint = DreamlandGold) }
                Button(
                    onClick = vm::openReport,
                    colors = ButtonDefaults.buttonColors(containerColor = DreamlandGold),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.height(48.dp),
                ) {
                    Icon(Icons.Filled.Print, null, tint = Color(0xFF0D1F17), modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Print", color = Color(0xFF0D1F17), fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                }
            }
        }

        when {
            state.isLoading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator(color = DreamlandGold) }
            state.error != null -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Text(state.error ?: "Error", color = RED) }
            else -> Column(Modifier.fillMaxSize().padding(horizontal = 24.dp, vertical = 16.dp)) {
                SummaryCards(state)
                Spacer(Modifier.height(16.dp))
                BookTableHeader()
                HorizontalDivider(color = DreamlandGold.copy(alpha = 0.18f))
                if (state.rows.isEmpty()) {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text("No cash or bank activity on this day.", color = DreamlandMuted)
                    }
                } else {
                    LazyColumn(Modifier.fillMaxSize()) {
                        items(state.rows) { t ->
                            BookRow(t)
                            HorizontalDivider(color = DreamlandMuted.copy(alpha = 0.08f))
                        }
                    }
                }
            }
        }
    }

    if (showDatePicker) {
        SimpleDatePickerDialog(
            initialDate = state.selectedDate,
            onDateSelected = { vm.setDate(it); showDatePicker = false },
            onDismiss = { showDatePicker = false },
        )
    }
    if (state.reportOpen) DailyBookReportDialog(state, vm)
}

// ── Summary cards ───────────────────────────────────────────────────────────────

@Composable
private fun SummaryCards(state: DailyBookState) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
        StatCard("OPENING", state.openingTotal, DreamlandOnDark, state.openingCash, state.openingBank, Modifier.weight(1f))
        StatCard("MONEY IN", state.inTotal, GREEN, state.inCash, state.inBank, Modifier.weight(1f))
        StatCard("MONEY OUT", state.outTotal, RED, state.outCash, state.outBank, Modifier.weight(1f))
        StatCard("CLOSING (TILL)", state.closingTotal, DreamlandGold, state.closingCash, state.closingBank, Modifier.weight(1f), highlight = true)
    }
}

@Composable
private fun StatCard(label: String, total: Double, color: Color, cash: Double, bank: Double, modifier: Modifier, highlight: Boolean = false) {
    Column(
        modifier
            .clip(RoundedCornerShape(14.dp))
            .background(if (highlight) DreamlandGold.copy(alpha = 0.10f) else DreamlandForestElevated)
            .border(1.dp, if (highlight) DreamlandGold.copy(alpha = 0.5f) else DreamlandMuted.copy(alpha = 0.15f), RoundedCornerShape(14.dp))
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Text(label, color = DreamlandMuted, fontSize = 10.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 1.sp)
        Text(money(total), color = color, fontSize = 22.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("Cash ${money(cash)}", color = DreamlandMuted, fontSize = 11.sp)
            Text("Bank ${money(bank)}", color = DreamlandMuted, fontSize = 11.sp)
        }
    }
}

// ── Table ───────────────────────────────────────────────────────────────────────

private const val W_TIME = 1.1f
private const val W_PARTICULARS = 3.0f
private const val W_BY = 1.2f
private const val W_ACCT = 0.9f
private const val W_IN = 1.2f
private const val W_OUT = 1.2f
private const val W_BAL = 1.3f

@Composable
private fun BookTableHeader() {
    Row(Modifier.fillMaxWidth().padding(vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
        HCell("TIME", W_TIME)
        HCell("PARTICULARS", W_PARTICULARS)
        HCell("BY", W_BY)
        HCell("ACCOUNT", W_ACCT)
        HCell("IN", W_IN, end = true)
        HCell("OUT", W_OUT, end = true)
        HCell("BALANCE", W_BAL, end = true)
    }
}

@Composable
private fun androidx.compose.foundation.layout.RowScope.HCell(text: String, weight: Float, end: Boolean = false) {
    Text(
        text, modifier = Modifier.weight(weight),
        style = MaterialTheme.typography.labelSmall, color = DreamlandGold, letterSpacing = 1.sp, fontWeight = FontWeight.SemiBold,
        textAlign = if (end) androidx.compose.ui.text.style.TextAlign.End else androidx.compose.ui.text.style.TextAlign.Start,
    )
}

@Composable
private fun BookRow(t: RegisterTransaction) {
    val isIn = t.direction == "IN"
    Row(Modifier.fillMaxWidth().padding(vertical = 12.dp), verticalAlignment = Alignment.Top) {
        Text(timeFmt.format(t.createdAt), Modifier.weight(W_TIME), color = DreamlandOnDark, style = MaterialTheme.typography.bodySmall)
        Column(Modifier.weight(W_PARTICULARS).padding(end = 8.dp)) {
            Text(categoryOf(t), color = DreamlandOnDark, fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.bodyMedium)
            if (t.description.isNotBlank()) {
                Text(t.description, color = DreamlandMuted, style = MaterialTheme.typography.labelSmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
        }
        Text(t.manager.ifBlank { "—" }, Modifier.weight(W_BY).padding(end = 8.dp), color = DreamlandMuted, style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Box(Modifier.weight(W_ACCT)) {
            val c = if (t.account == "bank") Color(0xFF42A5F5) else GREEN
            Box(Modifier.clip(RoundedCornerShape(6.dp)).background(c.copy(alpha = 0.14f)).padding(horizontal = 8.dp, vertical = 3.dp)) {
                Text(if (t.account == "bank") "BANK" else "CASH", color = c, fontSize = 9.sp, fontWeight = FontWeight.Bold)
            }
        }
        Text(
            if (isIn) money(t.amount) else "",
            Modifier.weight(W_IN), color = GREEN, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold,
            textAlign = androidx.compose.ui.text.style.TextAlign.End,
        )
        Text(
            if (!isIn) money(t.amount) else "",
            Modifier.weight(W_OUT), color = RED, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold,
            textAlign = androidx.compose.ui.text.style.TextAlign.End,
        )
        Text(
            money(t.balanceAfter),
            Modifier.weight(W_BAL), color = DreamlandOnDark, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold,
            textAlign = androidx.compose.ui.text.style.TextAlign.End,
        )
    }
}

// ── Print dialog ──────────────────────────────────────────────────────────────

@Composable
private fun DailyBookReportDialog(state: DailyBookState, vm: DailyBookViewModel) {
    var printerExpanded by remember { mutableStateOf(false) }
    // Auto-build the PDF when the dialog opens.
    LaunchedEffect(state.reportOpen) { if (state.reportOpen && state.reportPdf == null && !state.reportGenerating) vm.generateReport() }

    Dialog(onDismissRequest = vm::closeReport, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Box(
            Modifier.fillMaxWidth(0.82f).fillMaxHeight(0.92f).clip(RoundedCornerShape(18.dp)).background(DreamlandForestSurface),
        ) {
            Column(Modifier.fillMaxSize()) {
                Row(
                    Modifier.fillMaxWidth().background(DreamlandForestElevated).padding(horizontal = 24.dp, vertical = 16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
                        Text("PRINT", style = MaterialTheme.typography.labelSmall, color = DreamlandGold, letterSpacing = 2.sp)
                        Text("Daily Book · ${dayFmt.format(state.selectedDate)}", style = MaterialTheme.typography.titleMedium, color = DreamlandOnDark, fontWeight = FontWeight.SemiBold)
                    }
                    IconButton(onClick = vm::closeReport) { Icon(Icons.Filled.Close, "Close", tint = DreamlandMuted) }
                }

                Box(Modifier.weight(1f).fillMaxWidth().background(Color(0xFF20302A)), contentAlignment = Alignment.Center) {
                    when {
                        state.reportGenerating -> Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            CircularProgressIndicator(color = DreamlandGold)
                            Text("Building daily book…", color = DreamlandMuted, style = MaterialTheme.typography.labelSmall)
                        }
                        state.reportError != null -> Text(state.reportError ?: "", color = RED, modifier = Modifier.padding(24.dp))
                        state.reportPages.isNotEmpty() -> LazyColumn(
                            Modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp), horizontalAlignment = Alignment.CenterHorizontally,
                        ) {
                            items(state.reportPages) { page ->
                                Image(
                                    bitmap = remember(page) { page.toComposeImageBitmap() },
                                    contentDescription = "Page", contentScale = ContentScale.FillWidth,
                                    modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(4.dp)),
                                )
                            }
                        }
                        else -> Text("Preparing…", color = DreamlandMuted, modifier = Modifier.padding(24.dp))
                    }
                }
                HorizontalDivider(color = DreamlandGold.copy(alpha = 0.12f))

                if (state.reportStatus != null) {
                    Text(state.reportStatus ?: "", color = DreamlandMuted, style = MaterialTheme.typography.labelSmall, modifier = Modifier.padding(horizontal = 24.dp, vertical = 6.dp))
                }
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Box(Modifier.weight(1f)) {
                        Row(
                            Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp)).background(DreamlandForestElevated)
                                .border(1.dp, DreamlandGold.copy(alpha = 0.4f), RoundedCornerShape(8.dp))
                                .clickable { printerExpanded = true }.padding(horizontal = 14.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween,
                        ) {
                            Text(
                                state.reportSelectedPrinter.ifBlank { if (state.reportPrinters.isEmpty()) "No printers found" else "Select printer" },
                                color = if (state.reportSelectedPrinter.isBlank()) DreamlandMuted else DreamlandOnDark, style = MaterialTheme.typography.bodySmall,
                            )
                            Text("▾", color = DreamlandGold)
                        }
                        DropdownMenu(expanded = printerExpanded, onDismissRequest = { printerExpanded = false }) {
                            state.reportPrinters.forEach { p ->
                                DropdownMenuItem(text = { Text(p, color = DreamlandOnDark) }, onClick = { vm.selectReportPrinter(p); printerExpanded = false })
                            }
                        }
                    }
                    OutlinedButton(
                        onClick = vm::saveReport, enabled = state.reportPdf != null,
                        border = BorderStroke(1.dp, DreamlandGold.copy(alpha = 0.6f)), shape = RoundedCornerShape(8.dp), modifier = Modifier.height(46.dp),
                    ) { Text("Save PDF", color = DreamlandGold, fontWeight = FontWeight.SemiBold) }
                    Button(
                        onClick = vm::printReport,
                        enabled = state.reportPdf != null && state.reportSelectedPrinter.isNotBlank(),
                        colors = ButtonDefaults.buttonColors(containerColor = DreamlandGold, disabledContainerColor = DreamlandGold.copy(alpha = 0.35f)),
                        shape = RoundedCornerShape(8.dp), modifier = Modifier.height(46.dp),
                    ) {
                        Icon(Icons.Filled.Print, null, tint = Color(0xFF0D1F17), modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Print", color = Color(0xFF0D1F17), fontWeight = FontWeight.SemiBold)
                    }
                }
            }
        }
    }
}
