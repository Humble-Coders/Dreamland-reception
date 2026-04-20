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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
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
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
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
import com.example.dreamland_reception.ui.viewmodel.StaffFormDialogState
import com.example.dreamland_reception.ui.viewmodel.StaffViewModel

private val roles = listOf("HOUSEKEEPING", "MAINTENANCE", "RECEPTION")

private fun roleColor(role: String) = when (role) {
    "HOUSEKEEPING" -> Color(0xFF4DB6AC)
    "MAINTENANCE"  -> Color(0xFFFF8A65)
    "RECEPTION"    -> Color(0xFF7986CB)
    else           -> Color(0xFF8FA69E)
}

private fun roleLabel(role: String) = when (role) {
    "HOUSEKEEPING" -> "Housekeeping"
    "MAINTENANCE"  -> "Maintenance"
    "RECEPTION"    -> "Reception"
    else           -> role.lowercase().replaceFirstChar { it.uppercase() }
}

@Composable
fun AddEditStaffDialog(state: StaffFormDialogState, vm: StaffViewModel) {
    Dialog(
        onDismissRequest = { if (!state.isSaving) vm.closeForm() },
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth(0.40f)
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
                            if (state.isEditing) "EDIT STAFF" else "ADD STAFF",
                            style = MaterialTheme.typography.labelLarge,
                            color = DreamlandGold,
                            letterSpacing = 2.sp,
                        )
                        Text(
                            if (state.isEditing) "Update team member" else "Add a new team member",
                            style = MaterialTheme.typography.headlineSmall,
                            color = DreamlandOnDark,
                        )
                    }
                    TextButton(onClick = { if (!state.isSaving) vm.closeForm() }) {
                        Text("Cancel", color = DreamlandMuted)
                    }
                }

                Spacer(Modifier.height(24.dp))

                // ── Name ───────────────────────────────────────────────────
                FormLabel("Full Name")
                Spacer(Modifier.height(6.dp))
                OutlinedTextField(
                    value = state.name,
                    onValueChange = { vm.onFormName(it) },
                    placeholder = { Text("e.g. Amit Kumar", color = DreamlandMuted, fontSize = 13.sp) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    colors = dreamlandTextFieldColors(),
                    shape = RoundedCornerShape(10.dp),
                )

                Spacer(Modifier.height(16.dp))

                // ── Phone ──────────────────────────────────────────────────
                FormLabel("Phone Number")
                Spacer(Modifier.height(6.dp))
                OutlinedTextField(
                    value = state.phone,
                    onValueChange = { vm.onFormPhone(it.filter { c -> c.isDigit() || c == '+' }) },
                    placeholder = { Text("e.g. 9876543210", color = DreamlandMuted, fontSize = 13.sp) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    colors = dreamlandTextFieldColors(),
                    shape = RoundedCornerShape(10.dp),
                )

                Spacer(Modifier.height(16.dp))

                // ── Role ───────────────────────────────────────────────────
                FormLabel("Role")
                Spacer(Modifier.height(6.dp))
                RoleSelector(
                    selected = state.role,
                    onSelect = { vm.onFormRole(it) },
                )

                Spacer(Modifier.height(20.dp))
                HorizontalDivider(color = DreamlandGold.copy(alpha = 0.12f))
                Spacer(Modifier.height(16.dp))

                // ── Active toggle ──────────────────────────────────────────
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column {
                        Text(
                            "Active",
                            color = DreamlandOnDark,
                            fontWeight = FontWeight.SemiBold,
                            style = MaterialTheme.typography.bodyMedium,
                        )
                        Text(
                            if (state.isActive) "Staff member is active" else "Staff member is inactive",
                            color = DreamlandMuted,
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                    Switch(
                        checked = state.isActive,
                        onCheckedChange = { vm.onFormActive(it) },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = DreamlandGold,
                            checkedTrackColor = DreamlandGold.copy(alpha = 0.4f),
                            uncheckedThumbColor = DreamlandMuted,
                            uncheckedTrackColor = DreamlandMuted.copy(alpha = 0.3f),
                        ),
                    )
                }

                // ── Error ──────────────────────────────────────────────────
                if (state.error != null) {
                    Spacer(Modifier.height(12.dp))
                    Text(
                        state.error,
                        color = Color(0xFFEF5350),
                        style = MaterialTheme.typography.bodySmall,
                    )
                }

                Spacer(Modifier.height(24.dp))

                // ── Submit ─────────────────────────────────────────────────
                Button(
                    onClick = { vm.submitForm() },
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
                            if (state.isEditing) "Save Changes" else "Add Staff",
                            color = Color(0xFF0D1F17),
                            fontWeight = FontWeight.Bold,
                        )
                    }
                }
            }
        }
    }
}

// ── Role selector ─────────────────────────────────────────────────────────────

private const val ADD_NEW_ROLE_SENTINEL = "__ADD_NEW__"

