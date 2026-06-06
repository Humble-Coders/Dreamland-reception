package com.example.dreamland_reception.stays

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.foundation.clickable
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.Alignment
import java.text.SimpleDateFormat
import java.time.temporal.ChronoUnit
import java.util.Calendar
import java.util.Date
import java.util.Locale
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.dreamland_reception.DreamlandForestElevated
import com.example.dreamland_reception.DreamlandForestSurface
import com.example.dreamland_reception.DreamlandGold
import com.example.dreamland_reception.DreamlandMuted
import com.example.dreamland_reception.DreamlandOnDark
import com.example.dreamland_reception.data.model.BillingInvoice
import com.example.dreamland_reception.data.model.Order
import com.example.dreamland_reception.data.model.Stay
import com.example.dreamland_reception.ui.viewmodel.CheckOutState
import com.example.dreamland_reception.ui.viewmodel.StaysViewModel

@Composable
fun CheckOutDialog(
    state: CheckOutState,
    vm: StaysViewModel,
    onNavigateToBilling: (stayId: String) -> Unit = {},
) {
    // Navigate to Billing after successful checkout
    LaunchedEffect(state.navigateToBilling) {
        if (state.navigateToBilling) {
            if (state.selectedBillGuestName.isNotBlank()) {
                com.example.dreamland_reception.DreamlandAppInitializer
                    .getStayBillingViewModel()
                    .setPendingGuestName(state.selectedBillGuestName)
            }
            onNavigateToBilling(state.checkedOutStayId)
            vm.onNavigateToBillingHandled()
        }
    }

    // Bill name picker — shown after checkout completes
    if (state.billNamePickerOpen) {
        BillNamePickerDialog(
            options = state.billNameOptions,
            onSelect = { name -> vm.selectBillGuestName(name) },
            onDismiss = { vm.dismissBillNamePicker() },
        )
    }

    if (!state.isOpen) return

    Dialog(
        onDismissRequest = { if (!state.isProcessing) vm.closeCheckOut() },
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth(0.5f)
                .heightIn(max = 760.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(DreamlandForestSurface)
                .padding(24.dp),
        ) {
            Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                Text("CHECK-OUT", style = MaterialTheme.typography.labelLarge, color = DreamlandGold, letterSpacing = 2.sp)
                state.stay?.let { stay ->
                    Text(stay.guestName, style = MaterialTheme.typography.headlineMedium, color = DreamlandOnDark, fontWeight = FontWeight.Bold)
                    Text("Room ${stay.roomNumber}", color = DreamlandMuted, style = MaterialTheme.typography.bodyMedium)
                    Spacer(Modifier.height(8.dp))
                    val dtFmt = SimpleDateFormat("d MMM, h:mm a", Locale.getDefault())
                    val timeParts = state.hotelCheckOutTime.split(":")
                    val expHour = timeParts.getOrNull(0)?.toIntOrNull() ?: 11
                    val expMin = timeParts.getOrNull(1)?.toIntOrNull() ?: 0
                    val expectedDateTime = Calendar.getInstance().apply {
                        time = stay.expectedCheckOut
                        set(Calendar.HOUR_OF_DAY, expHour)
                        set(Calendar.MINUTE, expMin)
                        set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
                    }.time
                    val actualNow = Date()
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Column {
                            Text("Expected Check-out", color = DreamlandMuted, style = MaterialTheme.typography.labelSmall)
                            Text(dtFmt.format(expectedDateTime), color = DreamlandOnDark, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Medium)
                        }
                        Column(horizontalAlignment = Alignment.End) {
                            Text("Actual Check-out", color = DreamlandMuted, style = MaterialTheme.typography.labelSmall)
                            Text(
                                dtFmt.format(actualNow),
                                color = if (state.isLateCheckout) Color(0xFFEF5350) else DreamlandOnDark,
                                style = MaterialTheme.typography.bodySmall,
                                fontWeight = FontWeight.Medium,
                            )
                        }
                    }
                }

                // ── Late checkout warning ──────────────────────────────────
                if (state.isLateCheckout) {
                    Spacer(Modifier.height(12.dp))
                    Card(
                        colors = CardDefaults.cardColors(containerColor = Color(0xFFEF5350).copy(alpha = 0.12f)),
                        shape = RoundedCornerShape(10.dp),
                    ) {
                        Column(Modifier.fillMaxWidth().padding(12.dp)) {
                            Text(
                                "Late Check-out",
                                color = Color(0xFFEF5350),
                                fontWeight = FontWeight.SemiBold,
                                style = MaterialTheme.typography.bodyMedium,
                            )
                            Text(
                                when {
                                    state.lateCheckoutCharge <= 0 -> "Guest is checking out past the scheduled checkout time."
                                    state.lateChargeType == "ROOM_RATE" -> "Room charges of ₹${"%.2f".format(state.lateCheckoutCharge)} will be added for the extra stay."
                                    else -> "Guest is checking out past the scheduled time. Late checkout charge of ₹${"%.2f".format(state.lateCheckoutCharge)} will be added."
                                },
                                color = Color(0xFFEF5350).copy(alpha = 0.85f),
                                style = MaterialTheme.typography.bodySmall,
                            )
                            if (state.flatLateCheckoutFee > 0 || state.roomPricePerNight > 0) {
                                Spacer(Modifier.height(10.dp))
                                Text(
                                    "Apply charge:",
                                    color = Color(0xFFEF5350).copy(alpha = 0.7f),
                                    style = MaterialTheme.typography.labelSmall,
                                )
                                Spacer(Modifier.height(2.dp))
                                if (state.flatLateCheckoutFee > 0) {
                                    Row(
                                        Modifier.fillMaxWidth().clickable { vm.onLateChargeType("FLAT") },
                                        verticalAlignment = Alignment.CenterVertically,
                                    ) {
                                        RadioButton(
                                            selected = state.lateChargeType == "FLAT",
                                            onClick = { vm.onLateChargeType("FLAT") },
                                            colors = RadioButtonDefaults.colors(
                                                selectedColor = Color(0xFFEF5350),
                                                unselectedColor = Color(0xFFEF5350).copy(alpha = 0.5f),
                                            ),
                                        )
                                        Text(
                                            "Late check-out fee  ₹${"%.2f".format(state.flatLateCheckoutFee)}",
                                            color = Color(0xFFEF5350).copy(alpha = 0.85f),
                                            style = MaterialTheme.typography.bodySmall,
                                        )
                                    }
                                }
                                if (state.roomPricePerNight > 0) {
                                    Row(
                                        Modifier.fillMaxWidth().clickable { vm.onLateChargeType("ROOM_RATE") },
                                        verticalAlignment = Alignment.CenterVertically,
                                    ) {
                                        RadioButton(
                                            selected = state.lateChargeType == "ROOM_RATE",
                                            onClick = { vm.onLateChargeType("ROOM_RATE") },
                                            colors = RadioButtonDefaults.colors(
                                                selectedColor = Color(0xFFEF5350),
                                                unselectedColor = Color(0xFFEF5350).copy(alpha = 0.5f),
                                            ),
                                        )
                                        Text(
                                            "Room charges (1 night)  ₹${"%.2f".format(state.roomPricePerNight)}",
                                            color = Color(0xFFEF5350).copy(alpha = 0.85f),
                                            style = MaterialTheme.typography.bodySmall,
                                        )
                                    }
                                }
                                Spacer(Modifier.height(6.dp))
                                DreamlandTextField(
                                    value = state.customLateChargeInput,
                                    onValueChange = vm::onLateChargeCustomInput,
                                    label = "Late charge amount (₹)",
                                    keyboardType = KeyboardType.Number,
                                    modifier = Modifier.fillMaxWidth(),
                                )
                            }
                        }
                    }
                }

                Spacer(Modifier.height(20.dp))

                // ── Step 1: Room selection (group only) ────────────────────
                if (state.groupStays.size > 1 && state.checkoutStep == 1) {
                    Text(
                        "SELECT ROOMS TO CHECK OUT",
                        style = MaterialTheme.typography.labelLarge,
                        color = DreamlandGold,
                        letterSpacing = 1.5.sp,
                    )
                    Spacer(Modifier.height(4.dp))
                    state.stay?.let { stay ->
                        Text(
                            "Room ${stay.roomNumber} is selected by default. Check additional rooms to include.",
                            color = DreamlandMuted,
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                    Spacer(Modifier.height(12.dp))
                    // Full-width checkbox list
                    state.groupStays.forEach { gs ->
                        val checked = gs.id in state.checkedGroupStayIds
                        val bill = state.groupBills[gs.id]
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .background(if (checked) DreamlandGold.copy(alpha = 0.06f) else DreamlandForestElevated)
                                .clickable { vm.toggleGroupStayCheck(gs.id) }
                                .padding(horizontal = 14.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            Checkbox(
                                checked = checked,
                                onCheckedChange = { vm.toggleGroupStayCheck(gs.id) },
                                colors = CheckboxDefaults.colors(
                                    checkedColor = DreamlandGold,
                                    uncheckedColor = DreamlandMuted.copy(alpha = 0.4f),
                                    checkmarkColor = Color(0xFF0D1F17),
                                ),
                            )
                            Column(Modifier.weight(1f)) {
                                Text("Room ${gs.roomNumber}", color = DreamlandOnDark, fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.bodyMedium)
                                Text(gs.guestName, color = DreamlandMuted, style = MaterialTheme.typography.bodySmall)
                            }
                            if (bill != null) {
                                Text("₹${"%.2f".format(bill.totalAmount)}", color = if (checked) DreamlandGold else DreamlandMuted, fontWeight = FontWeight.Medium, style = MaterialTheme.typography.bodySmall)
                            }
                        }
                        Spacer(Modifier.height(6.dp))
                    }
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "${state.checkedGroupStayIds.size} of ${state.groupStays.size} rooms selected",
                        color = DreamlandMuted,
                        style = MaterialTheme.typography.labelSmall,
                    )
                    Spacer(Modifier.height(16.dp))
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        TextButton(onClick = { vm.closeCheckOut() }, modifier = Modifier.weight(1f)) {
                            Text("Cancel", color = DreamlandMuted)
                        }
                        Button(
                            onClick = { vm.setCheckoutStep(2) },
                            enabled = state.checkedGroupStayIds.isNotEmpty(),
                            modifier = Modifier.weight(2f).height(52.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = DreamlandGold),
                            shape = RoundedCornerShape(10.dp),
                        ) {
                            Text("Continue →", color = Color(0xFF0D1F17), fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
                        }
                    }
                    return@Column  // skip step 2 content
                }

                // ── Step 2: Bill summary + confirm ─────────────────────────
                if (state.groupStays.size > 1) {
                    // Back + selected rooms summary
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        TextButton(
                            onClick = { vm.setCheckoutStep(1) },
                            contentPadding = PaddingValues(horizontal = 0.dp),
                        ) {
                            Text("← Back", color = DreamlandGold, style = MaterialTheme.typography.labelMedium)
                        }
                        Spacer(Modifier.weight(1f))
                        val selected = state.groupStays.filter { it.id in state.checkedGroupStayIds }
                        Text(
                            "Rooms: ${selected.joinToString(", ") { it.roomNumber }}",
                            color = DreamlandMuted,
                            style = MaterialTheme.typography.labelSmall,
                        )
                    }
                    Spacer(Modifier.height(8.dp))
                }

                // ── Bill Summary ───────────────────────────────────────────
                Card(
                    colors = CardDefaults.cardColors(containerColor = DreamlandForestElevated),
                    shape = RoundedCornerShape(12.dp),
                ) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("Bill Summary", style = MaterialTheme.typography.titleMedium, color = DreamlandGold, fontWeight = FontWeight.SemiBold)
                        HorizontalDivider(color = DreamlandGold.copy(alpha = 0.2f))

                        // Walk-ins (no booking behind the stay) hide tax — it's revealed only at
                        // the billing screen. Bookings show tax as normal.
                        val isWalkIn = (state.stay?.bookingId ?: "").isBlank()
                        // For group: aggregate bills for checked stays; for single: use the primary bill
                        val isGroup = state.groupStays.size > 1
                        val checkedBills = if (isGroup)
                            state.groupBills.filterKeys { it in state.checkedGroupStayIds }.values.toList()
                        else
                            listOfNotNull(state.bill)

                        if (checkedBills.isNotEmpty()) {
                            val totalRoomCharges = checkedBills.sumOf { it.roomCharges }
                            val totalService = checkedBills.sumOf { it.serviceCharges }
                            val totalEarlyCI = checkedBills.sumOf { it.earlyCheckInCharge }
                            val totalAmountPaid = checkedBills.sumOf { it.amountPaid }

                            // Nights actually billed = full days between check-in and now
                            // (matches the bill engine), so the room charge is never ambiguous.
                            val nowForNights = Date()
                            fun nightsFor(s: Stay?): Long = s?.let {
                                ChronoUnit.DAYS.between(it.checkInActual.toInstant(), nowForNights.toInstant())
                            }?.coerceAtLeast(1L) ?: 1L
                            fun nightsSublabel(nights: Long, charge: Double): String {
                                val perNight = if (nights > 0) charge / nights else charge
                                return "$nights night${if (nights > 1) "s" else ""} × ₹${"%.0f".format(perNight)}"
                            }

                            if (isGroup) {
                                // Per-room breakdown
                                checkedBills.forEach { b ->
                                    val n = nightsFor(state.groupStays.find { it.id == b.stayId })
                                    CheckOutBillRow(
                                        "Room ${b.roomNumber} charges",
                                        b.roomCharges + b.serviceCharges + b.earlyCheckInCharge,
                                        sublabel = if (b.roomCharges > 0) nightsSublabel(n, b.roomCharges) else null,
                                    )
                                }
                                HorizontalDivider(color = DreamlandMuted.copy(alpha = 0.15f))
                            } else {
                                val n = nightsFor(state.stay)
                                CheckOutBillRow(
                                    "Room Charges", totalRoomCharges,
                                    sublabel = if (totalRoomCharges > 0) nightsSublabel(n, totalRoomCharges) else null,
                                )
                                if (totalService > 0) CheckOutBillRow("Service Charges", totalService)
                                if (totalEarlyCI > 0) CheckOutBillRow("Early Check-in", totalEarlyCI)
                            }

                            if (state.ordersTotal > 0) CheckOutBillRow("Orders", state.ordersTotal)
                            val lateCharge = if (state.lateCheckoutCharge > 0) state.lateCheckoutCharge else checkedBills.sumOf { it.lateCheckOutCharge }
                            if (lateCharge > 0) CheckOutBillRow("Late Check-out", lateCharge)
                            val totalTax = checkedBills.sumOf { it.tax }
                            if (!isWalkIn && totalTax > 0) CheckOutBillRow("Tax", totalTax)
                            val firstBill = checkedBills.first()
                            if (firstBill.discount > 0) CheckOutBillRow("Discount", -firstBill.discount)

                            HorizontalDivider(color = DreamlandMuted.copy(alpha = 0.3f))
                            val extraLate = if (state.lateCheckoutCharge > 0 && checkedBills.all { it.lateCheckOutCharge == 0.0 }) state.lateCheckoutCharge else 0.0
                            val fullTotal = checkedBills.sumOf { it.totalAmount } + extraLate + state.ordersTotal
                            // Walk-ins show the pre-tax total (tax added at the billing screen).
                            val displayTotal = if (isWalkIn) (fullTotal - totalTax) else fullTotal
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text("Total", color = DreamlandOnDark, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                                Text("₹${"%.2f".format(displayTotal)}", color = DreamlandGold, fontWeight = FontWeight.Bold, fontSize = 20.sp)
                            }
                            if (totalAmountPaid > 0) CheckOutBillRow("Already Paid", totalAmountPaid)
                            val pending = displayTotal - totalAmountPaid
                            if (pending > 0) {
                                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                    Text("Amount Due", color = Color(0xFFEF5350), fontWeight = FontWeight.Bold, fontSize = 15.sp)
                                    Text("₹${"%.2f".format(pending)}", color = Color(0xFFEF5350), fontWeight = FontWeight.Bold, fontSize = 16.sp)
                                }
                            }
                        } else {
                            Text(
                                if (isGroup) "Select at least one room to see the bill" else "No billing record found",
                                color = DreamlandMuted,
                                style = MaterialTheme.typography.bodySmall,
                            )
                        }
                    }
                }

                Spacer(Modifier.height(16.dp))

                // ── Pending Orders ─────────────────────────────────────────
                if (state.pendingOrders.isNotEmpty()) {
                    Card(
                        colors = CardDefaults.cardColors(
                            containerColor = Color(0xFFF39C12).copy(alpha = 0.08f),
                        ),
                        shape = RoundedCornerShape(12.dp),
                    ) {
                        Column(Modifier.fillMaxWidth().padding(16.dp)) {
                            Row(
                                Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text(
                                    "Pending Orders",
                                    style = MaterialTheme.typography.titleSmall,
                                    color = Color(0xFFF39C12),
                                    fontWeight = FontWeight.SemiBold,
                                )
                                Text(
                                    "${state.checkedOrderIds.size} / ${state.pendingOrders.size} marked done",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = Color(0xFFF39C12).copy(alpha = 0.8f),
                                )
                            }
                            Spacer(Modifier.height(4.dp))
                            Text(
                                "Check orders to mark them as completed",
                                style = MaterialTheme.typography.bodySmall,
                                color = DreamlandMuted,
                            )
                            Spacer(Modifier.height(10.dp))
                            HorizontalDivider(color = Color(0xFFF39C12).copy(alpha = 0.2f))
                            Spacer(Modifier.height(6.dp))
                            state.pendingOrders.forEach { order ->
                                PendingOrderRow(
                                    order = order,
                                    checked = order.id in state.checkedOrderIds,
                                    onToggle = { vm.toggleOrderCheck(order.id) },
                                )
                            }
                        }
                    }
                    Spacer(Modifier.height(16.dp))
                }

                if (state.error != null) {
                    Text(state.error, color = Color(0xFFEF5350), style = MaterialTheme.typography.bodySmall)
                    Spacer(Modifier.height(8.dp))
                }

                Spacer(Modifier.height(8.dp))

                // ── CTAs ───────────────────────────────────────────────────
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    TextButton(
                        onClick = { vm.closeCheckOut() },
                        enabled = !state.isProcessing,
                        modifier = Modifier.weight(1f),
                    ) {
                        Text("Cancel", color = DreamlandMuted)
                    }
                    Button(
                        onClick = { vm.confirmCheckOut() },
                        enabled = !state.isProcessing,
                        modifier = Modifier.weight(2f).height(52.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFEF5350)),
                        shape = RoundedCornerShape(10.dp),
                    ) {
                        if (state.isProcessing) {
                            CircularProgressIndicator(color = Color.White, modifier = Modifier.width(20.dp).height(20.dp), strokeWidth = 2.dp)
                        } else {
                            Text("Confirm Check-out", color = Color.White, fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
                        }
                    }
                }
            }
        }
    }
}

