package com.example.dreamland_reception.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.SecondaryTabRow
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Tab
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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.example.dreamland_reception.DreamlandForestElevated
import com.example.dreamland_reception.DreamlandForestSurface
import com.example.dreamland_reception.DreamlandGold
import com.example.dreamland_reception.DreamlandMuted
import com.example.dreamland_reception.DreamlandOnDark
import com.example.dreamland_reception.data.model.ComplaintType
import com.example.dreamland_reception.data.model.FoodItem
import com.example.dreamland_reception.data.model.Service
import com.example.dreamland_reception.ui.viewmodel.AddComplaintTypeDialog
import com.example.dreamland_reception.ui.viewmodel.AddFoodDialog
import com.example.dreamland_reception.ui.viewmodel.AddServiceDialog
import com.example.dreamland_reception.ui.viewmodel.SettingsViewModel

private val PANEL_TABS = listOf("Services", "Food Menu", "Issue Types")

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ServicesPanel(
    services: List<Service>,
    foodItems: List<FoodItem>,
    complaintTypes: List<ComplaintType>,
    addServiceDialog: AddServiceDialog,
    addFoodDialog: AddFoodDialog,
    addComplaintTypeDialog: AddComplaintTypeDialog,
    isNoHotelSelected: Boolean,
    vm: SettingsViewModel,
    modifier: Modifier = Modifier,
) {
    var selectedTab by remember { mutableStateOf(0) }

    Column(modifier = modifier) {
        // Tab row
        SecondaryTabRow(
            selectedTabIndex = selectedTab,
            containerColor = DreamlandForestSurface,
            contentColor = DreamlandGold,
        ) {
            PANEL_TABS.forEachIndexed { index, title ->
                Tab(
                    selected = selectedTab == index,
                    onClick = { selectedTab = index },
                    text = {
                        Text(
                            title,
                            style = MaterialTheme.typography.titleSmall,
                            color = if (selectedTab == index) DreamlandGold else DreamlandMuted,
                        )
                    },
                )
            }
        }

        if (isNoHotelSelected) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("Select a hotel to manage its services", color = DreamlandMuted, style = MaterialTheme.typography.bodyMedium)
            }
            return
        }

        when (selectedTab) {
            0 -> ServicesTab(services, addServiceDialog, vm)
            1 -> FoodMenuTab(foodItems, addFoodDialog, vm)
            2 -> IssueTypesTab(complaintTypes, addComplaintTypeDialog, vm)
        }
    }

    // Dialogs
    if (addServiceDialog.show) AddServiceDialogUI(addServiceDialog, vm)
    if (addFoodDialog.show) AddFoodDialogUI(addFoodDialog, vm)
    if (addComplaintTypeDialog.show) AddComplaintTypeDialogUI(addComplaintTypeDialog, vm)
}

// ── Services Tab ──────────────────────────────────────────────────────────────

@Composable
private fun ServicesTab(services: List<Service>, dialog: AddServiceDialog, vm: SettingsViewModel) {
    Column(Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("Additional Services", style = MaterialTheme.typography.titleMedium, color = DreamlandOnDark)
            TextButton(onClick = { vm.openAddService() }) {
                Icon(Icons.Default.Add, contentDescription = null, tint = DreamlandGold, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(4.dp))
                Text("Add Service", color = DreamlandGold)
            }
        }

        if (services.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("No services added yet", color = DreamlandMuted, style = MaterialTheme.typography.bodyMedium)
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(services, key = { it.id }) { service ->
                    ServiceRow(service, vm)
                }
                item { Spacer(Modifier.height(16.dp)) }
            }
        }
    }
}

