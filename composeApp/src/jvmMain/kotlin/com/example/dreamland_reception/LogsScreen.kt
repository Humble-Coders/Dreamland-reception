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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material.icons.filled.Print
import androidx.compose.material.icons.filled.Receipt
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.toComposeImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.dreamland_reception.data.model.Bill
import com.example.dreamland_reception.data.model.GuestRecord
import com.example.dreamland_reception.data.model.Stay
import com.example.dreamland_reception.stays.SimpleDatePickerDialog
import com.example.dreamland_reception.ui.viewmodel.LogsState
import com.example.dreamland_reception.ui.viewmodel.LogsViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit

private val dtFull = SimpleDateFormat("dd MMM yyyy, hh:mm a", Locale.getDefault())
private val dtShort = SimpleDateFormat("dd MMM, hh:mm a", Locale.getDefault())
private val dOnly = SimpleDateFormat("dd MMM yyyy", Locale.getDefault())

private fun money(v: Double): String = "₹" + "%,.2f".format(v)

private fun nightsBetween(from: Date?, to: Date?): Long? {
    if (from == null || to == null) return null
    return TimeUnit.MILLISECONDS.toDays(to.time - from.time).coerceAtLeast(0)
}

@Composable
fun LogsScreen(vm: LogsViewModel = DreamlandAppInitializer.getLogsViewModel()) {
    val state by vm.state.collectAsStateWithLifecycle()

    // Refresh the logs every time the screen is shown (the VM is a shared singleton).
    LaunchedEffect(Unit) { vm.load() }

    Column(Modifier.fillMaxSize().background(DreamlandForest)) {
        // ── Header ────────────────────────────────────────────────────────────
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(DreamlandForestSurface)
                .padding(horizontal = 24.dp, vertical = 18.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Column {
                Text("ACTIVITY", style = MaterialTheme.typography.labelSmall, color = DreamlandGold, letterSpacing = 2.sp)
                Text("Guest Logs", style = MaterialTheme.typography.headlineSmall, color = DreamlandOnDark, fontWeight = FontWeight.Bold)
                Text(
                    "${state.filtered.size} stay${if (state.filtered.size != 1) "s" else ""}",
                    style = MaterialTheme.typography.labelMedium, color = DreamlandMuted,
                )
            }
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = state.searchQuery,
                    onValueChange = vm::onSearch,
                    placeholder = { Text("Search guest, room or phone…", color = DreamlandMuted, fontSize = 13.sp) },
                    singleLine = true,
                    modifier = Modifier.widthIn(min = 240.dp, max = 340.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = DreamlandOnDark, unfocusedTextColor = DreamlandOnDark,
                        focusedBorderColor = DreamlandGold, unfocusedBorderColor = DreamlandMuted.copy(alpha = 0.4f),
                        cursorColor = DreamlandGold,
                    ),
                )
                Button(
                    onClick = vm::openReport,
                    colors = ButtonDefaults.buttonColors(containerColor = DreamlandGold),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.height(52.dp),
                ) {
                    Icon(Icons.Filled.Print, contentDescription = null, tint = Color(0xFF0D1F17), modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Print Logs", color = Color(0xFF0D1F17), fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                }
            }
        }

        when {
            state.isLoading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = DreamlandGold)
            }
            state.error != null -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(state.error ?: "Error", color = Color(0xFFEF5350))
            }
            else -> Column(Modifier.fillMaxSize().padding(horizontal = 24.dp, vertical = 16.dp)) {
                LogTableHeader()
                HorizontalDivider(color = DreamlandGold.copy(alpha = 0.18f))
                if (state.filtered.isEmpty()) {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text("No stays found.", color = DreamlandMuted, style = MaterialTheme.typography.bodyMedium)
                    }
                } else {
                    LazyColumn(Modifier.fillMaxSize()) {
                        items(state.filtered) { stay ->
                            LogRow(stay, onClick = { vm.openStay(stay) })
                            HorizontalDivider(color = DreamlandMuted.copy(alpha = 0.08f))
                        }
                    }
                }
            }
        }
    }

    state.selectedStay?.let { stay ->
        StayDetailDialog(stay = stay, state = state, vm = vm)
    }
    state.selectedGuest?.let { guest ->
        GuestDetailDialog(guest = guest, onClose = vm::closeGuest)
    }
    if (state.invoiceOpen) {
        InvoiceDialog(state = state, onClose = vm::closeInvoice)
    }
    if (state.reportOpen) {
        ReportDialog(state = state, vm = vm)
    }
}