// ── Group rooms section ───────────────────────────────────────────────────────

@Composable
private fun GroupRoomsSection(state: CheckOutState, vm: StaysViewModel) {
    Card(
        colors = CardDefaults.cardColors(containerColor = DreamlandForestElevated),
        shape = RoundedCornerShape(12.dp),
    ) {
        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("Group Rooms", style = MaterialTheme.typography.titleMedium, color = DreamlandGold, fontWeight = FontWeight.SemiBold)
                Text(
                    "${state.checkedGroupStayIds.size} / ${state.groupStays.size} selected",
                    style = MaterialTheme.typography.labelSmall,
                    color = DreamlandMuted,
                )
            }
            Text(
                "Check the rooms to include in this check-out",
                style = MaterialTheme.typography.bodySmall,
                color = DreamlandMuted,
            )
            HorizontalDivider(color = DreamlandGold.copy(alpha = 0.15f))
            state.groupStays.forEach { groupStay ->
                val checked = groupStay.id in state.checkedGroupStayIds
                val bill = state.groupBills[groupStay.id]
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { vm.toggleGroupStayCheck(groupStay.id) }
                        .padding(vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        modifier = Modifier.weight(1f),
                    ) {
                        Checkbox(
                            checked = checked,
                            onCheckedChange = { vm.toggleGroupStayCheck(groupStay.id) },
                            colors = CheckboxDefaults.colors(
                                checkedColor = DreamlandGold,
                                uncheckedColor = DreamlandMuted,
                                checkmarkColor = DreamlandForestSurface,
                            ),
                            modifier = Modifier.size(20.dp),
                        )
                        Column {
                            Text(
                                "Room ${groupStay.roomNumber}",
                                color = if (checked) DreamlandOnDark else DreamlandMuted,
                                style = MaterialTheme.typography.bodySmall,
                                fontWeight = if (checked) FontWeight.SemiBold else FontWeight.Normal,
                            )
                            Text(
                                groupStay.roomCategoryName.ifBlank { groupStay.guestName },
                                color = DreamlandMuted,
                                style = MaterialTheme.typography.labelSmall,
                            )
                        }
                    }
                    if (bill != null) {
                        Text(
                            "₹${"%.2f".format(bill.totalAmount)}",
                            color = if (checked) DreamlandOnDark else DreamlandMuted,
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.SemiBold,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun CheckOutBillRow(label: String, amount: Double, sublabel: String? = null) {
    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(label, color = DreamlandMuted, style = MaterialTheme.typography.bodySmall)
            if (!sublabel.isNullOrBlank()) {
                Text(sublabel, color = DreamlandMuted.copy(alpha = 0.6f), style = MaterialTheme.typography.labelSmall)
            }
        }
        Text(
            text = if (amount < 0) "-₹${"%.2f".format(-amount)}" else "₹${"%.2f".format(amount)}",
            color = DreamlandOnDark,
            style = MaterialTheme.typography.bodySmall,
        )
    }
}

@Composable
private fun PendingOrderRow(order: Order, checked: Boolean, onToggle: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onToggle)
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Row(
            modifier = Modifier.weight(1f),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Checkbox(
                checked = checked,
                onCheckedChange = { onToggle() },
                colors = CheckboxDefaults.colors(
                    checkedColor = Color(0xFF4CAF50),
                    uncheckedColor = DreamlandMuted,
                    checkmarkColor = DreamlandForestSurface,
                ),
                modifier = Modifier.size(20.dp),
            )
            Column {
                val itemSummary = order.items
                    .take(2)
                    .joinToString(", ") { "${it.quantity}× ${it.name}" }
                    .let { if (order.items.size > 2) "$it…" else it }
                    .ifBlank { order.type }
                Text(
                    itemSummary,
                    style = MaterialTheme.typography.bodySmall,
                    color = if (checked) DreamlandMuted else DreamlandOnDark,
                    fontWeight = if (checked) FontWeight.Normal else FontWeight.Medium,
                )
                Text(
                    order.status,
                    style = MaterialTheme.typography.labelSmall,
                    color = Color(0xFFF39C12).copy(alpha = 0.8f),
                    fontSize = 10.sp,
                )
            }
        }
        Text(
            "₹${"%.2f".format(order.totalAmount)}",
            style = MaterialTheme.typography.bodySmall,
            color = if (checked) DreamlandMuted else DreamlandOnDark,
            fontWeight = FontWeight.SemiBold,
        )
    }
}

