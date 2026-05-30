@file:OptIn(
    androidx.compose.foundation.layout.ExperimentalLayoutApi::class,
    androidx.compose.foundation.ExperimentalFoundationApi::class,
)

package com.example.dreamland_reception.stays

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Hotel
import androidx.compose.material.icons.filled.KeyboardArrowLeft
import androidx.compose.material.icons.filled.KeyboardArrowRight
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.PopupProperties
import com.example.dreamland_reception.DreamlandForestElevated
import com.example.dreamland_reception.DreamlandForestSurface
import com.example.dreamland_reception.DreamlandGold
import com.example.dreamland_reception.DreamlandMuted
import com.example.dreamland_reception.DreamlandOnDark
import com.example.dreamland_reception.ui.viewmodel.GuestEntry
import com.example.dreamland_reception.ui.viewmodel.StaysViewModel
import com.example.dreamland_reception.ui.viewmodel.WalkInState
import com.example.dreamland_reception.util.dateFromPicker
import com.example.dreamland_reception.util.toMidnightUtc
import java.text.SimpleDateFormat
import java.time.temporal.ChronoUnit
import java.util.Calendar
import java.util.Date
import java.util.Locale

// ── Wizard entry point ────────────────────────────────────────────────────────

@Composable
fun WalkInDialog(state: WalkInState, vm: StaysViewModel) {
    if (!state.isOpen) return

    var currentStep by remember { mutableStateOf(1) }
    // Per-room guest index: instanceId → index in guestEntries (null = unassigned)
    var roomGuestIndex by remember(state.selectedInstanceIds) { mutableStateOf(mapOf<String, Int?>()) }

    Dialog(
        onDismissRequest = vm::closeWalkIn,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth(0.50f)
                .fillMaxHeight(0.90f)
                .clip(RoundedCornerShape(20.dp))
                .background(DreamlandForestSurface),
        ) {
            // ── Fixed header ──────────────────────────────────────────────────
            Column(Modifier.padding(start = 24.dp, end = 24.dp, top = 24.dp, bottom = 0.dp)) {
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = when {
                            state.isBookingMode -> "ADD BOOKING"
                            state.sourceBooking != null -> "CHECK-IN FROM BOOKING"
                            else -> "WALK-IN CHECK-IN"
                        },
                        style = MaterialTheme.typography.labelLarge,
                        color = DreamlandGold,
                        letterSpacing = 2.sp,
                    )
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("Cancel", color = DreamlandMuted, style = MaterialTheme.typography.bodyMedium)
                        Spacer(Modifier.width(4.dp))
                        IconButton(onClick = vm::closeWalkIn, modifier = Modifier.size(28.dp)) {
                            Icon(Icons.Filled.Close, contentDescription = "Close", tint = DreamlandMuted, modifier = Modifier.size(16.dp))
                        }
                    }
                }
                Text(
                    if (state.isBookingMode) "New Booking" else "New Stay",
                    style = MaterialTheme.typography.headlineMedium,
                    color = DreamlandOnDark,
                    fontWeight = FontWeight.Bold,
                )
                Spacer(Modifier.height(16.dp))
                WizardStepIndicator(currentStep, onStepClick = { currentStep = it })
                Spacer(Modifier.height(16.dp))
            }

            HorizontalDivider(color = DreamlandGold.copy(alpha = 0.15f))

            // ── Scrollable step content ───────────────────────────────────────
            Column(
                modifier = Modifier
                    .weight(1f)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 24.dp, vertical = 20.dp),
                verticalArrangement = Arrangement.spacedBy(0.dp),
            ) {
                when (currentStep) {
                    1 -> Step1StayDetails(state, vm)
                    2 -> Step2GuestInfo(state, vm)
                    3 -> Step3AssignRooms(
                        state = state,
                        vm = vm,
                        roomGuestIndex = roomGuestIndex,
                        onAssign = { instanceId, guestIdx ->
                            roomGuestIndex = roomGuestIndex + (instanceId to guestIdx)
                            val g = guestIdx?.let { state.guestEntries.getOrNull(it) }
                            vm.onRoomGuestName(instanceId, g?.name ?: "")
                            vm.onRoomGuestPhone(instanceId, g?.phone ?: "")
                            vm.onRoomGuestIdProof(instanceId, g?.idProofVerified ?: false)
                        },
                        onSameForAll = {
                            roomGuestIndex = state.selectedInstanceIds.associateWith { 0 }
                            vm.onToggleSameGuestForAll(true)
                        },
                        onPerRoom = {
                            roomGuestIndex = mapOf()
                            vm.onToggleSameGuestForAll(false)
                        },
                    )
                    4 -> Step4Confirm(state, vm)
                }
            }

            HorizontalDivider(color = DreamlandGold.copy(alpha = 0.15f))

            // ── Fixed bottom nav ──────────────────────────────────────────────
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp, vertical = 16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (currentStep > 1) {
                    OutlinedButton(
                        onClick = { currentStep-- },
                        shape = RoundedCornerShape(12.dp),
                        border = androidx.compose.foundation.BorderStroke(1.dp, DreamlandGold.copy(alpha = 0.5f)),
                    ) {
                        Icon(Icons.Filled.KeyboardArrowLeft, contentDescription = null, tint = DreamlandGold, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("Back", color = DreamlandGold, fontWeight = FontWeight.SemiBold)
                    }
                } else {
                    Spacer(Modifier.width(1.dp))
                }

                if (currentStep < 4) {
                    Button(
                        onClick = { currentStep++ },
                        colors = ButtonDefaults.buttonColors(containerColor = DreamlandGold),
                        shape = RoundedCornerShape(12.dp),
                    ) {
                        Text("Next", color = Color(0xFF0D1F17), fontWeight = FontWeight.SemiBold)
                        Spacer(Modifier.width(4.dp))
                        Icon(Icons.Filled.KeyboardArrowRight, contentDescription = null, tint = Color(0xFF0D1F17), modifier = Modifier.size(18.dp))
                    }
                } else {
                    // Step 4: submit
                    val primaryIdVerified = if (!state.sameGuestForAllRooms && state.selectedInstanceIds.size > 1) {
                        state.guestEntries.all { it.idProofVerified }
                    } else {
                        state.guestEntries.firstOrNull()?.idProofVerified == true
                    }
                    Button(
                        onClick = { if (state.isBookingMode) vm.submitAsBooking() else vm.requestSubmitWalkIn() },
                        enabled = !state.isSaving && (state.isBookingMode || primaryIdVerified),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = DreamlandGold,
                            disabledContainerColor = DreamlandGold.copy(alpha = 0.35f),
                        ),
                        shape = RoundedCornerShape(12.dp),
                    ) {
                        val bookingCount = state.bookingRoomCountsByCategory.values.sum()
                            .coerceAtLeast(state.selectedInstanceIds.count { id ->
                                (state.selectedInstanceDetails[id]?.categoryId ?: "") !in state.bookingRoomCountsByCategory
                            })
                        Text(
                            when {
                                state.isSaving && state.isBookingMode -> "Adding…"
                                state.isSaving -> "Checking in…"
                                state.isBookingMode && bookingCount > 1 -> "Add $bookingCount Bookings"
                                state.isBookingMode -> "Add Booking"
                                state.selectedInstanceIds.size > 1 -> "Check-in ${state.selectedInstanceIds.size} Rooms"
                                else -> "Check-in Guest"
                            },
                            color = Color(0xFF0D1F17),
                            fontWeight = FontWeight.SemiBold,
                        )
                    }
                }
            }
        }
    }

    state.groupChangeConfirmDialog?.let { d ->
        GroupChangeConfirmDialog(
            dialogState = d,
            requirements = state.categoryRequirements,
            onConfirm = vm::onGroupDeviationConfirmed,
            onDismiss = vm::dismissGroupDeviation,
        )
    }

    state.checkInMismatchConfirm?.let { d ->
        CheckInMismatchConfirmDialog(
            state = d,
            onCheckInLater = { vm.confirmCheckInMismatch(cancelUnmet = false) },
            onCancelUnmet = { vm.confirmCheckInMismatch(cancelUnmet = true) },
            onDismiss = vm::dismissCheckInMismatch,
        )
    }
}

// ── Step indicator ────────────────────────────────────────────────────────────

