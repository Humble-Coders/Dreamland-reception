package com.example.dreamland_reception.stays

import androidx.compose.runtime.LaunchedEffect
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SecondaryTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
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
import com.example.dreamland_reception.DreamlandForestElevated
import com.example.dreamland_reception.DreamlandForestSurface
import com.example.dreamland_reception.DreamlandGold
import com.example.dreamland_reception.DreamlandMuted
import com.example.dreamland_reception.DreamlandOnDark
import com.example.dreamland_reception.data.model.BillingInvoice
import com.example.dreamland_reception.data.model.Complaint
import com.example.dreamland_reception.data.model.Order
import com.example.dreamland_reception.data.model.Stay
import com.example.dreamland_reception.ui.viewmodel.StayDetailState
import com.example.dreamland_reception.ui.viewmodel.StaysListState
import com.example.dreamland_reception.ui.viewmodel.StaysViewModel
import java.text.SimpleDateFormat
import java.time.temporal.ChronoUnit
import java.util.Locale

@Composable
fun StayDetailPanel(listState: StaysListState, detailState: StayDetailState, vm: StaysViewModel) {
    val stay = listState.stays.find { it.id == listState.selectedStayId } ?: return
    var selectedTab by remember(stay.id) { mutableStateOf(0) }

    // Refresh orders and complaints whenever those tabs are opened
    LaunchedEffect(selectedTab, stay.id) {
        if (selectedTab == 1 || selectedTab == 2) vm.refreshDetail()
    }
    val fmt = SimpleDateFormat("dd MMM yyyy", Locale.getDefault())
    val nights = ChronoUnit.DAYS.between(stay.checkInActual.toInstant(), stay.expectedCheckOut.toInstant()).coerceAtLeast(1)

    Column(Modifier.fillMaxSize().padding(24.dp)) {
        // ── Guest & Room header ────────────────────────────────────────────────
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.Top,
        ) {
            Column(Modifier.weight(1f)) {
                Text(stay.guestName, style = MaterialTheme.typography.headlineMedium, color = DreamlandOnDark, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(4.dp))
                if (stay.guestPhone.isNotBlank()) {
                    Text(stay.guestPhone, color = DreamlandMuted, style = MaterialTheme.typography.bodyMedium)
                }
                Spacer(Modifier.height(6.dp))
                Text(
                    text = "Room ${stay.roomNumber} · ${stay.roomCategoryName}",
                    color = DreamlandGold,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                )
            }
            if (stay.status == "ACTIVE") {
                Button(
                    onClick = { vm.openCheckOut(stay.id) },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFEF5350)),
                    shape = RoundedCornerShape(10.dp),
                ) {
                    Text("Check-out", color = Color.White, fontWeight = FontWeight.SemiBold)
                }
            }
        }

        Spacer(Modifier.height(12.dp))

        // ── Stay info pills ────────────────────────────────────────────────────
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            InfoPill("${fmt.format(stay.checkInActual)} → ${fmt.format(stay.expectedCheckOut)}")
            InfoPill("$nights nights")
            InfoPill("${stay.adults}A${if (stay.children > 0) " + ${stay.children}C" else ""}")
            if (stay.breakfast) InfoPill("Breakfast")
            if (stay.extraBed) InfoPill("Extra Bed")
            if (stay.earlyCheckIn) InfoPill("Early CI")
            if (stay.lateCheckOut) InfoPill("Late CO")
        }

        Spacer(Modifier.height(16.dp))
        HorizontalDivider(color = DreamlandGold.copy(alpha = 0.2f))
        Spacer(Modifier.height(8.dp))

        // ── Tabs ───────────────────────────────────────────────────────────────
        SecondaryTabRow(
            selectedTabIndex = selectedTab,
            containerColor = Color.Transparent,
            contentColor = DreamlandGold,
            indicator = {
                androidx.compose.material3.TabRowDefaults.SecondaryIndicator(
                    Modifier.tabIndicatorOffset(selectedTab),
                    color = DreamlandGold,
                )
            },
        ) {
            listOf("Billing", "Orders", "Complaints").forEachIndexed { index, label ->
                Tab(
                    selected = selectedTab == index,
                    onClick = { selectedTab = index },
                    text = {
                        Text(
                            label,
                            color = if (selectedTab == index) DreamlandGold else DreamlandMuted,
                            fontWeight = if (selectedTab == index) FontWeight.SemiBold else FontWeight.Normal,
                        )
                    },
                )
            }
        }

        Spacer(Modifier.height(12.dp))

        if (detailState.isLoading) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = DreamlandGold)
            }
        } else {
            when (selectedTab) {
                0 -> BillingTab(detailState.bill)
                1 -> OrdersTab(detailState.orders, vm, stay.status == "ACTIVE")
                2 -> ComplaintsTab(detailState.complaints, vm, stay.status == "ACTIVE")
            }
        }
    }
}

