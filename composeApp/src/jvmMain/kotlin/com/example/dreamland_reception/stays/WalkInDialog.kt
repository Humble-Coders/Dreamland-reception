@file:OptIn(
    androidx.compose.foundation.layout.ExperimentalLayoutApi::class,
    androidx.compose.foundation.ExperimentalFoundationApi::class,
)

package com.example.dreamland_reception.stays

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
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
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Hotel
import androidx.compose.material.icons.filled.Print
import androidx.compose.material.icons.filled.KeyboardArrowLeft
import androidx.compose.material.icons.filled.KeyboardArrowRight
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material.icons.filled.DateRange
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
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import kotlinx.coroutines.delay
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.window.PopupProperties
import com.example.dreamland_reception.DreamlandForestElevated
import com.example.dreamland_reception.DreamlandForestSurface
import com.example.dreamland_reception.DreamlandGold
import com.example.dreamland_reception.DreamlandMuted
import com.example.dreamland_reception.DreamlandOnDark
import com.example.dreamland_reception.ui.viewmodel.GRC_SAVE_AS_PDF
import com.example.dreamland_reception.ui.viewmodel.GrcPhase
import com.example.dreamland_reception.ui.viewmodel.GuestEntry
import com.example.dreamland_reception.ui.viewmodel.RoomGuestAssignment
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

    // Step 3 validation: all rooms filled, all guests assigned to exactly one room
    val step3Valid = if (currentStep == 3) {
        val assignedIndices = state.roomGuestAssignment.values.flatMap { it.guestIndices }.toSet()
        val allGuestIndices = state.guestEntries.indices.toSet()
        state.selectedInstanceIds.isNotEmpty() &&
            state.selectedInstanceIds.all { id ->
                state.roomGuestAssignment[id]?.guestIndices?.isNotEmpty() == true
            } &&
            allGuestIndices.all { it in assignedIndices }
    } else true

    // The GRC-print step exists only for actual check-ins (not "Add Booking" mode), and is the
    // LAST step so the advance amount entered on Confirm is reflected on the card.
    val showGrcStep = !state.isBookingMode
    val stepLabels = if (showGrcStep)
        listOf("Stay\nDetails", "Guest\nInfo", "Assign\nRooms", "Confirm", "Print\nGRC")
    else
        listOf("Stay\nDetails", "Guest\nInfo", "Assign\nRooms", "Confirm")
    val totalSteps = stepLabels.size
    val confirmStep = 4
    val grcStep = if (showGrcStep) 5 else -1

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
                WizardStepIndicator(currentStep, stepLabels, onStepClick = { currentStep = it })
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
                    3 -> Step3AssignRooms(state = state, vm = vm)
                    grcStep -> Step4PrintGrc(state, vm)
                    confirmStep -> Step4Confirm(state, vm)
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

                if (currentStep < totalSteps) {
                    Column(horizontalAlignment = Alignment.End) {
                        // Hint when step 3 validation fails
                        if (currentStep == 3 && !step3Valid && !state.isBookingMode) {
                            val assignedIdx = state.roomGuestAssignment.values.flatMap { it.guestIndices }.toSet()
                            val unassigned = state.guestEntries.indices.count { it !in assignedIdx }
                            val emptyRooms = state.selectedInstanceIds.count {
                                state.roomGuestAssignment[it]?.guestIndices?.isEmpty() != false
                            }
                            val hint = when {
                                emptyRooms > 0 && unassigned > 0 -> "$emptyRooms room(s) empty · $unassigned guest(s) unassigned"
                                emptyRooms > 0 -> "$emptyRooms room(s) have no guests"
                                unassigned > 0 -> "$unassigned guest(s) not assigned to any room"
                                else -> ""
                            }
                            if (hint.isNotEmpty()) Text(hint, color = Color(0xFFEF5350),
                                style = MaterialTheme.typography.labelSmall,
                                modifier = Modifier.padding(bottom = 4.dp))
                        }
                        Button(
                            onClick = { currentStep++ },
                            enabled = step3Valid || state.isBookingMode,
                            colors = ButtonDefaults.buttonColors(
                                containerColor = DreamlandGold,
                                disabledContainerColor = DreamlandGold.copy(alpha = 0.35f),
                            ),
                            shape = RoundedCornerShape(12.dp),
                        ) {
                            Text("Next", color = Color(0xFF0D1F17), fontWeight = FontWeight.SemiBold)
                            Spacer(Modifier.width(4.dp))
                            Icon(Icons.Filled.KeyboardArrowRight, contentDescription = null, tint = Color(0xFF0D1F17), modifier = Modifier.size(18.dp))
                        }
                    }
                } else {
                    // Final (Confirm) step: submit
                    Button(
                        onClick = { if (state.isBookingMode) vm.submitAsBooking() else vm.requestSubmitWalkIn() },
                        enabled = !state.isSaving,
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
private fun WizardStepIndicator(currentStep: Int, steps: List<String>, onStepClick: (Int) -> Unit) {
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
            if (summaryEntries.isNotEmpty()) {
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
    LaunchedEffect(Unit) { vm.loadPurposeTypes() }
    // Auto-dismiss scanner message after 3s
    LaunchedEffect(state.scannerMessage) {
        if (state.scannerMessage != null) {
            delay(3000)
            vm.clearScannerMessage()
        }
    }

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

    // Scanner error toast
    if (state.scannerMessage != null) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(8.dp))
                .background(Color(0xFFEF5350).copy(alpha = 0.15f))
                .padding(horizontal = 12.dp, vertical = 8.dp),
        ) {
            Text(
                "⚠ ${state.scannerMessage}",
                color = Color(0xFFEF5350),
                style = MaterialTheme.typography.bodySmall,
                fontWeight = FontWeight.SemiBold,
            )
        }
        Spacer(Modifier.height(4.dp))
    }

    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        state.guestEntries.forEachIndexed { index, guest ->
            GuestWizardCard(
                index = index,
                entry = guest,
                onNameChange = { vm.onGuestName(index, it) },
                onPhoneChange = { vm.onGuestPhone(index, it) },
                onIdProofChange = { vm.onGuestIdProof(index, it) },
                onGenderChange = { vm.onGuestGender(index, it) },
                onIdTypeChange = { vm.onGuestIdType(index, it) },
                onGovIdChange = { vm.onGuestGovIdNumber(index, it) },
                onAddressChange = { vm.onGuestAddress(index, it) },
                onDobChange = { vm.onGuestDob(index, it) },
                onAgeChange = { vm.onGuestAge(index, it) },
                purposeOptions = state.purposeOptions,
                onPurposeChange = { vm.onGuestPurpose(index, it) },
                onAddPurpose = { vm.addPurposeType(index, it) },
                onScannerClick = { vm.fillGuestFromScanner(index) },
                onRemove = if (index > 0) { { vm.removeGuest(index) } } else null,
            )
        }

        OutlinedButton(
            onClick = vm::addGuest,
            shape = RoundedCornerShape(10.dp),
            border = androidx.compose.foundation.BorderStroke(1.dp, DreamlandGold.copy(alpha = 0.5f)),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text("+ Add Guest", color = DreamlandGold, fontWeight = FontWeight.SemiBold)
        }
    }
}

