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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.PopupProperties
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.dreamland_reception.data.model.Complaint
import com.example.dreamland_reception.data.model.Order
import com.example.dreamland_reception.data.model.StaffMember
import com.example.dreamland_reception.staff.AddEditStaffDialog
import com.example.dreamland_reception.staff.AssignTasksDialog
import com.example.dreamland_reception.ui.viewmodel.StaffViewModel

// ── Role colours ─────────────────────────────────────────────────────────────

private val roleColor = { role: String ->
    when (role) {
        "HOUSEKEEPING" -> Color(0xFF4DB6AC)
        "MAINTENANCE"  -> Color(0xFFFF8A65)
        "RECEPTION"    -> Color(0xFF7986CB)
        else           -> DreamlandMuted
    }
}

private val roleLabel = { role: String ->
    when (role) {
        "HOUSEKEEPING" -> "Housekeeping"
        "MAINTENANCE"  -> "Maintenance"
        "RECEPTION"    -> "Reception"
        else           -> role.lowercase().replaceFirstChar { it.uppercase() }
    }
}

private val availColor = { member: StaffMember ->
    when {
        !member.isActive   -> DreamlandMuted
        member.isAvailable -> Color(0xFF4CAF50)
        else               -> Color(0xFFFFC107)
    }
}

// ── Screen ────────────────────────────────────────────────────────────────────

@Composable
fun StaffScreen(vm: StaffViewModel = DreamlandAppInitializer.getStaffViewModel()) {
    val state by vm.screenState.collectAsStateWithLifecycle()
    val formDialog by vm.formDialog.collectAsStateWithLifecycle()
    val assignTasksDialog by vm.assignTasksDialog.collectAsStateWithLifecycle()

    val ordersVm = DreamlandAppInitializer.getOrdersViewModel()
    val complaintsVm = DreamlandAppInitializer.getComplaintsViewModel()
    val allOrders by ordersVm.screenState.collectAsStateWithLifecycle()
    val allComplaints by complaintsVm.screenState.collectAsStateWithLifecycle()

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
                    "TEAM",
                    style = MaterialTheme.typography.labelLarge,
                    color = DreamlandGold,
                    letterSpacing = 2.sp,
                )
                Text(
                    "Staff Management",
                    style = MaterialTheme.typography.headlineMedium,
                    color = DreamlandOnDark,
                )
            }
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                // Summary chips
                if (!state.isLoading && state.staff.isNotEmpty()) {
                    StaffSummaryChip(count = state.availableCount, label = "Available", color = Color(0xFF4CAF50))
                    StaffSummaryChip(count = state.busyCount, label = "Busy", color = Color(0xFFFFC107))
                    if (state.inactiveCount > 0) {
                        StaffSummaryChip(count = state.inactiveCount, label = "Inactive", color = DreamlandMuted)
                    }
                }
                Button(
                    onClick = { vm.openAddStaff() },
                    colors = ButtonDefaults.buttonColors(containerColor = DreamlandGold),
                    shape = RoundedCornerShape(10.dp),
                ) {
                    Text("+ Add Staff", color = Color(0xFF0D1F17), fontWeight = FontWeight.SemiBold)
                }
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
                value = state.searchQuery,
                onValueChange = { vm.onSearch(it) },
                label = { Text("Search by name or phone…", color = DreamlandMuted, fontSize = 12.sp) },
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
            StaffFilterDropdown(
                label = "Role",
                selected = state.roleFilter,
                options = listOf(
                    "" to "All Roles",
                    "HOUSEKEEPING" to "Housekeeping",
                    "MAINTENANCE" to "Maintenance",
                    "RECEPTION" to "Reception",
                ),
                onSelect = { vm.onRoleFilter(it) },
            )
            StaffFilterDropdown(
                label = "Availability",
                selected = state.availabilityFilter,
                options = listOf(
                    "" to "All",
                    "AVAILABLE" to "Available",
                    "BUSY" to "Busy",
                ),
                onSelect = { vm.onAvailabilityFilter(it) },
            )
            StaffFilterDropdown(
                label = "Status",
                selected = state.statusFilter,
                options = listOf(
                    "" to "All Status",
                    "ACTIVE" to "Active",
                    "INACTIVE" to "Inactive",
                ),
                onSelect = { vm.onStatusFilter(it) },
            )
        }

        HorizontalDivider(color = DreamlandGold.copy(alpha = 0.15f))

        // ── Body ───────────────────────────────────────────────────────────
        Box(modifier = Modifier.fillMaxSize()) {
            when {
                state.isLoading -> {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(color = DreamlandGold)
                    }
                }
                state.error != null -> {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        ErrorText(state.error!!)
                    }
                }
                state.filtered.isEmpty() -> {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                if (state.staff.isEmpty()) "No staff members yet" else "No staff match the filters",
                                color = DreamlandMuted,
                                style = MaterialTheme.typography.bodyLarge,
                            )
                            if (state.staff.isEmpty()) {
                                Spacer(Modifier.height(8.dp))
                                Text(
                                    "Tap \"+ Add Staff\" to add your first team member",
                                    color = DreamlandMuted.copy(alpha = 0.6f),
                                    style = MaterialTheme.typography.bodySmall,
                                )
                            } else {
                                Spacer(Modifier.height(8.dp))
                                Text(
                                    "Try adjusting your search or filters",
                                    color = DreamlandMuted.copy(alpha = 0.6f),
                                    style = MaterialTheme.typography.bodySmall,
                                )
                            }
                        }
                    }
                }
                else -> {
                    LazyVerticalGrid(
                        columns = GridCells.Fixed(3),
                        contentPadding = PaddingValues(16.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                        modifier = Modifier.fillMaxSize(),
                    ) {
                        items(state.filtered, key = { it.id }) { member ->
                            StaffCard(
                                member = member,
                                vm = vm,
                                assignedOrders = allOrders.orders.filter {
                                    it.assignedTo == member.id && it.status == "ASSIGNED"
                                },
                                assignedComplaints = allComplaints.complaints.filter {
                                    it.assignedTo == member.id && it.status == "ASSIGNED"
                                },
                            )
                        }
                    }
                }
            }
        }
    }

    if (formDialog.isOpen) AddEditStaffDialog(state = formDialog, vm = vm)
    if (assignTasksDialog.isOpen) AssignTasksDialog(state = assignTasksDialog, vm = vm)
}

