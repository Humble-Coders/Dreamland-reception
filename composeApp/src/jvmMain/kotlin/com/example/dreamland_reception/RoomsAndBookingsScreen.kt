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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.items as lazyItems
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
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
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
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
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.foundation.BorderStroke
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.TabRowDefaults.SecondaryIndicator
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.KeyboardType
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
import com.example.dreamland_reception.stays.WalkInDialog
import com.example.dreamland_reception.data.repository.ManualRefundMethod
import com.example.dreamland_reception.data.repository.ReceptionRefundMode
import com.example.dreamland_reception.ui.viewmodel.BookingDateFilter
import com.example.dreamland_reception.ui.viewmodel.CancelDialogState
import com.example.dreamland_reception.ui.viewmodel.NoShowRefundDialogState
import com.example.dreamland_reception.ui.viewmodel.RoomsAndBookingsUiState
import com.example.dreamland_reception.ui.viewmodel.RoomsAndBookingsViewModel
import com.example.dreamland_reception.ui.viewmodel.StaysViewModel
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
    "CONFIRMED"       -> Color(0xFFF39C12)
    "PENDING_PAYMENT" -> Color(0xFFE67E22)
    "COMPLETED"       -> Color(0xFF2ECC71)
    "CANCELLED"       -> Color(0xFFE74C3C)
    "NO_SHOW"         -> Color(0xFF95A5A6)
    else              -> Color(0xFF8FA69E)
}

private fun formatRupees(amount: Double): String {
    val nf = NumberFormat.getNumberInstance(Locale("en", "IN")).apply {
        minimumFractionDigits = 2
        maximumFractionDigits = 2
    }
    return "₹${nf.format(amount)}"
}

// ─────────────────────────────────────────────────────────────────────────────
// Root screen
// ─────────────────────────────────────────────────────────────────────────────