@Composable
private fun WizardStepIndicator(currentStep: Int, onStepClick: (Int) -> Unit) {
    val steps = listOf("Stay\nDetails", "Guest\nInfo", "Assign\nRooms", "Confirm")
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        steps.forEachIndexed { index, label ->
            val num = index + 1
            val isActive = num == currentStep
            val isDone = num < currentStep

            // Circle + label
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = if (!isActive) Modifier.clickable { onStepClick(num) } else Modifier,
            ) {
                Box(
                    modifier = Modifier
                        .size(32.dp)
                        .clip(CircleShape)
                        .background(
                            when {
                                isActive -> DreamlandGold
                                isDone -> DreamlandForestElevated
                                else -> DreamlandForestElevated.copy(alpha = 0.5f)
                            }
                        ),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        "$num",
                        color = when {
                            isActive -> Color(0xFF0D1F17)
                            isDone -> DreamlandMuted
                            else -> DreamlandMuted.copy(alpha = 0.4f)
                        },
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp,
                    )
                }
                Spacer(Modifier.height(4.dp))
                Text(
                    label,
                    style = MaterialTheme.typography.labelSmall,
                    color = when {
                        isActive -> DreamlandGold
                        isDone -> DreamlandMuted
                        else -> DreamlandMuted.copy(alpha = 0.4f)
                    },
                    fontWeight = if (isActive) FontWeight.SemiBold else FontWeight.Normal,
                    textAlign = TextAlign.Center,
                    fontSize = 10.sp,
                )
            }

            // Connector line (not after last step)
            if (index < steps.size - 1) {
                HorizontalDivider(
                    modifier = Modifier
                        .weight(1f)
                        .padding(bottom = 16.dp, start = 4.dp, end = 4.dp),
                    color = if (isDone) DreamlandMuted.copy(alpha = 0.5f) else DreamlandMuted.copy(alpha = 0.2f),
                )
            }
        }
    }
}

// ── Step 1: Stay Details + Room Selection ─────────────────────────────────────

@Composable
private fun Step1StayDetails(state: WalkInState, vm: StaysViewModel) {
    // CHECK-IN INFORMATION
    WizardSectionLabel("CHECK-IN INFORMATION")
    Spacer(Modifier.height(10.dp))
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        DateSelectorField(modifier = Modifier.weight(1f), label = "Check-in *", date = state.checkInTime, onDateSelected = { it?.let(vm::onWalkInCheckInTime) }, minDate = null)
        DateSelectorField(modifier = Modifier.weight(1f), label = "Expected Check-out *", date = state.expectedCheckOut, onDateSelected = vm::onWalkInExpectedCheckOut, minDate = state.checkInTime)
    }

    Spacer(Modifier.height(20.dp))

    // NUMBER OF GUESTS
    WizardSectionLabel("NUMBER OF GUESTS")
    Spacer(Modifier.height(10.dp))
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Box(Modifier.weight(1f)) {
            CounterField("Adults *", state.adults, onIncrement = { vm.onWalkInAdults(state.adults + 1) }, onDecrement = { vm.onWalkInAdults(state.adults - 1) })
        }
        Box(Modifier.weight(1f)) {
            CounterField("Children", state.children, onIncrement = { vm.onWalkInChildren(state.children + 1) }, onDecrement = { vm.onWalkInChildren(state.children - 1) })
        }
    }

    Spacer(Modifier.height(20.dp))

    // SELECT ROOMS
    WizardSectionLabel("SELECT ROOMS")
    Spacer(Modifier.height(10.dp))

    // Always show ALL loaded categories so none disappear due to filtering.
    // Overlay availability counts from categoryAvailability when dates are set.
    // Categories with 0 available (not in map) show "0 avail." as a hint.
    val datesReady = state.expectedCheckOut != null
    val availChecked = datesReady && state.categoryAvailability.isNotEmpty()
    DreamlandDropdown(
        modifier = Modifier.fillMaxWidth(),
        label = "Room Category *",
        selectedText = if (state.selectedCategoryName.isBlank()) "Select category"
        else {
            val effectivePrice = state.categoryPrices[state.selectedCategoryId]
                ?: state.categories.find { it.id == state.selectedCategoryId }?.pricePerNight ?: 0.0
            val count = state.categoryAvailability[state.selectedCategoryId]
            buildString {
                append(state.selectedCategoryName)
                append("  ·  ₹${"%,.0f".format(effectivePrice)}/night")
                // Only show avail. when we have a real count; skip for categories not in the map
                // (e.g. non-required categories in group check-in mode that return null)
                if (availChecked && count != null) append("  ·  $count avail.")
            }
        },
        options = state.categories.map { room ->
            val price = state.categoryPrices[room.id] ?: room.pricePerNight
            val label = buildString {
                append("${room.type}  ·  ₹${"%,.0f".format(price)}/night")
                if (availChecked) {
                    val count = state.categoryAvailability[room.id]
                    if (count != null) append("  ·  $count avail.")
                }
            }
            room.id to label
        },
        onSelected = vm::onCategorySelected,
    )

    if (state.selectedCategoryId.isNotBlank() && state.expectedCheckOut != null) {
        Spacer(Modifier.height(10.dp))

        if (state.isBookingMode) {
            val catId = state.selectedCategoryId
            val currentCount = state.bookingRoomCountsByCategory[catId] ?: 0
            val maxAllowed = state.availableCount.takeIf { it > 0 } ?: Int.MAX_VALUE

            // Per-category room count counter
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(Modifier.weight(1f)) {
                    CounterField(
                        label = "Number of Rooms",
                        value = currentCount,
                        onIncrement = { if (currentCount < maxAllowed) vm.onBookingRoomCountForCategory(catId, currentCount + 1) },
                        onDecrement = { vm.onBookingRoomCountForCategory(catId, currentCount - 1) },
                    )
                }
            }

            // Optional: specific room chip selection
            if (state.selectableInstances.isNotEmpty() || state.cleaningInstances.isNotEmpty() || state.dueOutInstances.isNotEmpty()) {
                Spacer(Modifier.height(10.dp))
                Text(
                    "Optional: pick specific rooms",
                    style = MaterialTheme.typography.labelSmall,
                    color = DreamlandMuted,
                    letterSpacing = 0.5.sp,
                )
                Spacer(Modifier.height(6.dp))
                val specificInCat = state.selectedInstanceIds.count { id ->
                    state.selectedInstanceDetails[id]?.categoryId == catId
                }
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    state.selectableInstances.forEach { inst ->
                        val isSelected = inst.id in state.selectedInstanceIds
                        RoomChip(
                            number = inst.roomNumber,
                            isSelected = isSelected,
                            isCleaning = false,
                            isDisabled = false,
                            onClick = {
                                vm.onInstanceToggled(inst.id)
                                // Keep counter at least as high as chip count
                                val newSpecific = if (isSelected) specificInCat - 1 else specificInCat + 1
                                if (newSpecific > currentCount) vm.onBookingRoomCountForCategory(catId, newSpecific)
                            },
                        )
                    }
                    state.cleaningInstances.forEach { inst ->
                        RoomChip(number = inst.roomNumber, isSelected = false, isCleaning = true, isDisabled = true, onClick = {})
                    }
                    state.dueOutInstances.forEach { inst ->
                        RoomChip(number = inst.roomNumber, isSelected = false, isCleaning = false, isDueOut = true, isDisabled = true, onClick = {})
                    }
                }
            }

            // Booking summary: all categories with room counts
            val summaryEntries = buildList {
                state.bookingRoomCountsByCategory.forEach { (cId, count) ->
                    val cName = state.categories.find { it.id == cId }?.type
                        ?: state.selectedInstanceDetails.values.find { it.categoryId == cId }?.categoryName
                        ?: cId
                    val specificRooms = state.selectedInstanceIds
                        .filter { state.selectedInstanceDetails[it]?.categoryId == cId }
                        .mapNotNull { state.selectedInstanceDetails[it]?.roomNumber }
                    add(Triple(cId, count, specificRooms))
                }
                // Categories only in specific chips (no counter entry)
                state.selectedInstanceDetails.values.groupBy { it.categoryId }.forEach { (cId, insts) ->
                    if (cId !in state.bookingRoomCountsByCategory) {
                        val cName = insts.first().categoryName
                        val nums = insts.mapNotNull { it.roomNumber.ifBlank { null } }
                        add(Triple(cId, nums.size, nums))
                    }
                }
            }
            if (summaryEntries.size > 1 || (summaryEntries.size == 1 && summaryEntries.first().third.isNotEmpty())) {
                Spacer(Modifier.height(14.dp))
                WizardSectionLabel("BOOKING SUMMARY")
                Spacer(Modifier.height(6.dp))
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    summaryEntries.forEach { (cId, count, rooms) ->
                        val cName = state.categories.find { it.id == cId }?.type ?: cId
                        val roomLabel = if (rooms.isEmpty()) "$count room${if (count != 1) "s" else ""}"
                                        else "${rooms.size} room${if (rooms.size != 1) "s" else ""}: ${rooms.joinToString(", ")}"
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text(cName, color = DreamlandOnDark, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.SemiBold)
                            Text(roomLabel, color = DreamlandGold, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Medium)
                        }
                    }
                }
            }
        } else {
        // Walk-in mode: SELECTED ROOMS shown above chip grid, then chip grid for current category

        // SELECTED ROOMS — shown above the chip grid so it stays visible when browsing any category
        if (state.selectedInstanceIds.isNotEmpty()) {
            WizardSectionLabel("SELECTED ROOMS")
            Spacer(Modifier.height(8.dp))
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                state.selectedInstanceIds.forEach { id ->
                    val inst = state.selectedInstanceDetails[id]
                    if (inst != null) {
                        SelectedRoomTag(
                            categoryName = inst.categoryName,
                            roomNumber = inst.roomNumber,
                            onRemove = { vm.onInstanceToggled(id) },
                        )
                    }
                }
            }
            Spacer(Modifier.height(14.dp))
        }

        // Chip grid for the currently viewed category
        val currentCatIds = (state.selectableInstances + state.cleaningInstances).map { it.id }.toSet()
        val selectedInCat = state.selectedInstanceIds.count { it in currentCatIds }
        if (state.selectableInstances.isNotEmpty() || state.cleaningInstances.isNotEmpty() || state.dueOutInstances.isNotEmpty()) {
            val availCount = state.availableCount
            val atMax = selectedInCat >= availCount

            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                state.selectableInstances.forEach { inst ->
                    val isSelected = inst.id in state.selectedInstanceIds
                    val disabled = atMax && !isSelected
                    RoomChip(
                        number = inst.roomNumber,
                        isSelected = isSelected,
                        isCleaning = false,
                        isDisabled = disabled,
                        onClick = { vm.onInstanceToggled(inst.id) },
                    )
                }
                state.cleaningInstances.forEach { inst ->
                    RoomChip(
                        number = inst.roomNumber,
                        isSelected = false,
                        isCleaning = true,
                        isDisabled = true,
                        onClick = {},
                    )
                }
                state.dueOutInstances.forEach { inst ->
                    RoomChip(
                        number = inst.roomNumber,
                        isSelected = false,
                        isCleaning = false,
                        isDueOut = true,
                        isDisabled = true,
                        onClick = {},
                    )
                }
            }
            Spacer(Modifier.height(6.dp))
            Text(
                "Switch category above to add rooms from another type",
                style = MaterialTheme.typography.bodySmall,
                color = DreamlandMuted.copy(alpha = 0.6f),
            )
        }
        } // end walk-in mode block
    } else if (state.selectedCategoryId.isNotBlank() && state.expectedCheckOut == null) {
        Spacer(Modifier.height(6.dp))
        Text("Set check-out date to see available rooms", style = MaterialTheme.typography.bodySmall, color = DreamlandMuted)
    }

    // Group requirements
    if (state.isGroupCheckIn && state.categoryRequirements.isNotEmpty()) {
        Spacer(Modifier.height(12.dp))
        CategoryRequirementsPanel(state)
    }

    if (state.error != null) {
        Spacer(Modifier.height(8.dp))
        Text(state.error, color = Color(0xFFEF5350), style = MaterialTheme.typography.bodySmall)
    }
}

