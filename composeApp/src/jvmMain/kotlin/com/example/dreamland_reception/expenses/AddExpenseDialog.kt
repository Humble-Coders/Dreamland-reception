package com.example.dreamland_reception.expenses

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
import androidx.compose.runtime.LaunchedEffect
import com.example.dreamland_reception.DreamlandForestElevated
import com.example.dreamland_reception.DreamlandForestSurface
import com.example.dreamland_reception.DreamlandGold
import com.example.dreamland_reception.DreamlandMuted
import com.example.dreamland_reception.DreamlandOnDark
import com.example.dreamland_reception.data.accounting.VendorBalanceInfo
import com.example.dreamland_reception.data.model.Vendor
import kotlin.math.abs

private val AMBER = Color(0xFFFFC107)
private val GREEN = Color(0xFF4CAF50)

/**
 * Records a hotel expense. A vendor is optional: pick one to track a balance
 * (pay-later / overpay), or leave it blank for a direct cash/bank spend. When no
 * vendor is chosen a **title** is required, and the amount must be fully paid.
 */
@Composable
fun AddExpenseDialog(
    vendors: List<Vendor>,
    onAddVendor: (name: String, phone: String, onCreated: (Vendor) -> Unit) -> Unit,
    onLoadBalance: suspend (externalId: String) -> VendorBalanceInfo?,
    onConfirm: (title: String, notes: String, amount: Double, vendorId: String, vendorName: String, cash: Double, bank: Double) -> Unit,
    onDismiss: () -> Unit,
) {
    var selectedVendor by remember { mutableStateOf<Vendor?>(null) }
    var dropdownOpen by remember { mutableStateOf(false) }
    var showAddVendor by remember { mutableStateOf(false) }

    var titleText by remember { mutableStateOf("") }
    var notesText by remember { mutableStateOf("") }
    var amountText by remember { mutableStateOf("") }
    var cashText by remember { mutableStateOf("") }
    var bankText by remember { mutableStateOf("") }

    var currentBalance by remember { mutableStateOf<VendorBalanceInfo?>(null) }
    var loadingBalance by remember { mutableStateOf(false) }
    LaunchedEffect(selectedVendor?.id) {
        currentBalance = null
        val v = selectedVendor
        if (v != null) { loadingBalance = true; currentBalance = onLoadBalance(v.id); loadingBalance = false }
    }

    val amount = amountText.toDoubleOrNull() ?: 0.0
    val cash = cashText.toDoubleOrNull() ?: 0.0
    val bank = bankText.toDoubleOrNull() ?: 0.0
    val hasVendor = selectedVendor != null
    val titleOk = hasVendor || titleText.trim().isNotEmpty()
    val paidOk = if (hasVendor) true else abs(amount - cash - bank) <= 0.01
    val canConfirm = amount > 0.0 && cash >= 0.0 && bank >= 0.0 && titleOk && paidOk

    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Box(
            modifier = Modifier
                .fillMaxWidth(0.46f)
                .heightIn(max = 660.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(DreamlandForestSurface)
                .padding(24.dp),
        ) {
            Column(Modifier.fillMaxWidth()) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Column {
                        Text("EXPENSE", style = MaterialTheme.typography.labelLarge, color = DreamlandGold, letterSpacing = 2.sp)
                        Text("Record an expense", style = MaterialTheme.typography.headlineSmall, color = DreamlandOnDark)
                    }
                    TextButton(onClick = onDismiss) { Text("Cancel", color = DreamlandMuted) }
                }
                Spacer(Modifier.height(16.dp))

                Column(Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState())) {
                    // Title
                    Text(if (hasVendor) "TITLE (optional)" else "TITLE", style = MaterialTheme.typography.labelSmall, color = DreamlandMuted, letterSpacing = 1.sp)
                    Spacer(Modifier.height(6.dp))
                    ExpField(titleText, { titleText = it }, "e.g. Diesel, Plumbing, Stationery")

                    Spacer(Modifier.height(14.dp))
                    // Vendor (optional)
                    Text("VENDOR (optional)", style = MaterialTheme.typography.labelSmall, color = DreamlandMuted, letterSpacing = 1.sp)
                    Spacer(Modifier.height(6.dp))
                    Box {
                        Row(
                            modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp))
                                .border(1.dp, DreamlandMuted.copy(alpha = 0.35f), RoundedCornerShape(10.dp))
                                .background(DreamlandForestElevated).clickable { dropdownOpen = true }
                                .padding(horizontal = 14.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                selectedVendor?.name ?: "No vendor (direct cash/bank)",
                                color = if (selectedVendor != null) DreamlandOnDark else DreamlandMuted,
                                style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f),
                            )
                            Text("▾", color = DreamlandMuted)
                        }
                        DropdownMenu(expanded = dropdownOpen, onDismissRequest = { dropdownOpen = false }) {
                            DropdownMenuItem(text = { Text("No vendor (direct)", color = DreamlandMuted) }, onClick = { selectedVendor = null; dropdownOpen = false })
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

                    if (selectedVendor != null) {
                        Spacer(Modifier.height(10.dp))
                        val bal = currentBalance
                        val (balText, balColor) = when {
                            loadingBalance -> "Checking balance…" to DreamlandMuted
                            bal == null -> "New vendor — no balance yet" to DreamlandMuted
                            bal.balanceType == "PAYABLE" -> "You currently owe ₹${fmt(bal.payable)}" to AMBER
                            bal.balanceType == "CREDIT" -> "Vendor credit ₹${fmt(-bal.payable)} (prepaid)" to GREEN
                            else -> "Settled — nothing owed" to GREEN
                        }
                        Box(
                            Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).background(balColor.copy(alpha = 0.12f))
                                .border(1.dp, balColor.copy(alpha = 0.3f), RoundedCornerShape(10.dp)).padding(horizontal = 14.dp, vertical = 12.dp),
                        ) {
                            Column {
                                Text("CURRENT BALANCE", style = MaterialTheme.typography.labelSmall, color = DreamlandMuted, letterSpacing = 1.sp)
                                Spacer(Modifier.height(2.dp))
                                Text(balText, color = balColor, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                            }
                        }
                    }

                    Spacer(Modifier.height(14.dp))
                    Text("AMOUNT", style = MaterialTheme.typography.labelSmall, color = DreamlandMuted, letterSpacing = 1.sp)
                    Spacer(Modifier.height(6.dp))
                    ExpField(amountText, { amountText = it.filterMoney() }, "Total cost")

                    Spacer(Modifier.height(12.dp))
                    Text("PAID NOW", style = MaterialTheme.typography.labelSmall, color = DreamlandMuted, letterSpacing = 1.sp)
                    Spacer(Modifier.height(8.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Column(Modifier.weight(1f)) {
                            Text("Cash (₹)", style = MaterialTheme.typography.labelMedium, color = DreamlandOnDark, fontWeight = FontWeight.SemiBold)
                            Spacer(Modifier.height(4.dp))
                            ExpField(cashText, { cashText = it.filterMoney() }, "Cash amount")
                        }
                        Column(Modifier.weight(1f)) {
                            Text("Bank (₹)", style = MaterialTheme.typography.labelMedium, color = DreamlandOnDark, fontWeight = FontWeight.SemiBold)
                            Spacer(Modifier.height(4.dp))
                            ExpField(bankText, { bankText = it.filterMoney() }, "Bank amount")
                        }
                    }

                    Spacer(Modifier.height(12.dp))
                    Text("NOTES (optional)", style = MaterialTheme.typography.labelSmall, color = DreamlandMuted, letterSpacing = 1.sp)
                    Spacer(Modifier.height(6.dp))
                    ExpField(notesText, { notesText = it }, "Any extra detail")

                    Spacer(Modifier.height(12.dp))
                    val owe = round2(amount - cash - bank)
                    val (previewText, previewColor) = when {
                        !hasVendor && !paidOk -> "Pay the full amount — or pick a vendor to owe the rest later" to AMBER
                        abs(owe) <= 0.01 -> "Fully paid" to GREEN
                        owe > 0.01 -> "You'll owe this vendor ₹${fmt(owe)}" to AMBER
                        else -> "Vendor credit ₹${fmt(-owe)} (prepaid)" to GREEN
                    }
                    Box(
                        Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp)).background(previewColor.copy(alpha = 0.12f)).padding(horizontal = 12.dp, vertical = 10.dp),
                    ) { Text(previewText, color = previewColor, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.SemiBold) }
                }

                Spacer(Modifier.height(16.dp))
                Button(
                    onClick = {
                        val v = selectedVendor
                        onConfirm(titleText.trim(), notesText.trim(), round2(amount), v?.id ?: "", v?.name ?: "", round2(cash), round2(bank))
                    },
                    enabled = canConfirm,
                    modifier = Modifier.fillMaxWidth().height(46.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = GREEN, disabledContainerColor = GREEN.copy(alpha = 0.3f)),
                    shape = RoundedCornerShape(10.dp),
                ) { Text("Save Expense", color = Color(0xFF0D1F17), fontWeight = FontWeight.SemiBold) }
            }
        }
    }

    if (showAddVendor) {
        ExpenseAddVendorDialog(
            onSubmit = { name, phone -> onAddVendor(name, phone) { created -> selectedVendor = created; showAddVendor = false } },
            onDismiss = { showAddVendor = false },
        )
    }
}