// ── Staff Card ────────────────────────────────────────────────────────────────

@Composable
private fun StaffCard(
    member: StaffMember,
    vm: StaffViewModel,
    assignedOrders: List<Order> = emptyList(),
    assignedComplaints: List<Complaint> = emptyList(),
) {
    val accentColor = availColor(member)
    val rc = roleColor(member.role)

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
                // ── Role badge + Active/Inactive chip ──────────────────────
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    RoleBadge(role = member.role)
                    ActiveChip(isActive = member.isActive)
                }

                Spacer(Modifier.height(10.dp))

                // ── Name ───────────────────────────────────────────────────
                Text(
                    member.name,
                    style = MaterialTheme.typography.titleMedium,
                    color = DreamlandOnDark,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )

                // ── Phone ──────────────────────────────────────────────────
                if (member.phone.isNotBlank()) {
                    Text(
                        member.phone,
                        color = DreamlandMuted,
                        style = MaterialTheme.typography.bodySmall,
                    )
                }

                Spacer(Modifier.height(10.dp))

                // ── Availability pill ──────────────────────────────────────
                AvailabilityPill(member = member)

                Spacer(Modifier.height(12.dp))
                HorizontalDivider(color = DreamlandGold.copy(alpha = 0.10f))
                Spacer(Modifier.height(10.dp))

                // ── Assigned tasks ─────────────────────────────────────────
                val totalAssigned = assignedOrders.size + assignedComplaints.size
                if (totalAssigned > 0) {
                    Spacer(Modifier.height(6.dp))
                    AssignedTasksList(orders = assignedOrders, complaints = assignedComplaints)
                    Spacer(Modifier.height(6.dp))
                    HorizontalDivider(color = DreamlandGold.copy(alpha = 0.10f))
                }

                // ── Actions ────────────────────────────────────────────────
                StaffCardActions(member = member, vm = vm)
            }
        }
    }
}

// ── Card sub-components ───────────────────────────────────────────────────────

@Composable
private fun RoleBadge(role: String) {
    val color = roleColor(role)
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(6.dp))
            .background(color.copy(alpha = 0.15f))
            .border(1.dp, color.copy(alpha = 0.35f), RoundedCornerShape(6.dp))
            .padding(horizontal = 8.dp, vertical = 3.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(6.dp)
                    .clip(CircleShape)
                    .background(color),
            )
            Spacer(Modifier.width(5.dp))
            Text(
                roleLabel(role),
                color = color,
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.SemiBold,
            )
        }
    }
}

@Composable
private fun ActiveChip(isActive: Boolean) {
    val color = if (isActive) Color(0xFF4CAF50) else DreamlandMuted
    val label = if (isActive) "Active" else "Inactive"
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(6.dp))
            .background(color.copy(alpha = 0.12f))
            .padding(horizontal = 7.dp, vertical = 3.dp),
    ) {
        Text(
            label,
            color = color,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.SemiBold,
        )
    }
}

@Composable
private fun AvailabilityPill(member: StaffMember) {
    val (dotColor, label) = when {
        !member.isActive   -> DreamlandMuted to "Inactive"
        member.isAvailable -> Color(0xFF4CAF50) to "Available"
        else               -> Color(0xFFFFC107) to "Busy"
    }
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Box(
            modifier = Modifier
                .size(9.dp)
                .clip(CircleShape)
                .background(dotColor),
        )
        Text(
            label,
            color = dotColor,
            fontWeight = FontWeight.SemiBold,
            style = MaterialTheme.typography.bodySmall,
        )
    }
}

