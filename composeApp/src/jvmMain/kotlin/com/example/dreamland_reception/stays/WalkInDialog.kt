package com.example.dreamland_reception.stays

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
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
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
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
import com.example.dreamland_reception.DreamlandForestElevated
import com.example.dreamland_reception.DreamlandForestSurface
import com.example.dreamland_reception.DreamlandGold
import com.example.dreamland_reception.DreamlandMuted
import com.example.dreamland_reception.DreamlandOnDark
import com.example.dreamland_reception.ui.viewmodel.GuestEntry
import com.example.dreamland_reception.ui.viewmodel.StaysViewModel
import com.example.dreamland_reception.ui.viewmodel.WalkInState
import java.text.SimpleDateFormat
import java.time.temporal.ChronoUnit
import java.util.Calendar
import java.util.Date
import java.util.Locale

@Composable
fun WalkInDialog(state: WalkInState, vm: StaysViewModel) {
    Dialog(
        onDismissRequest = { vm.closeWalkIn() },
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth(0.72f)
                .heightIn(max = 760.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(DreamlandForestSurface)
                .padding(24.dp),
        ) {
            Column(modifier = Modifier.verticalScroll(rememberScrollState())) {

                // ── Header ─────────────────────────────────────────────────
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column {
                        Text(
                            text = if (state.sourceBooking != null) "CHECK IN FROM BOOKING" else "WALK-IN CHECK-IN",
                            style = MaterialTheme.typography.labelLarge,
                            color = DreamlandGold,
                            letterSpacing = 2.sp,
                        )
                        Text("New Stay", style = MaterialTheme.typography.headlineMedium, color = DreamlandOnDark)
                    }
                    TextButton(onClick = { vm.closeWalkIn() }) {
                        Text("Cancel", color = DreamlandMuted)
                    }
                }

                Spacer(Modifier.height(24.dp))

                // ── 1. Guest Details ───────────────────────────────────────
                SectionLabel("Guest Details")
                Spacer(Modifier.height(10.dp))

                state.guestEntries.forEachIndexed { index, guest ->
                    GuestEntryRow(
                        index = index,
                        entry = guest,
                        isPrimary = index == 0,
                        onNameChange = { vm.onGuestName(index, it) },
                        onPhoneChange = { vm.onGuestPhone(index, it) },
                        onIdProofChange = { vm.onGuestIdProof(index, it) },
                        onRemove = { vm.removeGuest(index) },
                        canRemove = state.guestEntries.size > 1,
                    )
                    if (index < state.guestEntries.lastIndex) Spacer(Modifier.height(8.dp))
                }

                Spacer(Modifier.height(8.dp))
                TextButton(onClick = { vm.addGuest() }) {
                    Text("+ Add Guest", color = DreamlandGold, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                }

                Spacer(Modifier.height(20.dp))

                // ── 2. Room Selection ──────────────────────────────────────
                SectionLabel("Room Selection")
                Spacer(Modifier.height(8.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    DreamlandDropdown(
                        modifier = Modifier.weight(1f),
                        label = "Room Category *",
                        selectedText = if (state.selectedCategoryName.isBlank()) "Select category"
                                       else "${state.selectedCategoryName}  ·  ₹${state.categories.find { it.id == state.selectedCategoryId }?.pricePerNight?.toLong() ?: 0}/night",
                        options = state.categories.map { room ->
                            room.id to "${room.type}  ·  ₹${room.pricePerNight.toLong()}/night"
                        },
                        onSelected = vm::onCategorySelected,
                    )
                    Column(Modifier.weight(1f)) {
                        val isPreAssigned = state.sourceBooking?.roomInstanceId?.isNotBlank() == true &&
                            state.selectedInstanceId == state.sourceBooking.roomInstanceId
                        DreamlandDropdown(
                            label = "Room Number *",
                            selectedText = if (state.selectedInstanceNumber.isBlank()) "Select room" else "Room ${state.selectedInstanceNumber}",
                            options = state.availableInstances.map { inst ->
                                inst.id to "Room ${inst.roomNumber}  ·  ${inst.status}"
                            },
                            onSelected = vm::onInstanceSelected,
                            enabled = state.selectedCategoryId.isNotBlank(),
                            emptyText = if (state.selectedCategoryId.isNotBlank()) "No available rooms" else "Select category first",
                        )
                        if (isPreAssigned) {
                            Spacer(Modifier.height(4.dp))
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp),
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(6.dp)
                                        .clip(CircleShape)
                                        .background(Color(0xFF3498DB)),
                                )
                                Text(
                                    text = "Pre-assigned from booking · tap to change",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = Color(0xFF3498DB),
                                )
                            }
                        }
                    }
                }

                Spacer(Modifier.height(20.dp))

                // ── 3. Stay Details ────────────────────────────────────────
                SectionLabel("Stay Details")
                Spacer(Modifier.height(8.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Column(Modifier.weight(1f)) {
                        Text("Check-in", style = MaterialTheme.typography.labelMedium, color = DreamlandMuted)
                        Spacer(Modifier.height(4.dp))
                        ReadOnlyField(SimpleDateFormat("dd MMM yyyy, hh:mm a", Locale.getDefault()).format(Date()))
                    }
                    DateSelectorField(
                        modifier = Modifier.weight(1f),
                        label = "Expected Check-out *",
                        date = state.expectedCheckOut,
                        onDateSelected = vm::onWalkInExpectedCheckOut,
                    )
                }
                Spacer(Modifier.height(10.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Box(Modifier.weight(1f)) {
                        CounterField("Adults", state.adults,
                            onIncrement = { vm.onWalkInAdults(state.adults + 1) },
                            onDecrement = { vm.onWalkInAdults(state.adults - 1) },
                        )
                    }
                    Box(Modifier.weight(1f)) {
                        CounterField("Children", state.children,
                            onIncrement = { vm.onWalkInChildren(state.children + 1) },
                            onDecrement = { vm.onWalkInChildren(state.children - 1) },
                        )
                    }
                }

                Spacer(Modifier.height(20.dp))

                // ── 4. Options ─────────────────────────────────────────────
                SectionLabel("Options")
                Spacer(Modifier.height(8.dp))
                val breakfastPriceLabel = if (state.selectedCategoryBreakfastPrice > 0)
                    "₹${state.selectedCategoryBreakfastPrice.toLong()} per person per night"
                else
                    "Price set per room category"
                OptionToggle(
                    title = "Breakfast Included",
                    subtitle = breakfastPriceLabel,
                    checked = state.breakfast,
                    onChecked = vm::onWalkInBreakfast,
                )

                Spacer(Modifier.height(20.dp))

                // ── 5. Pricing Preview ─────────────────────────────────────
                PricingPreviewCard(state)

                Spacer(Modifier.height(16.dp))

                // ── 6. Advance Payment ─────────────────────────────────────
                SectionLabel("Advance Payment")
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = state.advancePayment,
                    onValueChange = vm::onWalkInAdvancePayment,
                    label = { Text("Advance Amount (₹)", color = DreamlandMuted, fontSize = 13.sp) },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = DreamlandOnDark,
                        unfocusedTextColor = DreamlandOnDark,
                        focusedBorderColor = DreamlandGold,
                        unfocusedBorderColor = DreamlandMuted.copy(alpha = 0.4f),
                        cursorColor = DreamlandGold,
                    ),
                )
                val advance = state.advancePayment.toDoubleOrNull() ?: 0.0
                if (advance > 0) {
                    val cat = state.categories.find { it.id == state.selectedCategoryId }
                    val nights = if (state.expectedCheckOut != null)
                        ChronoUnit.DAYS.between(Date().toInstant(), state.expectedCheckOut.toInstant()).coerceAtLeast(1)
                    else 1L
                    val roomCharge = (cat?.pricePerNight ?: 0.0) * nights
                    val bfCharge = if (state.breakfast) state.selectedCategoryBreakfastPrice * state.adults * nights else 0.0
                    val total = roomCharge + bfCharge
                    val pending = (total - advance).coerceAtLeast(0.0)
                    Spacer(Modifier.height(6.dp))
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("Remaining at check-out:", color = DreamlandMuted, style = MaterialTheme.typography.bodySmall)
                        Text("₹${pending.toLong()}", color = if (pending > 0) Color(0xFFFFC107) else Color(0xFF4CAF50), fontWeight = FontWeight.SemiBold)
                    }
                }

                Spacer(Modifier.height(16.dp))

                if (state.error != null) {
                    Text(state.error, color = Color(0xFFEF5350), style = MaterialTheme.typography.bodyMedium)
                    Spacer(Modifier.height(8.dp))
                }

                Button(
                    onClick = { vm.submitWalkIn() },
                    enabled = !state.isSaving,
                    modifier = Modifier.fillMaxWidth().height(52.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = DreamlandGold),
                    shape = RoundedCornerShape(10.dp),
                ) {
                    Text(
                        text = if (state.isSaving) "Checking in..." else "Check-in Guest",
                        color = Color(0xFF0D1F17),
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 16.sp,
                    )
                }
            }
        }
    }
}