@Composable
private fun RoleSelector(selected: String, onSelect: (String) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    var addingNew by remember { mutableStateOf(false) }
    var customRole by remember { mutableStateOf("") }
    val focusRequester = remember { FocusRequester() }

    // If the selected role is not in the built-in list, it's a custom role
    val isCustom = selected.isNotBlank() && selected !in roles
    val color = roleColor(selected)

    Column {
        Box {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(10.dp))
                    .border(1.dp, color.copy(alpha = 0.5f), RoundedCornerShape(10.dp))
                    .background(DreamlandForestElevated)
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                    ) { expanded = true }
                    .padding(horizontal = 14.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(10.dp)
                            .clip(CircleShape)
                            .background(color),
                    )
                    Spacer(Modifier.width(10.dp))
                    Text(
                        roleLabel(selected),
                        color = DreamlandOnDark,
                        fontWeight = FontWeight.Medium,
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
                Text("▾", color = DreamlandGold, fontSize = 12.sp)
            }
            DropdownMenu(
                expanded = expanded,
                onDismissRequest = { expanded = false },
                modifier = Modifier.background(DreamlandForestElevated).fillMaxWidth(0.35f),
            ) {
                roles.forEach { role ->
                    val rc = roleColor(role)
                    DropdownMenuItem(
                        text = {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(
                                    modifier = Modifier
                                        .size(9.dp)
                                        .clip(CircleShape)
                                        .background(rc),
                                )
                                Spacer(Modifier.width(10.dp))
                                Text(
                                    roleLabel(role),
                                    color = if (role == selected) DreamlandGold else DreamlandOnDark,
                                    fontWeight = if (role == selected) FontWeight.SemiBold else FontWeight.Normal,
                                    style = MaterialTheme.typography.bodySmall,
                                )
                            }
                        },
                        onClick = {
                            addingNew = false
                            onSelect(role)
                            expanded = false
                        },
                    )
                }
                HorizontalDivider(color = DreamlandGold.copy(alpha = 0.12f))
                DropdownMenuItem(
                    text = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                "+ Add New Role",
                                color = DreamlandGold,
                                fontWeight = FontWeight.SemiBold,
                                style = MaterialTheme.typography.bodySmall,
                            )
                        }
                    },
                    onClick = {
                        expanded = false
                        addingNew = true
                        customRole = if (isCustom) selected else ""
                    },
                )
            }
        }

        // ── Custom role text field ─────────────────────────────────────────
        if (addingNew) {
            Spacer(Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                OutlinedTextField(
                    value = customRole,
                    onValueChange = { customRole = it.uppercase() },
                    placeholder = { Text("e.g. SECURITY", color = DreamlandMuted, fontSize = 12.sp) },
                    singleLine = true,
                    modifier = Modifier
                        .weight(1f)
                        .heightIn(max = 52.dp)
                        .focusRequester(focusRequester),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = DreamlandOnDark,
                        unfocusedTextColor = DreamlandOnDark,
                        focusedBorderColor = DreamlandGold,
                        unfocusedBorderColor = DreamlandMuted.copy(alpha = 0.35f),
                        cursorColor = DreamlandGold,
                        focusedContainerColor = DreamlandForestElevated,
                        unfocusedContainerColor = DreamlandForestElevated,
                    ),
                    shape = RoundedCornerShape(8.dp),
                )
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(DreamlandGold)
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                        ) {
                            val trimmed = customRole.trim()
                            if (trimmed.isNotBlank()) {
                                onSelect(trimmed)
                                addingNew = false
                            }
                        }
                        .padding(horizontal = 14.dp, vertical = 10.dp),
                ) {
                    Text(
                        "Add",
                        color = Color(0xFF0D1F17),
                        fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .border(1.dp, DreamlandMuted.copy(alpha = 0.3f), RoundedCornerShape(8.dp))
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                        ) { addingNew = false }
                        .padding(horizontal = 14.dp, vertical = 10.dp),
                ) {
                    Text(
                        "Cancel",
                        color = DreamlandMuted,
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }
        }
    }
}

// ── Helpers ───────────────────────────────────────────────────────────────────

@Composable
private fun FormLabel(text: String) {
    Text(
        text,
        color = DreamlandMuted,
        style = MaterialTheme.typography.labelLarge,
        letterSpacing = 0.8.sp,
    )
}

@Composable
private fun dreamlandTextFieldColors() = OutlinedTextFieldDefaults.colors(
    focusedTextColor = DreamlandOnDark,
    unfocusedTextColor = DreamlandOnDark,
    focusedBorderColor = DreamlandGold,
    unfocusedBorderColor = DreamlandMuted.copy(alpha = 0.35f),
    cursorColor = DreamlandGold,
    focusedContainerColor = DreamlandForestElevated,
    unfocusedContainerColor = DreamlandForestElevated,
)
