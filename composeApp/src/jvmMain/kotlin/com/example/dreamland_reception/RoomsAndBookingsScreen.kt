@file:OptIn(ExperimentalMaterial3Api::class)

package com.example.dreamland_reception

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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Sort
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.TabRowDefaults.SecondaryIndicator
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.VerticalDivider
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.dreamland_reception.data.model.Booking
import com.example.dreamland_reception.data.model.RoomInstance
import com.example.dreamland_reception.data.model.Stay
import com.example.dreamland_reception.roomsbookings.AssignRoomDialog
import com.example.dreamland_reception.ui.viewmodel.BookingDateFilter
import com.example.dreamland_reception.ui.viewmodel.RoomsAndBookingsUiState
import com.example.dreamland_reception.ui.viewmodel.RoomsAndBookingsViewModel
import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

// ── Helpers ────────────────────────────────────────────────────────────────────

// Returns null if hotel times haven't been fetched yet (empty string).
// Callers must handle null to avoid showing stale availability before hotel data loads.
private fun hotelTimeToday(timeStr: String, daysOffset: Int): Date? {
    if (timeStr.isBlank()) return null
    val parts = timeStr.split(":")
    val h = parts.getOrNull(0)?.toIntOrNull() ?: return null
    val m = parts.getOrNull(1)?.toIntOrNull() ?: 0
    return Calendar.getInstance().apply {
        if (daysOffset != 0) add(Calendar.DAY_OF_YEAR, daysOffset)
        set(Calendar.HOUR_OF_DAY, h); set(Calendar.MINUTE, m)
        set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
    }.time
}

// ── Status colours ─────────────────────────────────────────────────────────────

private fun roomStatusColor(status: String): Color = when (status) {
    "AVAILABLE"   -> Color(0xFF2ECC71)
    "OCCUPIED"    -> Color(0xFFE74C3C)
    "CLEANING"    -> Color(0xFFE67E22)
    "MAINTENANCE" -> Color(0xFF95A5A6)
    else          -> Color(0xFF8FA69E)
}

private fun bookingStatusColor(status: String): Color = when (status) {
    "CONFIRMED" -> Color(0xFFF39C12)
    "COMPLETED" -> Color(0xFF2ECC71)
    "CANCELLED" -> Color(0xFFE74C3C)
    "NO_SHOW" -> Color(0xFF95A5A6)
    else -> Color(0xFF8FA69E)
}

private fun formatRupees(amount: Double): String =
    "₹${NumberFormat.getNumberInstance(Locale("en", "IN")).format(amount.toLong())}"

// ─────────────────────────────────────────────────────────────────────────────
// Root screen
// ─────────────────────────────────────────────────────────────────────────────

