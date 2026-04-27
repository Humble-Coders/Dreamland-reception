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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.material3.TextButton
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
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
import com.example.dreamland_reception.data.model.Complaint
import com.example.dreamland_reception.data.model.Order
import com.example.dreamland_reception.data.model.StaffMember
import com.example.dreamland_reception.staff.AddEditStaffDialog
import com.example.dreamland_reception.staff.AssignTasksDialog
import com.example.dreamland_reception.ui.viewmodel.StaffViewModel

// ── Role / availability helpers ───────────────────────────────────────────────

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

    val selectedMember = state.staff.find { it.id == state.selectedMemberId }
    val assignedOrders = selectedMember?.let { m ->
        allOrders.orders.filter { it.assignedTo == m.id && it.status == "ASSIGNED" }
    } ?: emptyList()
    val assignedComplaints = selectedMember?.let { m ->
        allComplaints.complaints.filter { it.assignedTo == m.id && it.status == "ASSIGNED" }
    } ?: emptyList()

    Row(Modifier.fillMaxSize()) {
        // ── LEFT: staff list ──────────────────────────────────────────────────
        Column(Modifier.width(480.dp).fillMaxHeight().background(DreamlandForestSurface)) {
            // Header
            Column(Modifier.fillMaxWidth().padding(start = 20.dp, end = 16.dp, top = 20.dp, bottom = 12.dp)) {
                Text("TEAM", style = MaterialTheme.typography.labelLarge, color = DreamlandGold, letterSpacing = 2.sp)
                Text("Staff Management", style = MaterialTheme.typography.headlineSmall, color = DreamlandOnDark, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(10.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    if (!state.isLoading && state.staff.isNotEmpty()) {
                        StaffSummaryChip(state.availableCount, "Available", Color(0xFF4CAF50))
                        StaffSummaryChip(state.busyCount, "Busy", Color(0xFFFFC107))
                        if (state.inactiveCount > 0) StaffSummaryChip(state.inactiveCount, "Inactive", DreamlandMuted)
                    }
                    Spacer(Modifier.weight(1f))
                    Button(
                        onClick = { vm.openAddStaff() },
                        colors = ButtonDefaults.buttonColors(containerColor = DreamlandGold),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.height(34.dp),
                        contentPadding = PaddingValues(horizontal = 12.dp),
                    ) {
                        Text("+ Add", color = Color(0xFF0D1F17), fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                    }
                }
            }
            HorizontalDivider(color = DreamlandGold.copy(alpha = 0.15f))

            // Filters
            Column(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = state.searchQuery,
                    onValueChange = { vm.onSearch(it) },
                    placeholder = { Text("Search name or phone…", color = DreamlandMuted, fontSize = 12.sp) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = DreamlandOnDark,
                        unfocusedTextColor = DreamlandOnDark,
                        focusedBorderColor = DreamlandGold,
                        unfocusedBorderColor = DreamlandMuted.copy(alpha = 0.4f),
                        cursorColor = DreamlandGold,
                    ),
                )
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    StaffFilterDropdown(
                        label = "Role", selected = state.roleFilter,
                        options = listOf("" to "All Roles", "HOUSEKEEPING" to "Housekeeping", "MAINTENANCE" to "Maintenance", "RECEPTION" to "Reception"),
                        onSelect = { vm.onRoleFilter(it) },
                        modifier = Modifier.weight(1f),
                    )
                    StaffFilterDropdown(
                        label = "Avail.", selected = state.availabilityFilter,
                        options = listOf("" to "All", "AVAILABLE" to "Available", "BUSY" to "Busy"),
                        onSelect = { vm.onAvailabilityFilter(it) },
                        modifier = Modifier.weight(1f),
                    )
                    StaffFilterDropdown(
                        label = "Status", selected = state.statusFilter,
                        options = listOf("" to "All", "ACTIVE" to "Active", "INACTIVE" to "Inactive"),
                        onSelect = { vm.onStatusFilter(it) },
                        modifier = Modifier.weight(1f),
                    )
                }
            }
            HorizontalDivider(color = DreamlandGold.copy(alpha = 0.15f))

            // List
            StaffListContent(
                state.isLoading, state.error, state.filtered, state.staff.isEmpty(),
                state.selectedMemberId, allOrders.orders, allComplaints.complaints, vm,
            )
        }

        VerticalDivider(color = DreamlandGold.copy(alpha = 0.15f), thickness = 1.dp)

        // ── RIGHT: detail panel ───────────────────────────────────────────────
        Box(Modifier.weight(1f).fillMaxHeight().background(DreamlandForest)) {
            if (selectedMember != null) {
                StaffDetailPanel(
                    member = selectedMember,
                    assignedOrders = assignedOrders,
                    assignedComplaints = assignedComplaints,
                    vm = vm,
                )
            } else {
                StaffDetailPlaceholder()
            }
        }
    }

    if (formDialog.isOpen) AddEditStaffDialog(state = formDialog, vm = vm)
    if (assignTasksDialog.isOpen) AssignTasksDialog(state = assignTasksDialog, vm = vm)
}

