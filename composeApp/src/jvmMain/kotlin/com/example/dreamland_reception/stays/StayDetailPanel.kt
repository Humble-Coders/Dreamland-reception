package com.example.dreamland_reception.stays

import androidx.compose.runtime.LaunchedEffect
import androidx.compose.foundation.background
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.clickable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.Icon
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
import com.example.dreamland_reception.DreamlandForest
import com.example.dreamland_reception.DreamlandForestElevated
import com.example.dreamland_reception.DreamlandForestSurface
import com.example.dreamland_reception.DreamlandGold
import com.example.dreamland_reception.DreamlandMuted
import com.example.dreamland_reception.DreamlandOnDark
import com.example.dreamland_reception.data.model.Bill
import com.example.dreamland_reception.data.model.GuestRecord
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
fun StayDetailPanel(listState: StaysListState, detailState: StayDetailState, vm: StaysViewModel, onNavigateToOrder: (String) -> Unit = {}) {
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
                val categoryName = stay.roomCategoryName.ifBlank {
                    listState.categoryNames[stay.roomCategoryId] ?: ""
                }
                Text(
                    text = buildString {
                        append("Room ${stay.roomNumber}")
                        if (categoryName.isNotBlank()) append(" · $categoryName")
                    },
                    color = DreamlandGold,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                )
            }
            if (stay.status == "ACTIVE") {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    androidx.compose.material3.OutlinedButton(
                        onClick = { vm.openExtendStay(stay.id) },
                        shape = RoundedCornerShape(10.dp),
                        border = androidx.compose.foundation.BorderStroke(1.dp, DreamlandGold.copy(alpha = 0.7f)),
                    ) {
                        Text("Extend Stay", color = DreamlandGold, fontWeight = FontWeight.SemiBold)
                    }
                    androidx.compose.material3.OutlinedButton(
                        onClick = { vm.openChangeRoom(stay.id) },
                        shape = RoundedCornerShape(10.dp),
                        border = androidx.compose.foundation.BorderStroke(1.dp, DreamlandGold.copy(alpha = 0.7f)),
                    ) {
                        Text("Change Room", color = DreamlandGold, fontWeight = FontWeight.SemiBold)
                    }
                    Button(
                        onClick = { vm.openCheckOut(stay.id) },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFEF5350)),
                        shape = RoundedCornerShape(10.dp),
                    ) {
                        Text("Check-out", color = Color.White, fontWeight = FontWeight.SemiBold)
                    }
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

        // ── Guest details ──────────────────────────────────────────────────────
        if (stay.guests.isNotEmpty()) {
            Spacer(Modifier.height(14.dp))
            GuestsSection(guests = stay.guests, totalAdults = stay.adults)
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
                1 -> OrdersTab(detailState.orders, vm, stay.status == "ACTIVE", onNavigateToOrder)
                2 -> ComplaintsTab(detailState.complaints, vm, stay.status == "ACTIVE")
            }
        }
    }
}

// ── Guest details section (collapsible) ──────────────────────────────────────

@Composable
private fun GuestsSection(guests: List<GuestRecord>, totalAdults: Int) {
    var expanded by remember(guests) { mutableStateOf(true) }

    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        // Header row — click anywhere to toggle
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { expanded = !expanded }
                .padding(vertical = 2.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "GUESTS",
                    style = MaterialTheme.typography.labelSmall,
                    color = DreamlandMuted,
                    letterSpacing = 1.5.sp,
                )
                // Compact summary shown when collapsed
                if (!expanded) {
                    val primary = guests.firstOrNull()
                    val summaryText = buildString {
                        if (primary?.name?.isNotBlank() == true) append(primary.name)
                        if (guests.size > 1) append(" +${guests.size - 1} more")
                    }
                    if (summaryText.isNotBlank()) {
                        Text(
                            summaryText,
                            style = MaterialTheme.typography.bodySmall,
                            color = DreamlandOnDark,
                        )
                    }
                }
            }
            Icon(
                if (expanded) Icons.Filled.KeyboardArrowUp else Icons.Filled.KeyboardArrowDown,
                contentDescription = if (expanded) "Collapse guests" else "Expand guests",
                tint = DreamlandMuted,
                modifier = Modifier.size(18.dp),
            )
        }

        if (expanded) {
            guests.forEachIndexed { index, guest ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .background(DreamlandForestElevated)
                        .padding(horizontal = 14.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Box(
                        Modifier
                            .size(32.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(if (index == 0) DreamlandGold.copy(alpha = 0.15f) else DreamlandMuted.copy(alpha = 0.12f)),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            Icons.Filled.Person,
                            contentDescription = null,
                            tint = if (index == 0) DreamlandGold else DreamlandMuted,
                            modifier = Modifier.size(18.dp),
                        )
                    }
                    Column(Modifier.weight(1f)) {
                        val displayName = guest.name.ifBlank { if (index == 0) "Primary Guest" else "Guest ${index + 1}" }
                        Text(
                            displayName,
                            color = DreamlandOnDark,
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = if (index == 0) FontWeight.SemiBold else FontWeight.Normal,
                        )
                        if (guest.phone.isNotBlank()) {
                            Text(guest.phone, color = DreamlandMuted, style = MaterialTheme.typography.bodySmall)
                        }
                        if (index == 0) {
                            Text(
                                "Primary Guest",
                                color = DreamlandGold.copy(alpha = 0.7f),
                                style = MaterialTheme.typography.labelSmall,
                            )
                        }
                    }
                    if (guest.idProofVerified) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                        ) {
                            Icon(Icons.Filled.CheckCircle, contentDescription = "ID Verified", tint = Color(0xFF4CAF50), modifier = Modifier.size(14.dp))
                            Text("ID Verified", color = Color(0xFF4CAF50), style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Medium)
                        }
                    } else {
                        Text("ID Pending", color = DreamlandMuted.copy(alpha = 0.6f), style = MaterialTheme.typography.labelSmall)
                    }
                }
            }
        }
    }
}

