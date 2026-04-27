package com.example.dreamland_reception

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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CreditCard
import androidx.compose.material.icons.automirrored.filled.Sort
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.dreamland_reception.billing.BillSummaryCard
import com.example.dreamland_reception.billing.PaymentRow
import com.example.dreamland_reception.billing.StayInfoCard
import com.example.dreamland_reception.billing.TypeChip
import com.example.dreamland_reception.data.model.Bill
import com.example.dreamland_reception.ui.viewmodel.BillingScreenState
import com.example.dreamland_reception.ui.viewmodel.BillingViewModel
import java.text.SimpleDateFormat
import java.util.Locale

private val dateFmt = SimpleDateFormat("dd MMM yyyy", Locale.getDefault())

@Composable
fun BillingScreen(
    vm: BillingViewModel = DreamlandAppInitializer.getBillingViewModel(),
    onOpenStayBilling: (stayId: String) -> Unit = {},
) {
    val state by vm.state.collectAsStateWithLifecycle()

    Column(Modifier.fillMaxSize().background(DreamlandForest)) {
        // ── Header ─────────────────────────────────────────────────────────────
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(DreamlandForestSurface)
                .padding(horizontal = 24.dp, vertical = 18.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(Icons.Filled.CreditCard, contentDescription = null, tint = DreamlandGold, modifier = Modifier.size(28.dp))
            Spacer(Modifier.width(12.dp))
            Column {
                Text("PAYMENTS", style = MaterialTheme.typography.labelSmall, color = DreamlandGold, letterSpacing = 2.sp)
                Text("Billing", style = MaterialTheme.typography.headlineSmall, color = DreamlandOnDark, fontWeight = FontWeight.Bold)
            }
        }
        HorizontalDivider(color = DreamlandGold.copy(alpha = 0.15f))

        // ── Summary bar ─────────────────────────────────────────────────────────
        if (state.bills.isNotEmpty()) {
            BillingSummaryBar(state)
            HorizontalDivider(color = DreamlandGold.copy(alpha = 0.1f))
        }

        when {
            state.isLoading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = DreamlandGold)
            }
            state.error != null -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(state.error ?: "Error", color = Color(0xFFEF5350))
            }
            else -> Row(Modifier.fillMaxSize()) {
                // ── LEFT 30% — list panel ─────────────────────────────────────
                Column(
                    modifier = Modifier
                        .weight(0.3f)
                        .fillMaxHeight()
                        .background(DreamlandForestSurface),
                ) {
                    // Filter + sort bar
                    FilterSortBar(state, vm)
                    HorizontalDivider(color = DreamlandGold.copy(alpha = 0.1f))

                    if (state.filteredSorted.isEmpty()) {
                        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Text(
                                if (state.bills.isEmpty()) "No billing records yet" else "No records match filter",
                                color = DreamlandMuted,
                                style = MaterialTheme.typography.bodySmall,
                            )
                        }
                    } else {
                        LazyColumn(Modifier.fillMaxSize()) {
                            items(state.filteredSorted, key = { it.id }) { bill ->
                                BillListCard(
                                    bill = bill,
                                    selected = bill.id == state.selectedBillId,
                                    onClick = { vm.selectBill(bill.id) },
                                )
                                HorizontalDivider(color = DreamlandGold.copy(alpha = 0.06f))
                            }
                        }
                    }
                }

                VerticalDivider(color = DreamlandGold.copy(alpha = 0.2f))

                // ── RIGHT 70% — detail panel ──────────────────────────────────
                Box(
                    modifier = Modifier
                        .weight(0.7f)
                        .fillMaxHeight()
                        .background(DreamlandForest),
                ) {
                    val selected = state.selectedBill
                    if (selected == null) {
                        Column(
                            modifier = Modifier.fillMaxSize(),
                            verticalArrangement = Arrangement.Center,
                            horizontalAlignment = Alignment.CenterHorizontally,
                        ) {
                            Icon(Icons.Filled.CreditCard, contentDescription = null, tint = DreamlandMuted, modifier = Modifier.size(48.dp))
                            Spacer(Modifier.height(12.dp))
                            Text("Select a record to view details", color = DreamlandMuted, style = MaterialTheme.typography.bodyMedium)
                        }
                    } else {
                        BillDetailPanel(bill = selected, onOpenFull = { onOpenStayBilling(it) })
                    }
                }
            }
        }
    }
}