// ── Billing tab ───────────────────────────────────────────────────────────────

@Composable
private fun BillingTab(bill: BillingInvoice?) {
    if (bill == null) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("No billing record found", color = DreamlandMuted, style = MaterialTheme.typography.bodyMedium)
        }
        return
    }

    LazyColumn(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = DreamlandForestElevated),
                shape = RoundedCornerShape(12.dp),
            ) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Bill Summary", style = MaterialTheme.typography.titleMedium, color = DreamlandGold, fontWeight = FontWeight.SemiBold)
                    HorizontalDivider(color = DreamlandGold.copy(alpha = 0.2f))
                    BillRow("Room Charges", bill.roomCharges)
                    if (bill.serviceCharges > 0) BillRow("Service Charges", bill.serviceCharges)
                    if (bill.earlyCheckInCharge > 0) BillRow("Early Check-in", bill.earlyCheckInCharge)
                    if (bill.lateCheckOutCharge > 0) BillRow("Late Check-out", bill.lateCheckOutCharge)
                    if (bill.discount > 0) BillRow("Discount", -bill.discount)
                    HorizontalDivider(color = DreamlandMuted.copy(alpha = 0.3f))
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("Total", color = DreamlandOnDark, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                        Text("₹${bill.totalAmount.toLong()}", color = DreamlandGold, fontWeight = FontWeight.Bold, fontSize = 18.sp)
                    }
                    BillRow("Amount Paid", bill.amountPaid)
                    val pending = bill.totalAmount - bill.amountPaid
                    if (pending > 0) {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("Pending", color = Color(0xFFEF5350), fontWeight = FontWeight.SemiBold)
                            Text("₹${pending.toLong()}", color = Color(0xFFEF5350), fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }
        item {
            StatusChip(
                text = bill.status.uppercase(),
                color = when (bill.status.uppercase()) {
                    "PAID" -> Color(0xFF4CAF50)
                    "PARTIAL" -> Color(0xFFFFC107)
                    else -> Color(0xFFEF5350)
                },
            )
        }
    }
}

@Composable
private fun BillRow(label: String, amount: Double) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, color = DreamlandMuted, style = MaterialTheme.typography.bodySmall)
        Text(
            text = if (amount < 0) "-₹${(-amount).toLong()}" else "₹${amount.toLong()}",
            color = DreamlandOnDark,
            style = MaterialTheme.typography.bodySmall,
        )
    }
}

// ── Orders tab ────────────────────────────────────────────────────────────────

@Composable
private fun OrdersTab(orders: List<Order>, vm: StaysViewModel, canAdd: Boolean) {
    LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        if (canAdd) {
            item {
                androidx.compose.material3.OutlinedButton(
                    onClick = { vm.openAddOrder() },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(10.dp),
                    border = androidx.compose.foundation.BorderStroke(1.dp, DreamlandGold.copy(alpha = 0.6f)),
                ) {
                    Text("+ Add Order", color = DreamlandGold, fontWeight = FontWeight.SemiBold)
                }
            }
        }
        if (orders.isEmpty()) {
            item {
                Box(Modifier.fillMaxWidth().height(80.dp), contentAlignment = Alignment.Center) {
                    Text("No orders for this stay", color = DreamlandMuted, style = MaterialTheme.typography.bodyMedium)
                }
            }
        }
        items(orders) { order ->
            Card(
                colors = CardDefaults.cardColors(containerColor = DreamlandForestElevated),
                shape = RoundedCornerShape(10.dp),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(
                            order.type.replace("_", " ").lowercase().replaceFirstChar { it.uppercase() },
                            color = DreamlandOnDark,
                            fontWeight = FontWeight.SemiBold,
                            style = MaterialTheme.typography.bodyMedium,
                        )
                        Text(
                            order.items.joinToString(", ") { "${it.name} ×${it.quantity}" },
                            color = DreamlandMuted,
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                    Spacer(Modifier.width(8.dp))
                    Column(horizontalAlignment = Alignment.End) {
                        Text("₹${order.totalAmount.toLong()}", color = DreamlandGold, fontWeight = FontWeight.SemiBold)
                        StatusChip(
                            text = order.status,
                            color = when (order.status) {
                                "COMPLETED" -> Color(0xFF4CAF50)
                                "ASSIGNED" -> Color(0xFFFFC107)
                                else -> Color(0xFF90A4AE)
                            },
                        )
                    }
                }
            }
        }
    }
}

