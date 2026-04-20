package com.example.dreamland_reception.staff

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.dreamland_reception.DreamlandForestElevated
import com.example.dreamland_reception.DreamlandForestSurface
import com.example.dreamland_reception.DreamlandGold
import com.example.dreamland_reception.DreamlandMuted
import com.example.dreamland_reception.DreamlandOnDark
import com.example.dreamland_reception.data.model.Complaint
import com.example.dreamland_reception.data.model.Order
import com.example.dreamland_reception.ui.viewmodel.AssignTasksDialogState
import com.example.dreamland_reception.ui.viewmodel.StaffViewModel

@Composable
fun AssignTasksDialog(state: AssignTasksDialogState, vm: StaffViewModel) {
    Dialog(
        onDismissRequest = { if (!state.isSaving) vm.closeAssignTasks() },
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth(0.52f)
                .clip(RoundedCornerShape(16.dp))
                .background(DreamlandForestSurface)
                .padding(28.dp),
        ) {
            Column {
                // ── Header ─────────────────────────────────────────────────
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column {
                        Text(
                            "ASSIGN TASKS",
                            style = MaterialTheme.typography.labelLarge,
                            color = DreamlandGold,
                            letterSpacing = 2.sp,
                        )
                        Text(
                            state.staffMember?.name ?: "",
                            style = MaterialTheme.typography.headlineSmall,
                            color = DreamlandOnDark,
                        )
                    }
                    TextButton(onClick = { if (!state.isSaving) vm.closeAssignTasks() }) {
                        Text("Cancel", color = DreamlandMuted)
                    }
                }

                Spacer(Modifier.height(4.dp))

                val selectedCount = state.selectedOrderIds.size + state.selectedComplaintIds.size
                if (selectedCount > 0) {
                    Text(
                        "$selectedCount task${if (selectedCount > 1) "s" else ""} selected",
                        color = DreamlandGold,
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.SemiBold,
                    )
                }

                Spacer(Modifier.height(16.dp))
                HorizontalDivider(color = DreamlandGold.copy(alpha = 0.12f))
                Spacer(Modifier.height(16.dp))

                // ── Body ───────────────────────────────────────────────────
                if (state.isLoading) {
                    Box(
                        modifier = Modifier.fillMaxWidth().height(120.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        CircularProgressIndicator(color = DreamlandGold, strokeWidth = 2.dp, modifier = Modifier.size(28.dp))
                    }
                } else if (state.newOrders.isEmpty() && state.newComplaints.isEmpty()) {
                    Box(
                        modifier = Modifier.fillMaxWidth().height(80.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            "No new orders or complaints to assign",
                            color = DreamlandMuted,
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    }
                } else {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f, fill = false)
                            .verticalScroll(rememberScrollState()),
                    ) {
                        // ── Orders section ─────────────────────────────────
                        if (state.newOrders.isNotEmpty()) {
                            SectionHeader(
                                title = "New Orders",
                                count = state.newOrders.size,
                                color = Color(0xFF7986CB),
                            )
                            Spacer(Modifier.height(8.dp))
                            state.newOrders.forEach { order ->
                                OrderTaskItem(
                                    order = order,
                                    selected = order.id in state.selectedOrderIds,
                                    onToggle = { vm.toggleOrderSelection(order.id) },
                                )
                                Spacer(Modifier.height(6.dp))
                            }
                            if (state.newComplaints.isNotEmpty()) {
                                Spacer(Modifier.height(12.dp))
                            }
                        }

                        // ── Complaints section ─────────────────────────────
                        if (state.newComplaints.isNotEmpty()) {
                            SectionHeader(
                                title = "New Complaints",
                                count = state.newComplaints.size,
                                color = Color(0xFFEF5350),
                            )
                            Spacer(Modifier.height(8.dp))
                            state.newComplaints.forEach { complaint ->
                                ComplaintTaskItem(
                                    complaint = complaint,
                                    selected = complaint.id in state.selectedComplaintIds,
                                    onToggle = { vm.toggleComplaintSelection(complaint.id) },
                                )
                                Spacer(Modifier.height(6.dp))
                            }
                        }
                    }
                }

                // ── Error ──────────────────────────────────────────────────
                if (state.error != null) {
                    Spacer(Modifier.height(8.dp))
                    Text(
                        state.error,
                        color = Color(0xFFEF5350),
                        style = MaterialTheme.typography.bodySmall,
                    )
                }

                Spacer(Modifier.height(20.dp))
                HorizontalDivider(color = DreamlandGold.copy(alpha = 0.12f))
                Spacer(Modifier.height(16.dp))

                // ── Done button ────────────────────────────────────────────
                Button(
                    onClick = { vm.confirmAssignTasks() },
                    enabled = !state.isSaving,
                    modifier = Modifier.fillMaxWidth().height(48.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = DreamlandGold,
                        disabledContainerColor = DreamlandGold.copy(alpha = 0.4f),
                    ),
                    shape = RoundedCornerShape(10.dp),
                ) {
                    if (state.isSaving) {
                        CircularProgressIndicator(
                            color = Color(0xFF0D1F17),
                            strokeWidth = 2.dp,
                            modifier = Modifier.size(20.dp),
                        )
                    } else {
                        Text(
                            if (selectedCount > 0) "Done ($selectedCount)" else "Done",
                            color = Color(0xFF0D1F17),
                            fontWeight = FontWeight.Bold,
                        )
                    }
                }
            }
        }
    }
}

