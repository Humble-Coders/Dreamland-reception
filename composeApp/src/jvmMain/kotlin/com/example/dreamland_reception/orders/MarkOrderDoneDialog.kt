package com.example.dreamland_reception.orders

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
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
import com.example.dreamland_reception.data.accounting.VendorBalanceInfo
import com.example.dreamland_reception.data.model.Order
import com.example.dreamland_reception.data.model.Vendor
import kotlin.math.abs

private val AMBER = Color(0xFFFFC107)
private val GREEN = Color(0xFF4CAF50)

/**
 * First step of "Mark Done": choose how the order was fulfilled.
 *  • In-house  → hotel made/served it (cold drink, water, tea…). Completes immediately.
 *  • Vendor    → bought from an outside supplier; opens the vendor & payment dialog.
 */
@Composable
fun MarkDoneChoiceDialog(
    order: Order,
    onInHouse: () -> Unit,
    onVendor: () -> Unit,
    onDismiss: () -> Unit,
) {
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Box(
            modifier = Modifier
                .fillMaxWidth(0.34f)
                .clip(RoundedCornerShape(16.dp))
                .background(DreamlandForestSurface)
                .padding(24.dp),
        ) {
            Column(Modifier.fillMaxWidth()) {
                Text("Mark Done", style = MaterialTheme.typography.titleMedium, color = DreamlandOnDark, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.height(16.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    ChoiceCard("In-house", "water, cold drink…", GREEN, onInHouse, Modifier.weight(1f))
                    ChoiceCard("Vendor", "outside supplier", DreamlandGold, onVendor, Modifier.weight(1f))
                }
                Spacer(Modifier.height(12.dp))
                TextButton(onClick = onDismiss, modifier = Modifier.align(Alignment.End)) {
                    Text("Cancel", color = DreamlandMuted)
                }
            }
        }
    }
}

