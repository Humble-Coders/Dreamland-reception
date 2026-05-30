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
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Receipt
import androidx.compose.material.icons.filled.Warning
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
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
import com.example.dreamland_reception.ui.viewmodel.AccountingStatus
import com.example.dreamland_reception.ui.viewmodel.AddBillItemDialog
import com.example.dreamland_reception.ui.viewmodel.AddPaymentDialog
import com.example.dreamland_reception.ui.viewmodel.EditBillItemDialog
import com.example.dreamland_reception.ui.viewmodel.EditPaymentDialog
import com.example.dreamland_reception.ui.viewmodel.StayBillingViewModel
import com.example.dreamland_reception.ui.viewmodel.TaxDiscountDialog
import com.example.dreamland_reception.stays.SimpleDatePickerDialog
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit

private fun Double.fmtAmt(): String = if (this % 1.0 == 0.0) "%.0f".format(this) else "%.2f".format(this)

private val dateFmt = SimpleDateFormat("dd MMM yyyy", Locale.getDefault())
private val timeFmt = SimpleDateFormat("dd MMM yyyy, hh:mm a", Locale.getDefault())

@Composable
fun StayBillingScreen(
    stayId: String = "",
    billId: String = "",
    onBack: () -> Unit,
    vm: StayBillingViewModel = DreamlandAppInitializer.getStayBillingViewModel(),
) {
    val state by vm.state.collectAsStateWithLifecycle()
    var showBackConfirm by remember { mutableStateOf(false) }

    LaunchedEffect(stayId, billId) {
        if (billId.isNotBlank()) vm.loadByBillId(billId)
        else vm.load(stayId)
    }

    Column(Modifier.fillMaxSize().background(DreamlandForest)) {
        // ── Header ────────────────────────────────────────────────────────────
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(DreamlandForestSurface)
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = { showBackConfirm = true }) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = DreamlandGold)
            }
            Spacer(Modifier.width(8.dp))
            Column(Modifier.weight(1f)) {
                Text("BILLING", style = MaterialTheme.typography.labelSmall, color = DreamlandGold, letterSpacing = 2.sp)
                val guest = state.bill?.guestName ?: state.stay?.guestName ?: ""
                val bill = state.bill
                val roomLabel = when {
                    bill != null && bill.roomNumbers.size > 1 -> "${bill.roomNumbers.size} Rooms: ${bill.roomNumbers.joinToString(", ")}"
                    bill?.roomNumber?.isNotBlank() == true -> "Room ${bill.roomNumber}"
                    state.stay?.roomNumber?.isNotBlank() == true -> "Room ${state.stay!!.roomNumber}"
                    else -> ""
                }
                if (guest.isNotBlank()) {
                    Text(guest, style = MaterialTheme.typography.titleLarge, color = DreamlandOnDark, fontWeight = FontWeight.Bold)
                    if (roomLabel.isNotBlank()) Text(roomLabel, style = MaterialTheme.typography.bodySmall, color = DreamlandMuted)
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
                val isPreview = bill.id.isBlank()   // active stay — not yet persisted in Firestore
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
                        // Preview mode banner
                        if (isPreview) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(DreamlandGold.copy(alpha = 0.08f))
                                    .border(1.dp, DreamlandGold.copy(alpha = 0.3f), RoundedCornerShape(10.dp))
                                    .padding(horizontal = 14.dp, vertical = 10.dp),
                                horizontalArrangement = Arrangement.spacedBy(10.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Icon(Icons.Filled.Receipt, contentDescription = null, tint = DreamlandGold, modifier = Modifier.size(18.dp))
                                Column {
                                    Text("Preview — Stay is still active", color = DreamlandGold, fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.bodySmall)
                                    Text("The bill will be created in Firestore at checkout. Editing is disabled until then.", color = DreamlandMuted, style = MaterialTheme.typography.labelSmall)
                                }
                            }
                        }

                        // Stay info card
                        StayInfoCard(bill, onCheckInChanged = if (!isPreview) { { vm.updateDates(it, bill.checkOutDate ?: it) } } else null, onCheckOutChanged = if (!isPreview) { { vm.updateDates(bill.checkInDate ?: it, it) } } else null)

                        // Items section
                        Row(
                            Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text("Bill Items", style = MaterialTheme.typography.titleMedium, color = DreamlandOnDark, fontWeight = FontWeight.SemiBold)
                            if (!isPreview) {
                                Button(
                                    onClick = { vm.openAddItem() },
                                    colors = ButtonDefaults.buttonColors(containerColor = DreamlandGold, contentColor = Color(0xFF0D1F17)),
                                    shape = RoundedCornerShape(8.dp),
                                    modifier = Modifier.height(36.dp),
                                ) {
                                    Icon(Icons.Filled.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(Modifier.width(4.dp))
                                    Text("Add Item", fontSize = 13.sp)
                                }
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
                                Text("No items yet.", color = DreamlandMuted, style = MaterialTheme.typography.bodySmall)
                            }
                        } else {
                            bill.items.forEach { item ->
                                BillItemRow(
                                    item = item,
                                    readOnly = isPreview,
                                    onDelete = { vm.removeItem(item.id) },
                                    onEdit = { vm.openEditItem(item) },
                                )
                            }
                        }

                        // Tax / Discount card
                        TaxDiscountSummaryCard(bill, onEdit = { vm.openTaxDiscount() }, readOnly = isPreview)
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
                            if (!isPreview && bill.pendingAmount > 0) {
                                Button(
                                    onClick = { vm.openAddPayment() },
                                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF4CAF50), contentColor = Color(0xFF0D1F17)),
                                    shape = RoundedCornerShape(8.dp),
                                    modifier = Modifier.height(34.dp),
                                ) {
                                    Icon(Icons.Filled.Add, contentDescription = null, modifier = Modifier.size(14.dp))
                                    Spacer(Modifier.width(4.dp))
                                    Text("Add Payment", fontSize = 12.sp, maxLines = 1)
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
                                onEdit = null,
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
                                onEdit = if (isPreview) null else { { vm.openEditPayment(tx) } },
                            )
                        }

                        Spacer(Modifier.height(4.dp))
                        HorizontalDivider(color = DreamlandGold.copy(alpha = 0.2f))
                        Spacer(Modifier.height(4.dp))

                        // Confirm Payment — disabled in preview mode
                        Button(
                            onClick = { if (!isPreview) vm.openConfirmPayment() },
                            enabled = !isPreview,
                            modifier = Modifier.fillMaxWidth().height(48.dp),
                            shape = RoundedCornerShape(10.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = Color(0xFF4CAF50),
                                disabledContainerColor = DreamlandMuted.copy(alpha = 0.2f),
                            ),
                        ) {
                            Text(
                                if (isPreview) "Confirm Payment (available after checkout)" else "Confirm Payment",
                                color = if (isPreview) DreamlandMuted else Color.White,
                                fontWeight = FontWeight.SemiBold,
                                maxLines = 1,
                            )
                        }

                        // Accounting sync status indicator
                        when (val status = state.accountingStatus) {
                            is AccountingStatus.InProgress -> Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                modifier = Modifier.fillMaxWidth(),
                            ) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(12.dp),
                                    color = DreamlandGold,
                                    strokeWidth = 1.5.dp,
                                )
                                Text(
                                    "Syncing to accounting ledger…",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = DreamlandMuted,
                                )
                            }
                            is AccountingStatus.Synced -> Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                modifier = Modifier.fillMaxWidth(),
                            ) {
                                Text("✓", color = Color(0xFF4CAF50), style = MaterialTheme.typography.labelSmall)
                                Text(
                                    "Ledger updated",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = Color(0xFF4CAF50),
                                )
                            }
                            is AccountingStatus.Failed -> Column(Modifier.fillMaxWidth()) {
                                Text(
                                    "⚠ Accounting sync failed",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = Color(0xFFEF9A9A),
                                    fontWeight = FontWeight.SemiBold,
                                )
                                Text(
                                    status.message,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = DreamlandMuted,
                                )
                            }
                            else -> Unit
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

    val ed = state.editBillItemDialog
    if (ed.show) EditBillItemDialogUI(ed, vm)

    val pd = state.addPaymentDialog
    if (pd.show) AddPaymentDialogUI(pd, vm)

    val epd = state.editPaymentDialog
    if (epd.show) EditPaymentDialogUI(epd, vm)

    val td = state.taxDiscountDialog
    if (td.show) TaxDiscountDialogUI(td, vm)

    val cpd = state.confirmPaymentDialog
    if (cpd.show) ConfirmPaymentDialogUI(cpd, state.bill, vm)

    if (showBackConfirm) BackConfirmDialog(
        onConfirm = {
            showBackConfirm = false
            DreamlandAppInitializer.getBillingViewModel().load()
            onBack()
        },
        onDismiss = { showBackConfirm = false },
    )
}