// ── Step 3: Assign Guests to Rooms ────────────────────────────────────────────

@Composable
private fun Step3AssignRooms(state: WalkInState, vm: StaysViewModel) {
    val orderedIds = state.selectedInstanceDetails.keys.toList()

    Text("ASSIGN GUESTS TO ROOMS", style = MaterialTheme.typography.labelLarge, color = DreamlandGold, letterSpacing = 1.sp)

    Spacer(Modifier.height(12.dp))

    if (orderedIds.isEmpty()) {
        Text("No rooms selected. Go back to Step 1 to select rooms.", color = DreamlandMuted, style = MaterialTheme.typography.bodySmall)
        return
    }

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

    if (state.guestEntries.isEmpty()) {
        Text("Add guest info in Step 2 first.", color = DreamlandMuted, style = MaterialTheme.typography.bodySmall)
        return
    }

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        orderedIds.forEach { instanceId ->
            val inst = state.selectedInstanceDetails[instanceId]
            val assignment = state.roomGuestAssignment[instanceId] ?: RoomGuestAssignment()
            val effectivePrimary = assignment.primaryIndex ?: assignment.guestIndices.minOrNull()

            Card(
                colors = CardDefaults.cardColors(containerColor = DreamlandForestElevated),
                shape = RoundedCornerShape(10.dp),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    // Room header
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Icon(Icons.Filled.Hotel, contentDescription = null, tint = DreamlandGold, modifier = Modifier.size(16.dp))
                        Text(
                            "Room ${inst?.roomNumber ?: "?"}${if (!inst?.categoryName.isNullOrBlank()) " · ${inst?.categoryName}" else ""}",
                            color = DreamlandOnDark,
                            fontWeight = FontWeight.SemiBold,
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    }
                    HorizontalDivider(color = DreamlandGold.copy(alpha = 0.1f))
                    // Guest rows
                    state.guestEntries.forEachIndexed { idx, guest ->
                        val isChecked = idx in assignment.guestIndices
                        val isPrimary = idx == effectivePrimary && isChecked
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(6.dp))
                                .background(if (isChecked) DreamlandGold.copy(alpha = 0.06f) else Color.Transparent)
                                .padding(horizontal = 8.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            // Checkbox
                            androidx.compose.material3.Checkbox(
                                checked = isChecked,
                                onCheckedChange = { checked ->
                                    val newIndices = if (checked) assignment.guestIndices + idx else assignment.guestIndices - idx
                                    val newPrimary = if (!checked && isPrimary) null else assignment.primaryIndex
                                    vm.onRoomGuestAssignment(instanceId, assignment.copy(guestIndices = newIndices, primaryIndex = newPrimary))
                                },
                                colors = androidx.compose.material3.CheckboxDefaults.colors(
                                    checkedColor = DreamlandGold,
                                    uncheckedColor = DreamlandMuted.copy(alpha = 0.4f),
                                    checkmarkColor = Color(0xFF0D1F17),
                                ),
                                modifier = Modifier.size(20.dp),
                            )
                            // Guest name + phone
                            Column(Modifier.weight(1f)) {
                                Text(
                                    guest.name.ifBlank { "Guest ${idx + 1}" },
                                    color = if (isChecked) DreamlandOnDark else DreamlandMuted,
                                    style = MaterialTheme.typography.bodySmall,
                                    fontWeight = if (isPrimary) FontWeight.SemiBold else FontWeight.Normal,
                                )
                                if (guest.phone.isNotBlank()) {
                                    Text(guest.phone, color = DreamlandMuted.copy(alpha = 0.7f), style = MaterialTheme.typography.labelSmall)
                                }
                            }
                            // ID status chip
                            Box(
                                Modifier
                                    .clip(RoundedCornerShape(4.dp))
                                    .background(if (guest.idProofVerified) Color(0xFF4CAF50).copy(alpha = 0.12f) else Color(0xFFF39C12).copy(alpha = 0.12f))
                                    .padding(horizontal = 6.dp, vertical = 2.dp),
                            ) {
                                Text(
                                    if (guest.idProofVerified) "✓ ID" else "⚠ ID",
                                    color = if (guest.idProofVerified) Color(0xFF4CAF50) else Color(0xFFF39C12),
                                    style = MaterialTheme.typography.labelSmall,
                                    fontSize = 10.sp,
                                )
                            }
                            // Primary selector — star icon; only one per room at a time
                            // Filled gold star = current primary; dim outline star = click to set as primary
                            if (isChecked) {
                                Column(
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    modifier = Modifier.clickable(enabled = !isPrimary) {
                                        vm.onRoomGuestAssignment(instanceId, assignment.copy(primaryIndex = idx))
                                    }.padding(horizontal = 4.dp),
                                ) {
                                    Text(
                                        if (isPrimary) "★" else "☆",
                                        color = if (isPrimary) DreamlandGold else DreamlandMuted.copy(alpha = 0.35f),
                                        fontSize = 16.sp,
                                        fontWeight = FontWeight.Bold,
                                    )
                                    Text(
                                        "Primary",
                                        color = if (isPrimary) DreamlandGold else DreamlandMuted.copy(alpha = 0.35f),
                                        style = MaterialTheme.typography.labelSmall,
                                        fontSize = 9.sp,
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

// ── Step 4 (check-in only): Print GRC ─────────────────────────────────────────

@Composable
private fun Step4PrintGrc(state: WalkInState, vm: StaysViewModel) {
    LaunchedEffect(Unit) { vm.loadGrcPrinters() }
    val grc = state.grc
    // Only guests whose ID was captured in the Guest Info step get a GRC.
    val idGuests = state.guestEntries.withIndex().filter { it.value.idProofVerified }

    WizardSectionLabel("PRINT GUEST REGISTRATION CARDS")
    Spacer(Modifier.height(8.dp))

    // Printer dropdown (mirrors the billing print flow)
    Text("Printer", style = MaterialTheme.typography.labelSmall, color = DreamlandMuted)
    Spacer(Modifier.height(4.dp))
    var printerExpanded by remember { mutableStateOf(false) }
    Box {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(10.dp))
                .background(DreamlandForestElevated)
                .border(1.dp, DreamlandGold.copy(alpha = 0.4f), RoundedCornerShape(10.dp))
                .clickable { printerExpanded = true }
                .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                when {
                    grc.selectedPrinter.isNotBlank() -> grc.selectedPrinter
                    grc.availablePrinters.isEmpty() -> "Select target"
                    else -> "Select Printer"
                },
                color = if (grc.selectedPrinter.isBlank()) DreamlandMuted else DreamlandOnDark,
                style = MaterialTheme.typography.bodyMedium,
            )
            Icon(Icons.Filled.ArrowDropDown, contentDescription = null, tint = DreamlandGold)
        }
        DropdownMenu(expanded = printerExpanded, onDismissRequest = { printerExpanded = false }) {
            // Test option — saves the rendered GRC to a PDF file (and opens it) instead of printing.
            DropdownMenuItem(
                text = { Text(GRC_SAVE_AS_PDF, color = DreamlandGold, fontWeight = FontWeight.SemiBold) },
                onClick = { vm.selectGrcPrinter(GRC_SAVE_AS_PDF); printerExpanded = false },
            )
            grc.availablePrinters.forEach { p ->
                DropdownMenuItem(
                    text = { Text(p, color = DreamlandOnDark) },
                    onClick = { vm.selectGrcPrinter(p); printerExpanded = false },
                )
            }
        }
    }

    Spacer(Modifier.height(16.dp))

    if (idGuests.isEmpty()) {
        Text(
            "No guest ID captured yet. Capture an ID in the Guest Info step to print a GRC.",
            color = DreamlandMuted, style = MaterialTheme.typography.bodySmall,
        )
    } else {
        val isSaveMode = grc.selectedPrinter == GRC_SAVE_AS_PDF
        idGuests.forEach { (index, entry) ->
            val phase = grc.statuses[index] ?: GrcPhase.IDLE
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 5.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(DreamlandForestElevated)
                    .padding(horizontal = 14.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text(
                        entry.name.ifBlank { "Guest ${index + 1}" },
                        color = DreamlandOnDark, fontWeight = FontWeight.SemiBold,
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    val sub = when (phase) {
                        GrcPhase.WORKING -> if (isSaveMode) "Generating PDF…" else "Generating & printing…"
                        GrcPhase.DONE -> if (isSaveMode) "PDF saved & opened ✓" else "Sent to printer ✓"
                        GrcPhase.ERROR -> grc.errors[index] ?: "Failed"
                        else -> entry.govIdNumber.ifBlank { "ID on file" }
                    }
                    Text(
                        sub,
                        color = if (phase == GrcPhase.ERROR) Color(0xFFEF5350) else DreamlandMuted,
                        style = MaterialTheme.typography.labelSmall,
                    )
                }
                Button(
                    onClick = { vm.printGrcForGuest(index) },
                    enabled = phase != GrcPhase.WORKING && grc.selectedPrinter.isNotBlank(),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = DreamlandGold,
                        disabledContainerColor = DreamlandGold.copy(alpha = 0.35f),
                    ),
                    shape = RoundedCornerShape(10.dp),
                ) {
                    if (phase == GrcPhase.WORKING) {
                        CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp, color = Color(0xFF0D1F17))
                    } else {
                        Icon(
                            if (phase == GrcPhase.DONE) Icons.Filled.CheckCircle else Icons.Filled.Print,
                            contentDescription = null, tint = Color(0xFF0D1F17), modifier = Modifier.size(16.dp),
                        )
                        Spacer(Modifier.width(6.dp))
                        Text(
                            when {
                                isSaveMode && phase == GrcPhase.DONE -> "Save again"
                                isSaveMode -> "Save PDF"
                                phase == GrcPhase.DONE -> "Reprint"
                                else -> "Print GRC"
                            },
                            color = Color(0xFF0D1F17), fontWeight = FontWeight.SemiBold,
                        )
                    }
                }
            }
        }
    }

    Spacer(Modifier.height(8.dp))
    Text(
        "Printing the GRC is optional — you can proceed to Confirm. Pick \"$GRC_SAVE_AS_PDF\" to save the form to a file instead of printing.",
        color = DreamlandMuted.copy(alpha = 0.7f), style = MaterialTheme.typography.labelSmall,
    )
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
                "₹${"%.2f".format(state.selectedCategoryBreakfastPrice)} per person per night"
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

    // Advance payment — show booking's existing advance if checking in from booking
    val bookingAdvance = when {
        state.sourceBooking != null -> state.sourceBooking.advancePaidAmount
        state.groupBookings.isNotEmpty() -> state.groupBookings.sumOf { it.advancePaidAmount }
        else -> 0.0
    }
    WizardSectionLabel("ADVANCE PAYMENT")
    Spacer(Modifier.height(8.dp))
    if (bookingAdvance > 0) {
        Row(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(8.dp))
                .background(Color(0xFF4CAF50).copy(alpha = 0.08f))
                .padding(horizontal = 12.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("Paid from booking:", color = DreamlandMuted, style = MaterialTheme.typography.bodySmall)
            Text("₹${"%.2f".format(bookingAdvance)}", color = Color(0xFF4CAF50), fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.bodySmall)
        }
        Spacer(Modifier.height(8.dp))
    }
    OutlinedTextField(
        value = state.advancePayment,
        onValueChange = vm::onWalkInAdvancePayment,
        label = { Text("Advance Amount (₹)", color = DreamlandMuted, fontSize = 13.sp) },
        modifier = Modifier.fillMaxWidth(),
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
        colors = OutlinedTextFieldDefaults.colors(
            focusedTextColor = DreamlandOnDark, unfocusedTextColor = DreamlandOnDark,
            focusedBorderColor = DreamlandGold, unfocusedBorderColor = DreamlandMuted.copy(alpha = 0.4f),
            cursorColor = DreamlandGold,
        ),
    )
    Spacer(Modifier.height(8.dp))
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        listOf("CASH", "BANK").forEach { method ->
            val selected = state.advancePaymentMethod == method
            Box(
                Modifier
                    .clip(RoundedCornerShape(6.dp))
                    .background(if (selected) DreamlandGold.copy(alpha = 0.15f) else Color.Transparent)
                    .border(1.dp, if (selected) DreamlandGold else DreamlandMuted.copy(alpha = 0.3f), RoundedCornerShape(6.dp))
                    .clickable { vm.onWalkInAdvancePaymentMethod(method) }
                    .padding(horizontal = 16.dp, vertical = 8.dp),
            ) { Text(method, color = if (selected) DreamlandGold else DreamlandMuted, fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal, fontSize = 13.sp) }
        }
    }
    val advance = state.advancePayment.toDoubleOrNull() ?: 0.0
    if (advance > 0 || bookingAdvance > 0) {
        val roomTotal = state.selectedInstanceDetails.values.groupBy { it.categoryId }.entries.sumOf { (catId, insts) ->
            val price = state.categoryPrices[catId] ?: state.categories.find { it.id == catId }?.pricePerNight ?: 0.0
            price * nights * insts.size
        }.let { if (it == 0.0) (state.categories.find { it.id == state.selectedCategoryId }?.pricePerNight ?: 0.0) * nights * state.selectedInstanceIds.size.coerceAtLeast(1) else it }
        val bfCharge = if (state.breakfast) state.selectedCategoryBreakfastPrice * state.adults * nights else 0.0
        val taxAmount = state.selectedInstanceDetails.values.groupBy { it.categoryId }.entries.sumOf { (catId, insts) ->
            val price = state.categoryPrices[catId] ?: state.categories.find { it.id == catId }?.pricePerNight ?: 0.0
            val taxRate = state.categories.find { it.id == catId }?.taxPercentage ?: 0.0
            price * nights * insts.size * taxRate / 100.0
        }
        val total = roomTotal + bfCharge + taxAmount
        val pending = (total - bookingAdvance - advance).coerceAtLeast(0.0)
        Spacer(Modifier.height(6.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("Remaining at check-out:", color = DreamlandMuted, style = MaterialTheme.typography.bodySmall)
            Text("₹${"%.2f".format(pending)}", color = if (pending > 0) Color(0xFFFFC107) else Color(0xFF4CAF50), fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.bodySmall)
        }
    }
    Spacer(Modifier.height(20.dp))

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

// Shared label width so every field in the 2-column grid starts at the same x.
private val GUEST_LABEL_WIDTH = 74.dp

/** One grid cell = fixed-width left label + field. Occupies one half of the row. */
@Composable
private fun RowScope.LabeledCell(label: String, field: @Composable RowScope.() -> Unit) {
    Row(
        modifier = Modifier.weight(1f),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text(label, color = DreamlandMuted, style = MaterialTheme.typography.labelSmall, modifier = Modifier.width(GUEST_LABEL_WIDTH))
        field()
    }
}

@Composable
private fun GuestWizardCard(
    index: Int,
    entry: GuestEntry,
    onNameChange: (String) -> Unit,
    onPhoneChange: (String) -> Unit,
    onIdProofChange: (Boolean) -> Unit,
    onGenderChange: (String) -> Unit,
    onIdTypeChange: (String) -> Unit,
    onGovIdChange: (String) -> Unit,
    onAddressChange: (String) -> Unit,
    onDobChange: (String) -> Unit,
    onAgeChange: (String) -> Unit,
    purposeOptions: List<String>,
    onPurposeChange: (String) -> Unit,
    onAddPurpose: (String) -> Unit,
    onScannerClick: () -> Unit,
    onRemove: (() -> Unit)? = null,
) {
    val isPrimary = index == 0
    // Compact field colors reused across all rows
    val compactColors = OutlinedTextFieldDefaults.colors(
        focusedTextColor = DreamlandOnDark, unfocusedTextColor = DreamlandOnDark,
        focusedBorderColor = DreamlandGold, unfocusedBorderColor = DreamlandMuted.copy(alpha = 0.4f),
        cursorColor = DreamlandGold, errorBorderColor = Color(0xFFEF5350),
    )
    Card(
        colors = CardDefaults.cardColors(containerColor = DreamlandForestElevated),
        shape = RoundedCornerShape(12.dp),
    ) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 14.dp), verticalArrangement = Arrangement.spacedBy(11.dp)) {
            // Header: number circle + label + Use Scanner + Remove
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth().padding(bottom = 2.dp),
            ) {
                Box(
                    modifier = Modifier
                        .size(26.dp)
                        .clip(CircleShape)
                        .background(if (isPrimary) DreamlandGold else Color.Transparent)
                        .border(1.dp, if (isPrimary) DreamlandGold else DreamlandMuted.copy(alpha = 0.5f), CircleShape),
                    contentAlignment = Alignment.Center,
                ) {
                    Text("${index + 1}", color = if (isPrimary) Color(0xFF0D1F17) else DreamlandMuted, fontWeight = FontWeight.Bold, fontSize = 11.sp)
                }
                Text("Guest ${index + 1}", color = DreamlandGold, fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f))
                OutlinedButton(
                    onClick = onScannerClick,
                    shape = RoundedCornerShape(6.dp),
                    border = androidx.compose.foundation.BorderStroke(1.dp, DreamlandGold.copy(alpha = 0.6f)),
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                    modifier = Modifier.height(28.dp),
                ) {
                    Text("Use Scanner", color = DreamlandGold, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                }
                if (onRemove != null) {
                    TextButton(onClick = onRemove, contentPadding = PaddingValues(horizontal = 6.dp, vertical = 2.dp)) {
                        Text("Remove", color = Color(0xFFEF5350), fontSize = 11.sp)
                    }
                }
            }

            // Clean 2-column grid: each cell = fixed-width label + field, columns align across rows.
            val rowGap = Arrangement.spacedBy(24.dp)

            // Full Name | Phone
            Row(Modifier.fillMaxWidth(), horizontalArrangement = rowGap, verticalAlignment = Alignment.CenterVertically) {
                LabeledCell(if (isPrimary) "Full Name *" else "Full Name") {
                    CompactField(
                        value = entry.name, onValueChange = onNameChange,
                        placeholder = "Enter name", colors = compactColors, modifier = Modifier.weight(1f),
                    )
                }
                LabeledCell("Phone") {
                    CompactField(
                        value = entry.phone,
                        onValueChange = { v -> onPhoneChange(v.filter(Char::isDigit).take(10)) },
                        placeholder = "00000 00000", prefix = "+91 ",
                        keyboardType = KeyboardType.Phone,
                        isError = entry.phone.isNotBlank() && entry.phone.length != 10,
                        colors = compactColors, modifier = Modifier.weight(1f),
                    )
                }
            }

            // Gender | Age
            Row(Modifier.fillMaxWidth(), horizontalArrangement = rowGap, verticalAlignment = Alignment.CenterVertically) {
                LabeledCell("Gender") {
                    CompactDropdown(
                        selectedText = entry.gender, placeholder = "Select",
                        options = listOf("Male", "Female", "Other"),
                        onSelected = onGenderChange, modifier = Modifier.weight(1f),
                    )
                }
                LabeledCell("Age") {
                    CompactField(
                        value = entry.age?.toString() ?: "",
                        onValueChange = { onAgeChange(it.filter(Char::isDigit).take(3)) },
                        placeholder = "—", keyboardType = KeyboardType.Number,
                        colors = compactColors, modifier = Modifier.weight(1f),
                    )
                }
            }

            // Date of Birth | ID Type
            Row(Modifier.fillMaxWidth(), horizontalArrangement = rowGap, verticalAlignment = Alignment.CenterVertically) {
                LabeledCell("DOB") {
                    DobPickerField(modifier = Modifier.weight(1f), dob = entry.dob, onDobChange = onDobChange)
                }
                LabeledCell("ID Type") {
                    CompactDropdown(
                        selectedText = entry.idType, placeholder = "Select",
                        options = ID_TYPE_OPTIONS,
                        onSelected = onIdTypeChange, modifier = Modifier.weight(1f),
                    )
                }
            }

            // Govt ID | Purpose
            Row(Modifier.fillMaxWidth(), horizontalArrangement = rowGap, verticalAlignment = Alignment.CenterVertically) {
                LabeledCell("Govt ID") {
                    CompactField(
                        value = entry.govIdNumber, onValueChange = onGovIdChange,
                        placeholder = "Enter ID number", colors = compactColors, modifier = Modifier.weight(1f),
                    )
                }
                LabeledCell("Purpose") {
                    PurposeAutocompleteField(
                        modifier = Modifier.weight(1f),
                        value = entry.purpose, options = purposeOptions,
                        onValueChange = onPurposeChange, onAdd = onAddPurpose,
                        colors = compactColors,
                    )
                }
            }

            // Address (full width) + optional ID image buttons
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("Address", color = DreamlandMuted, style = MaterialTheme.typography.labelSmall, modifier = Modifier.width(GUEST_LABEL_WIDTH))
                CompactField(
                    value = entry.address, onValueChange = onAddressChange,
                    placeholder = "Enter address", colors = compactColors, modifier = Modifier.weight(1f),
                )
                if (entry.govIdPicture1.isNotBlank()) {
                    OutlinedButton(
                        onClick = { openUrlInBrowser(entry.govIdPicture1) },
                        shape = RoundedCornerShape(6.dp),
                        border = androidx.compose.foundation.BorderStroke(1.dp, DreamlandGold.copy(alpha = 0.5f)),
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp),
                    ) { Text("ID Image 1", color = DreamlandGold, fontSize = 11.sp) }
                }
                if (entry.govIdPicture2.isNotBlank()) {
                    OutlinedButton(
                        onClick = { openUrlInBrowser(entry.govIdPicture2) },
                        shape = RoundedCornerShape(6.dp),
                        border = androidx.compose.foundation.BorderStroke(1.dp, DreamlandGold.copy(alpha = 0.5f)),
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp),
                    ) { Text("ID Image 2", color = DreamlandGold, fontSize = 11.sp) }
                }
            }
        }
    }
}

