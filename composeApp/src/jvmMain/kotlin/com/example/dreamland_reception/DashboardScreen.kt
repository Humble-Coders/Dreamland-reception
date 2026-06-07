@file:OptIn(ExperimentalMaterial3Api::class)

package com.example.dreamland_reception

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AttachMoney
import androidx.compose.material.icons.filled.CleaningServices
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.CreditCard
import androidx.compose.material.icons.filled.Feedback
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.EventAvailable
import androidx.compose.material.icons.filled.Hotel
import androidx.compose.material.icons.automirrored.filled.Login
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material.icons.filled.Receipt
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.ShoppingBag
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.dreamland_reception.data.model.Booking
import com.example.dreamland_reception.data.model.RoomInstance
import com.example.dreamland_reception.data.model.Stay
import com.example.dreamland_reception.ui.viewmodel.ActiveStayRow
import com.example.dreamland_reception.ui.viewmodel.AlertType
import com.example.dreamland_reception.ui.viewmodel.AvailabilityViewModel
import com.example.dreamland_reception.ui.viewmodel.AvailableCategory
import com.example.dreamland_reception.ui.viewmodel.DashboardAlert
import com.example.dreamland_reception.ui.viewmodel.DashboardState
import com.example.dreamland_reception.ui.viewmodel.DashboardViewModel
import com.example.dreamland_reception.ui.viewmodel.DayTrendPoint
import com.example.dreamland_reception.stays.SimpleDatePickerDialog
import com.example.dreamland_reception.util.dateFromPicker
import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

// ── Entry point ───────────────────────────────────────────────────────────────

@Composable
fun DashboardScreen(
    vm: DashboardViewModel = DreamlandAppInitializer.getDashboardViewModel(),
    onNewWalkIn: () -> Unit = {},
    onAddBooking: () -> Unit = {},
    onNavigateToOrders: () -> Unit = {},
    onNavigateToComplaints: () -> Unit = {},
    onNavigateToStaff: () -> Unit = {},
    onRoomClick: (String) -> Unit = {},
) {
    val state by vm.state.collectAsStateWithLifecycle()
    // Hotel's configured check-out time (HH:mm) — room cards show this as the time,
    // while keeping the check-out DATE from the stay.
    val settingsState by DreamlandAppInitializer.getSettingsViewModel().state.collectAsStateWithLifecycle()
    val hotelCheckOutTime = settingsState.selectedHotel?.checkOutTime?.takeIf { it.isNotBlank() } ?: "11:00"
    var showCheckAvailability by remember { mutableStateOf(false) }

    Column(Modifier.fillMaxSize()) {
        // Error banner
        if (state.error != null) {
            Surface(color = Color(0xFFB71C1C).copy(alpha = 0.9f), modifier = Modifier.fillMaxWidth()) {
                Row(
                    Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(Icons.Filled.Warning, contentDescription = null, tint = Color.White, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(
                        state.error ?: "",
                        color = Color.White,
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.weight(1f),
                    )
                    TextButton(onClick = { vm.clearError() }) {
                        Text("Dismiss", color = Color.White, style = MaterialTheme.typography.labelSmall)
                    }
                }
            }
        }

        Row(Modifier.weight(1f).fillMaxWidth()) {
                // ── Left main column ──────────────────────────────────────────
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .verticalScroll(rememberScrollState())
                        .padding(20.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    RoomGridSection(
                        rooms = state.roomInstances,
                        stays = state.rawActiveStays,
                        categoryNames = state.categoryNames,
                        hotelCheckOutTime = hotelCheckOutTime,
                        onSetCleaning = vm::setRoomCleaning,
                        onSetAvailable = vm::setRoomAvailable,
                        onToggleBookable = vm::setRoomAvailableForBooking,
                        onRoomClick = onRoomClick,
                    )
                    TodayBookingsCard(bookings = state.todayBookings)
                }

                // ── Divider ───────────────────────────────────────────────────
                Box(
                    modifier = Modifier
                        .fillMaxHeight()
                        .width(1.dp)
                        .background(DreamlandGold.copy(alpha = 0.12f)),
                )

                // ── Right sidebar ─────────────────────────────────────────────
                Column(
                    modifier = Modifier
                        .width(340.dp)
                        .fillMaxHeight()
                        .verticalScroll(rememberScrollState())
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    CheckAvailabilityCard()
                    QuickActionsCard(
                        onNewWalkIn = onNewWalkIn,
                        onAddBooking = onAddBooking,
                        onNavigateToOrders = onNavigateToOrders,
                        activeOrdersCount = state.activeOrdersCount,
                    )
                }
            }
    }

    if (showCheckAvailability) {
        com.example.dreamland_reception.stays.CheckAvailabilityDialog(
            onDismiss = { showCheckAvailability = false },
        )
    }
}

// ── Room grid ─────────────────────────────────────────────────────────────────

private fun roomStatusColor(status: String, isOccupied: Boolean): Color = when {
    isOccupied -> Color(0xFFEF5350)            // occupied — red
    status == "CLEANING" -> Color(0xFF2196F3)  // cleaning — blue
    status == "MAINTENANCE" -> Color(0xFFF39C12) // maintenance — amber (kept distinct from occupied red)
    else -> Color(0xFF4CAF50)                  // available — green
}

@Composable
private fun RoomGridSection(
    rooms: List<RoomInstance>,
    stays: List<Stay>,
    categoryNames: Map<String, String>,
    hotelCheckOutTime: String,
    onSetCleaning: (String) -> Unit,
    onSetAvailable: (String) -> Unit,
    onToggleBookable: (String, Boolean) -> Unit,
    onRoomClick: (String) -> Unit,
) {
    if (rooms.isEmpty()) return
    // Always ascending by room number (numeric-aware: 2 < 10 < 101), falling back to text.
    val sorted = remember(rooms) {
        rooms.sortedWith(compareBy({ it.roomNumber.trim().toIntOrNull() ?: Int.MAX_VALUE }, { it.roomNumber }))
    }
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("ROOMS", style = MaterialTheme.typography.labelLarge, color = DreamlandGold, letterSpacing = 2.sp)
        // Manual responsive grid (the parent is a verticalScroll Column, so a LazyVerticalGrid
        // would need a fragile fixed height). Equal-width tiles via weight + fixed height.
        BoxWithConstraints(Modifier.fillMaxWidth()) {
            val gap = 18.dp
            val cols = ((maxWidth.value + gap.value) / (240f + gap.value)).toInt().coerceIn(1, 5)
            Column(verticalArrangement = Arrangement.spacedBy(gap)) {
                sorted.chunked(cols).forEach { rowRooms ->
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(gap)) {
                        rowRooms.forEach { room ->
                            val stay = stays.find { it.roomInstanceId == room.id }
                            RoomCard(
                                room = room,
                                stay = stay,
                                isOccupied = stay != null,
                                categoryNames = categoryNames,
                                hotelCheckOutTime = hotelCheckOutTime,
                                onSetCleaning = onSetCleaning,
                                onSetAvailable = onSetAvailable,
                                onToggleBookable = onToggleBookable,
                                onClick = { stay?.id?.let { onRoomClick(it) } },
                                modifier = Modifier.weight(1f),
                            )
                        }
                        // Keep tile widths equal when the last row isn't full.
                        repeat(cols - rowRooms.size) { Spacer(Modifier.weight(1f)) }
                    }
                }
            }
        }
    }
}