// ── Summary bar ───────────────────────────────────────────────────────────────

@Composable
private fun BillingSummaryBar(state: BillingScreenState) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(DreamlandForestSurface)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        SummaryStatCard("Total Revenue", "₹${state.totalRevenue.toLong()}", DreamlandGold, Modifier.weight(1f))
        SummaryStatCard("Collected", "₹${state.totalCollected.toLong()}", Color(0xFF4CAF50), Modifier.weight(1f))
        SummaryStatCard("Outstanding", "₹${state.totalOutstanding.toLong()}", Color(0xFFEF5350), Modifier.weight(1f))
        SummaryStatCard("Paid", "${state.paidCount}", Color(0xFF4CAF50), Modifier.weight(1f))
        SummaryStatCard("Partial", "${state.partialCount}", Color(0xFFFF9800), Modifier.weight(1f))
        SummaryStatCard("Pending", "${state.pendingCount}", Color(0xFFEF5350), Modifier.weight(1f))
    }
}

@Composable
private fun SummaryStatCard(label: String, value: String, accent: Color, modifier: Modifier = Modifier) {
    Card(
        colors = CardDefaults.cardColors(containerColor = DreamlandForestElevated),
        shape = RoundedCornerShape(10.dp),
        modifier = modifier,
    ) {
        Column(
            Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(value, color = accent, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
            Text(label, color = DreamlandMuted, style = MaterialTheme.typography.labelSmall)
        }
    }
}

// ── Filter + sort bar ─────────────────────────────────────────────────────────

@Composable
private fun FilterSortBar(state: BillingScreenState, vm: BillingViewModel) {
    Column(Modifier.fillMaxWidth().padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        // Status filter chips
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            listOf("ALL", "PAID", "PARTIAL", "PENDING").forEach { filter ->
                val selected = state.statusFilter == filter
                val accent = when (filter) {
                    "PAID" -> Color(0xFF4CAF50)
                    "PARTIAL" -> Color(0xFFFF9800)
                    "PENDING" -> Color(0xFFEF5350)
                    else -> DreamlandGold
                }
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .background(if (selected) accent.copy(alpha = 0.15f) else DreamlandForestElevated)
                        .border(1.dp, if (selected) accent else DreamlandMuted.copy(alpha = 0.2f), RoundedCornerShape(6.dp))
                        .clickable { vm.onStatusFilter(filter) }
                        .padding(horizontal = 10.dp, vertical = 6.dp),
                ) {
                    Text(
                        filter,
                        color = if (selected) accent else DreamlandMuted,
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                    )
                }
            }
        }
        // Sort toggle
        Row(
            Modifier
                .clip(RoundedCornerShape(6.dp))
                .border(1.dp, DreamlandMuted.copy(alpha = 0.2f), RoundedCornerShape(6.dp))
                .clickable { vm.onSortOrder(if (state.sortOrder == "NEWEST") "OLDEST" else "NEWEST") }
                .padding(horizontal = 10.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Icon(Icons.AutoMirrored.Filled.Sort, contentDescription = null, tint = DreamlandMuted, modifier = Modifier.size(14.dp))
            Text(
                if (state.sortOrder == "NEWEST") "Newest first" else "Oldest first",
                color = DreamlandMuted,
                style = MaterialTheme.typography.labelSmall,
            )
        }
    }
}

// ── Bill list card ────────────────────────────────────────────────────────────

@Composable
private fun BillListCard(bill: Bill, selected: Boolean, onClick: () -> Unit) {
    val statusColor = when (bill.status) {
        "PAID" -> Color(0xFF4CAF50)
        "PARTIAL" -> Color(0xFFFF9800)
        else -> Color(0xFFEF5350)
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(if (selected) DreamlandGold.copy(alpha = 0.08f) else Color.Transparent)
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // Status accent bar
        Box(
            modifier = Modifier
                .width(3.dp)
                .height(40.dp)
                .clip(RoundedCornerShape(2.dp))
                .background(statusColor),
        )
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text(
                bill.guestName,
                color = DreamlandOnDark,
                fontWeight = FontWeight.SemiBold,
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text("Room ${bill.roomNumber}", color = DreamlandMuted, style = MaterialTheme.typography.labelSmall)
            Text(dateFmt.format(bill.createdAt), color = DreamlandMuted, style = MaterialTheme.typography.labelSmall)
        }
        Column(horizontalAlignment = Alignment.End) {
            Text("₹${bill.totalAmount.toLong()}", color = DreamlandGold, fontWeight = FontWeight.Bold, fontSize = 15.sp)
            Box(
                Modifier
                    .clip(RoundedCornerShape(4.dp))
                    .background(statusColor.copy(alpha = 0.15f))
                    .padding(horizontal = 6.dp, vertical = 2.dp),
            ) {
                Text(bill.status, color = statusColor, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.SemiBold)
            }
        }
    }
}