private fun openUrlInBrowser(url: String) {
    runCatching { java.awt.Desktop.getDesktop().browse(java.net.URI(url)) }
}

// ── Compact single-line field (no clipping at 46.dp) ──────────────────────────
// Standard M3 OutlinedTextField clips when forced below ~56.dp. This uses the
// lower-level DecorationBox with reduced content padding so text + placeholder
// render fully inside a fixed 46.dp height.
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun CompactField(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    colors: androidx.compose.material3.TextFieldColors,
    modifier: Modifier = Modifier,
    keyboardType: KeyboardType = KeyboardType.Text,
    prefix: String? = null,
    isError: Boolean = false,
) {
    val interaction = remember { MutableInteractionSource() }
    BasicTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = modifier.height(46.dp),
        textStyle = TextStyle(fontSize = 13.sp, color = DreamlandOnDark),
        singleLine = true,
        cursorBrush = SolidColor(DreamlandGold),
        keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
        interactionSource = interaction,
        decorationBox = { inner ->
            OutlinedTextFieldDefaults.DecorationBox(
                value = value,
                innerTextField = inner,
                enabled = true,
                singleLine = true,
                visualTransformation = VisualTransformation.None,
                interactionSource = interaction,
                isError = isError,
                placeholder = { Text(placeholder, color = DreamlandMuted.copy(alpha = 0.5f), fontSize = 12.sp, maxLines = 1) },
                prefix = prefix?.let { { Text(it, color = DreamlandMuted, fontSize = 13.sp) } },
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 0.dp),
                colors = colors,
            )
        },
    )
}