// ── Sub-composables ───────────────────────────────────────────────────────────

@Composable
internal fun StayInfoCard(
    bill: Bill,
    onCheckInChanged: ((Date) -> Unit)? = null,
    onCheckOutChanged: ((Date) -> Unit)? = null,
) {
    var showCheckInPicker by remember { mutableStateOf(false) }
    var showCheckOutPicker by remember { mutableStateOf(false) }

    val nights = if (bill.checkInDate != null && bill.checkOutDate != null) {
        (TimeUnit.MILLISECONDS.toDays(bill.checkOutDate.time - bill.checkInDate.time)).coerceAtLeast(1)
    } else null

    Card(
        colors = CardDefaults.cardColors(containerColor = DreamlandForestElevated),
        shape = RoundedCornerShape(12.dp),
    ) {
        Row(Modifier.fillMaxWidth().padding(14.dp), horizontalArrangement = Arrangement.SpaceEvenly) {
            EditableInfoCell(
                label = "Check-in",
                value = bill.checkInDate?.let { dateFmt.format(it) } ?: "-",
                editable = onCheckInChanged != null,
                onClick = { showCheckInPicker = true },
            )
            EditableInfoCell(
                label = "Check-out",
                value = bill.checkOutDate?.let { dateFmt.format(it) } ?: "-",
                editable = onCheckOutChanged != null,
                onClick = { showCheckOutPicker = true },
            )
            if (nights != null) InfoCell("Duration", "$nights night${if (nights != 1L) "s" else ""}")
        }
    }

    if (showCheckInPicker) {
        SimpleDatePickerDialog(
            initialDate = bill.checkInDate ?: Date(),
            onDateSelected = { date ->
                onCheckInChanged?.invoke(date)
                showCheckInPicker = false
            },
            onDismiss = { showCheckInPicker = false },
        )
    }
    if (showCheckOutPicker) {
        SimpleDatePickerDialog(
            initialDate = bill.checkOutDate ?: Date(),
            onDateSelected = { date ->
                onCheckOutChanged?.invoke(date)
                showCheckOutPicker = false
            },
            onDismiss = { showCheckOutPicker = false },
            minDate = bill.checkInDate,
        )
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
private fun EditableInfoCell(label: String, value: String, editable: Boolean, onClick: () -> Unit) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = if (editable) Modifier.clickable(onClick = onClick) else Modifier,
    ) {
        Text(label, style = MaterialTheme.typography.labelSmall, color = DreamlandMuted)
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(value, style = MaterialTheme.typography.bodyMedium, color = if (editable) DreamlandGold else DreamlandOnDark, fontWeight = FontWeight.SemiBold)
            if (editable) Icon(Icons.Filled.Edit, contentDescription = "Edit", tint = DreamlandGold.copy(alpha = 0.6f), modifier = Modifier.size(12.dp))
        }
    }
}