// ── Guest entry row ───────────────────────────────────────────────────────────

@Composable
private fun GuestEntryRow(
    index: Int,
    entry: GuestEntry,
    isPrimary: Boolean,
    onNameChange: (String) -> Unit,
    onPhoneChange: (String) -> Unit,
    onIdProofChange: (Boolean) -> Unit,
    onRemove: () -> Unit,
    canRemove: Boolean,
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = DreamlandForestElevated),
        shape = RoundedCornerShape(10.dp),
    ) {
        Column(Modifier.fillMaxWidth().padding(12.dp)) {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = if (isPrimary) "Primary Guest" else "Guest ${index + 1}",
                    style = MaterialTheme.typography.labelMedium,
                    color = if (isPrimary) DreamlandGold else DreamlandMuted,
                    fontWeight = FontWeight.SemiBold,
                )
                if (canRemove && !isPrimary) {
                    TextButton(onClick = onRemove) {
                        Text("Remove", color = Color(0xFFEF5350), style = MaterialTheme.typography.labelSmall)
                    }
                }
            }
            Spacer(Modifier.height(8.dp))
            if (isPrimary) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    DreamlandTextField(
                        modifier = Modifier.weight(1f),
                        value = entry.name,
                        onValueChange = onNameChange,
                        label = "Full Name *",
                    )
                    DreamlandTextField(
                        modifier = Modifier.weight(1f),
                        value = entry.phone,
                        onValueChange = onPhoneChange,
                        label = "Phone Number",
                        keyboardType = KeyboardType.Phone,
                    )
                }
            } else {
                DreamlandTextField(
                    modifier = Modifier.fillMaxWidth(),
                    value = entry.name,
                    onValueChange = onNameChange,
                    label = "Full Name",
                )
            }
            Spacer(Modifier.height(10.dp))
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column {
                    Text("ID Proof Verified", color = DreamlandOnDark, style = MaterialTheme.typography.bodyMedium)
                    Text(
                        text = if (entry.idProofVerified) "Verified ✓" else "Not verified",
                        color = if (entry.idProofVerified) Color(0xFF4CAF50) else DreamlandMuted,
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
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

// ── Pricing preview ───────────────────────────────────────────────────────────

@Composable
private fun PricingPreviewCard(state: WalkInState) {
    val cat = state.categories.find { it.id == state.selectedCategoryId }
    val checkOut = state.expectedCheckOut
    val nights = if (checkOut != null)
        ChronoUnit.DAYS.between(Date().toInstant(), checkOut.toInstant()).coerceAtLeast(1)
    else 1L

    val roomCharge = (cat?.pricePerNight ?: 0.0) * nights
    val breakfastCharge = if (state.breakfast) state.selectedCategoryBreakfastPrice * state.adults * nights else 0.0
    val total = roomCharge + breakfastCharge

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = DreamlandForestElevated),
        shape = RoundedCornerShape(10.dp),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text("Pricing Preview", style = MaterialTheme.typography.labelLarge, color = DreamlandGold)
            HorizontalDivider(color = DreamlandGold.copy(alpha = 0.2f))
            if (cat != null) {
                PriceRow("Room — ${cat.type} × $nights nights @ ₹${cat.pricePerNight.toLong()}", roomCharge)
            } else {
                PriceRow("Room charges", 0.0, hint = "Select category")
            }
            if (state.breakfast) {
                PriceRow(
                    "Breakfast — ${state.adults} pax × $nights nights @ ₹${state.selectedCategoryBreakfastPrice.toLong()}",
                    breakfastCharge,
                )
            }
            HorizontalDivider(color = DreamlandGold.copy(alpha = 0.2f))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("Total", color = DreamlandOnDark, fontWeight = FontWeight.SemiBold)
                Text("₹${total.toLong()}", color = DreamlandGold, fontWeight = FontWeight.Bold, fontSize = 18.sp)
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
        value = value,
        onValueChange = onValueChange,
        label = { Text(label, color = DreamlandMuted, fontSize = 13.sp) },
        modifier = modifier,
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
        colors = OutlinedTextFieldDefaults.colors(
            focusedTextColor = DreamlandOnDark,
            unfocusedTextColor = DreamlandOnDark,
            focusedBorderColor = DreamlandGold,
            unfocusedBorderColor = DreamlandMuted.copy(alpha = 0.4f),
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
            modifier = Modifier
                .fillMaxWidth()
                .onSizeChanged { triggerSize = it }
                .clip(RoundedCornerShape(8.dp))
                .border(
                    1.dp,
                    if (enabled) DreamlandMuted.copy(alpha = 0.4f) else DreamlandMuted.copy(alpha = 0.15f),
                    RoundedCornerShape(8.dp),
                )
                .background(if (enabled) DreamlandForestElevated else DreamlandForestElevated.copy(alpha = 0.5f))
                .clickable(enabled = enabled) { expanded = true }
                .padding(horizontal = 16.dp, vertical = 14.dp),
        ) {
            Text(selectedText, color = if (enabled) DreamlandOnDark else DreamlandMuted, style = MaterialTheme.typography.bodyMedium)
        }
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
            modifier = Modifier.width(with(density) { triggerSize.width.toDp() }),
        ) {
            if (options.isEmpty()) {
                DropdownMenuItem(
                    modifier = Modifier.fillMaxWidth(),
                    text = { Text(emptyText, color = DreamlandMuted, modifier = Modifier.fillMaxWidth()) },
                    onClick = {},
                )
            } else {
                options.forEach { (id, lbl) ->
                    DropdownMenuItem(
                        modifier = Modifier.fillMaxWidth(),
                        text = {
                            Text(
                                lbl,
                                color = DreamlandOnDark,
                                style = MaterialTheme.typography.bodyMedium,
                                modifier = Modifier.fillMaxWidth(),
                            )
                        },
                        onClick = { onSelected(id); expanded = false },
                    )
                    HorizontalDivider(color = DreamlandGold.copy(alpha = 0.08f))
                }
            }
        }
    }
}

