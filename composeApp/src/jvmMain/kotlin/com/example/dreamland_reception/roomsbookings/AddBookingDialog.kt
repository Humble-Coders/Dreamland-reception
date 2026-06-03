package com.example.dreamland_reception.roomsbookings

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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.IntSize
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
import com.example.dreamland_reception.data.model.Room
import com.example.dreamland_reception.stays.DreamlandTextField
import com.example.dreamland_reception.stays.SectionLabel
import com.example.dreamland_reception.stays.DateSelectorField
import com.example.dreamland_reception.util.dateFromPicker
import com.example.dreamland_reception.util.toMidnightUtc
import com.example.dreamland_reception.ui.viewmodel.RoomCategoryEntry
import com.example.dreamland_reception.ui.viewmodel.RoomsAndBookingsUiState
import com.example.dreamland_reception.ui.viewmodel.RoomsAndBookingsViewModel
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

@Composable
fun AddBookingDialog(
    state: RoomsAndBookingsUiState,
    vm: RoomsAndBookingsViewModel,
) {
    var guestName by remember { mutableStateOf("") }
    var guestPhone by remember { mutableStateOf("") }
    // categoryId → room count (0 = not selected)
    var roomCounts by remember { mutableStateOf(mapOf<String, Int>()) }
    var checkIn by remember { mutableStateOf<Date?>(null) }
    var checkOut by remember { mutableStateOf<Date?>(null) }
    var adults by remember { mutableStateOf(1) }
    var children by remember { mutableStateOf(0) }
    var totalAmountText by remember { mutableStateOf("") }
    var advancePaidText by remember { mutableStateOf("") }
    var advancePaymentMethod by remember { mutableStateOf("CASH") }
    var breakfastIncluded by remember { mutableStateOf(false) }
    var sourceText by remember { mutableStateOf("") }
    var sourceDropdownExpanded by remember { mutableStateOf(false) }
    var notesText by remember { mutableStateOf("") }
    var validationError by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(checkIn, checkOut) {
        val ci = checkIn; val co = checkOut
        if (ci != null && co != null && co.after(ci)) {
            vm.computeAvailableRoomsForNewBooking(ci, co)
        }
    }

    val datesSet = checkIn != null && checkOut != null && checkOut!!.after(checkIn!!)

    val filteredSources = if (sourceText.isBlank()) state.bookingSources
        else state.bookingSources.filter { it.name.contains(sourceText, ignoreCase = true) }
    val showAddSourceOption = sourceText.isNotBlank() &&
        state.bookingSources.none { it.name.equals(sourceText, ignoreCase = true) }

    // Compute auto total from selected rooms + nights
    val nightsComputed = if (datesSet)
        java.time.temporal.ChronoUnit.DAYS.between(checkIn!!.toMidnightUtc().toInstant(), checkOut!!.toMidnightUtc().toInstant()).coerceAtLeast(1)
    else 0L
    val autoTotal = if (nightsComputed > 0) {
        roomCounts.entries.sumOf { (catId, count) ->
            val cat = state.roomCategories.find { it.id == catId }
            val price = cat?.pricePerNight ?: 0.0
            price * count * nightsComputed
        }
    } else 0.0
    val totalRoomsSelected = roomCounts.values.sum()

    fun onSubmit() {
        val filledEntries = roomCounts.entries
            .filter { it.value > 0 }
            .flatMap { (catId, count) ->
                val catName = state.categoryNames[catId] ?: ""
                List(count) { RoomCategoryEntry(categoryId = catId, categoryName = catName) }
            }
        validationError = when {
            guestName.isBlank()    -> "Guest name is required"
            sourceText.isBlank()   -> "Booking source is required"
            filledEntries.isEmpty() -> "Select at least one room"
            checkIn == null        -> "Check-in date is required"
            checkOut == null       -> "Check-out date is required"
            checkOut != null && !checkOut!!.after(checkIn) -> "Check-out must be after check-in"
            else -> null
        }
        if (validationError != null) return
        val finalTotal = totalAmountText.toDoubleOrNull()?.takeIf { it > 0 } ?: autoTotal
        vm.addBooking(
            guestName         = guestName,
            guestPhone        = guestPhone,
            rooms             = filledEntries,
            checkIn           = checkIn!!,
            checkOut          = checkOut!!,
            adults            = adults,
            children          = children,
            totalAmount       = finalTotal,
            advancePaidAmount = advancePaidText.toDoubleOrNull() ?: 0.0,
            advancePaymentMethod = advancePaymentMethod,
            source            = sourceText,
            notes             = notesText,
            breakfastIncluded = breakfastIncluded,
            breakfastPricePerDay = if (breakfastIncluded) {
                val firstCatId = roomCounts.entries.firstOrNull { it.value > 0 }?.key
                state.roomCategories.find { it.id == firstCatId }?.breakfastPrice ?: 0.0
            } else 0.0,
        )
    }

    Dialog(
        onDismissRequest = vm::closeAddBookingDialog,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth(0.72f)
                .heightIn(max = 820.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(DreamlandForestSurface)
                .padding(24.dp),
        ) {
            Column(modifier = Modifier.verticalScroll(rememberScrollState())) {

                // ── Header ────────────────────────────────────────────────────
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column {
                        Text("ADD BOOKING", style = MaterialTheme.typography.labelLarge, color = DreamlandGold, letterSpacing = 2.sp)
                        Text("New Booking", style = MaterialTheme.typography.headlineMedium, color = DreamlandOnDark)
                    }
                    TextButton(onClick = vm::closeAddBookingDialog) {
                        Text("Cancel", color = DreamlandMuted)
                    }
                }

                Spacer(Modifier.height(24.dp))

                // ── Guest Details ─────────────────────────────────────────────
                SectionLabel("Guest Details")
                Spacer(Modifier.height(10.dp))
                DreamlandTextField(modifier = Modifier.fillMaxWidth(), value = guestName, onValueChange = { guestName = it }, label = "Guest Name *")
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = guestPhone,
                    onValueChange = { guestPhone = it.filter(Char::isDigit).take(10) },
                    label = { Text("Phone", color = DreamlandMuted, fontSize = 13.sp) },
                    prefix = { Text("+91 ", color = DreamlandMuted, fontSize = 13.sp) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                    isError = guestPhone.isNotBlank() && guestPhone.length != 10,
                    modifier = Modifier.fillMaxWidth(),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = DreamlandOnDark, unfocusedTextColor = DreamlandOnDark,
                        focusedBorderColor = DreamlandGold, unfocusedBorderColor = DreamlandMuted.copy(alpha = 0.4f),
                        cursorColor = DreamlandGold, errorBorderColor = Color(0xFFEF5350),
                    ),
                )

                Spacer(Modifier.height(20.dp))

                // ── Stay Dates ────────────────────────────────────────────────
                SectionLabel("Stay Details")
                Spacer(Modifier.height(8.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    DateSelectorField(modifier = Modifier.weight(1f), label = "Check-in *", date = checkIn, onDateSelected = { checkIn = it }, minDate = null)
                    DateSelectorField(modifier = Modifier.weight(1f), label = "Check-out *", date = checkOut, onDateSelected = { checkOut = it }, minDate = checkIn)
                }
                Spacer(Modifier.height(10.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Box(Modifier.weight(1f)) {
                        CounterField("Adults", adults, onIncrement = { adults++ }, onDecrement = { if (adults > 1) adults-- })
                    }
                    Box(Modifier.weight(1f)) {
                        CounterField("Children", children, onIncrement = { children++ }, onDecrement = { if (children > 0) children-- })
                    }
                }

                Spacer(Modifier.height(20.dp))

                // ── Room Selection — category count grid ──────────────────────
                SectionLabel("Room Selection")
                Spacer(Modifier.height(4.dp))
                if (datesSet) {
                    Text("Showing availability for selected dates", style = MaterialTheme.typography.bodySmall, color = DreamlandMuted)
                } else {
                    Text("Set dates to see real-time availability", style = MaterialTheme.typography.bodySmall, color = DreamlandMuted)
                }
                Spacer(Modifier.height(10.dp))

                if (state.roomCategories.isEmpty()) {
                    Text("No room categories found", style = MaterialTheme.typography.bodySmall, color = DreamlandMuted)
                } else {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        state.roomCategories
                            .filter { it.available }
                            .sortedBy { it.type }
                            .forEach { cat ->
                                CategoryCountRow(
                                    category = cat,
                                    count = roomCounts[cat.id] ?: 0,
                                    availableCount = if (datesSet) state.newBookingCategoryAvailability[cat.id] else null,
                                    nights = nightsComputed,
                                    onCountChange = { newCount ->
                                        roomCounts = roomCounts.toMutableMap().also {
                                            if (newCount <= 0) it.remove(cat.id)
                                            else it[cat.id] = newCount
                                        }
                                    },
                                )
                            }
                    }
                }

                // Selection summary
                if (totalRoomsSelected > 0) {
                    Spacer(Modifier.height(10.dp))
                    val breakdown = roomCounts.entries
                        .filter { it.value > 0 }
                        .joinToString(" · ") { (catId, count) ->
                            val name = state.categoryNames[catId] ?: catId
                            "$name × $count"
                        }
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .background(DreamlandGold.copy(alpha = 0.08f))
                            .border(1.dp, DreamlandGold.copy(alpha = 0.25f), RoundedCornerShape(8.dp))
                            .padding(horizontal = 14.dp, vertical = 10.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column {
                            Text(
                                "$totalRoomsSelected room${if (totalRoomsSelected != 1) "s" else ""} selected",
                                color = DreamlandGold,
                                fontWeight = FontWeight.SemiBold,
                                style = MaterialTheme.typography.bodyMedium,
                            )
                            Text(breakdown, color = DreamlandMuted, style = MaterialTheme.typography.bodySmall)
                        }
                        if (totalRoomsSelected > 1) {
                            Box(
                                Modifier
                                    .clip(RoundedCornerShape(4.dp))
                                    .background(DreamlandGold.copy(alpha = 0.15f))
                                    .padding(horizontal = 8.dp, vertical = 3.dp),
                            ) {
                                Text("GROUP BOOKING", color = DreamlandGold, style = MaterialTheme.typography.labelSmall, fontSize = 10.sp, letterSpacing = 1.sp)
                            }
                        }
                    }
                }

                Spacer(Modifier.height(20.dp))

                // ── Billing ───────────────────────────────────────────────────
                SectionLabel("Billing")
                Spacer(Modifier.height(8.dp))

                // Auto-computed total hint
                if (autoTotal > 0) {
                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text("Estimated total ($totalRoomsSelected rooms × $nightsComputed nights):", color = DreamlandMuted, style = MaterialTheme.typography.bodySmall)
                        Text("₹${autoTotal.toLong()}", color = DreamlandOnDark, fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.bodySmall)
                    }
                    Spacer(Modifier.height(6.dp))
                }

                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    DreamlandTextField(
                        modifier = Modifier.weight(1f),
                        value = totalAmountText,
                        onValueChange = { totalAmountText = it },
                        label = if (autoTotal > 0) "Total Amount (₹, auto: ${autoTotal.toLong()})" else "Total Amount (₹)",
                        keyboardType = KeyboardType.Decimal,
                    )
                    DreamlandTextField(
                        modifier = Modifier.weight(1f),
                        value = advancePaidText,
                        onValueChange = { advancePaidText = it },
                        label = "Advance Paid (₹)",
                        keyboardType = KeyboardType.Decimal,
                    )
                }
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf("CASH", "BANK").forEach { method ->
                        val selected = advancePaymentMethod == method
                        Box(
                            Modifier
                                .clip(RoundedCornerShape(6.dp))
                                .background(if (selected) DreamlandGold.copy(alpha = 0.15f) else Color.Transparent)
                                .border(1.dp, if (selected) DreamlandGold else DreamlandMuted.copy(alpha = 0.3f), RoundedCornerShape(6.dp))
                                .clickable { advancePaymentMethod = method }
                                .padding(horizontal = 16.dp, vertical = 8.dp),
                        ) { Text(method, color = if (selected) DreamlandGold else DreamlandMuted, fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal, fontSize = 13.sp) }
                    }
                }

                Spacer(Modifier.height(20.dp))

                // ── Booking Source ────────────────────────────────────────────
                SectionLabel("Booking Source")
                Spacer(Modifier.height(8.dp))

                var sourceTriggerSize by remember { mutableStateOf(IntSize.Zero) }
                val density = LocalDensity.current

                Box(Modifier.fillMaxWidth().onSizeChanged { sourceTriggerSize = it }) {
                    OutlinedTextField(
                        value = sourceText,
                        onValueChange = { sourceText = it; sourceDropdownExpanded = true },
                        label = { Text("Source (e.g. OTA, Phone)", color = DreamlandMuted, fontSize = 13.sp) },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        trailingIcon = {
                            if (sourceText.isNotBlank()) {
                                IconButton(onClick = { sourceText = ""; sourceDropdownExpanded = false }) {
                                    Icon(Icons.Filled.Close, contentDescription = "Clear", tint = DreamlandMuted, modifier = Modifier.size(16.dp))
                                }
                            }
                        },
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = DreamlandOnDark,
                            unfocusedTextColor = DreamlandOnDark,
                            focusedBorderColor = DreamlandGold,
                            unfocusedBorderColor = DreamlandMuted.copy(alpha = 0.4f),
                            cursorColor = DreamlandGold,
                        ),
                    )
                    DropdownMenu(
                        expanded = sourceDropdownExpanded && (filteredSources.isNotEmpty() || showAddSourceOption),
                        onDismissRequest = { sourceDropdownExpanded = false },
                        properties = PopupProperties(focusable = false),
                        modifier = Modifier.width(with(density) { sourceTriggerSize.width.toDp() }),
                    ) {
                        filteredSources.forEach { source ->
                            DropdownMenuItem(
                                modifier = Modifier.fillMaxWidth(),
                                text = { Text(source.name, color = DreamlandOnDark, style = MaterialTheme.typography.bodyMedium) },
                                onClick = { sourceText = source.name; sourceDropdownExpanded = false },
                            )
                            HorizontalDivider(color = DreamlandGold.copy(alpha = 0.08f))
                        }
                        if (showAddSourceOption) {
                            if (filteredSources.isNotEmpty()) HorizontalDivider(color = DreamlandMuted.copy(alpha = 0.2f))
                            DropdownMenuItem(
                                modifier = Modifier.fillMaxWidth(),
                                text = { Text("Add \"$sourceText\"", color = DreamlandGold, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold) },
                                onClick = { vm.addBookingSource(sourceText); sourceDropdownExpanded = false },
                            )
                        }
                    }
                }

                Spacer(Modifier.height(20.dp))

                // ── Notes ─────────────────────────────────────────────────────
                SectionLabel("Notes")
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = notesText,
                    onValueChange = { notesText = it },
                    label = { Text("Notes (optional)", color = DreamlandMuted, fontSize = 13.sp) },
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 2,
                    maxLines = 4,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = DreamlandOnDark,
                        unfocusedTextColor = DreamlandOnDark,
                        focusedBorderColor = DreamlandGold,
                        unfocusedBorderColor = DreamlandMuted.copy(alpha = 0.4f),
                        cursorColor = DreamlandGold,
                    ),
                )

                if (validationError != null) {
                    Spacer(Modifier.height(12.dp))
                    Text(validationError!!, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                }

                Spacer(Modifier.height(24.dp))

                // ── Actions ───────────────────────────────────────────────────
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    TextButton(onClick = vm::closeAddBookingDialog) {
                        Text("Cancel", color = DreamlandMuted)
                    }
                    Spacer(Modifier.width(8.dp))
                    Button(
                        onClick = ::onSubmit,
                        enabled = !state.addBookingLoading,
                        colors = ButtonDefaults.buttonColors(containerColor = DreamlandGold),
                        shape = RoundedCornerShape(10.dp),
                    ) {
                        if (state.addBookingLoading) {
                            CircularProgressIndicator(modifier = Modifier.size(18.dp), color = Color(0xFF0D1F17), strokeWidth = 2.dp)
                        } else {
                            val label = when {
                                totalRoomsSelected > 1 -> "Add $totalRoomsSelected Bookings"
                                else -> "Add Booking"
                            }
                            Text(label, color = Color(0xFF0D1F17), fontWeight = FontWeight.SemiBold)
                        }
                    }
                }
            }
        }
    }
}

