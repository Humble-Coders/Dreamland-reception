@file:OptIn(ExperimentalMaterial3Api::class)

package com.example.dreamland_reception.billing

import androidx.compose.foundation.Image
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
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import com.example.dreamland_reception.data.accounting.CustomerBalanceInfo
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.AccountBalance
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AttachMoney
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.CropFree
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.Receipt
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
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
import androidx.compose.ui.graphics.toComposeImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.graphics.SolidColor
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
import com.example.dreamland_reception.ui.viewmodel.CatalogItem
import com.example.dreamland_reception.ui.viewmodel.StayBillingState
import com.example.dreamland_reception.ui.viewmodel.StayBillingViewModel
import com.example.dreamland_reception.ui.viewmodel.TaxDiscountDialog
import com.example.dreamland_reception.stays.AutocompleteItemField
import com.example.dreamland_reception.stays.SimpleDatePickerDialog
import com.example.dreamland_reception.settings.AddFoodDialogUI
import com.example.dreamland_reception.settings.AddServiceDialogUI
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit

private fun Double.fmtAmt(): String = "%.2f".format(this)
private fun Double.fmtRate(): String = if (this % 1.0 == 0.0) "${this.toInt()}%" else "${"%.2f".format(this)}%"

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
    val pd = state.addPaymentDialog
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
                val bill = state.bill
                val roomLabel = when {
                    bill != null && bill.roomNumbers.size > 1 -> "${bill.roomNumbers.size} Rooms: ${bill.roomNumbers.joinToString(", ")}"
                    bill?.roomNumber?.isNotBlank() == true -> "Room ${bill.roomNumber}"
                    state.stay?.roomNumber?.isNotBlank() == true -> "Room ${state.stay!!.roomNumber}"
                    else -> ""
                }
                // Editable guest name + picker icon — icon sits immediately next to name
                Row(verticalAlignment = Alignment.CenterVertically) {
                    BasicTextField(
                        value = state.billGuestName,
                        onValueChange = vm::onBillGuestName,
                        singleLine = true,
                        cursorBrush = SolidColor(Color.White),
                        textStyle = MaterialTheme.typography.titleLarge.copy(
                            color = DreamlandOnDark, fontWeight = FontWeight.Bold,
                        ),
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                        keyboardActions = KeyboardActions(onDone = { vm.saveBillGuestName() }),
                        modifier = Modifier
                            .widthIn(min = 60.dp)
                            .onFocusChanged { if (!it.isFocused) vm.saveBillGuestName() },
                        decorationBox = { inner ->
                            Box {
                                if (state.billGuestName.isEmpty()) {
                                    Text("Guest name", color = DreamlandMuted, style = MaterialTheme.typography.titleLarge)
                                }
                                inner()
                            }
                        },
                    )
                    IconButton(onClick = { vm.openGuestPicker() }, modifier = Modifier.size(28.dp)) {
                        Icon(Icons.Filled.Person, contentDescription = "Choose guest", tint = DreamlandGold.copy(alpha = 0.7f), modifier = Modifier.size(16.dp))
                    }
                }
                // Editable phone number row
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("+91 ", color = DreamlandMuted, style = MaterialTheme.typography.bodySmall)
                    BasicTextField(
                        value = state.billGuestPhone,
                        onValueChange = vm::onBillGuestPhone,
                        singleLine = true,
                        cursorBrush = SolidColor(DreamlandGold),
                        textStyle = MaterialTheme.typography.bodySmall.copy(color = DreamlandMuted),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone, imeAction = ImeAction.Done),
                        keyboardActions = KeyboardActions(onDone = { vm.saveBillGuestPhone() }),
                        modifier = Modifier
                            .widthIn(min = 60.dp)
                            .onFocusChanged { if (!it.isFocused) vm.saveBillGuestPhone() },
                        decorationBox = { inner ->
                            Box {
                                if (state.billGuestPhone.isEmpty()) {
                                    Text("Phone", color = DreamlandMuted.copy(alpha = 0.4f), style = MaterialTheme.typography.bodySmall)
                                }
                                inner()
                            }
                        },
                    )
                }
                // Optional GSTIN — for B2B tax invoices. Blank by default; rendered on the
                // invoice and stored on the ledger customer only when filled.
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("GSTIN ", color = DreamlandMuted, style = MaterialTheme.typography.bodySmall)
                    BasicTextField(
                        value = state.billGuestGstin,
                        onValueChange = vm::onBillGuestGstin,
                        singleLine = true,
                        cursorBrush = SolidColor(DreamlandGold),
                        textStyle = MaterialTheme.typography.bodySmall.copy(color = DreamlandMuted),
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                        keyboardActions = KeyboardActions(onDone = { vm.saveBillGuestGstin() }),
                        modifier = Modifier
                            .widthIn(min = 60.dp)
                            .onFocusChanged { if (!it.isFocused) vm.saveBillGuestGstin() },
                        decorationBox = { inner ->
                            Box {
                                if (state.billGuestGstin.isEmpty()) {
                                    Text("optional", color = DreamlandMuted.copy(alpha = 0.4f), style = MaterialTheme.typography.bodySmall)
                                }
                                inner()
                            }
                        },
                    )
                }
                if (roomLabel.isNotBlank()) Text(roomLabel, style = MaterialTheme.typography.bodySmall, color = DreamlandMuted)
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
                            .weight(0.7f)
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

                        // Nights mismatch warning banner
                        state.nightsMismatchCount?.let { nights ->
                            val dateNights = if (bill.checkInDate != null && bill.checkOutDate != null)
                                java.util.concurrent.TimeUnit.MILLISECONDS.toDays(bill.checkOutDate.time - bill.checkInDate.time).toInt() else null
                            val roomQtys = bill.items.filter { it.type == "ROOM" }.map { it.quantity }.distinct()
                            val hasCrossRoomMismatch = roomQtys.size > 1
                            val hasDateMismatch = dateNights != null && nights != dateNights
                            Column(
                                modifier = Modifier.fillMaxWidth()
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(Color(0xFFF39C12).copy(alpha = 0.10f))
                                    .border(1.dp, Color(0xFFF39C12).copy(alpha = 0.35f), RoundedCornerShape(10.dp))
                                    .padding(horizontal = 14.dp, vertical = 10.dp),
                                verticalArrangement = Arrangement.spacedBy(4.dp),
                            ) {
                                Text("⚠ Room nights changed to $nights", color = Color(0xFFF39C12), fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.bodySmall)
                                if (hasCrossRoomMismatch)
                                    Text("Some rooms still have different nights. Sync all rooms to $nights nights.", color = Color(0xFFF39C12).copy(alpha = 0.8f), style = MaterialTheme.typography.labelSmall)
                                if (hasDateMismatch)
                                    Text("Stay dates show $dateNights nights. Update check-out date to match.", color = Color(0xFFF39C12).copy(alpha = 0.8f), style = MaterialTheme.typography.labelSmall)
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    if (hasCrossRoomMismatch)
                                        TextButton(onClick = { vm.syncAllRoomsToNights() }, contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)) {
                                            Text("Sync all rooms", color = Color(0xFFF39C12), fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.labelSmall)
                                        }
                                    if (hasDateMismatch)
                                        TextButton(onClick = { vm.fixCheckoutDateForNights() }, contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)) {
                                            Text("Fix dates", color = Color(0xFFF39C12), fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.labelSmall)
                                        }
                                    Spacer(Modifier.weight(1f))
                                    IconButton(onClick = { vm.dismissNightsMismatch() }, modifier = Modifier.size(24.dp)) {
                                        Icon(Icons.Filled.Close, contentDescription = "Dismiss", tint = Color(0xFFF39C12).copy(alpha = 0.5f), modifier = Modifier.size(14.dp))
                                    }
                                }
                            }
                        }

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
                                    Icon(Icons.Filled.Add, contentDescription = null, tint = Color(0xFF0D1F17), modifier = Modifier.size(16.dp))
                                    Spacer(Modifier.width(4.dp))
                                    Text("Add Item", fontSize = 13.sp, color = Color(0xFF0D1F17), fontWeight = FontWeight.SemiBold)
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
                                    taxInclusive = bill.taxInclusive,
                                    onDelete = { vm.removeItem(item.id) },
                                    onEdit = { vm.openEditItem(item) },
                                )
                            }
                        }

                    }

                    // ── RIGHT: Summary + Payments ──────────────────────────────
                    Column(
                        modifier = Modifier
                            .weight(0.3f)
                            .fillMaxHeight()
                            .background(DreamlandForestSurface)
                            .border(1.dp, DreamlandGold.copy(alpha = 0.2f))
                            .verticalScroll(rememberScrollState())
                            .padding(20.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp),
                    ) {
                        // Guest's current Humble Ledger balance (prior dues / credit on account).
                        state.guestLedgerBalance?.let { bal -> GuestLedgerBalanceCard(bal) }
                        // Bill summary — editable tax%, advance, and live payment preview
                        BillSummaryCard(
                            bill = bill,
                            previewCash = pd.cashAmount.toDoubleOrNull() ?: 0.0,
                            previewBank = pd.bankAmount.toDoubleOrNull() ?: 0.0,
                            editableAdvancePaid = state.editableAdvancePaid,
                            editableDiscountType = state.editableDiscountType,
                            editableDiscountValue = state.editableDiscountValue,
                            onTaxInclusiveChange = vm::setTaxInclusive,
                            onAdvancePaidChange = vm::onAdvancePaidInline,
                            onAdvancePaidSave = vm::saveAdvancePaidInline,
                            onDiscountTypeChange = vm::onDiscountTypeInline,
                            onDiscountValueChange = vm::onDiscountValueInline,
                            onDiscountSave = vm::saveDiscountInline,
                        )

                        // Payments card
                        Card(
                            colors = CardDefaults.cardColors(containerColor = DreamlandForestElevated),
                            shape = RoundedCornerShape(12.dp),
                        ) {
                            Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 14.dp)) {
                                Text("Payments", style = MaterialTheme.typography.titleSmall, color = DreamlandGold, fontWeight = FontWeight.SemiBold, letterSpacing = 0.5.sp)
                                Spacer(Modifier.height(8.dp))
                                HorizontalDivider(color = DreamlandGold.copy(alpha = 0.15f))
                                if (!isPreview) {
                                    InlineAddPayment(pd = pd, vm = vm)
                                } else if (bill.advancePayment > 0) {
                                    PaymentRow(
                                        label = "Advance paid at check-in",
                                        amount = bill.advancePayment,
                                        method = "",
                                        date = null,
                                        isAdvance = true,
                                        onEdit = null,
                                    )
                                }
                            }
                        }

                        // Confirm Payment — disabled in preview mode or when no value typed in cash/bank
                        val confirmEnabled = !isPreview && (pd.cashAmount.isNotBlank() || pd.bankAmount.isNotBlank())
                        Button(
                            onClick = { if (confirmEnabled) vm.openConfirmPayment() },
                            enabled = confirmEnabled,
                            modifier = Modifier.fillMaxWidth().height(52.dp),
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = Color(0xFF4CAF50),
                                disabledContainerColor = DreamlandForestElevated,
                            ),
                        ) {
                            Text(
                                when {
                                    isPreview -> "Available after checkout"
                                    !confirmEnabled -> "Enter payment to confirm"
                                    else -> "Confirm Payment"
                                },
                                color = if (confirmEnabled) Color.White else DreamlandMuted,
                                fontWeight = FontWeight.SemiBold,
                                maxLines = 1,
                            )
                        }

                        // "Paid via QR" — fills BANK with the full balance due (disabled when already PAID)
                        if (!isPreview) {
                            val liveDiscValForBtn = state.editableDiscountValue.toDoubleOrNull() ?: bill.discountValue
                            val liveDiscAmtForBtn = when (state.editableDiscountType) {
                                "PERCENT" -> bill.subtotal * liveDiscValForBtn / 100.0
                                else -> liveDiscValForBtn
                            }
                            val liveTotalForBtn = (bill.subtotal + bill.taxAmount - liveDiscAmtForBtn).coerceAtLeast(0.0)
                            val liveAdvForBtn = state.editableAdvancePaid.toDoubleOrNull() ?: bill.advancePayment
                            val livePendingForBtn = (liveTotalForBtn - liveAdvForBtn - (pd.cashAmount.toDoubleOrNull() ?: 0.0) - (pd.bankAmount.toDoubleOrNull() ?: 0.0)).coerceAtLeast(0.0)
                            val hasBalanceDue = livePendingForBtn > 0
                            OutlinedButton(
                                onClick = { vm.payViaQr() },
                                enabled = hasBalanceDue,
                                modifier = Modifier.fillMaxWidth().height(44.dp),
                                shape = RoundedCornerShape(12.dp),
                                border = BorderStroke(1.dp, if (hasBalanceDue) DreamlandGold.copy(alpha = 0.5f) else DreamlandMuted.copy(alpha = 0.2f)),
                                colors = ButtonDefaults.outlinedButtonColors(contentColor = if (hasBalanceDue) DreamlandGold else DreamlandMuted),
                            ) {
                                Icon(Icons.Filled.CropFree, contentDescription = null, modifier = Modifier.size(16.dp), tint = if (hasBalanceDue) DreamlandGold else DreamlandMuted)
                                Spacer(Modifier.width(8.dp))
                                Text("Paid via QR", fontWeight = FontWeight.SemiBold, color = if (hasBalanceDue) DreamlandGold else DreamlandMuted)
                            }
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
                                Text(
                                    "It will also retry automatically next time this bill is opened.",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = DreamlandMuted,
                                )
                                TextButton(
                                    onClick = { vm.retryLedgerSync() },
                                    contentPadding = PaddingValues(horizontal = 0.dp),
                                ) {
                                    Text(
                                        "Retry sync now",
                                        style = MaterialTheme.typography.labelMedium,
                                        color = DreamlandGold,
                                        fontWeight = FontWeight.SemiBold,
                                    )
                                }
                            }
                            else -> Unit
                        }

                        Button(
                            onClick = { vm.openInvoicePdf() },
                            enabled = state.bill != null,
                            modifier = Modifier.fillMaxWidth().height(48.dp),
                            shape = RoundedCornerShape(10.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = DreamlandGold,
                                contentColor = DreamlandForest,
                                disabledContainerColor = DreamlandMuted.copy(alpha = 0.3f),
                            ),
                        ) {
                            Icon(Icons.Filled.Receipt, contentDescription = null, modifier = Modifier.size(18.dp), tint = DreamlandForest)
                            Spacer(Modifier.width(8.dp))
                            Text("Generate Invoice (PDF)", fontWeight = FontWeight.SemiBold, color = DreamlandForest)
                        }
                    }
                }
            }
        }
    }

    // ── Dialogs ───────────────────────────────────────────────────────────────
    val settingsVm = DreamlandAppInitializer.getSettingsViewModel()
    val settingsState by settingsVm.state.collectAsStateWithLifecycle()

    val d = state.addItemDialog
    if (d.show) AddItemDialog(d, vm, state)

    // Show Add Food Item dialog when triggered from ORDER autocomplete "+ Add as new food item"
    if (d.showAddFoodDialog) {
        AddFoodDialogUI(settingsState.addFoodDialog, settingsVm)
        LaunchedEffect(settingsState.addFoodDialog.show) {
            if (!settingsState.addFoodDialog.show) vm.onAddItemFoodDialogClosed()
        }
    }
    // Show Add Service dialog when triggered from SERVICE autocomplete "+ Add as new service"
    if (d.showAddServiceDialog) {
        AddServiceDialogUI(settingsState.addServiceDialog, settingsVm)
        LaunchedEffect(settingsState.addServiceDialog.show) {
            if (!settingsState.addServiceDialog.show) vm.onAddItemServiceDialogClosed()
        }
    }

    val ed = state.editBillItemDialog
    if (ed.show) EditBillItemDialogUI(ed, vm)

    val epd = state.editPaymentDialog
    if (epd.show) EditPaymentDialogUI(epd, vm)

    val td = state.taxDiscountDialog
    if (td.show) TaxDiscountDialogUI(td, vm)

    val cpd = state.confirmPaymentDialog
    if (cpd.show) {
        val bill4Dialog = state.bill
        val liveTaxForDialog = bill4Dialog?.taxAmount ?: 0.0
        val liveTotalForDialog = if (bill4Dialog != null) {
            val liveDiscVD = state.editableDiscountValue.toDoubleOrNull() ?: bill4Dialog.discountValue
            val liveDiscD = when (state.editableDiscountType) {
                "PERCENT" -> bill4Dialog.subtotal * liveDiscVD / 100.0; else -> liveDiscVD
            }
            (bill4Dialog.subtotal + liveTaxForDialog - liveDiscD).coerceAtLeast(0.0)
        } else 0.0
        ConfirmPaymentDialogUI(
            cpd = cpd,
            bill = state.bill,
            vm = vm,
            previewCash = pd.cashAmount.toDoubleOrNull() ?: 0.0,
            previewBank = pd.bankAmount.toDoubleOrNull() ?: 0.0,
            liveTotalAmount = liveTotalForDialog,
            liveTaxAmount = liveTaxForDialog,
        )
    }

    val ipd = state.invoicePdf
    if (ipd.show) InvoicePdfViewerDialog(ipd, vm, onClose = vm::closeInvoicePdf)

    if (state.guestPickerOpen) {
        GuestPickerDialog(
            guests = state.billGuests,
            currentName = state.billGuestName,
            onSelect = vm::selectBillGuest,
            onDismiss = vm::closeGuestPicker,
        )
    }

    if (showBackConfirm) {
        val bill = state.bill
        val liveStatusForBack = if (bill != null) {
            val liveTax = bill.taxAmount
            val discV = state.editableDiscountValue.toDoubleOrNull() ?: bill.discountValue
            val discA = when (state.editableDiscountType) { "PERCENT" -> bill.subtotal * discV / 100.0; else -> discV }
            val liveTotal = (bill.subtotal + liveTax - discA).coerceAtLeast(0.0)
            val liveAdv = state.editableAdvancePaid.toDoubleOrNull() ?: bill.advancePayment
            val liveReceived = liveAdv + (pd.cashAmount.toDoubleOrNull() ?: 0.0) + (pd.bankAmount.toDoubleOrNull() ?: 0.0)
            val livePending = (liveTotal - liveReceived).coerceAtLeast(0.0)
            when {
                livePending <= 0 && liveTotal > 0 -> "PAID"
                liveReceived > 0 -> "PARTIAL"
                else -> "PENDING"
            }
        } else "PENDING"
        BackConfirmDialog(
            billStatus = liveStatusForBack,
            onConfirm = {
                showBackConfirm = false
                DreamlandAppInitializer.getBillingViewModel().load()
                onBack()
            },
            onDismiss = { showBackConfirm = false },
        )
    }
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
private fun BillItemRow(item: BillItem, readOnly: Boolean = false, taxInclusive: Boolean = false, onDelete: () -> Unit, onEdit: () -> Unit) {
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
            // Mode-aware figures: when GST is inclusive the entered price is gross, so we back
            // the tax out and show the taxable BASE on the row (so the rows reconcile with the
            // Subtotal in the summary). When exclusive, the entered price IS the base.
            val taxed = item.taxPercentage > 0
            val divisor = 1.0 + item.taxPercentage / 100.0
            val baseTotal = if (taxInclusive && taxed) item.total / divisor else item.total
            val baseUnit = if (taxInclusive && taxed) item.unitPrice / divisor else item.unitPrice
            val gst = if (taxInclusive && taxed) item.total - baseTotal
                      else if (taxed) item.total * item.taxPercentage / 100.0
                      else 0.0
            Column(Modifier.weight(1f)) {
                Text(item.name, color = DreamlandOnDark, fontWeight = FontWeight.Medium, style = MaterialTheme.typography.bodyMedium)
                if (item.notes.isNotBlank()) Text(item.notes, color = DreamlandMuted, style = MaterialTheme.typography.labelSmall)
                Text("${item.quantity} × ₹${baseUnit.fmtAmt()}", color = DreamlandMuted, style = MaterialTheme.typography.labelSmall)
                if (taxed) {
                    // GST pill: rate + the GST rupees for this line.
                    Spacer(Modifier.height(3.dp))
                    Box(
                        Modifier
                            .clip(RoundedCornerShape(4.dp))
                            .background(DreamlandGold.copy(alpha = 0.12f))
                            .padding(horizontal = 6.dp, vertical = 2.dp),
                    ) {
                        Text(
                            "GST ${item.taxPercentage.fmtRate()} · ₹${gst.fmtAmt()}",
                            color = DreamlandGold,
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Medium,
                        )
                    }
                }
            }
            Text("₹${baseTotal.fmtAmt()}", color = DreamlandOnDark, fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.bodyMedium)
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
private fun GuestLedgerBalanceCard(bal: CustomerBalanceInfo) {
    val owes = bal.balance > 0.009
    val credit = bal.balance < -0.009
    val (label, amountText, color) = when {
        owes -> Triple("Previous dues (owes the hotel)", "₹${"%,.2f".format(bal.balance)}", Color(0xFFEF5350))
        credit -> Triple("Credit on account (prepaid)", "₹${"%,.2f".format(-bal.balance)}", Color(0xFF4CAF50))
        else -> Triple("No outstanding balance", "Settled", DreamlandMuted)
    }
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(color.copy(alpha = 0.12f))
            .border(1.dp, color.copy(alpha = 0.3f), RoundedCornerShape(12.dp))
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Text("HUMBLE LEDGER", color = DreamlandMuted, fontSize = 9.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 1.sp)
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Text(label, color = DreamlandOnDark, style = MaterialTheme.typography.bodySmall)
            Text(amountText, color = color, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
        }
    }
}