// ── Section header ────────────────────────────────────────────────────────────

@Composable
private fun SectionHeader(title: String, count: Int, color: Color) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Box(
            modifier = Modifier
                .size(8.dp)
                .clip(CircleShape)
                .background(color),
        )
        Text(
            title,
            color = color,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.SemiBold,
            letterSpacing = 0.5.sp,
        )
        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(10.dp))
                .background(color.copy(alpha = 0.15f))
                .padding(horizontal = 7.dp, vertical = 2.dp),
        ) {
            Text(
                "$count",
                color = color,
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold,
            )
        }
    }
}

// ── Order task row ────────────────────────────────────────────────────────────

@Composable
private fun OrderTaskItem(order: Order, selected: Boolean, onToggle: () -> Unit) {
    val borderColor = if (selected) DreamlandGold else DreamlandMuted.copy(alpha = 0.25f)
    val bgColor = if (selected) DreamlandGold.copy(alpha = 0.08f) else DreamlandForestElevated

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(bgColor)
            .border(1.dp, borderColor, RoundedCornerShape(10.dp))
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
            ) { onToggle() }
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        SelectionCircle(selected = selected)

        Column(modifier = Modifier.weight(1f)) {
            Text(
                "Room ${order.roomNumber} — ${order.guestName}",
                color = DreamlandOnDark,
                style = MaterialTheme.typography.bodySmall,
                fontWeight = FontWeight.SemiBold,
            )
            val itemSummary = order.items.take(2).joinToString(", ") { it.name }
                .let { if (order.items.size > 2) "$it +${order.items.size - 2} more" else it }
            if (itemSummary.isNotBlank()) {
                Text(
                    itemSummary,
                    color = DreamlandMuted,
                    style = MaterialTheme.typography.labelSmall,
                )
            }
        }

        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(6.dp))
                .background(Color(0xFF7986CB).copy(alpha = 0.15f))
                .padding(horizontal = 7.dp, vertical = 3.dp),
        ) {
            Text(
                "Order",
                color = Color(0xFF7986CB),
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.SemiBold,
            )
        }
    }
}

// ── Complaint task row ────────────────────────────────────────────────────────

@Composable
private fun ComplaintTaskItem(complaint: Complaint, selected: Boolean, onToggle: () -> Unit) {
    val priorityColor = when (complaint.priority) {
        "HIGH" -> Color(0xFFEF5350)
        "LOW"  -> Color(0xFF4CAF50)
        else   -> Color(0xFFFFC107)
    }
    val borderColor = if (selected) DreamlandGold else DreamlandMuted.copy(alpha = 0.25f)
    val bgColor = if (selected) DreamlandGold.copy(alpha = 0.08f) else DreamlandForestElevated

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(bgColor)
            .border(1.dp, borderColor, RoundedCornerShape(10.dp))
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
            ) { onToggle() }
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        SelectionCircle(selected = selected)

        Column(modifier = Modifier.weight(1f)) {
            Text(
                "Room ${complaint.roomNumber} — ${complaint.guestName}",
                color = DreamlandOnDark,
                style = MaterialTheme.typography.bodySmall,
                fontWeight = FontWeight.SemiBold,
            )
            if (complaint.description.isNotBlank()) {
                Text(
                    complaint.description.take(60).let { if (complaint.description.length > 60) "$it…" else it },
                    color = DreamlandMuted,
                    style = MaterialTheme.typography.labelSmall,
                )
            }
        }

        Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(6.dp))
                    .background(Color(0xFFEF5350).copy(alpha = 0.15f))
                    .padding(horizontal = 7.dp, vertical = 3.dp),
            ) {
                Text(
                    "Complaint",
                    color = Color(0xFFEF5350),
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.SemiBold,
                )
            }
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(6.dp))
                    .background(priorityColor.copy(alpha = 0.15f))
                    .padding(horizontal = 7.dp, vertical = 3.dp),
            ) {
                Text(
                    complaint.priority,
                    color = priorityColor,
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.SemiBold,
                )
            }
        }
    }
}

// ── Selection circle ──────────────────────────────────────────────────────────

@Composable
private fun SelectionCircle(selected: Boolean) {
    Box(
        modifier = Modifier
            .size(20.dp)
            .clip(CircleShape)
            .background(if (selected) DreamlandGold else Color.Transparent)
            .border(
                width = 1.5.dp,
                color = if (selected) DreamlandGold else DreamlandMuted.copy(alpha = 0.5f),
                shape = CircleShape,
            ),
        contentAlignment = Alignment.Center,
    ) {
        if (selected) {
            Text(
                "✓",
                color = Color(0xFF0D1F17),
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold,
            )
        }
    }
}
