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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.PopupProperties
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.dreamland_reception.data.model.Order
import com.example.dreamland_reception.orders.AssignStaffDialog
import com.example.dreamland_reception.orders.CreateOrderDialog
import com.example.dreamland_reception.ui.viewmodel.OrdersViewModel
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit

@Composable
fun OrdersScreen(vm: OrdersViewModel = DreamlandAppInitializer.getOrdersViewModel()) {
    val screenState by vm.screenState.collectAsStateWithLifecycle()
    val createDialog by vm.createOrderDialog.collectAsStateWithLifecycle()
    val assignDialog by vm.assignStaffDialog.collectAsStateWithLifecycle()

    val selectedOrder = screenState.orders.find { it.id == screenState.selectedOrderId }

    Row(Modifier.fillMaxSize()) {
        // ── LEFT: list ────────────────────────────────────────────────────────
        Column(Modifier.width(480.dp).fillMaxHeight().background(DreamlandForestSurface)) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Column {
                    Text("REQUESTS", style = MaterialTheme.typography.labelLarge, color = DreamlandGold, letterSpacing = 2.sp)
                    Text("Orders", style = MaterialTheme.typography.headlineSmall, color = DreamlandOnDark, fontWeight = FontWeight.Bold)
                }
                Button(
                    onClick = { vm.openCreateOrder() },
                    colors = ButtonDefaults.buttonColors(containerColor = DreamlandGold),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.height(34.dp),
                    contentPadding = PaddingValues(horizontal = 12.dp),
                ) {
                    Text("+ New", color = Color(0xFF0D1F17), fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                }
            }
            HorizontalDivider(color = DreamlandGold.copy(alpha = 0.15f))

            // Filters
            Column(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                OutlinedTextField(
                    value = screenState.searchQuery,
                    onValueChange = { vm.onSearch(it) },
                    placeholder = { Text("Search guest, room, item…", color = DreamlandMuted, fontSize = 12.sp) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = DreamlandOnDark, unfocusedTextColor = DreamlandOnDark,
                        focusedBorderColor = DreamlandGold, unfocusedBorderColor = DreamlandMuted.copy(alpha = 0.4f),
                        cursorColor = DreamlandGold,
                    ),
                )
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    FilterDropdown(
                        label = "Room", selected = screenState.roomFilter,
                        options = listOf("" to "All Rooms") + screenState.uniqueRooms.map { it to "Room $it" },
                        onSelect = { vm.onRoomFilter(it) }, modifier = Modifier.weight(1f),
                    )
                    FilterDropdown(
                        label = "Staff", selected = screenState.staffFilter,
                        options = listOf("" to "All Staff") + screenState.uniqueStaff.map { it.first to it.second },
                        onSelect = { vm.onStaffFilter(it) }, modifier = Modifier.weight(1f),
                    )
                }
            }
            HorizontalDivider(color = DreamlandGold.copy(alpha = 0.15f))

            // Tabs
            val tabLabels = listOf("NEW" to screenState.newOrders.size, "ASSIGNED" to screenState.assignedOrders.size, "COMPLETED" to screenState.completedOrders.size)
            val tabColors = listOf(Color(0xFFFFC107), Color(0xFF4CAF50), DreamlandMuted)
            SecondaryTabRow(
                selectedTabIndex = screenState.selectedTab,
                containerColor = DreamlandForestSurface,
                contentColor = DreamlandGold,
                indicator = { TabRowDefaults.SecondaryIndicator(Modifier.tabIndicatorOffset(screenState.selectedTab), color = tabColors[screenState.selectedTab]) },
            ) {
                tabLabels.forEachIndexed { i, (label, count) ->
                    Tab(selected = screenState.selectedTab == i, onClick = { vm.onTabSelected(i) }, modifier = Modifier.padding(vertical = 2.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp), modifier = Modifier.padding(vertical = 8.dp)) {
                            Text(label, color = if (screenState.selectedTab == i) tabColors[i] else DreamlandMuted, fontWeight = if (screenState.selectedTab == i) FontWeight.Bold else FontWeight.Normal, style = MaterialTheme.typography.labelMedium)
                            if (count > 0) {
                                Box(modifier = Modifier.clip(RoundedCornerShape(8.dp)).background(tabColors[i].copy(alpha = 0.2f)).padding(horizontal = 5.dp, vertical = 1.dp)) {
                                    Text("$count", color = tabColors[i], style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }
                }
            }

            // List
            OrderListContent(screenState.isLoading, screenState.error, screenState.filtered, screenState.selectedOrderId, vm)
        }

        VerticalDivider(color = DreamlandGold.copy(alpha = 0.15f), thickness = 1.dp)

        // ── RIGHT: detail ─────────────────────────────────────────────────────
        Box(Modifier.weight(1f).fillMaxHeight().background(DreamlandForest)) {
            if (selectedOrder != null) {
                OrderDetailPanel(order = selectedOrder, vm = vm)
            } else {
                OrderDetailPlaceholder()
            }
        }
    }

    if (createDialog.isOpen) CreateOrderDialog(state = createDialog, vm = vm)
    if (assignDialog.isOpen) AssignStaffDialog(state = assignDialog, vm = vm)
}

// ── Left panel: list ──────────────────────────────────────────────────────────

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
                    if (order.totalAmount > 0) Text("₹${order.totalAmount.toLong()}", color = DreamlandGold, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                }
            }
            Spacer(Modifier.height(2.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text(timeAgo(order.orderedAt), color = DreamlandMuted.copy(alpha = 0.7f), style = MaterialTheme.typography.labelSmall)
                if (order.assignedToName.isNotBlank()) {
                    Text("→ ${order.assignedToName}", color = Color(0xFF4CAF50), style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
        }
    }
}

// ── Right panel: detail ───────────────────────────────────────────────────────

@Composable
private fun OrderDetailPanel(order: Order, vm: OrdersViewModel) {
    var showConfirm by remember(order.id) { mutableStateOf(false) }
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
                    if (item.price > 0) Text("₹${(item.price * item.quantity).toLong()}", color = DreamlandMuted, style = MaterialTheme.typography.bodySmall)
                }
            }
        }

        if (order.totalAmount > 0) {
            Spacer(Modifier.height(8.dp))
            HorizontalDivider(color = DreamlandGold.copy(alpha = 0.08f))
            Spacer(Modifier.height(6.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("Total", color = DreamlandMuted, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.SemiBold)
                Text("₹${order.totalAmount.toLong()}", color = DreamlandGold, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodyMedium)
            }
        }

        Spacer(Modifier.height(16.dp))
        HorizontalDivider(color = DreamlandGold.copy(alpha = 0.12f))
        Spacer(Modifier.height(12.dp))

        // Meta
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Text(timeAgo(order.orderedAt), color = DreamlandMuted, style = MaterialTheme.typography.labelSmall)
        }
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