/** Formats the hotel's configured check-out time ("HH:mm") for display ("hh:mm a"); falls back to the raw value. */
private fun formatHotelTime(hhmm: String): String = runCatching {
    val parts = hhmm.trim().split(":")
    val h = parts[0].toInt()
    val m = parts.getOrNull(1)?.toInt() ?: 0
    val cal = Calendar.getInstance().apply { set(Calendar.HOUR_OF_DAY, h); set(Calendar.MINUTE, m) }
    SimpleDateFormat("hh:mm a", Locale.getDefault()).format(cal.time)
}.getOrElse { hhmm }

@Composable
private fun RoomCard(
    room: RoomInstance,
    stay: Stay?,
    isOccupied: Boolean,
    categoryNames: Map<String, String> = emptyMap(),
    hotelCheckOutTime: String = "11:00",
    onSetCleaning: (String) -> Unit,
    onSetAvailable: (String) -> Unit,
    onToggleBookable: (String, Boolean) -> Unit,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val statusColor = roomStatusColor(room.status, isOccupied)
    // Date only — the time shown comes from the hotel's configured check-out time.
    val dateFmt = remember { SimpleDateFormat("dd MMM", Locale.getDefault()) }
    val statusLabel = when {
        isOccupied -> "OCCUPIED"
        room.status == "CLEANING" -> "CLEANING"
        room.status == "MAINTENANCE" -> "MAINTENANCE"
        else -> "AVAILABLE"
    }
    // Room type from whatever's available: the instance's own name, else a category lookup, else the active stay's category.
    val roomType = room.categoryName.ifBlank { categoryNames[room.categoryId] ?: stay?.roomCategoryName.orEmpty() }
    Column(
        modifier = modifier
            .height(200.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(DreamlandForestElevated)
            .border(1.dp, statusColor.copy(alpha = 0.35f), RoundedCornerShape(14.dp))
            .clickable(onClick = onClick)
            .padding(14.dp),
    ) {
        // Header: room number + type chip + status pill
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.Top) {
            Column(Modifier.weight(1f)) {
                Text("Room ${room.roomNumber}", color = DreamlandOnDark, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                if (roomType.isNotBlank()) {
                    Spacer(Modifier.height(4.dp))
                    RoomTypeChip(roomType)
                }
            }
            Spacer(Modifier.width(8.dp))
            RoomStatusPill(statusLabel, statusColor)
        }

        Spacer(Modifier.height(6.dp))

        // Admin toggle: takes the room off the new-booking pool without affecting check-in/extend/change-room.
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Text(
                if (room.isAvailableForBooking) "Bookable" else "Not Bookable",
                color = DreamlandMuted,
                style = MaterialTheme.typography.labelSmall,
            )
            Switch(
                checked = room.isAvailableForBooking,
                onCheckedChange = { onToggleBookable(room.id, it) },
                modifier = Modifier.scale(0.7f),
                colors = SwitchDefaults.colors(checkedThumbColor = DreamlandGold, checkedTrackColor = DreamlandGold.copy(alpha = 0.4f)),
            )
        }

        Spacer(Modifier.height(6.dp))

        when {
            // Occupied — guest summary. No housekeeping toggle: an occupied room can't be set to cleaning here.
            isOccupied && stay != null -> {
                Text(stay.guestName.ifBlank { "Guest" }, color = DreamlandOnDark, fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                if (stay.guestPhone.isNotBlank()) {
                    Text("+91 ${stay.guestPhone}", color = DreamlandMuted, style = MaterialTheme.typography.labelSmall)
                }
                Spacer(Modifier.weight(1f))
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                    Icon(Icons.Filled.Schedule, contentDescription = null, tint = DreamlandMuted, modifier = Modifier.size(13.dp))
                    Text("Check-out · ${dateFmt.format(stay.expectedCheckOut)}, ${formatHotelTime(hotelCheckOutTime)}", color = DreamlandMuted, style = MaterialTheme.typography.labelSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
            room.status == "MAINTENANCE" -> {
                Spacer(Modifier.weight(1f))
                Text("Under maintenance", color = statusColor, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Medium)
            }
            // Vacant — housekeeping toggle.
            else -> {
                Spacer(Modifier.weight(1f))
                val cleaningSelected = room.status == "CLEANING" || room.needsCleaning
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    RoomToggleChip("Available", !cleaningSelected, Color(0xFF4CAF50), Modifier.weight(1f)) { onSetAvailable(room.id) }
                    RoomToggleChip("Cleaning", cleaningSelected, Color(0xFF2196F3), Modifier.weight(1f)) { onSetCleaning(room.id) }
                }
            }
        }
    }
}

@Composable
private fun RoomTypeChip(type: String) {
    Box(
        Modifier.clip(RoundedCornerShape(6.dp)).background(DreamlandGold.copy(alpha = 0.12f)).padding(horizontal = 7.dp, vertical = 2.dp),
    ) {
        Text(type, color = DreamlandGold, fontSize = 9.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 0.3.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
private fun RoomStatusPill(label: String, color: Color) {
    Row(
        modifier = Modifier.clip(RoundedCornerShape(20.dp)).background(color.copy(alpha = 0.15f)).padding(horizontal = 9.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(5.dp),
    ) {
        Box(Modifier.size(6.dp).clip(CircleShape).background(color))
        Text(label, color = color, fontSize = 9.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.5.sp)
    }
}

@Composable
private fun RoomToggleChip(label: String, selected: Boolean, accent: Color, modifier: Modifier, onClick: () -> Unit) {
    Box(
        modifier = modifier
            .height(30.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(if (selected) accent.copy(alpha = 0.18f) else Color.Transparent)
            .border(1.dp, if (selected) accent else DreamlandMuted.copy(alpha = 0.3f), RoundedCornerShape(8.dp))
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(label, color = if (selected) accent else DreamlandMuted, fontSize = 11.sp, fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal)
    }
}

// ── Today's Bookings card ─────────────────────────────────────────────────────

@Composable
private fun TodayBookingsCard(bookings: List<Booking>) {
    if (bookings.isEmpty()) return
    val timeFmt = remember { SimpleDateFormat("hh:mm a", Locale.getDefault()) }
    Card(
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = DreamlandForestElevated),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("TODAY'S ARRIVALS", style = MaterialTheme.typography.labelLarge, color = DreamlandGold, letterSpacing = 2.sp)
            androidx.compose.material3.HorizontalDivider(color = DreamlandGold.copy(alpha = 0.2f))
            bookings.forEach { booking ->
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(booking.guestName, color = DreamlandOnDark, fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(booking.roomCategoryName.ifBlank { "Room" }, color = DreamlandMuted, style = MaterialTheme.typography.labelSmall)
                    }
                    Text(timeFmt.format(booking.checkIn), color = DreamlandGold, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.SemiBold)
                }
            }
        }
    }
}

// ── KPI cards ─────────────────────────────────────────────────────────────────

@Composable
private fun KpiCardsSection(state: DashboardState) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            KpiCard(
                label = "Revenue",
                value = "₹${state.revenueToday.toLong()}",
                subtitle = if (state.isToday) "Today" else "Selected day",
                icon = Icons.Filled.AttachMoney,
                accentColor = DreamlandGoldBright,
                modifier = Modifier.weight(1f),
            )
            KpiCard(
                label = "Occupancy",
                value = "${(state.occupancyRate * 100).toInt()}%",
                subtitle = "${state.roomStatus.occupied} of ${state.roomStatus.total} rooms",
                icon = Icons.Filled.Hotel,
                accentColor = Color(0xFF3498DB),
                modifier = Modifier.weight(1f),
            )
            KpiCard(
                label = "Check-ins",
                value = "${state.checkInsCount}",
                subtitle = if (state.isToday) "Today" else "Selected day",
                icon = Icons.AutoMirrored.Filled.Login,
                accentColor = Color(0xFF2ECC71),
                modifier = Modifier.weight(1f),
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            KpiCard(
                label = "Check-outs",
                value = "${state.checkOutsCount}",
                subtitle = if (state.isToday) "Today" else "Selected day",
                icon = Icons.AutoMirrored.Filled.Logout,
                accentColor = Color(0xFF3498DB),
                modifier = Modifier.weight(1f),
            )
            KpiCard(
                label = "Pending Bills",
                value = "${state.pendingPaymentsCount}",
                subtitle = if (state.pendingPaymentsCount > 0) "₹${state.pendingPaymentsAmount.toLong()} due" else "All clear",
                icon = Icons.Filled.CreditCard,
                accentColor = if (state.pendingPaymentsCount > 0) Color(0xFFE74C3C) else DreamlandGoldBright,
                modifier = Modifier.weight(1f),
            )
            KpiCard(
                label = "Complaints",
                value = "${state.activeComplaintsCount}",
                subtitle = if (state.activeComplaintsCount > 0) "Open / Assigned" else "All resolved",
                icon = Icons.Filled.Warning,
                accentColor = if (state.activeComplaintsCount > 0) Color(0xFFF39C12) else DreamlandGoldBright,
                modifier = Modifier.weight(1f),
            )
        }
    }
}

@Composable
private fun KpiCard(
    label: String,
    value: String,
    subtitle: String,
    icon: ImageVector,
    accentColor: Color,
    modifier: Modifier = Modifier,
) {
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = DreamlandForestElevated),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
    ) {
        Row(
            modifier = Modifier.padding(16.dp).fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(accentColor.copy(alpha = 0.15f)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(icon, contentDescription = null, tint = accentColor, modifier = Modifier.size(22.dp))
            }
            Spacer(Modifier.width(12.dp))
            Column {
                Text(value, style = MaterialTheme.typography.headlineSmall, color = accentColor, fontWeight = FontWeight.Bold)
                Text(label, style = MaterialTheme.typography.labelMedium, color = DreamlandOnDark, fontWeight = FontWeight.Medium)
                Text(subtitle, style = MaterialTheme.typography.labelSmall, color = DreamlandMuted, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}

// ── Alerts ────────────────────────────────────────────────────────────────────

@Composable
private fun AlertsSection(
    alerts: List<DashboardAlert>,
    onOrdersClick: () -> Unit,
    onComplaintsClick: () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("Alerts & Activity", style = MaterialTheme.typography.titleMedium, color = DreamlandOnDark, fontWeight = FontWeight.SemiBold)
            if (alerts.isNotEmpty()) {
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(12.dp))
                        .background(DreamlandGold.copy(alpha = 0.15f))
                        .padding(horizontal = 8.dp, vertical = 2.dp),
                ) {
                    Text("${alerts.size}", style = MaterialTheme.typography.labelMedium, color = DreamlandGold)
                }
            }
        }

        if (alerts.isEmpty()) {
            Card(
                colors = CardDefaults.cardColors(containerColor = DreamlandForestElevated),
                shape = RoundedCornerShape(12.dp),
            ) {
                Row(
                    Modifier.padding(16.dp).fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Icon(Icons.Filled.CheckCircle, contentDescription = null, tint = Color(0xFF2ECC71), modifier = Modifier.size(20.dp))
                    Text("All clear — no active alerts", style = MaterialTheme.typography.bodyMedium, color = DreamlandMuted)
                }
            }
        } else {
            alerts.forEach { alert ->
                val clickAction: () -> Unit = when (alert.type) {
                    AlertType.NEW_ORDER -> onOrdersClick
                    AlertType.HIGH_COMPLAINT -> onComplaintsClick
                    else -> ({})
                }
                AlertRow(alert = alert, onClick = clickAction)
            }
        }
    }
}

