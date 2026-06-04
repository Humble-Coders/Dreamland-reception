package com.example.dreamland_reception

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.SecondaryTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRowDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.VerticalDivider
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.PopupProperties
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.dreamland_reception.data.model.Order
import com.example.dreamland_reception.orders.AssignStaffDialog
import com.example.dreamland_reception.orders.CreateOrderDialog
import com.example.dreamland_reception.orders.MarkOrderDoneDialog
import com.example.dreamland_reception.ui.viewmodel.OrdersViewModel
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit

@Composable
fun OrdersScreen(
    vm: OrdersViewModel = DreamlandAppInitializer.getOrdersViewModel(),
    initialOrderId: String = "",
) {
    val screenState by vm.screenState.collectAsStateWithLifecycle()
    val createDialog by vm.createOrderDialog.collectAsStateWithLifecycle()
    val assignDialog by vm.assignStaffDialog.collectAsStateWithLifecycle()
    val vendors by vm.vendors.collectAsStateWithLifecycle()

    // Pre-select order when navigated from stays screen
    LaunchedEffect(initialOrderId) {
        if (initialOrderId.isNotBlank()) vm.selectOrder(initialOrderId)
    }

    var confirmMarkDoneId by remember { mutableStateOf<String?>(null) }
    var confirmDeleteId by remember { mutableStateOf<String?>(null) }

    val staysVm = DreamlandAppInitializer.getStaysViewModel()
    val staysState by staysVm.listState.collectAsStateWithLifecycle()
    val stayPhoneMap = staysState.stays.associate { it.id to it.guestPhone }

    val tabColors = listOf(Color(0xFFFFC107), Color(0xFF4CAF50), DreamlandMuted)

    Column(Modifier.fillMaxSize().background(DreamlandForest)) {
        // ── Top bar ───────────────────────────────────────────────────────────
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Column(Modifier.width(140.dp)) {
                Text("REQUESTS", style = MaterialTheme.typography.labelSmall, color = DreamlandGold, letterSpacing = 2.sp)
                Text("Orders", style = MaterialTheme.typography.titleLarge, color = DreamlandOnDark, fontWeight = FontWeight.Bold)
            }
            OutlinedTextField(
                value = screenState.searchQuery,
                onValueChange = { vm.onSearch(it) },
                placeholder = { Text("Search guest, room, item…", color = DreamlandMuted, fontSize = 12.sp) },
                singleLine = true,
                modifier = Modifier.weight(1f),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedTextColor = DreamlandOnDark, unfocusedTextColor = DreamlandOnDark,
                    focusedBorderColor = DreamlandGold, unfocusedBorderColor = DreamlandMuted.copy(alpha = 0.4f),
                    cursorColor = DreamlandGold,
                ),
            )
            FilterDropdown(
                label = "Room", selected = screenState.roomFilter,
                options = listOf("" to "All Rooms") + screenState.uniqueRooms.map { it to "Room $it" },
                onSelect = { vm.onRoomFilter(it) }, modifier = Modifier.width(140.dp),
            )
            FilterDropdown(
                label = "Staff", selected = screenState.staffFilter,
                options = listOf("" to "All Staff") + screenState.uniqueStaff.map { it.first to it.second },
                onSelect = { vm.onStaffFilter(it) }, modifier = Modifier.width(140.dp),
            )
            Button(
                onClick = { vm.openCreateOrder() },
                colors = ButtonDefaults.buttonColors(containerColor = DreamlandGold),
                shape = RoundedCornerShape(8.dp),
                modifier = Modifier.height(36.dp),
                contentPadding = PaddingValues(horizontal = 14.dp),
            ) {
                Text("+ New", color = Color(0xFF0D1F17), fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
            }
        }
        HorizontalDivider(color = DreamlandGold.copy(alpha = 0.15f))

        // ── Tabs (ASSIGNED removed from UI; vm tab 0=NEW, 2=COMPLETED) ──────────
        val visibleTabs = listOf(0 to ("NEW" to screenState.newOrders.size), 2 to ("COMPLETED" to screenState.completedOrders.size))
        val uiTabIndex = visibleTabs.indexOfFirst { it.first == screenState.selectedTab }.coerceAtLeast(0)
        val uiTabColors = listOf(tabColors[0], tabColors[2])
        SecondaryTabRow(
            selectedTabIndex = uiTabIndex,
            containerColor = DreamlandForestSurface,
            contentColor = DreamlandGold,
            indicator = { TabRowDefaults.SecondaryIndicator(Modifier.tabIndicatorOffset(uiTabIndex), color = uiTabColors[uiTabIndex]) },
            modifier = Modifier.fillMaxWidth(),
        ) {
            visibleTabs.forEachIndexed { uiIdx, (vmIdx, labelCount) ->
                val (label, count) = labelCount
                val color = uiTabColors[uiIdx]
                Tab(selected = uiTabIndex == uiIdx, onClick = { vm.onTabSelected(vmIdx) }) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.padding(vertical = 10.dp)) {
                        Text(label, color = if (uiTabIndex == uiIdx) color else DreamlandMuted, fontWeight = if (uiTabIndex == uiIdx) FontWeight.Bold else FontWeight.Normal, style = MaterialTheme.typography.labelMedium)
                        if (count > 0) {
                            Box(Modifier.clip(RoundedCornerShape(8.dp)).background(color.copy(alpha = 0.2f)).padding(horizontal = 6.dp, vertical = 2.dp)) {
                                Text("$count", color = color, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }
        }

        // ── Table header (shared weights with OrderTableRow → perfect alignment) ─
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Spacer(Modifier.width(W_STATUS))            // status-dot column (filled in rows)
            OrderHeaderCell("ROOM", W_ROOM)
            OrderHeaderCell("GUEST", W_GUEST)
            OrderHeaderCell("PHONE", W_PHONE)
            OrderHeaderCell("ITEMS & PRICE", W_ITEMS)
            OrderHeaderCell("TOTAL", W_TOTAL)
            OrderHeaderCell("ACTIONS", W_ACTIONS)
        }
        HorizontalDivider(color = DreamlandGold.copy(alpha = 0.18f))

        // ── Table content ─────────────────────────────────────────────────────
        when {
            screenState.isLoading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = DreamlandGold)
            }
            screenState.error != null -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                ErrorText(screenState.error!!)
            }
            screenState.filtered.isEmpty() -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("No orders", color = DreamlandMuted, style = MaterialTheme.typography.bodyMedium)
            }
            else -> LazyColumn(Modifier.fillMaxSize()) {
                items(screenState.filtered, key = { it.id }) { order ->
                    OrderTableRow(
                        order = order,
                        stayPhoneMap = stayPhoneMap,
                        onMarkDone = { confirmMarkDoneId = order.id },
                        onDelete = { confirmDeleteId = order.id },
                    )
                    HorizontalDivider(color = DreamlandMuted.copy(alpha = 0.08f))
                }
            }
        }
    }

    // ── Mark Done — vendor & payment ───────────────────────────────────────────
    confirmMarkDoneId?.let { orderId ->
        val order = screenState.orders.find { it.id == orderId }
        if (order != null) {
            MarkOrderDoneDialog(
                order = order,
                vendors = vendors,
                onAddVendor = { name, phone, onCreated -> vm.addVendor(name, phone, onCreated = onCreated) },
                onLoadBalance = { externalId -> vm.vendorBalance(externalId) },
                onConfirm = { vendor, cost, cash, bank ->
                    confirmMarkDoneId = null
                    vm.markOrderDoneWithVendor(orderId, vendor.id, vendor.name, cost, cash, bank)
                },
                onSkip = { confirmMarkDoneId = null; vm.markOrderDoneInHouse(orderId) },
                onDismiss = { confirmMarkDoneId = null },
            )
        } else {
            confirmMarkDoneId = null
        }
    }

    // ── Delete confirmation ────────────────────────────────────────────────────
    confirmDeleteId?.let { orderId ->
        val order = screenState.orders.find { it.id == orderId }
        if (order != null) {
            AlertDialog(
                onDismissRequest = { confirmDeleteId = null },
                containerColor = DreamlandForestElevated,
                title = { Text("Delete Order?", color = DreamlandOnDark, fontWeight = FontWeight.SemiBold) },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text("This permanently deletes the order.", color = DreamlandMuted, style = MaterialTheme.typography.bodySmall)
                        Text("Room ${order.roomNumber}  ·  ${order.guestName}", color = DreamlandOnDark, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                        order.items.forEach { item -> Text("• ${item.name} ×${item.quantity}", color = DreamlandMuted, style = MaterialTheme.typography.bodySmall) }
                    }
                },
                confirmButton = {
                    Button(
                        onClick = { confirmDeleteId = null; vm.deleteOrder(orderId) },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFEF5350)),
                        shape = RoundedCornerShape(8.dp),
                    ) { Text("Delete", color = Color.White, fontWeight = FontWeight.SemiBold) }
                },
                dismissButton = { TextButton(onClick = { confirmDeleteId = null }) { Text("Cancel", color = DreamlandMuted) } },
            )
        }
    }

    if (createDialog.isOpen) CreateOrderDialog(state = createDialog, vm = vm)
    if (assignDialog.isOpen) AssignStaffDialog(state = assignDialog, vm = vm)
}