// ── Left panel: list ──────────────────────────────────────────────────────────

@Composable
private fun StaffListContent(
    isLoading: Boolean,
    error: String?,
    filtered: List<StaffMember>,
    isEmpty: Boolean,
    selectedId: String?,
    allOrders: List<Order>,
    allComplaints: List<Complaint>,
    vm: StaffViewModel,
) {
    when {
        isLoading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator(color = DreamlandGold)
        }
        error != null -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            ErrorText(error)
        }
        filtered.isEmpty() -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text(
                if (isEmpty) "No staff members yet" else "No staff match filters",
                color = DreamlandMuted, style = MaterialTheme.typography.bodyMedium,
            )
        }
        else -> LazyColumn(
            modifier = Modifier.fillMaxSize().padding(10.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            items(filtered, key = { it.id }) { member ->
                val taskCount = allOrders.count { it.assignedTo == member.id && it.status == "ASSIGNED" } +
                    allComplaints.count { it.assignedTo == member.id && it.status == "ASSIGNED" }
                StaffListItem(
                    member = member,
                    isSelected = member.id == selectedId,
                    assignedTaskCount = taskCount,
                    onClick = {
                        vm.selectMember(if (member.id == selectedId) null else member.id)
                    },
                )
            }
        }
    }
}

@Composable
private fun StaffListItem(
    member: StaffMember,
    isSelected: Boolean,
    assignedTaskCount: Int,
    onClick: () -> Unit,
) {
    val accentColor = availColor(member)
    val rc = roleColor(member.role)
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
        Column(Modifier.weight(1f).padding(horizontal = 10.dp, vertical = 10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    member.name,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = DreamlandOnDark,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(4.dp))
                        .background(rc.copy(alpha = 0.12f))
                        .padding(horizontal = 6.dp, vertical = 2.dp),
                ) {
                    Text(roleLabel(member.role), color = rc, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.SemiBold)
                }
            }
            Spacer(Modifier.height(2.dp))
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                if (member.phone.isNotBlank()) {
                    Text(member.phone, color = DreamlandMuted, style = MaterialTheme.typography.labelSmall, modifier = Modifier.weight(1f))
                } else {
                    Spacer(Modifier.weight(1f))
                }
                val (dotColor, availLabel) = when {
                    !member.isActive   -> DreamlandMuted to "Inactive"
                    member.isAvailable -> Color(0xFF4CAF50) to "Available"
                    else               -> Color(0xFFFFC107) to "Busy"
                }
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    Box(Modifier.size(6.dp).clip(CircleShape).background(dotColor))
                    Text(availLabel, color = dotColor, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.SemiBold)
                }
                if (assignedTaskCount > 0) {
                    Box(
                        modifier = Modifier
                            .clip(CircleShape)
                            .background(Color(0xFFFFC107).copy(alpha = 0.2f))
                            .padding(horizontal = 5.dp, vertical = 1.dp),
                    ) {
                        Text("$assignedTaskCount", color = Color(0xFFFFC107), style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

// ── Right panel: detail ───────────────────────────────────────────────────────

@Composable
private fun StaffDetailPanel(
    member: StaffMember,
    assignedOrders: List<Order>,
    assignedComplaints: List<Complaint>,
    vm: StaffViewModel,
) {
    var showAvailableConfirm by remember(member.id) { mutableStateOf(false) }
    val rc = roleColor(member.role)
    val accentColor = availColor(member)

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(28.dp),
        verticalArrangement = Arrangement.spacedBy(0.dp),
    ) {
        // ── Header ────────────────────────────────────────────────────────────
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.Top) {
            Column(Modifier.weight(1f)) {
                Text(member.name, style = MaterialTheme.typography.headlineMedium, color = DreamlandOnDark, fontWeight = FontWeight.Bold)
                if (member.phone.isNotBlank()) {
                    Spacer(Modifier.height(4.dp))
                    Text(member.phone, color = DreamlandMuted, style = MaterialTheme.typography.bodyMedium)
                }
                Spacer(Modifier.height(10.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    // Role badge
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .background(rc.copy(alpha = 0.15f))
                            .border(1.dp, rc.copy(alpha = 0.35f), RoundedCornerShape(6.dp))
                            .padding(horizontal = 10.dp, vertical = 4.dp),
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                            Box(Modifier.size(6.dp).clip(CircleShape).background(rc))
                            Text(roleLabel(member.role), color = rc, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold)
                        }
                    }
                    // Active chip
                    val activeColor = if (member.isActive) Color(0xFF4CAF50) else DreamlandMuted
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .background(activeColor.copy(alpha = 0.12f))
                            .padding(horizontal = 8.dp, vertical = 4.dp),
                    ) {
                        Text(
                            if (member.isActive) "Active" else "Inactive",
                            color = activeColor, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold,
                        )
                    }
                }
            }
            // Availability pill (large)
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(20.dp))
                    .background(accentColor.copy(alpha = 0.14f))
                    .border(1.dp, accentColor.copy(alpha = 0.35f), RoundedCornerShape(20.dp))
                    .padding(horizontal = 14.dp, vertical = 8.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                    Box(Modifier.size(10.dp).clip(CircleShape).background(accentColor))
                    val label = when {
                        !member.isActive   -> "Inactive"
                        member.isAvailable -> "Available"
                        else               -> "Busy"
                    }
                    Text(label, color = accentColor, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodyMedium)
                }
            }
        }

        Spacer(Modifier.height(20.dp))
        HorizontalDivider(color = DreamlandGold.copy(alpha = 0.12f))
        Spacer(Modifier.height(20.dp))

        // ── Assigned tasks ────────────────────────────────────────────────────
        val totalAssigned = assignedOrders.size + assignedComplaints.size
        if (totalAssigned > 0) {
            Text(
                "CURRENT TASKS",
                style = MaterialTheme.typography.labelMedium,
                color = DreamlandGold,
                letterSpacing = 1.5.sp,
            )
            Spacer(Modifier.height(10.dp))
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                assignedOrders.forEach { order ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .background(Color(0xFF7986CB).copy(alpha = 0.10f))
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Box(Modifier.size(7.dp).clip(CircleShape).background(Color(0xFF7986CB)))
                        Text("Order · Room ${order.roomNumber}", color = Color(0xFF7986CB), style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                        val items = order.items.take(1).joinToString { it.name }.let { if (order.items.size > 1) "$it +${order.items.size - 1}" else it }
                        if (items.isNotBlank()) Text(items, color = DreamlandMuted, style = MaterialTheme.typography.labelSmall)
                    }
                }
                assignedComplaints.forEach { complaint ->
                    val pc = when (complaint.priority) { "HIGH" -> Color(0xFFEF5350); "LOW" -> Color(0xFF4CAF50); else -> Color(0xFFFFC107) }
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .background(Color(0xFFEF5350).copy(alpha = 0.08f))
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Box(Modifier.size(7.dp).clip(CircleShape).background(pc))
                        Text("Complaint · Room ${complaint.roomNumber}", color = Color(0xFFEF5350), style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                        Text(complaint.priority, color = pc, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.SemiBold)
                    }
                }
            }
            Spacer(Modifier.height(20.dp))
            HorizontalDivider(color = DreamlandGold.copy(alpha = 0.12f))
            Spacer(Modifier.height(20.dp))
        }

        // ── Actions ───────────────────────────────────────────────────────────
        Text("ACTIONS", style = MaterialTheme.typography.labelMedium, color = DreamlandGold, letterSpacing = 1.5.sp)
        Spacer(Modifier.height(12.dp))

        if (member.isActive) {
            Button(
                onClick = { vm.openAssignTasks(member) },
                modifier = Modifier.fillMaxWidth().height(42.dp),
                colors = ButtonDefaults.buttonColors(containerColor = DreamlandGold.copy(alpha = 0.18f)),
                shape = RoundedCornerShape(10.dp),
                contentPadding = PaddingValues(horizontal = 12.dp),
                border = BorderStroke(1.dp, DreamlandGold.copy(alpha = 0.5f)),
            ) {
                Text("Assign Task", color = DreamlandGold, fontWeight = FontWeight.SemiBold)
            }
            if (!member.isAvailable) {
                Spacer(Modifier.height(8.dp))
                Button(
                    onClick = { showAvailableConfirm = true },
                    modifier = Modifier.fillMaxWidth().height(42.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF4CAF50).copy(alpha = 0.18f)),
                    shape = RoundedCornerShape(10.dp),
                    contentPadding = PaddingValues(horizontal = 12.dp),
                    border = BorderStroke(1.dp, Color(0xFF4CAF50).copy(alpha = 0.5f)),
                ) {
                    Text("Set Available", color = Color(0xFF4CAF50), fontWeight = FontWeight.SemiBold)
                }
            }
            Spacer(Modifier.height(10.dp))
        }

        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            OutlinedButton(
                onClick = { vm.openEditStaff(member) },
                modifier = Modifier.weight(1f).height(42.dp),
                border = BorderStroke(1.dp, DreamlandGold.copy(alpha = 0.55f)),
                shape = RoundedCornerShape(10.dp),
            ) {
                Text("Edit", color = DreamlandGold, fontWeight = FontWeight.SemiBold)
            }
            OutlinedButton(
                onClick = { vm.toggleActive(member) },
                modifier = Modifier.weight(1f).height(42.dp),
                border = BorderStroke(1.dp, if (member.isActive) Color(0xFFEF5350).copy(alpha = 0.5f) else Color(0xFF4CAF50).copy(alpha = 0.5f)),
                shape = RoundedCornerShape(10.dp),
            ) {
                Text(
                    if (member.isActive) "Deactivate" else "Activate",
                    color = if (member.isActive) Color(0xFFEF5350) else Color(0xFF4CAF50),
                    fontWeight = FontWeight.SemiBold,
                )
            }
        }
    }

    // Set Available confirmation dialog
    if (showAvailableConfirm) {
        val hasTasks = assignedOrders.isNotEmpty() || assignedComplaints.isNotEmpty()
        AlertDialog(
            onDismissRequest = { showAvailableConfirm = false },
            containerColor = DreamlandForestElevated,
            title = { Text("Set ${member.name} Available?", color = DreamlandOnDark, fontWeight = FontWeight.SemiBold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    if (hasTasks) {
                        Text("The following tasks will be marked as completed:", color = DreamlandMuted, style = MaterialTheme.typography.bodySmall)
                        assignedOrders.forEach { order ->
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                Box(Modifier.size(6.dp).clip(CircleShape).background(Color(0xFF7986CB)))
                                Text("Order · Room ${order.roomNumber}", color = Color(0xFF7986CB), style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.SemiBold)
                            }
                        }
                        assignedComplaints.forEach { complaint ->
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                Box(Modifier.size(6.dp).clip(CircleShape).background(Color(0xFFEF5350)))
                                Text("Complaint · Room ${complaint.roomNumber}", color = Color(0xFFEF5350), style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.SemiBold)
                            }
                        }
                    } else {
                        Text("Mark ${member.name} as available?", color = DreamlandMuted, style = MaterialTheme.typography.bodyMedium)
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        showAvailableConfirm = false
                        vm.setAvailableWithCompletion(member, assignedOrders.map { it.id }, assignedComplaints.map { it.id })
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF4CAF50)),
                    shape = RoundedCornerShape(8.dp),
                ) {
                    Text(if (hasTasks) "Complete & Set Available" else "Set Available", color = Color(0xFF0D1F17), fontWeight = FontWeight.SemiBold)
                }
            },
            dismissButton = { TextButton(onClick = { showAvailableConfirm = false }) { Text("Cancel", color = DreamlandMuted) } },
        )
    }
}