@Composable
fun RoomsAndBookingsScreen(
    vm: RoomsAndBookingsViewModel = DreamlandAppInitializer.getRoomsAndBookingsViewModel(),
    onCheckIn: (Booking) -> Unit = {},
) {
    val state by vm.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(state.operationMessage) {
        state.operationMessage?.let { msg ->
            snackbarHostState.showSnackbar(msg)
            vm.clearMessage()
        }
    }
    LaunchedEffect(state.error) {
        state.error?.let { err ->
            snackbarHostState.showSnackbar("Error: $err")
            vm.clearError()
        }
    }

    Box(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize()) {
            PageHeader(state = state, vm = vm)

            RoomsBookingsTabRow(
                selectedTab = state.selectedTab,
                roomCount = state.rooms.size,
                bookingCount = state.filteredBookings.size,
                onTabSelected = vm::setTab,
            )

            Box(Modifier.weight(1f).fillMaxWidth()) {
                when {
                    state.isInitialLoading -> {
                        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                CircularProgressIndicator(color = DreamlandGold, strokeWidth = 2.5.dp)
                                Spacer(Modifier.height(16.dp))
                                Text("Loading…", style = MaterialTheme.typography.bodySmall, color = DreamlandMuted)
                            }
                        }
                    }
                    state.selectedTab == 0 -> RoomsTabContent(state = state, vm = vm)
                    else -> BookingsTabContent(state = state, vm = vm, onCheckIn = onCheckIn)
                }
            }
        }

        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 16.dp),
        )
    }

    if (state.assignRoomDialogBooking != null) {
        AssignRoomDialog(state = state, vm = vm)
    }
    if (state.cancelConfirmBooking != null) {
        CancelConfirmDialog(
            guestName = state.cancelConfirmBooking!!.guestName,
            onConfirm = vm::confirmCancelBooking,
            onDismiss = vm::dismissCancelBooking,
        )
    }
    if (state.noShowConfirmBooking != null) {
        NoShowConfirmDialog(
            booking = state.noShowConfirmBooking!!,
            onConfirm = vm::confirmNoShow,
            onDismiss = vm::dismissNoShow,
        )
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Page header — title + compact search bar
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun PageHeader(state: RoomsAndBookingsUiState, vm: RoomsAndBookingsViewModel) {
    val searchPlaceholder = if (state.selectedTab == 0) "Search rooms…" else "Search bookings…"

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(DreamlandForestSurface)
            .padding(horizontal = 24.dp, vertical = 20.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Column {
            Text(
                text = "ACCOMMODATION",
                style = MaterialTheme.typography.labelLarge,
                color = DreamlandGold,
                letterSpacing = 2.sp,
            )
            Spacer(Modifier.height(2.dp))
            Text(
                text = "Rooms & Bookings",
                style = MaterialTheme.typography.headlineMedium,
                color = DreamlandOnDark,
            )
        }

        OutlinedTextField(
            value = state.searchQuery,
            onValueChange = vm::setSearchQuery,
            placeholder = { Text(searchPlaceholder, style = MaterialTheme.typography.bodySmall, color = DreamlandMuted, fontSize = 12.sp) },
            leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null, tint = DreamlandMuted, modifier = Modifier.size(18.dp)) },
            trailingIcon = {
                if (state.searchQuery.isNotEmpty()) {
                    IconButton(onClick = { vm.setSearchQuery("") }) {
                        Icon(Icons.Filled.Close, contentDescription = "Clear", tint = DreamlandMuted, modifier = Modifier.size(16.dp))
                    }
                }
            },
            singleLine = true,
            modifier = Modifier.width(260.dp),
            shape = RoundedCornerShape(10.dp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedTextColor = DreamlandOnDark,
                unfocusedTextColor = DreamlandOnDark,
                focusedBorderColor = DreamlandGold.copy(alpha = 0.6f),
                unfocusedBorderColor = DreamlandGold.copy(alpha = 0.25f),
                cursorColor = DreamlandGold,
                focusedContainerColor = DreamlandForestElevated,
                unfocusedContainerColor = DreamlandForestElevated,
            ),
        )
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Tab row
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun RoomsBookingsTabRow(
    selectedTab: Int,
    roomCount: Int,
    bookingCount: Int,
    onTabSelected: (Int) -> Unit,
) {
    TabRow(
        selectedTabIndex = selectedTab,
        containerColor = DreamlandForestSurface,
        contentColor = DreamlandOnDark,
        indicator = { tabPositions ->
            SecondaryIndicator(
                modifier = Modifier.tabIndicatorOffset(tabPositions[selectedTab]),
                color = DreamlandGold,
            )
        },
        divider = { HorizontalDivider(color = DreamlandGold.copy(alpha = 0.15f)) },
    ) {
        val tabs = listOf("Rooms" to roomCount, "Bookings" to bookingCount)
        tabs.forEachIndexed { index, (label, count) ->
            Tab(
                selected = selectedTab == index,
                onClick = { onTabSelected(index) },
                selectedContentColor = DreamlandGold,
                unselectedContentColor = DreamlandMuted,
            ) {
                Row(
                    modifier = Modifier.padding(vertical = 14.dp, horizontal = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text(
                        text = label,
                        fontWeight = if (selectedTab == index) FontWeight.SemiBold else FontWeight.Normal,
                        fontSize = 14.sp,
                    )
                    if (count > 0) {
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(10.dp))
                                .background(
                                    if (selectedTab == index) DreamlandGold.copy(alpha = 0.2f)
                                    else DreamlandMuted.copy(alpha = 0.15f),
                                )
                                .padding(horizontal = 7.dp, vertical = 2.dp),
                        ) {
                            Text(
                                text = count.toString(),
                                style = MaterialTheme.typography.labelMedium,
                                color = if (selectedTab == index) DreamlandGold else DreamlandMuted,
                                fontSize = 11.sp,
                            )
                        }
                    }
                }
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Rooms tab — 30 / 70 master-detail split
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun RoomsTabContent(state: RoomsAndBookingsUiState, vm: RoomsAndBookingsViewModel) {
    Row(Modifier.fillMaxSize()) {

        // ── Left panel (30%) ──────────────────────────────────────────────────
        Column(
            Modifier
                .fillMaxHeight()
                .weight(0.3f)
                .background(DreamlandForestSurface),
        ) {
            // Null until hotel data loads from Firestore; no OCCUPIED derived until both times are available
            val rangeStart = hotelTimeToday(state.hotelCheckInTime, 0)
            val rangeEnd   = hotelTimeToday(state.hotelCheckOutTime, 1)
            val hotelTimesReady = rangeStart != null && rangeEnd != null

            fun roomDisplayStatus(room: RoomInstance): String = when {
                room.status == "MAINTENANCE" -> "MAINTENANCE"
                room.status == "CLEANING"    -> "CLEANING"
                hotelTimesReady && state.activeStaysByRoom[room.roomNumber]?.let { s ->
                    s.expectedCheckOut.after(rangeStart!!)
                } == true -> "OCCUPIED"
                else -> "AVAILABLE"
            }

            val allRooms = state.roomsForPanel
            val summaryMap = mapOf(
                "AVAILABLE"   to allRooms.count { roomDisplayStatus(it) == "AVAILABLE" },
                "OCCUPIED"    to allRooms.count { roomDisplayStatus(it) == "OCCUPIED" },
                "CLEANING"    to allRooms.count { roomDisplayStatus(it) == "CLEANING" },
                "MAINTENANCE" to allRooms.count { roomDisplayStatus(it) == "MAINTENANCE" },
            )

            RoomStatusSummary(counts = summaryMap)
            HorizontalDivider(color = DreamlandGold.copy(alpha = 0.12f))

            Column(
                Modifier.padding(horizontal = 10.dp, vertical = 10.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                RoomFilterDropdown(
                    label = "Category",
                    selected = state.roomCategoryFilter,
                    options = listOf("" to "All Categories") +
                        state.categoryNames.entries.sortedBy { it.value }.map { it.key to it.value },
                    onSelect = vm::setRoomCategoryFilter,
                )
                RoomFilterDropdown(
                    label = "Status",
                    selected = state.roomStatusFilter,
                    options = listOf(
                        "" to "All Statuses",
                        "AVAILABLE"   to "Available",
                        "OCCUPIED"    to "Occupied",
                        "CLEANING"    to "Cleaning",
                        "MAINTENANCE" to "Maintenance",
                    ),
                    onSelect = vm::setRoomStatusFilter,
                )
            }

            HorizontalDivider(color = DreamlandGold.copy(alpha = 0.12f))

            val rooms = if (state.roomStatusFilter.isBlank()) allRooms
                        else allRooms.filter { roomDisplayStatus(it) == state.roomStatusFilter }

            if (rooms.isEmpty()) {
                Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                    Text("No rooms", style = MaterialTheme.typography.bodySmall, color = DreamlandMuted)
                }
            } else {
                LazyColumn(
                    modifier = Modifier.weight(1f),
                    contentPadding = PaddingValues(8.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    items(rooms, key = { it.id }) { room ->
                        val displayStatus = roomDisplayStatus(room)
                        val guestName = if (displayStatus == "OCCUPIED")
                            state.activeStaysByRoom[room.roomNumber]?.guestName else null
                        RoomListItem(
                            room = room,
                            guestName = guestName,
                            displayStatus = displayStatus,
                            isSelected = room.id == state.selectedRoomId,
                            onClick = {
                                vm.selectRoom(if (room.id == state.selectedRoomId) null else room.id)
                            },
                        )
                    }
                }
            }
        }

        VerticalDivider(color = DreamlandGold.copy(alpha = 0.15f))

        // ── Right panel (70%) ─────────────────────────────────────────────────
        Box(
            Modifier
                .weight(0.7f)
                .fillMaxHeight()
                .background(DreamlandForest),
        ) {
            val selectedRoom = state.selectedRoom
            if (selectedRoom == null) {
                Column(
                    Modifier.fillMaxSize(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                ) {
                    Text(
                        "Select a room to view details",
                        style = MaterialTheme.typography.bodyLarge,
                        color = DreamlandMuted,
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "${state.rooms.size} rooms · ${state.rooms.count { it.status == "AVAILABLE" }} available",
                        style = MaterialTheme.typography.bodySmall,
                        color = DreamlandMuted.copy(alpha = 0.6f),
                    )
                }
            } else {
                RoomDetailPanel(room = selectedRoom, state = state, vm = vm)
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Room list item (left panel)
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun RoomListItem(
    room: RoomInstance,
    guestName: String?,
    displayStatus: String,
    isSelected: Boolean,
    onClick: () -> Unit,
) {
    val statusColor = roomStatusColor(displayStatus)
    val showPill = displayStatus in setOf("OCCUPIED", "CLEANING", "MAINTENANCE")

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(if (isSelected) DreamlandGold.copy(alpha = 0.1f) else Color.Transparent)
            .border(
                1.dp,
                if (isSelected) DreamlandGold.copy(alpha = 0.5f) else DreamlandGold.copy(alpha = 0.1f),
                RoundedCornerShape(8.dp),
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier
                .width(3.dp)
                .height(36.dp)
                .clip(RoundedCornerShape(2.dp))
                .background(statusColor),
        )
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text(
                "Room ${room.roomNumber}",
                style = MaterialTheme.typography.bodyMedium,
                color = DreamlandOnDark,
                fontWeight = FontWeight.SemiBold,
            )
            if (room.categoryName.isNotBlank()) {
                Text(
                    room.categoryName,
                    style = MaterialTheme.typography.labelSmall,
                    color = DreamlandMuted,
                    fontSize = 9.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            if (!guestName.isNullOrBlank()) {
                Text(
                    guestName,
                    style = MaterialTheme.typography.bodySmall,
                    color = DreamlandMuted,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        if (showPill) {
            Box(
                Modifier
                    .clip(RoundedCornerShape(4.dp))
                    .background(statusColor.copy(alpha = 0.15f))
                    .padding(horizontal = 6.dp, vertical = 3.dp),
            ) {
                Text(
                    displayStatus,
                    style = MaterialTheme.typography.labelSmall,
                    color = statusColor,
                    fontSize = 9.sp,
                    letterSpacing = 0.5.sp,
                )
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Room detail panel (right panel)
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun RoomDetailPanel(
    room: RoomInstance,
    state: RoomsAndBookingsUiState,
    vm: RoomsAndBookingsViewModel,
) {
    val fmt = SimpleDateFormat("d MMM yyyy", Locale.getDefault())
    val fmtShort = SimpleDateFormat("d MMM", Locale.getDefault())
    val statusColor = roomStatusColor(room.status)

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        // Header
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column {
                Text(
                    "Room ${room.roomNumber}",
                    style = MaterialTheme.typography.headlineMedium,
                    color = DreamlandOnDark,
                    fontWeight = FontWeight.Bold,
                )
                val catName = state.categoryNames[room.categoryId] ?: room.categoryName.ifBlank { "—" }
                Text(catName, style = MaterialTheme.typography.bodyMedium, color = DreamlandMuted)
            }
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Box(
                    Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(statusColor.copy(alpha = 0.15f))
                        .border(1.dp, statusColor.copy(alpha = 0.4f), RoundedCornerShape(8.dp))
                        .padding(horizontal = 14.dp, vertical = 6.dp),
                ) {
                    Text(room.status, style = MaterialTheme.typography.labelMedium, color = statusColor, letterSpacing = 1.sp)
                }
                if (room.status == "CLEANING") {
                    Button(
                        onClick = { vm.markCleaningComplete(room) },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2ECC71)),
                        shape = RoundedCornerShape(8.dp),
                        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp),
                    ) {
                        Icon(Icons.Filled.CheckCircle, null, modifier = Modifier.size(14.dp), tint = Color.White)
                        Spacer(Modifier.width(6.dp))
                        Text("Mark Available", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                    }
                }
            }
        }

        HorizontalDivider(color = DreamlandGold.copy(alpha = 0.12f))

        // Current stay
        val currentStay = state.selectedRoomCurrentStay
        if (currentStay != null) {
            DetailSection(title = "Current Stay") {
                StayInfoCard(stay = currentStay, fmt = fmt)
            }
        }

        // Upcoming bookings
        val upcoming = state.selectedRoomUpcomingBookings
        DetailSection(
            title = if (upcoming.isNotEmpty()) "Upcoming Bookings · ${upcoming.size}" else "Upcoming Bookings",
        ) {
            if (upcoming.isEmpty()) {
                Text("No upcoming bookings", style = MaterialTheme.typography.bodySmall, color = DreamlandMuted)
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    upcoming.forEach { booking -> BookingInfoCard(booking = booking, fmt = fmtShort) }
                }
            }
        }

        // Stay history
        val history = state.roomDetailStays.filter { it.status == "COMPLETED" }
        DetailSection(
            title = if (history.isNotEmpty()) "Stay History · ${history.size}" else "Stay History",
            trailing = if (state.roomDetailLoading) {
                { CircularProgressIndicator(modifier = Modifier.size(14.dp), color = DreamlandGold, strokeWidth = 1.5.dp) }
            } else null,
        ) {
            if (history.isEmpty() && !state.roomDetailLoading) {
                Text("No completed stays yet", style = MaterialTheme.typography.bodySmall, color = DreamlandMuted)
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    history.forEach { stay -> StayHistoryCard(stay = stay, fmt = fmtShort) }
                }
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Detail section wrapper
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun DetailSection(
    title: String,
    trailing: (@Composable () -> Unit)? = null,
    content: @Composable () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Box(
                Modifier
                    .width(3.dp)
                    .height(14.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(DreamlandGold),
            )
            Text(
                title,
                style = MaterialTheme.typography.titleSmall,
                color = DreamlandGold,
                fontWeight = FontWeight.SemiBold,
                letterSpacing = 0.5.sp,
            )
            trailing?.invoke()
        }
        content()
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Current stay card
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun StayInfoCard(stay: Stay, fmt: SimpleDateFormat) {
    val occupiedRed = Color(0xFFE74C3C)
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(DreamlandForestSurface)
            .border(1.dp, occupiedRed.copy(alpha = 0.4f), RoundedCornerShape(10.dp))
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Text(stay.guestName, style = MaterialTheme.typography.titleSmall, color = DreamlandOnDark, fontWeight = FontWeight.Bold)
            Box(
                Modifier
                    .clip(RoundedCornerShape(4.dp))
                    .background(occupiedRed.copy(alpha = 0.15f))
                    .padding(horizontal = 8.dp, vertical = 3.dp),
            ) { Text("ACTIVE", style = MaterialTheme.typography.labelSmall, color = occupiedRed, fontSize = 9.sp) }
        }
        if (stay.guestPhone.isNotBlank()) {
            Text(stay.guestPhone, style = MaterialTheme.typography.bodySmall, color = DreamlandMuted)
        }
        Text(
            "${fmt.format(stay.checkInActual)}  →  ${fmt.format(stay.expectedCheckOut)}",
            style = MaterialTheme.typography.bodySmall,
            color = DreamlandMuted,
        )
        val guests = buildString {
            append("${stay.adults} Adult${if (stay.adults != 1) "s" else ""}")
            if (stay.children > 0) append(" · ${stay.children} Child${if (stay.children != 1) "ren" else ""}")
        }
        Text(guests, style = MaterialTheme.typography.bodySmall, color = DreamlandMuted)
        if (stay.breakfast || stay.extraBed || stay.earlyCheckIn || stay.lateCheckOut) {
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                if (stay.breakfast) ExtraChip("Breakfast")
                if (stay.extraBed) ExtraChip("Extra Bed")
                if (stay.earlyCheckIn) ExtraChip("Early CI")
                if (stay.lateCheckOut) ExtraChip("Late CO")
            }
        }
        if (stay.specialRequests.isNotBlank()) {
            Text(
                "Requests: ${stay.specialRequests}",
                style = MaterialTheme.typography.bodySmall,
                color = DreamlandMuted,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun ExtraChip(text: String) {
    Box(
        Modifier
            .clip(RoundedCornerShape(4.dp))
            .background(DreamlandGold.copy(alpha = 0.12f))
            .padding(horizontal = 6.dp, vertical = 2.dp),
    ) { Text(text, style = MaterialTheme.typography.labelSmall, color = DreamlandGold, fontSize = 9.sp) }
}

// ─────────────────────────────────────────────────────────────────────────────
// Upcoming booking card (room detail)
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun BookingInfoCard(booking: Booking, fmt: SimpleDateFormat) {
    val statusColor = bookingStatusColor(booking.status)
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(DreamlandForestSurface)
            .border(1.dp, statusColor.copy(alpha = 0.3f), RoundedCornerShape(8.dp))
            .padding(12.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                booking.guestName.ifBlank { "Guest" },
                style = MaterialTheme.typography.bodyMedium,
                color = DreamlandOnDark,
                fontWeight = FontWeight.Medium,
            )
            Text(
                "${fmt.format(booking.checkIn)}  →  ${fmt.format(booking.checkOut)}",
                style = MaterialTheme.typography.bodySmall,
                color = DreamlandMuted,
            )
            val guests = buildString {
                append("${booking.adults} Adult${if (booking.adults != 1) "s" else ""}")
                if (booking.children > 0) append(" · ${booking.children} children")
            }
            Text(guests, style = MaterialTheme.typography.bodySmall, color = DreamlandMuted)
        }
        Column(horizontalAlignment = Alignment.End) {
            Box(
                Modifier
                    .clip(RoundedCornerShape(4.dp))
                    .background(statusColor.copy(alpha = 0.15f))
                    .padding(horizontal = 6.dp, vertical = 3.dp),
            ) { Text(booking.status, style = MaterialTheme.typography.labelSmall, color = statusColor, fontSize = 9.sp) }
            Spacer(Modifier.height(4.dp))
            Text(formatRupees(booking.totalAmount), style = MaterialTheme.typography.bodySmall, color = DreamlandOnDark, fontWeight = FontWeight.SemiBold)
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Stay history card (room detail)
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun StayHistoryCard(stay: Stay, fmt: SimpleDateFormat) {
    val green = Color(0xFF2ECC71)
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(DreamlandForestSurface)
            .border(1.dp, DreamlandGold.copy(alpha = 0.1f), RoundedCornerShape(8.dp))
            .padding(12.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(stay.guestName, style = MaterialTheme.typography.bodyMedium, color = DreamlandOnDark, fontWeight = FontWeight.Medium)
            val checkOut = stay.checkOutActual ?: stay.expectedCheckOut
            Text(
                "${fmt.format(stay.checkInActual)}  →  ${fmt.format(checkOut)}",
                style = MaterialTheme.typography.bodySmall,
                color = DreamlandMuted,
            )
            val guests = buildString {
                append("${stay.adults} Adult${if (stay.adults != 1) "s" else ""}")
                if (stay.children > 0) append(" · ${stay.children} children")
            }
            Text(guests, style = MaterialTheme.typography.bodySmall, color = DreamlandMuted)
        }
        Box(
            Modifier
                .clip(RoundedCornerShape(4.dp))
                .background(green.copy(alpha = 0.12f))
                .padding(horizontal = 6.dp, vertical = 3.dp),
        ) { Text("COMPLETED", style = MaterialTheme.typography.labelSmall, color = green, fontSize = 9.sp) }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Room status summary bar (left panel header)
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun RoomStatusSummary(counts: Map<String, Int>) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(DreamlandForestElevated)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        counts.forEach { (status, count) ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(7.dp).clip(CircleShape).background(roomStatusColor(status)))
                Spacer(Modifier.width(4.dp))
                Text(
                    "$count",
                    style = MaterialTheme.typography.labelMedium,
                    color = DreamlandMuted,
                    fontSize = 10.sp,
                )
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Room filter dropdown
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun RoomFilterDropdown(
    label: String,
    selected: String,
    options: List<Pair<String, String>>,
    onSelect: (String) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    val selectedLabel = options.find { it.first == selected }?.second ?: label

    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { expanded = it },
        modifier = Modifier.fillMaxWidth(),
    ) {
        OutlinedTextField(
            value = selectedLabel,
            onValueChange = {},
            readOnly = true,
            label = { Text(label, fontSize = 11.sp) },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            modifier = Modifier.menuAnchor().fillMaxWidth(),
            shape = RoundedCornerShape(8.dp),
            textStyle = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedTextColor = DreamlandOnDark,
                unfocusedTextColor = DreamlandOnDark,
                focusedBorderColor = DreamlandGold,
                unfocusedBorderColor = DreamlandMuted.copy(alpha = 0.4f),
                cursorColor = DreamlandGold,
                focusedLabelColor = DreamlandGold,
                unfocusedLabelColor = DreamlandMuted,
                focusedTrailingIconColor = DreamlandGold,
                unfocusedTrailingIconColor = DreamlandMuted,
            ),
        )
        ExposedDropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
            modifier = Modifier.background(DreamlandForestElevated),
        ) {
            options.forEach { (value, displayName) ->
                DropdownMenuItem(
                    text = {
                        Text(
                            displayName,
                            style = MaterialTheme.typography.bodySmall,
                            color = if (selected == value) DreamlandGold else DreamlandOnDark,
                        )
                    },
                    onClick = { onSelect(value); expanded = false },
                    contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp),
                )
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Bookings tab — filters + list
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun BookingsTabContent(
    state: RoomsAndBookingsUiState,
    vm: RoomsAndBookingsViewModel,
    onCheckIn: (Booking) -> Unit,
) {
    val bookings = state.filteredBookings

    Column {
        BookingFilterBar(state = state, vm = vm)

        when {
            state.bookingsLoading && bookings.isEmpty() -> {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = DreamlandGold, strokeWidth = 2.5.dp)
                }
            }
            bookings.isEmpty() -> {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("No bookings found", style = MaterialTheme.typography.bodyLarge, color = DreamlandMuted)
                        Spacer(Modifier.height(4.dp))
                        Text(
                            text = if (state.searchQuery.isNotBlank()) "Try a different search or filter"
                            else "No bookings for the selected filters",
                            style = MaterialTheme.typography.bodySmall,
                            color = DreamlandMuted.copy(alpha = 0.7f),
                        )
                    }
                }
            }
            else -> {
                LazyColumn(
                    contentPadding = PaddingValues(horizontal = 20.dp, vertical = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    items(bookings, key = { it.id }) { booking ->
                        BookingCard(
                            booking = booking,
                            isCheckInToday = state.isCheckInToday(booking),
                            onAssignRoom = { vm.openAssignRoom(booking) },
                            onCheckIn = { onCheckIn(booking) },
                            onCancel = { vm.promptCancelBooking(booking) },
                            onNoShow = { vm.promptMarkNoShow(booking) },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun BookingFilterBar(state: RoomsAndBookingsUiState, vm: RoomsAndBookingsViewModel) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(DreamlandForestElevated)
            .padding(horizontal = 20.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        BookingDateFilter.values().forEach { filter ->
            val label = when (filter) {
                BookingDateFilter.ALL -> "All"
                BookingDateFilter.TODAY -> "Today"
                BookingDateFilter.UPCOMING -> "Upcoming"
            }
            FilterChip(
                selected = state.bookingDateFilter == filter,
                onClick = { vm.setBookingDateFilter(filter) },
                label = { Text(label, fontSize = 12.sp, fontWeight = FontWeight.Medium) },
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = DreamlandGold.copy(alpha = 0.2f),
                    selectedLabelColor = DreamlandGold,
                    containerColor = Color.Transparent,
                    labelColor = DreamlandMuted,
                ),
                border = FilterChipDefaults.filterChipBorder(
                    enabled = true,
                    selected = state.bookingDateFilter == filter,
                    selectedBorderColor = DreamlandGold.copy(alpha = 0.5f),
                    borderColor = DreamlandMuted.copy(alpha = 0.3f),
                ),
                shape = RoundedCornerShape(8.dp),
            )
        }

        Spacer(Modifier.weight(1f))

        val statusOptions = listOf(null to "All", "CONFIRMED" to "Confirmed", "CANCELLED" to "Cancelled", "NO_SHOW" to "No Show", "COMPLETED" to "Completed")
        statusOptions.forEach { (status, label) ->
            FilterChip(
                selected = state.bookingStatusFilter == status,
                onClick = { vm.setBookingStatusFilter(status) },
                label = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (status != null) {
                            Box(
                                modifier = Modifier
                                    .size(6.dp)
                                    .clip(CircleShape)
                                    .background(bookingStatusColor(status)),
                            )
                            Spacer(Modifier.width(5.dp))
                        }
                        Text(label, fontSize = 12.sp, fontWeight = FontWeight.Medium)
                    }
                },
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = (if (status != null) bookingStatusColor(status) else DreamlandGold).copy(alpha = 0.15f),
                    selectedLabelColor = if (status != null) bookingStatusColor(status) else DreamlandGold,
                    containerColor = Color.Transparent,
                    labelColor = DreamlandMuted,
                ),
                border = FilterChipDefaults.filterChipBorder(
                    enabled = true,
                    selected = state.bookingStatusFilter == status,
                    selectedBorderColor = (if (status != null) bookingStatusColor(status) else DreamlandGold).copy(alpha = 0.45f),
                    borderColor = DreamlandMuted.copy(alpha = 0.3f),
                ),
                shape = RoundedCornerShape(8.dp),
            )
        }

        Spacer(Modifier.width(8.dp))

        val isNewest = state.bookingSortOrder == "NEWEST"
        IconButton(
            onClick = { vm.setBookingSortOrder(if (isNewest) "OLDEST" else "NEWEST") },
            modifier = Modifier
                .size(32.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(DreamlandGold.copy(alpha = if (isNewest) 0.15f else 0.05f)),
        ) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.Sort,
                contentDescription = if (isNewest) "Newest first" else "Oldest first",
                tint = DreamlandGold,
                modifier = Modifier.size(16.dp),
            )
        }
    }
}

@Composable
private fun BookingCard(
    booking: Booking,
    isCheckInToday: Boolean,
    onAssignRoom: () -> Unit,
    onCheckIn: () -> Unit,
    onCancel: () -> Unit,
    onNoShow: () -> Unit = {},
) {
    val fmt = SimpleDateFormat("d MMM", Locale.getDefault())
    val statusColor = bookingStatusColor(booking.status)
    val isConfirmed = booking.status == "CONFIRMED"
    val hasRoom = booking.roomNumber.isNotBlank()
    val now = Date()
    val isOverdue = isConfirmed && booking.checkIn.before(now)
    val hoursOverdue = if (isOverdue) ((now.time - booking.checkIn.time) / (1000L * 3600)).toInt() else 0

    Card(
        colors = CardDefaults.cardColors(containerColor = DreamlandForestSurface),
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier
            .fillMaxWidth()
            .border(
                width = if (isCheckInToday && isConfirmed) 1.5.dp else 1.dp,
                color = when {
                    isCheckInToday && isConfirmed -> DreamlandGold.copy(alpha = 0.6f)
                    else -> DreamlandGold.copy(alpha = 0.12f)
                },
                shape = RoundedCornerShape(12.dp),
            ),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top,
            ) {
                Column(Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = booking.guestName.ifBlank { "Guest" },
                            style = MaterialTheme.typography.titleMedium,
                            color = DreamlandOnDark,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        if (isCheckInToday && isConfirmed) {
                            Spacer(Modifier.width(8.dp))
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(4.dp))
                                    .background(DreamlandGold.copy(alpha = 0.2f))
                                    .padding(horizontal = 6.dp, vertical = 2.dp),
                            ) {
                                Text(
                                    "CHECK-IN TODAY",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = DreamlandGold,
                                    fontSize = 9.sp,
                                    letterSpacing = 0.8.sp,
                                )
                            }
                        }
                    }
                    Spacer(Modifier.height(3.dp))
                    Text(
                        text = buildString {
                            if (booking.roomCategoryName.isNotBlank()) append("${booking.roomCategoryName}  ·  ")
                            append("${fmt.format(booking.checkIn)} → ${fmt.format(booking.checkOut)}")
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = DreamlandMuted,
                    )
                }
                Spacer(Modifier.width(12.dp))
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .background(statusColor.copy(alpha = 0.15f))
                        .border(1.dp, statusColor.copy(alpha = 0.4f), RoundedCornerShape(6.dp))
                        .padding(horizontal = 10.dp, vertical = 4.dp),
                ) {
                    Text(
                        booking.status,
                        style = MaterialTheme.typography.labelMedium,
                        color = statusColor,
                        fontSize = 10.sp,
                        letterSpacing = 0.8.sp,
                    )
                }
            }

            Spacer(Modifier.height(10.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = buildString {
                        append("${booking.adults} Adult${if (booking.adults != 1) "s" else ""}")
                        if (booking.children > 0) append(" · ${booking.children} Child${if (booking.children != 1) "ren" else ""}")
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = DreamlandMuted,
                )
                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        formatRupees(booking.totalAmount),
                        style = MaterialTheme.typography.bodyMedium,
                        color = DreamlandOnDark,
                        fontWeight = FontWeight.SemiBold,
                    )
                    if (booking.advancePaidAmount > 0) {
                        Text(
                            "${formatRupees(booking.advancePaidAmount)} paid",
                            style = MaterialTheme.typography.bodySmall,
                            color = Color(0xFF2ECC71),
                        )
                    }
                }
            }

            if (hasRoom) {
                Spacer(Modifier.height(6.dp))
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(4.dp))
                        .background(DreamlandGold.copy(alpha = 0.1f))
                        .padding(horizontal = 8.dp, vertical = 3.dp),
                ) {
                    Text(
                        "Room ${booking.roomNumber}",
                        style = MaterialTheme.typography.labelMedium,
                        color = DreamlandGold,
                        fontSize = 11.sp,
                    )
                }
            }

            if (isOverdue) {
                Spacer(Modifier.height(6.dp))
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Box(
                        Modifier
                            .clip(RoundedCornerShape(4.dp))
                            .background(Color(0xFFF39C12).copy(alpha = 0.15f))
                            .padding(horizontal = 8.dp, vertical = 3.dp),
                    ) {
                        Text(
                            "Check-in ${hoursOverdue}h overdue",
                            color = Color(0xFFF39C12),
                            fontSize = 10.sp,
                            style = MaterialTheme.typography.labelSmall,
                        )
                    }
                }
            }

            if (isConfirmed) {
                Spacer(Modifier.height(12.dp))
                HorizontalDivider(color = DreamlandGold.copy(alpha = 0.1f))
                Spacer(Modifier.height(10.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    OutlinedButton(
                        onClick = onAssignRoom,
                        shape = RoundedCornerShape(8.dp),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = DreamlandGold),
                        border = androidx.compose.foundation.BorderStroke(1.dp, DreamlandGold.copy(alpha = 0.45f)),
                        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp),
                    ) {
                        Text(
                            if (hasRoom) "Reassign Room" else "Assign Room",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium,
                        )
                    }
                    Button(
                        onClick = onCheckIn,
                        colors = ButtonDefaults.buttonColors(containerColor = DreamlandGold),
                        shape = RoundedCornerShape(8.dp),
                        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp),
                    ) {
                        Text("Check-in", color = Color(0xFF0D1F17), fontWeight = FontWeight.Bold, fontSize = 12.sp)
                    }
                    Spacer(Modifier.weight(1f))
                    if (isOverdue) {
                        TextButton(
                            onClick = onNoShow,
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 8.dp),
                        ) {
                            Text("No-Show", color = Color(0xFFE74C3C), fontSize = 12.sp)
                        }
                    }
                    TextButton(
                        onClick = onCancel,
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 8.dp),
                    ) {
                        Text("Cancel", color = Color(0xFFE74C3C).copy(alpha = 0.8f), fontSize = 12.sp)
                    }
                }
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Cancel confirmation dialog
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun CancelConfirmDialog(
    guestName: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = DreamlandForestSurface,
        titleContentColor = DreamlandOnDark,
        textContentColor = DreamlandMuted,
        title = { Text("Cancel Booking?") },
        text = { Text("This will mark the booking for $guestName as Cancelled. This action cannot be undone.") },
        confirmButton = {
            Button(
                onClick = onConfirm,
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFE74C3C)),
                shape = RoundedCornerShape(8.dp),
            ) { Text("Yes, Cancel", fontWeight = FontWeight.SemiBold) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Keep", color = DreamlandMuted) }
        },
        shape = RoundedCornerShape(16.dp),
    )
}

// ─────────────────────────────────────────────────────────────────────────────
// No-show confirmation dialog
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun NoShowConfirmDialog(
    booking: Booking,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = DreamlandForestSurface,
        titleContentColor = DreamlandOnDark,
        textContentColor = DreamlandMuted,
        title = { Text("Mark as No-Show?") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("This will permanently mark ${booking.guestName}'s booking as No-Show. The room slot will be released.")
                Text("This action is final and cannot be undone. The guest must make a fresh booking if they arrive later.", fontSize = 12.sp)
                if (booking.advancePaidAmount > 0) {
                    Spacer(Modifier.height(2.dp))
                    Box(
                        Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .background(Color(0xFFF39C12).copy(alpha = 0.15f))
                            .padding(8.dp),
                    ) {
                        Text(
                            "⚠️  Advance payment of ${formatRupees(booking.advancePaidAmount)} on record. Handle refund per hotel policy.",
                            color = Color(0xFFF39C12),
                            fontSize = 11.sp,
                        )
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = onConfirm,
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFE74C3C)),
                shape = RoundedCornerShape(8.dp),
            ) { Text("Mark No-Show", fontWeight = FontWeight.SemiBold) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Keep Booking", color = DreamlandMuted) }
        },
        shape = RoundedCornerShape(16.dp),
    )
}