@Composable
private fun ReadOnlyField(text: String) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .border(1.dp, DreamlandMuted.copy(alpha = 0.2f), RoundedCornerShape(8.dp))
            .background(DreamlandForestElevated.copy(alpha = 0.6f))
            .padding(horizontal = 16.dp, vertical = 14.dp),
    ) {
        Text(text, color = DreamlandMuted, style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
private fun DateSelectorField(
    modifier: Modifier = Modifier,
    label: String,
    date: Date?,
    onDateSelected: (Date?) -> Unit,
) {
    var showPicker by remember { mutableStateOf(false) }
    val fmt = SimpleDateFormat("dd MMM yyyy", Locale.getDefault())
    Column(modifier) {
        Text(label, style = MaterialTheme.typography.labelMedium, color = DreamlandMuted)
        Spacer(Modifier.height(4.dp))
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(8.dp))
                .border(1.dp, DreamlandMuted.copy(alpha = 0.4f), RoundedCornerShape(8.dp))
                .background(DreamlandForestElevated)
                .clickable { showPicker = true }
                .padding(horizontal = 16.dp, vertical = 14.dp),
        ) {
            Text(
                text = if (date != null) fmt.format(date) else "Tap to set date",
                color = if (date != null) DreamlandOnDark else DreamlandMuted,
                style = MaterialTheme.typography.bodyMedium,
            )
        }
    }
    if (showPicker) {
        SimpleDatePickerDialog(
            initialDate = date ?: run { val c = Calendar.getInstance(); c.add(Calendar.DAY_OF_YEAR, 1); c.time },
            onDateSelected = { onDateSelected(it); showPicker = false },
            onDismiss = { showPicker = false },
        )
    }
}

