@file:OptIn(ExperimentalMaterial3Api::class)

package com.example.dreamland_reception.billing

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Receipt
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.dreamland_reception.DreamlandAppInitializer
import com.example.dreamland_reception.DreamlandForest
import com.example.dreamland_reception.DreamlandForestElevated
import com.example.dreamland_reception.DreamlandForestSurface
import com.example.dreamland_reception.DreamlandGold
import com.example.dreamland_reception.DreamlandMuted
import com.example.dreamland_reception.DreamlandOnDark
import com.example.dreamland_reception.data.model.Bill
import com.example.dreamland_reception.data.model.BillItem
import com.example.dreamland_reception.data.model.PaymentTransaction
import com.example.dreamland_reception.ui.viewmodel.AddBillItemDialog
import com.example.dreamland_reception.ui.viewmodel.AddPaymentDialog
import com.example.dreamland_reception.ui.viewmodel.StayBillingViewModel
import com.example.dreamland_reception.ui.viewmodel.TaxDiscountDialog
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit

private val dateFmt = SimpleDateFormat("dd MMM yyyy", Locale.getDefault())
private val timeFmt = SimpleDateFormat("dd MMM yyyy, hh:mm a", Locale.getDefault())

@Composable
fun StayBillingScreen(
    stayId: String,
    onBack: () -> Unit,
    vm: StayBillingViewModel = DreamlandAppInitializer.getStayBillingViewModel(),
) {
    val state by vm.state.collectAsStateWithLifecycle()

    LaunchedEffect(stayId) { vm.load(stayId) }

    Column(Modifier.fillMaxSize().background(DreamlandForest)) {
        // ── Header ────────────────────────────────────────────────────────────
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(DreamlandForestSurface)
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = DreamlandGold)
            }
            Spacer(Modifier.width(8.dp))
            Column(Modifier.weight(1f)) {
                Text("BILLING", style = MaterialTheme.typography.labelSmall, color = DreamlandGold, letterSpacing = 2.sp)
                val guest = state.bill?.guestName ?: state.stay?.guestName ?: ""
                val room = state.bill?.roomNumber ?: state.stay?.roomNumber ?: ""
                if (guest.isNotBlank()) {
                    Text(guest, style = MaterialTheme.typography.titleLarge, color = DreamlandOnDark, fontWeight = FontWeight.Bold)
                    if (room.isNotBlank()) Text("Room $room", style = MaterialTheme.typography.bodySmall, color = DreamlandMuted)
                }
            }
            state.bill?.let { bill ->
                StatusBadge(bill.status)
            }
        }

        when {
            state.isLoading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = DreamlandGold)
            }
            state.error != null -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(state.error ?: "Error", color = Color(0xFFEF5350))
            }
            state.bill != null -> {
                val bill = state.bill!!
                Row(Modifier.fillMaxSize()) {
                    // ── LEFT: Bill items ───────────────────────────────────────
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight()
                            .verticalScroll(rememberScrollState())
                            .padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        // Stay info card
                        StayInfoCard(bill)

                        // Items section
                        Row(
                            Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text("Bill Items", style = MaterialTheme.typography.titleMedium, color = DreamlandOnDark, fontWeight = FontWeight.SemiBold)
                            Button(
                                onClick = { vm.openAddItem() },
                                colors = ButtonDefaults.buttonColors(containerColor = DreamlandGold),
                                shape = RoundedCornerShape(8.dp),
                                modifier = Modifier.height(36.dp),
                            ) {
                                Icon(Icons.Filled.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(4.dp))
                                Text("Add Item", fontSize = 13.sp)
                            }
                        }

                        if (bill.items.isEmpty()) {
                            Box(
                                Modifier
                                    .fillMaxWidth()
                                    .height(80.dp)
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(DreamlandForestSurface),
                                contentAlignment = Alignment.Center,
                            ) {
                                Text("No items yet. Add room charges, orders or services.", color = DreamlandMuted, style = MaterialTheme.typography.bodySmall)
                            }
                        } else {
                            bill.items.forEach { item ->
                                BillItemRow(item, onDelete = { vm.removeItem(item.id) })
                            }
                        }

                        // Tax / Discount card
                        TaxDiscountSummaryCard(bill, onEdit = { vm.openTaxDiscount() })
                    }

                    // ── RIGHT: Summary + Payments ──────────────────────────────
                    Column(
                        modifier = Modifier
                            .width(380.dp)
                            .fillMaxHeight()
                            .background(DreamlandForestSurface)
                            .border(1.dp, DreamlandGold.copy(alpha = 0.2f))
                            .verticalScroll(rememberScrollState())
                            .padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        // Bill summary
                        BillSummaryCard(bill)

                        // Payment history
                        Row(
                            Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text("Payments", style = MaterialTheme.typography.titleMedium, color = DreamlandOnDark, fontWeight = FontWeight.SemiBold)
                            if (bill.pendingAmount > 0) {
                                Button(
                                    onClick = { vm.openAddPayment() },
                                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF4CAF50)),
                                    shape = RoundedCornerShape(8.dp),
                                    modifier = Modifier.height(34.dp),
                                ) {
                                    Icon(Icons.Filled.Add, contentDescription = null, modifier = Modifier.size(14.dp))
                                    Spacer(Modifier.width(4.dp))
                                    Text("Add Payment", fontSize = 12.sp)
                                }
                            }
                        }

                        if (bill.advancePayment > 0) {
                            PaymentRow(
                                label = "Advance paid at check-in",
                                amount = bill.advancePayment,
                                method = "",
                                date = null,
                                isAdvance = true,
                            )
                        }

                        if (bill.transactions.isEmpty() && bill.advancePayment <= 0) {
                            Box(
                                Modifier
                                    .fillMaxWidth()
                                    .height(56.dp)
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(DreamlandForestElevated),
                                contentAlignment = Alignment.Center,
                            ) {
                                Text("No payments recorded yet.", color = DreamlandMuted, style = MaterialTheme.typography.bodySmall)
                            }
                        }

                        bill.transactions.forEach { tx ->
                            PaymentRow(
                                label = tx.method,
                                amount = tx.amount,
                                method = tx.method,
                                date = tx.createdAt,
                            )
                        }

                        Spacer(Modifier.height(4.dp))
                        HorizontalDivider(color = DreamlandGold.copy(alpha = 0.2f))
                        Spacer(Modifier.height(4.dp))

                        // Confirm Payment
                        Button(
                            onClick = { vm.openConfirmPayment() },
                            modifier = Modifier.fillMaxWidth().height(48.dp),
                            shape = RoundedCornerShape(10.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF4CAF50)),
                        ) {
                            Text("Confirm Payment", color = Color.White, fontWeight = FontWeight.SemiBold)
                        }

                        // Generate invoice (disabled stub)
                        Button(
                            onClick = { /* PDF stub - not implemented yet */ },
                            enabled = false,
                            modifier = Modifier.fillMaxWidth().height(48.dp),
                            shape = RoundedCornerShape(10.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = DreamlandGold,
                                disabledContainerColor = DreamlandMuted.copy(alpha = 0.3f),
                            ),
                        ) {
                            Icon(Icons.Filled.Receipt, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(8.dp))
                            Text("Generate Invoice (PDF)", fontWeight = FontWeight.SemiBold)
                        }

                        Text(
                            "PDF generation coming soon",
                            style = MaterialTheme.typography.labelSmall,
                            color = DreamlandMuted,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                }
            }
        }
    }

    // ── Dialogs ───────────────────────────────────────────────────────────────
    val d = state.addItemDialog
    if (d.show) AddItemDialog(d, vm)

    val pd = state.addPaymentDialog
    if (pd.show) AddPaymentDialogUI(pd, vm)

    val td = state.taxDiscountDialog
    if (td.show) TaxDiscountDialogUI(td, vm)

    val cpd = state.confirmPaymentDialog
    if (cpd.show) ConfirmPaymentDialogUI(cpd, state.bill, vm)
}