@Composable
fun RoomsAndBookingsScreen(
    vm: RoomsAndBookingsViewModel = DreamlandAppInitializer.getRoomsAndBookingsViewModel(),
    staysVm: StaysViewModel = DreamlandAppInitializer.getStaysViewModel(),
    onCheckIn: (Booking) -> Unit = {},
    onCheckInAll: (List<Booking>) -> Unit = {},
) {
    val state by vm.uiState.collectAsStateWithLifecycle()
    val walkInState by staysVm.walkInState.collectAsStateWithLifecycle()
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
            /* ROOMS_TAB_HIDDEN: PageHeader and RoomsBookingsTabRow removed
            PageHeader(state = state, vm = vm, staysVm = staysVm)
            RoomsBookingsTabRow(selectedTab = state.selectedTab, roomCount = state.rooms.size,
                bookingCount = state.filteredBookings.size, onTabSelected = vm::setTab)
            */

            Box(Modifier.weight(1f).fillMaxWidth()) {
                if (state.isInitialLoading) {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            CircularProgressIndicator(color = DreamlandGold, strokeWidth = 2.5.dp)
                            Spacer(Modifier.height(16.dp))
                            Text("Loading…", style = MaterialTheme.typography.bodySmall, color = DreamlandMuted)
                        }
                    }
                } else {
                    BookingsTabContent(state = state, vm = vm, staysVm = staysVm, onCheckIn = onCheckIn, onCheckInAll = onCheckInAll)
                }
                /* ROOMS_TAB_HIDDEN: RoomsTabContent removed
                if (state.selectedTab == 0) RoomsTabContent(state = state, vm = vm)
                */
            }
        }

        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 16.dp),
        ) { data ->
            Snackbar(
                snackbarData = data,
                containerColor = Color(0xFF1A0A0A),
                contentColor = Color(0xFFEF9A9A),
                shape = androidx.compose.foundation.shape.RoundedCornerShape(10.dp),
            )
        }
    }

    if (state.assignRoomDialogBooking != null) {
        AssignRoomDialog(state = state, vm = vm)
    }
    state.cancelDialog?.let { dialog ->
        CancelConfirmDialog(
            dialog = dialog,
            onReasonChanged = vm::onCancelReasonChanged,
            onRefundModeChanged = vm::onCancelRefundModeChanged,
            onRefundAmountChanged = vm::onCancelRefundAmountChanged,
            onPaidViaChanged = vm::onPaidViaChanged,
            onConfirm = vm::confirmCancelByReception,
            onDismiss = vm::dismissCancelDialog,
        )
    }
    if (state.noShowConfirmBooking != null) {
        NoShowConfirmDialog(
            booking = state.noShowConfirmBooking!!,
            hotelCheckInTime = state.hotelCheckInTime,
            onConfirm = vm::confirmNoShow,
            onDismiss = vm::dismissNoShow,
        )
    }
    state.groupNoShowBookings?.let { group ->
        val selectedIds = state.groupNoShowSelectedIds
        val totalAdvance = group.filter { it.id in selectedIds }.sumOf { it.advancePaidAmount }
        val guestName = group.firstOrNull()?.guestName ?: "Guest"
        Dialog(
            onDismissRequest = vm::dismissGroupNoShow,
            properties = DialogProperties(usePlatformDefaultWidth = false),
        ) {
            Box(
                modifier = Modifier
                    .widthIn(min = 380.dp, max = 500.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(DreamlandForestSurface)
                    .padding(24.dp),
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(0.dp)) {
                    Text("Mark as No-Show?", style = MaterialTheme.typography.titleLarge, color = DreamlandOnDark, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(4.dp))
                    Text("Select which rooms to mark as no-show for $guestName.", style = MaterialTheme.typography.bodySmall, color = DreamlandMuted)
                    Spacer(Modifier.height(16.dp))
                    HorizontalDivider(color = DreamlandGold.copy(alpha = 0.15f))
                    Spacer(Modifier.height(8.dp))
                    // Room list with checkboxes
                    LazyColumn(modifier = Modifier.heightIn(max = 240.dp)) {
                        lazyItems(group) { booking ->
                            val isChecked = booking.id in selectedIds
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { vm.toggleGroupNoShowBooking(booking.id) }
                                    .padding(vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                Checkbox(
                                    checked = isChecked,
                                    onCheckedChange = { vm.toggleGroupNoShowBooking(booking.id) },
                                    colors = CheckboxDefaults.colors(
                                        checkedColor = Color(0xFFE74C3C),
                                        uncheckedColor = DreamlandMuted,
                                    ),
                                )
                                if (booking.roomNumber.isNotBlank()) {
                                    Box(
                                        Modifier
                                            .clip(RoundedCornerShape(4.dp))
                                            .background(DreamlandGold.copy(alpha = 0.1f))
                                            .padding(horizontal = 7.dp, vertical = 2.dp),
                                    ) {
                                        Text("Room ${booking.roomNumber}", style = MaterialTheme.typography.labelSmall, color = DreamlandGold, fontSize = 11.sp)
                                    }
                                }
                                Text(
                                    booking.roomCategoryName.ifBlank { "No category" },
                                    style = MaterialTheme.typography.bodySmall,
                                    color = if (isChecked) DreamlandOnDark else DreamlandMuted,
                                )
                            }
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                    Text("This action is final and cannot be undone.", fontSize = 12.sp, color = Color(0xFFEF5350))
                    if (totalAdvance > 0) {
                        Spacer(Modifier.height(8.dp))
                        Box(
                            Modifier
                                .clip(RoundedCornerShape(6.dp))
                                .background(Color(0xFFF39C12).copy(alpha = 0.12f))
                                .padding(horizontal = 10.dp, vertical = 8.dp),
                        ) {
                            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                Text("Advance payment on record", style = MaterialTheme.typography.labelSmall, color = Color(0xFFF39C12), fontWeight = FontWeight.SemiBold)
                                Text(
                                    "${formatRupees(totalAdvance)} — you will be prompted to record the refund outcome after confirming.",
                                    color = Color(0xFFF39C12), fontSize = 11.sp,
                                )
                            }
                        }
                    }
                    Spacer(Modifier.height(20.dp))
                    HorizontalDivider(color = DreamlandGold.copy(alpha = 0.15f))
                    Spacer(Modifier.height(12.dp))
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End, verticalAlignment = Alignment.CenterVertically) {
                        TextButton(onClick = vm::dismissGroupNoShow) { Text("Keep Booking", color = DreamlandMuted) }
                        Spacer(Modifier.width(8.dp))
                        Button(
                            onClick = vm::confirmGroupNoShow,
                            enabled = selectedIds.isNotEmpty(),
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFE74C3C)),
                            shape = RoundedCornerShape(8.dp),
                        ) { Text("Mark No-Show", fontWeight = FontWeight.SemiBold) }
                    }
                }
            }
        }
    }
    if (state.noShowRefundDialog != null) {
        NoShowRefundOutcomeDialog(
            state = state.noShowRefundDialog!!,
            onStatusSelected = vm::onNoShowRefundStatus,
            onNoteChanged = vm::onNoShowRefundNote,
            onConfirm = vm::submitNoShowRefundOutcome,
            onDismiss = vm::dismissNoShowRefundDialog,
        )
    }
    if (walkInState.isOpen && walkInState.isBookingMode) {
        WalkInDialog(state = walkInState, vm = staysVm)
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Page header — title + compact search bar
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun PageHeader(state: RoomsAndBookingsUiState, vm: RoomsAndBookingsViewModel, staysVm: StaysViewModel) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(DreamlandForestSurface)
            .padding(horizontal = 24.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column {
            Text(
                text = "ACCOMMODATION",
                style = MaterialTheme.typography.labelSmall,
                color = DreamlandGold,
                letterSpacing = 2.sp,
            )
            Text(
                text = "Rooms & Bookings",
                style = MaterialTheme.typography.titleLarge,
                color = DreamlandOnDark,
                fontWeight = FontWeight.SemiBold,
            )
        }
        if (state.selectedTab == 1) {
            OutlinedButton(
                onClick = staysVm::openWalkInAsBooking,
                border = BorderStroke(1.dp, DreamlandGold),
                shape = RoundedCornerShape(8.dp),
            ) {
                Text("+ Add Booking", color = DreamlandGold, style = MaterialTheme.typography.labelLarge)
            }
        }
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
                    modifier = Modifier.padding(vertical = 10.dp, horizontal = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Text(
                        text = label,
                        fontWeight = if (selectedTab == index) FontWeight.SemiBold else FontWeight.Normal,
                        fontSize = 13.sp,
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
    // Hoisted so both panels can use it
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

    Row(Modifier.fillMaxSize()) {

        // ── Left panel (30%) ──────────────────────────────────────────────────
        Column(
            Modifier
                .fillMaxHeight()
                .weight(0.3f)
                .background(DreamlandForestSurface),
        ) {

            val allRooms = state.roomsForPanel

            Column(
                Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                OutlinedTextField(
                    value = state.searchQuery,
                    onValueChange = vm::setSearchQuery,
                    placeholder = { Text("Search rooms…", style = MaterialTheme.typography.bodySmall, color = DreamlandMuted, fontSize = 11.sp) },
                    leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null, tint = DreamlandMuted, modifier = Modifier.size(15.dp)) },
                    trailingIcon = {
                        if (state.searchQuery.isNotEmpty()) {
                            IconButton(onClick = { vm.setSearchQuery("") }, modifier = Modifier.size(28.dp)) {
                                Icon(Icons.Filled.Close, contentDescription = "Clear", tint = DreamlandMuted, modifier = Modifier.size(13.dp))
                            }
                        }
                    },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(8.dp),
                    textStyle = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp),
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
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    RoomFilterDropdown(
                        label = "Category",
                        selected = state.roomCategoryFilter,
                        options = listOf("" to "All Categories") +
                            state.categoryNames.entries.sortedBy { it.value }.map { it.key to it.value },
                        onSelect = vm::setRoomCategoryFilter,
                        modifier = Modifier.weight(1f),
                    )
                    RoomFilterDropdown(
                        label = "Status",
                        selected = state.roomStatusFilter,
                        options = listOf(
                            "" to "All Statuses",
                            "OCCUPIED"    to "Occupied",
                            "CLEANING"    to "Cleaning",
                            "MAINTENANCE" to "Maintenance",
                        ),
                        onSelect = vm::setRoomStatusFilter,
                        modifier = Modifier.weight(1f),
                    )
                }
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
                        val categoryName = state.categoryNames[room.categoryId] ?: room.categoryName
                        RoomListItem(
                            room = room,
                            categoryName = categoryName,
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
                        "${state.rooms.size} rooms · ${state.rooms.count { roomDisplayStatus(it) == "AVAILABLE" }} available",
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
    categoryName: String,
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
            .padding(horizontal = 12.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier
                .width(3.dp)
                .height(44.dp)
                .clip(RoundedCornerShape(2.dp))
                .background(statusColor),
        )
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "Room ${room.roomNumber}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = DreamlandOnDark,
                    fontWeight = FontWeight.SemiBold,
                )
                if (!guestName.isNullOrBlank()) {
                    Text(
                        "  ·  $guestName",
                        style = MaterialTheme.typography.bodySmall,
                        color = DreamlandMuted,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            if (categoryName.isNotBlank() || !room.isAvailableForBooking) {
                Spacer(Modifier.height(4.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                    if (categoryName.isNotBlank()) {
                        Box(
                            Modifier
                                .clip(RoundedCornerShape(3.dp))
                                .background(DreamlandGold.copy(alpha = 0.12f))
                                .padding(horizontal = 5.dp, vertical = 2.dp),
                        ) {
                            Text(
                                categoryName,
                                style = MaterialTheme.typography.labelSmall,
                                color = DreamlandGold.copy(alpha = 0.8f),
                                fontSize = 9.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                    if (!room.isAvailableForBooking) {
                        Box(
                            Modifier
                                .clip(RoundedCornerShape(3.dp))
                                .background(Color(0xFFF39C12).copy(alpha = 0.12f))
                                .padding(horizontal = 5.dp, vertical = 2.dp),
                        ) {
                            Text(
                                "Not Bookable",
                                style = MaterialTheme.typography.labelSmall,
                                color = Color(0xFFF39C12).copy(alpha = 0.85f),
                                fontSize = 9.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                }
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
                if (room.status == "CLEANING" || room.needsCleaning) {
                    Button(
                        onClick = { vm.markCleaningComplete(room) },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2ECC71)),
                        shape = RoundedCornerShape(8.dp),
                        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp),
                    ) {
                        Icon(Icons.Filled.CheckCircle, null, modifier = Modifier.size(14.dp), tint = Color.White)
                        Spacer(Modifier.width(6.dp))
                        Text(if (room.needsCleaning && room.status != "CLEANING") "Mark Clean" else "Mark Available", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                    }
                }
                if (room.status != "OCCUPIED") {
                    val isMaintenance = room.status == "MAINTENANCE"
                    OutlinedButton(
                        onClick = { vm.toggleMaintenance(room) },
                        shape = RoundedCornerShape(8.dp),
                        border = BorderStroke(1.dp, if (isMaintenance) Color(0xFF95A5A6) else DreamlandMuted.copy(alpha = 0.4f)),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                        modifier = Modifier.height(34.dp),
                    ) {
                        Text(
                            if (isMaintenance) "Remove Maintenance" else "Maintenance",
                            color = if (isMaintenance) Color(0xFF95A5A6) else DreamlandMuted,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold,
                        )
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
// Room filter dropdown
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun RoomFilterDropdown(
    label: String,
    selected: String,
    options: List<Pair<String, String>>,
    onSelect: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    var expanded by remember { mutableStateOf(false) }
    val selectedLabel = options.find { it.first == selected }?.second ?: options.firstOrNull()?.second ?: label

    Column(modifier) {
        Text(label, style = MaterialTheme.typography.labelSmall, color = DreamlandMuted, fontSize = 9.sp)
        Spacer(Modifier.height(2.dp))
        ExposedDropdownMenuBox(
            expanded = expanded,
            onExpandedChange = { expanded = it },
        ) {
            Box(
                Modifier
                    .menuAnchor()
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(6.dp))
                    .background(DreamlandForestElevated)
                    .border(
                        1.dp,
                        if (expanded) DreamlandGold.copy(alpha = 0.6f) else DreamlandMuted.copy(alpha = 0.3f),
                        RoundedCornerShape(6.dp),
                    )
                    .padding(horizontal = 10.dp, vertical = 7.dp),
            ) {
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        selectedLabel,
                        style = MaterialTheme.typography.bodySmall,
                        color = DreamlandOnDark,
                        fontSize = 11.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f),
                    )
                    ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded)
                }
            }
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
}

// ─────────────────────────────────────────────────────────────────────────────
// Bookings tab — filters + list
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun BookingsTabContent(
    state: RoomsAndBookingsUiState,
    vm: RoomsAndBookingsViewModel,
    staysVm: StaysViewModel,
    onCheckIn: (Booking) -> Unit,
    onCheckInAll: (List<Booking>) -> Unit = {},
) {
    var bookingSubTab by remember { mutableStateOf(0) }

    Column(Modifier.fillMaxSize()) {
        BookingTopBar(state = state, vm = vm, staysVm = staysVm)

        // ── Inner sub-tabs ────────────────────────────────────────────────────
        TabRow(
            selectedTabIndex = bookingSubTab,
            containerColor = DreamlandForestSurface,
            contentColor = DreamlandOnDark,
            indicator = { tabPositions ->
                SecondaryIndicator(
                    modifier = Modifier.tabIndicatorOffset(tabPositions[bookingSubTab]),
                    color = DreamlandGold,
                )
            },
            divider = { HorizontalDivider(color = DreamlandGold.copy(alpha = 0.12f)) },
        ) {
            listOf("Upcoming Bookings", "Today Check-ins").forEachIndexed { idx, label ->
                Tab(
                    selected = bookingSubTab == idx,
                    onClick = { bookingSubTab = idx },
                    selectedContentColor = DreamlandGold,
                    unselectedContentColor = DreamlandMuted,
                ) {
                    Text(
                        text = label,
                        fontWeight = if (bookingSubTab == idx) FontWeight.SemiBold else FontWeight.Normal,
                        fontSize = 13.sp,
                        modifier = Modifier.padding(vertical = 10.dp),
                    )
                }
            }
        }

        // ── Content ───────────────────────────────────────────────────────────
        val allGroups = state.filteredBookingGroups
        // "Today Check-ins": match on calendar date only, not hotel check-in time
        val todayStart = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
        }.time
        val tomorrowStart = Calendar.getInstance().apply {
            add(Calendar.DAY_OF_YEAR, 1)
            set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
        }.time
        val displayedGroups = if (bookingSubTab == 1)
            allGroups.filter { g -> g.any { it.checkIn >= todayStart && it.checkIn < tomorrowStart } }
        else allGroups

        when {
            state.bookingsLoading && displayedGroups.isEmpty() -> {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = DreamlandGold, strokeWidth = 2.5.dp)
                }
            }
            displayedGroups.isEmpty() -> {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            if (bookingSubTab == 1) "No check-ins today" else "No bookings found",
                            style = MaterialTheme.typography.bodyLarge, color = DreamlandMuted,
                        )
                        Spacer(Modifier.height(4.dp))
                        Text(
                            text = if (state.searchQuery.isNotBlank()) "Try a different search or filter"
                            else if (bookingSubTab == 1) "All caught up for today"
                            else "No bookings for the selected filters",
                            style = MaterialTheme.typography.bodySmall,
                            color = DreamlandMuted.copy(alpha = 0.7f),
                        )
                    }
                }
            }
            else -> {
                val checkedInBookingIds = state.activeStaysByRoom.values
                    .map { it.bookingId }.filter { it.isNotBlank() }.toSet()
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
                LazyColumn(
                    modifier = Modifier.widthIn(max = 960.dp).fillMaxHeight(),
                    contentPadding = PaddingValues(horizontal = 20.dp, vertical = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    items(displayedGroups, key = { group ->
                        if (group.size == 1) group[0].id else group[0].groupBookingId
                    }) { group ->
                        if (group.size == 1) {
                            val booking = group[0]
                            BookingCard(
                                booking = booking,
                                isCheckInToday = state.isCheckInToday(booking),
                                isCheckInTodayOrPassed = state.isCheckInTodayOrPassed(booking),
                                isCheckedIn = booking.id in checkedInBookingIds,
                                hotelCheckInTime = state.hotelCheckInTime,
                                onAssignRoom = { vm.openAssignRoom(booking) },
                                onCheckIn = { onCheckIn(booking) },
                                onCancel = { vm.promptCancelBooking(booking) },
                                onNoShow = { vm.promptMarkNoShow(booking) },
                            )
                        } else {
                            GroupBookingCard(
                                group = group,
                                isCheckInToday = state.isCheckInToday(group[0]),
                                isCheckInTodayOrPassed = state.isCheckInTodayOrPassed(group[0]),
                                checkedInBookingIds = checkedInBookingIds,
                                hotelCheckInTime = state.hotelCheckInTime,
                                onAssignRoom = { vm.openAssignRoom(it) },
                                onCheckIn = { onCheckIn(it) },
                                onCheckInAll = onCheckInAll,
                                onCancel = { vm.promptCancelBooking(it) },
                                onNoShow = { vm.promptMarkNoShow(it) },
                                onNoShowAll = { vm.promptMarkGroupNoShow(group) },
                                onCancelAll = { vm.promptCancelGroupBooking(group) },
                            )
                        }
                    }
                }
                } // end Box
            }
        }
    }
}

@Composable
private fun BookingTopBar(
    state: RoomsAndBookingsUiState,
    vm: RoomsAndBookingsViewModel,
    staysVm: StaysViewModel,
) {
    var timeMenuExpanded by remember { mutableStateOf(false) }
    var statusMenuExpanded by remember { mutableStateOf(false) }

    val timeLabel = when (state.bookingDateFilter) {
        BookingDateFilter.ALL -> "All Time"
        BookingDateFilter.TODAY -> "Today"
        BookingDateFilter.UPCOMING -> "Upcoming"
    }
    val statusLabel = when (state.bookingStatusFilter) {
        null -> "All Status"
        "CONFIRMED" -> "Confirmed"
        "PENDING_PAYMENT" -> "Pending"
        "CANCELLED" -> "Cancelled"
        "NO_SHOW" -> "No Show"
        "COMPLETED" -> "Completed"
        else -> "All Status"
    }
    val isTimeFiltered = state.bookingDateFilter != BookingDateFilter.ALL
    val isStatusFiltered = state.bookingStatusFilter != null

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(DreamlandForestElevated)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        // ── Search ────────────────────────────────────────────────────────────
        OutlinedTextField(
            value = state.searchQuery,
            onValueChange = vm::setSearchQuery,
            placeholder = { Text("Search bookings…", style = MaterialTheme.typography.bodySmall, color = DreamlandMuted, fontSize = 12.sp) },
            leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null, tint = DreamlandMuted, modifier = Modifier.size(15.dp)) },
            trailingIcon = {
                if (state.searchQuery.isNotEmpty()) {
                    IconButton(onClick = { vm.setSearchQuery("") }, modifier = Modifier.size(28.dp)) {
                        Icon(Icons.Filled.Close, contentDescription = "Clear", tint = DreamlandMuted, modifier = Modifier.size(13.dp))
                    }
                }
            },
            singleLine = true,
            modifier = Modifier.weight(1f),
            shape = RoundedCornerShape(8.dp),
            textStyle = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedTextColor = DreamlandOnDark,
                unfocusedTextColor = DreamlandOnDark,
                focusedBorderColor = DreamlandGold.copy(alpha = 0.5f),
                unfocusedBorderColor = DreamlandMuted.copy(alpha = 0.25f),
                cursorColor = DreamlandGold,
                focusedContainerColor = DreamlandForestSurface,
                unfocusedContainerColor = DreamlandForestSurface,
            ),
        )

        // ── All Time dropdown ─────────────────────────────────────────────────
        Box {
            OutlinedButton(
                onClick = { timeMenuExpanded = true },
                border = BorderStroke(1.dp, if (isTimeFiltered) DreamlandGold else DreamlandMuted.copy(alpha = 0.3f)),
                shape = RoundedCornerShape(8.dp),
                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 0.dp),
                modifier = Modifier.height(36.dp),
                colors = ButtonDefaults.outlinedButtonColors(containerColor = DreamlandForestSurface),
            ) {
                Text(timeLabel, color = if (isTimeFiltered) DreamlandGold else DreamlandMuted, fontSize = 12.sp, fontWeight = FontWeight.Medium)
                Spacer(Modifier.width(4.dp))
                Icon(Icons.Filled.KeyboardArrowDown, contentDescription = null, tint = if (isTimeFiltered) DreamlandGold else DreamlandMuted, modifier = Modifier.size(14.dp))
            }
            androidx.compose.material3.DropdownMenu(
                expanded = timeMenuExpanded,
                onDismissRequest = { timeMenuExpanded = false },
                containerColor = DreamlandForestElevated,
            ) {
                listOf(BookingDateFilter.ALL to "All Time", BookingDateFilter.TODAY to "Today", BookingDateFilter.UPCOMING to "Upcoming").forEach { (filter, label) ->
                    DropdownMenuItem(
                        text = { Text(label, color = if (state.bookingDateFilter == filter) DreamlandGold else DreamlandOnDark, fontSize = 12.sp) },
                        onClick = { vm.setBookingDateFilter(filter); timeMenuExpanded = false },
                        modifier = Modifier.background(if (state.bookingDateFilter == filter) DreamlandGold.copy(alpha = 0.08f) else Color.Transparent),
                    )
                }
            }
        }

        // ── All Status dropdown ───────────────────────────────────────────────
        Box {
            OutlinedButton(
                onClick = { statusMenuExpanded = true },
                border = BorderStroke(1.dp, if (isStatusFiltered) DreamlandGold else DreamlandMuted.copy(alpha = 0.3f)),
                shape = RoundedCornerShape(8.dp),
                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 0.dp),
                modifier = Modifier.height(36.dp),
                colors = ButtonDefaults.outlinedButtonColors(containerColor = DreamlandForestSurface),
            ) {
                if (isStatusFiltered) {
                    Box(Modifier.size(6.dp).clip(CircleShape).background(bookingStatusColor(state.bookingStatusFilter!!)))
                    Spacer(Modifier.width(5.dp))
                }
                Text(statusLabel, color = if (isStatusFiltered) DreamlandGold else DreamlandMuted, fontSize = 12.sp, fontWeight = FontWeight.Medium)
                Spacer(Modifier.width(4.dp))
                Icon(Icons.Filled.KeyboardArrowDown, contentDescription = null, tint = if (isStatusFiltered) DreamlandGold else DreamlandMuted, modifier = Modifier.size(14.dp))
            }
            val statusOptions = listOf(null to "All Status", "CONFIRMED" to "Confirmed", "PENDING_PAYMENT" to "Pending Payment", "CANCELLED" to "Cancelled", "NO_SHOW" to "No Show", "COMPLETED" to "Completed")
            androidx.compose.material3.DropdownMenu(
                expanded = statusMenuExpanded,
                onDismissRequest = { statusMenuExpanded = false },
                containerColor = DreamlandForestElevated,
            ) {
                statusOptions.forEach { (status, label) ->
                    DropdownMenuItem(
                        text = {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                if (status != null) {
                                    Box(Modifier.size(6.dp).clip(CircleShape).background(bookingStatusColor(status)))
                                    Spacer(Modifier.width(6.dp))
                                }
                                Text(label, color = if (state.bookingStatusFilter == status) DreamlandGold else DreamlandOnDark, fontSize = 12.sp)
                            }
                        },
                        onClick = { vm.setBookingStatusFilter(status); statusMenuExpanded = false },
                        modifier = Modifier.background(if (state.bookingStatusFilter == status) DreamlandGold.copy(alpha = 0.08f) else Color.Transparent),
                    )
                }
            }
        }

        // ── Sort ──────────────────────────────────────────────────────────────
        val isNewest = state.bookingSortOrder == "NEWEST"
        OutlinedButton(
            onClick = { vm.setBookingSortOrder(if (isNewest) "OLDEST" else "NEWEST") },
            border = BorderStroke(1.dp, DreamlandMuted.copy(alpha = 0.3f)),
            shape = RoundedCornerShape(8.dp),
            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 0.dp),
            modifier = Modifier.height(36.dp),
            colors = ButtonDefaults.outlinedButtonColors(containerColor = DreamlandForestSurface),
        ) {
            Icon(Icons.AutoMirrored.Filled.Sort, contentDescription = if (isNewest) "Newest first" else "Oldest first", tint = DreamlandGold, modifier = Modifier.size(16.dp))
        }

        // ── New Booking ───────────────────────────────────────────────────────
        Button(
            onClick = staysVm::openWalkInAsBooking,
            shape = RoundedCornerShape(8.dp),
            contentPadding = PaddingValues(horizontal = 14.dp, vertical = 0.dp),
            modifier = Modifier.height(36.dp),
            colors = ButtonDefaults.buttonColors(containerColor = DreamlandGold, contentColor = Color(0xFF0D1F17)),
        ) {
            Text("+ New Booking", fontWeight = FontWeight.SemiBold, fontSize = 12.sp, color = Color(0xFF0D1F17))
        }
    }
}

