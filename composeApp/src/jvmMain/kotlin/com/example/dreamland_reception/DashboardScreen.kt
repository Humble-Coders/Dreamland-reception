@file:OptIn(ExperimentalMaterial3Api::class)

package com.example.dreamland_reception

import androidx.compose.animation.core.LinearEasing
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
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.CreditCard
import androidx.compose.material.icons.filled.Feedback
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.Hotel
import androidx.compose.material.icons.automirrored.filled.Login
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material.icons.filled.Receipt
import androidx.compose.material.icons.filled.ShoppingBag
import androidx.compose.material.icons.automirrored.filled.TrendingFlat
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
import com.example.dreamland_reception.ui.viewmodel.ActiveStayRow
import com.example.dreamland_reception.ui.viewmodel.AlertType
import com.example.dreamland_reception.ui.viewmodel.DashboardAlert
import com.example.dreamland_reception.ui.viewmodel.DashboardState
import com.example.dreamland_reception.ui.viewmodel.DashboardViewModel
import com.example.dreamland_reception.ui.viewmodel.DayTrendPoint
import com.example.dreamland_reception.ui.viewmodel.RoomStatusBreakdown
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

// ── Entry point ───────────────────────────────────────────────────────────────

@Composable
fun DashboardScreen(
    vm: DashboardViewModel = DreamlandAppInitializer.getDashboardViewModel(),
    onNewWalkIn: () -> Unit = {},
    onNavigateToBookings: () -> Unit = {},
    onNavigateToOrders: () -> Unit = {},
    onNavigateToComplaints: () -> Unit = {},
    onNavigateToStaff: () -> Unit = {},
) {
    val state by vm.state.collectAsStateWithLifecycle()
    var showDatePicker by remember { mutableStateOf(false) }
    var showCheckAvailability by remember { mutableStateOf(false) }

    Column(Modifier.fillMaxSize()) {
        DashboardHeader(
            state = state,
            onDateChipClick = { showDatePicker = true },
            onNewWalkIn = onNewWalkIn,
            onNavigateToBookings = onNavigateToBookings,
        )

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

        if (state.isLoading && state.activeStays.isEmpty() && state.roomStatus.total == 0) {
            LoadingDashboard()
        } else {
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
                    KpiCardsSection(state)
                    AlertsSection(
                        alerts = state.alerts,
                        onOrdersClick = onNavigateToOrders,
                        onComplaintsClick = onNavigateToComplaints,
                    )
                    ActiveStaysSection(stays = state.activeStays)
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
                    RoomStatusCard(state.roomStatus)
                    TrendsSection(state.trendPoints)
                    QuickActionsCard(
                        onNewWalkIn = onNewWalkIn,
                        onNavigateToOrders = onNavigateToOrders,
                        onNavigateToComplaints = onNavigateToComplaints,
                        onNavigateToStaff = onNavigateToStaff,
                        onCheckAvailability = { showCheckAvailability = true },
                    )
                }
            }
        }
    }

    if (showDatePicker) {
        DashboardDatePickerDialog(
            currentDate = state.selectedDate,
            onSelect = { date -> vm.onDateSelected(date); showDatePicker = false },
            onDismiss = { showDatePicker = false },
        )
    }
    if (showCheckAvailability) {
        com.example.dreamland_reception.stays.CheckAvailabilityDialog(
            onDismiss = { showCheckAvailability = false },
        )
    }
}

// ── Header ────────────────────────────────────────────────────────────────────