// ── Step 2: Guest Information ─────────────────────────────────────────────────

@Composable
private fun Step2GuestInfo(state: WalkInState, vm: StaysViewModel) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Icon(Icons.Filled.Hotel, contentDescription = null, tint = DreamlandGold, modifier = Modifier.size(18.dp))
        Text(
            "GUEST INFORMATION (${state.adults} ADULT${if (state.adults != 1) "S" else ""})",
            style = MaterialTheme.typography.labelLarge,
            color = DreamlandGold,
            letterSpacing = 1.sp,
        )
    }
    Spacer(Modifier.height(12.dp))

    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        state.guestEntries.forEachIndexed { index, guest ->
            GuestWizardCard(
                index = index,
                entry = guest,
                onNameChange = { vm.onGuestName(index, it) },
                onPhoneChange = { vm.onGuestPhone(index, it) },
                onIdProofChange = { vm.onGuestIdProof(index, it) },
            )
        }
    }
}

// ── Step 3: Assign Guests to Rooms ────────────────────────────────────────────

@Composable
private fun Step3AssignRooms(
    state: WalkInState,
    vm: StaysViewModel,
    roomGuestIndex: Map<String, Int?>,
    onAssign: (instanceId: String, guestIdx: Int?) -> Unit,
    onSameForAll: () -> Unit,
    onPerRoom: () -> Unit,
) {
    val orderedIds = state.selectedInstanceDetails.keys.toList()

    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text("ASSIGN GUESTS TO ROOMS", style = MaterialTheme.typography.labelLarge, color = DreamlandGold, letterSpacing = 1.sp)
        if (orderedIds.size > 1) {
            OutlinedButton(
                onClick = if (state.sameGuestForAllRooms) onPerRoom else onSameForAll,
                shape = RoundedCornerShape(8.dp),
                border = androidx.compose.foundation.BorderStroke(1.dp, DreamlandGold.copy(alpha = 0.5f)),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 12.dp, vertical = 6.dp),
            ) {
                Text(
                    if (state.sameGuestForAllRooms) "Per room" else "Same guest for all",
                    color = DreamlandGold,
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.SemiBold,
                )
            }
        }
    }

    Spacer(Modifier.height(12.dp))

    if (orderedIds.isEmpty()) {
        Text("No rooms selected. Go back to Step 1 to select rooms.", color = DreamlandMuted, style = MaterialTheme.typography.bodySmall)
        return
    }

    // Info box showing selected room numbers
    val roomNumbers = orderedIds.mapNotNull { state.selectedInstanceDetails[it]?.roomNumber }
    Box(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(DreamlandForestElevated)
            .padding(horizontal = 14.dp, vertical = 10.dp),
    ) {
        Text(
            "You have selected ${orderedIds.size} room${if (orderedIds.size != 1) "s" else ""}: ${roomNumbers.joinToString(", ")}",
            color = DreamlandMuted,
            style = MaterialTheme.typography.bodySmall,
        )
    }

    Spacer(Modifier.height(12.dp))

    if (state.sameGuestForAllRooms) {
        // Shared mode — show all rooms with the primary guest
        val primaryName = state.guestEntries.firstOrNull()?.name?.ifBlank { "Primary Guest" } ?: "Primary Guest"
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            orderedIds.forEach { instanceId ->
                val inst = state.selectedInstanceDetails[instanceId]
                RoomGuestRow(
                    roomNumber = inst?.roomNumber ?: "?",
                    categoryName = inst?.categoryName ?: "",
                    guestLabel = "Primary: $primaryName",
                    isShared = true,
                    expanded = false,
                    onDropdownClick = {},
                    guestOptions = emptyList(),
                    onGuestSelected = {},
                )
            }
        }
    } else {
        // Per-room assignment
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            orderedIds.forEach { instanceId ->
                val inst = state.selectedInstanceDetails[instanceId]
                val selectedIdx = roomGuestIndex[instanceId]
                val label = when (selectedIdx) {
                    null -> "No guest assigned"
                    0 -> "Primary: ${state.guestEntries[0].name.ifBlank { "Primary Guest" }}"
                    else -> "Guest ${selectedIdx + 1}: ${state.guestEntries.getOrNull(selectedIdx)?.name?.ifBlank { "Guest ${selectedIdx + 1}" } ?: "Guest ${selectedIdx + 1}"}"
                }
                val options = buildList {
                    add(null to "No guest assigned")
                    state.guestEntries.forEachIndexed { i, g ->
                        val name = g.name.ifBlank { if (i == 0) "Primary Guest" else "Guest ${i + 1}" }
                        add(i to if (i == 0) "Primary: $name" else "Guest ${i + 1}: $name")
                    }
                }

                var dropdownExpanded by remember { mutableStateOf(false) }
                RoomGuestRow(
                    roomNumber = inst?.roomNumber ?: "?",
                    categoryName = inst?.categoryName ?: "",
                    guestLabel = label,
                    isShared = false,
                    expanded = dropdownExpanded,
                    onDropdownClick = { dropdownExpanded = !dropdownExpanded },
                    guestOptions = options,
                    onGuestSelected = { idx ->
                        onAssign(instanceId, idx)
                        dropdownExpanded = false
                    },
                )
            }
        }
    }
}