// ── Group booking card (multiple rooms, same groupBookingId) ──────────────────

@Composable
private fun GroupBookingCard(
    group: List<Booking>,
    isCheckInToday: Boolean,
    isCheckInTodayOrPassed: Boolean = false,
    checkedInBookingIds: Set<String> = emptySet(),
    hotelCheckInTime: String = "12:00",
    onAssignRoom: (Booking) -> Unit,
    onCheckIn: (Booking) -> Unit,
    onCheckInAll: (List<Booking>) -> Unit = {},
    onCancel: (Booking) -> Unit,
    onNoShow: (Booking) -> Unit,
    onNoShowAll: () -> Unit = {},
    onCancelAll: () -> Unit = {},
) {
    val fmt = SimpleDateFormat("d MMM", Locale.getDefault())
    val primary = group[0]
    val allStatuses = group.map { it.status }.distinct()
    val overallStatus = when {
        allStatuses.all { it == "CONFIRMED" }       -> "CONFIRMED"
        allStatuses.any { it == "PENDING_PAYMENT" } -> "PENDING_PAYMENT"
        allStatuses.all { it == "COMPLETED" }       -> "COMPLETED"
        allStatuses.all { it == "CANCELLED" }       -> "CANCELLED"
        else                                        -> "CONFIRMED"
    }
    val statusColor = bookingStatusColor(overallStatus)
    val totalAmount = group.sumOf { it.totalAmount }
    val totalAdvance = group.sumOf { it.advancePaidAmount }

    val now = Date()
    val graceDeadline = run {
        val parts = hotelCheckInTime.split(":")
        val ciHour = parts.getOrNull(0)?.toIntOrNull() ?: 12
        val ciMin  = parts.getOrNull(1)?.toIntOrNull() ?: 0
        Calendar.getInstance().apply {
            time = primary.checkIn
            set(Calendar.HOUR_OF_DAY, ciHour); set(Calendar.MINUTE, ciMin)
            set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
            add(Calendar.HOUR_OF_DAY, 2)
        }.time
    }
    val isOverdue = overallStatus == "CONFIRMED" && now.after(graceDeadline)
    val hoursOverdue = if (isOverdue) ((now.time - graceDeadline.time) / (1000L * 3600)).toInt() else 0

    // Note: show the first non-blank note across group members
    val sharedNote = group.firstOrNull { it.notes.isNotBlank() }?.notes

    Card(
        colors = CardDefaults.cardColors(containerColor = DreamlandForestSurface),
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier
            .fillMaxWidth()
            .border(
                width = if (isOverdue) 1.5.dp else if (isCheckInToday) 1.5.dp else 1.dp,
                color = when {
                    isOverdue     -> Color(0xFFF39C12).copy(alpha = 0.6f)
                    isCheckInToday -> DreamlandGold.copy(alpha = 0.6f)
                    else           -> DreamlandGold.copy(alpha = 0.12f)
                },
                shape = RoundedCornerShape(12.dp),
            ),
    ) {
        Column(Modifier.padding(16.dp)) {
            // ── Header ──────────────────────────────────────────────────────
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.Top) {
                Column(Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(
                            primary.guestName.ifBlank { "Guest" },
                            style = MaterialTheme.typography.titleMedium,
                            color = DreamlandOnDark,
                            fontWeight = FontWeight.Bold,
                        )
                        Box(
                            Modifier
                                .clip(RoundedCornerShape(4.dp))
                                .background(DreamlandGold.copy(alpha = 0.15f))
                                .padding(horizontal = 6.dp, vertical = 2.dp),
                        ) {
                            Text("${group.size} ROOMS", style = MaterialTheme.typography.labelSmall, color = DreamlandGold, fontSize = 9.sp, letterSpacing = 0.8.sp)
                        }
                        if (isOverdue) {
                            Box(
                                Modifier
                                    .clip(RoundedCornerShape(4.dp))
                                    .background(Color(0xFFF39C12).copy(alpha = 0.2f))
                                    .padding(horizontal = 6.dp, vertical = 2.dp),
                            ) {
                                Text("OVERDUE", style = MaterialTheme.typography.labelSmall, color = Color(0xFFF39C12), fontSize = 9.sp, letterSpacing = 0.8.sp)
                            }
                        } else if (isCheckInToday) {
                            Box(
                                Modifier
                                    .clip(RoundedCornerShape(4.dp))
                                    .background(DreamlandGold.copy(alpha = 0.2f))
                                    .padding(horizontal = 6.dp, vertical = 2.dp),
                            ) {
                                Text("CHECK-IN TODAY", style = MaterialTheme.typography.labelSmall, color = DreamlandGold, fontSize = 9.sp, letterSpacing = 0.8.sp)
                            }
                        }
                    }
                    Spacer(Modifier.height(3.dp))
                    Text(
                        "${fmt.format(primary.checkIn)} → ${fmt.format(primary.checkOut)}  ·  ${primary.adults} Adults",
                        style = MaterialTheme.typography.bodySmall,
                        color = DreamlandMuted,
                    )
                    if (primary.guestPhone.isNotBlank()) {
                        Spacer(Modifier.height(3.dp))
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            Icon(Icons.Filled.Phone, contentDescription = null, tint = DreamlandGold.copy(alpha = 0.8f), modifier = Modifier.size(11.dp))
                            Text(primary.guestPhone, style = MaterialTheme.typography.bodySmall, color = DreamlandGold.copy(alpha = 0.9f), fontWeight = FontWeight.Medium)
                        }
                    }
                }
                Box(
                    Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .background(statusColor.copy(alpha = 0.15f))
                        .border(1.dp, statusColor.copy(alpha = 0.4f), RoundedCornerShape(6.dp))
                        .padding(horizontal = 10.dp, vertical = 4.dp),
                ) {
                    Text(overallStatus, style = MaterialTheme.typography.labelMedium, color = statusColor, fontSize = 10.sp, letterSpacing = 0.8.sp)
                }
            }

            // Overdue hint banner
            if (isOverdue) {
                Spacer(Modifier.height(8.dp))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(6.dp))
                        .background(Color(0xFFF39C12).copy(alpha = 0.08f))
                        .padding(horizontal = 10.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text(
                        if (hoursOverdue == 0) "Grace period passed — no-show eligible"
                        else "${hoursOverdue}h past grace period — consider marking no-show",
                        color = Color(0xFFF39C12),
                        style = MaterialTheme.typography.labelSmall,
                        fontSize = 10.sp,
                    )
                }
            }

            // Note
            if (!sharedNote.isNullOrBlank()) {
                Spacer(Modifier.height(6.dp))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(6.dp))
                        .background(DreamlandForestElevated)
                        .padding(horizontal = 10.dp, vertical = 6.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.Top,
                ) {
                    Text("Note:", style = MaterialTheme.typography.labelSmall, color = DreamlandMuted, fontSize = 10.sp)
                    Text(sharedNote, style = MaterialTheme.typography.bodySmall, color = DreamlandOnDark, fontSize = 11.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
                }
            }

            Spacer(Modifier.height(12.dp))
            HorizontalDivider(color = DreamlandGold.copy(alpha = 0.1f))
            Spacer(Modifier.height(8.dp))

            // ── Per-room rows ────────────────────────────────────────────────
            group.forEach { booking ->
                val hasRoom = booking.roomNumber.isNotBlank()
                val isConfirmed = booking.status == "CONFIRMED"
                Row(
                    Modifier.fillMaxWidth().padding(vertical = 5.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            if (hasRoom) {
                                Box(
                                    Modifier
                                        .clip(RoundedCornerShape(4.dp))
                                        .background(DreamlandGold.copy(alpha = 0.1f))
                                        .padding(horizontal = 7.dp, vertical = 2.dp),
                                ) {
                                    Text("Room ${booking.roomNumber}", style = MaterialTheme.typography.labelSmall, color = DreamlandGold, fontSize = 11.sp)
                                }
                            } else {
                                Box(
                                    Modifier
                                        .clip(RoundedCornerShape(4.dp))
                                        .background(DreamlandMuted.copy(alpha = 0.1f))
                                        .padding(horizontal = 7.dp, vertical = 2.dp),
                                ) {
                                    Text("No room", style = MaterialTheme.typography.labelSmall, color = DreamlandMuted, fontSize = 11.sp)
                                }
                            }
                            if (booking.roomCategoryName.isNotBlank()) {
                                Text(booking.roomCategoryName, style = MaterialTheme.typography.bodySmall, color = DreamlandMuted)
                            }
                        }
                    }
                    if (booking.status == "NO_SHOW") {
                        Box(
                            Modifier
                                .clip(RoundedCornerShape(4.dp))
                                .background(Color(0xFF95A5A6).copy(alpha = 0.15f))
                                .padding(horizontal = 7.dp, vertical = 3.dp),
                        ) {
                            Text("NO SHOW", style = MaterialTheme.typography.labelSmall, color = Color(0xFF95A5A6), fontSize = 9.sp, letterSpacing = 0.8.sp)
                        }
                    }
                    if (isConfirmed) {
                        val isRoomCheckedIn = booking.id in checkedInBookingIds
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                            if (isCheckInTodayOrPassed && !isRoomCheckedIn) {
                                TextButton(
                                    onClick = { onNoShow(booking) },
                                    contentPadding = PaddingValues(horizontal = 6.dp, vertical = 4.dp),
                                    modifier = Modifier.height(30.dp),
                                ) { Text("No-Show", color = Color(0xFFE74C3C), fontSize = 10.sp) }
                            }
                            if (!isRoomCheckedIn) {
                                OutlinedButton(
                                    onClick = { onAssignRoom(booking) },
                                    shape = RoundedCornerShape(6.dp),
                                    colors = ButtonDefaults.outlinedButtonColors(contentColor = DreamlandGold),
                                    border = androidx.compose.foundation.BorderStroke(1.dp, DreamlandGold.copy(alpha = 0.45f)),
                                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                                    modifier = Modifier.height(30.dp),
                                ) { Text(if (hasRoom) "Reassign" else "Assign", fontSize = 11.sp) }
                                Button(
                                    onClick = { onCheckIn(booking) },
                                    colors = ButtonDefaults.buttonColors(containerColor = DreamlandGold),
                                    shape = RoundedCornerShape(6.dp),
                                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                                    modifier = Modifier.height(30.dp),
                                ) { Text("Check-in", color = Color(0xFF0D1F17), fontWeight = FontWeight.Bold, fontSize = 11.sp) }
                            }
                        }
                    }
                }
                if (booking != group.last()) HorizontalDivider(color = DreamlandMuted.copy(alpha = 0.08f))
            }

            Spacer(Modifier.height(10.dp))
            HorizontalDivider(color = DreamlandGold.copy(alpha = 0.1f))
            Spacer(Modifier.height(10.dp))

            // ── Footer: totals + actions ─────────────────────────────────────
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Column {
                    Text(formatRupees(totalAmount), style = MaterialTheme.typography.bodyMedium, color = DreamlandOnDark, fontWeight = FontWeight.SemiBold)
                    if (totalAdvance > 0) Text("${formatRupees(totalAdvance)} paid", style = MaterialTheme.typography.bodySmall, color = Color(0xFF2ECC71))
                }
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                    val confirmedInGroup = group.filter { it.status == "CONFIRMED" && it.id !in checkedInBookingIds }
                    if (confirmedInGroup.isNotEmpty()) {
                        Button(
                            onClick = { onCheckInAll(confirmedInGroup) },
                            colors = ButtonDefaults.buttonColors(containerColor = DreamlandGold),
                            shape = RoundedCornerShape(8.dp),
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                            modifier = Modifier.height(32.dp),
                        ) {
                            Text("Check In All", color = Color(0xFF0D1F17), fontWeight = FontWeight.Bold, fontSize = 11.sp)
                        }
                    }
                    val anyNoShowEligible = group.any { b ->
                        b.status == "CONFIRMED" && b.id !in checkedInBookingIds && isCheckInTodayOrPassed
                    }
                    if (anyNoShowEligible) {
                        TextButton(
                            onClick = onNoShowAll,
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                        ) { Text("No-Show All", color = Color(0xFFE74C3C), fontSize = 12.sp) }
                    }
                    val anyCancellable = group.any { it.status == "CONFIRMED" }
                    if (anyCancellable) {
                        TextButton(
                            onClick = onCancelAll,
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                        ) { Text("Cancel All", color = Color(0xFFE74C3C).copy(alpha = 0.8f), fontSize = 12.sp) }
                    }
                }
            }
        }
    }
}