@Composable
private fun SimpleDatePickerDialog(initialDate: Date, onDateSelected: (Date) -> Unit, onDismiss: () -> Unit) {
    val cal = Calendar.getInstance().apply { time = initialDate }
    var year by remember { mutableStateOf(cal.get(Calendar.YEAR)) }
    var month by remember { mutableStateOf(cal.get(Calendar.MONTH) + 1) }
    var day by remember { mutableStateOf(cal.get(Calendar.DAY_OF_MONTH)) }
    Dialog(onDismissRequest = onDismiss) {
        Card(colors = CardDefaults.cardColors(containerColor = DreamlandForestSurface), shape = RoundedCornerShape(16.dp)) {
            Column(Modifier.padding(24.dp)) {
                Text("Select Date", style = MaterialTheme.typography.titleMedium, color = DreamlandOnDark)
                Spacer(Modifier.height(16.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    NumberSpinner("Day", day, 1, 31) { day = it }
                    NumberSpinner("Month", month, 1, 12) { month = it }
                    NumberSpinner("Year", year, 2024, 2030) { year = it }
                }
                Spacer(Modifier.height(16.dp))
                Row(horizontalArrangement = Arrangement.End, modifier = Modifier.fillMaxWidth()) {
                    TextButton(onClick = onDismiss) { Text("Cancel", color = DreamlandMuted) }
                    Spacer(Modifier.width(8.dp))
                    Button(
                        onClick = { val c = Calendar.getInstance(); c.set(year, month - 1, day, 12, 0, 0); onDateSelected(c.time) },
                        colors = ButtonDefaults.buttonColors(containerColor = DreamlandGold),
                    ) { Text("Set", color = Color(0xFF0D1F17), fontWeight = FontWeight.SemiBold) }
                }
            }
        }
    }
}

@Composable
private fun NumberSpinner(label: String, value: Int, min: Int, max: Int, onChanged: (Int) -> Unit) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(label, style = MaterialTheme.typography.labelSmall, color = DreamlandMuted)
        Spacer(Modifier.height(4.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = { if (value > min) onChanged(value - 1) }) { Text("-", color = DreamlandGold, fontWeight = FontWeight.Bold) }
            Text("$value", color = DreamlandOnDark, modifier = Modifier.width(40.dp), textAlign = TextAlign.Center)
            TextButton(onClick = { if (value < max) onChanged(value + 1) }) { Text("+", color = DreamlandGold, fontWeight = FontWeight.Bold) }
        }
    }
}

