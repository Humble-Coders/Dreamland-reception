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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
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
import com.example.dreamland_reception.data.model.RoomInstance
import com.example.dreamland_reception.ui.viewmodel.ChangeRoomState
import com.example.dreamland_reception.ui.viewmodel.StaysViewModel

@Composable
fun ChangeRoomDialog(state: ChangeRoomState, vm: StaysViewModel) {
    if (!state.isOpen || state.stay == null) return

    val stay = state.stay
    val categoryName = stay.roomCategoryName.ifBlank { state.categoryNames[stay.roomCategoryId] ?: stay.roomCategoryId }

    Dialog(
        onDismissRequest = { vm.closeChangeRoom() },
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Box(
            modifier = Modifier
                .widthIn(min = 380.dp, max = 520.dp)
                .fillMaxWidth(0.46f)
                .heightIn(max = 600.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(DreamlandForestSurface)
                .padding(24.dp),
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(0.dp)) {

                // ── Header ────────────────────────────────────────────────────
                Text("Change Room", style = MaterialTheme.typography.titleLarge, color = DreamlandOnDark, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(4.dp))
                Text(
                    "Currently: Room ${stay.roomNumber}${if (categoryName.isNotBlank()) " · $categoryName" else ""}",
                    style = MaterialTheme.typography.bodySmall,
                    color = DreamlandMuted,
                )

                Spacer(Modifier.height(20.dp))
                HorizontalDivider(color = DreamlandGold.copy(alpha = 0.15f))
                Spacer(Modifier.height(16.dp))

                // ── Body ──────────────────────────────────────────────────────
                when {
                    state.isLoading -> {
                        Box(Modifier.fillMaxWidth().height(120.dp), contentAlignment = Alignment.Center) {
                            CircularProgressIndicator(color = DreamlandGold, strokeWidth = 3.dp, modifier = Modifier.size(36.dp))
                        }
                    }

                    state.selectableRooms.isEmpty() && state.cleaningRooms.isEmpty() -> {
                        Box(Modifier.fillMaxWidth().height(80.dp), contentAlignment = Alignment.Center) {
                            Text("No rooms available for these dates.", color = DreamlandMuted, style = MaterialTheme.typography.bodyMedium)
                        }
                    }

                    else -> {
                        LazyColumn(
                            modifier = Modifier.fillMaxWidth().heightIn(max = 360.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            items(state.selectableRooms) { room ->
                                val catName = room.categoryName.ifBlank { state.categoryNames[room.categoryId] ?: "" }
                                RoomRow(room, catName = catName, selected = state.selectedInstance?.id == room.id, cleaning = false, notBookable = !room.isAvailableForBooking) {
                                    vm.onChangeRoomSelected(room)
                                }
                            }
                            if (state.cleaningRooms.isNotEmpty()) {
                                item {
                                    Spacer(Modifier.height(4.dp))
                                    Text(
                                        "Needs Cleaning",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = DreamlandMuted,
                                        letterSpacing = 1.sp,
                                    )
                                    Spacer(Modifier.height(4.dp))
                                }
                                items(state.cleaningRooms) { room ->
                                    val catName = room.categoryName.ifBlank { state.categoryNames[room.categoryId] ?: "" }
                                    RoomRow(room, catName = catName, selected = state.selectedInstance?.id == room.id, cleaning = true, notBookable = !room.isAvailableForBooking) {
                                        vm.onChangeRoomSelected(room)
                                    }
                                }
                            }
                        }
                    }
                }

                // ── Error ─────────────────────────────────────────────────────
                if (state.error != null) {
                    Spacer(Modifier.height(8.dp))
                    Text(state.error, color = Color(0xFFEF5350), style = MaterialTheme.typography.bodySmall)
                }

                Spacer(Modifier.height(20.dp))
                HorizontalDivider(color = DreamlandGold.copy(alpha = 0.15f))
                Spacer(Modifier.height(16.dp))

                // ── Footer ────────────────────────────────────────────────────
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End, verticalAlignment = Alignment.CenterVertically) {
                    TextButton(onClick = { vm.closeChangeRoom() }) {
                        Text("Cancel", color = DreamlandMuted)
                    }
                    Spacer(Modifier.width(8.dp))
                    Button(
                        onClick = { vm.confirmChangeRoom() },
                        enabled = state.selectedInstance != null && !state.isSaving,
                        colors = ButtonDefaults.buttonColors(containerColor = DreamlandGold),
                        shape = RoundedCornerShape(10.dp),
                    ) {
                        if (state.isSaving) {
                            CircularProgressIndicator(color = Color(0xFF0D1F17), strokeWidth = 2.dp, modifier = Modifier.size(16.dp))
                        } else {
                            Text("Confirm", color = Color(0xFF0D1F17), fontWeight = FontWeight.SemiBold)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun RoomRow(room: RoomInstance, catName: String, selected: Boolean, cleaning: Boolean, notBookable: Boolean = false, onClick: () -> Unit) {
    val borderColor = if (selected) DreamlandGold else DreamlandGold.copy(alpha = 0.2f)
    val bgColor = if (selected) DreamlandGold.copy(alpha = 0.08f) else Color.Transparent
    val textColor = if (cleaning) DreamlandMuted else DreamlandOnDark

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(bgColor)
            .border(1.dp, borderColor, RoundedCornerShape(10.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                "Room ${room.roomNumber}",
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                color = if (selected) DreamlandGold else textColor,
            )
            if (catName.isNotBlank()) {
                Text("·", color = DreamlandMuted, style = MaterialTheme.typography.bodyMedium)
                Text(
                    catName,
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (selected) DreamlandGold.copy(alpha = 0.8f) else DreamlandMuted,
                )
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            if (notBookable) {
                Text("Not Bookable", style = MaterialTheme.typography.labelSmall, color = Color(0xFFF39C12).copy(alpha = 0.8f))
            }
            if (cleaning) {
                Text("Cleaning", style = MaterialTheme.typography.labelSmall, color = DreamlandMuted.copy(alpha = 0.7f))
            }
        }
    }
}