@Composable
private fun StaffDetailPlaceholder() {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Select a staff member", color = DreamlandMuted, style = MaterialTheme.typography.titleMedium)
            Text("Choose someone from the list to see their details and manage tasks", color = DreamlandMuted.copy(alpha = 0.6f), style = MaterialTheme.typography.bodySmall)
        }
    }
}

// ── Shared sub-components ─────────────────────────────────────────────────────

@Composable
private fun StaffSummaryChip(count: Int, label: String, color: Color) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(20.dp))
            .background(color.copy(alpha = 0.12f))
            .border(1.dp, color.copy(alpha = 0.3f), RoundedCornerShape(20.dp))
            .padding(horizontal = 8.dp, vertical = 4.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            Box(Modifier.size(6.dp).clip(CircleShape).background(color))
            Text("$count $label", color = color, fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.labelSmall)
        }
    }
}

@Composable
private fun StaffFilterDropdown(
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
            modifier = Modifier.widthIn(min = 130.dp).background(DreamlandForestElevated),
        ) {
            options.forEach { (value, display) ->
                DropdownMenuItem(
                    text = {
                        Text(display, color = if (value == selected) DreamlandGold else DreamlandOnDark, style = MaterialTheme.typography.bodySmall, fontWeight = if (value == selected) FontWeight.SemiBold else FontWeight.Normal)
                    },
                    onClick = { onSelect(value); expanded = false },
                )
            }
        }
    }
}
