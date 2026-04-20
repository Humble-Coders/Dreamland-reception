@file:OptIn(ExperimentalMaterial3Api::class)

package com.example.dreamland_reception

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
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
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
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
import com.example.dreamland_reception.roomsbookings.AssignRoomDialog
import com.example.dreamland_reception.ui.viewmodel.BookingDateFilter
import com.example.dreamland_reception.ui.viewmodel.RoomsAndBookingsUiState
import com.example.dreamland_reception.ui.viewmodel.RoomsAndBookingsViewModel
import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.Locale

// ── Status colours ─────────────────────────────────────────────────────────────

private fun roomStatusColor(status: String): Color = when (status) {
    "AVAILABLE" -> Color(0xFF2ECC71)
    "ASSIGNED" -> Color(0xFF3498DB)
    "OCCUPIED" -> Color(0xFFE74C3C)
    "CLEANING" -> Color(0xFFE67E22)
    "MAINTENANCE" -> Color(0xFF95A5A6)
    else -> Color(0xFF8FA69E)
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

            // ── Page header ──────────────────────────────────────────────────
            PageHeader(state = state, vm = vm)

            // ── Tab row ──────────────────────────────────────────────────────
            RoomsBookingsTabRow(
                selectedTab = state.selectedTab,
                roomCount = state.rooms.size,
                bookingCount = state.filteredBookings.size,
                onTabSelected = vm::setTab,
            )

            // ── Content ──────────────────────────────────────────────────────
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

    // ── Dialogs ──────────────────────────────────────────────────────────────
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
}

// ─────────────────────────────────────────────────────────────────────────────
// Page header: title + search + action button
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun PageHeader(state: RoomsAndBookingsUiState, vm: RoomsAndBookingsViewModel) {
    val searchPlaceholder = if (state.selectedTab == 0) "Search by room number…" else "Search by guest name…"

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(DreamlandForestSurface)
            .padding(horizontal = 24.dp, vertical = 20.dp),
    ) {
        // Title row
        Row(
            modifier = Modifier.fillMaxWidth(),
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

            // Add dummy booking button
            Button(
                onClick = vm::addDummyBooking,
                colors = ButtonDefaults.buttonColors(containerColor = DreamlandGoldDeep),
                shape = RoundedCornerShape(10.dp),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 10.dp),
            ) {
                Icon(Icons.Filled.Add, contentDescription = null, modifier = Modifier.size(16.dp), tint = DreamlandOnDark)
                Spacer(Modifier.width(6.dp))
                Text("Add Dummy Booking", color = DreamlandOnDark, fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
            }
        }

        Spacer(Modifier.height(16.dp))

        // Search bar
        OutlinedTextField(
            value = state.searchQuery,
            onValueChange = vm::setSearchQuery,
            placeholder = { Text(searchPlaceholder, style = MaterialTheme.typography.bodyMedium, color = DreamlandMuted) },
            leadingIcon = {
                Icon(Icons.Filled.Search, contentDescription = null, tint = DreamlandMuted, modifier = Modifier.size(20.dp))
            },
            trailingIcon = {
                if (state.searchQuery.isNotEmpty()) {
                    IconButton(onClick = { vm.setSearchQuery("") }) {
                        Icon(Icons.Filled.Close, contentDescription = "Clear", tint = DreamlandMuted, modifier = Modifier.size(18.dp))
                    }
                }
            },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
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
// Rooms tab — grid of room cards
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun RoomsTabContent(state: RoomsAndBookingsUiState, vm: RoomsAndBookingsViewModel) {
    val rooms = state.filteredRooms

    if (rooms.isEmpty() && !state.roomsLoading) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text("No rooms found", style = MaterialTheme.typography.bodyLarge, color = DreamlandMuted)
                if (state.searchQuery.isNotEmpty()) {
                    Spacer(Modifier.height(4.dp))
                    Text("Try a different search", style = MaterialTheme.typography.bodySmall, color = DreamlandMuted.copy(alpha = 0.7f))
                }
            }
        }
        return
    }

    // Group by categoryId, then resolve display name from the loaded category map.
    // Sort categories by resolved name alphabetically; unknown ids go to end.
    val grouped = rooms.groupBy { it.categoryId }
    val sortedCategoryIds = grouped.keys.sortedBy { id ->
        state.categoryNames[id] ?: "\uFFFF$id" // unknown ids sort last
    }

    LazyVerticalGrid(
        columns = GridCells.Adaptive(minSize = 210.dp),
        contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 0.dp, bottom = 20.dp),
        horizontalArrangement = Arrangement.spacedBy(14.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        // Status summary bar — full width
        item(span = { GridItemSpan(maxLineSpan) }) {
            RoomStatusSummary(rooms = state.rooms)
        }

        sortedCategoryIds.forEach { categoryId ->
            val categoryRooms = grouped[categoryId] ?: return@forEach
            val displayName = state.categoryNames[categoryId]
                ?: categoryRooms.first().categoryName.ifBlank { "Uncategorized" }

            // Category header — full width
            item(span = { GridItemSpan(maxLineSpan) }) {
                RoomCategoryHeader(
                    name = displayName,
                    total = categoryRooms.size,
                    available = categoryRooms.count { it.status == "AVAILABLE" },
                    assigned = categoryRooms.count { it.status == "ASSIGNED" },
                    occupied = categoryRooms.count { it.status == "OCCUPIED" },
                )
            }

            // Room cards for this category
            items(categoryRooms, key = { it.id }) { room ->
                val displayName = when (room.status) {
                    "OCCUPIED" -> state.activeStaysByRoom[room.roomNumber]?.guestName
                    "ASSIGNED" -> state.bookings.find { it.roomInstanceId == room.id }?.guestName
                    else -> null
                }
                RoomCard(room = room, guestName = displayName, onMarkClean = { vm.markCleaningComplete(room) })
            }
        }
    }
}

