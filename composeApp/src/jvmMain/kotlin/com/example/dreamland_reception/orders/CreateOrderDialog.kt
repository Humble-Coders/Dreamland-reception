package com.example.dreamland_reception.orders

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
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
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.PopupProperties
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.dreamland_reception.DreamlandForestElevated
import com.example.dreamland_reception.DreamlandForestSurface
import com.example.dreamland_reception.DreamlandGold
import com.example.dreamland_reception.DreamlandMuted
import com.example.dreamland_reception.DreamlandOnDark
import com.example.dreamland_reception.DreamlandAppInitializer
import com.example.dreamland_reception.settings.AddFoodDialogUI
import com.example.dreamland_reception.settings.AddServiceDialogUI
import com.example.dreamland_reception.stays.DreamlandTextField
import com.example.dreamland_reception.stays.SectionLabel
import com.example.dreamland_reception.ui.viewmodel.CatalogItem
import com.example.dreamland_reception.ui.viewmodel.CreateOrderDialogState
import com.example.dreamland_reception.ui.viewmodel.OrdersViewModel
import com.example.dreamland_reception.ui.viewmodel.SettingsViewModel

@Composable
fun CreateOrderDialog(
    state: CreateOrderDialogState,
    vm: OrdersViewModel,
    settingsVm: SettingsViewModel = DreamlandAppInitializer.getSettingsViewModel(),
) {
    val settingsState by settingsVm.state.collectAsStateWithLifecycle()

    // Refresh catalog when food/service add dialogs close after saving
    var wasFoodDialogOpen by remember { mutableStateOf(false) }
    var wasServiceDialogOpen by remember { mutableStateOf(false) }
    LaunchedEffect(settingsState.addFoodDialog.show) {
        if (wasFoodDialogOpen && !settingsState.addFoodDialog.show) vm.refreshCreateOrderCatalog()
        wasFoodDialogOpen = settingsState.addFoodDialog.show
    }
    LaunchedEffect(settingsState.addServiceDialog.show) {
        if (wasServiceDialogOpen && !settingsState.addServiceDialog.show) vm.refreshCreateOrderCatalog()
        wasServiceDialogOpen = settingsState.addServiceDialog.show
    }

    Dialog(
        onDismissRequest = { if (!state.isSaving) vm.closeCreateOrder() },
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
                        Text(
                            "NEW ORDER",
                            style = MaterialTheme.typography.labelLarge,
                            color = DreamlandGold,
                            letterSpacing = 2.sp,
                        )
                        Text(
                            "Create Service Request",
                            style = MaterialTheme.typography.headlineSmall,
                            color = DreamlandOnDark,
                        )
                    }
                    TextButton(onClick = { vm.closeCreateOrder() }) {
                        Text("Cancel", color = DreamlandMuted)
                    }
                }

                Spacer(Modifier.height(20.dp))

                // ── Guest Stay autocomplete ────────────────────────────────
                SectionLabel("Guest Stay *")
                Spacer(Modifier.height(8.dp))
                if (state.isLoadingStays) {
                    CircularProgressIndicator(
                        modifier = Modifier.height(32.dp).width(32.dp),
                        color = DreamlandGold,
                        strokeWidth = 2.dp,
                    )
                } else {
                    StayAutocompleteField(
                        query = state.stayQuery,
                        isSelected = state.selectedStayId.isNotBlank(),
                        suggestions = state.filteredStays.map { "Room ${it.roomNumber} — ${it.guestName}" to it.id },
                        onQueryChange = { vm.onCreateOrderStayQuery(it) },
                        onSelect = { vm.onCreateOrderStaySelected(it) },
                    )
                }

                Spacer(Modifier.height(20.dp))

                // ── Items ──────────────────────────────────────────────────
                SectionLabel("Items")
                Spacer(Modifier.height(8.dp))

                state.items.forEachIndexed { index, item ->
                    // Compact inline row: item name · qty stepper · price · delete.
                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        // Combined food + service combobox — opens on focus, filters as you type.
                        OrderItemNameField(
                            value = item.name,
                            catalog = state.catalogItems,
                            onValueChange = { vm.onCreateOrderItemName(index, it) },
                            onSelect = { vm.selectCreateOrderItemSuggestion(index, it) },
                            onAddNew = { typedName -> settingsVm.openAddFood(typedName) },
                            modifier = Modifier.weight(2.2f),
                        )
                        // Qty stepper
                        Row(
                            modifier = Modifier
                                .weight(1.2f)
                                .height(56.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .border(1.dp, DreamlandMuted.copy(alpha = 0.4f), RoundedCornerShape(8.dp))
                                .background(DreamlandForestSurface),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            TextButton(onClick = { vm.onCreateOrderItemQty(index, item.quantity - 1) }) {
                                Text("-", color = DreamlandGold, fontWeight = FontWeight.Bold, fontSize = 18.sp)
                            }
                            Text("${item.quantity}", color = DreamlandOnDark, fontWeight = FontWeight.SemiBold)
                            TextButton(onClick = { vm.onCreateOrderItemQty(index, item.quantity + 1) }) {
                                Text("+", color = DreamlandGold, fontWeight = FontWeight.Bold, fontSize = 18.sp)
                            }
                        }
                        DreamlandTextField(
                            modifier = Modifier.weight(1.2f),
                            value = item.price,
                            onValueChange = { vm.onCreateOrderItemPrice(index, it) },
                            label = "Price (₹)",
                            keyboardType = KeyboardType.Number,
                        )
                        IconButton(
                            onClick = { vm.removeCreateOrderItem(index) },
                            enabled = state.items.size > 1,
                        ) {
                            Icon(
                                Icons.Filled.Delete,
                                contentDescription = "Remove item",
                                tint = Color(0xFFEF5350).copy(alpha = if (state.items.size > 1) 0.9f else 0.3f),
                                modifier = Modifier.size(18.dp),
                            )
                        }
                    }
                    Spacer(Modifier.height(10.dp))
                }

                TextButton(onClick = { vm.addCreateOrderItem() }) {
                    Text("+ Add Item", color = DreamlandGold, fontWeight = FontWeight.SemiBold)
                }

                // ── Total preview ──────────────────────────────────────────
                val total = state.items.sumOf { (it.price.toDoubleOrNull() ?: 0.0) * it.quantity }
                if (total > 0) {
                    Spacer(Modifier.height(8.dp))
                    HorizontalDivider(color = DreamlandGold.copy(alpha = 0.2f))
                    Spacer(Modifier.height(8.dp))
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("Total", color = DreamlandMuted, style = MaterialTheme.typography.bodyMedium)
                        Text(
                            "₹${total.toLong()}",
                            color = DreamlandGold,
                            fontWeight = FontWeight.Bold,
                            fontSize = 16.sp,
                        )
                    }
                }

                Spacer(Modifier.height(16.dp))

                // ── Notes ──────────────────────────────────────────────────
                OutlinedTextField(
                    value = state.notes,
                    onValueChange = vm::onCreateOrderNotes,
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
                    onClick = { vm.submitCreateOrder() },
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

// ── Stay autocomplete field ───────────────────────────────────────────────────

@Composable
private fun StayAutocompleteField(
    query: String,
    isSelected: Boolean,
    suggestions: List<Pair<String, String>>,   // display to stayId
    onQueryChange: (String) -> Unit,
    onSelect: (String) -> Unit,
) {
    val density = LocalDensity.current
    val focusManager = LocalFocusManager.current
    var fieldWidthDp by remember { mutableStateOf(0.dp) }
    var focused by remember { mutableStateOf(false) }
    // Open as soon as the field is focused (even when empty), filter as the user types,
    // and close when focus leaves.
    val showDropdown = focused && suggestions.isNotEmpty()

    Box {
        OutlinedTextField(
            value = query,
            onValueChange = onQueryChange,
            label = { Text("Search by room or guest name…", color = DreamlandMuted, fontSize = 13.sp) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
                .onSizeChanged { fieldWidthDp = with(density) { it.width.toDp() } }
                .onFocusChanged { focused = it.isFocused },
            colors = OutlinedTextFieldDefaults.colors(
                focusedTextColor = DreamlandOnDark,
                unfocusedTextColor = DreamlandOnDark,
                focusedBorderColor = if (isSelected) DreamlandGold else DreamlandGold,
                unfocusedBorderColor = if (isSelected) DreamlandGold else DreamlandMuted.copy(alpha = 0.4f),
                cursorColor = DreamlandGold,
            ),
            trailingIcon = if (isSelected) {
                {
                    Text(
                        "✓",
                        color = Color(0xFF4CAF50),
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(end = 8.dp),
                    )
                }
            } else null,
        )
        DropdownMenu(
            expanded = showDropdown,
            onDismissRequest = { /* let user keep typing */ },
            properties = PopupProperties(focusable = false),
            modifier = Modifier
                .width(fieldWidthDp)
                .heightIn(max = 260.dp)
                .background(DreamlandForestElevated),
        ) {
            if (suggestions.isEmpty()) {
                DropdownMenuItem(
                    text = { Text("No active stays found", color = DreamlandMuted) },
                    onClick = {},
                )
            }
            suggestions.forEachIndexed { i, (display, id) ->
                DropdownMenuItem(
                    text = {
                        Text(
                            display,
                            color = DreamlandOnDark,
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    },
                    onClick = { onSelect(id); focusManager.clearFocus() },
                    modifier = Modifier.fillMaxWidth(),
                )
                if (i < suggestions.lastIndex) {
                    HorizontalDivider(color = DreamlandMuted.copy(alpha = 0.1f))
                }
            }
        }
    }
}

// ── Item name combobox (combined food + services) ─────────────────────────────

@Composable
private fun OrderItemNameField(
    value: String,
    catalog: List<CatalogItem>,
    onValueChange: (String) -> Unit,
    onSelect: (CatalogItem) -> Unit,
    onAddNew: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val density = LocalDensity.current
    val focusManager = LocalFocusManager.current
    var fieldWidthDp by remember { mutableStateOf(0.dp) }
    var focused by remember { mutableStateOf(false) }

    val trimmed = value.trim()
    // Combined catalog (food + services), available only, filtered by the typed name (all when blank).
    val matches = catalog.filter {
        it.isAvailable && (trimmed.isBlank() || it.name.contains(trimmed, ignoreCase = true))
    }
    val showAddNew = trimmed.isNotBlank() && catalog.none { it.name.equals(trimmed, ignoreCase = true) }
    val showDropdown = focused && (matches.isNotEmpty() || showAddNew)

    Box(modifier = modifier) {
        OutlinedTextField(
            value = value,
            onValueChange = onValueChange,
            label = { Text("Item Name *", color = DreamlandMuted, fontSize = 13.sp) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
                .onSizeChanged { fieldWidthDp = with(density) { it.width.toDp() } }
                .onFocusChanged { focused = it.isFocused },
            colors = OutlinedTextFieldDefaults.colors(
                focusedTextColor = DreamlandOnDark,
                unfocusedTextColor = DreamlandOnDark,
                focusedBorderColor = DreamlandGold,
                unfocusedBorderColor = DreamlandMuted.copy(alpha = 0.4f),
                cursorColor = DreamlandGold,
            ),
        )
        DropdownMenu(
            expanded = showDropdown,
            onDismissRequest = { /* closes on focus loss */ },
            properties = PopupProperties(focusable = false),
            modifier = Modifier
                .width(fieldWidthDp)
                .heightIn(max = 260.dp)
                .background(DreamlandForestElevated),
        ) {
            matches.forEachIndexed { i, item ->
                DropdownMenuItem(
                    text = {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(item.name, color = DreamlandOnDark, style = MaterialTheme.typography.bodyMedium)
                            if (item.category.isNotBlank()) {
                                Text(item.category, color = DreamlandMuted, fontSize = 10.sp)
                            }
                        }
                    },
                    onClick = { onSelect(item); focusManager.clearFocus() },
                    modifier = Modifier.fillMaxWidth(),
                )
                if (i < matches.lastIndex) HorizontalDivider(color = DreamlandMuted.copy(alpha = 0.1f))
            }
            if (showAddNew) {
                if (matches.isNotEmpty()) HorizontalDivider(color = DreamlandMuted.copy(alpha = 0.1f))
                DropdownMenuItem(
                    text = { Text("Add \"$trimmed\" as new item", color = DreamlandGold, fontWeight = FontWeight.SemiBold) },
                    onClick = { onAddNew(trimmed); focusManager.clearFocus() },
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }
}
