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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
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
import androidx.compose.ui.text.style.TextAlign
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
import com.example.dreamland_reception.ui.viewmodel.AvailabilityUiState
import com.example.dreamland_reception.ui.viewmodel.AvailabilityViewModel
import com.example.dreamland_reception.ui.viewmodel.AvailableCategory
import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

@Composable
fun CheckAvailabilityDialog(
    vm: AvailabilityViewModel = DreamlandAppInitializer.getAvailabilityViewModel(),
    onDismiss: () -> Unit,
) {
    val state by vm.uiState.collectAsStateWithLifecycle()

    Dialog(
        onDismissRequest = { vm.reset(); onDismiss() },
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth(0.72f)
                .heightIn(max = 700.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(DreamlandForestSurface)
                .padding(24.dp),
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        "Check Availability",
                        style = MaterialTheme.typography.titleLarge,
                        color = DreamlandOnDark,
                        fontWeight = FontWeight.Bold,
                    )
                    TextButton(onClick = { vm.reset(); onDismiss() }) {
                        Text("Close", color = DreamlandMuted)
                    }
                }

                HorizontalDivider(color = DreamlandGold.copy(alpha = 0.15f))

                // Form row
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.Bottom,
                ) {
                    AvailabilityDateField(
                        label = "Check-In",
                        date = state.checkIn,
                        onDateSelected = vm::setCheckIn,
                        modifier = Modifier.weight(1f),
                    )
                    AvailabilityDateField(
                        label = "Check-Out",
                        date = state.checkOut,
                        onDateSelected = vm::setCheckOut,
                        modifier = Modifier.weight(1f),
                    )
                    AvailabilityGuestsField(
                        guests = state.guests,
                        onChanged = vm::setGuests,
                        modifier = Modifier.weight(0.6f),
                    )
                    Button(
                        onClick = vm::search,
                        enabled = !state.loading,
                        colors = ButtonDefaults.buttonColors(containerColor = DreamlandGold),
                        shape = RoundedCornerShape(10.dp),
                    ) {
                        Icon(Icons.Filled.Search, contentDescription = null, tint = Color(0xFF0D1F17), modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Search", color = Color(0xFF0D1F17), fontWeight = FontWeight.SemiBold)
                    }
                }

                if (state.error != null) {
                    Text(state.error ?: "", color = Color(0xFFE74C3C), style = MaterialTheme.typography.bodySmall)
                }

                when {
                    state.loading -> {
                        Box(Modifier.fillMaxWidth().height(120.dp), contentAlignment = Alignment.Center) {
                            CircularProgressIndicator(color = DreamlandGold, strokeWidth = 2.dp)
                        }
                    }
                    state.searched && state.results.isEmpty() -> {
                        Box(Modifier.fillMaxWidth().height(120.dp), contentAlignment = Alignment.Center) {
                            Text(
                                "No rooms available for these dates",
                                style = MaterialTheme.typography.bodyMedium,
                                color = DreamlandMuted,
                                textAlign = TextAlign.Center,
                            )
                        }
                    }
                    state.results.isNotEmpty() -> {
                        Text(
                            "${state.results.size} category available",
                            style = MaterialTheme.typography.labelMedium,
                            color = DreamlandGold,
                            fontSize = 11.sp,
                        )
                        LazyColumn(
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.heightIn(max = 400.dp),
                        ) {
                            items(state.results) { cat -> AvailableCategoryCard(cat) }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun AvailableCategoryCard(cat: AvailableCategory) {
    val fmt = NumberFormat.getNumberInstance(Locale("en", "IN"))
    Card(
        colors = CardDefaults.cardColors(containerColor = DreamlandForestElevated),
        shape = RoundedCornerShape(10.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(
                    cat.room.type.replaceFirstChar { it.titlecase() },
                    style = MaterialTheme.typography.titleSmall,
                    color = DreamlandOnDark,
                    fontWeight = FontWeight.SemiBold,
                )
                Spacer(Modifier.height(2.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("Capacity: ${cat.room.capacity}", style = MaterialTheme.typography.labelSmall, color = DreamlandMuted, fontSize = 10.sp)
                    if (cat.room.amenities.isNotEmpty()) {
                        Text(cat.room.amenities.take(3).joinToString(", "), style = MaterialTheme.typography.labelSmall, color = DreamlandMuted, fontSize = 10.sp)
                    }
                }
            }
            Column(horizontalAlignment = Alignment.End) {
                Box(
                    Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .background(Color(0xFF2ECC71).copy(alpha = 0.15f))
                        .padding(horizontal = 10.dp, vertical = 4.dp),
                ) {
                    Text(
                        "${cat.availableCount} room${if (cat.availableCount != 1) "s" else ""} free",
                        color = Color(0xFF2ECC71),
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 11.sp,
                    )
                }
                Spacer(Modifier.height(4.dp))
                Text(
                    "₹${fmt.format(cat.pricePerNight.toLong())}/night",
                    style = MaterialTheme.typography.bodySmall,
                    color = DreamlandGold,
                    fontWeight = FontWeight.Medium,
                )
            }
        }
    }
}

@Composable
private fun AvailabilityDateField(
    label: String,
    date: Date,
    onDateSelected: (Date) -> Unit,
    modifier: Modifier = Modifier,
) {
    val fmt = remember { SimpleDateFormat("d MMM yyyy", Locale.getDefault()) }
    var showPicker by remember { mutableStateOf(false) }

    Column(modifier) {
        Text(label, style = MaterialTheme.typography.labelSmall, color = DreamlandMuted, fontSize = 10.sp)
        Spacer(Modifier.height(4.dp))
        Box(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(8.dp))
                .background(DreamlandForestElevated)
                .border(1.dp, DreamlandGold.copy(alpha = 0.3f), RoundedCornerShape(8.dp))
                .clickable { showPicker = true }
                .padding(horizontal = 12.dp, vertical = 8.dp),
        ) {
            Text(fmt.format(date), style = MaterialTheme.typography.bodySmall, color = DreamlandOnDark, fontSize = 12.sp)
        }
    }

    if (showPicker) {
        AvailabilityDatePickerDialog(
            initialDate = date,
            onDateSelected = { onDateSelected(it); showPicker = false },
            onDismiss = { showPicker = false },
        )
    }
}

@Composable
private fun AvailabilityGuestsField(guests: Int, onChanged: (Int) -> Unit, modifier: Modifier = Modifier) {
    Column(modifier) {
        Text("Guests", style = MaterialTheme.typography.labelSmall, color = DreamlandMuted, fontSize = 10.sp)
        Spacer(Modifier.height(4.dp))
        Row(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(8.dp))
                .background(DreamlandForestElevated)
                .border(1.dp, DreamlandGold.copy(alpha = 0.3f), RoundedCornerShape(8.dp))
                .padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            TextButton(onClick = { if (guests > 1) onChanged(guests - 1) }, modifier = Modifier.size(28.dp)) {
                Text("-", color = DreamlandGold, fontWeight = FontWeight.Bold, fontSize = 14.sp)
            }
            Text("$guests", color = DreamlandOnDark, fontSize = 13.sp, fontWeight = FontWeight.Medium)
            TextButton(onClick = { onChanged(guests + 1) }, modifier = Modifier.size(28.dp)) {
                Text("+", color = DreamlandGold, fontWeight = FontWeight.Bold, fontSize = 14.sp)
            }
        }
    }
}

@Composable
private fun AvailabilityDatePickerDialog(
    initialDate: Date,
    onDateSelected: (Date) -> Unit,
    onDismiss: () -> Unit,
) {
    val cal = Calendar.getInstance().apply { time = initialDate }
    var year  by remember { mutableStateOf(cal.get(Calendar.YEAR)) }
    var month by remember { mutableStateOf(cal.get(Calendar.MONTH) + 1) }
    var day   by remember { mutableStateOf(cal.get(Calendar.DAY_OF_MONTH)) }

    Dialog(onDismissRequest = onDismiss) {
        Card(
            colors = CardDefaults.cardColors(containerColor = DreamlandForestSurface),
            shape = RoundedCornerShape(16.dp),
        ) {
            Column(Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Text("Select Date", style = MaterialTheme.typography.titleMedium, color = DreamlandOnDark)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    AvailabilitySpinner("Day",   day,   1, 31)   { day   = it }
                    AvailabilitySpinner("Month", month, 1, 12)   { month = it }
                    AvailabilitySpinner("Year",  year,  2024, 2030) { year = it }
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    TextButton(onClick = onDismiss) { Text("Cancel", color = DreamlandMuted) }
                    Spacer(Modifier.width(8.dp))
                    Button(
                        onClick = {
                            val c = Calendar.getInstance()
                            c.set(year, month - 1, day, 0, 0, 0)
                            c.set(Calendar.MILLISECOND, 0)
                            onDateSelected(c.time)
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = DreamlandGold),
                        shape = RoundedCornerShape(8.dp),
                    ) { Text("Set", color = Color(0xFF0D1F17), fontWeight = FontWeight.SemiBold) }
                }
            }
        }
    }
}

@Composable
private fun AvailabilitySpinner(label: String, value: Int, min: Int, max: Int, onChanged: (Int) -> Unit) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(label, style = MaterialTheme.typography.labelSmall, color = DreamlandMuted)
        Spacer(Modifier.height(4.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = { if (value > min) onChanged(value - 1) }) {
                Text("-", color = DreamlandGold, fontWeight = FontWeight.Bold)
            }
            Text(
                "$value",
                color = DreamlandOnDark,
                modifier = Modifier.width(40.dp),
                textAlign = TextAlign.Center,
                fontSize = 13.sp,
            )
            TextButton(onClick = { if (value < max) onChanged(value + 1) }) {
                Text("+", color = DreamlandGold, fontWeight = FontWeight.Bold)
            }
        }
    }
}