// ── Sub-composables ───────────────────────────────────────────────────────────

@Composable
internal fun StayInfoCard(bill: Bill) {
    Card(
        colors = CardDefaults.cardColors(containerColor = DreamlandForestElevated),
        shape = RoundedCornerShape(12.dp),
    ) {
        Row(Modifier.fillMaxWidth().padding(14.dp), horizontalArrangement = Arrangement.SpaceEvenly) {
            val nights = if (bill.checkInDate != null && bill.checkOutDate != null) {
                val diff = bill.checkOutDate.time - bill.checkInDate.time
                (TimeUnit.MILLISECONDS.toDays(diff)).coerceAtLeast(1)
            } else null

            InfoCell("Check-in", bill.checkInDate?.let { dateFmt.format(it) } ?: "-")
            InfoCell("Check-out", bill.checkOutDate?.let { dateFmt.format(it) } ?: "-")
            if (nights != null) InfoCell("Duration", "$nights night${if (nights != 1L) "s" else ""}")
        }
    }
}

@Composable
private fun InfoCell(label: String, value: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(label, style = MaterialTheme.typography.labelSmall, color = DreamlandMuted)
        Text(value, style = MaterialTheme.typography.bodyMedium, color = DreamlandOnDark, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun BillItemRow(item: BillItem, onDelete: () -> Unit) {
    Card(
        colors = CardDefaults.cardColors(containerColor = DreamlandForestSurface),
        shape = RoundedCornerShape(8.dp),
    ) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TypeChip(item.type)
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text(item.name, color = DreamlandOnDark, fontWeight = FontWeight.Medium, style = MaterialTheme.typography.bodyMedium)
                if (item.notes.isNotBlank()) Text(item.notes, color = DreamlandMuted, style = MaterialTheme.typography.labelSmall)
                Text("${item.quantity} × ₹${item.unitPrice.toLong()}", color = DreamlandMuted, style = MaterialTheme.typography.labelSmall)
            }
            Text("₹${item.total.toLong()}", color = DreamlandOnDark, fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.bodyMedium)
            Spacer(Modifier.width(4.dp))
            IconButton(onClick = onDelete, modifier = Modifier.size(32.dp)) {
                Icon(Icons.Filled.Delete, contentDescription = "Remove", tint = Color(0xFFEF5350), modifier = Modifier.size(18.dp))
            }
        }
    }
}