@Composable
private fun AlertRow(alert: DashboardAlert, onClick: () -> Unit) {
    val priorityColor = when (alert.priority) {
        "HIGH" -> Color(0xFFE74C3C)
        "LOW" -> Color(0xFF2ECC71)
        else -> Color(0xFFF39C12)
    }
    val alertIcon = when (alert.type) {
        AlertType.HIGH_COMPLAINT -> Icons.Filled.Warning
        AlertType.NEW_ORDER -> Icons.Filled.ShoppingBag
        AlertType.CLEANING_ROOM -> Icons.Filled.CleaningServices
        AlertType.PENDING_BILL -> Icons.Filled.Receipt
    }
    Card(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
        shape = RoundedCornerShape(10.dp),
        colors = CardDefaults.cardColors(containerColor = DreamlandForestElevated),
    ) {
        Row(Modifier.fillMaxWidth()) {
            // Colored left border
            Box(
                modifier = Modifier
                    .width(4.dp)
                    .height(64.dp)
                    .background(priorityColor),
            )
            Row(
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp).fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Icon(alertIcon, contentDescription = null, tint = priorityColor, modifier = Modifier.size(20.dp))
                Column(Modifier.weight(1f)) {
                    Text(alert.title, style = MaterialTheme.typography.bodyMedium, color = DreamlandOnDark, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(alert.subtitle, style = MaterialTheme.typography.labelSmall, color = DreamlandMuted, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
        }
    }
}

// ── Active stays ──────────────────────────────────────────────────────────────

@Composable
private fun ActiveStaysSection(stays: List<ActiveStayRow>) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("Active Stays", style = MaterialTheme.typography.titleMedium, color = DreamlandOnDark, fontWeight = FontWeight.SemiBold)
            Text("${stays.size} guest${if (stays.size != 1) "s" else ""}", style = MaterialTheme.typography.labelMedium, color = DreamlandMuted)
        }

        Card(
            shape = RoundedCornerShape(12.dp),
            colors = CardDefaults.cardColors(containerColor = DreamlandForestElevated),
        ) {
            if (stays.isEmpty()) {
                Box(Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) {
                    Text("No active stays", style = MaterialTheme.typography.bodyMedium, color = DreamlandMuted)
                }
            } else {
                Box(Modifier.height(260.dp).fillMaxWidth()) {
                    LazyColumn {
                        items(stays) { row ->
                            ActiveStayRowItem(row)
                            if (stays.last() != row) {
                                Box(Modifier.fillMaxWidth().height(1.dp).background(DreamlandGold.copy(alpha = 0.08f)))
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ActiveStayRowItem(row: ActiveStayRow) {
    val timeFmt = remember { SimpleDateFormat("dd MMM, hh:mm a", Locale.getDefault()) }
    Row(
        modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp).fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        // Room badge
        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(6.dp))
                .background(DreamlandGold.copy(alpha = 0.18f))
                .padding(horizontal = 8.dp, vertical = 4.dp),
        ) {
            Text(row.roomNumber, style = MaterialTheme.typography.labelMedium, color = DreamlandGoldBright, fontWeight = FontWeight.Bold)
        }
        // Guest name
        Text(
            text = row.guestName,
            style = MaterialTheme.typography.bodyMedium,
            color = DreamlandOnDark,
            modifier = Modifier.weight(1f),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        // Checkout time
        Column(horizontalAlignment = Alignment.End) {
            Text(
                text = "Check-out",
                style = MaterialTheme.typography.labelSmall,
                color = DreamlandMuted,
            )
            Text(
                text = timeFmt.format(row.expectedCheckOut),
                style = MaterialTheme.typography.labelSmall,
                color = if (row.isOverdue) Color(0xFFE74C3C) else DreamlandMuted,
                fontWeight = if (row.isOverdue) FontWeight.SemiBold else FontWeight.Normal,
            )
        }
    }
}

// ── Check availability card (inline sidebar) ──────────────────────────────────

@Composable
private fun CheckAvailabilityCard(
    vm: AvailabilityViewModel = DreamlandAppInitializer.getAvailabilityViewModel(),
) {
    val state by vm.uiState.collectAsStateWithLifecycle()

    Card(
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = DreamlandForestElevated),
    ) {
        Column(Modifier.padding(16.dp).fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("Check Availability", style = MaterialTheme.typography.titleMedium, color = DreamlandOnDark, fontWeight = FontWeight.SemiBold)

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                SidebarDateField("Check-In",  state.checkIn,  vm::setCheckIn,  Modifier.weight(1f))
                SidebarDateField("Check-Out", state.checkOut, vm::setCheckOut, Modifier.weight(1f))
            }

            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.Bottom,
            ) {
                SidebarGuestsField(state.guests, vm::setGuests, Modifier.weight(1f))
                Button(
                    onClick = vm::search,
                    enabled = !state.loading,
                    colors = ButtonDefaults.buttonColors(containerColor = DreamlandGold),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.height(40.dp),
                ) {
                    Icon(Icons.Filled.Search, contentDescription = null, tint = Color(0xFF0D1F17), modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("Search", color = Color(0xFF0D1F17), fontWeight = FontWeight.SemiBold, fontSize = 12.sp)
                }
            }

            if (state.error != null) {
                Text(state.error ?: "", color = Color(0xFFE74C3C), style = MaterialTheme.typography.bodySmall)
            }

            when {
                state.loading -> {
                    Box(Modifier.fillMaxWidth().height(60.dp), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(color = DreamlandGold, strokeWidth = 2.dp, modifier = Modifier.size(24.dp))
                    }
                }
                state.searched && state.results.isEmpty() -> {
                    Box(Modifier.fillMaxWidth().padding(vertical = 6.dp), contentAlignment = Alignment.Center) {
                        Text("No rooms available for these dates", style = MaterialTheme.typography.bodySmall, color = DreamlandMuted, textAlign = TextAlign.Center)
                    }
                }
                state.results.isNotEmpty() -> {
                    Text(
                        "${state.results.size} categor${if (state.results.size != 1) "ies" else "y"} available",
                        style = MaterialTheme.typography.labelMedium,
                        color = DreamlandGold,
                        fontSize = 11.sp,
                    )
                    state.results.forEach { cat -> SidebarAvailableCategoryRow(cat) }
                }
            }
        }
    }
}

@Composable
private fun SidebarDateField(label: String, date: Date, onDateSelected: (Date) -> Unit, modifier: Modifier = Modifier) {
    val fmt = remember { SimpleDateFormat("d MMM yy", Locale.getDefault()) }
    var showPicker by remember { mutableStateOf(false) }

    Column(modifier) {
        Text(label, style = MaterialTheme.typography.labelSmall, color = DreamlandMuted, fontSize = 10.sp)
        Spacer(Modifier.height(4.dp))
        Box(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(8.dp))
                .background(DreamlandForestSurface)
                .border(1.dp, DreamlandGold.copy(alpha = 0.3f), RoundedCornerShape(8.dp))
                .clickable { showPicker = true }
                .padding(horizontal = 10.dp, vertical = 8.dp),
        ) {
            Text(fmt.format(date), style = MaterialTheme.typography.bodySmall, color = DreamlandOnDark, fontSize = 11.sp)
        }
    }

    if (showPicker) {
        SimpleDatePickerDialog(
            initialDate = date,
            onDateSelected = { onDateSelected(it); showPicker = false },
            onDismiss = { showPicker = false },
        )
    }
}

@Composable
private fun SidebarGuestsField(guests: Int, onChanged: (Int) -> Unit, modifier: Modifier = Modifier) {
    Column(modifier) {
        Text("Guests", style = MaterialTheme.typography.labelSmall, color = DreamlandMuted, fontSize = 10.sp)
        Spacer(Modifier.height(4.dp))
        Row(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(8.dp))
                .background(DreamlandForestSurface)
                .border(1.dp, DreamlandGold.copy(alpha = 0.3f), RoundedCornerShape(8.dp))
                .padding(horizontal = 4.dp, vertical = 2.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            TextButton(onClick = { if (guests > 1) onChanged(guests - 1) }, modifier = Modifier.size(28.dp)) {
                Text("-", color = DreamlandGold, fontWeight = FontWeight.Bold, fontSize = 14.sp)
            }
            Text("$guests", color = DreamlandOnDark, fontSize = 13.sp)
            TextButton(onClick = { onChanged(guests + 1) }, modifier = Modifier.size(28.dp)) {
                Text("+", color = DreamlandGold, fontWeight = FontWeight.Bold, fontSize = 14.sp)
            }
        }
    }
}

@Composable
private fun SidebarDatePickerDialog(initialDate: Date, onDateSelected: (Date) -> Unit, onDismiss: () -> Unit) {
    val cal = Calendar.getInstance().apply { time = initialDate }
    var year  by remember { mutableStateOf(cal.get(Calendar.YEAR)) }
    var month by remember { mutableStateOf(cal.get(Calendar.MONTH) + 1) }
    var day   by remember { mutableStateOf(cal.get(Calendar.DAY_OF_MONTH)) }

    Dialog(onDismissRequest = onDismiss) {
        Card(
            colors = CardDefaults.cardColors(containerColor = DreamlandForestSurface),
            shape = RoundedCornerShape(16.dp),
        ) {
            Column(Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Text("Select Date", style = MaterialTheme.typography.titleMedium, color = DreamlandOnDark)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    DashboardNumberSpinner("Day",   day,   1,    31)   { day   = it }
                    DashboardNumberSpinner("Month", month, 1,    12)   { month = it }
                    DashboardNumberSpinner("Year",  year,  2024, 2030) { year  = it }
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    TextButton(onClick = onDismiss) { Text("Cancel", color = DreamlandMuted) }
                    Spacer(Modifier.width(8.dp))
                    Button(
                        onClick = { onDateSelected(dateFromPicker(year, month - 1, day)) },
                        colors = ButtonDefaults.buttonColors(containerColor = DreamlandGold),
                        shape = RoundedCornerShape(8.dp),
                    ) { Text("Set", color = Color(0xFF0D1F17), fontWeight = FontWeight.SemiBold) }
                }
            }
        }
    }
}

@Composable
private fun SidebarAvailableCategoryRow(cat: AvailableCategory) {
    val fmt = remember { NumberFormat.getNumberInstance(Locale("en", "IN")) }
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(DreamlandForestSurface)
            .padding(horizontal = 12.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                cat.room.type.replaceFirstChar { it.titlecase() },
                style = MaterialTheme.typography.bodySmall,
                color = DreamlandOnDark,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                "${cat.availableCount} room${if (cat.availableCount != 1) "s" else ""} free",
                style = MaterialTheme.typography.labelSmall,
                color = Color(0xFF2ECC71),
                fontSize = 10.sp,
            )
            Text(
                "${cat.availableForBookingCount} bookable",
                style = MaterialTheme.typography.labelSmall,
                color = Color(0xFFF39C12),
                fontSize = 10.sp,
            )
        }
        Text(
            "₹${fmt.format(cat.pricePerNight.toLong())}/night",
            style = MaterialTheme.typography.labelSmall,
            color = DreamlandGold,
            fontWeight = FontWeight.Medium,
            fontSize = 11.sp,
        )
    }
}