@Composable
private fun RoomGuestRow(
    roomNumber: String,
    categoryName: String,
    guestLabel: String,
    isShared: Boolean,
    expanded: Boolean,
    onDropdownClick: () -> Unit,
    guestOptions: List<Pair<Int?, String>>,
    onGuestSelected: (Int?) -> Unit,
) {
    var triggerSize by remember { mutableStateOf(IntSize.Zero) }
    val density = LocalDensity.current

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(DreamlandForestElevated)
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Icon(Icons.Filled.Hotel, contentDescription = null, tint = DreamlandGold, modifier = Modifier.size(18.dp))
        Text(
            "Room $roomNumber${if (categoryName.isNotBlank()) " · $categoryName" else ""}",
            color = DreamlandOnDark,
            fontWeight = FontWeight.SemiBold,
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.width(140.dp),
        )
        Box(
            modifier = Modifier
                .weight(1f)
                .onSizeChanged { triggerSize = it }
                .clip(RoundedCornerShape(8.dp))
                .border(1.dp, if (isShared) DreamlandGold.copy(alpha = 0.2f) else DreamlandMuted.copy(alpha = 0.35f), RoundedCornerShape(8.dp))
                .background(DreamlandForestSurface)
                .then(if (!isShared) Modifier.clickable { onDropdownClick() } else Modifier)
                .padding(horizontal = 12.dp, vertical = 8.dp),
        ) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text(guestLabel, color = DreamlandOnDark, style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f))
                if (!isShared) {
                    Text("▾", color = DreamlandMuted, fontSize = 12.sp)
                }
            }
            if (!isShared) {
                DropdownMenu(
                    expanded = expanded,
                    onDismissRequest = onDropdownClick,
                    modifier = Modifier.width(with(density) { triggerSize.width.toDp() }),
                ) {
                    guestOptions.forEach { (idx, label) ->
                        DropdownMenuItem(
                            modifier = Modifier.fillMaxWidth(),
                            text = { Text(label, color = DreamlandOnDark, style = MaterialTheme.typography.bodyMedium) },
                            onClick = { onGuestSelected(idx) },
                        )
                        HorizontalDivider(color = DreamlandGold.copy(alpha = 0.08f))
                    }
                }
            }
        }
    }
}

// ── Step 4: Confirm ───────────────────────────────────────────────────────────

@Composable
private fun Step4Confirm(state: WalkInState, vm: StaysViewModel) {
    val fmt = SimpleDateFormat("dd MMM yyyy", Locale.getDefault())
    val roomNumbers = state.selectedInstanceDetails.values.mapNotNull { it.roomNumber.ifBlank { null } }
    val nights = if (state.expectedCheckOut != null)
        ChronoUnit.DAYS.between(state.checkInTime.toMidnightUtc().toInstant(), state.expectedCheckOut.toMidnightUtc().toInstant()).coerceAtLeast(1)
    else 1L

    // Summary card
    Card(
        colors = CardDefaults.cardColors(containerColor = DreamlandForestElevated),
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            ConfirmRow("Check-in", fmt.format(state.checkInTime))
            HorizontalDivider(color = DreamlandMuted.copy(alpha = 0.15f))
            ConfirmRow("Check-out", if (state.expectedCheckOut != null) fmt.format(state.expectedCheckOut) else "—")
            HorizontalDivider(color = DreamlandMuted.copy(alpha = 0.15f))
            val guestLabel = buildString {
                append("${state.adults} Adult${if (state.adults != 1) "s" else ""}")
                if (state.children > 0) append(" + ${state.children} Child${if (state.children != 1) "ren" else ""}")
            }
            ConfirmRow("Guests", guestLabel)
            if (roomNumbers.isNotEmpty()) {
                HorizontalDivider(color = DreamlandMuted.copy(alpha = 0.15f))
                ConfirmRow("Rooms", roomNumbers.joinToString(", "))
            }
        }
    }

    Spacer(Modifier.height(20.dp))

    // Options
    WizardSectionLabel("OPTIONS")
    Spacer(Modifier.height(8.dp))
    Card(
        colors = CardDefaults.cardColors(containerColor = DreamlandForestElevated),
        shape = RoundedCornerShape(10.dp),
    ) {
        Column(Modifier.padding(horizontal = 14.dp, vertical = 10.dp)) {
            val breakfastPriceLabel = if (state.selectedCategoryBreakfastPrice > 0)
                "₹${state.selectedCategoryBreakfastPrice.toLong()} per person per night"
            else "Price set per room category"
            OptionToggle("Breakfast Included", breakfastPriceLabel, state.breakfast, vm::onWalkInBreakfast)
        }
    }

    Spacer(Modifier.height(20.dp))

    // Pricing preview
    WizardSectionLabel("PRICING PREVIEW")
    Spacer(Modifier.height(8.dp))
    PricingPreviewCard(state)

    Spacer(Modifier.height(20.dp))

    // Advance payment (walk-in only)
    if (!state.isBookingMode) {
        WizardSectionLabel("ADVANCE PAYMENT")
        Spacer(Modifier.height(8.dp))
        OutlinedTextField(
            value = state.advancePayment,
            onValueChange = vm::onWalkInAdvancePayment,
            label = { Text("Advance Amount (₹)", color = DreamlandMuted, fontSize = 13.sp) },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            colors = OutlinedTextFieldDefaults.colors(
                focusedTextColor = DreamlandOnDark, unfocusedTextColor = DreamlandOnDark,
                focusedBorderColor = DreamlandGold, unfocusedBorderColor = DreamlandMuted.copy(alpha = 0.4f),
                cursorColor = DreamlandGold,
            ),
        )
        val advance = state.advancePayment.toDoubleOrNull() ?: 0.0
        if (advance > 0 || state.selectedInstanceIds.isNotEmpty()) {
            val roomTotal = state.selectedInstanceDetails.values.groupBy { it.categoryId }.entries.sumOf { (catId, insts) ->
                val price = state.categoryPrices[catId] ?: state.categories.find { it.id == catId }?.pricePerNight ?: 0.0
                price * nights * insts.size
            }.let { if (it == 0.0) (state.categories.find { it.id == state.selectedCategoryId }?.pricePerNight ?: 0.0) * nights * state.selectedInstanceIds.size.coerceAtLeast(1) else it }
            val bfCharge = if (state.breakfast) state.selectedCategoryBreakfastPrice * state.adults * nights else 0.0
            val total = roomTotal + bfCharge
            val pending = (total - advance).coerceAtLeast(0.0)
            Spacer(Modifier.height(6.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("Remaining at check-out:", color = DreamlandMuted, style = MaterialTheme.typography.bodySmall)
                Text("₹${pending.toLong()}", color = if (pending > 0) Color(0xFFFFC107) else Color(0xFF4CAF50), fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.bodySmall)
            }
        }
        Spacer(Modifier.height(20.dp))
    }

    // Source (booking mode only) — dropdown from bookingSources collection
    if (state.isBookingMode) {
        WizardSectionLabel("BOOKING SOURCE")
        Spacer(Modifier.height(8.dp))
        BookingSourceDropdown(
            sources = state.bookingSources,
            selectedName = state.source,
            onSourceSelected = { id, name -> vm.onWalkInSourceSelected(id, name) },
            onAddSource = { name -> vm.addWalkInBookingSource(name) },
            onTextChanged = vm::onWalkInSource,
        )
        Spacer(Modifier.height(20.dp))
    }

    if (state.error != null) {
        Text(state.error, color = Color(0xFFEF5350), style = MaterialTheme.typography.bodySmall)
        Spacer(Modifier.height(8.dp))
    }

    // ID proof warning
    val primaryIdVerified = if (!state.sameGuestForAllRooms && state.selectedInstanceIds.size > 1) {
        state.guestEntries.all { it.idProofVerified }
    } else {
        state.guestEntries.firstOrNull()?.idProofVerified == true
    }
    if (!state.isBookingMode && !primaryIdVerified) {
        Row(
            modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp))
                .background(Color(0xFFF39C12).copy(alpha = 0.12f)).padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text("⚠", color = Color(0xFFF39C12), fontSize = 14.sp)
            Text("Verify primary guest ID proof to enable check-in", color = Color(0xFFF39C12), style = MaterialTheme.typography.bodySmall)
        }
    }
}