@Composable
internal fun TypeChip(type: String) {
    val (bg, text) = when (type) {
        "ROOM" -> DreamlandGold.copy(alpha = 0.2f) to DreamlandGold
        "ORDER" -> Color(0xFF4CAF50).copy(alpha = 0.2f) to Color(0xFF4CAF50)
        "SERVICE" -> Color(0xFF2196F3).copy(alpha = 0.2f) to Color(0xFF2196F3)
        else -> DreamlandMuted.copy(alpha = 0.15f) to DreamlandMuted
    }
    Box(
        Modifier
            .clip(RoundedCornerShape(4.dp))
            .background(bg)
            .padding(horizontal = 8.dp, vertical = 4.dp),
    ) {
        Text(
            text = type,
            color = text,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            softWrap = false,
        )
    }
}

@Composable
private fun TaxDiscountSummaryCard(bill: Bill, onEdit: () -> Unit) {
    Card(
        colors = CardDefaults.cardColors(containerColor = DreamlandForestSurface),
        shape = RoundedCornerShape(10.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            Modifier.fillMaxWidth().padding(12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column {
                Text("Tax & Discount", color = DreamlandOnDark, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
                val taxStr = if (bill.taxEnabled) "Tax ${bill.taxPercentage.toInt()}%" else "No tax"
                val discStr = if (bill.discountValue > 0) {
                    val sym = if (bill.discountType == "PERCENT") "%" else "₹"
                    "Discount ${bill.discountValue.toInt()}$sym"
                } else "No discount"
                Text("$taxStr  ·  $discStr", color = DreamlandMuted, style = MaterialTheme.typography.labelSmall)
            }
            TextButton(onClick = onEdit) {
                Text("Edit", color = DreamlandGold)
            }
        }
    }
}

@Composable
internal fun BillSummaryCard(bill: Bill) {
    Card(
        colors = CardDefaults.cardColors(containerColor = DreamlandForestElevated),
        shape = RoundedCornerShape(12.dp),
    ) {
        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Summary", style = MaterialTheme.typography.titleMedium, color = DreamlandGold, fontWeight = FontWeight.SemiBold)
            HorizontalDivider(color = DreamlandGold.copy(alpha = 0.2f))
            SummaryRow("Subtotal", bill.subtotal)
            if (bill.taxEnabled && bill.taxAmount > 0) SummaryRow("Tax (${bill.taxPercentage.toInt()}%)", bill.taxAmount)
            if (bill.discountAmount > 0) SummaryRow("Discount", -bill.discountAmount)
            HorizontalDivider(color = DreamlandMuted.copy(alpha = 0.25f))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("Total", color = DreamlandOnDark, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                Text("₹${bill.totalAmount.toLong()}", color = DreamlandGold, fontWeight = FontWeight.Bold, fontSize = 20.sp)
            }
            if (bill.advancePayment > 0) SummaryRow("Advance paid", bill.advancePayment)
            if (bill.totalPaid > 0) SummaryRow("Payments received", bill.totalPaid)
            if (bill.pendingAmount > 0) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("Balance Due", color = Color(0xFFEF5350), fontWeight = FontWeight.Bold)
                    Text("₹${bill.pendingAmount.toLong()}", color = Color(0xFFEF5350), fontWeight = FontWeight.Bold, fontSize = 16.sp)
                }
            } else if (bill.totalAmount > 0) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("Balance Due", color = Color(0xFF4CAF50), fontWeight = FontWeight.Bold)
                    Text("PAID", color = Color(0xFF4CAF50), fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

@Composable
private fun SummaryRow(label: String, amount: Double) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, color = DreamlandMuted, style = MaterialTheme.typography.bodySmall)
        Text(
            text = if (amount < 0) "-₹${(-amount).toLong()}" else "₹${amount.toLong()}",
            color = if (amount < 0) Color(0xFF4CAF50) else DreamlandOnDark,
            style = MaterialTheme.typography.bodySmall,
            fontWeight = FontWeight.Medium,
        )
    }
}