@Composable
private fun RoomCategoryHeader(name: String, total: Int, available: Int, assigned: Int, occupied: Int) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 20.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .width(3.dp)
                    .height(18.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(DreamlandGold),
            )
            Spacer(Modifier.width(10.dp))
            Text(
                text = name,
                style = MaterialTheme.typography.titleMedium,
                color = DreamlandOnDark,
                fontWeight = FontWeight.SemiBold,
            )
            Spacer(Modifier.width(10.dp))
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(6.dp))
                    .background(DreamlandGold.copy(alpha = 0.12f))
                    .padding(horizontal = 8.dp, vertical = 3.dp),
            ) {
                Text(
                    text = "$total rooms",
                    style = MaterialTheme.typography.labelMedium,
                    color = DreamlandGold,
                    fontSize = 10.sp,
                )
            }
        }
        Row(
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(modifier = Modifier.size(6.dp).clip(CircleShape).background(Color(0xFF2ECC71)))
                Spacer(Modifier.width(4.dp))
                Text("$available avail.", style = MaterialTheme.typography.labelMedium, color = DreamlandMuted, fontSize = 10.sp)
            }
            if (assigned > 0) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(modifier = Modifier.size(6.dp).clip(CircleShape).background(Color(0xFF3498DB)))
                    Spacer(Modifier.width(4.dp))
                    Text("$assigned assigned", style = MaterialTheme.typography.labelMedium, color = DreamlandMuted, fontSize = 10.sp)
                }
            }
            if (occupied > 0) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(modifier = Modifier.size(6.dp).clip(CircleShape).background(Color(0xFFE74C3C)))
                    Spacer(Modifier.width(4.dp))
                    Text("$occupied occ.", style = MaterialTheme.typography.labelMedium, color = DreamlandMuted, fontSize = 10.sp)
                }
            }
        }
    }
    HorizontalDivider(color = DreamlandGold.copy(alpha = 0.12f), modifier = Modifier.padding(bottom = 4.dp))
}

