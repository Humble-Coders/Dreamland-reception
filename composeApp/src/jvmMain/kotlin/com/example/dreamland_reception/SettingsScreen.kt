package com.example.dreamland_reception

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.TextButton
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.VerticalDivider
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.dreamland_reception.data.model.Hotel
import com.example.dreamland_reception.settings.ServicesPanel
import com.example.dreamland_reception.ui.viewmodel.SettingsViewModel

@Composable
fun SettingsScreen(vm: SettingsViewModel = DreamlandAppInitializer.getSettingsViewModel()) {
    val state by vm.state.collectAsStateWithLifecycle()

    Row(Modifier.fillMaxSize()) {
        // ── LEFT: read-only hotel info ─────────────────────────────────────────
        Column(
            modifier = Modifier
                .widthIn(min = 340.dp, max = 420.dp)
                .fillMaxHeight()
                .verticalScroll(rememberScrollState())
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            // Header
            Text(
                "SETTINGS",
                style = MaterialTheme.typography.labelLarge,
                color = DreamlandGold,
                letterSpacing = 2.sp,
            )
            Text("Hotel Configuration", style = MaterialTheme.typography.headlineSmall, color = DreamlandOnDark)

            // Firebase status
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Box(
                    Modifier.size(8.dp).clip(CircleShape)
                        .background(if (state.firebaseConnected) Color(0xFF4CAF50) else Color(0xFFE53935)),
                )
                Text(
                    if (state.firebaseConnected) "Firebase · ${state.projectId}" else "Firebase not connected",
                    style = MaterialTheme.typography.bodySmall,
                    color = DreamlandMuted,
                )
            }

            // Hotel Selector
            HotelSelectorCard(state.hotels, state.selectedHotelId, state.isLoadingHotels, vm)

            if (state.selectedHotelId.isNotBlank()) {
                if (state.isLoadingData) {
                    Box(Modifier.fillMaxWidth().height(80.dp), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(color = DreamlandGold, modifier = Modifier.size(28.dp))
                    }
                } else {
                    val hotel = state.selectedHotel
                    if (hotel != null) {
                        CollapsibleInfoCard("General") {
                            InfoRow("Hotel Name", hotel.name)
                            InfoRow("Address", hotel.address)
                            InfoRow("City", hotel.city)
                            InfoRow("Country", hotel.country)
                            InfoRow("Contact Phone", hotel.contactPhone)
                            InfoRow("Currency", hotel.currency)
                            InfoRow("Contact / Notes", hotel.contactInfo)
                        }

//                        CollapsibleInfoCard("Billing") {
//                            InfoBoolRow("Tax Enabled", hotel.taxEnabled)
//                            InfoRow("Tax %", "${hotel.taxPercentage}%")
//                            InfoRow("Discount Type", hotel.defaultDiscountType)
//                            InfoRow("Default Discount", hotel.defaultDiscountValue.toString())
//                        }

                        CollapsibleInfoCard("Room Rules") {
                            InfoRow("Check-In Time", hotel.checkInTime)
                            InfoRow("Check-Out Time", hotel.checkOutTime)
                            InfoBoolRow("Auto-Assign Room", hotel.autoAssignRoom)
                            InfoBoolRow("Early Check-In", hotel.earlyCheckInAllowed)
                            if (hotel.earlyCheckInAllowed) {
                                InfoRow("Early Check-In Charge", "₹${hotel.earlyCheckInPrice}")
                            }
                            InfoBoolRow("Late Check-Out", hotel.lateCheckOutAllowed)
                            if (hotel.lateCheckOutAllowed) {
                                InfoRow("Late Check-Out Charge", "₹${hotel.lateCheckOutPrice}")
                            }
                        }

//                        CollapsibleInfoCard("Room Options") {
//                            InfoBoolRow("Breakfast Included", hotel.breakfastEnabled)
//                            if (hotel.breakfastEnabled) {
//                                InfoRow("Breakfast Price / Person", "₹${hotel.breakfastPricePerPerson}")
//                            }
//                            InfoBoolRow("Early Check-In", hotel.earlyCheckInEnabled)
//                            if (hotel.earlyCheckInEnabled) {
//                                InfoRow("Early Check-In Charge", "₹${hotel.earlyCheckInCharge}")
//                            }
//                            InfoBoolRow("Late Check-Out", hotel.lateCheckOutEnabled)
//                            if (hotel.lateCheckOutEnabled) {
//                                InfoRow("Late Check-Out Charge", "₹${hotel.lateCheckOutCharge}")
//                            }
//                        }
                    }

                    // ── Email Settings (local, not in Firestore) ──────────────
                    Card(
                        colors = CardDefaults.cardColors(containerColor = DreamlandForestElevated),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            Text("EMAIL SETTINGS", style = MaterialTheme.typography.labelSmall, color = DreamlandGold, letterSpacing = 2.sp)
                            OutlinedTextField(
                                value = state.senderEmail,
                                onValueChange = vm::onSenderEmailChanged,
                                label = { Text("Invoice Sender Email") },
                                placeholder = { Text("noreply@yourdomain.com") },
                                modifier = Modifier.fillMaxWidth(),
                                singleLine = true,
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedTextColor = DreamlandOnDark,
                                    unfocusedTextColor = DreamlandOnDark,
                                    focusedBorderColor = DreamlandGold,
                                    unfocusedBorderColor = DreamlandMuted.copy(alpha = 0.4f),
                                    focusedLabelColor = DreamlandGold,
                                    unfocusedLabelColor = DreamlandMuted,
                                    cursorColor = DreamlandOnDark,
                                ),
                            )
                            Text(
                                "Stored locally on this device only — not saved to Firestore.",
                                style = MaterialTheme.typography.labelSmall,
                                color = DreamlandMuted,
                            )
                        }
                    }

                    // ── GRC Form Template (printed at check-in) ───────────────
                    Card(
                        colors = CardDefaults.cardColors(containerColor = DreamlandForestElevated),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            Text("GRC FORM TEMPLATE", style = MaterialTheme.typography.labelSmall, color = DreamlandGold, letterSpacing = 2.sp)
                            Text(
                                "HTML printed as the Guest Registration Card at check-in. Use placeholders like " +
                                    "{{guestName}}, {{idNumber}}, {{roomNumber}}, {{roomCategory}}, {{checkIn}}, {{checkOut}}, " +
                                    "{{hotelName}}, {{logoImg}} and {{idImages}}.",
                                style = MaterialTheme.typography.labelSmall,
                                color = DreamlandMuted,
                            )
                            OutlinedTextField(
                                value = state.grcLogoDraft,
                                onValueChange = vm::onGrcLogoChanged,
                                label = { Text("Logo URL (shown in GRC header)") },
                                placeholder = { Text("https://…/logo.png") },
                                modifier = Modifier.fillMaxWidth(),
                                singleLine = true,
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedTextColor = DreamlandOnDark,
                                    unfocusedTextColor = DreamlandOnDark,
                                    focusedBorderColor = DreamlandGold,
                                    unfocusedBorderColor = DreamlandMuted.copy(alpha = 0.4f),
                                    focusedLabelColor = DreamlandGold,
                                    unfocusedLabelColor = DreamlandMuted,
                                    cursorColor = DreamlandOnDark,
                                ),
                            )
                            OutlinedTextField(
                                value = state.grcTemplateDraft,
                                onValueChange = vm::onGrcTemplateChanged,
                                label = { Text("GRC HTML template (blank = built-in default)") },
                                modifier = Modifier.fillMaxWidth().height(260.dp),
                                textStyle = MaterialTheme.typography.bodySmall.copy(fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace),
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedTextColor = DreamlandOnDark,
                                    unfocusedTextColor = DreamlandOnDark,
                                    focusedBorderColor = DreamlandGold,
                                    unfocusedBorderColor = DreamlandMuted.copy(alpha = 0.4f),
                                    focusedLabelColor = DreamlandGold,
                                    unfocusedLabelColor = DreamlandMuted,
                                    cursorColor = DreamlandOnDark,
                                ),
                            )
                            Row(
                                Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                TextButton(onClick = vm::resetGrcTemplateToDefault) {
                                    Text("Reset to default", color = DreamlandMuted)
                                }
                                Spacer(Modifier.weight(1f))
                                if (state.grcSaved) {
                                    Text("Saved ✓", color = Color(0xFF4CAF50), style = MaterialTheme.typography.labelMedium)
                                    Spacer(Modifier.width(8.dp))
                                }
                                Button(
                                    onClick = vm::saveGrcTemplate,
                                    enabled = !state.grcSaving,
                                    colors = ButtonDefaults.buttonColors(containerColor = DreamlandGold),
                                    shape = RoundedCornerShape(10.dp),
                                ) {
                                    if (state.grcSaving) {
                                        CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp, color = Color(0xFF0D1F17))
                                    } else {
                                        Text("Save Template", color = Color(0xFF0D1F17), fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold)
                                    }
                                }
                            }
                        }
                    }
                }

                if (state.error != null) {
                    Text(state.error!!, color = Color(0xFFE53935), style = MaterialTheme.typography.bodySmall)
                }
            } else if (!state.isLoadingHotels) {
                Text(
                    "Select a hotel above to view its configuration.",
                    color = DreamlandMuted,
                    style = MaterialTheme.typography.bodyMedium,
                )
            }

            Spacer(Modifier.height(24.dp))
        }

        // ── Divider ────────────────────────────────────────────────────────────
        VerticalDivider(color = DreamlandGold.copy(alpha = 0.15f))

        // ── RIGHT: services panel ──────────────────────────────────────────────
        ServicesPanel(
            services = state.services,
            foodItems = state.foodItems,
            complaintTypes = state.complaintTypes,
            addServiceDialog = state.addServiceDialog,
            addFoodDialog = state.addFoodDialog,
            addComplaintTypeDialog = state.addComplaintTypeDialog,
            isNoHotelSelected = state.selectedHotelId.isBlank(),
            vm = vm,
            modifier = Modifier.weight(1f).fillMaxHeight(),
        )
    }
}

