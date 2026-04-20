package com.example.dreamland_reception.stays

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
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
import com.example.dreamland_reception.data.model.Booking
import com.example.dreamland_reception.ui.viewmodel.FromBookingState
import com.example.dreamland_reception.ui.viewmodel.StaysViewModel
import java.text.SimpleDateFormat
import java.util.Locale

@Composable
fun FromBookingDialog(state: FromBookingState, vm: StaysViewModel) {
    Dialog(
        onDismissRequest = { vm.closeFromBooking() },
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth(0.55f)
                .heightIn(max = 600.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(DreamlandForestSurface)
                .padding(24.dp),
        ) {
            Column {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column {
                        Text("UPCOMING BOOKINGS", style = MaterialTheme.typography.labelLarge, color = DreamlandGold, letterSpacing = 2.sp)
                        Text("Check-in from booking", style = MaterialTheme.typography.headlineMedium, color = DreamlandOnDark)
                    }
                    TextButton(onClick = { vm.closeFromBooking() }) {
                        Text("Close", color = DreamlandMuted)
                    }
                }

                Spacer(Modifier.height(8.dp))
                Text("Bookings due today ± 1 day", color = DreamlandMuted, style = MaterialTheme.typography.bodySmall)
                Spacer(Modifier.height(16.dp))
                HorizontalDivider(color = DreamlandGold.copy(alpha = 0.2f))
                Spacer(Modifier.height(8.dp))

                when {
                    state.isLoading -> {
                        Box(Modifier.fillMaxWidth().height(200.dp), contentAlignment = Alignment.Center) {
                            CircularProgressIndicator(color = DreamlandGold)
                        }
                    }
                    state.bookings.isEmpty() -> {
                        Box(Modifier.fillMaxWidth().height(200.dp), contentAlignment = Alignment.Center) {
                            Text("No upcoming bookings", color = DreamlandMuted, style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                    else -> {
                        LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            items(state.bookings) { booking ->
                                BookingRow(booking = booking, onCheckIn = { vm.prefillFromBooking(booking) })
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun BookingRow(booking: Booking, onCheckIn: () -> Unit) {
    val fmt = SimpleDateFormat("dd MMM", Locale.getDefault())
    Card(
        colors = CardDefaults.cardColors(containerColor = DreamlandForestElevated),
        shape = RoundedCornerShape(10.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(booking.guestName, color = DreamlandOnDark, fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.bodyLarge)
                Spacer(Modifier.height(2.dp))
                Text(
                    text = "${booking.roomCategoryName.ifBlank { "Room" }} · ${fmt.format(booking.checkIn)} → ${fmt.format(booking.checkOut)}",
                    color = DreamlandMuted,
                    style = MaterialTheme.typography.bodySmall,
                )
                Text(
                    text = "${booking.adults}A ${if (booking.children > 0) "+ ${booking.children}C" else ""}",
                    color = DreamlandMuted,
                    style = MaterialTheme.typography.bodySmall,
                )
            }
            Button(
                onClick = onCheckIn,
                colors = ButtonDefaults.buttonColors(containerColor = DreamlandGold),
                shape = RoundedCornerShape(8.dp),
            ) {
                Text("Check In", color = Color(0xFF0D1F17), fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
            }
        }
    }
}
