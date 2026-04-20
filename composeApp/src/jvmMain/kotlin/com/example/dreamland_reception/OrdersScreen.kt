package com.example.dreamland_reception

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
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

    Column(
        modifier = Modifier.fillMaxSize().background(DreamlandForest),
    ) {
        // ── Header ─────────────────────────────────────────────────────────
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Column {
                Text(
                    "REQUESTS",
                    style = MaterialTheme.typography.labelLarge,
                    color = DreamlandGold,
                    letterSpacing = 2.sp,
                )
                Text("Orders", style = MaterialTheme.typography.headlineMedium, color = DreamlandOnDark)
            }
            Button(
                onClick = { vm.openCreateOrder() },
                colors = ButtonDefaults.buttonColors(containerColor = DreamlandGold),
                shape = RoundedCornerShape(10.dp),
            ) {
                Text("+ New Order", color = Color(0xFF0D1F17), fontWeight = FontWeight.SemiBold)
            }
        }

        HorizontalDivider(color = DreamlandGold.copy(alpha = 0.15f))

        // ── Filter row ─────────────────────────────────────────────────────
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            OutlinedTextField(
                value = screenState.searchQuery,
                onValueChange = { vm.onSearch(it) },
                label = { Text("Search guest, room, item…", color = DreamlandMuted, fontSize = 12.sp) },
                singleLine = true,
                modifier = Modifier.weight(1f).heightIn(max = 56.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedTextColor = DreamlandOnDark,
                    unfocusedTextColor = DreamlandOnDark,
                    focusedBorderColor = DreamlandGold,
                    unfocusedBorderColor = DreamlandMuted.copy(alpha = 0.4f),
                    cursorColor = DreamlandGold,
                ),
            )
            FilterDropdown(
                label = "Room",
                selected = screenState.roomFilter,
                options = listOf("" to "All Rooms") + screenState.uniqueRooms.map { it to "Room $it" },
                onSelect = { vm.onRoomFilter(it) },
            )
            FilterDropdown(
                label = "Staff",
                selected = screenState.staffFilter,
                options = listOf("" to "All Staff") + screenState.uniqueStaff.map { it.first to it.second },
                onSelect = { vm.onStaffFilter(it) },
            )
        }

        HorizontalDivider(color = DreamlandGold.copy(alpha = 0.15f))

        // ── Tab row ────────────────────────────────────────────────────────
        val tabLabels = listOf(
            "NEW" to screenState.newOrders.size,
            "ASSIGNED" to screenState.assignedOrders.size,
            "COMPLETED" to screenState.completedOrders.size,
        )
        val tabAccentColors = listOf(
            Color(0xFFFFC107),
            Color(0xFF4CAF50),
            DreamlandMuted,
        )
        SecondaryTabRow(
            selectedTabIndex = screenState.selectedTab,
            containerColor = DreamlandForestSurface,
            contentColor = DreamlandGold,
            indicator = {
                TabRowDefaults.SecondaryIndicator(
                    Modifier.tabIndicatorOffset(screenState.selectedTab),
                    color = tabAccentColors[screenState.selectedTab],
                )
            },
        ) {
            tabLabels.forEachIndexed { i, (label, count) ->
                Tab(
                    selected = screenState.selectedTab == i,
                    onClick = { vm.onTabSelected(i) },
                    modifier = Modifier.padding(vertical = 4.dp),
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        modifier = Modifier.padding(vertical = 10.dp),
                    ) {
                        Text(
                            label,
                            color = if (screenState.selectedTab == i) tabAccentColors[i] else DreamlandMuted,
                            fontWeight = if (screenState.selectedTab == i) FontWeight.Bold else FontWeight.Normal,
                            style = MaterialTheme.typography.bodyMedium,
                        )
                        if (count > 0) {
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(tabAccentColors[i].copy(alpha = 0.2f))
                                    .padding(horizontal = 7.dp, vertical = 2.dp),
                            ) {
                                Text(
                                    "$count",
                                    color = tabAccentColors[i],
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Bold,
                                )
                            }
                        }
                    }
                }
            }
        }

        // ── Body ───────────────────────────────────────────────────────────
        Box(modifier = Modifier.fillMaxSize()) {
            when {
                screenState.isLoading -> {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(color = DreamlandGold)
                    }
                }
                screenState.error != null -> {
                    val err = screenState.error!!
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        ErrorText(err)
                    }
                }
                screenState.filtered.isEmpty() -> {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                when (screenState.selectedTab) {
                                    0 -> "No new orders"
                                    1 -> "No assigned orders"
                                    else -> "No completed orders"
                                },
                                color = DreamlandMuted,
                                style = MaterialTheme.typography.bodyLarge,
                            )
                            if (screenState.searchQuery.isNotBlank() || screenState.roomFilter.isNotBlank() || screenState.staffFilter.isNotBlank()) {
                                Spacer(Modifier.height(8.dp))
                                Text(
                                    "Try adjusting your filters",
                                    color = DreamlandMuted.copy(alpha = 0.6f),
                                    style = MaterialTheme.typography.bodySmall,
                                )
                            }
                        }
                    }
                }
                else -> {
                    LazyVerticalGrid(
                        columns = GridCells.Fixed(2),
                        contentPadding = PaddingValues(16.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                        modifier = Modifier.fillMaxSize(),
                    ) {
                        items(screenState.filtered, key = { it.id }) { order ->
                            OrderCard(order = order, vm = vm)
                        }
                    }
                }
            }
        }
    }

    if (createDialog.isOpen) CreateOrderDialog(state = createDialog, vm = vm)
    if (assignDialog.isOpen) AssignStaffDialog(state = assignDialog, vm = vm)
}