@Composable
private fun ChoiceCard(title: String, hint: String, accent: Color, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .background(DreamlandForestElevated)
            .border(1.dp, accent.copy(alpha = 0.5f), RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .padding(vertical = 20.dp, horizontal = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(title, color = accent, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(4.dp))
        Text(hint, color = DreamlandMuted, style = MaterialTheme.typography.labelSmall)
    }
}

/**
 * "Mark Done" dialog for an order. Captures the outside **vendor** and the **cash/bank
 * split** paid to them now, showing the vendor's **current balance** up front. The
 * vendor's running balance is updated in Humble Ledger. **Skip** completes an in-house
 * order with no vendor. Adding a vendor opens a separate dialog on top.
 */
@Composable
fun MarkOrderDoneDialog(
    order: Order,
    vendors: List<Vendor>,
    onAddVendor: (name: String, phone: String, discountPercent: Double, onCreated: (Vendor) -> Unit) -> Unit,
    onLoadBalance: suspend (externalId: String) -> VendorBalanceInfo?,
    onConfirm: (vendor: Vendor, cost: Double, cashPaid: Double, bankPaid: Double) -> Unit,
    onSkip: () -> Unit,
    onDismiss: () -> Unit,
) {
    // The guest's actual (pre-tax) price for this order. Tax is applied on the final bill,
    // not here, so the vendor side works entirely off the actual price.
    val actualPrice = order.subtotalAmount

    var selectedVendor by remember { mutableStateOf<Vendor?>(null) }
    var dropdownOpen by remember { mutableStateOf(false) }
    var showAddVendor by remember { mutableStateOf(false) }

    var costText by remember { mutableStateOf(fmtMoney(actualPrice)) }
    var costEdited by remember { mutableStateOf(false) }
    var cashText by remember { mutableStateOf(fmtMoney(actualPrice)) }
    var cashEdited by remember { mutableStateOf(false) }
    var bankText by remember { mutableStateOf("0") }

    // Current balance of the selected vendor, fetched from Humble Ledger.
    var currentBalance by remember { mutableStateOf<VendorBalanceInfo?>(null) }
    var loadingBalance by remember { mutableStateOf(false) }
    LaunchedEffect(selectedVendor?.id) {
        val v = selectedVendor
        // Auto-fill the vendor cost from the vendor's discount on the actual price.
        // Respects a manual edit — once the user changes the cost we keep their value.
        if (v != null && !costEdited) {
            val computed = round2(actualPrice * (1 - v.discountPercent / 100.0))
            costText = fmtMoney(computed)
            if (!cashEdited) cashText = fmtMoney(computed)
        }
        currentBalance = null
        if (v != null) {
            loadingBalance = true
            currentBalance = onLoadBalance(v.id)
            loadingBalance = false
        }
    }

    val cost = costText.toDoubleOrNull() ?: 0.0
    val cash = cashText.toDoubleOrNull() ?: 0.0
    val bank = bankText.toDoubleOrNull() ?: 0.0
    val canConfirm = selectedVendor != null && cost > 0.0 && cash >= 0.0 && bank >= 0.0

    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Box(
            modifier = Modifier
                .fillMaxWidth(0.46f)
                .heightIn(max = 640.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(DreamlandForestSurface)
                .padding(24.dp),
        ) {
            Column(Modifier.fillMaxWidth()) {
                // ── Header (fixed) ─────────────────────────────────────────────
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Column {
                        Text("MARK DONE", style = MaterialTheme.typography.labelLarge, color = DreamlandGold, letterSpacing = 2.sp)
                        Text("Vendor & payment", style = MaterialTheme.typography.headlineSmall, color = DreamlandOnDark)
                    }
                    TextButton(onClick = onDismiss) { Text("Cancel", color = DreamlandMuted) }
                }
                Spacer(Modifier.height(4.dp))
                Text(
                    "Room ${order.roomNumber}  ·  ${order.guestName}  ·  ₹${fmtMoney(actualPrice)}",
                    color = DreamlandMuted, style = MaterialTheme.typography.bodySmall,
                )
                Spacer(Modifier.height(16.dp))

                // ── Scrollable body ────────────────────────────────────────────
                Column(
                    modifier = Modifier
                        .weight(1f, fill = false)
                        .verticalScroll(rememberScrollState()),
                ) {
                    Text("VENDOR", style = MaterialTheme.typography.labelSmall, color = DreamlandMuted, letterSpacing = 1.sp)
                    Spacer(Modifier.height(6.dp))
                    Box {
                        Row(
                            modifier = Modifier.fillMaxWidth()
                                .clip(RoundedCornerShape(10.dp))
                                .border(1.dp, DreamlandMuted.copy(alpha = 0.35f), RoundedCornerShape(10.dp))
                                .background(DreamlandForestElevated)
                                .clickable { dropdownOpen = true }
                                .padding(horizontal = 14.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                selectedVendor?.name ?: "Select vendor…",
                                color = if (selectedVendor != null) DreamlandOnDark else DreamlandMuted,
                                style = MaterialTheme.typography.bodyMedium,
                                modifier = Modifier.weight(1f),
                            )
                            Text("▾", color = DreamlandMuted)
                        }
                        DropdownMenu(expanded = dropdownOpen, onDismissRequest = { dropdownOpen = false }) {
                            if (vendors.isEmpty()) {
                                DropdownMenuItem(enabled = false, text = { Text("No vendors yet", color = DreamlandMuted) }, onClick = {})
                            }
                            vendors.forEach { v ->
                                DropdownMenuItem(text = { Text(v.name) }, onClick = { selectedVendor = v; dropdownOpen = false })
                            }
                            HorizontalDivider()
                            DropdownMenuItem(
                                text = { Text("+ Add new vendor", color = DreamlandGold, fontWeight = FontWeight.SemiBold) },
                                onClick = { dropdownOpen = false; showAddVendor = true },
                            )
                        }
                    }

                    // ── Current balance (prominent) ────────────────────────────
                    if (selectedVendor != null) {
                        Spacer(Modifier.height(10.dp))
                        val bal = currentBalance
                        val (balText, balColor) = when {
                            loadingBalance -> "Checking balance…" to DreamlandMuted
                            bal == null -> "New vendor — no balance yet" to DreamlandMuted
                            bal.balanceType == "PAYABLE" -> "You currently owe ₹${fmtMoney(bal.payable)}" to AMBER
                            bal.balanceType == "CREDIT" -> "Vendor credit ₹${fmtMoney(-bal.payable)} (prepaid)" to GREEN
                            else -> "Settled — nothing owed" to GREEN
                        }
                        Box(
                            Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp))
                                .background(balColor.copy(alpha = 0.12f))
                                .border(1.dp, balColor.copy(alpha = 0.3f), RoundedCornerShape(10.dp))
                                .padding(horizontal = 14.dp, vertical = 12.dp),
                        ) {
                            Column {
                                Text("CURRENT BALANCE", style = MaterialTheme.typography.labelSmall, color = DreamlandMuted, letterSpacing = 1.sp)
                                Spacer(Modifier.height(2.dp))
                                Text(balText, color = balColor, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                            }
                        }
                    }

                    Spacer(Modifier.height(18.dp))
                    Text("VENDOR COST", style = MaterialTheme.typography.labelSmall, color = DreamlandMuted, letterSpacing = 1.sp)
                    Spacer(Modifier.height(6.dp))
                    DialogField(value = costText, onChange = { costText = it.filterMoney(); costEdited = true }, label = "What the vendor charged")
                    selectedVendor?.let { v ->
                        if (v.discountPercent > 0 && !costEdited) {
                            Spacer(Modifier.height(4.dp))
                            Text(
                                "Auto: ${fmtMoney(v.discountPercent)}% off the ₹${fmtMoney(actualPrice)} actual price",
                                style = MaterialTheme.typography.labelSmall, color = DreamlandMuted,
                            )
                        }
                    }

                    Spacer(Modifier.height(12.dp))
                    Text("PAID TO VENDOR NOW", style = MaterialTheme.typography.labelSmall, color = DreamlandMuted, letterSpacing = 1.sp)
                    Spacer(Modifier.height(8.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Column(Modifier.weight(1f)) {
                            Text("Cash (₹)", style = MaterialTheme.typography.labelMedium, color = DreamlandOnDark, fontWeight = FontWeight.SemiBold)
                            Spacer(Modifier.height(4.dp))
                            DialogField(value = cashText, onChange = { cashText = it.filterMoney(); cashEdited = true }, label = "Cash amount")
                        }
                        Column(Modifier.weight(1f)) {
                            Text("Bank (₹)", style = MaterialTheme.typography.labelMedium, color = DreamlandOnDark, fontWeight = FontWeight.SemiBold)
                            Spacer(Modifier.height(4.dp))
                            DialogField(value = bankText, onChange = { bankText = it.filterMoney() }, label = "Bank amount")
                        }
                    }
                    Spacer(Modifier.height(6.dp))
                    Text(
                        "Total paid now: ₹${fmtMoney(cash + bank)}  ·  leave it below the cost to pay the vendor later",
                        style = MaterialTheme.typography.labelSmall, color = DreamlandMuted,
                    )

                    Spacer(Modifier.height(12.dp))
                    val owe = round2(cost - cash - bank)
                    val (previewText, previewColor) = when {
                        abs(owe) <= 0.01 -> "This order: vendor settled" to GREEN
                        owe > 0.01 -> "This order: you'll owe ₹${fmtMoney(owe)} more" to AMBER
                        else -> "This order: ₹${fmtMoney(-owe)} extra (vendor credit)" to GREEN
                    }
                    Box(
                        Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp))
                            .background(previewColor.copy(alpha = 0.12f)).padding(horizontal = 12.dp, vertical = 10.dp),
                    ) { Text(previewText, color = previewColor, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.SemiBold) }
                }

                // ── Actions (fixed footer) ─────────────────────────────────────
                Spacer(Modifier.height(16.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    TextButton(onClick = onSkip, modifier = Modifier.weight(1f).height(44.dp)) {
                        Text("Skip (in-house)", color = DreamlandMuted, fontWeight = FontWeight.SemiBold)
                    }
                    Button(
                        onClick = { selectedVendor?.let { onConfirm(it, round2(cost), round2(cash), round2(bank)) } },
                        enabled = canConfirm,
                        modifier = Modifier.weight(1.4f).height(44.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = GREEN, disabledContainerColor = GREEN.copy(alpha = 0.3f)),
                        shape = RoundedCornerShape(8.dp),
                    ) { Text("Mark Done", color = Color(0xFF0D1F17), fontWeight = FontWeight.SemiBold) }
                }
            }
        }
    }

    // ── Add-vendor dialog (stacked above) ──────────────────────────────────────
    if (showAddVendor) {
        AddVendorDialog(
            onSubmit = { name, phone, discountPercent ->
                onAddVendor(name, phone, discountPercent) { created ->
                    selectedVendor = created
                    showAddVendor = false
                }
            },
            onDismiss = { showAddVendor = false },
        )
    }
}

