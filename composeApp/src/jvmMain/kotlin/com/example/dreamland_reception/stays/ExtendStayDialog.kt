package com.example.dreamland_reception.stays

import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
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
import com.example.dreamland_reception.ui.viewmodel.ExtendStayState
import com.example.dreamland_reception.ui.viewmodel.StaysViewModel
import java.text.SimpleDateFormat
import java.time.temporal.ChronoUnit
import java.util.Locale

@Composable
fun ExtendStayDialog(state: ExtendStayState, vm: StaysViewModel) {
    if (!state.isOpen || state.stay == null) return

    val stay = state.stay
    val fmt = SimpleDateFormat("dd MMM yyyy", Locale.getDefault())
    val currentCheckOut = stay.expectedCheckOut

    val extensionNights = if (state.newCheckOut != null)
        ChronoUnit.DAYS.between(currentCheckOut.toInstant(), state.newCheckOut.toInstant()).coerceAtLeast(0)
    else 0L

    Dialog(
        onDismissRequest = { vm.closeExtendStay() },
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Box(
            modifier = Modifier
                .widthIn(min = 380.dp, max = 520.dp)
                .fillMaxWidth(0.46f)
                .heightIn(max = 640.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(DreamlandForestSurface)
                .padding(24.dp),
        ) {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(0.dp),
            ) {

                // ── Header ────────────────────────────────────────────────────
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column {
                        Text("EXTEND STAY", style = MaterialTheme.typography.labelLarge, color = DreamlandGold, letterSpacing = 2.sp)
                        Text("${stay.guestName} · Room ${stay.roomNumber}", style = MaterialTheme.typography.titleMedium, color = DreamlandOnDark, fontWeight = FontWeight.SemiBold)
                    }
                    TextButton(onClick = vm::closeExtendStay) {
                        Text("Cancel", color = DreamlandMuted)
                    }
                }

                Spacer(Modifier.height(20.dp))

                // ── Current checkout ──────────────────────────────────────────
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .background(DreamlandForestElevated)
                        .padding(14.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column {
                        Text("Current Check-out", style = MaterialTheme.typography.labelSmall, color = DreamlandMuted)
                        Spacer(Modifier.height(2.dp))
                        Text(fmt.format(currentCheckOut), color = DreamlandOnDark, fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.bodyLarge)
                    }
                    Box(
                        Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .background(DreamlandGold.copy(alpha = 0.12f))
                            .padding(horizontal = 10.dp, vertical = 4.dp),
                    ) {
                        Text(stay.roomCategoryName, color = DreamlandGold, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.SemiBold)
                    }
                }

                Spacer(Modifier.height(16.dp))

                // ── New checkout date picker ───────────────────────────────────
                SectionLabel("New Check-out Date")
                Spacer(Modifier.height(8.dp))
                DateSelectorField(
                    modifier = Modifier.fillMaxWidth(),
                    label = "Extend until *",
                    date = state.newCheckOut,
                    onDateSelected = { it?.let(vm::onExtendNewCheckOut) },
                    minDate = currentCheckOut,
                )

                // Extension summary pill
                if (state.newCheckOut != null && extensionNights > 0) {
                    Spacer(Modifier.height(10.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            Modifier
                                .clip(RoundedCornerShape(20.dp))
                                .background(DreamlandGold.copy(alpha = 0.12f))
                                .padding(horizontal = 12.dp, vertical = 5.dp),
                        ) {
                            Text(
                                "+$extensionNights night${if (extensionNights != 1L) "s" else ""}",
                                color = DreamlandGold,
                                fontWeight = FontWeight.SemiBold,
                                style = MaterialTheme.typography.bodySmall,
                            )
                        }
                        Text(
                            "${fmt.format(currentCheckOut)} → ${fmt.format(state.newCheckOut)}",
                            color = DreamlandMuted,
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                }

                Spacer(Modifier.height(20.dp))
                HorizontalDivider(color = DreamlandGold.copy(alpha = 0.15f))
                Spacer(Modifier.height(16.dp))

                // ── Availability result ───────────────────────────────────────
                when {
                    state.isChecking -> {
                        Row(
                            Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            CircularProgressIndicator(modifier = Modifier.size(18.dp), color = DreamlandGold, strokeWidth = 2.dp)
                            Text("Checking availability for Room ${stay.roomNumber}…", color = DreamlandMuted, style = MaterialTheme.typography.bodySmall)
                        }
                    }

                    state.roomAvailable == true -> {
                        // Available — show confirm section
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(10.dp))
                                .background(Color(0xFF4CAF50).copy(alpha = 0.1f))
                                .border(1.dp, Color(0xFF4CAF50).copy(alpha = 0.4f), RoundedCornerShape(10.dp))
                                .padding(14.dp),
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text("✓", color = Color(0xFF4CAF50), fontWeight = FontWeight.Bold, fontSize = 16.sp)
                            Column {
                                Text(
                                    "Room ${stay.roomNumber} is available",
                                    color = Color(0xFF4CAF50),
                                    fontWeight = FontWeight.SemiBold,
                                    style = MaterialTheme.typography.bodyMedium,
                                )
                                if (state.newCheckOut != null) {
                                    Text(
                                        "Extended until ${fmt.format(state.newCheckOut)}",
                                        color = DreamlandMuted,
                                        style = MaterialTheme.typography.bodySmall,
                                    )
                                }
                            }
                        }

                        Spacer(Modifier.height(16.dp))

                        if (state.error != null) {
                            Text(state.error, color = Color(0xFFEF5350), style = MaterialTheme.typography.bodySmall)
                            Spacer(Modifier.height(8.dp))
                        }

                        Button(
                            onClick = vm::confirmExtend,
                            enabled = !state.isSaving,
                            modifier = Modifier.fillMaxWidth().height(52.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = DreamlandGold,
                                disabledContainerColor = DreamlandGold.copy(alpha = 0.35f),
                            ),
                            shape = RoundedCornerShape(10.dp),
                        ) {
                            if (state.isSaving) {
                                CircularProgressIndicator(modifier = Modifier.size(18.dp), color = Color(0xFF0D1F17), strokeWidth = 2.dp)
                            } else {
                                Text(
                                    "Confirm Extension (+$extensionNights nights)",
                                    color = Color(0xFF0D1F17),
                                    fontWeight = FontWeight.SemiBold,
                                    fontSize = 15.sp,
                                )
                            }
                        }
                    }

                    state.roomAvailable == false -> {
                        // Determine reason: category full vs specific room taken
                        val categoryFull = state.alternativeInstances.isEmpty()
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(10.dp))
                                .background(Color(0xFFEF5350).copy(alpha = 0.08f))
                                .border(1.dp, Color(0xFFEF5350).copy(alpha = 0.35f), RoundedCornerShape(10.dp))
                                .padding(14.dp),
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text("✕", color = Color(0xFFEF5350), fontWeight = FontWeight.Bold, fontSize = 16.sp)
                            Column {
                                Text(
                                    if (categoryFull)
                                        "${stay.roomCategoryName} category is fully booked"
                                    else
                                        "Room ${stay.roomNumber} is not available",
                                    color = Color(0xFFEF5350),
                                    fontWeight = FontWeight.SemiBold,
                                    style = MaterialTheme.typography.bodyMedium,
                                )
                                Text(
                                    if (categoryFull)
                                        "No ${stay.roomCategoryName} rooms are free for this period"
                                    else
                                        "This room is already booked — see alternatives below",
                                    color = DreamlandMuted,
                                    style = MaterialTheme.typography.bodySmall,
                                )
                            }
                        }

                        if (state.alternativeInstances.isNotEmpty()) {
                            Spacer(Modifier.height(14.dp))
                            Text(
                                "AVAILABLE ${stay.roomCategoryName.uppercase()} ROOMS",
                                style = MaterialTheme.typography.labelSmall,
                                color = DreamlandMuted,
                                letterSpacing = 1.sp,
                                fontSize = 10.sp,
                            )
                            Spacer(Modifier.height(6.dp))
                            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                items(state.alternativeInstances) { inst ->
                                    Box(
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(8.dp))
                                            .background(Color(0xFF4CAF50).copy(alpha = 0.08f))
                                            .border(1.dp, Color(0xFF4CAF50).copy(alpha = 0.35f), RoundedCornerShape(8.dp))
                                            .padding(horizontal = 14.dp, vertical = 8.dp),
                                        contentAlignment = Alignment.Center,
                                    ) {
                                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                            Text("Room ${inst.roomNumber}", color = Color(0xFF4CAF50), fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.bodySmall)
                                            Text("available", color = DreamlandMuted, style = MaterialTheme.typography.labelSmall, fontSize = 10.sp)
                                            if (!inst.isAvailableForBooking) {
                                                Text("not bookable", color = Color(0xFFF39C12).copy(alpha = 0.8f), style = MaterialTheme.typography.labelSmall, fontSize = 9.sp, fontWeight = FontWeight.Medium)
                                            }
                                        }
                                    }
                                }
                            }
                            Spacer(Modifier.height(8.dp))
                            Text(
                                "To move the guest to one of these rooms, check them out and create a new walk-in stay.",
                                color = DreamlandMuted.copy(alpha = 0.7f),
                                style = MaterialTheme.typography.bodySmall,
                                lineHeight = 16.sp,
                            )
                        }

                        // All-category availability for the extension window
                        if (state.availableCategoryOptions.isNotEmpty()) {
                            Spacer(Modifier.height(14.dp))
                            Text(
                                "AVAILABLE CATEGORIES FOR THESE DATES",
                                style = MaterialTheme.typography.labelSmall,
                                color = DreamlandMuted,
                                letterSpacing = 1.sp,
                                fontSize = 10.sp,
                            )
                            Spacer(Modifier.height(8.dp))
                            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                state.availableCategoryOptions.forEach { cat ->
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clip(RoundedCornerShape(8.dp))
                                            .background(DreamlandForestElevated)
                                            .padding(horizontal = 12.dp, vertical = 10.dp),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically,
                                    ) {
                                        Column {
                                            Text(cat.categoryName, color = DreamlandOnDark, fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.bodySmall)
                                            Text("₹${cat.pricePerNight.toLong()}/night", color = DreamlandMuted, style = MaterialTheme.typography.labelSmall)
                                        }
                                        Box(
                                            Modifier
                                                .clip(RoundedCornerShape(4.dp))
                                                .background(Color(0xFF4CAF50).copy(alpha = 0.15f))
                                                .padding(horizontal = 8.dp, vertical = 3.dp),
                                        ) {
                                            Text("${cat.availableCount} avail.", color = Color(0xFF4CAF50), style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.SemiBold)
                                        }
                                    }
                                }
                            }
                            Spacer(Modifier.height(6.dp))
                            Text(
                                "Check out and use Walk-in to book a different room.",
                                color = DreamlandMuted.copy(alpha = 0.6f),
                                style = MaterialTheme.typography.bodySmall,
                            )
                        } else if (state.alternativeInstances.isEmpty()) {
                            Spacer(Modifier.height(10.dp))
                            Text(
                                "No rooms of any category are available for this extension period.",
                                color = DreamlandMuted,
                                style = MaterialTheme.typography.bodySmall,
                            )
                        }
                    }
                }

                if (state.error != null && state.roomAvailable == null) {
                    Spacer(Modifier.height(8.dp))
                    Text(state.error, color = Color(0xFFEF5350), style = MaterialTheme.typography.bodySmall)
                }
            }
        }
    }
}