// ── Complaints tab ────────────────────────────────────────────────────────────

@Composable
private fun ComplaintsTab(complaints: List<Complaint>, vm: StaysViewModel, canAdd: Boolean) {
    LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        if (canAdd) {
            item {
                androidx.compose.material3.OutlinedButton(
                    onClick = { vm.openAddComplaint() },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(10.dp),
                    border = androidx.compose.foundation.BorderStroke(1.dp, DreamlandGold.copy(alpha = 0.6f)),
                ) {
                    Text("+ Add Complaint", color = DreamlandGold, fontWeight = FontWeight.SemiBold)
                }
            }
        }
        if (complaints.isEmpty()) {
            item {
                Box(Modifier.fillMaxWidth().height(80.dp), contentAlignment = Alignment.Center) {
                    Text("No complaints for this stay", color = DreamlandMuted, style = MaterialTheme.typography.bodyMedium)
                }
            }
        }
        items(complaints) { complaint ->
            Card(
                colors = CardDefaults.cardColors(containerColor = DreamlandForestElevated),
                shape = RoundedCornerShape(10.dp),
            ) {
                Column(Modifier.fillMaxWidth().padding(12.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            complaint.type.replace("_", " ").lowercase().replaceFirstChar { it.uppercase() },
                            color = DreamlandOnDark,
                            fontWeight = FontWeight.SemiBold,
                            style = MaterialTheme.typography.bodyMedium,
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            StatusChip(
                                text = complaint.priority,
                                color = when (complaint.priority) {
                                    "HIGH" -> Color(0xFFEF5350)
                                    "MEDIUM" -> Color(0xFFFFC107)
                                    else -> Color(0xFF90A4AE)
                                },
                            )
                            StatusChip(
                                text = complaint.status,
                                color = when (complaint.status) {
                                    "COMPLETED" -> Color(0xFF4CAF50)
                                    "ASSIGNED" -> Color(0xFFFFC107)
                                    else -> Color(0xFFEF5350)
                                },
                            )
                        }
                    }
                    if (complaint.description.isNotBlank()) {
                        Spacer(Modifier.height(4.dp))
                        Text(complaint.description, color = DreamlandMuted, style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
        }
    }
}

// ── Shared composables ────────────────────────────────────────────────────────

@Composable
fun StayDetailPlaceholder() {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text("SELECT A STAY", style = MaterialTheme.typography.labelLarge, color = DreamlandMuted.copy(alpha = 0.5f), letterSpacing = 2.sp)
            Spacer(Modifier.height(8.dp))
            Text("Click a stay card to view details", color = DreamlandMuted.copy(alpha = 0.4f), style = MaterialTheme.typography.bodyMedium)
        }
    }
}

@Composable
private fun InfoPill(text: String) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(20.dp))
            .background(DreamlandForestElevated)
            .padding(horizontal = 10.dp, vertical = 4.dp),
    ) {
        Text(text, color = DreamlandMuted, style = MaterialTheme.typography.bodySmall)
    }
}

@Composable
private fun StatusChip(text: String, color: Color) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(20.dp))
            .background(color.copy(alpha = 0.15f))
            .padding(horizontal = 8.dp, vertical = 3.dp),
    ) {
        Text(text, color = color, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.SemiBold)
    }
}
