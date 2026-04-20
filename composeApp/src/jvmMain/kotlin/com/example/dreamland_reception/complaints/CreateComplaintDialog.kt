package com.example.dreamland_reception.complaints

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.PopupProperties
import com.example.dreamland_reception.DreamlandAppInitializer
import com.example.dreamland_reception.DreamlandForestElevated
import com.example.dreamland_reception.DreamlandForestSurface
import com.example.dreamland_reception.DreamlandGold
import com.example.dreamland_reception.DreamlandMuted
import com.example.dreamland_reception.DreamlandOnDark
import com.example.dreamland_reception.data.model.Stay
import com.example.dreamland_reception.settings.AddComplaintTypeDialogUI
import com.example.dreamland_reception.ui.viewmodel.ComplaintsViewModel
import com.example.dreamland_reception.ui.viewmodel.CreateComplaintDialogState

private val COMPLAINT_PRIORITIES = listOf(
    "LOW" to "Low",
    "MEDIUM" to "Medium",
    "HIGH" to "High",
)

private val PRIORITY_COLORS = mapOf(
    "LOW" to Color(0xFF90A4AE),
    "MEDIUM" to Color(0xFFFFC107),
    "HIGH" to Color(0xFFEF5350),
)

@Composable
fun CreateComplaintDialog(state: CreateComplaintDialogState, vm: ComplaintsViewModel) {
    val settingsVm = DreamlandAppInitializer.getSettingsViewModel()
    val settingsState by settingsVm.state.collectAsState()

    // When AddComplaintTypeDialog closes, refresh complaint types
    LaunchedEffect(Unit) {
        snapshotFlow { settingsState.addComplaintTypeDialog.show }
            .collect { isOpen ->
                if (!isOpen) vm.refreshCreateComplaintTypes()
            }
    }

    Dialog(
        onDismissRequest = { if (!state.isSaving) vm.closeCreateComplaint() },
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth(0.58f)
                .heightIn(max = 700.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(DreamlandForestSurface)
                .padding(24.dp),
        ) {
            Column(modifier = Modifier.verticalScroll(rememberScrollState())) {

                // ── Header ─────────────────────────────────────────────────
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column {
                        Text(
                            "ADD COMPLAINT",
                            style = MaterialTheme.typography.labelLarge,
                            color = DreamlandGold,
                            letterSpacing = 2.sp,
                        )
                        Text(
                            "Log Guest Issue",
                            style = MaterialTheme.typography.headlineSmall,
                            color = DreamlandOnDark,
                        )
                    }
                    TextButton(onClick = { vm.closeCreateComplaint() }) {
                        Text("Cancel", color = DreamlandMuted)
                    }
                }

                Spacer(Modifier.height(20.dp))

                // ── Guest Stay ─────────────────────────────────────────────
                SectionLabel("Guest Stay *")
                Spacer(Modifier.height(8.dp))
                StayAutocompleteField(
                    query = state.stayQuery,
                    selectedId = state.selectedStayId,
                    filteredStays = state.filteredStays,
                    isLoading = state.isLoadingStays,
                    onQueryChange = { vm.onCreateStayQuery(it) },
                    onStaySelected = { vm.onCreateStaySelected(it) },
                )

                Spacer(Modifier.height(20.dp))

                // ── Category ───────────────────────────────────────────────
                SectionLabel("Category")
                Spacer(Modifier.height(8.dp))
                if (state.isLoadingTypes) {
                    CircularProgressIndicator(
                        color = DreamlandGold,
                        strokeWidth = 2.dp,
                        modifier = Modifier.size(24.dp),
                    )
                } else {
                    Row(
                        modifier = Modifier.horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        state.complaintTypes.forEach { ct ->
                            val isSelected = state.category == ct.name
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(8.dp))
                                    .border(
                                        1.dp,
                                        if (isSelected) DreamlandGold else DreamlandMuted.copy(alpha = 0.3f),
                                        RoundedCornerShape(8.dp),
                                    )
                                    .background(if (isSelected) DreamlandGold.copy(alpha = 0.15f) else DreamlandForestElevated)
                                    .clickable(
                                        interactionSource = remember { MutableInteractionSource() },
                                        indication = null,
                                    ) { vm.onCreateCategory(ct.name) }
                                    .padding(horizontal = 14.dp, vertical = 8.dp),
                                contentAlignment = Alignment.Center,
                            ) {
                                Text(
                                    ct.name,
                                    color = if (isSelected) DreamlandGold else DreamlandMuted,
                                    fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
                                    style = MaterialTheme.typography.bodySmall,
                                )
                            }
                        }
                        // ── "+ Add" chip ───────────────────────────────────
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .border(1.dp, DreamlandGold.copy(alpha = 0.4f), RoundedCornerShape(8.dp))
                                .background(Color.Transparent)
                                .clickable(
                                    interactionSource = remember { MutableInteractionSource() },
                                    indication = null,
                                ) { settingsVm.openAddComplaintType() }
                                .padding(horizontal = 14.dp, vertical = 8.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                "+ Add",
                                color = DreamlandGold,
                                fontWeight = FontWeight.Normal,
                                style = MaterialTheme.typography.bodySmall,
                            )
                        }
                    }
                }

                Spacer(Modifier.height(20.dp))

                // ── Priority ───────────────────────────────────────────────
                SectionLabel("Priority")
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    COMPLAINT_PRIORITIES.forEach { (value, label) ->
                        val selected = state.priority == value
                        val accent = PRIORITY_COLORS[value] ?: DreamlandMuted
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .border(1.dp, if (selected) accent else DreamlandMuted.copy(alpha = 0.3f), RoundedCornerShape(8.dp))
                                .background(if (selected) accent.copy(alpha = 0.15f) else DreamlandForestElevated)
                                .clickable(
                                    interactionSource = remember { MutableInteractionSource() },
                                    indication = null,
                                ) { vm.onCreatePriority(value) }
                                .padding(horizontal = 14.dp, vertical = 8.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                label,
                                color = if (selected) accent else DreamlandMuted,
                                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                                style = MaterialTheme.typography.bodySmall,
                            )
                        }
                    }
                }

                Spacer(Modifier.height(20.dp))

                // ── Description ────────────────────────────────────────────
                SectionLabel("Description")
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = state.description,
                    onValueChange = { vm.onCreateDescription(it) },
                    label = { Text("Describe the issue *", color = DreamlandMuted, fontSize = 13.sp) },
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 3,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = DreamlandOnDark,
                        unfocusedTextColor = DreamlandOnDark,
                        focusedBorderColor = DreamlandGold,
                        unfocusedBorderColor = DreamlandMuted.copy(alpha = 0.4f),
                        cursorColor = DreamlandGold,
                    ),
                )

                Spacer(Modifier.height(16.dp))

                if (state.error != null) {
                    Text(state.error, color = Color(0xFFEF5350), style = MaterialTheme.typography.bodySmall)
                    Spacer(Modifier.height(8.dp))
                }

                Button(
                    onClick = { vm.submitCreateComplaint() },
                    enabled = !state.isSaving,
                    modifier = Modifier.fillMaxWidth().height(52.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = DreamlandGold,
                        disabledContainerColor = DreamlandGold.copy(alpha = 0.4f),
                    ),
                    shape = RoundedCornerShape(10.dp),
                ) {
                    Text(
                        if (state.isSaving) "Saving..." else "Submit Complaint",
                        color = Color(0xFF0D1F17),
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 16.sp,
                    )
                }
            }
        }
    }

    // Show AddComplaintTypeDialog inline on top of this dialog
    if (settingsState.addComplaintTypeDialog.show) {
        AddComplaintTypeDialogUI(state = settingsState.addComplaintTypeDialog, vm = settingsVm)
    }
}