@Composable
private fun BillItemRow(item: BillItem, readOnly: Boolean = false, onDelete: () -> Unit, onEdit: () -> Unit) {
    Card(
        colors = CardDefaults.cardColors(containerColor = DreamlandForestSurface),
        shape = RoundedCornerShape(8.dp),
    ) {
        Row(
            Modifier.fillMaxWidth().padding(start = 12.dp, top = 10.dp, bottom = 10.dp, end = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TypeChip(item.type)
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text(item.name, color = DreamlandOnDark, fontWeight = FontWeight.Medium, style = MaterialTheme.typography.bodyMedium)
                if (item.notes.isNotBlank()) Text(item.notes, color = DreamlandMuted, style = MaterialTheme.typography.labelSmall)
                Text("${item.quantity} × ₹${item.unitPrice.fmtAmt()}", color = DreamlandMuted, style = MaterialTheme.typography.labelSmall)
            }
            Text("₹${item.total.fmtAmt()}", color = DreamlandOnDark, fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.bodyMedium)
            if (!readOnly) {
                IconButton(onClick = onEdit, modifier = Modifier.size(32.dp)) {
                    Icon(Icons.Filled.Edit, contentDescription = "Edit item", tint = DreamlandGold.copy(alpha = 0.7f), modifier = Modifier.size(16.dp))
                }
                IconButton(onClick = onDelete, modifier = Modifier.size(32.dp)) {
                    Icon(Icons.Filled.Delete, contentDescription = "Remove", tint = Color(0xFFEF5350), modifier = Modifier.size(18.dp))
                }
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
private fun TaxDiscountSummaryCard(bill: Bill, onEdit: () -> Unit, readOnly: Boolean = false) {
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
            if (!readOnly) {
                TextButton(onClick = onEdit) {
                    Text("Edit", color = DreamlandGold)
                }
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
                Text("₹${bill.totalAmount.fmtAmt()}", color = DreamlandGold, fontWeight = FontWeight.Bold, fontSize = 20.sp)
            }
            if (bill.advancePayment > 0) SummaryRow("Advance paid", bill.advancePayment)
            val totalReceived = bill.totalPaid + bill.advancePayment
            if (bill.totalPaid > 0) SummaryRow("Payments received", bill.totalPaid)
            // Overpayment warning
            if (totalReceived > bill.totalAmount && bill.totalAmount > 0) {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .background(Color(0xFFEF5350).copy(alpha = 0.12f))
                        .padding(horizontal = 10.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        "⚠ Payments exceed total by ₹${(totalReceived - bill.totalAmount).fmtAmt()}",
                        color = Color(0xFFEF5350),
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
            }
            if (bill.pendingAmount > 0) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("Balance Due", color = Color(0xFFEF5350), fontWeight = FontWeight.Bold)
                    Text("₹${bill.pendingAmount.fmtAmt()}", color = Color(0xFFEF5350), fontWeight = FontWeight.Bold, fontSize = 16.sp)
                }
            } else if (bill.totalAmount > 0) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("Balance Due", color = if (totalReceived > bill.totalAmount) Color(0xFFEF5350) else Color(0xFF4CAF50), fontWeight = FontWeight.Bold)
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
            text = if (amount < 0) "-₹${(-amount).fmtAmt()}" else "₹${amount.fmtAmt()}",
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
    onEdit: (() -> Unit)? = null,
) {
    Card(
        colors = CardDefaults.cardColors(
            containerColor = if (isAdvance) DreamlandGold.copy(alpha = 0.08f) else DreamlandForestElevated,
        ),
        shape = RoundedCornerShape(8.dp),
    ) {
        Row(Modifier.fillMaxWidth().padding(start = 10.dp, top = 8.dp, bottom = 8.dp, end = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(label.uppercase(), color = DreamlandOnDark, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold)
                if (date != null) Text(timeFmt.format(date), color = DreamlandMuted, style = MaterialTheme.typography.labelSmall)
                else if (isAdvance) Text("At check-in", color = DreamlandMuted, style = MaterialTheme.typography.labelSmall)
            }
            Text("₹${amount.fmtAmt()}", color = Color(0xFF4CAF50), fontWeight = FontWeight.Bold, fontSize = 15.sp)
            if (onEdit != null) {
                IconButton(onClick = onEdit, modifier = Modifier.size(32.dp)) {
                    Icon(Icons.Filled.Edit, contentDescription = "Edit payment", tint = DreamlandMuted, modifier = Modifier.size(16.dp))
                }
            }
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
                .widthIn(min = 360.dp, max = 480.dp)
                .fillMaxWidth(0.42f)
                .clip(RoundedCornerShape(16.dp))
                .background(DreamlandForestSurface)
                .padding(24.dp),
        ) {
            if (d.step == 0) {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("ADD ITEM", style = MaterialTheme.typography.labelLarge, color = DreamlandGold, letterSpacing = 2.sp)
                    Text("Select item type", style = MaterialTheme.typography.titleMedium, color = DreamlandOnDark, fontWeight = FontWeight.SemiBold)
                    Spacer(Modifier.height(2.dp))
                    listOf("ROOM" to "Room Charges", "ORDER" to "Food & Beverage", "SERVICE" to "Hotel Service", "CUSTOM" to "Custom").forEach { (type, label) ->
                        TypeSelectRow(type, label, onClick = { vm.onAddItemType(type) })
                    }
                    Spacer(Modifier.height(2.dp))
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
                    if (d.type == "ROOM") {
                        BillingTextField("Room Number", d.roomNumber, onValueChange = vm::onAddItemRoomNumber)
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
            .height(52.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(bg)
            .border(1.dp, accent.copy(alpha = 0.35f), RoundedCornerShape(10.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(label, color = DreamlandOnDark, fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.bodyMedium)
        Box(
            Modifier
                .clip(RoundedCornerShape(6.dp))
                .background(accent.copy(alpha = 0.18f))
                .padding(horizontal = 10.dp, vertical = 4.dp),
        ) {
            Text(type, color = accent, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, maxLines = 1)
        }
    }
}

// ── Edit Payment Dialog ───────────────────────────────────────────────────────

@Composable
private fun AddPaymentDialogUI(pd: AddPaymentDialog, vm: StayBillingViewModel) {
    Dialog(
        onDismissRequest = { if (!pd.isSaving) vm.closeAddPayment() },
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Box(
            modifier = Modifier
                .widthIn(min = 360.dp, max = 440.dp)
                .fillMaxWidth(0.42f)
                .clip(RoundedCornerShape(16.dp))
                .background(DreamlandForestSurface)
                .padding(24.dp),
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("ADD PAYMENT", style = MaterialTheme.typography.labelLarge, color = DreamlandGold, letterSpacing = 2.sp)
                Text("Payment Method", color = DreamlandMuted, style = MaterialTheme.typography.labelMedium)
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf("CASH", "BANK").forEach { method ->
                        val selected = pd.method == method
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .height(44.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(if (selected) DreamlandGold.copy(alpha = 0.15f) else DreamlandForestElevated)
                                .border(1.dp, if (selected) DreamlandGold else DreamlandMuted.copy(alpha = 0.3f), RoundedCornerShape(8.dp))
                                .clickable { vm.onPaymentMethod(method) },
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(method, color = if (selected) DreamlandGold else DreamlandMuted, fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal, maxLines = 1)
                        }
                    }
                }
                BillingTextField("Amount (₹)", pd.amount, onValueChange = vm::onPaymentAmount, keyboard = KeyboardType.Decimal)
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TextButton(onClick = { vm.closeAddPayment() }, modifier = Modifier.weight(1f).height(48.dp)) {
                        Text("Cancel", color = DreamlandMuted, maxLines = 1)
                    }
                    Button(
                        onClick = { vm.submitPayment() },
                        enabled = (pd.amount.toDoubleOrNull() ?: 0.0) > 0 && !pd.isSaving,
                        modifier = Modifier.weight(2f).height(48.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF4CAF50)),
                        shape = RoundedCornerShape(10.dp),
                    ) {
                        if (pd.isSaving) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp, color = Color.White)
                        else Text("Record Payment", color = Color.White, fontWeight = FontWeight.SemiBold, maxLines = 1)
                    }
                }
            }
        }
    }
}

// ── Edit Payment Dialog ───────────────────────────────────────────────────────

@Composable
private fun EditPaymentDialogUI(epd: EditPaymentDialog, vm: StayBillingViewModel) {
    Dialog(
        onDismissRequest = { if (!epd.isSaving) vm.closeEditPayment() },
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Box(
            modifier = Modifier
                .widthIn(min = 360.dp, max = 440.dp)
                .fillMaxWidth(0.42f)
                .clip(RoundedCornerShape(16.dp))
                .background(DreamlandForestSurface)
                .padding(24.dp),
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("EDIT PAYMENT", style = MaterialTheme.typography.labelLarge, color = DreamlandGold, letterSpacing = 2.sp)
                Text("Payment Method", color = DreamlandMuted, style = MaterialTheme.typography.labelMedium)
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf("CASH", "BANK").forEach { method ->
                        val selected = epd.method == method
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .height(44.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(if (selected) DreamlandGold.copy(alpha = 0.15f) else DreamlandForestElevated)
                                .border(1.dp, if (selected) DreamlandGold else DreamlandMuted.copy(alpha = 0.3f), RoundedCornerShape(8.dp))
                                .clickable { vm.onEditPaymentMethod(method) },
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(method, color = if (selected) DreamlandGold else DreamlandMuted, fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal, maxLines = 1)
                        }
                    }
                }
                BillingTextField("Amount (₹)", epd.amount, onValueChange = vm::onEditPaymentAmount, keyboard = KeyboardType.Decimal)
                if (epd.error != null) Text(epd.error, color = Color(0xFFEF5350), style = MaterialTheme.typography.bodySmall)
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TextButton(onClick = { vm.closeEditPayment() }, modifier = Modifier.weight(1f).height(48.dp)) {
                        Text("Cancel", color = DreamlandMuted, maxLines = 1)
                    }
                    Button(
                        onClick = { vm.submitEditPayment() },
                        enabled = (epd.amount.toDoubleOrNull() ?: 0.0) > 0 && !epd.isSaving,
                        modifier = Modifier.weight(2f).height(48.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = DreamlandGold),
                        shape = RoundedCornerShape(10.dp),
                    ) {
                        if (epd.isSaving) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp, color = DreamlandForest)
                        else Text("Save Changes", color = DreamlandForest, fontWeight = FontWeight.SemiBold, maxLines = 1)
                    }
                }
            }
        }
    }
}

