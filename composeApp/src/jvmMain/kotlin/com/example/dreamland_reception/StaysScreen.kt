package com.example.dreamland_reception

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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.dreamland_reception.data.model.Stay
import com.example.dreamland_reception.stays.AddComplaintDialog
import com.example.dreamland_reception.stays.AddOrderDialog
import com.example.dreamland_reception.stays.CheckOutDialog
import com.example.dreamland_reception.stays.FromBookingDialog
import com.example.dreamland_reception.stays.StayDetailPanel
import com.example.dreamland_reception.stays.StayDetailPlaceholder
import com.example.dreamland_reception.stays.WalkInDialog
import com.example.dreamland_reception.ui.viewmodel.StaysListState
import com.example.dreamland_reception.ui.viewmodel.StaysViewModel
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.time.temporal.ChronoUnit
import java.util.Date
import java.util.Locale

@Composable
fun StaysScreen(
    vm: StaysViewModel = DreamlandAppInitializer.getStaysViewModel(),
    onNavigateToBilling: (stayId: String) -> Unit = {},
) {
    val listState by vm.listState.collectAsStateWithLifecycle()
    val walkInState by vm.walkInState.collectAsStateWithLifecycle()
    val fromBookingState by vm.fromBookingState.collectAsStateWithLifecycle()
    val detailState by vm.detailState.collectAsStateWithLifecycle()
    val checkOutState by vm.checkOutState.collectAsStateWithLifecycle()
    val addOrderState by vm.addOrderState.collectAsStateWithLifecycle()
    val addComplaintState by vm.addComplaintState.collectAsStateWithLifecycle()

    // Poll badges every 30s
    LaunchedEffect(Unit) {
        while (true) {
            delay(30_000L)
            vm.pollBadges()
        }
    }

    Row(Modifier.fillMaxSize()) {
        // ── LEFT: Stays list (fixed 380dp) ────────────────────────────────────
        Column(
            modifier = Modifier.width(380.dp).fillMaxHeight().background(DreamlandForestSurface),
        ) {
            StaysListHeader(vm)
            StaysFilterRow(listState, vm)
            StayCardList(listState, vm)
        }

        VerticalDivider(color = DreamlandGold.copy(alpha = 0.15f), thickness = 1.dp)

        // ── RIGHT: Detail panel ────────────────────────────────────────────────
        Box(Modifier.weight(1f).fillMaxHeight()) {
            val selectedId = listState.selectedStayId
            if (selectedId != null) {
                StayDetailPanel(listState, detailState, vm)
            } else {
                StayDetailPlaceholder()
            }
        }
    }

    // ── Dialogs ────────────────────────────────────────────────────────────────
    if (walkInState.isOpen) WalkInDialog(walkInState, vm)
    if (fromBookingState.isOpen) FromBookingDialog(fromBookingState, vm)
    CheckOutDialog(checkOutState, vm, onNavigateToBilling = onNavigateToBilling)
    if (addOrderState.isOpen) AddOrderDialog(addOrderState, vm)
    if (addComplaintState.isOpen) AddComplaintDialog(addComplaintState, vm)
}

// ── Header ────────────────────────────────────────────────────────────────────