// ── Order Card ────────────────────────────────────────────────────────────────

@Composable
private fun OrderCard(order: Order, vm: OrdersViewModel) {
    val accentColor = when (order.status) {
        "NEW" -> Color(0xFFFFC107)
        "ASSIGNED" -> Color(0xFF4CAF50)
        else -> DreamlandMuted
    }
    Card(
        colors = CardDefaults.cardColors(containerColor = DreamlandForestElevated),
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(modifier = Modifier.fillMaxWidth()) {
            // ── Status accent bar ──────────────────────────────────────────
            Box(
                modifier = Modifier
                    .width(4.dp)
                    .fillMaxHeight()
                    .background(accentColor),
            )
            Column(modifier = Modifier.weight(1f).padding(14.dp)) {
                // Room + type badge
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        "Room ${order.roomNumber}",
                        style = MaterialTheme.typography.titleMedium,
                        color = DreamlandOnDark,
                        fontWeight = FontWeight.Bold,
                    )
                    OrderTypeBadge(order.type)
                }
                Text(
                    order.guestName,
                    color = DreamlandMuted,
                    style = MaterialTheme.typography.bodySmall,
                )

                Spacer(Modifier.height(8.dp))

                // Items preview
                order.items.take(2).forEach { item ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Text(
                            "${item.name} ×${item.quantity}",
                            color = DreamlandOnDark,
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.weight(1f),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        if (item.price > 0) {
                            Text(
                                "₹${(item.price * item.quantity).toLong()}",
                                color = DreamlandMuted,
                                style = MaterialTheme.typography.bodySmall,
                            )
                        }
                    }
                }
                if (order.items.size > 2) {
                    Text(
                        "+${order.items.size - 2} more item${if (order.items.size - 2 > 1) "s" else ""}",
                        color = DreamlandMuted,
                        style = MaterialTheme.typography.labelSmall,
                    )
                }

                Spacer(Modifier.height(8.dp))
                HorizontalDivider(color = DreamlandGold.copy(alpha = 0.1f))
                Spacer(Modifier.height(8.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        timeAgo(order.orderedAt),
                        color = DreamlandMuted,
                        style = MaterialTheme.typography.labelSmall,
                    )
                    if (order.totalAmount > 0) {
                        Text(
                            "₹${order.totalAmount.toLong()}",
                            color = DreamlandGold,
                            fontWeight = FontWeight.Bold,
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    }
                }

                if (order.assignedToName.isNotBlank()) {
                    Text(
                        "→ ${order.assignedToName}",
                        color = DreamlandMuted,
                        style = MaterialTheme.typography.labelSmall,
                    )
                }

                if (order.notes.isNotBlank()) {
                    Text(
                        order.notes,
                        color = DreamlandMuted.copy(alpha = 0.7f),
                        style = MaterialTheme.typography.labelSmall,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }

                Spacer(Modifier.height(10.dp))
                OrderCardActions(order = order, vm = vm)
            }
        }
    }
}