// ── Edit Bill Item Dialog ─────────────────────────────────────────────────────

@Composable
private fun EditBillItemDialogUI(ed: EditBillItemDialog, vm: StayBillingViewModel) {
    Dialog(
        onDismissRequest = { if (!ed.isSaving) vm.closeEditItem() },
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Box(
            modifier = Modifier
                .widthIn(min = 360.dp, max = 480.dp)
                .fillMaxWidth(0.42f)
                .clip(RoundedCornerShape(16.dp))
                .background(DreamlandForestSurface)
                .padding(24.dp),
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    TypeChip(ed.type)
                    Spacer(Modifier.width(8.dp))
                    Text("Edit Item", color = DreamlandOnDark, fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.titleMedium)
                }
                BillingTextField("Name", ed.name, onValueChange = vm::onEditItemName)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    BillingTextField("Quantity", ed.quantity, onValueChange = vm::onEditItemQty, keyboard = KeyboardType.Number, modifier = Modifier.weight(1f))
                    BillingTextField("Unit Price (₹)", ed.unitPrice, onValueChange = vm::onEditItemPrice, keyboard = KeyboardType.Decimal, modifier = Modifier.weight(1f))
                }
                BillingTextField("Notes (optional)", ed.notes, onValueChange = vm::onEditItemNotes)
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TextButton(onClick = { vm.closeEditItem() }, modifier = Modifier.weight(1f).height(48.dp)) {
                        Text("Cancel", color = DreamlandMuted, maxLines = 1)
                    }
                    Button(
                        onClick = { vm.submitEditItem() },
                        enabled = ed.name.isNotBlank() && !ed.isSaving,
                        modifier = Modifier.weight(2f).height(48.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = DreamlandGold),
                        shape = RoundedCornerShape(10.dp),
                    ) {
                        if (ed.isSaving) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp, color = DreamlandForest)
                        else Text("Save Changes", color = DreamlandForest, fontWeight = FontWeight.SemiBold, maxLines = 1)
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
                .widthIn(min = 360.dp, max = 440.dp)
                .fillMaxWidth(0.42f)
                .clip(RoundedCornerShape(16.dp))
                .background(DreamlandForestSurface)
                .padding(24.dp),
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                Text("TAX & DISCOUNT", style = MaterialTheme.typography.labelLarge, color = DreamlandGold, letterSpacing = 2.sp)

                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text("Enable Tax (GST)", color = DreamlandOnDark, style = MaterialTheme.typography.bodyLarge)
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
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    listOf("FLAT" to "Flat (₹)", "PERCENT" to "Percent (%)").forEach { (type, label) ->
                        val selected = td.discountType == type
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .height(44.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .background(if (selected) DreamlandGold.copy(alpha = 0.15f) else DreamlandForestElevated)
                                .border(1.dp, if (selected) DreamlandGold else DreamlandMuted.copy(alpha = 0.3f), RoundedCornerShape(10.dp))
                                .clickable { vm.onDiscountType(type) },
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                label,
                                color = if (selected) DreamlandGold else DreamlandMuted,
                                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                                style = MaterialTheme.typography.bodySmall,
                                maxLines = 1,
                            )
                        }
                    }
                }

                BillingTextField("Discount Value", td.discountValue, onValueChange = vm::onDiscountValue, keyboard = KeyboardType.Decimal)

                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TextButton(
                        onClick = { vm.closeTaxDiscount() },
                        modifier = Modifier.weight(1f).height(48.dp),
                    ) {
                        Text("Cancel", color = DreamlandMuted, maxLines = 1)
                    }
                    Button(
                        onClick = { vm.submitTaxDiscount() },
                        enabled = !td.isSaving,
                        modifier = Modifier.weight(2f).height(48.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = DreamlandGold),
                        shape = RoundedCornerShape(10.dp),
                    ) {
                        if (td.isSaving) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp, color = DreamlandForest)
                        else Text("Apply", color = DreamlandForest, fontWeight = FontWeight.SemiBold, maxLines = 1)
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
                .widthIn(min = 360.dp, max = 460.dp)
                .fillMaxWidth(0.42f)
                .clip(RoundedCornerShape(20.dp))
                .background(DreamlandForestSurface)
                .padding(0.dp),
        ) {
            Column {
                // ── Coloured header band ──────────────────────────────────────
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(DreamlandForestElevated)
                        .padding(horizontal = 24.dp, vertical = 20.dp),
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text("FINALIZE BILLING", style = MaterialTheme.typography.labelSmall, color = DreamlandGold, letterSpacing = 2.sp)
                        if (bill != null) {
                            Text(bill.guestName, style = MaterialTheme.typography.titleLarge, color = DreamlandOnDark, fontWeight = FontWeight.Bold)
                            Text("Room ${bill.roomNumber}", style = MaterialTheme.typography.bodySmall, color = DreamlandMuted)
                        }
                    }
                }

                // ── Body ──────────────────────────────────────────────────────
                Column(
                    modifier = Modifier.padding(horizontal = 24.dp, vertical = 20.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp),
                ) {
                    if (bill != null) {
                        // Financial summary
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .background(DreamlandForestElevated)
                                .padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            ConfirmRow("Subtotal", "₹${bill.subtotal.fmtAmt()}", DreamlandOnDark)
                            if (bill.taxEnabled && bill.taxAmount > 0)
                                ConfirmRow("Tax (${bill.taxPercentage.toInt()}%)", "₹${bill.taxAmount.fmtAmt()}", DreamlandOnDark)
                            if (bill.discountAmount > 0)
                                ConfirmRow("Discount", "-₹${bill.discountAmount.fmtAmt()}", Color(0xFF4CAF50))
                            HorizontalDivider(color = DreamlandMuted.copy(alpha = 0.2f))
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text("Total", color = DreamlandOnDark, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodyMedium)
                                Text("₹${bill.totalAmount.fmtAmt()}", color = DreamlandGold, fontWeight = FontWeight.Bold, fontSize = 18.sp)
                            }
                            if (bill.advancePayment > 0)
                                ConfirmRow("Advance paid", "₹${bill.advancePayment.fmtAmt()}", Color(0xFF4CAF50))
                            if (bill.totalPaid > 0)
                                ConfirmRow("Payments received", "₹${bill.totalPaid.fmtAmt()}", Color(0xFF4CAF50))
                            if (bill.pendingAmount > 0) {
                                HorizontalDivider(color = Color(0xFFEF5350).copy(alpha = 0.3f))
                                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                    Text("Balance Due", color = Color(0xFFEF5350), fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.bodyMedium)
                                    Text("₹${bill.pendingAmount.fmtAmt()}", color = Color(0xFFEF5350), fontWeight = FontWeight.Bold, fontSize = 16.sp)
                                }
                            }
                        }

                        // Status preview
                        val willBePaid = bill.pendingAmount <= 0 && bill.totalAmount > 0
                        val statusColor = if (willBePaid) Color(0xFF4CAF50) else Color(0xFFFF9800)
                        val statusLabel = if (willBePaid) "PAID" else "PARTIAL"
                        val statusDesc = if (willBePaid) "Full amount received — bill will be marked as Paid" else "Balance still due — bill will be marked as Partial"
                        val statusIcon = if (willBePaid) Icons.Filled.CheckCircle else Icons.Filled.Receipt
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(10.dp))
                                .background(statusColor.copy(alpha = 0.12f))
                                .padding(horizontal = 14.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                        ) {
                            Icon(statusIcon, contentDescription = null, tint = statusColor, modifier = Modifier.size(20.dp))
                            Column {
                                Text("Status → $statusLabel", color = statusColor, fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.bodySmall)
                                Text(statusDesc, color = statusColor.copy(alpha = 0.8f), style = MaterialTheme.typography.labelSmall)
                            }
                        }
                    }

                    if (cpd.error != null) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .background(Color(0xFFEF5350).copy(alpha = 0.1f))
                                .padding(10.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Icon(Icons.Filled.Warning, contentDescription = null, tint = Color(0xFFEF5350), modifier = Modifier.size(16.dp))
                            Text(cpd.error, color = Color(0xFFEF5350), style = MaterialTheme.typography.bodySmall)
                        }
                    }

                    // Actions
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Button(
                            onClick = { vm.closeConfirmPayment() },
                            enabled = !cpd.isProcessing,
                            modifier = Modifier.weight(1f).height(48.dp),
                            shape = RoundedCornerShape(10.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = DreamlandForestElevated),
                        ) {
                            Text("Cancel", color = DreamlandMuted, fontWeight = FontWeight.Medium)
                        }
                        Button(
                            onClick = { vm.confirmPayment() },
                            enabled = !cpd.isProcessing,
                            modifier = Modifier.weight(2f).height(48.dp),
                            shape = RoundedCornerShape(10.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF4CAF50)),
                        ) {
                            if (cpd.isProcessing) {
                                CircularProgressIndicator(color = Color.White, modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                            } else {
                                Icon(Icons.Filled.CheckCircle, contentDescription = null, modifier = Modifier.size(18.dp), tint = Color.White)
                                Spacer(Modifier.width(8.dp))
                                Text("Create Billing", color = Color.White, fontWeight = FontWeight.SemiBold)
                            }
                        }
                    }
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