// ── Table ───────────────────────────────────────────────────────────────────

// Shared column metrics so the header and rows line up (matches the Logs table).
private val W_STATUS = 16.dp        // fixed status-dot column
private const val W_ROOM = 0.7f
private const val W_GUEST = 1.3f
private const val W_PHONE = 1.2f
private const val W_ITEMS = 2.6f
private const val W_TOTAL = 0.9f
private const val W_ACTIONS = 1.4f

@Composable
private fun androidx.compose.foundation.layout.RowScope.OrderHeaderCell(text: String, weight: Float) {
    Text(
        text,
        modifier = Modifier.weight(weight),
        style = MaterialTheme.typography.labelSmall,
        color = DreamlandGold,
        letterSpacing = 1.sp,
        fontWeight = FontWeight.SemiBold,
    )
}

@Composable
private fun OrderTableRow(
    order: Order,
    stayPhoneMap: Map<String, String> = emptyMap(),
    onMarkDone: () -> Unit,
    onDelete: () -> Unit,
) {
    val statusColor = when (order.status) { "NEW" -> Color(0xFFFFC107); "ASSIGNED" -> Color(0xFF4CAF50); else -> DreamlandMuted }
    val isDone = order.status == "COMPLETED"
    val phone = stayPhoneMap[order.stayId]?.ifBlank { null } ?: order.guestPhone.ifBlank { "—" }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp, vertical = 14.dp),
        verticalAlignment = Alignment.Top,
    ) {
        // Status dot — fixed-width column, aligned under the header's spacer.
        Box(Modifier.width(W_STATUS).padding(top = 5.dp)) {
            Box(Modifier.size(8.dp).clip(CircleShape).background(statusColor))
        }
        Text(
            order.roomNumber.ifBlank { "—" },
            style = MaterialTheme.typography.bodyMedium,
            color = DreamlandOnDark,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.weight(W_ROOM).padding(end = 8.dp),
        )
        Text(
            order.guestName.ifBlank { "—" },
            style = MaterialTheme.typography.bodyMedium,
            color = DreamlandOnDark,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(W_GUEST).padding(end = 8.dp),
        )
        Text(
            phone,
            style = MaterialTheme.typography.bodySmall,
            color = DreamlandMuted,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(W_PHONE).padding(end = 8.dp, top = 2.dp),
        )
        // Items & price — name fills, price right-aligned so prices line up down the column.
        Column(Modifier.weight(W_ITEMS).padding(end = 12.dp)) {
            order.items.forEach { item ->
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(
                        "${item.name} ×${item.quantity}",
                        style = MaterialTheme.typography.bodySmall,
                        color = DreamlandMuted,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f),
                    )
                    Text(
                        "₹${"%.2f".format(item.subtotal)}",
                        style = MaterialTheme.typography.bodySmall,
                        color = DreamlandOnDark,
                    )
                }
                if (item.taxAmount > 0.005) {
                    val rateLabel = if (item.taxPercentage % 1.0 == 0.0) "${item.taxPercentage.toInt()}%" else "${"%.1f".format(item.taxPercentage)}%"
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("Tax $rateLabel", style = MaterialTheme.typography.labelSmall, color = DreamlandMuted.copy(alpha = 0.55f), modifier = Modifier.weight(1f))
                        Text("+₹${"%.2f".format(item.taxAmount)}", style = MaterialTheme.typography.labelSmall, color = DreamlandMuted.copy(alpha = 0.55f))
                    }
                }
            }
        }
        Text(
            if (order.totalAmount > 0) "₹${"%.2f".format(order.totalAmount)}" else "—",
            style = MaterialTheme.typography.bodySmall,
            color = DreamlandGold,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.weight(W_TOTAL).padding(top = 1.dp),
        )
        // Actions — weighted column matching the header's ACTIONS cell.
        Row(
            modifier = Modifier.weight(W_ACTIONS),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            OutlinedButton(
                onClick = onMarkDone,
                enabled = !isDone,
                modifier = Modifier.height(30.dp),
                shape = RoundedCornerShape(6.dp),
                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 0.dp),
                border = BorderStroke(1.dp, if (isDone) DreamlandMuted.copy(alpha = 0.3f) else Color(0xFF4CAF50).copy(alpha = 0.7f)),
                colors = ButtonDefaults.outlinedButtonColors(
                    contentColor = Color(0xFF4CAF50),
                    disabledContentColor = DreamlandMuted.copy(alpha = 0.4f),
                ),
            ) {
                Text(
                    if (isDone) "Done" else "Mark Done",
                    fontSize = 10.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = if (isDone) DreamlandMuted.copy(alpha = 0.4f) else Color(0xFF4CAF50),
                )
            }
            IconButton(onClick = onDelete, modifier = Modifier.size(30.dp)) {
                Icon(Icons.Filled.Delete, contentDescription = "Delete", tint = Color(0xFFEF5350).copy(alpha = 0.7f), modifier = Modifier.size(14.dp))
            }
        }
    }
}