// ── Table ───────────────────────────────────────────────────────────────────

// Shared column weights so header + rows align perfectly.
private const val W_ROOM = 0.8f
private const val W_GUEST = 1.7f
private const val W_PHONE = 1.2f
private const val W_IN = 1.5f
private const val W_OUT = 1.5f
private const val W_STATUS = 1.0f

@Composable
private fun LogTableHeader() {
    Row(
        Modifier.fillMaxWidth().padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        HeaderCell("ROOM", W_ROOM)
        HeaderCell("GUEST", W_GUEST)
        HeaderCell("PHONE", W_PHONE)
        HeaderCell("CHECKED IN", W_IN)
        HeaderCell("CHECKED OUT", W_OUT)
        HeaderCell("STATUS", W_STATUS)
    }
}

@Composable
private fun androidx.compose.foundation.layout.RowScope.HeaderCell(text: String, weight: Float) {
    Text(
        text,
        modifier = Modifier.weight(weight),
        style = MaterialTheme.typography.labelSmall,
        color = DreamlandGold,
        letterSpacing = 1.sp,
        fontWeight = FontWeight.SemiBold,
    )
}

@Composable
private fun LogRow(stay: Stay, onClick: () -> Unit) {
    val checkedIn = stay.trueCheckIn ?: stay.checkInActual
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            stay.roomNumber.ifBlank { "—" },
            modifier = Modifier.weight(W_ROOM),
            color = DreamlandOnDark, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodyMedium,
        )
        Column(Modifier.weight(W_GUEST).padding(end = 8.dp)) {
            Text(stay.guestName.ifBlank { "Guest" }, color = DreamlandOnDark, style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (stay.roomCategoryName.isNotBlank()) {
                Text(stay.roomCategoryName, color = DreamlandMuted, style = MaterialTheme.typography.labelSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
        Text(
            stay.guestPhone.ifBlank { "—" },
            modifier = Modifier.weight(W_PHONE).padding(end = 8.dp),
            color = DreamlandMuted, style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis,
        )
        Text(
            dtShort.format(checkedIn),
            modifier = Modifier.weight(W_IN).padding(end = 8.dp),
            color = DreamlandOnDark, style = MaterialTheme.typography.bodySmall,
        )
        Box(Modifier.weight(W_OUT).padding(end = 8.dp)) {
            if (stay.checkOutActual != null) {
                Text(dtShort.format(stay.checkOutActual), color = DreamlandOnDark, style = MaterialTheme.typography.bodySmall)
            } else {
                Text("In-house", color = Color(0xFF4CAF50), style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Medium)
            }
        }
        Box(Modifier.weight(W_STATUS)) { LogStatusChip(stay.status) }
    }
}

@Composable
private fun LogStatusChip(status: String) {
    val (color, label) = when (status) {
        "ACTIVE" -> Color(0xFF4CAF50) to "ACTIVE"
        "COMPLETED" -> DreamlandGold to "CHECKED OUT"
        else -> DreamlandMuted to status
    }
    Box(
        Modifier.clip(RoundedCornerShape(20.dp)).background(color.copy(alpha = 0.14f)).padding(horizontal = 10.dp, vertical = 4.dp),
    ) {
        Text(label, color = color, fontSize = 9.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.5.sp)
    }
}

// ── Stay detail dialog ────────────────────────────────────────────────────────

@Composable
private fun StayDetailDialog(stay: Stay, state: LogsState, vm: LogsViewModel) {
    Dialog(onDismissRequest = vm::closeStay, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Box(
            modifier = Modifier
                .fillMaxWidth(0.66f)
                .fillMaxHeight(0.9f)
                .clip(RoundedCornerShape(18.dp))
                .background(DreamlandForestSurface),
        ) {
            Column(Modifier.fillMaxSize()) {
                // Header
                Row(
                    modifier = Modifier.fillMaxWidth().background(DreamlandForestElevated).padding(horizontal = 24.dp, vertical = 16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
                        Text("STAY DETAILS", style = MaterialTheme.typography.labelSmall, color = DreamlandGold, letterSpacing = 2.sp)
                        Text(stay.guestName.ifBlank { "Guest" }, style = MaterialTheme.typography.titleLarge, color = DreamlandOnDark, fontWeight = FontWeight.Bold)
                        Text(
                            "Room ${stay.roomNumber}${if (stay.roomCategoryName.isNotBlank()) " · ${stay.roomCategoryName}" else ""}",
                            color = DreamlandMuted, style = MaterialTheme.typography.bodySmall,
                        )
                        Spacer(Modifier.height(6.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            InfoPill("${stay.adults} Adult${if (stay.adults != 1) "s" else ""}")
                            InfoPill("${stay.children} Child${if (stay.children != 1) "ren" else ""}")
                        }
                    }
                    LogStatusChip(stay.status)
                    Spacer(Modifier.width(8.dp))
                    IconButton(onClick = vm::closeStay) { Icon(Icons.Filled.Close, contentDescription = "Close", tint = DreamlandMuted) }
                }

                Column(
                    Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(24.dp),
                    verticalArrangement = Arrangement.spacedBy(18.dp),
                ) {
                    val nights = nightsBetween(stay.checkInActual, stay.checkOutActual ?: stay.expectedCheckOut)

                    Section("Timeline") {
                        InfoGrid(
                            listOf(
                                "Checked in" to (stay.trueCheckIn?.let { dtFull.format(it) } ?: "—"),
                                "Checked out" to (stay.checkOutActual?.let { dtFull.format(it) } ?: "In-house"),
                                "Stay date" to dOnly.format(stay.checkInActual),
                                "Expected check-out" to dOnly.format(stay.expectedCheckOut),
                                "Nights" to (nights?.toString() ?: "—"),
                            ),
                        )
                    }

                    Section("Guest & Contact") {
                        InfoGrid(
                            buildList {
                                add("Primary guest" to stay.guestName.ifBlank { "—" })
                                add("Phone" to stay.guestPhone.ifBlank { "—" })
                                if (stay.userName.isNotBlank()) add("Account name" to stay.userName)
                                if (stay.userId.isNotBlank()) add("User ID" to stay.userId)
                                if (stay.bookingId.isNotBlank()) add("Booking ID" to stay.bookingId)
                                if (stay.groupStayId.isNotBlank()) add("Group stay ID" to stay.groupStayId)
                            },
                        )
                    }

                    Section("Room") {
                        InfoGrid(
                            buildList {
                                add("Room number" to stay.roomNumber.ifBlank { "—" })
                                add("Room type" to stay.roomCategoryName.ifBlank { "—" })
                                if (stay.roomInstanceId.isNotBlank()) add("Room instance ID" to stay.roomInstanceId)
                            },
                        )
                    }

                    // Financial summary (loaded with the stay) — replaces the old Charges section.
                    when {
                        state.billLoading -> Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp, color = DreamlandGold)
                            Text("Loading bill…", color = DreamlandMuted, style = MaterialTheme.typography.bodySmall)
                        }
                        state.bill != null -> FinancialSummaryCard(state.bill)
                        else -> Section("Financial Summary") {
                            Text(state.billError ?: "No bill on file.", color = DreamlandMuted, style = MaterialTheme.typography.bodySmall)
                        }
                    }

                    if (stay.guests.isNotEmpty()) {
                        Section("Guests on record (${stay.guests.size})") {
                            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                stay.guests.forEach { g -> GuestRow(g, onClick = { vm.openGuest(g) }) }
                            }
                        }
                    }

                    if (state.bill?.invoiceUrl?.isNotBlank() == true) {
                        OutlinedButton(
                            onClick = vm::openInvoice,
                            modifier = Modifier.fillMaxWidth().height(46.dp),
                            border = BorderStroke(1.dp, DreamlandGold.copy(alpha = 0.6f)),
                            shape = RoundedCornerShape(10.dp),
                        ) {
                            Icon(Icons.Filled.Receipt, contentDescription = null, tint = DreamlandGold, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(8.dp))
                            Text("View Invoice (PDF)", color = DreamlandGold, fontWeight = FontWeight.SemiBold)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun InfoPill(text: String) {
    Box(Modifier.clip(RoundedCornerShape(6.dp)).background(DreamlandForestSurface).padding(horizontal = 9.dp, vertical = 3.dp)) {
        Text(text, color = DreamlandGold, fontSize = 10.sp, fontWeight = FontWeight.SemiBold)
    }
}

/** Section with a gold label + divider, content below. */
@Composable
private fun Section(title: String, content: @Composable () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(title.uppercase(), style = MaterialTheme.typography.labelSmall, color = DreamlandGold, letterSpacing = 1.5.sp, fontWeight = FontWeight.SemiBold)
        HorizontalDivider(color = DreamlandGold.copy(alpha = 0.12f))
        content()
    }
}

/** Compact multi-column label/value grid (label above value), so details aren't a long vertical list. */
@Composable
private fun InfoGrid(pairs: List<Pair<String, String>>, columns: Int = 2) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        pairs.chunked(columns).forEach { row ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(20.dp)) {
                row.forEach { (label, value) ->
                    Column(Modifier.weight(1f)) {
                        Text(label, color = DreamlandMuted, style = MaterialTheme.typography.labelSmall)
                        Text(value, color = DreamlandOnDark, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Medium, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    }
                }
                repeat(columns - row.size) { Spacer(Modifier.weight(1f)) }
            }
        }
    }
}

@Composable
private fun GuestRow(g: GuestRecord, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(DreamlandForestElevated)
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(g.name.ifBlank { "Guest" }, color = DreamlandOnDark, fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.bodyMedium)
                if (g.idProofVerified) Text("✓ ID verified", color = Color(0xFF4CAF50), fontSize = 10.sp, fontWeight = FontWeight.SemiBold)
            }
            val idStr = listOf(g.idType, g.govIdNumber).filter { it.isNotBlank() }.joinToString(" ")
            val sub = listOfNotNull(
                g.phone.takeIf { it.isNotBlank() },
                idStr.takeIf { it.isNotBlank() },
            ).joinToString("  ·  ")
            if (sub.isNotBlank()) Text(sub, color = DreamlandMuted, style = MaterialTheme.typography.labelSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        Text("View ›", color = DreamlandGold, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold)
    }
}

// ── Guest detail dialog ───────────────────────────────────────────────────────

@Composable
private fun GuestDetailDialog(guest: GuestRecord, onClose: () -> Unit) {
    Dialog(onDismissRequest = onClose, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Box(
            modifier = Modifier
                .fillMaxWidth(0.5f)
                .fillMaxHeight(0.88f)
                .clip(RoundedCornerShape(18.dp))
                .background(DreamlandForestSurface),
        ) {
            Column(Modifier.fillMaxSize()) {
                Row(
                    modifier = Modifier.fillMaxWidth().background(DreamlandForestElevated).padding(horizontal = 24.dp, vertical = 16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
                        Text("GUEST", style = MaterialTheme.typography.labelSmall, color = DreamlandGold, letterSpacing = 2.sp)
                        Text(guest.name.ifBlank { "Guest" }, style = MaterialTheme.typography.titleLarge, color = DreamlandOnDark, fontWeight = FontWeight.Bold)
                    }
                    if (guest.idProofVerified) {
                        Box(Modifier.clip(RoundedCornerShape(20.dp)).background(Color(0xFF4CAF50).copy(alpha = 0.14f)).padding(horizontal = 10.dp, vertical = 4.dp)) {
                            Text("ID VERIFIED", color = Color(0xFF4CAF50), fontSize = 9.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                    Spacer(Modifier.width(8.dp))
                    IconButton(onClick = onClose) { Icon(Icons.Filled.Close, contentDescription = "Close", tint = DreamlandMuted) }
                }

                Column(
                    Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(24.dp),
                    verticalArrangement = Arrangement.spacedBy(18.dp),
                ) {
                    Section("Details") {
                        InfoGrid(
                            buildList {
                                add("Name" to guest.name.ifBlank { "—" })
                                add("Phone" to guest.phone.ifBlank { "—" })
                                add("Gender" to guest.gender.ifBlank { "—" })
                                if (guest.idType.isNotBlank()) add("ID type" to guest.idType)
                                if (guest.govIdNumber.isNotBlank()) add("ID number" to guest.govIdNumber)
                                if (guest.purpose.isNotBlank()) add("Purpose of visit" to guest.purpose)
                                add("Date of birth" to guest.dob.ifBlank { "—" })
                                if (guest.age > 0) add("Age" to guest.age.toString())
                                if (guest.address.isNotBlank()) add("Address" to guest.address)
                            },
                        )
                    }

                    Section("Identity Document") {
                        if (guest.govIdPictures.isEmpty()) {
                            Text("No ID images on record.", color = DreamlandMuted, style = MaterialTheme.typography.bodySmall)
                        } else {
                            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                                guest.govIdPictures.forEach { url -> IdImage(url) }
                            }
                        }
                    }
                }
            }
        }
    }
}

/** Downloads and shows a remote ID image; tapping opens it full-size in the browser. */
@Composable
private fun IdImage(url: String) {
    val bitmap by produceState<ImageBitmap?>(initialValue = null, url) {
        value = withContext(Dispatchers.IO) {
            runCatching { javax.imageio.ImageIO.read(java.net.URL(url))?.toComposeImageBitmap() }.getOrNull()
        }
    }
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 80.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(Color(0xFF20302A))
            .border(1.dp, DreamlandMuted.copy(alpha = 0.2f), RoundedCornerShape(10.dp))
            .clickable { runCatching { java.awt.Desktop.getDesktop().browse(java.net.URI(url)) } }
            .padding(6.dp),
        contentAlignment = Alignment.Center,
    ) {
        val bmp = bitmap
        if (bmp != null) {
            Image(bitmap = bmp, contentDescription = "ID image", contentScale = ContentScale.Fit, modifier = Modifier.fillMaxWidth())
        } else {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(16.dp)) {
                CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp, color = DreamlandGold)
                Text("Loading image…", color = DreamlandMuted, style = MaterialTheme.typography.labelSmall)
            }
        }
    }
}

// ── Invoice viewer (stored invoice PDF, rendered like the billing screen) ─────

@Composable
private fun InvoiceDialog(state: LogsState, onClose: () -> Unit) {
    val bill = state.bill
    Dialog(onDismissRequest = onClose, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Box(
            modifier = Modifier
                .fillMaxWidth(0.7f)
                .fillMaxHeight(0.92f)
                .clip(RoundedCornerShape(18.dp))
                .background(DreamlandForestSurface),
        ) {
            Column(Modifier.fillMaxSize()) {
                Row(
                    modifier = Modifier.fillMaxWidth().background(DreamlandForestElevated).padding(horizontal = 24.dp, vertical = 16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(Icons.Filled.Receipt, contentDescription = null, tint = DreamlandGold, modifier = Modifier.size(20.dp))
                    Spacer(Modifier.width(10.dp))
                    Column(Modifier.weight(1f)) {
                        Text("INVOICE", style = MaterialTheme.typography.labelSmall, color = DreamlandGold, letterSpacing = 2.sp)
                        Text(
                            bill?.ledgerInvoiceNumber?.takeIf { it.isNotBlank() } ?: "Tax Invoice",
                            style = MaterialTheme.typography.titleMedium, color = DreamlandOnDark, fontWeight = FontWeight.SemiBold,
                        )
                    }
                    if (!bill?.invoiceUrl.isNullOrBlank()) {
                        IconButton(onClick = { runCatching { java.awt.Desktop.getDesktop().browse(java.net.URI(bill!!.invoiceUrl)) } }) {
                            Icon(Icons.Filled.OpenInNew, contentDescription = "Open in browser", tint = DreamlandGold)
                        }
                    }
                    IconButton(onClick = onClose) { Icon(Icons.Filled.Close, contentDescription = "Close", tint = DreamlandMuted) }
                }

                Box(Modifier.fillMaxSize().background(Color(0xFF20302A)), contentAlignment = Alignment.Center) {
                    when {
                        state.invoiceLoading -> Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            CircularProgressIndicator(color = DreamlandGold)
                            Text("Loading invoice…", color = DreamlandMuted, style = MaterialTheme.typography.labelSmall)
                        }
                        state.invoiceError != null -> Column(
                            horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.padding(24.dp),
                        ) {
                            Text(state.invoiceError ?: "", color = DreamlandMuted, style = MaterialTheme.typography.bodySmall, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
                            if (!bill?.invoiceUrl.isNullOrBlank()) {
                                OutlinedButton(
                                    onClick = { runCatching { java.awt.Desktop.getDesktop().browse(java.net.URI(bill!!.invoiceUrl)) } },
                                    border = BorderStroke(1.dp, DreamlandGold.copy(alpha = 0.5f)),
                                ) { Text("Open in browser", color = DreamlandGold) }
                            }
                        }
                        else -> LazyColumn(
                            modifier = Modifier.fillMaxSize(),
                            contentPadding = PaddingValues(16.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                        ) {
                            items(state.invoicePages) { page ->
                                Image(
                                    bitmap = remember(page) { page.toComposeImageBitmap() },
                                    contentDescription = "Invoice page",
                                    contentScale = ContentScale.FillWidth,
                                    modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(4.dp)),
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun FinancialSummaryCard(bill: Bill) {
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(DreamlandForestElevated).padding(18.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text("FINANCIAL SUMMARY", style = MaterialTheme.typography.labelSmall, color = DreamlandGold, letterSpacing = 1.5.sp, fontWeight = FontWeight.SemiBold)
        HorizontalDivider(color = DreamlandGold.copy(alpha = 0.15f))
        MoneyRow("Subtotal", bill.subtotal)
        if (bill.taxEnabled || bill.taxAmount > 0) MoneyRow("Tax (${bill.taxPercentage.toInt()}%)", bill.taxAmount)
        if (bill.discountAmount > 0) MoneyRow("Discount", -bill.discountAmount, Color(0xFF4CAF50))
        HorizontalDivider(color = DreamlandGold.copy(alpha = 0.2f))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("Total", color = DreamlandOnDark, fontWeight = FontWeight.Bold)
            Text(money(bill.totalAmount), color = DreamlandGold, fontWeight = FontWeight.Bold, fontSize = 16.sp)
        }
        if (bill.advancePayment > 0) MoneyRow("Advance (${bill.advancePaymentMethod})", bill.advancePayment, Color(0xFF4CAF50))
        if (bill.totalPaid > 0) MoneyRow("Payments received", bill.totalPaid, Color(0xFF4CAF50))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("Balance due", color = if (bill.pendingAmount > 0) Color(0xFFEF5350) else Color(0xFF4CAF50), fontWeight = FontWeight.SemiBold)
            Text(if (bill.pendingAmount > 0) money(bill.pendingAmount) else "PAID", color = if (bill.pendingAmount > 0) Color(0xFFEF5350) else Color(0xFF4CAF50), fontWeight = FontWeight.Bold)
        }
        if (bill.transactions.isNotEmpty()) {
            Spacer(Modifier.height(2.dp))
            Text("PAYMENTS", style = MaterialTheme.typography.labelSmall, color = DreamlandMuted, letterSpacing = 1.sp)
            bill.transactions.forEach { tx ->
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("${tx.method} · ${dtShort.format(tx.createdAt)}", color = DreamlandMuted, style = MaterialTheme.typography.labelSmall)
                    Text(money(tx.amount), color = DreamlandOnDark, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Medium)
                }
            }
        }
    }
}

@Composable
private fun MoneyRow(label: String, amount: Double, valueColor: Color = DreamlandOnDark) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, color = DreamlandMuted, style = MaterialTheme.typography.bodySmall)
        Text(
            if (amount < 0) "-" + money(-amount) else money(amount),
            color = valueColor, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Medium,
        )
    }
}

// ── Print logs report dialog ──────────────────────────────────────────────────

@Composable
private fun ReportDialog(state: LogsState, vm: LogsViewModel) {
    var showFrom by remember { mutableStateOf(false) }
    var showTo by remember { mutableStateOf(false) }
    var printerExpanded by remember { mutableStateOf(false) }
    val dOnlyFmt = remember { SimpleDateFormat("dd MMM yyyy", Locale.getDefault()) }

    Dialog(onDismissRequest = vm::closeReport, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Box(
            modifier = Modifier
                .fillMaxWidth(0.88f)
                .fillMaxHeight(0.92f)
                .clip(RoundedCornerShape(18.dp))
                .background(DreamlandForestSurface),
        ) {
            Column(Modifier.fillMaxSize()) {
                // Header
                Row(
                    modifier = Modifier.fillMaxWidth().background(DreamlandForestElevated).padding(horizontal = 24.dp, vertical = 16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
                        Text("PRINT", style = MaterialTheme.typography.labelSmall, color = DreamlandGold, letterSpacing = 2.sp)
                        Text("Guest Register / Logs", style = MaterialTheme.typography.titleMedium, color = DreamlandOnDark, fontWeight = FontWeight.SemiBold)
                    }
                    IconButton(onClick = vm::closeReport) { Icon(Icons.Filled.Close, contentDescription = "Close", tint = DreamlandMuted) }
                }

                // Date range + generate
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 14.dp),
                    verticalAlignment = Alignment.Bottom,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    DateField(Modifier.weight(1f), "From", state.reportFrom, dOnlyFmt) { showFrom = true }
                    DateField(Modifier.weight(1f), "To", state.reportTo, dOnlyFmt) { showTo = true }
                    Button(
                        onClick = vm::generateReport,
                        enabled = !state.reportGenerating,
                        colors = ButtonDefaults.buttonColors(containerColor = DreamlandGold, disabledContainerColor = DreamlandGold.copy(alpha = 0.35f)),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.height(48.dp),
                    ) {
                        Text(if (state.reportGenerating) "Generating…" else "Generate", color = Color(0xFF0D1F17), fontWeight = FontWeight.SemiBold)
                    }
                }
                HorizontalDivider(color = DreamlandGold.copy(alpha = 0.12f))

                // Preview
                Box(Modifier.weight(1f).fillMaxWidth().background(Color(0xFF20302A)), contentAlignment = Alignment.Center) {
                    when {
                        state.reportGenerating -> Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            CircularProgressIndicator(color = DreamlandGold)
                            Text("Building register…", color = DreamlandMuted, style = MaterialTheme.typography.labelSmall)
                        }
                        state.reportError != null -> Text(state.reportError ?: "", color = Color(0xFFEF5350), style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(24.dp))
                        state.reportPages.isNotEmpty() -> LazyColumn(
                            modifier = Modifier.fillMaxSize(),
                            contentPadding = PaddingValues(16.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                        ) {
                            items(state.reportPages) { page ->
                                Image(
                                    bitmap = remember(page) { page.toComposeImageBitmap() },
                                    contentDescription = "Report page",
                                    contentScale = ContentScale.FillWidth,
                                    modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(4.dp)),
                                )
                            }
                        }
                        else -> Text("Pick a date range and tap Generate to preview the register.", color = DreamlandMuted, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(24.dp))
                    }
                }
                HorizontalDivider(color = DreamlandGold.copy(alpha = 0.12f))

                // Status + actions
                if (state.reportStatus != null) {
                    Text(state.reportStatus ?: "", color = DreamlandMuted, style = MaterialTheme.typography.labelSmall, modifier = Modifier.padding(horizontal = 24.dp, vertical = 6.dp))
                }
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    // Printer dropdown
                    Box(Modifier.weight(1f)) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .background(DreamlandForestElevated)
                                .border(1.dp, DreamlandGold.copy(alpha = 0.4f), RoundedCornerShape(8.dp))
                                .clickable { printerExpanded = true }
                                .padding(horizontal = 14.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                        ) {
                            Text(
                                state.reportSelectedPrinter.ifBlank { if (state.reportPrinters.isEmpty()) "No printers found" else "Select printer" },
                                color = if (state.reportSelectedPrinter.isBlank()) DreamlandMuted else DreamlandOnDark,
                                style = MaterialTheme.typography.bodySmall,
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
                        onClick = vm::saveReport,
                        enabled = state.reportPdf != null,
                        border = BorderStroke(1.dp, DreamlandGold.copy(alpha = 0.6f)),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.height(46.dp),
                    ) { Text("Save PDF", color = DreamlandGold, fontWeight = FontWeight.SemiBold) }
                    Button(
                        onClick = vm::printReport,
                        enabled = state.reportPdf != null && state.reportSelectedPrinter.isNotBlank(),
                        colors = ButtonDefaults.buttonColors(containerColor = DreamlandGold, disabledContainerColor = DreamlandGold.copy(alpha = 0.35f)),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.height(46.dp),
                    ) {
                        Icon(Icons.Filled.Print, contentDescription = null, tint = Color(0xFF0D1F17), modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Print", color = Color(0xFF0D1F17), fontWeight = FontWeight.SemiBold)
                    }
                }
            }
        }
    }

    if (showFrom) SimpleDatePickerDialog(initialDate = state.reportFrom, onDateSelected = { vm.setReportFrom(it); showFrom = false }, onDismiss = { showFrom = false })
    if (showTo) SimpleDatePickerDialog(initialDate = state.reportTo, onDateSelected = { vm.setReportTo(it); showTo = false }, onDismiss = { showTo = false }, minDate = state.reportFrom)
}

@Composable
private fun DateField(modifier: Modifier, label: String, date: Date, fmt: SimpleDateFormat, onClick: () -> Unit) {
    Column(modifier) {
        Text(label, color = DreamlandMuted, style = MaterialTheme.typography.labelSmall)
        Spacer(Modifier.height(4.dp))
        Box(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp))
                .border(1.dp, DreamlandGold.copy(alpha = 0.4f), RoundedCornerShape(8.dp))
                .background(DreamlandForestElevated).clickable(onClick = onClick)
                .padding(horizontal = 14.dp, vertical = 12.dp),
        ) {
            Text(fmt.format(date), color = DreamlandOnDark, style = MaterialTheme.typography.bodyMedium)
        }
    }
}
