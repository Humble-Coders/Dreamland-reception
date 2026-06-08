package com.example.dreamland_reception.stays

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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.dreamland_reception.DreamlandForestElevated
import com.example.dreamland_reception.DreamlandForestSurface
import com.example.dreamland_reception.DreamlandGold
import com.example.dreamland_reception.DreamlandMuted
import com.example.dreamland_reception.DreamlandOnDark
import com.example.dreamland_reception.ui.viewmodel.AddStayChargeState
import com.example.dreamland_reception.ui.viewmodel.StaysViewModel

/**
 * Adds an ad-hoc taxable item and/or an advance to an active stay. Either section is optional;
 * at least one must be filled. Item GST defaults to 5%. The advance updates the stay's running
 * advance (what the bill reads) and posts to the ledger immediately.
 */
@Composable
fun AddStayChargeDialog(state: AddStayChargeState, vm: StaysViewModel) {
    if (!state.isOpen) return
    val qty = state.quantity.toIntOrNull()?.coerceAtLeast(1) ?: 1
    val price = state.unitPrice.toDoubleOrNull() ?: 0.0
    val lineTotal = price * qty
    val advance = state.advanceAmount.toDoubleOrNull() ?: 0.0

    Dialog(onDismissRequest = vm::closeAddStayCharge, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Box(
            modifier = Modifier
                .fillMaxWidth(0.40f)
                .clip(RoundedCornerShape(16.dp))
                .background(DreamlandForestSurface)
                .padding(24.dp),
        ) {
            Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Column {
                        Text("ADD CHARGE", style = MaterialTheme.typography.labelLarge, color = DreamlandGold, letterSpacing = 2.sp)
                        Text("Room ${state.roomNumber}  ·  ${state.guestName}", color = DreamlandMuted, style = MaterialTheme.typography.bodySmall)
                    }
                    TextButton(onClick = vm::closeAddStayCharge) { Text("Cancel", color = DreamlandMuted) }
                }

                // ── Extra item (optional) ──────────────────────────────────────
                Text("EXTRA ITEM (OPTIONAL)", style = MaterialTheme.typography.labelSmall, color = DreamlandMuted, letterSpacing = 1.sp)
                ChargeField(state.itemName, vm::onStayChargeName, "Item (e.g. Late check-out, Extra mattress)")
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Box(Modifier.weight(1f)) { ChargeField(state.quantity, vm::onStayChargeQty, "Qty", KeyboardType.Number) }
                    Box(Modifier.weight(1f)) { ChargeField(state.unitPrice, vm::onStayChargePrice, "Unit price (₹)", KeyboardType.Decimal) }
                }
                if (price > 0) {
                    Text(
                        "GST 5% applied · pre-tax ₹${"%,.2f".format(lineTotal)}",
                        color = DreamlandMuted, style = MaterialTheme.typography.labelSmall,
                    )
                }

                HorizontalDivider(color = DreamlandMuted.copy(alpha = 0.15f))

                // ── Advance (optional) ─────────────────────────────────────────
                Text("ADVANCE (OPTIONAL)", style = MaterialTheme.typography.labelSmall, color = DreamlandMuted, letterSpacing = 1.sp)
                ChargeField(state.advanceAmount, vm::onStayChargeAdvanceAmount, "Advance collected (₹)", KeyboardType.Decimal)
                if (advance > 0) {
                    Text("Paid via", color = DreamlandMuted, fontSize = 11.sp, letterSpacing = 0.5.sp)
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        listOf("CASH", "BANK").forEach { method ->
                            val selected = state.advanceMethod == method
                            Box(
                                Modifier
                                    .weight(1f)
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(if (selected) DreamlandGold.copy(alpha = 0.18f) else Color.Transparent)
                                    .border(1.dp, if (selected) DreamlandGold else DreamlandMuted.copy(alpha = 0.35f), RoundedCornerShape(8.dp))
                                    .clickable { vm.onStayChargeAdvanceMethod(method) }
                                    .padding(vertical = 12.dp),
                                contentAlignment = Alignment.Center,
                            ) {
                                Text(method, color = if (selected) DreamlandGold else DreamlandMuted, fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal, fontSize = 13.sp)
                            }
                        }
                    }
                }

                if (state.error != null) {
                    Text(state.error, color = Color(0xFFEF5350), style = MaterialTheme.typography.bodySmall)
                }

                Button(
                    onClick = { vm.submitStayCharge() },
                    enabled = !state.isSaving,
                    modifier = Modifier.fillMaxWidth().height(48.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = DreamlandGold, disabledContainerColor = DreamlandGold.copy(alpha = 0.4f)),
                    shape = RoundedCornerShape(10.dp),
                ) {
                    Text(if (state.isSaving) "Saving…" else "Save to bill", color = Color(0xFF0D1F17), fontWeight = FontWeight.SemiBold)
                }
            }
        }
    }
}

@Composable
private fun ChargeField(value: String, onChange: (String) -> Unit, label: String, keyboard: KeyboardType = KeyboardType.Text) {
    OutlinedTextField(
        value = value,
        onValueChange = onChange,
        label = { Text(label, color = DreamlandMuted, fontSize = 12.sp) },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = keyboard),
        modifier = Modifier.fillMaxWidth(),
        colors = OutlinedTextFieldDefaults.colors(
            focusedTextColor = DreamlandOnDark, unfocusedTextColor = DreamlandOnDark,
            focusedBorderColor = DreamlandGold, unfocusedBorderColor = DreamlandMuted.copy(alpha = 0.4f),
            cursorColor = DreamlandGold,
        ),
    )
}