@Composable
private fun AddVendorDialog(onSubmit: (name: String, phone: String, discountPercent: Double) -> Unit, onDismiss: () -> Unit) {
    var name by remember { mutableStateOf("") }
    var phone by remember { mutableStateOf("") }
    var discount by remember { mutableStateOf("") }
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Box(
            modifier = Modifier
                .fillMaxWidth(0.34f)
                .clip(RoundedCornerShape(16.dp))
                .background(DreamlandForestElevated)
                .padding(24.dp),
        ) {
            Column(Modifier.fillMaxWidth()) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Text("ADD VENDOR", style = MaterialTheme.typography.labelLarge, color = DreamlandGold, letterSpacing = 2.sp)
                    TextButton(onClick = onDismiss) { Text("Cancel", color = DreamlandMuted) }
                }
                Spacer(Modifier.height(16.dp))
                DialogField(value = name, onChange = { name = it }, label = "Vendor name")
                Spacer(Modifier.height(10.dp))
                DialogField(value = phone, onChange = { phone = it }, label = "Phone (optional)")
                Spacer(Modifier.height(10.dp))
                DialogField(value = discount, onChange = { discount = it.filterMoney() }, label = "Discount % off actual price")
                Spacer(Modifier.height(4.dp))
                Text(
                    "Discount the hotel gets from this vendor — used to auto-fill the vendor cost.",
                    style = MaterialTheme.typography.labelSmall, color = DreamlandMuted,
                )
                Spacer(Modifier.height(18.dp))
                Button(
                    onClick = { onSubmit(name.trim(), phone.trim(), discount.toDoubleOrNull() ?: 0.0) },
                    enabled = name.trim().isNotEmpty(),
                    modifier = Modifier.fillMaxWidth().height(44.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = DreamlandGold, disabledContainerColor = DreamlandGold.copy(alpha = 0.3f)),
                    shape = RoundedCornerShape(8.dp),
                ) { Text("Add vendor", color = Color(0xFF0D1F17), fontWeight = FontWeight.SemiBold) }
            }
        }
    }
}