@Composable
private fun ConfirmRow(label: String, value: String) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, color = DreamlandMuted, style = MaterialTheme.typography.bodyMedium)
        Text(value, color = DreamlandOnDark, fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.bodyMedium)
    }
}

// ── Selected-room tag (pill with category · room# and × to remove) ───────────

@Composable
private fun SelectedRoomTag(categoryName: String, roomNumber: String, onRemove: () -> Unit) {
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .background(DreamlandGold.copy(alpha = 0.12f))
            .border(1.dp, DreamlandGold.copy(alpha = 0.4f), RoundedCornerShape(8.dp))
            .padding(horizontal = 10.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Text(
            if (categoryName.isNotBlank()) "$categoryName · $roomNumber" else "Room $roomNumber",
            color = DreamlandGold,
            fontWeight = FontWeight.SemiBold,
            style = MaterialTheme.typography.bodySmall,
        )
        Icon(
            Icons.Filled.Close,
            contentDescription = "Remove",
            tint = DreamlandGold.copy(alpha = 0.7f),
            modifier = Modifier.size(14.dp).clickable { onRemove() },
        )
    }
}

// ── Room chip (square, just number) ──────────────────────────────────────────

@Composable
private fun RoomChip(
    number: String,
    isSelected: Boolean,
    isCleaning: Boolean,
    isDisabled: Boolean,
    isDueOut: Boolean = false,
    onClick: () -> Unit,
) {
    Box(
        modifier = Modifier
            .size(62.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(
                when {
                    isSelected -> DreamlandGold
                    isDueOut -> DreamlandForestElevated.copy(alpha = 0.6f)
                    isDisabled && !isCleaning -> DreamlandForestElevated.copy(alpha = 0.4f)
                    else -> DreamlandForestElevated
                }
            )
            .border(
                1.dp,
                when {
                    isSelected -> DreamlandGold
                    isDueOut -> Color(0xFFBB8800).copy(alpha = 0.5f)
                    isCleaning -> Color(0xFFFF9800).copy(alpha = 0.4f)
                    else -> DreamlandGold.copy(alpha = 0.15f)
                },
                RoundedCornerShape(12.dp),
            )
            .then(if (!isDisabled || isSelected) Modifier.clickable { onClick() } else Modifier),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = number,
            color = when {
                isSelected -> Color(0xFF0D1F17)
                isDueOut -> Color(0xFFBB8800).copy(alpha = 0.85f)
                isDisabled -> DreamlandMuted.copy(alpha = 0.35f)
                isCleaning -> Color(0xFFFF9800).copy(alpha = 0.7f)
                else -> DreamlandOnDark
            },
            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
            fontSize = 16.sp,
        )
        // Cleaning badge
        if (isCleaning) {
            Text(
                "✦",
                color = Color(0xFFFF9800).copy(alpha = 0.8f),
                fontSize = 8.sp,
                modifier = Modifier.align(Alignment.TopEnd).padding(4.dp),
            )
        }
        // Due-out badge
        if (isDueOut) {
            Text(
                "↑",
                color = Color(0xFFBB8800).copy(alpha = 0.9f),
                fontSize = 8.sp,
                modifier = Modifier.align(Alignment.TopEnd).padding(4.dp),
            )
        }
    }
}

// ── Guest wizard card (step 2, all guests with name+phone+ID) ─────────────────

@Composable
private fun GuestWizardCard(
    index: Int,
    entry: GuestEntry,
    onNameChange: (String) -> Unit,
    onPhoneChange: (String) -> Unit,
    onIdProofChange: (Boolean) -> Unit,
) {
    val isPrimary = index == 0
    Card(
        colors = CardDefaults.cardColors(containerColor = DreamlandForestElevated),
        shape = RoundedCornerShape(12.dp),
    ) {
        Column(Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            // Number circle + label
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Box(
                    modifier = Modifier
                        .size(28.dp)
                        .clip(CircleShape)
                        .background(if (isPrimary) DreamlandGold else Color.Transparent)
                        .border(1.dp, if (isPrimary) DreamlandGold else DreamlandMuted.copy(alpha = 0.5f), CircleShape),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        "${index + 1}",
                        color = if (isPrimary) Color(0xFF0D1F17) else DreamlandMuted,
                        fontWeight = FontWeight.Bold,
                        fontSize = 12.sp,
                    )
                }
                Text(
                    if (isPrimary) "Primary Guest" else "Guest ${index + 1}",
                    color = DreamlandGold,
                    fontWeight = FontWeight.SemiBold,
                    style = MaterialTheme.typography.bodyMedium,
                )
            }

            // Name + phone (all guests get both fields)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                DreamlandTextField(modifier = Modifier.weight(1f), value = entry.name, onValueChange = onNameChange, label = "Full Name *")
                DreamlandTextField(modifier = Modifier.weight(1f), value = entry.phone, onValueChange = { onPhoneChange(it.filter(Char::isDigit).take(10)) }, label = "Phone Number", keyboardType = KeyboardType.Phone)
            }

            // ID proof toggle
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text("ID Proof Verified", color = DreamlandOnDark, style = MaterialTheme.typography.bodyMedium)
                Switch(
                    checked = entry.idProofVerified,
                    onCheckedChange = onIdProofChange,
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = Color(0xFF0D1F17),
                        checkedTrackColor = Color(0xFF4CAF50),
                        uncheckedThumbColor = DreamlandMuted,
                        uncheckedTrackColor = DreamlandForestElevated,
                    ),
                )
            }
        }
    }
}

// ── Wizard section label (uppercase, gold, no divider) ────────────────────────

@Composable
private fun WizardSectionLabel(text: String) {
    Text(text, style = MaterialTheme.typography.labelLarge, color = DreamlandGold, letterSpacing = 1.5.sp, fontSize = 11.sp)
}

// ── Category requirements panel ───────────────────────────────────────────────

@Composable
private fun CategoryRequirementsPanel(state: WalkInState) {
    val reqCatIds = state.categoryRequirements.map { it.categoryId }.toSet()
    val selectionsByCat = state.selectedInstanceDetails.values.groupBy { it.categoryId }

    // Categories selected beyond the original requirements
    val extraSelections = selectionsByCat.filter { (catId, _) -> catId !in reqCatIds }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(DreamlandForestElevated)
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Text("BOOKING REQUIREMENTS", style = MaterialTheme.typography.labelSmall, color = DreamlandGold, letterSpacing = 1.sp)

        // Original requirements with live selection status
        state.categoryRequirements.forEach { req ->
            val selected = selectionsByCat[req.categoryId]?.size ?: 0
            val (statusColor, icon) = when {
                selected == req.count -> Color(0xFF4CAF50) to "✓"
                selected < req.count -> Color(0xFFFF9800) to "○"
                else -> Color(0xFFEF5350) to "!"
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(icon, color = statusColor, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                    Text("${req.categoryName} × ${req.count}", color = DreamlandOnDark, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Medium)
                }
                Text("$selected / ${req.count} selected", color = statusColor, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.SemiBold)
            }
        }

        // Extra categories selected beyond original requirements
        extraSelections.forEach { (catId, insts) ->
            val catName = insts.firstOrNull()?.categoryName?.ifBlank { null }
                ?: state.categories.find { it.id == catId }?.type
                ?: catId
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text("+", color = DreamlandGold, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                    Text("$catName × ${insts.size}", color = DreamlandOnDark, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Medium)
                }
                Text("${insts.size} added", color = DreamlandGold, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.SemiBold)
            }
        }
    }
}

// ── Group deviation confirmation dialog ───────────────────────────────────────