// ── Compact dropdown styled to match CompactField (46.dp) ─────────────────────
@Composable
internal fun CompactDropdown(
    selectedText: String,
    placeholder: String,
    options: List<String>,
    onSelected: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    var expanded by remember { mutableStateOf(false) }
    var triggerSize by remember { mutableStateOf(IntSize.Zero) }
    val density = LocalDensity.current
    Box(modifier) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(46.dp)
                .onSizeChanged { triggerSize = it }
                .clip(RoundedCornerShape(4.dp))
                .border(1.dp, DreamlandMuted.copy(alpha = 0.4f), RoundedCornerShape(4.dp))
                .clickable { expanded = true }
                .padding(horizontal = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                selectedText.ifBlank { placeholder },
                color = if (selectedText.isBlank()) DreamlandMuted.copy(alpha = 0.5f) else DreamlandOnDark,
                fontSize = 13.sp, maxLines = 1, modifier = Modifier.weight(1f),
            )
            Icon(Icons.Filled.ArrowDropDown, contentDescription = null, tint = DreamlandMuted, modifier = Modifier.size(18.dp))
        }
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
            modifier = Modifier.width(with(density) { triggerSize.width.toDp() }).background(DreamlandForestElevated),
        ) {
            options.forEach { opt ->
                DropdownMenuItem(
                    modifier = Modifier.fillMaxWidth(),
                    text = { Text(opt, color = DreamlandOnDark, fontSize = 13.sp) },
                    onClick = { onSelected(opt); expanded = false },
                )
            }
        }
    }
}