@Composable
private fun CounterField(label: String, value: Int, onIncrement: () -> Unit, onDecrement: () -> Unit) {
    Column(Modifier.fillMaxWidth()) {
        Text(label, style = MaterialTheme.typography.labelMedium, color = DreamlandMuted)
        Spacer(Modifier.height(4.dp))
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(8.dp))
                .border(1.dp, DreamlandMuted.copy(alpha = 0.4f), RoundedCornerShape(8.dp))
                .background(DreamlandForestElevated),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TextButton(onClick = onDecrement) { Text("-", color = DreamlandGold, fontWeight = FontWeight.Bold, fontSize = 18.sp) }
            Text("$value", color = DreamlandOnDark, fontWeight = FontWeight.SemiBold)
            TextButton(onClick = onIncrement) { Text("+", color = DreamlandGold, fontWeight = FontWeight.Bold, fontSize = 18.sp) }
        }
    }
}

@Composable
private fun OptionToggle(title: String, subtitle: String, checked: Boolean, onChecked: (Boolean) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, color = DreamlandOnDark, style = MaterialTheme.typography.bodyMedium)
            Text(subtitle, color = DreamlandMuted, style = MaterialTheme.typography.bodySmall)
        }
        Switch(
            checked = checked,
            onCheckedChange = onChecked,
            colors = SwitchDefaults.colors(
                checkedThumbColor = Color(0xFF0D1F17),
                checkedTrackColor = DreamlandGold,
                uncheckedThumbColor = DreamlandMuted,
                uncheckedTrackColor = DreamlandForestElevated,
            ),
        )
    }
}

@Composable
private fun PriceRow(label: String, amount: Double, hint: String? = null) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, color = DreamlandMuted, style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f))
        Text(
            text = hint ?: "₹${amount.toLong()}",
            color = if (hint != null) DreamlandMuted.copy(alpha = 0.5f) else DreamlandOnDark,
            style = MaterialTheme.typography.bodySmall,
        )
    }
}