@Composable
internal fun PaymentRow(
    label: String,
    amount: Double,
    method: String,
    date: Date?,
    isAdvance: Boolean = false,
) {
    Card(
        colors = CardDefaults.cardColors(
            containerColor = if (isAdvance) DreamlandGold.copy(alpha = 0.08f) else DreamlandForestElevated,
        ),
        shape = RoundedCornerShape(8.dp),
    ) {
        Row(Modifier.fillMaxWidth().padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(label.uppercase(), color = DreamlandOnDark, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold)
                if (date != null) Text(timeFmt.format(date), color = DreamlandMuted, style = MaterialTheme.typography.labelSmall)
                else if (isAdvance) Text("At check-in", color = DreamlandMuted, style = MaterialTheme.typography.labelSmall)
            }
            Text("₹${amount.toLong()}", color = Color(0xFF4CAF50), fontWeight = FontWeight.Bold, fontSize = 15.sp)
        }
    }
}

@Composable
private fun StatusBadge(status: String) {
    val (bg, text) = when (status) {
        "PAID" -> Color(0xFF4CAF50).copy(alpha = 0.2f) to Color(0xFF4CAF50)
        "PARTIAL" -> Color(0xFFFF9800).copy(alpha = 0.2f) to Color(0xFFFF9800)
        else -> Color(0xFFEF5350).copy(alpha = 0.2f) to Color(0xFFEF5350)
    }
    Box(
        Modifier
            .clip(RoundedCornerShape(8.dp))
            .background(bg)
            .padding(horizontal = 12.dp, vertical = 6.dp),
    ) {
        Text(status, color = text, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelMedium)
    }
}

// ── Add Item Dialog ───────────────────────────────────────────────────────────