@Composable
private fun GroupChangeConfirmDialog(
    dialogState: com.example.dreamland_reception.ui.viewmodel.GroupChangeConfirmDialogState,
    requirements: List<com.example.dreamland_reception.ui.viewmodel.CategoryRequirement>,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Box(
            modifier = Modifier
                .widthIn(min = 340.dp, max = 460.dp)
                .fillMaxWidth(0.42f)
                .clip(RoundedCornerShape(16.dp))
                .background(DreamlandForestSurface)
                .padding(24.dp),
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                Text("DEVIATION FROM ORIGINAL BOOKING", style = MaterialTheme.typography.labelSmall, color = Color(0xFFFF9800), letterSpacing = 1.sp)
                Text("You're changing the room selection beyond what was originally booked.", color = DreamlandOnDark, style = MaterialTheme.typography.bodyMedium)
                val displayRequirements = dialogState.originalRequirements.ifEmpty { requirements }
                if (displayRequirements.isNotEmpty()) {
                    Column(modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp)).background(DreamlandForestElevated).padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text("Original booking:", style = MaterialTheme.typography.labelSmall, color = DreamlandMuted)
                        Text(displayRequirements.joinToString(" · ") { r -> if (r.count > 1) "${r.count}× ${r.categoryName}" else r.categoryName }, color = DreamlandOnDark, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Medium)
                    }
                }
                if (dialogState.availabilityMap.isNotEmpty()) {
                    Column(modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp)).background(DreamlandForestElevated).padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text("Available rooms:", style = MaterialTheme.typography.labelSmall, color = DreamlandMuted)
                        dialogState.availabilityMap.forEach { (catId, count) ->
                            val catName = dialogState.categoryNames[catId] ?: catId
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text(catName, color = DreamlandOnDark, style = MaterialTheme.typography.bodySmall)
                                Text("$count available", color = if (count > 0) Color(0xFF4CAF50) else Color(0xFFEF5350), style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.SemiBold)
                            }
                        }
                    }
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Button(onClick = onDismiss, modifier = Modifier.weight(1f).height(44.dp), shape = RoundedCornerShape(8.dp), colors = ButtonDefaults.buttonColors(containerColor = DreamlandForestElevated)) {
                        Text("Cancel Change", color = DreamlandMuted, fontWeight = FontWeight.Medium, maxLines = 1)
                    }
                    Button(onClick = onConfirm, modifier = Modifier.weight(1f).height(44.dp), shape = RoundedCornerShape(8.dp), colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFF9800))) {
                        Text("Confirm", color = Color.White, fontWeight = FontWeight.SemiBold)
                    }
                }
            }
        }
    }
}

// ── Check-in mismatch confirmation dialog ─────────────────────────────────────

@Composable
private fun CheckInMismatchConfirmDialog(
    state: com.example.dreamland_reception.ui.viewmodel.CheckInMismatchConfirmState,
    onCheckInLater: () -> Unit,
    onCancelUnmet: () -> Unit,
    onDismiss: () -> Unit,
) {
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Box(
            modifier = Modifier
                .widthIn(min = 360.dp, max = 500.dp)
                .fillMaxWidth(0.46f)
                .clip(RoundedCornerShape(16.dp))
                .background(DreamlandForestSurface)
                .padding(24.dp),
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                // Header
                Text(
                    "ROOM SELECTION MISMATCH",
                    style = MaterialTheme.typography.labelSmall,
                    color = Color(0xFFFF9800),
                    letterSpacing = 1.sp,
                )
                Text(
                    "The selected rooms differ from the original booking requirements.",
                    color = DreamlandOnDark,
                    style = MaterialTheme.typography.bodyMedium,
                )

                // Side-by-side comparison
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    // Original booking
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(8.dp))
                            .background(DreamlandForestElevated)
                            .padding(10.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        Text("Original booking", style = MaterialTheme.typography.labelSmall, color = DreamlandMuted)
                        state.originalLines.forEach { line ->
                            Text(
                                "${line.count}× ${line.categoryName}",
                                color = DreamlandOnDark,
                                style = MaterialTheme.typography.bodySmall,
                                fontWeight = FontWeight.Medium,
                            )
                        }
                    }
                    // Current selection
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(8.dp))
                            .background(DreamlandForestElevated)
                            .padding(10.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        Text("Current selection", style = MaterialTheme.typography.labelSmall, color = DreamlandMuted)
                        state.currentLines.forEach { line ->
                            Text(
                                "${line.count}× ${line.categoryName}",
                                color = DreamlandGold,
                                style = MaterialTheme.typography.bodySmall,
                                fontWeight = FontWeight.Medium,
                            )
                        }
                    }
                }

                // Question about unmet bookings
                if (state.unmetBookingCount > 0) {
                    Text(
                        "${state.unmetBookingCount} booking${if (state.unmetBookingCount > 1) "s" else ""} from this group will not be checked in now. What should happen to ${if (state.unmetBookingCount > 1) "them" else "it"}?",
                        color = DreamlandMuted,
                        style = MaterialTheme.typography.bodySmall,
                    )
                }

                // Action buttons
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (state.unmetBookingCount > 0) {
                        Button(
                            onClick = onCheckInLater,
                            modifier = Modifier.fillMaxWidth().height(44.dp),
                            shape = RoundedCornerShape(8.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = DreamlandGold),
                        ) {
                            Text(
                                "Check in remaining rooms later",
                                color = Color(0xFF0D1F17),
                                fontWeight = FontWeight.SemiBold,
                                maxLines = 1,
                            )
                        }
                        Button(
                            onClick = onCancelUnmet,
                            modifier = Modifier.fillMaxWidth().height(44.dp),
                            shape = RoundedCornerShape(8.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFEF5350).copy(alpha = 0.15f)),
                        ) {
                            Text(
                                "Cancel unmet bookings & proceed",
                                color = Color(0xFFEF5350),
                                fontWeight = FontWeight.SemiBold,
                                maxLines = 1,
                            )
                        }
                    } else {
                        // Only extras (no unmet) — single confirm button
                        Button(
                            onClick = onCheckInLater,
                            modifier = Modifier.fillMaxWidth().height(44.dp),
                            shape = RoundedCornerShape(8.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = DreamlandGold),
                        ) {
                            Text("Confirm Check-in", color = Color(0xFF0D1F17), fontWeight = FontWeight.SemiBold)
                        }
                    }
                    Button(
                        onClick = onDismiss,
                        modifier = Modifier.fillMaxWidth().height(44.dp),
                        shape = RoundedCornerShape(8.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = DreamlandForestElevated),
                    ) {
                        Text("Go Back", color = DreamlandMuted, fontWeight = FontWeight.Medium)
                    }
                }
            }
        }
    }
}

// ── GuestEntryRow (kept for external use by other screens) ────────────────────

@Composable
internal fun GuestEntryRow(
    index: Int,
    entry: GuestEntry,
    isPrimary: Boolean,
    primaryLabel: String = "Primary Guest",
    onNameChange: (String) -> Unit,
    onPhoneChange: (String) -> Unit,
    onIdProofChange: (Boolean) -> Unit,
    onRemove: () -> Unit,
    canRemove: Boolean,
) {
    Card(colors = CardDefaults.cardColors(containerColor = DreamlandForestElevated), shape = RoundedCornerShape(10.dp)) {
        Column(Modifier.fillMaxWidth().padding(12.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text(text = if (isPrimary) primaryLabel else "Guest ${index + 1}", style = MaterialTheme.typography.labelMedium, color = if (isPrimary) DreamlandGold else DreamlandMuted, fontWeight = FontWeight.SemiBold)
                if (canRemove && !isPrimary) { TextButton(onClick = onRemove) { Text("Remove", color = Color(0xFFEF5350), style = MaterialTheme.typography.labelSmall) } }
            }
            Spacer(Modifier.height(8.dp))
            if (isPrimary) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    DreamlandTextField(modifier = Modifier.weight(1f), value = entry.name, onValueChange = onNameChange, label = "Full Name *")
                    DreamlandTextField(modifier = Modifier.weight(1f), value = entry.phone, onValueChange = { onPhoneChange(it.filter(Char::isDigit).take(10)) }, label = "Phone Number", keyboardType = KeyboardType.Phone)
                }
            } else {
                DreamlandTextField(modifier = Modifier.fillMaxWidth(), value = entry.name, onValueChange = onNameChange, label = "Full Name")
            }
            Spacer(Modifier.height(10.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Column {
                    Text("ID Proof Verified", color = DreamlandOnDark, style = MaterialTheme.typography.bodyMedium)
                    Text(if (entry.idProofVerified) "Verified ✓" else "Not verified", color = if (entry.idProofVerified) Color(0xFF4CAF50) else DreamlandMuted, style = MaterialTheme.typography.bodySmall)
                }
                Switch(checked = entry.idProofVerified, onCheckedChange = onIdProofChange, colors = SwitchDefaults.colors(checkedThumbColor = Color(0xFF0D1F17), checkedTrackColor = Color(0xFF4CAF50), uncheckedThumbColor = DreamlandMuted, uncheckedTrackColor = DreamlandForestElevated))
            }
        }
    }
}

// ── Pricing preview (multi-category aware) ────────────────────────────────────