// ── Billing tab ───────────────────────────────────────────────────────────────

@Composable
private fun BillingTab(bill: Bill?) {
    if (bill == null) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("Bill generated at checkout", color = DreamlandMuted, style = MaterialTheme.typography.bodyMedium)
        }
        return
    }

    val roomCharges = bill.items.filter { it.type == "ROOM" }.sumOf { it.total }
    val serviceCharges = bill.items.filter { it.type == "SERVICE" }.sumOf { it.total }
    // orderCharges includes tax since order tax is shown inside the orders section, not separately
    val orderCharges = bill.items.filter { it.type == "ORDER" }.sumOf { it.total * (1 + it.taxPercentage / 100.0) }
    val customCharges = bill.items.filter { it.type == "CUSTOM" }.sumOf { it.total }
    val amountPaid = bill.totalPaid + bill.advancePayment

    LazyColumn(
        contentPadding = PaddingValues(horizontal = 100.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = DreamlandForestElevated),
                shape = RoundedCornerShape(12.dp),
            ) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Bill Summary", style = MaterialTheme.typography.titleMedium, color = DreamlandGold, fontWeight = FontWeight.SemiBold)
                    HorizontalDivider(color = DreamlandGold.copy(alpha = 0.2f))
                    if (roomCharges > 0) BillRow("Room Charges", roomCharges)
                    if (serviceCharges > 0) BillRow("Service Charges", serviceCharges)
                    val orderItems = bill.items.filter { it.type == "ORDER" }
                    if (orderItems.isNotEmpty()) {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("Orders", color = DreamlandMuted, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Medium)
                            Text("₹${"%.2f".format(orderCharges)}", color = DreamlandOnDark, style = MaterialTheme.typography.bodySmall)
                        }
                        orderItems.forEach { item ->
                            Row(Modifier.fillMaxWidth().padding(start = 14.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text("· ${item.name}", color = DreamlandMuted.copy(alpha = 0.7f), style = MaterialTheme.typography.labelSmall, modifier = Modifier.weight(1f), overflow = TextOverflow.Ellipsis, maxLines = 1)
                                Text("₹${"%.2f".format(item.total)}", color = DreamlandMuted.copy(alpha = 0.8f), style = MaterialTheme.typography.labelSmall)
                            }
                            val taxAmt = item.total * item.taxPercentage / 100.0
                            if (taxAmt > 0.005) {
                                val rateLabel = if (item.taxPercentage % 1.0 == 0.0) "${item.taxPercentage.toInt()}" else "%.1f".format(item.taxPercentage)
                                Row(Modifier.fillMaxWidth().padding(start = 22.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                                    Text("Tax ($rateLabel%)", color = DreamlandMuted.copy(alpha = 0.5f), style = MaterialTheme.typography.labelSmall)
                                    Text("₹${"%.2f".format(taxAmt)}", color = DreamlandMuted.copy(alpha = 0.5f), style = MaterialTheme.typography.labelSmall)
                                }
                            }
                        }
                    }
                    if (customCharges > 0) BillRow("Other", customCharges)
                    // Show per-rate tax rows (same logic as BillSummaryCard)
                    // Exclude ORDER items from tax rows — their tax is already included in orderCharges
                    val taxByRate = bill.items.filter { it.taxPercentage > 0 && it.type != "ORDER" }.groupBy { it.taxPercentage }
                    if (taxByRate.isNotEmpty()) {
                        taxByRate.forEach { (rate, items) ->
                            val rateAmt = items.sumOf { it.total * rate / 100.0 }
                            if (rateAmt > 0) BillRow("Tax (${rate.toInt()}%)", rateAmt)
                        }
                    } else if (bill.taxAmount > 0) {
                        BillRow("Tax (${bill.taxPercentage.toInt()}%)", bill.taxAmount)
                    }
                    if (bill.discountAmount > 0) BillRow("Discount", -bill.discountAmount)
                    HorizontalDivider(color = DreamlandMuted.copy(alpha = 0.3f))
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("Total", color = DreamlandOnDark, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                        Text("₹${"%.2f".format(bill.totalAmount)}", color = DreamlandGold, fontWeight = FontWeight.Bold, fontSize = 18.sp)
                    }
                    BillRow("Amount Paid", amountPaid)
                    if (bill.pendingAmount > 0) {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("Pending", color = Color(0xFFEF5350), fontWeight = FontWeight.SemiBold)
                            Text("₹${"%.2f".format(bill.pendingAmount)}", color = Color(0xFFEF5350), fontWeight = FontWeight.Bold)
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
            text = if (amount < 0) "-₹${"%.2f".format(-amount)}" else "₹${"%.2f".format(amount)}",
            color = DreamlandOnDark,
            style = MaterialTheme.typography.bodySmall,
        )
    }
}

// ── Orders tab ────────────────────────────────────────────────────────────────

@Composable
private fun OrdersTab(orders: List<Order>, vm: StaysViewModel, canAdd: Boolean, onNavigateToOrder: (String) -> Unit = {}) {
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
                onClick = { if (order.id.isNotBlank()) onNavigateToOrder(order.id) },
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
                        Text("₹${"%.2f".format(order.totalAmount)}", color = DreamlandGold, fontWeight = FontWeight.SemiBold)
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