@Composable
private fun DobPickerField(modifier: Modifier = Modifier, dob: String, onDobChange: (String) -> Unit) {
    val parts = dob.split("/")
    var selDay   by remember(dob) { mutableStateOf(parts.getOrNull(0)?.toIntOrNull() ?: 1) }
    var selMonth by remember(dob) { mutableStateOf(parts.getOrNull(1)?.toIntOrNull() ?: 1) }
    var selYear  by remember(dob) { mutableStateOf(parts.getOrNull(2)?.toIntOrNull() ?: java.time.LocalDate.now().year) }

    var dayExpanded   by remember { mutableStateOf(false) }
    var monthExpanded by remember { mutableStateOf(false) }
    var yearExpanded  by remember { mutableStateOf(false) }

    val monthNames = listOf("Jan","Feb","Mar","Apr","May","Jun","Jul","Aug","Sep","Oct","Nov","Dec")
    val currentYear = java.time.LocalDate.now().year

    fun commit() = onDobChange("%02d/%02d/%04d".format(selDay, selMonth, selYear))

    val btnBorder = androidx.compose.foundation.BorderStroke(1.dp, DreamlandGold.copy(alpha = 0.5f))
    val btnPad = PaddingValues(horizontal = 6.dp, vertical = 6.dp)
    val btnShape = RoundedCornerShape(8.dp)

    // 46.dp = matches the compact Age field height in the same row
    Box(
        modifier
            .height(46.dp)
            .border(1.dp, DreamlandMuted.copy(alpha = 0.4f), RoundedCornerShape(4.dp)),
        contentAlignment = Alignment.Center,
    ) {
    Row(Modifier.fillMaxWidth().padding(horizontal = 4.dp), horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
        // Day
        Box(Modifier.weight(1f)) {
            OutlinedButton(onClick = { dayExpanded = true }, shape = btnShape, border = btnBorder,
                contentPadding = btnPad, modifier = Modifier.fillMaxWidth(),
            ) { Text("%02d".format(selDay), color = DreamlandOnDark, fontSize = 12.sp, fontWeight = FontWeight.Medium) }
            DropdownMenu(expanded = dayExpanded, onDismissRequest = { dayExpanded = false },
                modifier = Modifier.background(DreamlandForestElevated).heightIn(max = 200.dp)) {
                (1..31).forEach { d ->
                    DropdownMenuItem(text = { Text("%02d".format(d), color = DreamlandOnDark, fontSize = 12.sp) },
                        onClick = { selDay = d; dayExpanded = false; commit() })
                }
            }
        }
        Text("/", color = DreamlandMuted, fontSize = 13.sp)
        // Month
        Box(Modifier.weight(1.2f)) {
            OutlinedButton(onClick = { monthExpanded = true }, shape = btnShape, border = btnBorder,
                contentPadding = btnPad, modifier = Modifier.fillMaxWidth(),
            ) { Text(monthNames.getOrElse(selMonth - 1) { "Jan" }, color = DreamlandOnDark, fontSize = 12.sp, fontWeight = FontWeight.Medium) }
            DropdownMenu(expanded = monthExpanded, onDismissRequest = { monthExpanded = false },
                modifier = Modifier.background(DreamlandForestElevated).heightIn(max = 200.dp)) {
                monthNames.forEachIndexed { i, name ->
                    DropdownMenuItem(text = { Text(name, color = DreamlandOnDark, fontSize = 12.sp) },
                        onClick = { selMonth = i + 1; monthExpanded = false; commit() })
                }
            }
        }
        Text("/", color = DreamlandMuted, fontSize = 13.sp)
        // Year
        Box(Modifier.weight(1.5f)) {
            OutlinedButton(onClick = { yearExpanded = true }, shape = btnShape, border = btnBorder,
                contentPadding = btnPad, modifier = Modifier.fillMaxWidth(),
            ) { Text(selYear.toString(), color = DreamlandOnDark, fontSize = 12.sp, fontWeight = FontWeight.Medium) }
            DropdownMenu(expanded = yearExpanded, onDismissRequest = { yearExpanded = false },
                modifier = Modifier.background(DreamlandForestElevated).heightIn(max = 220.dp)) {
                (currentYear downTo 1920).forEach { y ->
                    DropdownMenuItem(text = { Text(y.toString(), color = DreamlandOnDark, fontSize = 12.sp) },
                        onClick = { selYear = y; yearExpanded = false; commit() })
                }
            }
        }
    }   // end inner Row
    }   // end outer Box
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
                    OutlinedTextField(
                        value = entry.phone,
                        onValueChange = { onPhoneChange(it.filter(Char::isDigit).take(10)) },
                        label = { Text("Phone", color = DreamlandMuted, fontSize = 13.sp) },
                        prefix = { Text("+91 ", color = DreamlandMuted, fontSize = 13.sp) },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                        isError = entry.phone.isNotBlank() && entry.phone.length != 10,
                        modifier = Modifier.weight(1f),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = DreamlandOnDark, unfocusedTextColor = DreamlandOnDark,
                            focusedBorderColor = DreamlandGold, unfocusedBorderColor = DreamlandMuted.copy(alpha = 0.4f),
                            cursorColor = DreamlandGold, errorBorderColor = Color(0xFFEF5350),
                        ),
                    )
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
    val subtotal = roomTotal + breakfastCharge

    // Tax — per category using Room.taxPercentage
    data class TaxLine(val label: String, val rate: Double, val amount: Double)
    val taxLines = buildList {
        val catIds = if (grouped.isNotEmpty()) grouped.keys
            else state.bookingRoomCountsByCategory.keys.ifEmpty { listOfNotNull(state.selectedCategoryId.takeIf { it.isNotBlank() }) }.toSet()
        for (catId in catIds) {
            val cat = state.categories.find { it.id == catId } ?: continue
            if (cat.taxPercentage <= 0.0) continue
            val charge = when {
                grouped.isNotEmpty() -> {
                    val insts = grouped[catId] ?: continue
                    val price = state.categoryPrices[catId] ?: cat.pricePerNight
                    price * nights * insts.size
                }
                else -> {
                    val count = state.bookingRoomCountsByCategory[catId] ?: 1
                    val price = state.categoryPrices[catId] ?: cat.pricePerNight
                    price * nights * count
                }
            }
            add(TaxLine("Tax (${cat.taxPercentage.toInt()}%)", cat.taxPercentage, charge * cat.taxPercentage / 100.0))
        }
    }
    val taxTotal = taxLines.sumOf { it.amount }
    val total = subtotal + taxTotal

    Card(modifier = Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = DreamlandForestElevated), shape = RoundedCornerShape(10.dp)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            roomRows.forEach { (label, charge) ->
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(label, color = DreamlandMuted, style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f))
                    Text("₹${"%,.2f".format(charge)}", color = DreamlandOnDark, style = MaterialTheme.typography.bodySmall)
                }
            }
            if (state.breakfast) {
                HorizontalDivider(color = DreamlandMuted.copy(alpha = 0.15f))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("Breakfast — ${state.adults} pax × $nights nights", color = DreamlandMuted, style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f))
                    Text("₹${"%,.2f".format(breakfastCharge)}", color = DreamlandOnDark, style = MaterialTheme.typography.bodySmall)
                }
            }
            if (taxLines.isNotEmpty()) {
                HorizontalDivider(color = DreamlandMuted.copy(alpha = 0.15f))
                taxLines.forEach { tx ->
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(tx.label, color = DreamlandMuted, style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f))
                        Text("₹${"%,.2f".format(tx.amount)}", color = DreamlandOnDark, style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
            HorizontalDivider(color = DreamlandMuted.copy(alpha = 0.2f))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("Total", color = DreamlandOnDark, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                Text("₹${"%,.2f".format(total)}", color = DreamlandGold, fontWeight = FontWeight.Bold, fontSize = 18.sp)
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

private val ID_TYPE_OPTIONS = listOf("Aadhaar", "PAN", "Passport", "Driving Licence", "Voter ID", "Other")

/** Free-text "Purpose of Visit" field with type-to-filter suggestions from purposeType + an Add option. */
@Composable
private fun PurposeAutocompleteField(
    modifier: Modifier = Modifier,
    value: String,
    options: List<String>,
    onValueChange: (String) -> Unit,
    onAdd: (String) -> Unit,
    colors: androidx.compose.material3.TextFieldColors,
) {
    var expanded by remember { mutableStateOf(false) }
    val query = value.trim()
    val matches = options.filter { it.contains(query, ignoreCase = true) }.take(6)
    val showAdd = query.isNotEmpty() && options.none { it.equals(query, ignoreCase = true) }
    Box(modifier) {
        CompactField(
            value = value,
            onValueChange = { onValueChange(it); expanded = true },
            placeholder = "Business, Tourism…",
            colors = colors,
            modifier = Modifier.fillMaxWidth().onFocusChanged { if (it.isFocused) expanded = true },
        )
        DropdownMenu(
            expanded = expanded && (matches.isNotEmpty() || showAdd),
            onDismissRequest = { expanded = false },
            properties = PopupProperties(focusable = false),
        ) {
            matches.forEach { p ->
                DropdownMenuItem(
                    text = { Text(p, color = DreamlandOnDark, fontSize = 13.sp) },
                    onClick = { onValueChange(p); expanded = false },
                )
            }
            if (showAdd) {
                DropdownMenuItem(
                    text = { Text("Add \"$query\"", color = DreamlandGold, fontWeight = FontWeight.SemiBold, fontSize = 13.sp) },
                    onClick = { onAdd(query); expanded = false },
                )
            }
        }
    }
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