@Composable
private fun PricingPreviewCard(state: WalkInState) {
    val checkOut = state.expectedCheckOut
    val nights = if (checkOut != null)
        ChronoUnit.DAYS.between(state.checkInTime.toMidnightUtc().toInstant(), checkOut.toMidnightUtc().toInstant()).coerceAtLeast(1)
    else 1L

    val grouped = state.selectedInstanceDetails.values.groupBy { it.categoryId }
    var roomTotal = 0.0
    val roomRows = grouped.map { (catId, insts) ->
        val price = state.categoryPrices[catId] ?: state.categories.find { it.id == catId }?.pricePerNight ?: 0.0
        val count = insts.size
        val charge = price * nights * count
        roomTotal += charge
        val catName = insts.first().categoryName.ifBlank { state.categories.find { it.id == catId }?.type ?: catId }
        val label = if (count > 1) "$catName × $count rooms × $nights nights" else "$catName × $nights night${if (nights != 1L) "s" else ""}"
        label to charge
    }.ifEmpty {
        val countsToUse = state.bookingRoomCountsByCategory.filter { it.value > 0 }
        if (countsToUse.isNotEmpty()) {
            countsToUse.map { (catId, count) ->
                val cat = state.categories.find { it.id == catId }
                val price = state.categoryPrices[catId] ?: cat?.pricePerNight ?: 0.0
                val charge = price * nights * count
                roomTotal += charge
                val catName = cat?.type ?: catId
                val label = if (count > 1) "$catName × $count rooms × $nights nights"
                            else "$catName × $nights night${if (nights != 1L) "s" else ""}"
                label to charge
            }
        } else {
            val cat = state.categories.find { it.id == state.selectedCategoryId }
            val price = state.categoryPrices[state.selectedCategoryId] ?: cat?.pricePerNight ?: 0.0
            roomTotal = price * nights
            listOf("Room charges" to roomTotal)
        }
    }

    val breakfastCharge = if (state.breakfast) state.selectedCategoryBreakfastPrice * state.adults * nights else 0.0
    val total = roomTotal + breakfastCharge

    Card(modifier = Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = DreamlandForestElevated), shape = RoundedCornerShape(10.dp)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            roomRows.forEach { (label, charge) ->
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(label, color = DreamlandMuted, style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f))
                    Text("₹${"%,.0f".format(charge)}", color = DreamlandOnDark, style = MaterialTheme.typography.bodySmall)
                }
            }
            if (state.breakfast) {
                HorizontalDivider(color = DreamlandMuted.copy(alpha = 0.15f))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("Breakfast — ${state.adults} pax × $nights nights", color = DreamlandMuted, style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f))
                    Text("₹${"%,.0f".format(breakfastCharge)}", color = DreamlandOnDark, style = MaterialTheme.typography.bodySmall)
                }
            }
            HorizontalDivider(color = DreamlandMuted.copy(alpha = 0.2f))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("Total", color = DreamlandOnDark, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                Text("₹${"%,.0f".format(total)}", color = DreamlandGold, fontWeight = FontWeight.Bold, fontSize = 18.sp)
            }
        }
    }
}

// ── Booking source dropdown (with add-new capability) ─────────────────────────

@Composable
private fun BookingSourceDropdown(
    sources: List<com.example.dreamland_reception.data.model.BookingSource>,
    selectedName: String,
    onSourceSelected: (id: String, name: String) -> Unit,
    onAddSource: (name: String) -> Unit,
    onTextChanged: (String) -> Unit,
) {
    var text by remember(selectedName) { mutableStateOf(selectedName) }
    var expanded by remember { mutableStateOf(false) }
    var triggerSize by remember { mutableStateOf(IntSize.Zero) }
    val density = LocalDensity.current

    val filtered = if (text.isBlank()) sources
        else sources.filter { it.name.contains(text, ignoreCase = true) }
    val showAdd = text.isNotBlank() && sources.none { it.name.equals(text, ignoreCase = true) }

    Box(Modifier.fillMaxWidth().onSizeChanged { triggerSize = it }) {
        OutlinedTextField(
            value = text,
            onValueChange = { text = it; onTextChanged(it); expanded = true },
            label = { Text("Source", color = DreamlandMuted, fontSize = 13.sp) },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            trailingIcon = {
                if (text.isNotBlank()) {
                    IconButton(onClick = { text = ""; onTextChanged(""); expanded = false }) {
                        Icon(Icons.Filled.Close, contentDescription = "Clear", tint = DreamlandMuted, modifier = Modifier.size(16.dp))
                    }
                }
            },
            colors = OutlinedTextFieldDefaults.colors(
                focusedTextColor = DreamlandOnDark, unfocusedTextColor = DreamlandOnDark,
                focusedBorderColor = DreamlandGold, unfocusedBorderColor = DreamlandMuted.copy(alpha = 0.4f),
                cursorColor = DreamlandGold,
            ),
        )
        DropdownMenu(
            expanded = expanded && (filtered.isNotEmpty() || showAdd),
            onDismissRequest = { expanded = false },
            properties = PopupProperties(focusable = false),
            modifier = Modifier.width(with(density) { triggerSize.width.toDp() }),
        ) {
            filtered.forEach { src ->
                DropdownMenuItem(
                    modifier = Modifier.fillMaxWidth(),
                    text = { Text(src.name, color = DreamlandOnDark, style = MaterialTheme.typography.bodyMedium) },
                    onClick = { text = src.name; onSourceSelected(src.id, src.name); expanded = false },
                )
                HorizontalDivider(color = DreamlandGold.copy(alpha = 0.08f))
            }
            if (showAdd) {
                if (filtered.isNotEmpty()) HorizontalDivider(color = DreamlandMuted.copy(alpha = 0.2f))
                DropdownMenuItem(
                    modifier = Modifier.fillMaxWidth(),
                    text = { Text("Add \"$text\"", color = DreamlandGold, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold) },
                    onClick = { onAddSource(text); expanded = false },
                )
            }
        }
    }
}

// ── Shared helpers ────────────────────────────────────────────────────────────

@Composable
internal fun SectionLabel(text: String) {
    Text(text, style = MaterialTheme.typography.labelLarge, color = DreamlandGold, letterSpacing = 1.sp)
    HorizontalDivider(color = DreamlandGold.copy(alpha = 0.2f), modifier = Modifier.padding(top = 4.dp))
}

@Composable
internal fun DreamlandTextField(
    modifier: Modifier = Modifier,
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    keyboardType: KeyboardType = KeyboardType.Text,
) {
    OutlinedTextField(
        value = value, onValueChange = onValueChange,
        label = { Text(label, color = DreamlandMuted, fontSize = 13.sp) },
        modifier = modifier, singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
        colors = OutlinedTextFieldDefaults.colors(
            focusedTextColor = DreamlandOnDark, unfocusedTextColor = DreamlandOnDark,
            focusedBorderColor = DreamlandGold, unfocusedBorderColor = DreamlandMuted.copy(alpha = 0.4f),
            cursorColor = DreamlandGold,
        ),
    )
}

@Composable
private fun DreamlandDropdown(
    modifier: Modifier = Modifier,
    label: String,
    selectedText: String,
    options: List<Pair<String, String>>,
    onSelected: (String) -> Unit,
    enabled: Boolean = true,
    emptyText: String = "No options available",
) {
    var expanded by remember { mutableStateOf(false) }
    var triggerSize by remember { mutableStateOf(IntSize.Zero) }
    val density = LocalDensity.current
    Column(modifier) {
        Text(label, style = MaterialTheme.typography.labelMedium, color = DreamlandMuted)
        Spacer(Modifier.height(4.dp))
        Box(
            modifier = Modifier.fillMaxWidth().onSizeChanged { triggerSize = it }
                .clip(RoundedCornerShape(8.dp))
                .border(1.dp, if (enabled) DreamlandMuted.copy(alpha = 0.4f) else DreamlandMuted.copy(alpha = 0.15f), RoundedCornerShape(8.dp))
                .background(if (enabled) DreamlandForestElevated else DreamlandForestElevated.copy(alpha = 0.5f))
                .clickable(enabled = enabled) { expanded = true }
                .padding(horizontal = 16.dp, vertical = 14.dp),
        ) {
            Text(selectedText, color = if (enabled) DreamlandOnDark else DreamlandMuted, style = MaterialTheme.typography.bodyMedium)
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }, modifier = Modifier.width(with(density) { triggerSize.width.toDp() })) {
            if (options.isEmpty()) {
                DropdownMenuItem(modifier = Modifier.fillMaxWidth(), text = { Text(emptyText, color = DreamlandMuted, modifier = Modifier.fillMaxWidth()) }, onClick = {})
            } else {
                options.forEach { (id, lbl) ->
                    DropdownMenuItem(modifier = Modifier.fillMaxWidth(), text = { Text(lbl, color = DreamlandOnDark, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.fillMaxWidth()) }, onClick = { onSelected(id); expanded = false })
                    HorizontalDivider(color = DreamlandGold.copy(alpha = 0.08f))
                }
            }
        }
    }
}

