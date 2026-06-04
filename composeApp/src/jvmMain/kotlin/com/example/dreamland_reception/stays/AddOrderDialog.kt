package com.example.dreamland_reception.stays

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
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.ui.window.PopupProperties
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.dreamland_reception.DreamlandAppInitializer
import com.example.dreamland_reception.DreamlandForestElevated
import com.example.dreamland_reception.DreamlandForestSurface
import com.example.dreamland_reception.DreamlandGold
import com.example.dreamland_reception.DreamlandMuted
import com.example.dreamland_reception.DreamlandOnDark
import com.example.dreamland_reception.settings.AddFoodDialogUI
import com.example.dreamland_reception.settings.AddServiceDialogUI
import com.example.dreamland_reception.ui.viewmodel.AddOrderState
import com.example.dreamland_reception.ui.viewmodel.CatalogItem
import com.example.dreamland_reception.ui.viewmodel.SettingsViewModel
import com.example.dreamland_reception.ui.viewmodel.StaysViewModel

@Composable
fun AddOrderDialog(
    state: AddOrderState,
    vm: StaysViewModel,
    settingsVm: SettingsViewModel = DreamlandAppInitializer.getSettingsViewModel(),
) {
    val settingsState by settingsVm.state.collectAsStateWithLifecycle()
    val listState by vm.listState.collectAsStateWithLifecycle()
    val selectedStay = listState.stays.find { it.id == listState.selectedStayId }

    // Refresh catalog when food/service add dialogs close after saving
    var wasFoodDialogOpen by remember { mutableStateOf(false) }
    var wasServiceDialogOpen by remember { mutableStateOf(false) }
    LaunchedEffect(settingsState.addFoodDialog.show) {
        if (wasFoodDialogOpen && !settingsState.addFoodDialog.show) vm.refreshAddOrderCatalog()
        wasFoodDialogOpen = settingsState.addFoodDialog.show
    }
    LaunchedEffect(settingsState.addServiceDialog.show) {
        if (wasServiceDialogOpen && !settingsState.addServiceDialog.show) vm.refreshAddOrderCatalog()
        wasServiceDialogOpen = settingsState.addServiceDialog.show
    }

    Dialog(
        onDismissRequest = { if (!state.isSaving) vm.closeAddOrder() },
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth(0.65f)
                .heightIn(max = 800.dp)
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
                        Text("NEW ORDER", style = MaterialTheme.typography.labelLarge, color = DreamlandGold, letterSpacing = 2.sp)
                        Text("Create Service Request", style = MaterialTheme.typography.headlineSmall, color = DreamlandOnDark)
                    }
                    TextButton(onClick = { vm.closeAddOrder() }) {
                        Text("Cancel", color = DreamlandMuted)
                    }
                }

                Spacer(Modifier.height(16.dp))

                // ── Pre-filled room/stay display ───────────────────────────
                if (selectedStay != null) {
                    SectionLabel("Guest Stay")
                    Spacer(Modifier.height(6.dp))
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .border(1.dp, DreamlandGold.copy(alpha = 0.5f), RoundedCornerShape(8.dp))
                            .background(DreamlandForestElevated)
                            .padding(horizontal = 14.dp, vertical = 12.dp),
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text("Room ${selectedStay.roomNumber}", color = DreamlandGold, fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.bodyMedium)
                            Text("—", color = DreamlandMuted, style = MaterialTheme.typography.bodySmall)
                            Text(selectedStay.guestName, color = DreamlandOnDark, style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                }

                Spacer(Modifier.height(16.dp))

                // ── Items ──────────────────────────────────────────────────
                SectionLabel("Items")
                Spacer(Modifier.height(8.dp))

                state.items.forEachIndexed { index, item ->
                    var itemFieldFocused by remember { mutableStateOf(false) }
                    // Show all catalog items on focus, filter by name when typing
                    val dropdownSuggestions = if (item.name.isBlank()) state.catalogItems
                        else state.catalogItems.filter { it.name.contains(item.name, ignoreCase = true) }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        // Searchable item name (all items shown on focus, filters as you type)
                        AutocompleteItemField(
                            value = item.name,
                            suggestions = dropdownSuggestions,
                            showSuggestions = itemFieldFocused,
                            onValueChange = { vm.onAddOrderItemName(index, it) },
                            onSuggestionSelected = { vm.selectOrderItemSuggestion(index, it); itemFieldFocused = false },
                            onDismiss = { itemFieldFocused = false },
                            modifier = Modifier.weight(1f).onFocusChanged { itemFieldFocused = it.isFocused },
                            allCatalogNames = state.catalogItems.mapTo(mutableSetOf()) { it.name },
                            onToggleItemAvailability = { catalogItem ->
                                if (catalogItem.category == "Services") settingsVm.toggleService(catalogItem.id, true)
                                else settingsVm.toggleFoodItem(catalogItem.id, true)
                                vm.refreshAddOrderCatalog()
                            },
                        )
                        // Qty stepper (compact)
                        Row(
                            modifier = Modifier
                                .width(112.dp)
                                .height(56.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .border(1.dp, DreamlandMuted.copy(alpha = 0.4f), RoundedCornerShape(8.dp)),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Box(
                                Modifier.width(36.dp).fillMaxHeight().clickable { vm.onAddOrderItemQty(index, item.quantity - 1) },
                                contentAlignment = Alignment.Center,
                            ) { Icon(Icons.Filled.Remove, contentDescription = "Decrease", tint = DreamlandGold, modifier = Modifier.size(16.dp)) }
                            Text("${item.quantity}", color = DreamlandOnDark, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f), textAlign = androidx.compose.ui.text.style.TextAlign.Center)
                            Box(
                                Modifier.width(36.dp).fillMaxHeight().clickable { vm.onAddOrderItemQty(index, item.quantity + 1) },
                                contentAlignment = Alignment.Center,
                            ) { Icon(Icons.Filled.Add, contentDescription = "Increase", tint = DreamlandGold, modifier = Modifier.size(16.dp)) }
                        }
                        // Price
                        DreamlandTextField(
                            modifier = Modifier.width(116.dp),
                            value = item.price,
                            onValueChange = { vm.onAddOrderItemPrice(index, it) },
                            label = "Price (₹)",
                            keyboardType = KeyboardType.Number,
                        )
                        // Remove (fixed 36dp slot keeps every row aligned)
                        Box(Modifier.size(36.dp), contentAlignment = Alignment.Center) {
                            if (state.items.size > 1) {
                                Box(
                                    Modifier.size(36.dp).clip(RoundedCornerShape(8.dp)).clickable { vm.removeOrderItem(index) },
                                    contentAlignment = Alignment.Center,
                                ) { Icon(Icons.Filled.Delete, contentDescription = "Remove", tint = Color(0xFFEF5350).copy(alpha = 0.85f), modifier = Modifier.size(18.dp)) }
                            }
                        }
                    }
                    Spacer(Modifier.height(10.dp))
                }

                TextButton(onClick = { vm.addOrderItem() }, contentPadding = PaddingValues(horizontal = 4.dp, vertical = 4.dp)) {
                    Icon(Icons.Filled.Add, contentDescription = null, tint = DreamlandGold, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("Add Item", color = DreamlandGold, fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                }

                // ── Total preview ──────────────────────────────────────────
                val total = state.items.sumOf { (it.price.toDoubleOrNull() ?: 0.0) * it.quantity }
                if (total > 0) {
                    Spacer(Modifier.height(8.dp))
                    HorizontalDivider(color = DreamlandGold.copy(alpha = 0.2f))
                    Spacer(Modifier.height(8.dp))
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("Total", color = DreamlandMuted, style = MaterialTheme.typography.bodyMedium)
                        Text("₹${total.toLong()}", color = DreamlandGold, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                    }
                }

                Spacer(Modifier.height(16.dp))

                // ── Notes ──────────────────────────────────────────────────
                OutlinedTextField(
                    value = state.notes,
                    onValueChange = vm::onAddOrderNotes,
                    label = { Text("Notes / Instructions", color = DreamlandMuted, fontSize = 13.sp) },
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 2,
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
                    onClick = { vm.submitAddOrder() },
                    enabled = !state.isSaving,
                    modifier = Modifier.fillMaxWidth().height(52.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = DreamlandGold),
                    shape = RoundedCornerShape(10.dp),
                ) {
                    Text(
                        if (state.isSaving) "Saving..." else "Place Order",
                        color = Color(0xFF0D1F17),
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 16.sp,
                    )
                }
            }
        }
    }

    // Settings add-item dialogs shown inline over this dialog
    if (settingsState.addFoodDialog.show) AddFoodDialogUI(settingsState.addFoodDialog, settingsVm)
    if (settingsState.addServiceDialog.show) AddServiceDialogUI(settingsState.addServiceDialog, settingsVm)
}