@Composable
private fun StaffCardActions(member: StaffMember, vm: StaffViewModel) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        // Assign / Set Available — only for active staff
        if (member.isActive) {
            if (member.isAvailable) {
                // Available: show Assign button to open task assignment dialog
                Button(
                    onClick = { vm.openAssignTasks(member) },
                    modifier = Modifier.fillMaxWidth().height(34.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = DreamlandGold.copy(alpha = 0.18f),
                    ),
                    shape = RoundedCornerShape(8.dp),
                    contentPadding = PaddingValues(horizontal = 8.dp),
                    border = BorderStroke(1.dp, DreamlandGold.copy(alpha = 0.5f)),
                ) {
                    Text(
                        "Assign",
                        color = DreamlandGold,
                        fontWeight = FontWeight.SemiBold,
                        style = MaterialTheme.typography.labelMedium,
                    )
                }
            } else {
                // Busy: show Set Available button
                Button(
                    onClick = { vm.toggleAvailability(member) },
                    modifier = Modifier.fillMaxWidth().height(34.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFF4CAF50).copy(alpha = 0.18f),
                    ),
                    shape = RoundedCornerShape(8.dp),
                    contentPadding = PaddingValues(horizontal = 8.dp),
                    border = BorderStroke(1.dp, Color(0xFF4CAF50).copy(alpha = 0.5f)),
                ) {
                    Text(
                        "Set Available",
                        color = Color(0xFF4CAF50),
                        fontWeight = FontWeight.SemiBold,
                        style = MaterialTheme.typography.labelMedium,
                    )
                }
            }
        }

        // Edit + Activate/Deactivate row
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            OutlinedButton(
                onClick = { vm.openEditStaff(member) },
                modifier = Modifier.weight(1f).height(34.dp),
                border = BorderStroke(1.dp, DreamlandGold.copy(alpha = 0.55f)),
                shape = RoundedCornerShape(8.dp),
                contentPadding = PaddingValues(horizontal = 8.dp),
            ) {
                Text(
                    "Edit",
                    color = DreamlandGold,
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold,
                )
            }
            OutlinedButton(
                onClick = { vm.toggleActive(member) },
                modifier = Modifier.weight(1f).height(34.dp),
                border = BorderStroke(
                    1.dp,
                    if (member.isActive) Color(0xFFEF5350).copy(alpha = 0.5f)
                    else Color(0xFF4CAF50).copy(alpha = 0.5f),
                ),
                shape = RoundedCornerShape(8.dp),
                contentPadding = PaddingValues(horizontal = 8.dp),
            ) {
                Text(
                    if (member.isActive) "Deactivate" else "Activate",
                    color = if (member.isActive) Color(0xFFEF5350) else Color(0xFF4CAF50),
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold,
                )
            }
        }
    }
}

// ── Assigned tasks on card ────────────────────────────────────────────────────

@Composable
private fun AssignedTasksList(orders: List<Order>, complaints: List<Complaint>) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        orders.forEach { order ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(6.dp))
                    .background(Color(0xFF7986CB).copy(alpha = 0.10f))
                    .padding(horizontal = 8.dp, vertical = 5.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Box(
                    modifier = Modifier
                        .size(6.dp)
                        .clip(CircleShape)
                        .background(Color(0xFF7986CB)),
                )
                Text(
                    "Order · Rm ${order.roomNumber}",
                    color = Color(0xFF7986CB),
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.weight(1f),
                )
                val itemNames = order.items.take(1).joinToString { it.name }
                    .let { if (order.items.size > 1) "$it +${order.items.size - 1}" else it }
                if (itemNames.isNotBlank()) {
                    Text(
                        itemNames,
                        color = DreamlandMuted,
                        style = MaterialTheme.typography.labelSmall,
                    )
                }
            }
        }
        complaints.forEach { complaint ->
            val priorityColor = when (complaint.priority) {
                "HIGH" -> Color(0xFFEF5350)
                "LOW"  -> Color(0xFF4CAF50)
                else   -> Color(0xFFFFC107)
            }
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(6.dp))
                    .background(Color(0xFFEF5350).copy(alpha = 0.08f))
                    .padding(horizontal = 8.dp, vertical = 5.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Box(
                    modifier = Modifier
                        .size(6.dp)
                        .clip(CircleShape)
                        .background(priorityColor),
                )
                Text(
                    "Complaint · Rm ${complaint.roomNumber}",
                    color = Color(0xFFEF5350),
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    complaint.priority,
                    color = priorityColor,
                    style = MaterialTheme.typography.labelSmall,
                )
            }
        }
    }
}

// ── Summary chip ──────────────────────────────────────────────────────────────

@Composable
private fun StaffSummaryChip(count: Int, label: String, color: Color) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(20.dp))
            .background(color.copy(alpha = 0.12f))
            .border(1.dp, color.copy(alpha = 0.3f), RoundedCornerShape(20.dp))
            .padding(horizontal = 12.dp, vertical = 6.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
            Box(
                modifier = Modifier
                    .size(7.dp)
                    .clip(CircleShape)
                    .background(color),
            )
            Text(
                "$count $label",
                color = color,
                fontWeight = FontWeight.SemiBold,
                style = MaterialTheme.typography.labelMedium,
            )
        }
    }
}

// ── Filter dropdown ───────────────────────────────────────────────────────────

@Composable
private fun StaffFilterDropdown(
    label: String,
    selected: String,
    options: List<Pair<String, String>>,
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