// ── Hotel Selector ────────────────────────────────────────────────────────────

@Composable
private fun HotelSelectorCard(
    hotels: List<Hotel>,
    selectedId: String,
    isLoading: Boolean,
    vm: SettingsViewModel,
) {
    var expanded by remember { mutableStateOf(false) }
    val selectedName = hotels.find { it.id == selectedId }?.name ?: "Select Hotel"

    Card(
        colors = CardDefaults.cardColors(containerColor = DreamlandForestSurface),
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("HOTEL", style = MaterialTheme.typography.labelLarge, color = DreamlandGold)

            Box {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .border(1.dp, DreamlandGold.copy(alpha = 0.35f), RoundedCornerShape(8.dp))
                        .clickable { if (!isLoading) expanded = true }
                        .padding(horizontal = 14.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    if (isLoading) {
                        CircularProgressIndicator(color = DreamlandGold, modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                        Spacer(Modifier.width(8.dp))
                        Text("Loading hotels…", color = DreamlandMuted, style = MaterialTheme.typography.bodyMedium)
                    } else {
                        Text(
                            selectedName,
                            style = MaterialTheme.typography.bodyMedium,
                            color = if (selectedId.isBlank()) DreamlandMuted else DreamlandOnDark,
                            modifier = Modifier.weight(1f),
                        )
                        Icon(Icons.Default.KeyboardArrowDown, contentDescription = null, tint = DreamlandMuted, modifier = Modifier.size(18.dp))
                    }
                }

                DropdownMenu(
                    expanded = expanded,
                    onDismissRequest = { expanded = false },
                    containerColor = DreamlandForestElevated,
                ) {
                    if (hotels.isEmpty()) {
                        DropdownMenuItem(
                            text = { Text("No hotels found", color = DreamlandMuted) },
                            onClick = { expanded = false },
                        )
                    } else {
                        hotels.forEach { hotel ->
                            DropdownMenuItem(
                                text = {
                                    Column {
                                        Text(hotel.name, color = DreamlandOnDark, style = MaterialTheme.typography.bodyMedium)
                                        if (hotel.city.isNotBlank()) {
                                            Text(hotel.city, color = DreamlandMuted, style = MaterialTheme.typography.bodySmall)
                                        }
                                    }
                                },
                                onClick = { vm.selectHotel(hotel.id); expanded = false },
                                trailingIcon = if (hotel.id == selectedId) {
                                    { Icon(Icons.Default.Check, contentDescription = null, tint = DreamlandGold, modifier = Modifier.size(16.dp)) }
                                } else null,
                            )
                        }
                    }
                }
            }

            if (selectedId.isNotBlank()) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Box(Modifier.size(6.dp).clip(CircleShape).background(Color(0xFF4CAF50)))
                    Text("Active hotel", style = MaterialTheme.typography.bodySmall, color = DreamlandMuted)
                }
            }
        }
    }
}