@Composable
private fun RoomStatusSummary(rooms: List<RoomInstance>) {
    val counts = mapOf(
        "AVAILABLE" to rooms.count { it.status == "AVAILABLE" },
        "ASSIGNED" to rooms.count { it.status == "ASSIGNED" },
        "OCCUPIED" to rooms.count { it.status == "OCCUPIED" },
        "CLEANING" to rooms.count { it.status == "CLEANING" },
        "MAINTENANCE" to rooms.count { it.status == "MAINTENANCE" },
    )
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(DreamlandForestElevated)
            .padding(horizontal = 20.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(24.dp),
    ) {
        counts.forEach { (status, count) ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .clip(CircleShape)
                        .background(roomStatusColor(status)),
                )
                Spacer(Modifier.width(6.dp))
                Text(
                    text = "$count $status",
                    style = MaterialTheme.typography.labelMedium,
                    color = DreamlandMuted,
                    fontSize = 11.sp,
                    letterSpacing = 0.5.sp,
                )
            }
        }
    }
}

@Composable
private fun RoomCard(
    room: RoomInstance,
    guestName: String?,
    onMarkClean: () -> Unit,
) {
    val statusColor = roomStatusColor(room.status)
    val isOccupied = room.status == "OCCUPIED"
    val isAssigned = room.status == "ASSIGNED"
    val isCleaning = room.status == "CLEANING"

    Card(
        colors = CardDefaults.cardColors(containerColor = DreamlandForestSurface),
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier
            .fillMaxWidth()
            .border(
                width = if (isOccupied || isAssigned) 1.5.dp else 1.dp,
                color = if (isOccupied || isAssigned) statusColor.copy(alpha = 0.5f) else DreamlandGold.copy(alpha = 0.15f),
                shape = RoundedCornerShape(12.dp),
            ),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            // Room number + category
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top,
            ) {
                Column {
                    Text(
                        text = "Room ${room.roomNumber}",
                        style = MaterialTheme.typography.titleMedium,
                        color = DreamlandOnDark,
                        fontWeight = FontWeight.Bold,
                    )
//                    Text(
//                        text = room.categoryName.ifBlank { "—" },
//                        style = MaterialTheme.typography.bodySmall,
//                        color = DreamlandMuted,
//                    )
                }
                // Status dot
                Box(
                    modifier = Modifier
                        .size(10.dp)
                        .clip(CircleShape)
                        .background(statusColor),
                )
            }

            Spacer(Modifier.height(12.dp))

            // Status badge
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(6.dp))
                    .background(statusColor.copy(alpha = 0.15f))
                    .border(1.dp, statusColor.copy(alpha = 0.35f), RoundedCornerShape(6.dp))
                    .padding(horizontal = 10.dp, vertical = 4.dp),
            ) {
                Text(
                    text = room.status,
                    style = MaterialTheme.typography.labelMedium,
                    color = statusColor,
                    fontSize = 10.sp,
                    letterSpacing = 1.sp,
                )
            }

            // Guest name if occupied or assigned
            if ((isOccupied || isAssigned) && !guestName.isNullOrBlank()) {
                Spacer(Modifier.height(8.dp))
                Text(
                    text = guestName,
                    style = MaterialTheme.typography.bodyMedium,
                    color = DreamlandOnDark,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }

            // Mark clean button
            if (isCleaning) {
                Spacer(Modifier.height(10.dp))
                OutlinedButton(
                    onClick = onMarkClean,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(8.dp),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFF2ECC71)),
                    border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF2ECC71).copy(alpha = 0.5f)),
                    contentPadding = PaddingValues(vertical = 6.dp),
                ) {
                    Icon(Icons.Filled.CheckCircle, contentDescription = null, modifier = Modifier.size(14.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Mark Available", fontSize = 12.sp, fontWeight = FontWeight.Medium)
                }
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
        // Filter bar
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
                            else "Use 'Add Dummy Booking' to create a test entry",
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
        // Date filters
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

        // Status filters
        val statusOptions = listOf(null to "All", "CONFIRMED" to "Confirmed", "CANCELLED" to "Cancelled", "NO_SHOW" to "No Show")
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
    }
}