@Composable
internal fun BillSummaryCard(
    bill: Bill,
    previewCash: Double = 0.0,
    previewBank: Double = 0.0,
    readOnly: Boolean = false,
    editableAdvancePaid: String = "",
    editableDiscountType: String = "FLAT",
    editableDiscountValue: String = "",
    onTaxInclusiveChange: (Boolean) -> Unit = {},
    onAdvancePaidChange: (String) -> Unit = {},
    onAdvancePaidSave: () -> Unit = {},
    onDiscountTypeChange: (String) -> Unit = {},
    onDiscountValueChange: (String) -> Unit = {},
    onDiscountSave: () -> Unit = {},
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = DreamlandForestElevated),
        shape = RoundedCornerShape(12.dp),
    ) {
        Column(Modifier.fillMaxWidth().padding(20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("Summary", style = MaterialTheme.typography.titleMedium, color = DreamlandGold, fontWeight = FontWeight.SemiBold, letterSpacing = 0.5.sp)
            HorizontalDivider(color = DreamlandGold.copy(alpha = 0.2f))
            SummaryRow("Subtotal", bill.subtotal)

            // ── GST: read-only per-rate breakdown + Include/Exclude toggle ──
            // The rate comes from each item's category (shown as a pill on every row in the
            // left list) and is NOT editable here. The only control is whether the prices are
            // GST-inclusive (tax backed out of the price) or GST-added-on-top.
            // Discount (live, editable) — computed up-front because GST is charged on the
            // amount AFTER discount (discount applied before tax). The discount reduces the
            // taxable base, so each rate's GST scales down by the same fraction.
            val liveDiscountValue = editableDiscountValue.toDoubleOrNull() ?: bill.discountValue
            val liveDiscountAmount = when (editableDiscountType) {
                "PERCENT" -> bill.subtotal * liveDiscountValue / 100.0
                else -> liveDiscountValue
            }
            val liveDiscountFraction = if (bill.subtotal > 0.0) (liveDiscountAmount / bill.subtotal).coerceIn(0.0, 1.0) else 0.0

            // ── Discount (editable) — shown right after Subtotal because it is applied
            //    BEFORE tax; the GST rows below are charged on the resulting taxable value. ──
            if (readOnly) {
                if (liveDiscountAmount > 0) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("Discount", color = DreamlandMuted, style = MaterialTheme.typography.bodySmall)
                        Text("-₹${liveDiscountAmount.fmtAmt()}", color = Color(0xFF4CAF50), style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.SemiBold)
                    }
                }
            } else {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("Discount", color = DreamlandMuted, style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f))
                    listOf("FLAT" to "₹", "PERCENT" to "%").forEach { (key, label) ->
                        val selected = editableDiscountType == key
                        Box(
                            modifier = Modifier
                                .height(24.dp)
                                .clip(RoundedCornerShape(6.dp))
                                .background(if (selected) DreamlandGold.copy(alpha = 0.15f) else Color.Transparent)
                                .border(1.dp, if (selected) DreamlandGold else DreamlandMuted.copy(alpha = 0.25f), RoundedCornerShape(6.dp))
                                .clickable { onDiscountTypeChange(key) }
                                .padding(horizontal = 8.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(label, color = if (selected) DreamlandGold else DreamlandMuted, fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal, fontSize = 11.sp)
                        }
                    }
                    BasicTextField(
                        value = editableDiscountValue,
                        onValueChange = onDiscountValueChange,
                        singleLine = true,
                        cursorBrush = SolidColor(DreamlandOnDark),
                        textStyle = MaterialTheme.typography.bodySmall.copy(color = DreamlandOnDark, textAlign = TextAlign.End),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal, imeAction = ImeAction.Done),
                        keyboardActions = KeyboardActions(onDone = { onDiscountSave() }),
                        modifier = Modifier.width(56.dp).onFocusChanged { if (!it.isFocused) onDiscountSave() },
                        decorationBox = { inner ->
                            Box(contentAlignment = Alignment.CenterEnd) {
                                if (editableDiscountValue.isEmpty()) Text("0.00", color = DreamlandMuted.copy(alpha = 0.35f), style = MaterialTheme.typography.bodySmall, textAlign = TextAlign.End)
                                inner()
                            }
                        },
                    )
                }
                if (liveDiscountAmount > 0) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("Discount applied", color = Color(0xFF4CAF50), style = MaterialTheme.typography.labelSmall)
                        Text("-₹${liveDiscountAmount.fmtAmt()}", color = Color(0xFF4CAF50), style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.SemiBold)
                    }
                }
            }

            // Taxable value — shown only when a discount applies; this is what GST is charged on.
            if (liveDiscountAmount > 0) {
                SummaryRow("Taxable value", (bill.subtotal - liveDiscountAmount).coerceAtLeast(0.0))
            }

            val taxByRate = bill.items.filter { it.taxPercentage > 0 }.groupBy { it.taxPercentage }
            fun itemGst(item: BillItem): Double = when {
                !bill.taxEnabled || item.taxPercentage <= 0.0 -> 0.0
                bill.taxInclusive -> item.total - item.total / (1.0 + item.taxPercentage / 100.0)
                else -> item.total * item.taxPercentage / 100.0
            }
            val liveTaxAmount = bill.items.sumOf { itemGst(it) } * (1.0 - liveDiscountFraction)

            if (taxByRate.isNotEmpty()) {
                taxByRate.toSortedMap().forEach { (rate, rateItems) ->
                    val rateAmt = rateItems.sumOf { itemGst(it) } * (1.0 - liveDiscountFraction)
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("GST (${rate.fmtRate()})", color = DreamlandMuted, style = MaterialTheme.typography.bodySmall)
                        Text("₹${rateAmt.fmtAmt()}", color = DreamlandOnDark, style = MaterialTheme.typography.bodySmall)
                    }
                }
                if (readOnly) {
                    Text(
                        if (bill.taxInclusive) "GST included in price" else "GST added on top of prices",
                        color = DreamlandMuted, style = MaterialTheme.typography.labelSmall,
                    )
                } else {
                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text("GST included in price", color = DreamlandOnDark, style = MaterialTheme.typography.bodySmall)
                            Text(
                                if (bill.taxInclusive) "Prices already include GST" else "GST added on top of prices",
                                color = DreamlandMuted, style = MaterialTheme.typography.labelSmall,
                            )
                        }
                        Switch(
                            checked = bill.taxInclusive,
                            onCheckedChange = onTaxInclusiveChange,
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = DreamlandGold,
                                checkedTrackColor = DreamlandGold.copy(alpha = 0.4f),
                                uncheckedThumbColor = DreamlandMuted,
                                uncheckedTrackColor = DreamlandMuted.copy(alpha = 0.25f),
                            ),
                        )
                    }
                }
            }

            // Compute live totals before rendering rows that depend on them
            var advanceFocused by remember { mutableStateOf(false) }
            val liveAdvance = editableAdvancePaid.toDoubleOrNull() ?: bill.advancePayment
            val liveTotal = (bill.subtotal + liveTaxAmount - liveDiscountAmount).coerceAtLeast(0.0)

            HorizontalDivider(color = DreamlandGold.copy(alpha = 0.3f))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("Total", color = DreamlandOnDark, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                Text("₹${liveTotal.fmtAmt()}", color = DreamlandGold, fontWeight = FontWeight.Bold, fontSize = 20.sp)
            }

            // ── Advance paid row ─────────────────────────────────────────────
            if (readOnly) {
                if (liveAdvance > 0) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        Text("Advance paid (${bill.advancePaymentMethod})", color = DreamlandMuted, style = MaterialTheme.typography.bodySmall)
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("₹", color = DreamlandMuted, style = MaterialTheme.typography.bodySmall)
                            Spacer(Modifier.width(2.dp))
                            Text("%.2f".format(liveAdvance), color = DreamlandMuted, style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
            } else {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(6.dp))
                        .background(if (advanceFocused) DreamlandGold.copy(alpha = 0.05f) else Color.Transparent)
                        .border(
                            1.dp,
                            if (advanceFocused) DreamlandGold.copy(alpha = 0.5f) else Color.Transparent,
                            RoundedCornerShape(6.dp),
                        )
                        .padding(horizontal = if (advanceFocused) 8.dp else 0.dp, vertical = if (advanceFocused) 4.dp else 0.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text("Advance paid (${bill.advancePaymentMethod})", color = DreamlandMuted, style = MaterialTheme.typography.bodySmall)
                        if (!advanceFocused) {
                            Text("✎", color = DreamlandMuted.copy(alpha = 0.35f), fontSize = 9.sp)
                        }
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            "₹",
                            color = if (advanceFocused) DreamlandGold else DreamlandMuted,
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = if (advanceFocused) FontWeight.SemiBold else FontWeight.Normal,
                        )
                        Spacer(Modifier.width(2.dp))
                        BasicTextField(
                            value = editableAdvancePaid,
                            onValueChange = onAdvancePaidChange,
                            singleLine = true,
                            cursorBrush = SolidColor(DreamlandOnDark),
                            textStyle = MaterialTheme.typography.bodySmall.copy(
                                color = if (advanceFocused) DreamlandOnDark else DreamlandMuted,
                                textAlign = TextAlign.End,
                                fontWeight = if (advanceFocused) FontWeight.SemiBold else FontWeight.Normal,
                            ),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal, imeAction = ImeAction.Done),
                            keyboardActions = KeyboardActions(onDone = { onAdvancePaidSave() }),
                            modifier = Modifier
                                .width(70.dp)
                                .onFocusChanged { fs ->
                                    advanceFocused = fs.isFocused
                                    if (!fs.isFocused) onAdvancePaidSave()
                                },
                            decorationBox = { inner ->
                                Box(contentAlignment = Alignment.CenterEnd) {
                                    if (editableAdvancePaid.isEmpty()) Text("%.2f".format(liveAdvance), color = DreamlandMuted.copy(alpha = 0.35f), style = MaterialTheme.typography.bodySmall)
                                    inner()
                                }
                            },
                        )
                    }
                }
            }
            // Always use field values (previewCash/Bank are pre-filled from saved transactions
            // so they equal bill.totalPaid initially; clearing a field correctly shows 0)
            val effectiveReceived = liveAdvance + previewCash + previewBank
            if (previewCash > 0) SummaryRow("Cash", previewCash)
            if (previewBank > 0) SummaryRow("Bank", previewBank)
            val effectivePending = (liveTotal - effectiveReceived).coerceAtLeast(0.0)
            // Overpayment warning
            if (effectiveReceived > liveTotal && liveTotal > 0) {
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
                        "⚠ Payments exceed total by ₹${(effectiveReceived - liveTotal).fmtAmt()}",
                        color = Color(0xFFEF5350),
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
            }
            if (effectivePending > 0) {
                Row(
                    Modifier.fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .background(Color(0xFFEF5350).copy(alpha = 0.08f))
                        .padding(horizontal = 12.dp, vertical = 10.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text("Balance Due", color = Color(0xFFEF5350), fontWeight = FontWeight.Bold)
                    Text("₹${effectivePending.fmtAmt()}", color = Color(0xFFEF5350), fontWeight = FontWeight.Bold, fontSize = 16.sp)
                }
            } else if (bill.totalAmount > 0) {
                Row(
                    Modifier.fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .background(Color(0xFF4CAF50).copy(alpha = 0.08f))
                        .padding(horizontal = 12.dp, vertical = 10.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
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
private fun AddItemDialog(d: AddBillItemDialog, vm: StayBillingViewModel, state: StayBillingState) {
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

                    // ROOM: category + room number dropdowns
                    if (d.type == "ROOM") {
                        // Use Room.type as the authoritative category name source
                        val categories = state.rooms.map { it.type }.filter { it.isNotBlank() }.distinct().sorted()
                        DropdownField("Room Category", d.roomCategory, categories) { vm.onAddItemRoomCategory(it) }
                        // Filter instances by the selected category's Room id
                        val selectedRoomCat = if (d.roomCategory.isNotBlank()) state.rooms.find { it.type == d.roomCategory } else null
                        val filteredInstances = if (selectedRoomCat == null) state.roomInstances
                                                else state.roomInstances.filter { it.categoryId == selectedRoomCat.id }
                        val roomNumbers = filteredInstances.map { it.roomNumber }.filter { it.isNotBlank() }.distinct().sorted()
                        DropdownField("Room Number", d.roomNumber, roomNumbers) { num ->
                            val inst = state.roomInstances.find { it.roomNumber == num } ?: return@DropdownField
                            vm.onAddItemRoomInstanceSelected(inst)
                        }
                    }

                    // ORDER: food item autocomplete
                    if (d.type == "ORDER") {
                        val suggestions = state.foodItems
                            .filter { d.nameQuery.isBlank() || it.name.contains(d.nameQuery, ignoreCase = true) }
                            .map { CatalogItem(id = it.id, name = it.name, price = it.price, taxPercentage = it.taxPercentage, category = it.category, isAvailable = it.isAvailable) }
                        val allFoodNames = state.foodItems.map { it.name }.toSet()
                        AutocompleteItemField(
                            value = d.nameQuery,
                            suggestions = suggestions,
                            showSuggestions = d.showNameDropdown,
                            onValueChange = vm::onAddItemNameQuery,
                            onSuggestionSelected = { cat ->
                                val item = state.foodItems.find { it.id == cat.id } ?: return@AutocompleteItemField
                                vm.onAddItemFoodSelected(item)
                            },
                            onDismiss = vm::onAddItemNameDropdownDismiss,
                            allCatalogNames = allFoodNames,
                            onAddNew = vm::onAddItemAddNewFood,
                            addNewLabel = "Add as new food item",
                        )
                    }

                    // SERVICE: service autocomplete
                    if (d.type == "SERVICE") {
                        val svcSuggestions = state.services
                            .filter { d.nameQuery.isBlank() || it.name.contains(d.nameQuery, ignoreCase = true) }
                            .map { CatalogItem(id = it.id, name = it.name, price = it.price, taxPercentage = it.taxPercentage, category = "Services", isAvailable = it.isActive) }
                        val allSvcNames = state.services.map { it.name }.toSet()
                        AutocompleteItemField(
                            value = d.nameQuery,
                            suggestions = svcSuggestions,
                            showSuggestions = d.showNameDropdown,
                            onValueChange = vm::onAddItemNameQuery,
                            onSuggestionSelected = { cat ->
                                val svc = state.services.find { it.id == cat.id } ?: return@AutocompleteItemField
                                vm.onAddItemServiceSelected(svc)
                            },
                            onDismiss = vm::onAddItemNameDropdownDismiss,
                            allCatalogNames = allSvcNames,
                            onAddNew = vm::onAddItemAddNewService,
                            addNewLabel = "Add as new service",
                        )
                    }

                    // CUSTOM or ROOM: plain name field
                    if (d.type == "ROOM" || d.type == "CUSTOM") {
                        BillingTextField("Name", d.name, onValueChange = vm::onAddItemName)
                    }

                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        BillingTextField(if (d.type == "ROOM") "Nights" else "Quantity", d.quantity, onValueChange = vm::onAddItemQty, keyboard = KeyboardType.Number, modifier = Modifier.weight(1f))
                        BillingTextField("Unit Price (₹)", d.unitPrice, onValueChange = vm::onAddItemPrice, keyboard = KeyboardType.Decimal, modifier = Modifier.weight(1f))
                    }

                    // GST is taken automatically from the item's category/catalog — not editable
                    // here. It's shown as a pill on each line and controlled only by the
                    // Include/Exclude GST toggle in the summary.

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
private fun DropdownField(
    label: String,
    value: String,
    options: List<String>,
    onSelect: (String) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    val density = LocalDensity.current
    var fieldWidthDp by remember { mutableStateOf(0.dp) }
    Box {
        OutlinedTextField(
            value = value,
            onValueChange = {},
            readOnly = true,
            label = { Text(label, color = DreamlandMuted, fontSize = 13.sp) },
            trailingIcon = {
                Text(if (expanded) "▲" else "▼", color = DreamlandGold, style = MaterialTheme.typography.labelSmall, modifier = Modifier.padding(end = 12.dp))
            },
            modifier = Modifier.fillMaxWidth().onSizeChanged {
                fieldWidthDp = with(density) { it.width.toDp() }
            },
            colors = OutlinedTextFieldDefaults.colors(
                focusedTextColor = DreamlandOnDark,
                unfocusedTextColor = DreamlandOnDark,
                focusedBorderColor = DreamlandGold,
                unfocusedBorderColor = DreamlandMuted.copy(alpha = 0.4f),
            ),
        )
        Box(modifier = Modifier.matchParentSize().clickable { expanded = !expanded })
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
            modifier = Modifier.width(fieldWidthDp).background(DreamlandForestElevated),
        ) {
            if (options.isEmpty()) {
                DropdownMenuItem(
                    text = { Text("No options available", color = DreamlandMuted, style = MaterialTheme.typography.bodySmall) },
                    onClick = { expanded = false },
                )
            }
            options.forEach { option ->
                DropdownMenuItem(
                    text = { Text(option, color = DreamlandOnDark, style = MaterialTheme.typography.bodyMedium) },
                    onClick = { onSelect(option); expanded = false },
                )
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

@Composable
private fun InlineAddPayment(pd: AddPaymentDialog, vm: StayBillingViewModel) {
    Column(Modifier.fillMaxWidth()) {
        SimplePaymentField(
            label = "CASH",
            amount = pd.cashAmount,
            onAmountChange = vm::onCashAmount,
            onSave = { vm.updateMethodTotal("CASH") },
        )
        HorizontalDivider(color = DreamlandMuted.copy(alpha = 0.1f))
        SimplePaymentField(
            label = "BANK",
            amount = pd.bankAmount,
            onAmountChange = vm::onBankAmount,
            onSave = { vm.updateMethodTotal("BANK") },
        )
    }
}

@Composable
private fun SimplePaymentField(
    label: String,
    amount: String,
    onAmountChange: (String) -> Unit,
    onSave: () -> Unit,
) {
    var focused by remember { mutableStateOf(false) }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(6.dp))
            .background(if (focused) DreamlandGold.copy(alpha = 0.04f) else Color.Transparent)
            .padding(vertical = 12.dp, horizontal = if (focused) 6.dp else 0.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            label,
            color = if (focused) DreamlandOnDark else DreamlandMuted,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = if (focused) FontWeight.SemiBold else FontWeight.Normal,
            letterSpacing = 1.sp,
            modifier = Modifier.weight(1f),
        )
        Text("₹", color = if (focused) DreamlandGold else DreamlandMuted, style = MaterialTheme.typography.bodyMedium, fontWeight = if (focused) FontWeight.SemiBold else FontWeight.Normal)
        Spacer(Modifier.width(4.dp))
        BasicTextField(
            value = amount,
            onValueChange = onAmountChange,
            singleLine = true,
            cursorBrush = SolidColor(DreamlandOnDark),
            textStyle = MaterialTheme.typography.bodyMedium.copy(
                color = if (amount.isNotEmpty()) DreamlandOnDark else DreamlandMuted,
                textAlign = TextAlign.End,
                fontWeight = if (focused) FontWeight.SemiBold else FontWeight.Normal,
            ),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal, imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(onDone = {
                if ((amount.toDoubleOrNull() ?: 0.0) > 0) onSave()
            }),
            modifier = Modifier
                .width(80.dp)
                .onFocusChanged { focusState ->
                    focused = focusState.isFocused
                    if (!focusState.isFocused && (amount.toDoubleOrNull() ?: 0.0) > 0) onSave()
                },
            decorationBox = { inner ->
                Box(contentAlignment = Alignment.CenterEnd) {
                    if (amount.isEmpty()) {
                        Text("0.00", color = DreamlandMuted.copy(alpha = 0.4f), style = MaterialTheme.typography.bodyMedium, textAlign = TextAlign.End)
                    }
                    inner()
                }
            },
        )
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
                BillingTextField(
                    label = "Name",
                    value = ed.name,
                    onValueChange = vm::onEditItemName,
                    readOnly = ed.type == "ROOM",
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    BillingTextField("Quantity", ed.quantity, onValueChange = vm::onEditItemQty, keyboard = KeyboardType.Number, modifier = Modifier.weight(1f))
                    BillingTextField("Unit Price (₹)", ed.unitPrice, onValueChange = vm::onEditItemPrice, keyboard = KeyboardType.Decimal, modifier = Modifier.weight(1f))
                }
                // GST rate is inherited from the item's category/catalog and not editable here.
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
    previewCash: Double = 0.0,
    previewBank: Double = 0.0,
    liveTotalAmount: Double = bill?.totalAmount ?: 0.0,
    liveTaxAmount: Double = bill?.taxAmount ?: 0.0,
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
                            // Discount before tax: Subtotal → Discount → (Taxable) → GST → Total.
                            ConfirmRow("Subtotal", "₹${bill.subtotal.fmtAmt()}", DreamlandOnDark)
                            if (bill.discountAmount > 0) {
                                ConfirmRow("Discount", "-₹${bill.discountAmount.fmtAmt()}", Color(0xFF4CAF50))
                                ConfirmRow("Taxable value", "₹${(bill.subtotal - bill.discountAmount).coerceAtLeast(0.0).fmtAmt()}", DreamlandOnDark)
                            }
                            if (bill.taxEnabled && liveTaxAmount > 0)
                                ConfirmRow(if (bill.taxInclusive) "GST (incl.)" else "GST", "₹${liveTaxAmount.fmtAmt()}", DreamlandOnDark)
                            HorizontalDivider(color = DreamlandMuted.copy(alpha = 0.2f))
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text("Total", color = DreamlandOnDark, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodyMedium)
                                Text("₹${liveTotalAmount.fmtAmt()}", color = DreamlandGold, fontWeight = FontWeight.Bold, fontSize = 18.sp)
                            }
                            if (bill.advancePayment > 0)
                                ConfirmRow("Advance paid (${bill?.advancePaymentMethod ?: "CASH"})", "₹${bill.advancePayment.fmtAmt()}", Color(0xFF4CAF50))
                            // Show "Payments received" only when no preview payment fields are populated
                            // (previewCash/Bank already represent the same saved transactions)
                            val hasPreviewPayments = previewCash > 0 || previewBank > 0
                            if (!hasPreviewPayments && bill.totalPaid > 0)
                                ConfirmRow("Payments received", "₹${bill.totalPaid.fmtAmt()}", Color(0xFF4CAF50))
                            // Live payment amounts being entered now
                            if (previewCash > 0) ConfirmRow("Cash", "₹${previewCash.fmtAmt()}", Color(0xFF4CAF50))
                            if (previewBank > 0) ConfirmRow("Bank", "₹${previewBank.fmtAmt()}", Color(0xFF4CAF50))
                            // Effective pending = total - advance - saved payments - currently entered
                            // previewCash/previewBank are replacement totals (include saved transactions)
                            // so DO NOT add bill.totalPaid — that would double-count
                            val effectiveReceived = bill.advancePayment + previewCash + previewBank
                            val effectivePending = (liveTotalAmount - effectiveReceived).coerceAtLeast(0.0)
                            if (effectivePending > 0) {
                                HorizontalDivider(color = Color(0xFFEF5350).copy(alpha = 0.3f))
                                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                    Text("Balance Due", color = Color(0xFFEF5350), fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.bodyMedium)
                                    Text("₹${effectivePending.fmtAmt()}", color = Color(0xFFEF5350), fontWeight = FontWeight.Bold, fontSize = 16.sp)
                                }
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

// ── Invoice PDF Viewer ────────────────────────────────────────────────────────

@Composable
private fun InvoicePdfViewerDialog(
    ipd: com.example.dreamland_reception.ui.viewmodel.InvoicePdfState,
    vm: StayBillingViewModel,
    onClose: () -> Unit,
) {
    LaunchedEffect(Unit) { vm.loadPrinters() }
    Dialog(
        onDismissRequest = onClose,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth(0.7f)
                .fillMaxHeight(0.92f)
                .clip(RoundedCornerShape(16.dp))
                .background(DreamlandForestSurface),
        ) {
            Column(Modifier.fillMaxSize()) {
                // ── Header ────────────────────────────────────────────────────
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(DreamlandForestElevated)
                        .padding(horizontal = 20.dp, vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(Icons.Filled.Receipt, contentDescription = null, tint = DreamlandGold, modifier = Modifier.size(20.dp))
                    Spacer(Modifier.width(10.dp))
                    Column(Modifier.weight(1f)) {
                        Text("INVOICE", style = MaterialTheme.typography.labelSmall, color = DreamlandGold, letterSpacing = 2.sp)
                        Text("Tax Invoice (PDF)", style = MaterialTheme.typography.titleMedium, color = DreamlandOnDark, fontWeight = FontWeight.SemiBold)
                    }
                    // Printer dropdown + Print button (only when PDF is ready)
                    if (ipd.url.isNotBlank()) {
                        var printerExpanded by remember { mutableStateOf(false) }
                        Box {
                            OutlinedButton(
                                onClick = { if (ipd.availablePrinters.isNotEmpty()) printerExpanded = true },
                                modifier = Modifier.widthIn(min = 140.dp, max = 220.dp),
                                shape = RoundedCornerShape(8.dp),
                                border = BorderStroke(1.dp, DreamlandGold.copy(alpha = 0.4f)),
                                colors = ButtonDefaults.outlinedButtonColors(contentColor = DreamlandOnDark),
                                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp),
                            ) {
                                Text(
                                    when {
                                        ipd.availablePrinters.isEmpty() -> "No printers found"
                                        ipd.selectedPrinter.isBlank() -> "Select Printer"
                                        else -> ipd.selectedPrinter
                                    },
                                    maxLines = 1,
                                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                                    style = MaterialTheme.typography.bodySmall,
                                    modifier = Modifier.weight(1f),
                                )
                                Spacer(Modifier.width(4.dp))
                                if (ipd.availablePrinters.isNotEmpty())
                                    Text("▼", color = DreamlandGold, style = MaterialTheme.typography.labelSmall)
                            }
                            DropdownMenu(
                                expanded = printerExpanded,
                                onDismissRequest = { printerExpanded = false },
                                modifier = Modifier.background(DreamlandForestElevated),
                            ) {
                                ipd.availablePrinters.forEach { printer ->
                                    DropdownMenuItem(
                                        text = { Text(printer, color = DreamlandOnDark, style = MaterialTheme.typography.bodySmall) },
                                        onClick = { vm.selectPrinter(printer); printerExpanded = false },
                                    )
                                }
                            }
                        }
                        Spacer(Modifier.width(8.dp))
                        Button(
                            onClick = { vm.printInvoice() },
                            enabled = !ipd.isPrinting && ipd.selectedPrinter.isNotBlank() && ipd.availablePrinters.isNotEmpty(),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = DreamlandGold,
                                contentColor = Color(0xFF0D1F17),
                                disabledContainerColor = DreamlandGold.copy(alpha = 0.4f),
                                disabledContentColor = Color(0xFF0D1F17).copy(alpha = 0.5f),
                            ),
                            shape = RoundedCornerShape(8.dp),
                            contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp),
                        ) {
                            if (ipd.isPrinting) {
                                CircularProgressIndicator(Modifier.size(14.dp), strokeWidth = 2.dp, color = Color(0xFF0D1F17))
                                Spacer(Modifier.width(6.dp))
                            }
                            Text(
                                if (ipd.isPrinting) "Printing…" else "Print",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.SemiBold,
                                color = Color(0xFF0D1F17),
                            )
                        }
                        ipd.printError?.let { err ->
                            Spacer(Modifier.width(6.dp))
                            Text(
                                "⚠ $err",
                                color = Color(0xFFEF5350),
                                style = MaterialTheme.typography.labelSmall,
                                modifier = Modifier.widthIn(max = 180.dp),
                                maxLines = 1,
                                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                            )
                        }
                        Spacer(Modifier.width(4.dp))
                    }
                    // QR / Email / Open buttons
                    if (ipd.url.isNotBlank()) {
                        IconButton(onClick = { vm.openQrDialog() }) {
                            Icon(Icons.Filled.CropFree, contentDescription = "Show QR Code", tint = DreamlandGold)
                        }
                        IconButton(onClick = { vm.openEmailDialog() }) {
                            Icon(
                                Icons.Filled.Email,
                                contentDescription = "Send Email",
                                tint = if (ipd.sendSuccess) Color(0xFF4CAF50) else DreamlandGold,
                            )
                        }
                        IconButton(onClick = { openInBrowser(ipd.url) }) {
                            Icon(Icons.AutoMirrored.Filled.OpenInNew, contentDescription = "Open in browser", tint = DreamlandGold)
                        }
                    }
                    IconButton(onClick = onClose) {
                        Icon(Icons.Filled.Close, contentDescription = "Close", tint = DreamlandMuted)
                    }
                }

                // ── Send Email Dialog ─────────────────────────────────────────
                if (ipd.emailDialogOpen) {
                    Dialog(
                        onDismissRequest = { if (!ipd.isSending) vm.closeEmailDialog() },
                        properties = DialogProperties(usePlatformDefaultWidth = false),
                    ) {
                        Box(
                            modifier = Modifier
                                .widthIn(min = 320.dp, max = 440.dp)
                                .fillMaxWidth(0.35f)
                                .clip(RoundedCornerShape(16.dp))
                                .background(DreamlandForestSurface)
                                .padding(24.dp),
                        ) {
                            Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                                Text("Send Invoice by Email", color = DreamlandOnDark, fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.titleMedium)
                                OutlinedTextField(
                                    value = ipd.guestEmail,
                                    onValueChange = vm::onGuestEmailChanged,
                                    label = { Text("Recipient Email") },
                                    modifier = Modifier.fillMaxWidth(),
                                    singleLine = true,
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email, imeAction = ImeAction.Send),
                                    keyboardActions = KeyboardActions(onSend = { vm.sendInvoiceEmail() }),
                                    colors = OutlinedTextFieldDefaults.colors(
                                        focusedTextColor = DreamlandOnDark,
                                        unfocusedTextColor = DreamlandOnDark,
                                        focusedBorderColor = DreamlandGold,
                                        unfocusedBorderColor = DreamlandMuted.copy(alpha = 0.4f),
                                        cursorColor = DreamlandOnDark,
                                    ),
                                )
                                ipd.sendError?.let { err ->
                                    Text("⚠ $err", color = Color(0xFFEF5350), style = MaterialTheme.typography.labelSmall)
                                }
                                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                    TextButton(onClick = { vm.closeEmailDialog() }, modifier = Modifier.weight(1f)) {
                                        Text("Cancel", color = DreamlandMuted)
                                    }
                                    Button(
                                        onClick = { vm.sendInvoiceEmail() },
                                        enabled = ipd.guestEmail.trim().isNotBlank() && !ipd.isSending,
                                        modifier = Modifier.weight(2f),
                                        colors = ButtonDefaults.buttonColors(containerColor = DreamlandGold, contentColor = DreamlandForest),
                                        shape = RoundedCornerShape(10.dp),
                                    ) {
                                        if (ipd.isSending) {
                                            CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp, color = DreamlandForest)
                                            Spacer(Modifier.width(6.dp))
                                        }
                                        Text(if (ipd.isSending) "Sending…" else "Send Email", fontWeight = FontWeight.SemiBold)
                                    }
                                }
                            }
                        }
                    }
                }

                // ── QR Code Dialog ────────────────────────────────────────────
                if (ipd.showQrDialog) {
                    Dialog(
                        onDismissRequest = { vm.closeQrDialog() },
                        properties = DialogProperties(usePlatformDefaultWidth = false),
                    ) {
                        Box(
                            modifier = Modifier
                                .widthIn(min = 280.dp, max = 340.dp)
                                .fillMaxWidth(0.28f)
                                .clip(RoundedCornerShape(16.dp))
                                .background(DreamlandForestSurface)
                                .padding(24.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(14.dp),
                            ) {
                                Text("Scan to view invoice", color = DreamlandOnDark, fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.titleMedium)
                                if (ipd.qrImage != null) {
                                    androidx.compose.foundation.Image(
                                        bitmap = remember(ipd.qrImage) { ipd.qrImage.toComposeImageBitmap() },
                                        contentDescription = "Invoice QR Code",
                                        modifier = Modifier.size(260.dp).clip(RoundedCornerShape(8.dp)),
                                    )
                                } else {
                                    CircularProgressIndicator(color = DreamlandGold, modifier = Modifier.size(40.dp))
                                }
                                Text("Open on any phone camera", style = MaterialTheme.typography.labelSmall, color = DreamlandMuted)
                                TextButton(onClick = { vm.closeQrDialog() }) {
                                    Text("Close", color = DreamlandMuted)
                                }
                            }
                        }
                    }
                }

                // ── Body ──────────────────────────────────────────────────────
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    when {
                        ipd.isGenerating -> Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(14.dp),
                        ) {
                            CircularProgressIndicator(color = DreamlandGold)
                            Text("Generating invoice…", color = DreamlandMuted, style = MaterialTheme.typography.bodyMedium)
                            Text("This can take a few seconds.", color = DreamlandMuted.copy(alpha = 0.7f), style = MaterialTheme.typography.labelSmall)
                        }
                        ipd.error != null -> Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(12.dp),
                            modifier = Modifier.padding(32.dp),
                        ) {
                            Icon(Icons.Filled.Warning, contentDescription = null, tint = Color(0xFFEF5350), modifier = Modifier.size(32.dp))
                            Text("Could not generate the invoice", color = Color(0xFFEF5350), fontWeight = FontWeight.SemiBold)
                            Text(ipd.error, color = DreamlandMuted, style = MaterialTheme.typography.bodySmall, textAlign = TextAlign.Center)
                            if (ipd.url.isNotBlank()) {
                                OutlinedButton(
                                    onClick = { openInBrowser(ipd.url) },
                                    border = BorderStroke(1.dp, DreamlandGold.copy(alpha = 0.5f)),
                                    colors = ButtonDefaults.outlinedButtonColors(contentColor = DreamlandGold),
                                ) { Text("Open in browser") }
                            }
                        }
                        else -> LazyColumn(
                            modifier = Modifier.fillMaxSize().background(Color(0xFF20302A)),
                            contentPadding = PaddingValues(16.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                        ) {
                            items(ipd.pages) { page ->
                                Image(
                                    bitmap = remember(page) { page.toComposeImageBitmap() },
                                    contentDescription = "Invoice page",
                                    contentScale = ContentScale.FillWidth,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(4.dp)),
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

private fun openInBrowser(url: String) {
    runCatching {
        val desktop = java.awt.Desktop.getDesktop()
        desktop.browse(java.net.URI.create(url))
    }
}

// ── Guest Picker Dialog ───────────────────────────────────────────────────────

@Composable
private fun GuestPickerDialog(
    guests: List<com.example.dreamland_reception.ui.viewmodel.GuestNameOption>,
    currentName: String,
    onSelect: (com.example.dreamland_reception.ui.viewmodel.GuestNameOption) -> Unit,
    onDismiss: () -> Unit,
) {
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Box(
            modifier = Modifier
                .widthIn(min = 320.dp, max = 440.dp)
                .fillMaxWidth(0.38f)
                .clip(RoundedCornerShape(16.dp))
                .background(DreamlandForestSurface)
                .padding(24.dp),
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("SELECT BILL NAME", style = MaterialTheme.typography.labelLarge, color = DreamlandGold, letterSpacing = 2.sp)
                Text("Choose who to generate the bill for", color = DreamlandMuted, style = MaterialTheme.typography.bodySmall)
                HorizontalDivider(color = DreamlandGold.copy(alpha = 0.15f))
                if (guests.isEmpty()) {
                    Text("No guests found", color = DreamlandMuted, style = MaterialTheme.typography.bodySmall)
                } else {
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        guests.forEach { guest ->
                            val isSelected = guest.name == currentName
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(if (isSelected) DreamlandGold.copy(alpha = 0.1f) else DreamlandForestElevated)
                                    .border(1.dp, if (isSelected) DreamlandGold else Color.Transparent, RoundedCornerShape(8.dp))
                                    .clickable { onSelect(guest) }
                                    .padding(horizontal = 14.dp, vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp),
                            ) {
                                Column(Modifier.weight(1f)) {
                                    Text(guest.name, color = DreamlandOnDark, fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal, style = MaterialTheme.typography.bodyMedium)
                                    if (guest.phone.isNotBlank()) Text(guest.phone, color = DreamlandMuted, style = MaterialTheme.typography.labelSmall)
                                    Text("Room ${guest.roomNumber}", color = DreamlandMuted.copy(alpha = 0.7f), style = MaterialTheme.typography.labelSmall)
                                }
                                if (isSelected) Text("✓", color = DreamlandGold, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
                TextButton(onClick = onDismiss, modifier = Modifier.align(Alignment.End)) {
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

// ── Back Confirmation Dialog ───────────────────────────────────────────────────

@Composable
private fun BackConfirmDialog(billStatus: String, onConfirm: () -> Unit, onDismiss: () -> Unit) {
    val statusColor = when (billStatus) {
        "PAID"    -> Color(0xFF2ECC71)
        "PARTIAL" -> Color(0xFFF39C12)
        else      -> Color(0xFFFF9800)
    }
    val statusLabel = when (billStatus) {
        "PAID"    -> "PAID"
        "PARTIAL" -> "PARTIAL"
        else      -> "PENDING"
    }
    val description = when (billStatus) {
        "PAID"    -> "The bill is fully paid. Your changes are saved."
        "PARTIAL" -> "The bill has been partially paid. You can return to record the remaining payment."
        else      -> "The bill has been drafted and saved as PENDING. You can return to finalize and create the billing record later."
    }
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
                            .background(statusColor.copy(alpha = 0.15f)),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(Icons.Filled.Warning, contentDescription = null, tint = statusColor, modifier = Modifier.size(20.dp))
                    }
                    Column {
                        Text("Leave Billing?", style = MaterialTheme.typography.titleMedium, color = DreamlandOnDark, fontWeight = FontWeight.SemiBold)
                        Text("BILLING SUMMARY", style = MaterialTheme.typography.labelSmall, color = DreamlandMuted, letterSpacing = 1.5.sp)
                    }
                }

                HorizontalDivider(color = DreamlandMuted.copy(alpha = 0.15f))

                Text(
                    description,
                    color = DreamlandMuted,
                    style = MaterialTheme.typography.bodySmall,
                    lineHeight = 18.sp,
                )

                Box(
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .background(statusColor.copy(alpha = 0.1f))
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                ) {
                    Text("Status will be set to $statusLabel", color = statusColor, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.SemiBold)
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
                        colors = ButtonDefaults.buttonColors(containerColor = statusColor),
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
    readOnly: Boolean = false,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label) },
        singleLine = true,
        readOnly = readOnly,
        keyboardOptions = KeyboardOptions(keyboardType = keyboard),
        modifier = modifier,
        colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = DreamlandGold,
            unfocusedBorderColor = DreamlandMuted.copy(alpha = 0.4f),
            focusedLabelColor = DreamlandGold,
            unfocusedLabelColor = DreamlandMuted,
            focusedTextColor = if (readOnly) DreamlandMuted else DreamlandOnDark,
            unfocusedTextColor = if (readOnly) DreamlandMuted else DreamlandOnDark,
        ),
    )
}
