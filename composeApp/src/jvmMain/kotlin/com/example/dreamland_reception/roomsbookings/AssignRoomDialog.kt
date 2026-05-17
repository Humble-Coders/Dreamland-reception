package com.example.dreamland_reception.roomsbookings

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import com.example.dreamland_reception.data.model.RoomInstance
import com.example.dreamland_reception.ui.viewmodel.RoomsAndBookingsUiState
import com.example.dreamland_reception.ui.viewmodel.RoomsAndBookingsViewModel

@Composable
fun AssignRoomDialog(
    state: RoomsAndBookingsUiState,
    vm: RoomsAndBookingsViewModel,
) {
    val booking = state.assignRoomDialogBooking ?: return

    // Filter to the booking's category only; show all if category is unknown
    val filteredRooms = if (booking.roomCategoryId.isBlank()) {
        state.availableRoomsForAssign
    } else {
        state.availableRoomsForAssign.filter { it.categoryId == booking.roomCategoryId }
    }

    // Currently assigned room first, then alphabetical by room number
    val sortedRooms = filteredRooms.sortedWith(compareBy(
        { it.id != booking.roomInstanceId },   // currently assigned room sorts first
        { it.roomNumber.length },
        { it.roomNumber },
    ))

    Dialog(
        onDismissRequest = { vm.closeAssignRoom() },
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth(0.52f)
                .heightIn(max = 580.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(DreamlandForestSurface)
                .border(1.dp, DreamlandGold.copy(alpha = 0.25f), RoundedCornerShape(16.dp))
                .padding(24.dp),
        ) {
            Column {
                // ── Header ────────────────────────────────────────────────────
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.Top,
                ) {
                    Column {
                        Text(
                            text = "ASSIGN ROOM",
                            style = MaterialTheme.typography.labelLarge,
                            color = DreamlandGold,
                            letterSpacing = 2.sp,
                        )
                        Spacer(Modifier.height(4.dp))
                        Text(
                            text = booking.guestName,
                            style = MaterialTheme.typography.headlineMedium,
                            color = DreamlandOnDark,
                        )
                        Text(
                            text = booking.roomCategoryName.ifBlank { "Any category" },
                            style = MaterialTheme.typography.bodySmall,
                            color = DreamlandMuted,
                        )
                    }
                    TextButton(onClick = { vm.closeAssignRoom() }) {
                        Text("Close", color = DreamlandMuted)
                    }
                }

                Spacer(Modifier.height(12.dp))
                HorizontalDivider(color = DreamlandGold.copy(alpha = 0.2f))
                Spacer(Modifier.height(12.dp))

                Text(
                    text = "Available rooms",
                    style = MaterialTheme.typography.titleSmall,
                    color = DreamlandMuted,
                )
                Spacer(Modifier.height(8.dp))

                // ── Room list ─────────────────────────────────────────────────
                when {
                    state.assignRoomLoading -> {
                        Box(
                            modifier = Modifier.fillMaxWidth().height(180.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            CircularProgressIndicator(color = DreamlandGold, strokeWidth = 2.dp)
                        }
                    }
                    sortedRooms.isEmpty() -> {
                        Box(
                            modifier = Modifier.fillMaxWidth().height(180.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text(
                                    text = if (booking.roomCategoryName.isBlank()) "No available rooms"
                                           else "No ${booking.roomCategoryName} rooms available",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = DreamlandMuted,
                                )
                                Spacer(Modifier.height(4.dp))
                                Text(
                                    text = "All rooms are currently occupied or under maintenance",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = DreamlandMuted.copy(alpha = 0.7f),
                                )
                            }
                        }
                    }
                    else -> {
                        LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            items(sortedRooms) { room ->
                                val isCurrentlyAssigned = room.id == booking.roomInstanceId
                                AvailableRoomRow(
                                    room = room,
                                    categoryName = state.categoryNames[room.categoryId] ?: room.categoryName,
                                    isCurrentlyAssigned = isCurrentlyAssigned,
                                    onSelect = { vm.confirmAssignRoom(room) },
                                    onDeselect = { vm.unassignRoom() },
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun AvailableRoomRow(
    room: RoomInstance,
    categoryName: String,
    isCurrentlyAssigned: Boolean,
    onSelect: () -> Unit,
    onDeselect: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(
                if (isCurrentlyAssigned) DreamlandGold.copy(alpha = 0.08f) else DreamlandForestElevated,
            )
            .border(
                1.dp,
                if (isCurrentlyAssigned) DreamlandGold.copy(alpha = 0.55f) else DreamlandGold.copy(alpha = 0.45f),
                RoundedCornerShape(10.dp),
            )
            .padding(horizontal = 16.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(modifier = Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier.size(8.dp).clip(CircleShape).background(Color(0xFF2ECC71)),
            )
            Spacer(Modifier.width(12.dp))
            Column {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "Room ${room.roomNumber}",
                        color = DreamlandOnDark,
                        fontWeight = FontWeight.SemiBold,
                        style = MaterialTheme.typography.bodyLarge,
                    )
                    // MATCH badge — always shown since all listed rooms match the category
                    Spacer(Modifier.width(8.dp))
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(4.dp))
                            .background(DreamlandGold.copy(alpha = 0.18f))
                            .padding(horizontal = 6.dp, vertical = 2.dp),
                    ) {
                        Text(
                            text = if (isCurrentlyAssigned) "ASSIGNED" else "MATCH",
                            style = MaterialTheme.typography.labelMedium,
                            color = DreamlandGold,
                            fontSize = 9.sp,
                            letterSpacing = 1.sp,
                        )
                    }
                }
                if (categoryName.isNotBlank()) {
                    Text(
                        text = categoryName,
                        style = MaterialTheme.typography.bodySmall,
                        color = DreamlandMuted,
                    )
                }
            }
        }

        Spacer(Modifier.width(12.dp))

        if (isCurrentlyAssigned) {
            OutlinedButton(
                onClick = onDeselect,
                border = androidx.compose.foundation.BorderStroke(1.dp, DreamlandGold.copy(alpha = 0.6f)),
                shape = RoundedCornerShape(8.dp),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 10.dp),
            ) {
                Text("Deselect", color = DreamlandGold, fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
            }
        } else {
            Button(
                onClick = onSelect,
                colors = ButtonDefaults.buttonColors(containerColor = DreamlandGold),
                shape = RoundedCornerShape(8.dp),
                contentPadding = PaddingValues(horizontal = 20.dp, vertical = 10.dp),
            ) {
                Text("Select", color = Color(0xFF0D1F17), fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
            }
        }
    }
}