@Composable
private fun BookingCard(
    booking: Booking,
    isCheckInToday: Boolean,
    onAssignRoom: () -> Unit,
    onCheckIn: () -> Unit,
    onCancel: () -> Unit,
) {
    val fmt = SimpleDateFormat("d MMM", Locale.getDefault())
    val statusColor = bookingStatusColor(booking.status)
    val isCancelled = booking.status == "CANCELLED"
    val isConfirmed = booking.status == "CONFIRMED"
    val hasRoom = booking.roomNumber.isNotBlank()

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
            // Top row: guest name + status badge
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
                                    text = "CHECK-IN TODAY",
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

                // Status pill
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .background(statusColor.copy(alpha = 0.15f))
                        .border(1.dp, statusColor.copy(alpha = 0.4f), RoundedCornerShape(6.dp))
                        .padding(horizontal = 10.dp, vertical = 4.dp),
                ) {
                    Text(
                        text = booking.status,
                        style = MaterialTheme.typography.labelMedium,
                        color = statusColor,
                        fontSize = 10.sp,
                        letterSpacing = 0.8.sp,
                    )
                }
            }

            Spacer(Modifier.height(10.dp))

            // Middle row: guests + amounts
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                // Guest count
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = buildString {
                            append("${booking.adults} Adult${if (booking.adults != 1) "s" else ""}")
                            if (booking.children > 0) append(" · ${booking.children} Child${if (booking.children != 1) "ren" else ""}")
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = DreamlandMuted,
                    )
                }

                // Amount info
                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        text = formatRupees(booking.totalAmount),
                        style = MaterialTheme.typography.bodyMedium,
                        color = DreamlandOnDark,
                        fontWeight = FontWeight.SemiBold,
                    )
                    if (booking.advancePaidAmount > 0) {
                        Text(
                            text = "${formatRupees(booking.advancePaidAmount)} paid",
                            style = MaterialTheme.typography.bodySmall,
                            color = Color(0xFF2ECC71),
                        )
                    }
                }
            }

            // Room number if assigned
            if (hasRoom) {
                Spacer(Modifier.height(6.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(4.dp))
                            .background(DreamlandGold.copy(alpha = 0.1f))
                            .padding(horizontal = 8.dp, vertical = 3.dp),
                    ) {
                        Text(
                            text = "Room ${booking.roomNumber}",
                            style = MaterialTheme.typography.labelMedium,
                            color = DreamlandGold,
                            fontSize = 11.sp,
                        )
                    }
                }
            }

            // Actions — only for actionable statuses
            if (isConfirmed) {
                Spacer(Modifier.height(12.dp))
                HorizontalDivider(color = DreamlandGold.copy(alpha = 0.1f))
                Spacer(Modifier.height(10.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    // Assign room
                    OutlinedButton(
                        onClick = onAssignRoom,
                        shape = RoundedCornerShape(8.dp),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = DreamlandGold),
                        border = androidx.compose.foundation.BorderStroke(1.dp, DreamlandGold.copy(alpha = 0.45f)),
                        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp),
                    ) {
                        Text(
                            text = if (hasRoom) "Reassign Room" else "Assign Room",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium,
                        )
                    }

                    // Check-in
                    Button(
                        onClick = onCheckIn,
                        colors = ButtonDefaults.buttonColors(containerColor = DreamlandGold),
                        shape = RoundedCornerShape(8.dp),
                        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp),
                    ) {
                        Text("Check-in", color = Color(0xFF0D1F17), fontWeight = FontWeight.Bold, fontSize = 12.sp)
                    }

                    Spacer(Modifier.weight(1f))

                    // Cancel
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
        text = {
            Text(
                "This will mark the booking for $guestName as Cancelled. This action cannot be undone.",
            )
        },
        confirmButton = {
            Button(
                onClick = onConfirm,
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFE74C3C)),
                shape = RoundedCornerShape(8.dp),
            ) {
                Text("Yes, Cancel", fontWeight = FontWeight.SemiBold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Keep", color = DreamlandMuted)
            }
        },
        shape = RoundedCornerShape(16.dp),
    )
}