// ── Bill detail panel (read-only) ─────────────────────────────────────────────

@Composable
private fun BillDetailPanel(bill: Bill, onOpenFull: (stayId: String) -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        // Guest header
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Column {
                Text(bill.guestName, style = MaterialTheme.typography.headlineSmall, color = DreamlandOnDark, fontWeight = FontWeight.Bold)
                Text("Room ${bill.roomNumber}", color = DreamlandMuted, style = MaterialTheme.typography.bodySmall)
            }
            val statusColor = when (bill.status) {
                "PAID" -> Color(0xFF4CAF50)
                "PARTIAL" -> Color(0xFFFF9800)
                else -> Color(0xFFEF5350)
            }
            Box(
                Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .background(statusColor.copy(alpha = 0.2f))
                    .padding(horizontal = 12.dp, vertical = 6.dp),
            ) {
                Text(bill.status, color = statusColor, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelMedium)
            }
        }

        // Stay info (check-in / check-out / nights)
        StayInfoCard(bill)

        // Bill items (read-only)
        if (bill.items.isNotEmpty()) {
            Text("Bill Items", style = MaterialTheme.typography.titleMedium, color = DreamlandOnDark, fontWeight = FontWeight.SemiBold)
            bill.items.forEach { item ->
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
                    }
                }
            }
        }

        // Tax & discount summary
        if (bill.taxEnabled || bill.discountValue > 0) {
            Card(
                colors = CardDefaults.cardColors(containerColor = DreamlandForestSurface),
                shape = RoundedCornerShape(10.dp),
            ) {
                Row(Modifier.fillMaxWidth().padding(12.dp)) {
                    val taxStr = if (bill.taxEnabled) "Tax ${bill.taxPercentage.toInt()}%" else "No tax"
                    val discStr = if (bill.discountValue > 0) {
                        val sym = if (bill.discountType == "PERCENT") "%" else "₹"
                        "Discount ${bill.discountValue.toInt()}$sym"
                    } else "No discount"
                    Text("$taxStr  ·  $discStr", color = DreamlandMuted, style = MaterialTheme.typography.bodySmall)
                }
            }
        }

        // Bill summary
        BillSummaryCard(bill)

        // Payments
        Text("Payments", style = MaterialTheme.typography.titleMedium, color = DreamlandOnDark, fontWeight = FontWeight.SemiBold)
        if (bill.advancePayment > 0) {
            PaymentRow(label = "Advance paid at check-in", amount = bill.advancePayment, method = "", date = null, isAdvance = true)
        }
        if (bill.transactions.isEmpty() && bill.advancePayment <= 0) {
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(52.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(DreamlandForestSurface),
                contentAlignment = Alignment.Center,
            ) {
                Text("No payments recorded.", color = DreamlandMuted, style = MaterialTheme.typography.bodySmall)
            }
        }
        bill.transactions.forEach { tx ->
            PaymentRow(label = tx.method, amount = tx.amount, method = tx.method, date = tx.createdAt)
        }

        HorizontalDivider(color = DreamlandGold.copy(alpha = 0.15f))

        // Open Full Billing Screen button
        if (bill.stayId.isNotBlank()) {
            Button(
                onClick = { onOpenFull(bill.stayId) },
                modifier = Modifier.fillMaxWidth().height(48.dp),
                shape = RoundedCornerShape(10.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Color.Transparent),
                border = androidx.compose.foundation.BorderStroke(1.dp, DreamlandGold),
            ) {
                Text("Open Full Billing Screen", color = DreamlandGold, fontWeight = FontWeight.SemiBold)
            }
        }

        Spacer(Modifier.height(8.dp))
    }
}