// ── Back Confirmation Dialog ───────────────────────────────────────────────────

@Composable
private fun BackConfirmDialog(onConfirm: () -> Unit, onDismiss: () -> Unit) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Box(
            modifier = Modifier
                .widthIn(min = 320.dp, max = 420.dp)
                .fillMaxWidth(0.38f)
                .clip(RoundedCornerShape(16.dp))
                .background(DreamlandForestSurface)
                .padding(24.dp),
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Box(
                        Modifier
                            .size(36.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(Color(0xFFFF9800).copy(alpha = 0.15f)),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(Icons.Filled.Warning, contentDescription = null, tint = Color(0xFFFF9800), modifier = Modifier.size(20.dp))
                    }
                    Column {
                        Text("Leave Billing?", style = MaterialTheme.typography.titleMedium, color = DreamlandOnDark, fontWeight = FontWeight.SemiBold)
                        Text("UNSAVED BILLING", style = MaterialTheme.typography.labelSmall, color = DreamlandMuted, letterSpacing = 1.5.sp)
                    }
                }

                HorizontalDivider(color = DreamlandMuted.copy(alpha = 0.15f))

                Text(
                    "The bill has been drafted and saved as PENDING. You can return to finalize and create the billing record later.",
                    color = DreamlandMuted,
                    style = MaterialTheme.typography.bodySmall,
                    lineHeight = 18.sp,
                )

                Box(
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .background(Color(0xFFFF9800).copy(alpha = 0.1f))
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                ) {
                    Text("Status will be set to PENDING", color = Color(0xFFFF9800), style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.SemiBold)
                }

                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Button(
                        onClick = onDismiss,
                        modifier = Modifier.weight(1f).height(44.dp),
                        shape = RoundedCornerShape(10.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = DreamlandForestElevated),
                    ) {
                        Text("Keep Editing", color = DreamlandOnDark, fontWeight = FontWeight.Medium, maxLines = 1)
                    }
                    Button(
                        onClick = onConfirm,
                        modifier = Modifier.weight(1f).height(44.dp),
                        shape = RoundedCornerShape(10.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFF9800)),
                    ) {
                        Text("Go Back", color = Color.White, fontWeight = FontWeight.SemiBold, maxLines = 1)
                    }
                }
            }
        }
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