@Composable
private fun StaysListHeader(vm: StaysViewModel) {
    val dateFmt = SimpleDateFormat("EEEE, dd MMM yyyy", Locale.getDefault())
    Column(Modifier.fillMaxWidth().padding(start = 20.dp, end = 20.dp, top = 20.dp, bottom = 12.dp)) {
        Text("GUEST STAYS", style = MaterialTheme.typography.labelLarge, color = DreamlandGold, letterSpacing = 2.sp)
        Text(dateFmt.format(Date()), style = MaterialTheme.typography.bodySmall, color = DreamlandMuted)
        Spacer(Modifier.height(14.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(
                onClick = { vm.openWalkIn() },
                colors = ButtonDefaults.buttonColors(containerColor = DreamlandGold),
                shape = RoundedCornerShape(8.dp),
                modifier = Modifier.weight(1f).height(38.dp),
            ) {
                Text("+ Walk-in", color = Color(0xFF0D1F17), fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
            }
            OutlinedButton(
                onClick = { vm.openFromBooking() },
                shape = RoundedCornerShape(8.dp),
                modifier = Modifier.weight(1f).height(38.dp),
                border = androidx.compose.foundation.BorderStroke(1.dp, DreamlandGold.copy(alpha = 0.6f)),
            ) {
                Text("From Booking", color = DreamlandGold, fontSize = 13.sp)
            }
        }
    }
    HorizontalDivider(color = DreamlandGold.copy(alpha = 0.15f))
}

// ── Filter row ────────────────────────────────────────────────────────────────

@Composable
private fun StaysFilterRow(listState: StaysListState, vm: StaysViewModel) {
    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) {
        OutlinedTextField(
            value = listState.searchQuery,
            onValueChange = vm::onSearch,
            placeholder = { Text("Search by guest or room...", color = DreamlandMuted, fontSize = 13.sp) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
            colors = OutlinedTextFieldDefaults.colors(
                focusedTextColor = DreamlandOnDark,
                unfocusedTextColor = DreamlandOnDark,
                focusedBorderColor = DreamlandGold,
                unfocusedBorderColor = DreamlandMuted.copy(alpha = 0.3f),
                cursorColor = DreamlandGold,
            ),
        )
        Spacer(Modifier.height(8.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("Show completed", color = DreamlandMuted, style = MaterialTheme.typography.bodySmall)
            Switch(
                checked = listState.showCompleted,
                onCheckedChange = { vm.toggleShowCompleted(it) },
                colors = SwitchDefaults.colors(
                    checkedTrackColor = DreamlandGold,
                    checkedThumbColor = Color(0xFF0D1F17),
                    uncheckedThumbColor = DreamlandMuted,
                    uncheckedTrackColor = DreamlandForestElevated,
                ),
            )
        }
    }
    HorizontalDivider(color = DreamlandGold.copy(alpha = 0.15f))
}

// ── Stay card list ────────────────────────────────────────────────────────────

@Composable
private fun StayCardList(listState: StaysListState, vm: StaysViewModel) {
    when {
        listState.isLoading -> {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = DreamlandGold)
            }
        }
        listState.error != null -> {
            Box(Modifier.fillMaxSize().padding(16.dp), contentAlignment = Alignment.Center) {
                Text(listState.error, color = Color(0xFFEF5350), style = MaterialTheme.typography.bodySmall)
            }
        }
        listState.filtered.isEmpty() -> {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(
                    if (listState.stays.isEmpty()) "No active stays" else "No results",
                    color = DreamlandMuted,
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        }
        else -> {
            LazyColumn(modifier = Modifier.fillMaxSize().padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(listState.filtered, key = { it.id }) { stay ->
                    StayCard(
                        stay = stay,
                        isSelected = stay.id == listState.selectedStayId,
                        pendingOrders = listState.pendingOrdersByStay[stay.id] ?: 0,
                        openComplaints = listState.openComplaintsByStay[stay.id] ?: 0,
                        onClick = { vm.selectStay(stay.id) },
                    )
                }
            }
        }
    }
}

// ── Stay card ─────────────────────────────────────────────────────────────────

@Composable
private fun StayCard(
    stay: Stay,
    isSelected: Boolean,
    pendingOrders: Int,
    openComplaints: Int,
    onClick: () -> Unit,
) {
    val fmt = SimpleDateFormat("dd MMM", Locale.getDefault())
    val nights = ChronoUnit.DAYS.between(stay.checkInActual.toInstant(), stay.expectedCheckOut.toInstant()).coerceAtLeast(1)
    val borderColor = when {
        isSelected -> DreamlandGold
        stay.status == "ACTIVE" -> Color(0xFF4CAF50)
        else -> DreamlandMuted.copy(alpha = 0.3f)
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .border(1.dp, borderColor, RoundedCornerShape(10.dp))
            .background(if (isSelected) DreamlandForestElevated else DreamlandForestSurface)
            .clickable(onClick = onClick),
    ) {
        // Left accent bar
        Box(
            modifier = Modifier
                .width(4.dp)
                .fillMaxHeight()
                .background(
                    color = when (stay.status) {
                        "ACTIVE" -> Color(0xFF4CAF50)
                        else -> DreamlandMuted.copy(alpha = 0.3f)
                    },
                    shape = RoundedCornerShape(topStart = 10.dp, bottomStart = 10.dp),
                ),
        )
        Column(Modifier.weight(1f).padding(12.dp)) {
            // Row 1: room + status
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "Room ${stay.roomNumber} · ${stay.roomCategoryName.ifBlank { "—" }}",
                    color = DreamlandOnDark,
                    fontWeight = FontWeight.SemiBold,
                    style = MaterialTheme.typography.bodyMedium,
                )
                StatusBadge(stay.status)
            }
            Spacer(Modifier.height(2.dp))
            // Row 2: guest name
            Text(stay.guestName, color = DreamlandMuted, style = MaterialTheme.typography.bodySmall)
            Spacer(Modifier.height(4.dp))
            // Row 3: dates + nights
            Text(
                "${fmt.format(stay.checkInActual)} → ${fmt.format(stay.expectedCheckOut)} ($nights nights)",
                color = DreamlandMuted.copy(alpha = 0.8f),
                style = MaterialTheme.typography.bodySmall,
            )
            Spacer(Modifier.height(6.dp))
            // Row 4: occupancy + badges
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text("${stay.adults}A${if (stay.children > 0) " +${stay.children}C" else ""}", color = DreamlandMuted, style = MaterialTheme.typography.bodySmall)
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    if (pendingOrders > 0) NumberBadge(pendingOrders, "Orders", Color(0xFFFFC107))
                    if (openComplaints > 0) NumberBadge(openComplaints, "Issues", Color(0xFFEF5350))
                }
            }
        }
    }
}

@Composable
private fun StatusBadge(status: String) {
    val color = when (status) {
        "ACTIVE" -> Color(0xFF4CAF50)
        "COMPLETED" -> DreamlandMuted
        else -> DreamlandMuted
    }
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(20.dp))
            .background(color.copy(alpha = 0.15f))
            .padding(horizontal = 8.dp, vertical = 3.dp),
    ) {
        Text(status, color = color, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun NumberBadge(count: Int, label: String, color: Color) {
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(20.dp))
            .background(color.copy(alpha = 0.15f))
            .padding(horizontal = 6.dp, vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(3.dp),
    ) {
        Box(Modifier.size(6.dp).clip(CircleShape).background(color))
        Text("$count $label", color = color, style = MaterialTheme.typography.labelSmall)
    }
}