// ── Bill Name Picker Dialog ───────────────────────────────────────────────────

@Composable
private fun BillNamePickerDialog(
    options: List<Triple<String, String, String>>,  // name, phone, room
    onSelect: (String) -> Unit,
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
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("SELECT BILL NAME", style = MaterialTheme.typography.labelLarge, color = DreamlandGold, letterSpacing = 2.sp)
                Text("Choose who to generate the bill for", color = DreamlandMuted, style = MaterialTheme.typography.bodySmall)
                HorizontalDivider(color = DreamlandGold.copy(alpha = 0.15f))
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    options.forEach { (name, phone, room) ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(10.dp))
                                .border(1.dp, DreamlandGold.copy(alpha = 0.4f), RoundedCornerShape(10.dp))
                                .background(DreamlandForestElevated)
                                .clickable { onSelect(name) }
                                .padding(horizontal = 16.dp, vertical = 14.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Column {
                                Text(name, color = DreamlandOnDark, fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.bodyMedium)
                                if (phone.isNotBlank()) Text(phone, color = DreamlandMuted, style = MaterialTheme.typography.labelSmall)
                                Text("Room $room", color = DreamlandMuted.copy(alpha = 0.7f), style = MaterialTheme.typography.labelSmall)
                            }
                            Text("✓", color = DreamlandGold, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    TextButton(onClick = onDismiss) {
                        Text("Cancel", color = DreamlandMuted)
                    }
                }
            }
        }
    }
}