@Composable
private fun ServiceRow(service: Service, vm: SettingsViewModel) {
    var priceText by remember(service.id) { mutableStateOf(service.price.toBigDecimal().stripTrailingZeros().toPlainString()) }

    Card(
        colors = CardDefaults.cardColors(containerColor = DreamlandForestElevated),
        shape = RoundedCornerShape(10.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(service.name, style = MaterialTheme.typography.bodyMedium, color = DreamlandOnDark)
                Text(
                    if (service.isActive) "Active" else "Inactive",
                    style = MaterialTheme.typography.bodySmall,
                    color = if (service.isActive) Color(0xFF4CAF50) else DreamlandMuted,
                )
            }
            OutlinedTextField(
                value = priceText,
                onValueChange = { v ->
                    priceText = v.filter { it.isDigit() || it == '.' }
                    v.toDoubleOrNull()?.let { vm.updateServicePrice(service.id, it) }
                },
                modifier = Modifier.width(100.dp),
                prefix = { Text("₹", color = DreamlandMuted) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                textStyle = MaterialTheme.typography.bodyMedium.copy(color = DreamlandOnDark),
                colors = fieldColors(),
            )
            Spacer(Modifier.width(8.dp))
            Switch(
                checked = service.isActive,
                onCheckedChange = { vm.toggleService(service.id, it) },
                colors = SwitchDefaults.colors(checkedThumbColor = DreamlandGold, checkedTrackColor = DreamlandGold.copy(alpha = 0.4f)),
            )
            IconButton(onClick = { vm.deleteService(service.id) }) {
                Icon(Icons.Default.Delete, contentDescription = "Delete", tint = DreamlandMuted.copy(alpha = 0.6f), modifier = Modifier.size(18.dp))
            }
        }
    }
}

// ── Food Menu Tab ─────────────────────────────────────────────────────────────

@Composable
private fun FoodMenuTab(foodItems: List<FoodItem>, dialog: AddFoodDialog, vm: SettingsViewModel) {
    Column(Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("Food Menu Items", style = MaterialTheme.typography.titleMedium, color = DreamlandOnDark)
            TextButton(onClick = { vm.openAddFood() }) {
                Icon(Icons.Default.Add, contentDescription = null, tint = DreamlandGold, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(4.dp))
                Text("Add Item", color = DreamlandGold)
            }
        }

        if (foodItems.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("No food items added yet", color = DreamlandMuted, style = MaterialTheme.typography.bodyMedium)
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(foodItems, key = { it.id }) { item ->
                    FoodItemRow(item, vm)
                }
                item { Spacer(Modifier.height(16.dp)) }
            }
        }
    }
}

@Composable
private fun FoodItemRow(item: FoodItem, vm: SettingsViewModel) {
    var priceText by remember(item.id) { mutableStateOf(item.price.toBigDecimal().stripTrailingZeros().toPlainString()) }

    Card(
        colors = CardDefaults.cardColors(containerColor = DreamlandForestElevated),
        shape = RoundedCornerShape(10.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(item.name, style = MaterialTheme.typography.bodyMedium, color = DreamlandOnDark)
                if (item.category.isNotBlank()) {
                    Text(item.category, style = MaterialTheme.typography.bodySmall, color = DreamlandMuted)
                }
            }
            OutlinedTextField(
                value = priceText,
                onValueChange = { v ->
                    priceText = v.filter { it.isDigit() || it == '.' }
                    v.toDoubleOrNull()?.let { vm.updateFoodPrice(item.id, it) }
                },
                modifier = Modifier.width(100.dp),
                prefix = { Text("₹", color = DreamlandMuted) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                textStyle = MaterialTheme.typography.bodyMedium.copy(color = DreamlandOnDark),
                colors = fieldColors(),
            )
            Spacer(Modifier.width(8.dp))
            Switch(
                checked = item.isAvailable,
                onCheckedChange = { vm.toggleFoodItem(item.id, it) },
                colors = SwitchDefaults.colors(checkedThumbColor = DreamlandGold, checkedTrackColor = DreamlandGold.copy(alpha = 0.4f)),
            )
            IconButton(onClick = { vm.deleteFoodItem(item.id) }) {
                Icon(Icons.Default.Delete, contentDescription = "Delete", tint = DreamlandMuted.copy(alpha = 0.6f), modifier = Modifier.size(18.dp))
            }
        }
    }
}

// ── Issue Types Tab ───────────────────────────────────────────────────────────