@Composable
private fun DialogField(value: String, onChange: (String) -> Unit, label: String) {
    OutlinedTextField(
        value = value,
        onValueChange = onChange,
        placeholder = { Text(label, color = DreamlandMuted, fontSize = 12.sp) },
        singleLine = true,
        modifier = Modifier.fillMaxWidth(),
        colors = OutlinedTextFieldDefaults.colors(
            focusedTextColor = DreamlandOnDark, unfocusedTextColor = DreamlandOnDark,
            focusedBorderColor = DreamlandGold, unfocusedBorderColor = DreamlandMuted.copy(alpha = 0.4f),
            cursorColor = DreamlandGold,
        ),
    )
}

// Keep only digits and a single decimal point in money inputs.
private fun String.filterMoney(): String {
    val cleaned = filter { it.isDigit() || it == '.' }
    val firstDot = cleaned.indexOf('.')
    return if (firstDot < 0) cleaned
    else cleaned.substring(0, firstDot + 1) + cleaned.substring(firstDot + 1).replace(".", "")
}

private fun round2(d: Double): Double = Math.round(d * 100.0) / 100.0

private fun fmtMoney(d: Double): String {
    val r = round2(d)
    return if (r == r.toLong().toDouble()) r.toLong().toString() else r.toString()
}
