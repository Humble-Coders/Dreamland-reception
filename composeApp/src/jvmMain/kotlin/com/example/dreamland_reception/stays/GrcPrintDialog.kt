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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
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
import com.example.dreamland_reception.ui.viewmodel.GrcPhase
import com.example.dreamland_reception.ui.viewmodel.GrcPrintState
import com.example.dreamland_reception.ui.viewmodel.StaysViewModel

private val GREEN = Color(0xFF4CAF50)
private val RED = Color(0xFFEF5350)

/**
 * Print a Guest Registration Card for any guest of an active stay. Pick a printer once,
 * then print each guest's card. Uses the same renderer/printer path as check-in.
 */
@Composable
fun GrcPrintDialog(state: GrcPrintState, vm: StaysViewModel) {
    if (!state.isOpen) return
    Dialog(onDismissRequest = vm::closeGrcPrint, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Box(
            modifier = Modifier
                .fillMaxWidth(0.42f)
                .heightIn(max = 620.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(DreamlandForestSurface)
                .padding(24.dp),
        ) {
            Column(Modifier.fillMaxWidth()) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Column {
                        Text("PRINT GRC", style = MaterialTheme.typography.labelLarge, color = DreamlandGold, letterSpacing = 2.sp)
                        Text("Room ${state.roomNumber}", style = MaterialTheme.typography.headlineSmall, color = DreamlandOnDark)
                    }
                    TextButton(onClick = vm::closeGrcPrint) { Text("Close", color = DreamlandMuted) }
                }

                Spacer(Modifier.height(16.dp))
                Text("PRINTER", style = MaterialTheme.typography.labelSmall, color = DreamlandMuted, letterSpacing = 1.sp)
                Spacer(Modifier.height(6.dp))
                PrinterPicker(state, vm)

                Spacer(Modifier.height(18.dp))
                Text("GUESTS", style = MaterialTheme.typography.labelSmall, color = DreamlandMuted, letterSpacing = 1.sp)
                Spacer(Modifier.height(8.dp))

                if (state.guests.isEmpty()) {
                    Text("No guest details on record for this stay.", color = DreamlandMuted, style = MaterialTheme.typography.bodySmall)
                } else {
                    Column(
                        modifier = Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        state.guests.forEachIndexed { index, guest ->
                            GuestPrintRow(
                                index = index,
                                name = guest.name.ifBlank { "Guest ${index + 1}" },
                                grcNumber = guest.grcNumber,
                                phase = state.statuses[index] ?: GrcPhase.IDLE,
                                error = state.errors[index],
                                onPrint = { vm.printStayGrc(index) },
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun PrinterPicker(state: GrcPrintState, vm: StaysViewModel) {
    var open by remember { mutableStateOf(false) }
    Box {
        Row(
            modifier = Modifier.fillMaxWidth()
                .clip(RoundedCornerShape(10.dp))
                .border(1.dp, DreamlandMuted.copy(alpha = 0.35f), RoundedCornerShape(10.dp))
                .background(DreamlandForestElevated)
                .clickable { open = true }
                .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                state.selectedPrinter.ifBlank { "Select printer…" },
                color = if (state.selectedPrinter.isNotBlank()) DreamlandOnDark else DreamlandMuted,
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.weight(1f),
            )
            Text("▾", color = DreamlandMuted)
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            if (state.availablePrinters.isEmpty()) {
                DropdownMenuItem(enabled = false, text = { Text("Looking for printers…", color = DreamlandMuted) }, onClick = {})
            }
            state.availablePrinters.forEach { name ->
                DropdownMenuItem(text = { Text(name) }, onClick = { vm.selectGrcPrintPrinter(name); open = false })
            }
        }
    }
}

@Composable
private fun GuestPrintRow(
    index: Int,
    name: String,
    grcNumber: String,
    phase: GrcPhase,
    error: String?,
    onPrint: () -> Unit,
) {
    Column(
        Modifier.fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(DreamlandForestElevated)
            .padding(horizontal = 14.dp, vertical = 10.dp),
    ) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(name, color = DreamlandOnDark, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                if (grcNumber.isNotBlank()) {
                    Text("GRC $grcNumber", color = DreamlandMuted, style = MaterialTheme.typography.labelSmall)
                }
            }
            Spacer(Modifier.width(12.dp))
            when (phase) {
                GrcPhase.WORKING -> Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    CircularProgressIndicator(Modifier.size(14.dp), color = DreamlandGold, strokeWidth = 2.dp)
                    Text("Printing…", color = DreamlandMuted, style = MaterialTheme.typography.labelMedium)
                }
                GrcPhase.DONE -> PrintPill(label = "Reprint", accent = GREEN, doneCheck = true, onClick = onPrint)
                GrcPhase.ERROR -> PrintPill(label = "Retry", accent = RED, doneCheck = false, onClick = onPrint)
                GrcPhase.IDLE -> PrintPill(label = "Print", accent = DreamlandGold, doneCheck = false, onClick = onPrint)
            }
        }
        if (phase == GrcPhase.ERROR && !error.isNullOrBlank()) {
            Spacer(Modifier.height(4.dp))
            Text(error, color = RED, style = MaterialTheme.typography.labelSmall)
        }
    }
}

@Composable
private fun PrintPill(label: String, accent: Color, doneCheck: Boolean, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .border(1.dp, accent.copy(alpha = 0.6f), RoundedCornerShape(8.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(5.dp),
    ) {
        if (doneCheck) Text("✓", color = accent, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
        Text(label, color = accent, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold)
    }
}