@Composable
private fun DashboardHeader(
    state: DashboardState,
    onDateChipClick: () -> Unit,
    onNewWalkIn: () -> Unit,
    onNavigateToBookings: () -> Unit,
) {
    val dateFmt = remember { SimpleDateFormat("dd MMM yyyy", Locale.getDefault()) }
    Surface(
        color = DreamlandForestSurface,
        shadowElevation = 2.dp,
        modifier = Modifier.fillMaxWidth().border(1.dp, DreamlandGold.copy(alpha = 0.18f)),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(64.dp)
                .padding(horizontal = 20.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            // Title area
            Column {
                Text(
                    text = "OPERATIONS",
                    style = MaterialTheme.typography.labelSmall,
                    color = DreamlandGold,
                    letterSpacing = 2.sp,
                )
                Text(
                    text = "Dashboard",
                    style = MaterialTheme.typography.titleLarge,
                    color = DreamlandOnDark,
                    fontWeight = FontWeight.SemiBold,
                )
            }

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                // LIVE badge
                if (state.isToday) {
                    LiveDot()
                }

                // Date chip
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(20.dp))
                        .background(DreamlandForestElevated)
                        .border(1.dp, DreamlandGold.copy(alpha = 0.4f), RoundedCornerShape(20.dp))
                        .clickable(onClick = onDateChipClick)
                        .padding(horizontal = 14.dp, vertical = 6.dp),
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Icon(Icons.AutoMirrored.Filled.TrendingFlat, contentDescription = null, tint = DreamlandGold, modifier = Modifier.size(14.dp))
                        Text(
                            text = if (state.isToday) "Today, ${dateFmt.format(state.selectedDate)}"
                                   else dateFmt.format(state.selectedDate),
                            style = MaterialTheme.typography.labelMedium,
                            color = DreamlandOnDark,
                        )
                    }
                }

                // Walk-in button
                Button(
                    onClick = onNewWalkIn,
                    colors = ButtonDefaults.buttonColors(containerColor = DreamlandGold),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.height(36.dp),
                ) {
                    Icon(Icons.Filled.PersonAdd, contentDescription = null, tint = Color(0xFF0D1F17), modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Walk-in", color = Color(0xFF0D1F17), fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                }

                // Bookings button
                OutlinedButton(
                    onClick = onNavigateToBookings,
                    shape = RoundedCornerShape(8.dp),
                    border = androidx.compose.foundation.BorderStroke(1.dp, DreamlandGoldDeep),
                    modifier = Modifier.height(36.dp),
                ) {
                    Icon(Icons.Filled.Hotel, contentDescription = null, tint = DreamlandGold, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Bookings", color = DreamlandGold, fontSize = 13.sp)
                }
            }
        }
    }
}

@Composable
private fun LiveDot() {
    val infiniteTransition = rememberInfiniteTransition(label = "live_pulse")
    val alpha by infiniteTransition.animateFloat(
        initialValue = 0.4f, targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(900, easing = LinearEasing), RepeatMode.Reverse),
        label = "live_alpha",
    )
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        Box(
            modifier = Modifier
                .size(8.dp)
                .clip(CircleShape)
                .background(Color(0xFF2ECC71).copy(alpha = alpha)),
        )
        Text("LIVE", style = MaterialTheme.typography.labelSmall, color = Color(0xFF2ECC71), letterSpacing = 1.sp)
    }
}

// ── Loading placeholder ───────────────────────────────────────────────────────