/* ── OLD two-panel composables (kept for reference, no longer rendered) ──────

@Composable
private fun OrderListContent(
    isLoading: Boolean,
    error: String?,
    filtered: List<Order>,
    selectedId: String?,
    vm: OrdersViewModel,
) {
    when {
        isLoading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator(color = DreamlandGold) }
        error != null -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { ErrorText(error) }
        filtered.isEmpty() -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("No orders", color = DreamlandMuted, style = MaterialTheme.typography.bodyMedium)
        }
        else -> LazyColumn(modifier = Modifier.fillMaxSize().padding(10.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            items(filtered, key = { it.id }) { order ->
                OrderListItem(order = order, isSelected = order.id == selectedId, onClick = { vm.selectOrder(if (order.id == selectedId) null else order.id) })
            }
        }
    }
}

@Composable
private fun OrderListItem(order: Order, isSelected: Boolean, onClick: () -> Unit) {
    val accentColor = when (order.status) { "NEW" -> Color(0xFFFFC107); "ASSIGNED" -> Color(0xFF4CAF50); else -> DreamlandMuted }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(if (isSelected) DreamlandForestElevated else Color.Transparent)
            .border(1.dp, if (isSelected) DreamlandGold else DreamlandMuted.copy(alpha = 0.18f), RoundedCornerShape(8.dp))
            .clickable(onClick = onClick),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.width(4.dp).fillMaxHeight().background(accentColor, RoundedCornerShape(topStart = 8.dp, bottomStart = 8.dp)))
        Column(Modifier.weight(1f).padding(horizontal = 10.dp, vertical = 9.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text("Room ${order.roomNumber}", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold, color = DreamlandOnDark)
                OrderTypeBadge(order.type)
            }
            Text(order.guestName, color = DreamlandMuted, style = MaterialTheme.typography.labelSmall)
            Spacer(Modifier.height(4.dp))
            val preview = order.items.take(1).joinToString { "${it.name} ×${it.quantity}" }.let { if (order.items.size > 1) "$it +${order.items.size - 1}" else it }
            if (preview.isNotBlank()) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(preview, color = DreamlandOnDark, style = MaterialTheme.typography.labelSmall, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                    if (order.totalAmount > 0) {
                        val taxPct = if (order.subtotalAmount > 0 && order.totalTaxAmount > 0)
                            order.totalTaxAmount / order.subtotalAmount * 100.0 else 0.0
                        val taxStr = if (taxPct > 0) {
                            val label = if (taxPct % 1.0 == 0.0) "${taxPct.toInt()}%" else "${"%.2f".format(taxPct)}%"
                            " (+$label)"
                        } else ""
                        Text("₹${"%.2f".format(order.totalAmount)}$taxStr", color = DreamlandGold, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                    }
                }
            }
            if (order.assignedToName.isNotBlank()) {
                Spacer(Modifier.height(2.dp))
                Text("→ ${order.assignedToName}", color = Color(0xFF4CAF50), style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}

// ── Right panel: detail ───────────────────────────────────────────────────────

@Composable
private fun OrderDetailPanel(order: Order, vm: OrdersViewModel) {
    var showConfirm by remember(order.id) { mutableStateOf(false) }
    var showDeleteConfirm by remember(order.id) { mutableStateOf(false) }
    val accentColor = when (order.status) { "NEW" -> Color(0xFFFFC107); "ASSIGNED" -> Color(0xFF4CAF50); else -> DreamlandMuted }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(28.dp)) {
        // Header
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.Top) {
            Column(Modifier.weight(1f)) {
                Text("Room ${order.roomNumber}", style = MaterialTheme.typography.headlineMedium, color = DreamlandOnDark, fontWeight = FontWeight.Bold)
                Text(order.guestName, color = DreamlandMuted, style = MaterialTheme.typography.bodyMedium)
            }
            Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(6.dp)) {
                OrderTypeBadge(order.type, large = true)
                Box(
                    modifier = Modifier.clip(RoundedCornerShape(6.dp)).background(accentColor.copy(alpha = 0.14f)).border(1.dp, accentColor.copy(alpha = 0.4f), RoundedCornerShape(6.dp)).padding(horizontal = 10.dp, vertical = 4.dp),
                ) {
                    Text(order.status, color = accentColor, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold)
                }
            }
        }

        Spacer(Modifier.height(20.dp))
        HorizontalDivider(color = DreamlandGold.copy(alpha = 0.12f))
        Spacer(Modifier.height(16.dp))

        // Items
        Text("ORDER ITEMS", style = MaterialTheme.typography.labelMedium, color = DreamlandGold, letterSpacing = 1.5.sp)
        Spacer(Modifier.height(10.dp))
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            order.items.forEach { item ->
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("${item.name} ×${item.quantity}", color = DreamlandOnDark, style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f))
                    if (item.total > 0) Text("₹${"%.2f".format(item.total)}", color = DreamlandMuted, style = MaterialTheme.typography.bodySmall)
                }
                if (item.taxAmount > 0) {
                    Row(Modifier.fillMaxWidth().padding(start = 12.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                        val rateLabel = if (item.taxPercentage % 1.0 == 0.0) "${item.taxPercentage.toInt()}%"
                                        else "${"%.2f".format(item.taxPercentage)}%"
                        Text("Tax $rateLabel", color = DreamlandMuted.copy(alpha = 0.6f), style = MaterialTheme.typography.labelSmall)
                        Text("+₹${"%.2f".format(item.taxAmount)}", color = DreamlandMuted.copy(alpha = 0.6f), style = MaterialTheme.typography.labelSmall)
                    }
                }
            }
        }

        if (order.totalAmount > 0) {
            Spacer(Modifier.height(8.dp))
            HorizontalDivider(color = DreamlandGold.copy(alpha = 0.08f))
            Spacer(Modifier.height(6.dp))
            if (order.totalTaxAmount > 0) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("Subtotal", color = DreamlandMuted, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.SemiBold)
                    Text("₹${"%.2f".format(order.subtotalAmount)}", color = DreamlandMuted, style = MaterialTheme.typography.bodySmall)
                }
                val effectiveTaxPct = if (order.subtotalAmount > 0) order.totalTaxAmount / order.subtotalAmount * 100.0 else 0.0
                val taxLabel = if (effectiveTaxPct % 1.0 == 0.0) "Tax (${effectiveTaxPct.toInt()}%)" else "Tax (${"%.2f".format(effectiveTaxPct)}%)"
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(taxLabel, color = DreamlandMuted, style = MaterialTheme.typography.bodySmall)
                    Text("+₹${"%.2f".format(order.totalTaxAmount)}", color = DreamlandMuted, style = MaterialTheme.typography.bodySmall)
                }
                Spacer(Modifier.height(4.dp))
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("Total", color = DreamlandMuted, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.SemiBold)
                Text("₹${"%.2f".format(order.totalAmount)}", color = DreamlandGold, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodyMedium)
            }
        }

        Spacer(Modifier.height(16.dp))
        HorizontalDivider(color = DreamlandGold.copy(alpha = 0.12f))
        Spacer(Modifier.height(12.dp))

        if (order.assignedToName.isNotBlank()) {
            Spacer(Modifier.height(4.dp))
            Text("→ ${order.assignedToName}", color = Color(0xFF4CAF50), style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.SemiBold)
        }
        if (order.notes.isNotBlank()) {
            Spacer(Modifier.height(4.dp))
            Text(order.notes, color = DreamlandMuted.copy(alpha = 0.7f), style = MaterialTheme.typography.labelSmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
        }

        Spacer(Modifier.height(20.dp))
        HorizontalDivider(color = DreamlandGold.copy(alpha = 0.12f))
        Spacer(Modifier.height(16.dp))

        // Actions
        Text("ACTIONS", style = MaterialTheme.typography.labelMedium, color = DreamlandGold, letterSpacing = 1.5.sp)
        Spacer(Modifier.height(12.dp))

        when (order.status) {
            "NEW" -> {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Button(
                        onClick = { vm.openAssignStaff(order.id) },
                        modifier = Modifier.weight(1f).height(42.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = DreamlandGold),
                        shape = RoundedCornerShape(10.dp),
                    ) {
                        Text("Assign Staff", color = Color(0xFF0D1F17), fontWeight = FontWeight.SemiBold)
                    }
                    OutlinedButton(
                        onClick = { vm.updateStatus(order.id, "COMPLETED") },
                        modifier = Modifier.weight(1f).height(42.dp),
                        border = BorderStroke(1.dp, DreamlandGold.copy(alpha = 0.6f)),
                        shape = RoundedCornerShape(10.dp),
                    ) {
                        Text("Mark Done", color = DreamlandGold, fontWeight = FontWeight.SemiBold)
                    }
                }
            }
            "ASSIGNED" -> {
                Button(
                    onClick = { showConfirm = true },
                    modifier = Modifier.fillMaxWidth().height(42.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF4CAF50)),
                    shape = RoundedCornerShape(10.dp),
                ) {
                    Text("Mark Completed", color = Color(0xFF0D1F17), fontWeight = FontWeight.SemiBold)
                }
            }
            else -> {
                Box(Modifier.clip(RoundedCornerShape(8.dp)).background(DreamlandMuted.copy(alpha = 0.1f)).padding(horizontal = 14.dp, vertical = 8.dp)) {
                    Text("COMPLETED", color = DreamlandMuted, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold, letterSpacing = 1.sp)
                }
            }
        }

        // Delete — available for any status; removes the order document permanently.
        Spacer(Modifier.height(12.dp))
        OutlinedButton(
            onClick = { showDeleteConfirm = true },
            modifier = Modifier.fillMaxWidth().height(42.dp),
            border = BorderStroke(1.dp, Color(0xFFEF5350).copy(alpha = 0.6f)),
            shape = RoundedCornerShape(10.dp),
        ) {
            Text("Delete Order", color = Color(0xFFEF5350), fontWeight = FontWeight.SemiBold)
        }
    }

    if (showConfirm) {
        AlertDialog(
            onDismissRequest = { showConfirm = false },
            containerColor = DreamlandForestElevated,
            title = { Text("Complete Order?", color = DreamlandOnDark, fontWeight = FontWeight.SemiBold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("Room ${order.roomNumber}  ·  ${order.guestName}", color = DreamlandOnDark, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                    order.items.forEach { item -> Text("• ${item.name} ×${item.quantity}", color = DreamlandMuted, style = MaterialTheme.typography.bodySmall) }
                }
            },
            confirmButton = {
                Button(onClick = { showConfirm = false; vm.updateStatus(order.id, "COMPLETED") }, colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF4CAF50)), shape = RoundedCornerShape(8.dp)) {
                    Text("Yes, Complete", color = Color(0xFF0D1F17), fontWeight = FontWeight.SemiBold)
                }
            },
            dismissButton = { TextButton(onClick = { showConfirm = false }) { Text("Cancel", color = DreamlandMuted) } },
        )
    }

    if (showDeleteConfirm) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            containerColor = DreamlandForestElevated,
            title = { Text("Delete Order?", color = DreamlandOnDark, fontWeight = FontWeight.SemiBold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("This permanently deletes the order. This action cannot be undone.", color = DreamlandMuted, style = MaterialTheme.typography.bodySmall)
                    Text("Room ${order.roomNumber}  ·  ${order.guestName}", color = DreamlandOnDark, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                    order.items.forEach { item -> Text("• ${item.name} ×${item.quantity}", color = DreamlandMuted, style = MaterialTheme.typography.bodySmall) }
                }
            },
            confirmButton = {
                Button(onClick = { showDeleteConfirm = false; vm.deleteOrder(order.id) }, colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFEF5350)), shape = RoundedCornerShape(8.dp)) {
                    Text("Delete", color = Color.White, fontWeight = FontWeight.SemiBold)
                }
            },
            dismissButton = { TextButton(onClick = { showDeleteConfirm = false }) { Text("Cancel", color = DreamlandMuted) } },
        )
    }
}

@Composable
private fun OrderDetailPlaceholder() {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Select an order", color = DreamlandMuted, style = MaterialTheme.typography.titleMedium)
            Text("Choose an order from the list to see its details and take action", color = DreamlandMuted.copy(alpha = 0.6f), style = MaterialTheme.typography.bodySmall)
        }
    }
}

── END OLD composables ───────────────────────────────────────────────────── */