// ── Category count row ────────────────────────────────────────────────────────

@Composable
private fun CategoryCountRow(
    category: Room,
    count: Int,
    availableCount: Int?,   // null = dates not set
    nights: Long,
    onCountChange: (Int) -> Unit,
) {
    val isSelected = count > 0
    val maxAllowed = availableCount ?: Int.MAX_VALUE
    val soldOut = availableCount != null && availableCount == 0

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(if (isSelected) DreamlandGold.copy(alpha = 0.06f) else DreamlandForestElevated)
            .border(
                1.dp,
                if (isSelected) DreamlandGold.copy(alpha = 0.5f) else DreamlandGold.copy(alpha = 0.12f),
                RoundedCornerShape(10.dp),
            )
            .padding(horizontal = 16.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // Category info
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    category.type,
                    color = if (soldOut) DreamlandMuted else DreamlandOnDark,
                    fontWeight = FontWeight.SemiBold,
                    style = MaterialTheme.typography.bodyMedium,
                )
                if (soldOut) {
                    Box(
                        Modifier.clip(RoundedCornerShape(4.dp)).background(Color(0xFFEF5350).copy(alpha = 0.15f)).padding(horizontal = 6.dp, vertical = 2.dp),
                    ) {
                        Text("SOLD OUT", color = Color(0xFFEF5350), style = MaterialTheme.typography.labelSmall, fontSize = 9.sp, letterSpacing = 1.sp)
                    }
                }
            }
            Spacer(Modifier.height(2.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("₹${category.pricePerNight.toLong()}/night", color = DreamlandMuted, style = MaterialTheme.typography.bodySmall)
                if (availableCount != null && !soldOut) {
                    Text("·", color = DreamlandMuted, style = MaterialTheme.typography.bodySmall)
                    Text(
                        "$availableCount avail.",
                        color = Color(0xFF2ECC71),
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.Medium,
                    )
                }
                if (nights > 0 && isSelected) {
                    Text("·", color = DreamlandMuted, style = MaterialTheme.typography.bodySmall)
                    Text(
                        "₹${(category.pricePerNight * count * nights).toLong()} total",
                        color = DreamlandGold,
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.Medium,
                    )
                }
            }
        }

        Spacer(Modifier.width(12.dp))

        // Count stepper
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(0.dp),
        ) {
            TextButton(
                onClick = { if (count > 0) onCountChange(count - 1) },
                enabled = count > 0,
                modifier = Modifier.size(36.dp),
            ) {
                Text(
                    "−",
                    color = if (count > 0) DreamlandGold else DreamlandMuted.copy(alpha = 0.3f),
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp,
                )
            }
            Text(
                "$count",
                color = if (isSelected) DreamlandGold else DreamlandMuted,
                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.widthIn(min = 24.dp),
            )
            TextButton(
                onClick = { if (count < maxAllowed && !soldOut) onCountChange(count + 1) },
                enabled = count < maxAllowed && !soldOut,
                modifier = Modifier.size(36.dp),
            ) {
                Text(
                    "+",
                    color = if (count < maxAllowed && !soldOut) DreamlandGold else DreamlandMuted.copy(alpha = 0.3f),
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp,
                )
            }
        }
    }
}

// ── Counter field ─────────────────────────────────────────────────────────────

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
            TextButton(onClick = onDecrement) { Text("-", color = DreamlandGold, fontWeight = FontWeight.Bold, fontSize = 18.sp) }
            Text("$value", color = DreamlandOnDark, fontWeight = FontWeight.SemiBold)
            TextButton(onClick = onIncrement) { Text("+", color = DreamlandGold, fontWeight = FontWeight.Bold, fontSize = 18.sp) }
        }
    }
}