// ── Trends section ────────────────────────────────────────────────────────────

@Composable
private fun TrendsSection(trendPoints: List<DayTrendPoint>) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text("7-Day Trends", style = MaterialTheme.typography.titleMedium, color = DreamlandOnDark, fontWeight = FontWeight.SemiBold)
        if (trendPoints.isEmpty()) {
            Card(
                colors = CardDefaults.cardColors(containerColor = DreamlandForestElevated),
                shape = RoundedCornerShape(12.dp),
            ) {
                Box(Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) {
                    Text("No trend data yet", style = MaterialTheme.typography.bodySmall, color = DreamlandMuted)
                }
            }
        } else {
            DashboardBarChart(
                title = "Revenue (₹)",
                points = trendPoints,
                valueSelector = { it.revenue.toFloat() },
                formatValue = { "₹${it.toLong()}" },
                barColor = DreamlandGoldBright,
            )
            DashboardBarChart(
                title = "Occupancy (%)",
                points = trendPoints,
                valueSelector = { it.occupancyRate },
                formatValue = { "${(it * 100).toInt()}%" },
                barColor = Color(0xFF3498DB),
            )
        }
    }
}

@Composable
private fun DashboardBarChart(
    title: String,
    points: List<DayTrendPoint>,
    valueSelector: (DayTrendPoint) -> Float,
    formatValue: (Float) -> String,
    barColor: Color,
) {
    Card(
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = DreamlandForestElevated),
    ) {
        Column(Modifier.padding(14.dp).fillMaxWidth()) {
            Text(title, style = MaterialTheme.typography.labelMedium, color = DreamlandMuted, fontWeight = FontWeight.Medium)
            Spacer(Modifier.height(8.dp))
            Box(Modifier.fillMaxWidth().height(120.dp)) {
                val maxVal = points.maxOfOrNull { valueSelector(it) }.takeIf { it != null && it > 0f } ?: 1f
                androidx.compose.foundation.Canvas(
                    modifier = Modifier.fillMaxWidth().height(100.dp),
                ) {
                    val n = points.size
                    val slotW = size.width / n
                    val barW = slotW * 0.55f
                    val chartH = size.height

                    points.forEachIndexed { i, p ->
                        val barH = (valueSelector(p) / maxVal) * chartH
                        val x = i * slotW + (slotW - barW) / 2f
                        drawRect(
                            color = barColor.copy(alpha = if (i == n - 1) 1f else 0.55f),
                            topLeft = Offset(x, chartH - barH),
                            size = Size(barW, barH),
                        )
                    }
                    // Axis line
                    drawLine(
                        color = DreamlandGoldDeep.copy(alpha = 0.4f),
                        start = Offset(0f, chartH),
                        end = Offset(size.width, chartH),
                        strokeWidth = 1.dp.toPx(),
                    )
                }
                // Day labels at bottom
                Row(
                    modifier = Modifier.fillMaxWidth().align(Alignment.BottomCenter),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                ) {
                    points.forEach { p ->
                        Text(
                            text = p.label,
                            style = MaterialTheme.typography.labelSmall,
                            color = DreamlandMuted,
                            fontSize = 9.sp,
                        )
                    }
                }
            }
            // Peak value label
            val peakVal = points.maxOfOrNull { valueSelector(it) } ?: 0f
            if (peakVal > 0f) {
                Text(
                    text = "Peak: ${formatValue(peakVal)}",
                    style = MaterialTheme.typography.labelSmall,
                    color = barColor.copy(alpha = 0.8f),
                    modifier = Modifier.padding(top = 4.dp),
                )
            }
        }
    }
}