// ── Shared composables ────────────────────────────────────────────────────────

@Composable
private fun OrderTypeBadge(type: String, large: Boolean = false) {
    val (label, color) = when (type) {
        "ROOM_SERVICE" -> "Room Service" to Color(0xFF7986CB)
        "SERVICE" -> "Service" to Color(0xFF4DB6AC)
        else -> "Order" to DreamlandMuted
    }
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(6.dp))
            .background(color.copy(alpha = 0.15f))
            .border(1.dp, color.copy(alpha = 0.4f), RoundedCornerShape(6.dp))
            .padding(horizontal = if (large) 10.dp else 7.dp, vertical = if (large) 4.dp else 3.dp),
    ) {
        Text(label, color = color, style = if (large) MaterialTheme.typography.labelMedium else MaterialTheme.typography.labelSmall, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun FilterDropdown(
    label: String,
    selected: String,
    options: List<Pair<String, String>>,
    onSelect: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    var expanded by remember { mutableStateOf(false) }
    val displayLabel = options.find { it.first == selected }?.second ?: label
    Box(modifier) {
        OutlinedButton(
            onClick = { expanded = true },
            shape = RoundedCornerShape(8.dp),
            border = BorderStroke(1.dp, if (selected.isBlank()) DreamlandMuted.copy(alpha = 0.4f) else DreamlandGold),
            modifier = Modifier.fillMaxWidth(),
            contentPadding = PaddingValues(horizontal = 6.dp, vertical = 0.dp),
        ) {
            Text(displayLabel, color = if (selected.isBlank()) DreamlandMuted else DreamlandOnDark, style = MaterialTheme.typography.labelSmall, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
            Text("▾", color = DreamlandGold, fontSize = 9.sp)
        }
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
            properties = PopupProperties(focusable = false),
            modifier = Modifier.widthIn(min = 140.dp).background(DreamlandForestElevated),
        ) {
            options.forEach { (value, display) ->
                DropdownMenuItem(
                    text = { Text(display, color = if (value == selected) DreamlandGold else DreamlandOnDark, style = MaterialTheme.typography.bodySmall, fontWeight = if (value == selected) FontWeight.SemiBold else FontWeight.Normal) },
                    onClick = { onSelect(value); expanded = false },
                )
            }
        }
    }
}

private fun timeAgo(date: Date): String {
    val diff = System.currentTimeMillis() - date.time
    val minutes = TimeUnit.MILLISECONDS.toMinutes(diff)
    val hours = TimeUnit.MILLISECONDS.toHours(diff)
    val days = TimeUnit.MILLISECONDS.toDays(diff)
    return when {
        minutes < 1  -> "Just now"
        minutes < 60 -> "${minutes}m ago"
        hours < 24   -> "${hours}h ago"
        days < 7     -> "${days}d ago"
        else         -> SimpleDateFormat("d MMM", Locale.getDefault()).format(date)
    }
}