@Composable
private fun AddItemDialog(d: AddBillItemDialog, vm: StayBillingViewModel) {
    Dialog(
        onDismissRequest = { if (!d.isSaving) vm.closeAddItem() },
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Box(
            modifier = Modifier
                .widthIn(max = 480.dp)
                .fillMaxWidth(0.45f)
                .clip(RoundedCornerShape(16.dp))
                .background(DreamlandForestSurface)
                .padding(24.dp),
        ) {
            if (d.step == 0) {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("ADD ITEM", style = MaterialTheme.typography.labelLarge, color = DreamlandGold, letterSpacing = 2.sp)
                    Text("Select item type", color = DreamlandOnDark, fontWeight = FontWeight.SemiBold)
                    listOf("ROOM" to "Room Charges", "ORDER" to "Food & Beverage", "SERVICE" to "Hotel Service", "CUSTOM" to "Custom").forEach { (type, label) ->
                        TypeSelectRow(type, label, onClick = { vm.onAddItemType(type) })
                    }
                    TextButton(onClick = { vm.closeAddItem() }, modifier = Modifier.align(Alignment.End)) {
                        Text("Cancel", color = DreamlandMuted)
                    }
                }
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        TypeChip(d.type)
                        Spacer(Modifier.width(8.dp))
                        Text("Item Details", color = DreamlandOnDark, fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.titleMedium)
                    }
                    BillingTextField("Name", d.name, onValueChange = vm::onAddItemName)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        BillingTextField("Quantity", d.quantity, onValueChange = vm::onAddItemQty, keyboard = KeyboardType.Number, modifier = Modifier.weight(1f))
                        BillingTextField("Unit Price (₹)", d.unitPrice, onValueChange = vm::onAddItemPrice, keyboard = KeyboardType.Decimal, modifier = Modifier.weight(1f))
                    }
                    BillingTextField("Notes (optional)", d.notes, onValueChange = vm::onAddItemNotes)

                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        TextButton(onClick = { vm.onAddItemBackToTypeSelect() }, modifier = Modifier.weight(1f)) {
                            Text("Back", color = DreamlandMuted)
                        }
                        Button(
                            onClick = { vm.submitAddItem() },
                            enabled = d.name.isNotBlank() && !d.isSaving,
                            modifier = Modifier.weight(2f),
                            colors = ButtonDefaults.buttonColors(containerColor = DreamlandGold),
                            shape = RoundedCornerShape(10.dp),
                        ) {
                            if (d.isSaving) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp, color = DreamlandForest)
                            else Text("Add Item", color = DreamlandForest, fontWeight = FontWeight.SemiBold)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun TypeSelectRow(type: String, label: String, onClick: () -> Unit) {
    val (bg, accent) = when (type) {
        "ROOM" -> DreamlandGold.copy(alpha = 0.1f) to DreamlandGold
        "ORDER" -> Color(0xFF4CAF50).copy(alpha = 0.1f) to Color(0xFF4CAF50)
        "SERVICE" -> Color(0xFF2196F3).copy(alpha = 0.1f) to Color(0xFF2196F3)
        else -> DreamlandMuted.copy(alpha = 0.1f) to DreamlandMuted
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(bg)
            .border(1.dp, accent.copy(alpha = 0.3f), RoundedCornerShape(8.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(label, color = DreamlandOnDark, fontWeight = FontWeight.Medium, modifier = Modifier.weight(1f))
        Spacer(Modifier.width(8.dp))
        TypeChip(type)
    }
}

// ── Add Payment Dialog ────────────────────────────────────────────────────────

@Composable
private fun AddPaymentDialogUI(pd: AddPaymentDialog, vm: StayBillingViewModel) {
    Dialog(
        onDismissRequest = { if (!pd.isSaving) vm.closeAddPayment() },
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Box(
            modifier = Modifier
                .widthIn(max = 420.dp)
                .fillMaxWidth(0.4f)
                .clip(RoundedCornerShape(16.dp))
                .background(DreamlandForestSurface)
                .padding(24.dp),
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("ADD PAYMENT", style = MaterialTheme.typography.labelLarge, color = DreamlandGold, letterSpacing = 2.sp)

                BillingTextField("Amount (₹)", pd.amount, onValueChange = vm::onPaymentAmount, keyboard = KeyboardType.Decimal)

                Text("Payment Method", color = DreamlandMuted, style = MaterialTheme.typography.labelMedium)
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf("CASH", "UPI", "CARD").forEach { method ->
                        val selected = pd.method == method
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(8.dp))
                                .background(if (selected) DreamlandGold.copy(alpha = 0.15f) else DreamlandForestElevated)
                                .border(1.dp, if (selected) DreamlandGold else DreamlandMuted.copy(alpha = 0.3f), RoundedCornerShape(8.dp))
                                .clickable { vm.onPaymentMethod(method) }
                                .padding(horizontal = 12.dp, vertical = 12.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                text = method,
                                color = if (selected) DreamlandGold else DreamlandMuted,
                                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                                maxLines = 1,
                                softWrap = false,
                            )
                        }
                    }
                }

                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TextButton(
                        onClick = { vm.closeAddPayment() },
                        modifier = Modifier.weight(1f).height(48.dp),
                    ) {
                        Text("Cancel", color = DreamlandMuted, maxLines = 1)
                    }
                    Button(
                        onClick = { vm.submitPayment() },
                        enabled = pd.amount.toDoubleOrNull() != null && (pd.amount.toDoubleOrNull() ?: 0.0) > 0 && !pd.isSaving,
                        modifier = Modifier.weight(2f).height(48.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF4CAF50)),
                        shape = RoundedCornerShape(10.dp),
                    ) {
                        if (pd.isSaving) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp, color = Color.White)
                        else Text("Record Payment", color = Color.White, fontWeight = FontWeight.SemiBold, maxLines = 1, softWrap = false)
                    }
                }
            }
        }
    }
}