@Composable
private fun IssueTypesTab(complaintTypes: List<ComplaintType>, dialog: AddComplaintTypeDialog, vm: SettingsViewModel) {
    Column(Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("Issue / Complaint Types", style = MaterialTheme.typography.titleMedium, color = DreamlandOnDark)
            TextButton(onClick = { vm.openAddComplaintType() }) {
                Icon(Icons.Default.Add, contentDescription = null, tint = DreamlandGold, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(4.dp))
                Text("Add Type", color = DreamlandGold)
            }
        }

        if (complaintTypes.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("No issue types added yet", color = DreamlandMuted, style = MaterialTheme.typography.bodyMedium)
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(complaintTypes, key = { it.id }) { ct ->
                    ComplaintTypeRow(ct, vm)
                }
                item { Spacer(Modifier.height(16.dp)) }
            }
        }
    }
}

@Composable
private fun ComplaintTypeRow(ct: ComplaintType, vm: SettingsViewModel) {
    Card(
        colors = CardDefaults.cardColors(containerColor = DreamlandForestElevated),
        shape = RoundedCornerShape(10.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(ct.name, style = MaterialTheme.typography.bodyMedium, color = DreamlandOnDark)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TypeChip(ct.type)
                    if (ct.description.isNotBlank()) {
                        Text(ct.description, style = MaterialTheme.typography.bodySmall, color = DreamlandMuted, maxLines = 1)
                    }
                }
            }
            Switch(
                checked = ct.isActive,
                onCheckedChange = { vm.toggleComplaintType(ct.id, it) },
                colors = SwitchDefaults.colors(checkedThumbColor = DreamlandGold, checkedTrackColor = DreamlandGold.copy(alpha = 0.4f)),
            )
            IconButton(onClick = { vm.deleteComplaintType(ct.id) }) {
                Icon(Icons.Default.Delete, contentDescription = "Delete", tint = DreamlandMuted.copy(alpha = 0.6f), modifier = Modifier.size(18.dp))
            }
        }
    }
}

@Composable
private fun TypeChip(type: String) {
    val color = when (type) {
        "MAINTENANCE" -> Color(0xFFFF9800)
        "HOUSEKEEPING" -> Color(0xFF2196F3)
        "FOOD" -> Color(0xFF4CAF50)
        "NOISE" -> Color(0xFF9C27B0)
        "BILLING" -> Color(0xFFE91E63)
        else -> DreamlandMuted
    }
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(4.dp))
            .background(color.copy(alpha = 0.15f))
            .padding(horizontal = 6.dp, vertical = 2.dp),
    ) {
        Text(type, style = MaterialTheme.typography.labelMedium, color = color)
    }
}

// ── Add Dialogs ───────────────────────────────────────────────────────────────

@Composable
internal fun AddServiceDialogUI(state: AddServiceDialog, vm: SettingsViewModel) {
    AlertDialog(
        onDismissRequest = { vm.closeAddService() },
        title = { Text("Add Service", style = MaterialTheme.typography.titleLarge, color = DreamlandOnDark) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = state.name,
                    onValueChange = vm::onAddServiceName,
                    label = { Text("Service Name") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    colors = fieldColors(),
                )
                OutlinedTextField(
                    value = state.price,
                    onValueChange = vm::onAddServicePrice,
                    label = { Text("Price (₹)") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    colors = fieldColors(),
                )
            }
        },
        confirmButton = {
            Button(
                onClick = { vm.submitAddService() },
                enabled = !state.isSaving && state.name.isNotBlank(),
                colors = ButtonDefaults.buttonColors(containerColor = DreamlandGold, contentColor = Color(0xFF081C15)),
            ) { Text(if (state.isSaving) "Saving…" else "Add") }
        },
        dismissButton = {
            TextButton(onClick = { vm.closeAddService() }) { Text("Cancel", color = DreamlandMuted) }
        },
        containerColor = DreamlandForestSurface,
    )
}