// ── Quick actions ─────────────────────────────────────────────────────────────

@Composable
private fun QuickActionsCard(
    onNewWalkIn: () -> Unit,
    onAddBooking: () -> Unit,
    onNavigateToOrders: () -> Unit,
    activeOrdersCount: Int = 0,
) {
    Card(
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = DreamlandForestElevated),
    ) {
        Column(Modifier.padding(16.dp).fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Quick Actions", style = MaterialTheme.typography.titleMedium, color = DreamlandOnDark, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(2.dp))
            // Prominent walk-in check-in button
            Button(
                onClick = onNewWalkIn,
                modifier = Modifier.fillMaxWidth().height(42.dp),
                shape = RoundedCornerShape(8.dp),
                colors = ButtonDefaults.buttonColors(containerColor = DreamlandGold),
            ) {
                Icon(Icons.Filled.PersonAdd, contentDescription = null, tint = Color(0xFF0D1F17), modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(8.dp))
                Text("Walk-in Check-in", style = MaterialTheme.typography.bodySmall, color = Color(0xFF0D1F17), fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f), textAlign = TextAlign.Start)
            }
            QuickActionButton(Icons.Filled.EventAvailable, "Add Booking", onAddBooking)
            ActiveOrdersButton(activeOrdersCount = activeOrdersCount, onClick = onNavigateToOrders)
        }
    }
}