@Composable
private fun LoadingDashboard() {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
            CircularProgressIndicator(color = DreamlandGold, strokeWidth = 3.dp, modifier = Modifier.size(40.dp))
            Text("Loading dashboard…", style = MaterialTheme.typography.bodyMedium, color = DreamlandMuted)
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

// ── Room status card ──────────────────────────────────────────────────────────

@Composable
private fun RoomStatusCard(breakdown: RoomStatusBreakdown) {
    Card(
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = DreamlandForestElevated),
    ) {
        Column(Modifier.padding(16.dp).fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text("Room Status", style = MaterialTheme.typography.titleMedium, color = DreamlandOnDark, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(4.dp))
            RoomStatusRow("Available",   breakdown.available,   Color(0xFF2ECC71))
            RoomStatusRow("Occupied",    breakdown.occupied,    Color(0xFFE74C3C))
            RoomStatusRow("Cleaning",    breakdown.cleaning,    Color(0xFFE67E22))
            RoomStatusRow("Maintenance", breakdown.maintenance, Color(0xFF95A5A6))
            Spacer(Modifier.height(4.dp))
            Box(Modifier.fillMaxWidth().height(1.dp).background(DreamlandGold.copy(alpha = 0.12f)))
            Spacer(Modifier.height(4.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("Total", style = MaterialTheme.typography.labelMedium, color = DreamlandMuted, fontWeight = FontWeight.Medium)
                Text("${breakdown.total}", style = MaterialTheme.typography.labelMedium, color = DreamlandOnDark, fontWeight = FontWeight.SemiBold)
            }
        }
    }
}

@Composable
private fun RoomStatusRow(label: String, count: Int, color: Color) {
    Row(
        Modifier.fillMaxWidth().padding(vertical = 3.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Box(modifier = Modifier.size(8.dp).clip(CircleShape).background(color))
            Text(label, style = MaterialTheme.typography.bodySmall, color = DreamlandOnDark)
        }
        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(8.dp))
                .background(color.copy(alpha = 0.15f))
                .padding(horizontal = 10.dp, vertical = 2.dp),
        ) {
            Text("$count", style = MaterialTheme.typography.labelMedium, color = color, fontWeight = FontWeight.SemiBold)
        }
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
    onNavigateToOrders: () -> Unit,
    onNavigateToComplaints: () -> Unit,
    onNavigateToStaff: () -> Unit,
    onCheckAvailability: () -> Unit = {},
) {
    Card(
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = DreamlandForestElevated),
    ) {
        Column(Modifier.padding(16.dp).fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Quick Actions", style = MaterialTheme.typography.titleMedium, color = DreamlandOnDark, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(2.dp))
            // Prominent availability check button
            Button(
                onClick = onCheckAvailability,
                modifier = Modifier.fillMaxWidth().height(42.dp),
                shape = RoundedCornerShape(8.dp),
                colors = ButtonDefaults.buttonColors(containerColor = DreamlandGold),
            ) {
                Icon(Icons.Filled.Hotel, contentDescription = null, tint = Color(0xFF0D1F17), modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(8.dp))
                Text("Check Availability", style = MaterialTheme.typography.bodySmall, color = Color(0xFF0D1F17), fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f), textAlign = TextAlign.Start)
            }
            QuickActionButton(Icons.Filled.PersonAdd, "Walk-in Check-in", onNewWalkIn)
            QuickActionButton(Icons.Filled.ShoppingBag, "View Orders", onNavigateToOrders)
            QuickActionButton(Icons.Filled.Feedback, "View Complaints", onNavigateToComplaints)
            QuickActionButton(Icons.Filled.Groups, "Staff Management", onNavigateToStaff)
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

// ── Date picker dialog ────────────────────────────────────────────────────────

@Composable
private fun DashboardDatePickerDialog(
    currentDate: Date,
    onSelect: (Date) -> Unit,
    onDismiss: () -> Unit,
) {
    val cal = Calendar.getInstance().apply { time = currentDate }
    var year by remember { mutableStateOf(cal.get(Calendar.YEAR)) }
    var month by remember { mutableStateOf(cal.get(Calendar.MONTH) + 1) }
    var day by remember { mutableStateOf(cal.get(Calendar.DAY_OF_MONTH)) }

    Dialog(onDismissRequest = onDismiss) {
        Card(
            colors = CardDefaults.cardColors(containerColor = DreamlandForestSurface),
            shape = RoundedCornerShape(16.dp),
        ) {
            Column(Modifier.padding(24.dp)) {
                Text("Select Date", style = MaterialTheme.typography.titleMedium, color = DreamlandOnDark)
                Spacer(Modifier.height(16.dp))
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    DashboardNumberSpinner("Day", day, 1, 31) { day = it }
                    DashboardNumberSpinner("Month", month, 1, 12) { month = it }
                    DashboardNumberSpinner("Year", year, 2020, 2030) { year = it }
                }
                Spacer(Modifier.height(16.dp))
                Row(horizontalArrangement = Arrangement.End, modifier = Modifier.fillMaxWidth()) {
                    TextButton(onClick = onDismiss) { Text("Cancel", color = DreamlandMuted) }
                    Spacer(Modifier.width(8.dp))
                    Button(
                        onClick = {
                            val c = Calendar.getInstance()
                            c.set(year, month - 1, day, 0, 0, 0)
                            c.set(Calendar.MILLISECOND, 0)
                            onSelect(c.time)
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = DreamlandGold),
                    ) {
                        Text("Set", color = Color(0xFF0D1F17), fontWeight = FontWeight.SemiBold)
                    }
                }
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