@Composable
internal fun AddFoodDialogUI(state: AddFoodDialog, vm: SettingsViewModel) {
    var categoryExpanded by remember { mutableStateOf(false) }
    val allCategories = listOf("Breakfast", "Lunch", "Dinner", "Snacks", "Beverages", "Desserts", "Other")
    val selectedLabel = when {
        state.categories.isEmpty() -> ""
        state.categories.size == 1 -> state.categories.first()
        else -> "${state.categories.first()} +${state.categories.size - 1} more"
    }

    AlertDialog(
        onDismissRequest = { vm.closeAddFood() },
        title = { Text("Add Food Item", style = MaterialTheme.typography.titleLarge, color = DreamlandOnDark) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = state.name,
                    onValueChange = vm::onAddFoodName,
                    label = { Text("Item Name") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    colors = fieldColors(),
                )
                OutlinedTextField(
                    value = state.price,
                    onValueChange = vm::onAddFoodPrice,
                    label = { Text("Price (₹)") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    colors = fieldColors(),
                )
                // Multi-select category dropdown
                Box {
                    OutlinedTextField(
                        value = selectedLabel,
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("Category (select multiple)") },
                        trailingIcon = {
                            Text(
                                if (categoryExpanded) "▲" else "▼",
                                color = DreamlandGold,
                                style = MaterialTheme.typography.labelSmall,
                                modifier = Modifier.padding(end = 12.dp),
                            )
                        },
                        modifier = Modifier.fillMaxWidth(),
                        colors = fieldColors(),
                    )
                    // Invisible click target over the field to open/close dropdown
                    Box(
                        modifier = Modifier
                            .matchParentSize()
                            .clickable(
                                interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() },
                                indication = null,
                            ) { categoryExpanded = !categoryExpanded },
                    )
                    DropdownMenu(
                        expanded = categoryExpanded,
                        onDismissRequest = { categoryExpanded = false },
                        modifier = Modifier.fillMaxWidth(0.7f).background(DreamlandForestElevated),
                    ) {
                        allCategories.forEach { cat ->
                            val checked = cat in state.categories
                            DropdownMenuItem(
                                text = {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    ) {
                                        Checkbox(
                                            checked = checked,
                                            onCheckedChange = null,
                                            colors = CheckboxDefaults.colors(
                                                checkedColor = DreamlandGold,
                                                uncheckedColor = DreamlandMuted,
                                                checkmarkColor = DreamlandForestSurface,
                                            ),
                                        )
                                        Text(
                                            cat,
                                            color = if (checked) DreamlandGold else DreamlandOnDark,
                                            style = MaterialTheme.typography.bodyMedium,
                                        )
                                    }
                                },
                                onClick = { vm.onAddFoodCategory(cat) },
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = { vm.submitAddFood() },
                enabled = !state.isSaving && state.name.isNotBlank(),
                colors = ButtonDefaults.buttonColors(containerColor = DreamlandGold, contentColor = Color(0xFF081C15)),
            ) { Text(if (state.isSaving) "Saving…" else "Add") }
        },
        dismissButton = {
            TextButton(onClick = { vm.closeAddFood() }) { Text("Cancel", color = DreamlandMuted) }
        },
        containerColor = DreamlandForestSurface,
    )
}

@Composable
internal fun AddComplaintTypeDialogUI(state: AddComplaintTypeDialog, vm: SettingsViewModel) {
    AlertDialog(
        onDismissRequest = { vm.closeAddComplaintType() },
        title = { Text("Add Issue Type", style = MaterialTheme.typography.titleLarge, color = DreamlandOnDark) },
        text = {
            OutlinedTextField(
                value = state.name,
                onValueChange = vm::onAddComplaintTypeName,
                label = { Text("Name") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                colors = fieldColors(),
            )
        },
        confirmButton = {
            Button(
                onClick = { vm.submitAddComplaintType() },
                enabled = !state.isSaving && state.name.isNotBlank(),
                colors = ButtonDefaults.buttonColors(containerColor = DreamlandGold, contentColor = Color(0xFF081C15)),
            ) { Text(if (state.isSaving) "Saving…" else "Add") }
        },
        dismissButton = {
            TextButton(onClick = { vm.closeAddComplaintType() }) { Text("Cancel", color = DreamlandMuted) }
        },
        containerColor = DreamlandForestSurface,
    )
}

// ── Shared ────────────────────────────────────────────────────────────────────

@Composable
private fun fieldColors() = OutlinedTextFieldDefaults.colors(
    focusedBorderColor = DreamlandGold,
    unfocusedBorderColor = DreamlandMuted.copy(alpha = 0.4f),
    focusedLabelColor = DreamlandGold,
    unfocusedLabelColor = DreamlandMuted,
    cursorColor = DreamlandGold,
    focusedTextColor = DreamlandOnDark,
    unfocusedTextColor = DreamlandOnDark,
)