@Composable
private fun BookingCard(
    booking: Booking,
    isCheckInToday: Boolean,
    isCheckInTodayOrPassed: Boolean = false,
    isCheckedIn: Boolean = false,
    hotelCheckInTime: String = "12:00",
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
    // Grace deadline = hotel check-in time on the booking's check-in date + 12 hours
    val graceDeadline = run {
        val parts = hotelCheckInTime.split(":")
        val ciHour = parts.getOrNull(0)?.toIntOrNull() ?: 12
        val ciMin  = parts.getOrNull(1)?.toIntOrNull() ?: 0
        Calendar.getInstance().apply {
            time = booking.checkIn
            set(Calendar.HOUR_OF_DAY, ciHour)
            set(Calendar.MINUTE, ciMin)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
            add(Calendar.HOUR_OF_DAY, 2)
        }.time
    }
    val isOverdue = isConfirmed && now.after(graceDeadline)
    val hoursOverdue = if (isOverdue) ((now.time - graceDeadline.time) / (1000L * 3600)).toInt() else 0

    Card(
        colors = CardDefaults.cardColors(containerColor = DreamlandForestSurface),
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier
            .fillMaxWidth()
            .border(
                width = if (isOverdue || (isCheckInToday && isConfirmed)) 1.5.dp else 1.dp,
                color = when {
                    isOverdue && isConfirmed     -> Color(0xFFF39C12).copy(alpha = 0.6f)
                    isCheckInToday && isConfirmed -> DreamlandGold.copy(alpha = 0.6f)
                    else                          -> DreamlandGold.copy(alpha = 0.12f)
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
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(
                            text = booking.guestName.ifBlank { "Guest" },
                            style = MaterialTheme.typography.titleMedium,
                            color = DreamlandOnDark,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        if (isOverdue && isConfirmed) {
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(4.dp))
                                    .background(Color(0xFFF39C12).copy(alpha = 0.2f))
                                    .padding(horizontal = 6.dp, vertical = 2.dp),
                            ) {
                                Text("OVERDUE", style = MaterialTheme.typography.labelMedium, color = Color(0xFFF39C12), fontSize = 9.sp, letterSpacing = 0.8.sp)
                            }
                        } else if (isCheckInToday && isConfirmed) {
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(4.dp))
                                    .background(DreamlandGold.copy(alpha = 0.2f))
                                    .padding(horizontal = 6.dp, vertical = 2.dp),
                            ) {
                                Text("CHECK-IN TODAY", style = MaterialTheme.typography.labelMedium, color = DreamlandGold, fontSize = 9.sp, letterSpacing = 0.8.sp)
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
                    if (booking.guestPhone.isNotBlank()) {
                        Spacer(Modifier.height(3.dp))
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            Icon(Icons.Filled.Phone, contentDescription = null, tint = DreamlandGold.copy(alpha = 0.8f), modifier = Modifier.size(11.dp))
                            Text(booking.guestPhone, style = MaterialTheme.typography.bodySmall, color = DreamlandGold.copy(alpha = 0.9f), fontWeight = FontWeight.Medium)
                        }
                    }
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

            if (booking.notes.isNotBlank()) {
                Spacer(Modifier.height(6.dp))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(6.dp))
                        .background(DreamlandForestElevated)
                        .padding(horizontal = 10.dp, vertical = 6.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.Top,
                ) {
                    Text("Note:", style = MaterialTheme.typography.labelSmall, color = DreamlandMuted, fontSize = 10.sp)
                    Text(booking.notes, style = MaterialTheme.typography.bodySmall, color = DreamlandOnDark, fontSize = 11.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
                }
            }

            if (isOverdue) {
                Spacer(Modifier.height(6.dp))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(6.dp))
                        .background(Color(0xFFF39C12).copy(alpha = 0.08f))
                        .padding(horizontal = 10.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        if (hoursOverdue == 0) "Grace period passed — no-show eligible"
                        else "${hoursOverdue}h past grace period — consider marking no-show",
                        color = Color(0xFFF39C12),
                        fontSize = 10.sp,
                        style = MaterialTheme.typography.labelSmall,
                    )
                }
            }

            if (booking.status == "CANCELLED" && booking.cancellationSource == "RECEPTION") {
                Spacer(Modifier.height(6.dp))
                val refundRupees = "%.2f".format(booking.cancellationRefundAmountPaise / 100.0)
                val badgeText = when {
                    booking.cancellationRefundStatus == "COMPLETED" ->
                        "Refund completed — ₹$refundRupees"
                    booking.cancellationRefundStatus == "INITIATED" ->
                        "Refund initiated — ₹$refundRupees" +
                            booking.cancellationRefundMode.takeIf { it.isNotBlank() }?.let { " ($it)" }.orEmpty()
                    booking.cancellationRefundStatus == "FAILED" ->
                        "Refund failed — contact admin"
                    booking.cancellationRefundStatus == "MANUAL" -> {
                        val method = booking.cancellationManualRefundMethod.ifBlank { "manual" }
                        if (booking.cancellationRefundAmountPaise == 0L)
                            "Cancelled — no refund"
                        else
                            "Manual refund logged — ₹$refundRupees ($method)"
                    }
                    booking.cancellationRefundAmountPaise == 0L ->
                        "Cancelled — no refund per policy"
                    else -> "Cancelled by reception"
                }
                val badgeColor = when (booking.cancellationRefundStatus) {
                    "FAILED" -> Color(0xFFE74C3C)
                    "MANUAL" -> DreamlandMuted
                    else -> DreamlandGold
                }
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(6.dp))
                        .background(badgeColor.copy(alpha = 0.10f))
                        .padding(horizontal = 10.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(badgeText, color = badgeColor, fontSize = 10.sp, style = MaterialTheme.typography.labelSmall)
                    if (booking.cancelledByReceptionUserId.isNotBlank()) {
                        Spacer(Modifier.weight(1f))
                        Text("by ${booking.cancelledByReceptionUserId}", color = DreamlandMuted, fontSize = 10.sp)
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
                    if (!isCheckedIn) {
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
                    }
                    Spacer(Modifier.weight(1f))
                    if (isCheckInTodayOrPassed && !isCheckedIn) {
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
    dialog: CancelDialogState,
    onReasonChanged: (String) -> Unit,
    onRefundModeChanged: (ReceptionRefundMode) -> Unit,
    onRefundAmountChanged: (String) -> Unit,
    onPaidViaChanged: (ManualRefundMethod) -> Unit,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    val primary = dialog.primary
    val group = dialog.groupBookings
    val totalAdvanceRupees = "%.2f".format(dialog.totalAdvancePaise / 100.0)
    val checkInFmt = SimpleDateFormat("dd MMM yyyy", Locale.getDefault()).format(primary.checkIn)
    val reasonValid = dialog.reason.trim().length in 10..500
    val isManual = dialog.isManual

    // Amount field validity.
    //   Manual flow → required for every mode; must be > 0 and ≤ total advance.
    //   Razorpay flow → only required for FIXED.
    val amountPaise: Long? = run {
        val r = dialog.refundAmountRupeesInput.toDoubleOrNull() ?: return@run null
        (r * 100.0).toLong()
    }
    val amountValid = when {
        isManual -> amountPaise != null && amountPaise in 0L..dialog.totalAdvancePaise
        dialog.refundMode == ReceptionRefundMode.FIXED ->
            amountPaise != null && amountPaise in 1L..dialog.totalAdvancePaise
        else -> true
    }
    val paidViaValid = !isManual || dialog.paidVia != null

    val canConfirm = !dialog.isLoading && reasonValid && amountValid && paidViaValid

    AlertDialog(
        onDismissRequest = { if (!dialog.isLoading) onDismiss() },
        containerColor = DreamlandForestSurface,
        titleContentColor = DreamlandOnDark,
        textContentColor = DreamlandMuted,
        shape = RoundedCornerShape(16.dp),
        title = {
            Text(
                if (isManual) "Cancel Booking + Log Refund" else "Cancel Booking + Refund",
                fontWeight = FontWeight.SemiBold,
            )
        },
        text = {
            Column(modifier = Modifier.widthIn(min = 460.dp, max = 600.dp).verticalScroll(rememberScrollState())) {
                if (isManual) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(DreamlandGold.copy(alpha = 0.10f), RoundedCornerShape(8.dp))
                            .border(1.dp, DreamlandGold.copy(alpha = 0.35f), RoundedCornerShape(8.dp))
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                    ) {
                        Text(
                            "Hotel-side booking — no online payment. Refund will be logged for accounting; staff hands over the cash or initiates the bank transfer manually.",
                            color = DreamlandOnDark, fontSize = 12.sp,
                        )
                    }
                    Spacer(Modifier.height(12.dp))
                }

                // Booking summary
                CancelSummaryRow("Guest", "${primary.guestName.ifBlank { "—" }}${primary.guestPhone.takeIf { it.isNotBlank() }?.let { "  ($it)" }.orEmpty()}")
                if (primary.groupBookingId.isNotBlank()) {
                    CancelSummaryRow("Group", primary.groupBookingId)
                }
                CancelSummaryRow(
                    label = if (group.size > 1) "Rooms" else "Room",
                    value = group.joinToString(", ") { it.roomCategoryName.ifBlank { it.roomCategoryId } },
                )
                CancelSummaryRow("Check-in", checkInFmt)
                CancelSummaryRow("Advance paid", "₹$totalAdvanceRupees")

                Spacer(Modifier.height(14.dp))

                // Refund mode picker
                Text("Refund mode", color = DreamlandOnDark, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                Spacer(Modifier.height(4.dp))
                val policySubtitle = when (val preview = dialog.policyPreviewPaise) {
                    null -> "Calculating refund per cancellation policy…"
                    0L   -> "No refund per current policy."
                    else -> "Refund ₹${"%.2f".format(preview / 100.0)} per cancellation policy."
                }
                CancelRefundModeRow(
                    selected = dialog.refundMode == ReceptionRefundMode.POLICY,
                    enabled = !dialog.isLoading,
                    title = "As per cancellation policy",
                    subtitle = policySubtitle,
                    onClick = { onRefundModeChanged(ReceptionRefundMode.POLICY) },
                )
                CancelRefundModeRow(
                    selected = dialog.refundMode == ReceptionRefundMode.FULL,
                    enabled = !dialog.isLoading,
                    title = "Full advance refund",
                    subtitle = "Refund ₹$totalAdvanceRupees (whole advance).",
                    onClick = { onRefundModeChanged(ReceptionRefundMode.FULL) },
                )
                CancelRefundModeRow(
                    selected = dialog.refundMode == ReceptionRefundMode.FIXED,
                    enabled = !dialog.isLoading,
                    title = "Custom amount",
                    subtitle = "Manager enters a specific refund amount.",
                    onClick = { onRefundModeChanged(ReceptionRefundMode.FIXED) },
                )

                // Amount field: shown ALWAYS on the manual flow; only on FIXED for Razorpay.
                val showAmount = isManual || dialog.refundMode == ReceptionRefundMode.FIXED
                if (showAmount) {
                    Spacer(Modifier.height(10.dp))
                    Text(
                        if (isManual) "Refund amount" else "Custom amount",
                        color = DreamlandOnDark, fontWeight = FontWeight.SemiBold, fontSize = 13.sp,
                    )
                    Spacer(Modifier.height(4.dp))
                    OutlinedTextField(
                        value = dialog.refundAmountRupeesInput,
                        onValueChange = onRefundAmountChanged,
                        enabled = !dialog.isLoading,
                        placeholder = { Text("Refund amount (₹)", color = DreamlandMuted) },
                        prefix = { Text("₹", color = DreamlandOnDark) },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        modifier = Modifier.fillMaxWidth(),
                        isError = !amountValid && dialog.refundAmountRupeesInput.isNotBlank(),
                        supportingText = {
                            Text(
                                if (!amountValid && dialog.refundAmountRupeesInput.isNotBlank())
                                    "Must be at most ₹$totalAdvanceRupees."
                                else
                                    "Max ₹$totalAdvanceRupees.",
                                color = if (!amountValid && dialog.refundAmountRupeesInput.isNotBlank())
                                    Color(0xFFE74C3C) else DreamlandMuted,
                                fontSize = 12.sp,
                            )
                        },
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = DreamlandOnDark,
                            unfocusedTextColor = DreamlandOnDark,
                            focusedBorderColor = DreamlandGold,
                            unfocusedBorderColor = DreamlandMuted,
                            errorBorderColor = Color(0xFFE74C3C),
                            cursorColor = DreamlandGold,
                        ),
                    )
                }

                // Paid-via toggle (manual flow only)
                if (isManual) {
                    Spacer(Modifier.height(12.dp))
                    Text("Paid via", color = DreamlandOnDark, fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                    Spacer(Modifier.height(6.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        PaidViaButton(
                            label = "CASH",
                            selected = dialog.paidVia == ManualRefundMethod.CASH,
                            enabled = !dialog.isLoading,
                            modifier = Modifier.weight(1f),
                            onClick = { onPaidViaChanged(ManualRefundMethod.CASH) },
                        )
                        PaidViaButton(
                            label = "BANK",
                            selected = dialog.paidVia == ManualRefundMethod.BANK,
                            enabled = !dialog.isLoading,
                            modifier = Modifier.weight(1f),
                            onClick = { onPaidViaChanged(ManualRefundMethod.BANK) },
                        )
                    }
                    if (dialog.paidVia == null) {
                        Spacer(Modifier.height(4.dp))
                        Text(
                            "Pick CASH or BANK to confirm the manual refund.",
                            color = DreamlandMuted, fontSize = 11.sp,
                        )
                    }
                }

                Spacer(Modifier.height(14.dp))

                // Reason field
                Text("Reason for cancellation", color = DreamlandOnDark, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                Spacer(Modifier.height(4.dp))
                OutlinedTextField(
                    value = dialog.reason,
                    onValueChange = onReasonChanged,
                    enabled = !dialog.isLoading,
                    placeholder = { Text("Why is this booking being cancelled?", color = DreamlandMuted) },
                    minLines = 2,
                    maxLines = 4,
                    modifier = Modifier.fillMaxWidth(),
                    isError = dialog.reason.isNotEmpty() && !reasonValid,
                    supportingText = {
                        Text(
                            if (dialog.reason.isEmpty()) "Required — at least 10 characters."
                            else if (!reasonValid) "Reason must be 10–500 characters."
                            else "${dialog.reason.length}/500",
                            color = if (dialog.reason.isNotEmpty() && !reasonValid)
                                Color(0xFFE74C3C) else DreamlandMuted,
                            fontSize = 12.sp,
                        )
                    },
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = DreamlandOnDark,
                        unfocusedTextColor = DreamlandOnDark,
                        focusedBorderColor = DreamlandGold,
                        unfocusedBorderColor = DreamlandMuted,
                        errorBorderColor = Color(0xFFE74C3C),
                        cursorColor = DreamlandGold,
                    ),
                )

                // Loading + error banners
                if (dialog.isLoading) {
                    Spacer(Modifier.height(10.dp))
                    LinearProgressIndicator(
                        modifier = Modifier.fillMaxWidth(),
                        color = DreamlandGold,
                        trackColor = DreamlandForest,
                    )
                }
                dialog.error?.let { err ->
                    Spacer(Modifier.height(10.dp))
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(Color(0xFFE74C3C).copy(alpha = 0.12f), RoundedCornerShape(8.dp))
                            .border(1.dp, Color(0xFFE74C3C).copy(alpha = 0.4f), RoundedCornerShape(8.dp))
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                    ) {
                        Text(err.title, color = DreamlandOnDark, fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                        Text(err.message, color = DreamlandMuted, fontSize = 12.sp)
                        if (err.retrySafe) {
                            Text("Safe to retry.", color = DreamlandMuted, fontSize = 11.sp)
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = onConfirm,
                enabled = canConfirm,
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color(0xFFE74C3C),
                    disabledContainerColor = Color(0xFFE74C3C).copy(alpha = 0.4f),
                ),
                shape = RoundedCornerShape(8.dp),
            ) {
                Text(
                    if (isManual) "Cancel + Log refund" else "Cancel + Refund",
                    fontWeight = FontWeight.SemiBold,
                )
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, enabled = !dialog.isLoading) {
                Text("Keep booking", color = DreamlandMuted)
            }
        },
    )
}

@Composable
private fun PaidViaButton(
    label: String,
    selected: Boolean,
    enabled: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    val borderColor = if (selected) DreamlandGold else DreamlandMuted.copy(alpha = 0.5f)
    val bgColor = if (selected) DreamlandGold.copy(alpha = 0.18f) else Color.Transparent
    val textColor = if (selected) DreamlandGold else DreamlandOnDark
    OutlinedButton(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier,
        shape = RoundedCornerShape(8.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, borderColor),
        colors = ButtonDefaults.outlinedButtonColors(
            containerColor = bgColor,
            contentColor = textColor,
        ),
        contentPadding = PaddingValues(vertical = 12.dp),
    ) {
        Text(label, fontWeight = FontWeight.SemiBold, fontSize = 13.sp, letterSpacing = 0.5.sp)
    }
}

@Composable
private fun CancelSummaryRow(label: String, value: String) {
    Row(modifier = Modifier.padding(vertical = 2.dp)) {
        Text(label, color = DreamlandMuted, fontSize = 13.sp, modifier = Modifier.width(110.dp))
        Text(value, color = DreamlandOnDark, fontSize = 13.sp)
    }
}

@Composable
private fun CancelRefundModeRow(
    selected: Boolean,
    enabled: Boolean,
    title: String,
    subtitle: String,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = enabled, onClick = onClick)
            .padding(vertical = 4.dp),
        verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
    ) {
        RadioButton(
            selected = selected,
            onClick = onClick,
            enabled = enabled,
            colors = RadioButtonDefaults.colors(
                selectedColor = DreamlandGold,
                unselectedColor = DreamlandMuted,
            ),
        )
        Spacer(Modifier.width(4.dp))
        Column {
            Text(title, color = DreamlandOnDark, fontSize = 14.sp)
            Text(subtitle, color = DreamlandMuted, fontSize = 11.sp)
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// No-show confirmation dialog
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun NoShowConfirmDialog(
    booking: Booking,
    hotelCheckInTime: String = "12:00",
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    val timeFmt = SimpleDateFormat("dd MMM, hh:mm a", Locale.getDefault())
    val graceDeadline = run {
        val parts = hotelCheckInTime.split(":")
        val ciHour = parts.getOrNull(0)?.toIntOrNull() ?: 12
        val ciMin  = parts.getOrNull(1)?.toIntOrNull() ?: 0
        Calendar.getInstance().apply {
            time = booking.checkIn
            set(Calendar.HOUR_OF_DAY, ciHour)
            set(Calendar.MINUTE, ciMin)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
            add(Calendar.HOUR_OF_DAY, 2)
        }.time
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = DreamlandForestSurface,
        titleContentColor = DreamlandOnDark,
        textContentColor = DreamlandMuted,
        title = { Text("Mark as No-Show?") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Permanently mark ${booking.guestName}'s booking as No-Show. The room slot will be released and the guest must make a fresh booking if they arrive later.")
                Text("This action is final and cannot be undone.", fontSize = 12.sp, color = Color(0xFFEF5350))
                Box(
                    Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .background(DreamlandForestElevated)
                        .padding(horizontal = 10.dp, vertical = 8.dp),
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text("Grace period ended", style = MaterialTheme.typography.labelSmall, color = DreamlandMuted)
                        Text(timeFmt.format(graceDeadline), style = MaterialTheme.typography.bodySmall, color = DreamlandOnDark, fontWeight = FontWeight.SemiBold)
                    }
                }
                if (booking.advancePaidAmount > 0) {
                    Box(
                        Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .background(Color(0xFFF39C12).copy(alpha = 0.12f))
                            .padding(horizontal = 10.dp, vertical = 8.dp),
                    ) {
                        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            Text("Advance payment on record", style = MaterialTheme.typography.labelSmall, color = Color(0xFFF39C12), fontWeight = FontWeight.SemiBold)
                            Text(
                                "${formatRupees(booking.advancePaidAmount)} — you will be prompted to record the refund outcome after confirming.",
                                color = Color(0xFFF39C12),
                                fontSize = 11.sp,
                            )
                        }
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

// ─────────────────────────────────────────────────────────────────────────────
// No-show refund outcome dialog
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun NoShowRefundOutcomeDialog(
    state: NoShowRefundDialogState,
    onStatusSelected: (String) -> Unit,
    onNoteChanged: (String) -> Unit,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    val booking = state.booking
    Dialog(
        onDismissRequest = { if (!state.isSaving) onDismiss() },
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Box(
            modifier = Modifier
                .widthIn(min = 360.dp, max = 480.dp)
                .fillMaxWidth(0.45f)
                .clip(RoundedCornerShape(16.dp))
                .background(DreamlandForestSurface)
                .padding(0.dp),
        ) {
            Column {
                // Header band
                Box(
                    Modifier
                        .fillMaxWidth()
                        .background(Color(0xFFF39C12).copy(alpha = 0.12f))
                        .padding(horizontal = 22.dp, vertical = 18.dp),
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text("ADVANCE PAYMENT — REFUND OUTCOME", style = MaterialTheme.typography.labelSmall, color = Color(0xFFF39C12), letterSpacing = 1.sp)
                        Text(booking.guestName, style = MaterialTheme.typography.titleLarge, color = DreamlandOnDark, fontWeight = FontWeight.Bold)
                        Text(
                            "Advance paid: ${formatRupees(booking.advancePaidAmount)}",
                            style = MaterialTheme.typography.bodySmall,
                            color = DreamlandMuted,
                        )
                    }
                }

                Column(
                    modifier = Modifier.padding(horizontal = 22.dp, vertical = 18.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp),
                ) {
                    Text(
                        "Record what was decided about the advance payment. This is logged for audit purposes.",
                        style = MaterialTheme.typography.bodySmall,
                        color = DreamlandMuted,
                    )

                    // Status selector
                    val options = listOf(
                        "REFUNDED"  to "Refunded to guest",
                        "FORFEITED" to "Forfeited (no refund)",
                        "PARTIAL"   to "Partially refunded",
                        "PENDING"   to "Pending — decide later",
                    )
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        options.forEach { (status, label) ->
                            val selected = state.refundStatus == status
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(if (selected) DreamlandGold.copy(alpha = 0.12f) else DreamlandForestElevated)
                                    .border(
                                        1.dp,
                                        if (selected) DreamlandGold.copy(alpha = 0.5f) else DreamlandMuted.copy(alpha = 0.15f),
                                        RoundedCornerShape(8.dp),
                                    )
                                    .clickable { onStatusSelected(status) }
                                    .padding(horizontal = 14.dp, vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp),
                            ) {
                                Box(
                                    Modifier
                                        .size(16.dp)
                                        .clip(CircleShape)
                                        .background(if (selected) DreamlandGold else DreamlandMuted.copy(alpha = 0.3f)),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    if (selected) Box(Modifier.size(7.dp).clip(CircleShape).background(DreamlandForestSurface))
                                }
                                Text(label, color = if (selected) DreamlandOnDark else DreamlandMuted, style = MaterialTheme.typography.bodySmall, fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal)
                            }
                        }
                    }

                    // Note field
                    OutlinedTextField(
                        value = state.note,
                        onValueChange = onNoteChanged,
                        label = { Text("Note (optional)", color = DreamlandMuted, fontSize = 12.sp) },
                        placeholder = { Text("e.g. Refunded via UPI on 04 May", color = DreamlandMuted.copy(alpha = 0.5f), fontSize = 12.sp) },
                        modifier = Modifier.fillMaxWidth(),
                        minLines = 2,
                        maxLines = 3,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = DreamlandOnDark,
                            unfocusedTextColor = DreamlandOnDark,
                            focusedBorderColor = DreamlandGold,
                            unfocusedBorderColor = DreamlandMuted.copy(alpha = 0.3f),
                            cursorColor = DreamlandGold,
                        ),
                    )

                    if (state.error != null) {
                        Text(state.error, color = Color(0xFFEF5350), style = MaterialTheme.typography.bodySmall)
                    }

                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Button(
                            onClick = onDismiss,
                            enabled = !state.isSaving,
                            modifier = Modifier.weight(1f).height(44.dp),
                            shape = RoundedCornerShape(8.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = DreamlandForestElevated),
                        ) {
                            Text("Skip for now", color = DreamlandMuted, fontWeight = FontWeight.Medium)
                        }
                        Button(
                            onClick = onConfirm,
                            enabled = !state.isSaving,
                            modifier = Modifier.weight(2f).height(44.dp),
                            shape = RoundedCornerShape(8.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = DreamlandGold),
                        ) {
                            if (state.isSaving) CircularProgressIndicator(Modifier.size(18.dp), color = DreamlandForestSurface, strokeWidth = 2.dp)
                            else Text("Save Outcome", color = DreamlandForestSurface, fontWeight = FontWeight.SemiBold)
                        }
                    }
                }
            }
        }
    }
}