@Composable
private fun QuickActionButton(icon: ImageVector, label: String, onClick: () -> Unit) {
    OutlinedButton(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth().height(40.dp),
        shape = RoundedCornerShape(8.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, DreamlandGoldDeep.copy(alpha = 0.6f)),
        colors = ButtonDefaults.outlinedButtonColors(contentColor = DreamlandOnDark),
    ) {
        Icon(icon, contentDescription = null, tint = DreamlandGold, modifier = Modifier.size(16.dp))
        Spacer(Modifier.width(8.dp))
        Text(label, style = MaterialTheme.typography.bodySmall, color = DreamlandOnDark, modifier = Modifier.weight(1f), textAlign = TextAlign.Start)
    }
}

@Composable
private fun ActiveOrdersButton(activeOrdersCount: Int, onClick: () -> Unit) {
    val hasActive = activeOrdersCount > 0
    val infiniteTransition = rememberInfiniteTransition(label = "ordersAlert")
    val pulse by infiniteTransition.animateFloat(
        initialValue = 0f, targetValue = 1f,
        animationSpec = infiniteRepeatable(animation = tween(700), repeatMode = RepeatMode.Reverse),
        label = "pulse",
    )
    val alertRed = Color(0xFFEF5350)
    OutlinedButton(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth().height(40.dp),
        shape = RoundedCornerShape(8.dp),
        border = androidx.compose.foundation.BorderStroke(
            width = if (hasActive) 1.5.dp else 1.dp,
            color = if (hasActive) alertRed.copy(alpha = 0.4f + pulse * 0.6f) else DreamlandGoldDeep.copy(alpha = 0.6f),
        ),
        colors = ButtonDefaults.outlinedButtonColors(
            containerColor = if (hasActive) alertRed.copy(alpha = pulse * 0.12f) else Color.Transparent,
            contentColor = DreamlandOnDark,
        ),
    ) {
        Icon(Icons.Filled.ShoppingBag, contentDescription = null, tint = if (hasActive) alertRed else DreamlandGold, modifier = Modifier.size(16.dp))
        Spacer(Modifier.width(8.dp))
        Text("View Orders", style = MaterialTheme.typography.bodySmall, color = if (hasActive) alertRed else DreamlandOnDark, modifier = Modifier.weight(1f), textAlign = TextAlign.Start)
        if (hasActive) {
            Box(
                modifier = Modifier.size(20.dp).clip(CircleShape).background(alertRed),
                contentAlignment = Alignment.Center,
            ) {
                Text(activeOrdersCount.toString(), style = MaterialTheme.typography.labelSmall, color = Color.White, fontSize = 10.sp, fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
private fun DashboardNumberSpinner(
    label: String,
    value: Int,
    min: Int,
    max: Int,
    onChanged: (Int) -> Unit,
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(label, style = MaterialTheme.typography.labelSmall, color = DreamlandMuted)
        Spacer(Modifier.height(4.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = { if (value > min) onChanged(value - 1) }) {
                Text("-", color = DreamlandGold, fontWeight = FontWeight.Bold)
            }
            Text("$value", color = DreamlandOnDark, modifier = Modifier.width(40.dp), textAlign = TextAlign.Center)
            TextButton(onClick = { if (value < max) onChanged(value + 1) }) {
                Text("+", color = DreamlandGold, fontWeight = FontWeight.Bold)
            }
        }
    }
}

// ── Utilities (used by other screens — do not remove) ─────────────────────────

@Composable
internal fun ErrorText(message: String) {
    val isSetupError = message.contains("service-account", ignoreCase = true) ||
        message.contains("credentials", ignoreCase = true) ||
        message.contains("not connected", ignoreCase = true)

    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        if (isSetupError) {
            Text(
                text = "Firebase Setup Required",
                style = MaterialTheme.typography.titleMedium,
                color = DreamlandGold,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(12.dp))
            Text(
                text = "Place your service-account.json at:\n~/.dreamland/service-account.json",
                style = MaterialTheme.typography.bodyLarge,
                color = DreamlandOnDark,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(8.dp))
            Text(
                text = "Download from:\nFirebase Console → Project Settings\n→ Service Accounts → Generate new private key",
                style = MaterialTheme.typography.bodyLarge,
                color = DreamlandMuted,
                textAlign = TextAlign.Center,
            )
        } else {
            Text(
                text = "Error: $message",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.error,
                textAlign = TextAlign.Center,
            )
        }
    }
}

@Composable
internal fun ComingSoonContent(subtitle: String) {
    Text(
        text = "Coming soon",
        style = MaterialTheme.typography.bodyLarge,
        color = DreamlandMuted,
        textAlign = TextAlign.Center,
    )
    Spacer(Modifier.height(8.dp))
    Text(
        text = subtitle,
        style = MaterialTheme.typography.labelLarge,
        color = DreamlandMuted.copy(alpha = 0.6f),
        textAlign = TextAlign.Center,
    )
}