@Composable
internal fun DateSelectorField(
    modifier: Modifier = Modifier,
    label: String,
    date: Date?,
    onDateSelected: (Date?) -> Unit,
    minDate: Date? = null,
) {
    var showPicker by remember { mutableStateOf(false) }
    val fmt = SimpleDateFormat("dd MMM yyyy", Locale.getDefault())
    Column(modifier) {
        Text(label, style = MaterialTheme.typography.labelMedium, color = DreamlandMuted)
        Spacer(Modifier.height(4.dp))
        Box(
            modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp))
                .border(1.dp, DreamlandMuted.copy(alpha = 0.4f), RoundedCornerShape(8.dp))
                .background(DreamlandForestElevated).clickable { showPicker = true }
                .padding(horizontal = 16.dp, vertical = 14.dp),
        ) {
            Text(if (date != null) fmt.format(date) else "Tap to set date", color = if (date != null) DreamlandOnDark else DreamlandMuted, style = MaterialTheme.typography.bodyMedium)
        }
    }
    if (showPicker) {
        SimpleDatePickerDialog(
            initialDate = date ?: run { val c = Calendar.getInstance(); c.add(Calendar.DAY_OF_YEAR, 1); c.time },
            onDateSelected = { onDateSelected(it); showPicker = false },
            onDismiss = { showPicker = false },
            minDate = minDate,
        )
    }
}

@Composable
fun SimpleDatePickerDialog(
    initialDate: Date, onDateSelected: (Date) -> Unit, onDismiss: () -> Unit, minDate: Date? = null,
) {
    val minCal = minDate?.let {
        Calendar.getInstance().apply { time = it; set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0) }
    }
    val initCal = Calendar.getInstance().apply { time = initialDate }
    var displayYear  by remember { mutableStateOf(initCal.get(Calendar.YEAR)) }
    var displayMonth by remember { mutableStateOf(initCal.get(Calendar.MONTH)) }
    var selYear  by remember { mutableStateOf(initCal.get(Calendar.YEAR)) }
    var selMonth by remember { mutableStateOf(initCal.get(Calendar.MONTH)) }
    var selDay   by remember { mutableStateOf(initCal.get(Calendar.DAY_OF_MONTH)) }
    val today = Calendar.getInstance()
    fun isDisabled(y: Int, m: Int, d: Int): Boolean {
        if (minCal == null) return false
        val c = Calendar.getInstance().apply { set(y, m, d, 0, 0, 0); set(Calendar.MILLISECOND, 0) }
        return !c.after(minCal)
    }
    fun prevMonth() { if (displayMonth == 0) { displayMonth = 11; displayYear-- } else displayMonth-- }
    fun nextMonth() { if (displayMonth == 11) { displayMonth = 0; displayYear++ } else displayMonth++ }
    val monthNames = listOf("January","February","March","April","May","June","July","August","September","October","November","December")
    val dayLabels  = listOf("Mo","Tu","We","Th","Fr","Sa","Su")
    val firstDow = Calendar.getInstance().run { set(displayYear, displayMonth, 1); val dow = get(Calendar.DAY_OF_WEEK) - Calendar.MONDAY; if (dow < 0) dow + 7 else dow }
    val daysInMonth = Calendar.getInstance().apply { set(displayYear, displayMonth, 1) }.getActualMaximum(Calendar.DAY_OF_MONTH)
    val isSelectionValid = !isDisabled(selYear, selMonth, selDay)
    Dialog(onDismissRequest = onDismiss) {
        Card(colors = CardDefaults.cardColors(containerColor = DreamlandForestSurface), shape = RoundedCornerShape(16.dp), modifier = Modifier.width(340.dp)) {
            Column(Modifier.padding(20.dp)) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    TextButton(onClick = ::prevMonth, modifier = Modifier.size(36.dp)) { Text("‹", color = DreamlandGold, fontSize = 22.sp, fontWeight = FontWeight.Bold) }
                    Text("${monthNames[displayMonth]} $displayYear", style = MaterialTheme.typography.titleSmall, color = DreamlandOnDark, fontWeight = FontWeight.SemiBold)
                    TextButton(onClick = ::nextMonth, modifier = Modifier.size(36.dp)) { Text("›", color = DreamlandGold, fontSize = 22.sp, fontWeight = FontWeight.Bold) }
                }
                Spacer(Modifier.height(10.dp))
                Row(Modifier.fillMaxWidth()) { dayLabels.forEach { lbl -> Box(Modifier.weight(1f), contentAlignment = Alignment.Center) { Text(lbl, style = MaterialTheme.typography.labelSmall, color = DreamlandMuted, fontSize = 11.sp) } } }
                Spacer(Modifier.height(6.dp))
                val rows = (firstDow + daysInMonth + 6) / 7
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    for (row in 0 until rows) {
                        Row(Modifier.fillMaxWidth()) {
                            for (col in 0..6) {
                                val day = row * 7 + col - firstDow + 1
                                Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
                                    if (day < 1 || day > daysInMonth) Spacer(Modifier.size(36.dp))
                                    else {
                                        val isSelected = day == selDay && displayMonth == selMonth && displayYear == selYear
                                        val isToday = day == today.get(Calendar.DAY_OF_MONTH) && displayMonth == today.get(Calendar.MONTH) && displayYear == today.get(Calendar.YEAR)
                                        val disabled = isDisabled(displayYear, displayMonth, day)
                                        Box(modifier = Modifier.size(36.dp).clip(RoundedCornerShape(50))
                                            .background(when { isSelected -> DreamlandGold; isToday -> DreamlandGold.copy(alpha = 0.15f); else -> Color.Transparent })
                                            .clickable(enabled = !disabled) { selDay = day; selMonth = displayMonth; selYear = displayYear },
                                            contentAlignment = Alignment.Center,
                                        ) {
                                            Text("$day", color = when { isSelected -> Color(0xFF0D1F17); disabled -> DreamlandMuted.copy(alpha = 0.3f); isToday -> DreamlandGold; else -> DreamlandOnDark }, fontSize = 13.sp, fontWeight = if (isSelected || isToday) FontWeight.Bold else FontWeight.Normal)
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
                Spacer(Modifier.height(16.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    TextButton(onClick = onDismiss) { Text("Cancel", color = DreamlandMuted) }
                    Spacer(Modifier.width(8.dp))
                    Button(onClick = { onDateSelected(dateFromPicker(selYear, selMonth, selDay)) }, enabled = isSelectionValid, colors = ButtonDefaults.buttonColors(containerColor = DreamlandGold), shape = RoundedCornerShape(10.dp)) {
                        Text("Set", color = Color(0xFF0D1F17), fontWeight = FontWeight.SemiBold)
                    }
                }
            }
        }
    }
}

@Composable
private fun CounterField(label: String, value: Int, onIncrement: () -> Unit, onDecrement: () -> Unit) {
    Column(Modifier.fillMaxWidth()) {
        Text(label, style = MaterialTheme.typography.labelMedium, color = DreamlandMuted)
        Spacer(Modifier.height(4.dp))
        Row(
            modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp))
                .border(1.dp, DreamlandMuted.copy(alpha = 0.4f), RoundedCornerShape(8.dp))
                .background(DreamlandForestElevated),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TextButton(onClick = onDecrement) { Text("−", color = DreamlandGold, fontWeight = FontWeight.Bold, fontSize = 20.sp) }
            Text("$value", color = DreamlandOnDark, fontWeight = FontWeight.SemiBold, fontSize = 18.sp)
            TextButton(onClick = onIncrement) { Text("+", color = DreamlandGold, fontWeight = FontWeight.Bold, fontSize = 20.sp) }
        }
    }
}

@Composable
private fun OptionToggle(title: String, subtitle: String, checked: Boolean, onChecked: (Boolean) -> Unit) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(title, color = DreamlandOnDark, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
            Text(subtitle, color = DreamlandMuted, style = MaterialTheme.typography.bodySmall)
        }
        Switch(checked = checked, onCheckedChange = onChecked, colors = SwitchDefaults.colors(checkedThumbColor = Color(0xFF0D1F17), checkedTrackColor = DreamlandGold, uncheckedThumbColor = DreamlandMuted, uncheckedTrackColor = DreamlandForestElevated))
    }
}