// ── Autocomplete item name field ──────────────────────────────────────────────

@Composable
internal fun AutocompleteItemField(
    value: String,
    suggestions: List<CatalogItem>,
    showSuggestions: Boolean,
    onValueChange: (String) -> Unit,
    onSuggestionSelected: (CatalogItem) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    onAddNew: ((String) -> Unit)? = null,
    addNewLabel: String = "Add as new item",
    // Full catalog (all categories) used for exact-match check so "Add new" disappears as soon
    // as the item is saved, regardless of category filtering on suggestions.
    allCatalogNames: Set<String> = emptySet(),
    onToggleItemAvailability: ((CatalogItem) -> Unit)? = null,
) {
    val density = LocalDensity.current
    var fieldWidthDp by remember { mutableStateOf(0.dp) }

    // Show add-new only when the typed value doesn't exist anywhere in the full catalog
    val trimmed = value.trim()
    val showAddNew = onAddNew != null && trimmed.isNotBlank() &&
        allCatalogNames.none { it.equals(trimmed, ignoreCase = true) } &&
        suggestions.none { it.name.equals(trimmed, ignoreCase = true) }
    val dropdownVisible = (showSuggestions && suggestions.isNotEmpty()) || showAddNew

    Box(modifier = modifier) {
        OutlinedTextField(
            value = value,
            onValueChange = onValueChange,
            label = { Text("Item Name *", color = DreamlandMuted, fontSize = 13.sp) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth().onSizeChanged {
                fieldWidthDp = with(density) { it.width.toDp() }
            },
            colors = OutlinedTextFieldDefaults.colors(
                focusedTextColor = DreamlandOnDark,
                unfocusedTextColor = DreamlandOnDark,
                focusedBorderColor = DreamlandGold,
                unfocusedBorderColor = DreamlandMuted.copy(alpha = 0.4f),
                cursorColor = DreamlandGold,
            ),
        )
        DropdownMenu(
            expanded = dropdownVisible,
            onDismissRequest = onDismiss,
            properties = PopupProperties(focusable = false),
            modifier = Modifier
                .width(fieldWidthDp)
                .heightIn(max = 260.dp)
                .background(DreamlandForestElevated),
        ) {
            suggestions.forEachIndexed { i, suggestion ->
                if (!suggestion.isAvailable) {
                    DropdownMenuItem(
                        text = {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        suggestion.name,
                                        color = DreamlandMuted,
                                        style = MaterialTheme.typography.bodyMedium,
                                    )
                                    Text(
                                        "Inactive",
                                        color = Color(0xFFFFA726),
                                        fontSize = 10.sp,
                                    )
                                }
                                Switch(
                                    checked = false,
                                    onCheckedChange = { onToggleItemAvailability?.invoke(suggestion) },
                                    colors = SwitchDefaults.colors(
                                        uncheckedThumbColor = DreamlandMuted,
                                        uncheckedTrackColor = DreamlandMuted.copy(alpha = 0.3f),
                                    ),
                                )
                            }
                        },
                        onClick = {},
                        enabled = false,
                        modifier = Modifier.fillMaxWidth(),
                    )
                } else {
                    DropdownMenuItem(
                        text = {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text(
                                    suggestion.name,
                                    color = DreamlandOnDark,
                                    style = MaterialTheme.typography.bodyMedium,
                                    modifier = Modifier.weight(1f),
                                )
                                if (suggestion.price > 0) {
                                    Text(
                                        "₹${suggestion.price.toLong()}",
                                        color = DreamlandGold,
                                        style = MaterialTheme.typography.bodySmall,
                                        fontWeight = FontWeight.SemiBold,
                                    )
                                }
                            }
                        },
                        onClick = { onSuggestionSelected(suggestion) },
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                if (i < suggestions.lastIndex || showAddNew) {
                    HorizontalDivider(color = DreamlandMuted.copy(alpha = 0.1f))
                }
            }
            // "Add new" entry at the bottom
            if (showAddNew) {
                DropdownMenuItem(
                    text = {
                        Text(
                            "➕ $addNewLabel: \"${value.trim()}\"",
                            color = DreamlandGold,
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.SemiBold,
                        )
                    },
                    onClick = {
                        onDismiss()
                        onAddNew(value.trim())
                    },
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }
}

// ── Category chip row (wrapping) ──────────────────────────────────────────────

@Composable
internal fun FlowRow(
    categories: List<String>,
    selected: String,
    onSelect: (String) -> Unit,
) {
    // Simple wrapping row — split into rows of ~4 chips each
    val rows = categories.chunked(4)
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        rows.forEach { rowItems ->
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                rowItems.forEach { category ->
                    val isSelected = selected == category
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
                            ) { onSelect(category) }
                            .padding(horizontal = 14.dp, vertical = 8.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            category.replaceFirstChar { it.uppercase() },
                            color = if (isSelected) DreamlandGold else DreamlandMuted,
                            fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                }
            }
        }
    }
}