@Composable
private fun ExpenseAddVendorDialog(onSubmit: (name: String, phone: String) -> Unit, onDismiss: () -> Unit) {
    var name by remember { mutableStateOf("") }
    var phone by remember { mutableStateOf("") }
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Box(Modifier.fillMaxWidth(0.34f).clip(RoundedCornerShape(16.dp)).background(DreamlandForestElevated).padding(24.dp)) {
            Column(Modifier.fillMaxWidth()) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Text("ADD VENDOR", style = MaterialTheme.typography.labelLarge, color = DreamlandGold, letterSpacing = 2.sp)
                    TextButton(onClick = onDismiss) { Text("Cancel", color = DreamlandMuted) }
                }
                Spacer(Modifier.height(16.dp))
                ExpField(name, { name = it }, "Vendor name")
                Spacer(Modifier.height(10.dp))
                ExpField(phone, { phone = it }, "Phone (optional)")
                Spacer(Modifier.height(18.dp))
                Button(
                    onClick = { onSubmit(name.trim(), phone.trim()) },
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
private fun ExpField(value: String, onChange: (String) -> Unit, label: String) {
    OutlinedTextField(
        value = value, onValueChange = onChange,
        placeholder = { Text(label, color = DreamlandMuted, fontSize = 12.sp) },
        singleLine = true, modifier = Modifier.fillMaxWidth(),
        colors = OutlinedTextFieldDefaults.colors(
            focusedTextColor = DreamlandOnDark, unfocusedTextColor = DreamlandOnDark,
            focusedBorderColor = DreamlandGold, unfocusedBorderColor = DreamlandMuted.copy(alpha = 0.4f), cursorColor = DreamlandGold,
        ),
    )
}

private fun String.filterMoney(): String {
    val cleaned = filter { it.isDigit() || it == '.' }
    val firstDot = cleaned.indexOf('.')
    return if (firstDot < 0) cleaned else cleaned.substring(0, firstDot + 1) + cleaned.substring(firstDot + 1).replace(".", "")
}

private fun round2(d: Double): Double = Math.round(d * 100.0) / 100.0
private fun fmt(d: Double): String {
    val r = round2(d)
    return if (r == r.toLong().toDouble()) "%,d".format(r.toLong()) else "%,.2f".format(r)
}