@Composable
private fun OrderCardActions(order: Order, vm: OrdersViewModel) {
    when (order.status) {
        "NEW" -> {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    onClick = { vm.openAssignStaff(order.id) },
                    modifier = Modifier.weight(1f).height(36.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = DreamlandGold),
                    shape = RoundedCornerShape(8.dp),
                    contentPadding = PaddingValues(horizontal = 8.dp),
                ) {
                    Text(
                        "Assign Staff",
                        color = Color(0xFF0D1F17),
                        fontWeight = FontWeight.SemiBold,
                        style = MaterialTheme.typography.labelMedium,
                    )
                }
                OutlinedButton(
                    onClick = { vm.updateStatus(order.id, "COMPLETED") },
                    modifier = Modifier.weight(1f).height(36.dp),
                    border = BorderStroke(1.dp, DreamlandGold.copy(alpha = 0.6f)),
                    shape = RoundedCornerShape(8.dp),
                    contentPadding = PaddingValues(horizontal = 8.dp),
                ) {
                    Text("Mark Done", style = MaterialTheme.typography.labelMedium, color = DreamlandGold)
                }
            }
        }
        "ASSIGNED" -> {
            Button(
                onClick = { vm.updateStatus(order.id, "COMPLETED") },
                modifier = Modifier.fillMaxWidth().height(36.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF4CAF50)),
                shape = RoundedCornerShape(8.dp),
            ) {
                Text(
                    "Mark Completed",
                    color = Color(0xFF0D1F17),
                    fontWeight = FontWeight.SemiBold,
                    style = MaterialTheme.typography.labelMedium,
                )
            }
        }
        else -> {
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(6.dp))
                    .background(DreamlandMuted.copy(alpha = 0.1f))
                    .padding(horizontal = 10.dp, vertical = 4.dp),
            ) {
                Text(
                    "COMPLETED",
                    color = DreamlandMuted,
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.SemiBold,
                    letterSpacing = 1.sp,
                )
            }
        }
    }
}

@Composable
private fun OrderTypeBadge(type: String) {
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
            .padding(horizontal = 8.dp, vertical = 3.dp),
    ) {
        Text(label, color = color, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.SemiBold)
    }
}

// ── Filter dropdown ───────────────────────────────────────────────────────────

@Composable
private fun FilterDropdown(
    label: String,
    selected: String,
    options: List<Pair<String, String>>,  // value to display
    onSelect: (String) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    val displayLabel = options.find { it.first == selected }?.second ?: label
    Box {
        OutlinedButton(
            onClick = { expanded = true },
            shape = RoundedCornerShape(8.dp),
            border = BorderStroke(
                1.dp,
                if (selected.isBlank()) DreamlandMuted.copy(alpha = 0.4f) else DreamlandGold,
            ),
            modifier = Modifier.widthIn(min = 130.dp),
        ) {
            Text(
                displayLabel,
                color = if (selected.isBlank()) DreamlandMuted else DreamlandOnDark,
                style = MaterialTheme.typography.bodySmall,
            )
            Spacer(Modifier.width(4.dp))
            Text("▾", color = DreamlandGold, fontSize = 10.sp)
        }
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
            properties = PopupProperties(focusable = false),
            modifier = Modifier.widthIn(min = 150.dp).background(DreamlandForestElevated),
        ) {
            options.forEach { (value, display) ->
                DropdownMenuItem(
                    text = {
                        Text(
                            display,
                            color = if (value == selected) DreamlandGold else DreamlandOnDark,
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = if (value == selected) FontWeight.SemiBold else FontWeight.Normal,
                        )
                    },
                    onClick = {
                        onSelect(value)
                        expanded = false
                    },
                )
            }
        }
    }
}

// ── Time helper ───────────────────────────────────────────────────────────────

private fun timeAgo(date: Date): String {
    val diff = System.currentTimeMillis() - date.time
    val minutes = TimeUnit.MILLISECONDS.toMinutes(diff)
    val hours = TimeUnit.MILLISECONDS.toHours(diff)
    val days = TimeUnit.MILLISECONDS.toDays(diff)
    return when {
        minutes < 1 -> "Just now"
        minutes < 60 -> "${minutes}m ago"
        hours < 24 -> "${hours}h ago"
        days < 7 -> "${days}d ago"
        else -> SimpleDateFormat("d MMM", Locale.getDefault()).format(date)
    }
}