// ── Stay autocomplete ─────────────────────────────────────────────────────────

@Composable
private fun StayAutocompleteField(
    query: String,
    selectedId: String,
    filteredStays: List<Stay>,
    isLoading: Boolean,
    onQueryChange: (String) -> Unit,
    onStaySelected: (String) -> Unit,
) {
    val isSelected = selectedId.isNotBlank()
    var showDropdown by remember { mutableStateOf(false) }

    Box {
        OutlinedTextField(
            value = query,
            onValueChange = {
                onQueryChange(it)
                showDropdown = it.isNotBlank()
            },
            placeholder = { Text("Type room number or guest name…", color = DreamlandMuted, fontSize = 13.sp) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
            colors = OutlinedTextFieldDefaults.colors(
                focusedTextColor = DreamlandOnDark,
                unfocusedTextColor = DreamlandOnDark,
                focusedBorderColor = if (isSelected) Color(0xFF4CAF50) else DreamlandGold,
                unfocusedBorderColor = if (isSelected) Color(0xFF4CAF50).copy(alpha = 0.7f) else DreamlandMuted.copy(alpha = 0.4f),
                cursorColor = DreamlandGold,
            ),
            trailingIcon = if (isSelected) {
                { Text("✓", color = Color(0xFF4CAF50), fontWeight = FontWeight.Bold, modifier = Modifier.padding(end = 8.dp)) }
            } else null,
        )

        if (isLoading && query.isBlank()) {
            CircularProgressIndicator(
                color = DreamlandGold,
                strokeWidth = 2.dp,
                modifier = Modifier.size(20.dp).align(Alignment.CenterEnd).padding(end = 12.dp),
            )
        } else if (showDropdown && filteredStays.isNotEmpty()) {
            DropdownMenu(
                expanded = showDropdown,
                onDismissRequest = { showDropdown = false },
                properties = PopupProperties(focusable = false),
                modifier = Modifier.widthIn(min = 300.dp).heightIn(max = 260.dp).background(DreamlandForestElevated),
            ) {
                filteredStays.forEach { stay ->
                    DropdownMenuItem(
                        text = {
                            Column {
                                Text(
                                    "Room ${stay.roomNumber} — ${stay.guestName}",
                                    color = DreamlandOnDark,
                                    fontWeight = FontWeight.SemiBold,
                                    style = MaterialTheme.typography.bodySmall,
                                )
                                if (stay.roomCategoryName.isNotBlank()) {
                                    Text(stay.roomCategoryName, color = DreamlandMuted, style = MaterialTheme.typography.labelSmall)
                                }
                            }
                        },
                        onClick = {
                            onStaySelected(stay.id)
                            showDropdown = false
                        },
                    )
                }
            }
        }
    }
}

// ── Section label ─────────────────────────────────────────────────────────────

@Composable
private fun SectionLabel(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.labelLarge,
        color = DreamlandGold,
        letterSpacing = 0.5.sp,
    )
}