// ── Tax / Discount Dialog ─────────────────────────────────────────────────────

@Composable
private fun TaxDiscountDialogUI(td: TaxDiscountDialog, vm: StayBillingViewModel) {
    Dialog(
        onDismissRequest = { if (!td.isSaving) vm.closeTaxDiscount() },
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Box(
            modifier = Modifier
                .widthIn(max = 420.dp)
                .fillMaxWidth(0.4f)
                .clip(RoundedCornerShape(16.dp))
                .background(DreamlandForestSurface)
                .padding(24.dp),
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                Text("TAX & DISCOUNT", style = MaterialTheme.typography.labelLarge, color = DreamlandGold, letterSpacing = 2.sp)

                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Text("Enable Tax (GST)", color = DreamlandOnDark)
                    Switch(
                        checked = td.taxEnabled,
                        onCheckedChange = vm::onTaxEnabled,
                        colors = SwitchDefaults.colors(checkedThumbColor = DreamlandGold, checkedTrackColor = DreamlandGold.copy(alpha = 0.4f)),
                    )
                }

                if (td.taxEnabled) {
                    BillingTextField("Tax %", td.taxPercentage, onValueChange = vm::onTaxPercentage, keyboard = KeyboardType.Decimal)
                }

                Text("Discount Type", color = DreamlandMuted, style = MaterialTheme.typography.labelMedium)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf("FLAT" to "Flat (₹)", "PERCENT" to "Percent (%)").forEach { (type, label) ->
                        val selected = td.discountType == type
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .background(if (selected) DreamlandGold.copy(alpha = 0.15f) else DreamlandForestElevated)
                                .border(1.dp, if (selected) DreamlandGold else DreamlandMuted.copy(alpha = 0.3f), RoundedCornerShape(8.dp))
                                .clickable { vm.onDiscountType(type) }
                                .padding(horizontal = 14.dp, vertical = 10.dp),
                        ) {
                            Text(label, color = if (selected) DreamlandGold else DreamlandMuted, fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal, style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }

                BillingTextField("Discount Value", td.discountValue, onValueChange = vm::onDiscountValue, keyboard = KeyboardType.Decimal)

                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TextButton(onClick = { vm.closeTaxDiscount() }, modifier = Modifier.weight(1f)) {
                        Text("Cancel", color = DreamlandMuted)
                    }
                    Button(
                        onClick = { vm.submitTaxDiscount() },
                        enabled = !td.isSaving,
                        modifier = Modifier.weight(2f),
                        colors = ButtonDefaults.buttonColors(containerColor = DreamlandGold),
                        shape = RoundedCornerShape(10.dp),
                    ) {
                        if (td.isSaving) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp, color = DreamlandForest)
                        else Text("Apply", color = DreamlandForest, fontWeight = FontWeight.SemiBold)
                    }
                }
            }
        }
    }
}

// ── Confirm Payment Dialog ────────────────────────────────────────────────────