// ── Collapsible info card ─────────────────────────────────────────────────────

@Composable
private fun CollapsibleInfoCard(title: String, content: @Composable () -> Unit) {
    var expanded by remember { mutableStateOf(true) }

    Card(
        colors = CardDefaults.cardColors(containerColor = DreamlandForestSurface),
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column {
            // Header — click anywhere to toggle
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { expanded = !expanded }
                    .padding(horizontal = 16.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    title.uppercase(),
                    style = MaterialTheme.typography.labelLarge,
                    color = DreamlandGold,
                    modifier = Modifier.weight(1f),
                )
                Icon(
                    imageVector = if (expanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                    contentDescription = if (expanded) "Collapse" else "Expand",
                    tint = DreamlandMuted,
                    modifier = Modifier.size(20.dp),
                )
            }

            AnimatedVisibility(
                visible = expanded,
                enter = expandVertically(),
                exit = shrinkVertically(),
            ) {
                Column(
                    modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 14.dp),
                    verticalArrangement = Arrangement.spacedBy(0.dp),
                ) {
                    content()
                }
            }
        }
    }
}

// ── Read-only row components ──────────────────────────────────────────────────

@Composable
private fun InfoRow(label: String, value: String) {
    Column(modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
        Text(label, style = MaterialTheme.typography.bodySmall, color = DreamlandMuted)
        Text(
            value.ifBlank { "—" },
            style = MaterialTheme.typography.bodyMedium,
            color = DreamlandOnDark,
        )
        HorizontalDivider(
            modifier = Modifier.padding(top = 6.dp),
            color = DreamlandGold.copy(alpha = 0.08f),
        )
    }
}

@Composable
private fun InfoBoolRow(label: String, value: Boolean) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, style = MaterialTheme.typography.bodyMedium, color = DreamlandOnDark)
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            Box(
                Modifier.size(7.dp).clip(CircleShape)
                    .background(if (value) Color(0xFF4CAF50) else DreamlandMuted.copy(alpha = 0.5f)),
            )
            Text(
                if (value) "Yes" else "No",
                style = MaterialTheme.typography.bodySmall,
                color = if (value) Color(0xFF4CAF50) else DreamlandMuted,
            )
        }
    }
    HorizontalDivider(color = DreamlandGold.copy(alpha = 0.08f))
}