@Composable
private fun ConfirmPaymentDialogUI(
    cpd: com.example.dreamland_reception.ui.viewmodel.ConfirmPaymentDialog,
    bill: com.example.dreamland_reception.data.model.Bill?,
    vm: StayBillingViewModel,
) {
    LaunchedEffect(cpd.done) {
        if (cpd.done) vm.closeConfirmPayment()
    }
    Dialog(
        onDismissRequest = { if (!cpd.isProcessing) vm.closeConfirmPayment() },
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Box(
            modifier = Modifier
                .widthIn(max = 440.dp)
                .fillMaxWidth(0.42f)
                .clip(RoundedCornerShape(16.dp))
                .background(DreamlandForestSurface)
                .padding(24.dp),
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("CONFIRM PAYMENT", style = MaterialTheme.typography.labelLarge, color = DreamlandGold, letterSpacing = 2.sp)
                if (bill != null) {
                    Text(bill.guestName, style = MaterialTheme.typography.titleLarge, color = DreamlandOnDark, fontWeight = FontWeight.Bold)
                    Text("Room ${bill.roomNumber}", color = DreamlandMuted, style = MaterialTheme.typography.bodySmall)
                    Spacer(Modifier.height(4.dp))
                    HorizontalDivider(color = DreamlandGold.copy(alpha = 0.2f))
                    ConfirmRow("Total", "₹${bill.totalAmount.toLong()}", DreamlandGold)
                    ConfirmRow("Paid", "₹${(bill.totalPaid + bill.advancePayment).toLong()}", Color(0xFF4CAF50))
                    val due = bill.pendingAmount
                    if (due > 0) ConfirmRow("Balance Due", "₹${due.toLong()}", Color(0xFFEF5350))
                    HorizontalDivider(color = DreamlandGold.copy(alpha = 0.2f))
                }
                if (cpd.error != null) {
                    Text(cpd.error, color = Color(0xFFEF5350), style = MaterialTheme.typography.bodySmall)
                }
                // PDF stub button
                Button(
                    onClick = {},
                    enabled = false,
                    modifier = Modifier.fillMaxWidth().height(44.dp),
                    shape = RoundedCornerShape(10.dp),
                    colors = ButtonDefaults.buttonColors(disabledContainerColor = DreamlandMuted.copy(alpha = 0.2f)),
                ) {
                    Icon(Icons.Filled.Receipt, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Generate Invoice (PDF)", color = DreamlandMuted, fontWeight = FontWeight.Medium)
                }
                // Test button — creates billing doc
                Button(
                    onClick = { vm.confirmPayment() },
                    enabled = !cpd.isProcessing,
                    modifier = Modifier.fillMaxWidth().height(48.dp),
                    shape = RoundedCornerShape(10.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF4CAF50)),
                ) {
                    if (cpd.isProcessing) {
                        CircularProgressIndicator(color = Color.White, modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                    } else {
                        Text("Create Billing Record (Test)", color = Color.White, fontWeight = FontWeight.SemiBold)
                    }
                }
                TextButton(
                    onClick = { vm.closeConfirmPayment() },
                    enabled = !cpd.isProcessing,
                    modifier = Modifier.align(Alignment.End),
                ) {
                    Text("Cancel", color = DreamlandMuted)
                }
            }
        }
    }
}

@Composable
private fun ConfirmRow(label: String, value: String, valueColor: Color) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, color = DreamlandMuted, style = MaterialTheme.typography.bodySmall)
        Text(value, color = valueColor, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.SemiBold)
    }
}

// ── Shared input ──────────────────────────────────────────────────────────────

@Composable
private fun BillingTextField(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    keyboard: KeyboardType = KeyboardType.Text,
    modifier: Modifier = Modifier.fillMaxWidth(),
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label) },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = keyboard),
        modifier = modifier,
        colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = DreamlandGold,
            unfocusedBorderColor = DreamlandMuted.copy(alpha = 0.4f),
            focusedLabelColor = DreamlandGold,
            unfocusedLabelColor = DreamlandMuted,
            focusedTextColor = DreamlandOnDark,
            unfocusedTextColor = DreamlandOnDark,
        ),
    )
}
