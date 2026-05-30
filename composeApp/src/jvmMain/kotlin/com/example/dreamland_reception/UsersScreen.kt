package com.example.dreamland_reception

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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.dreamland_reception.data.model.Booking
import com.example.dreamland_reception.data.model.Guest
import com.example.dreamland_reception.data.model.Stay
import com.example.dreamland_reception.ui.viewmodel.UsersUiState
import com.example.dreamland_reception.ui.viewmodel.UsersViewModel
import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.Locale

@Composable
fun UsersScreen(
    vm: UsersViewModel = DreamlandAppInitializer.getUsersViewModel(),
) {
    val state by vm.state.collectAsStateWithLifecycle()

    Row(Modifier.fillMaxSize()) {
        UsersListPanel(
            state = state,
            onSearch = vm::setSearch,
            onSelect = { vm.selectUser(if (it.id == state.selectedUserId) null else it) },
            modifier = Modifier.width(340.dp).fillMaxHeight(),
        )
        VerticalDivider(thickness = 1.dp, color = DreamlandGold.copy(alpha = 0.15f))
        Box(
            modifier = Modifier.weight(1f).fillMaxHeight().background(DreamlandForest),
        ) {
            when {
                state.selectedUser != null -> UserDetailPanel(state)
                else -> UserDetailPlaceholder(state.users.size)
            }
        }
    }
}

// ── Left panel ────────────────────────────────────────────────────────────────

@Composable
private fun UsersListPanel(
    state: UsersUiState,
    onSearch: (String) -> Unit,
    onSelect: (Guest) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier.background(DreamlandForestSurface)) {
        // Header
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                "GUESTS",
                style = MaterialTheme.typography.labelSmall,
                color = DreamlandGold,
                fontWeight = FontWeight.SemiBold,
                letterSpacing = 1.sp,
            )
            if (state.users.isNotEmpty()) {
                Box(
                    Modifier
                        .clip(RoundedCornerShape(10.dp))
                        .background(DreamlandGold.copy(alpha = 0.15f))
                        .padding(horizontal = 8.dp, vertical = 3.dp),
                ) {
                    Text(
                        "${state.filteredUsers.size}",
                        style = MaterialTheme.typography.labelSmall,
                        color = DreamlandGold,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
            }
        }

        // Search
        OutlinedTextField(
            value = state.searchQuery,
            onValueChange = onSearch,
            placeholder = { Text("Search name or phone…", color = DreamlandMuted, fontSize = 12.sp) },
            leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, tint = DreamlandMuted, modifier = Modifier.size(16.dp)) },
            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp),
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Text),
            textStyle = MaterialTheme.typography.bodySmall.copy(color = DreamlandOnDark),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = DreamlandGold,
                unfocusedBorderColor = DreamlandMuted.copy(alpha = 0.3f),
                focusedTextColor = DreamlandOnDark,
                unfocusedTextColor = DreamlandOnDark,
                cursorColor = DreamlandGold,
            ),
        )

        Spacer(Modifier.height(8.dp))
        HorizontalDivider(color = DreamlandGold.copy(alpha = 0.1f))

        when {
            state.isLoading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = DreamlandGold, modifier = Modifier.size(28.dp))
            }
            state.error != null -> Box(Modifier.fillMaxSize().padding(16.dp), contentAlignment = Alignment.Center) {
                Text(state.error, color = Color(0xFFE74C3C), style = MaterialTheme.typography.bodySmall)
            }
            state.filteredUsers.isEmpty() -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("No guests found", color = DreamlandMuted, style = MaterialTheme.typography.bodySmall)
            }
            else -> LazyColumn(
                modifier = Modifier.fillMaxSize().padding(horizontal = 10.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                item { Spacer(Modifier.height(6.dp)) }
                items(state.filteredUsers, key = { it.id }) { guest ->
                    UserListItem(
                        guest = guest,
                        isSelected = guest.id == state.selectedUserId,
                        onClick = { onSelect(guest) },
                    )
                }
                item { Spacer(Modifier.height(12.dp)) }
            }
        }
    }
}

@Composable
private fun UserListItem(guest: Guest, isSelected: Boolean, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(if (isSelected) DreamlandGold.copy(alpha = 0.08f) else Color.Transparent)
            .border(
                1.dp,
                if (isSelected) DreamlandGold.copy(alpha = 0.5f) else DreamlandGold.copy(alpha = 0.08f),
                RoundedCornerShape(8.dp),
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // Avatar
        Box(
            modifier = Modifier
                .size(44.dp)
                .clip(CircleShape)
                .background(DreamlandGold.copy(alpha = if (isSelected) 0.25f else 0.12f)),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                guest.name.firstOrNull()?.uppercase() ?: "?",
                color = DreamlandGold,
                fontWeight = FontWeight.Bold,
                fontSize = 14.sp,
            )
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text(
                guest.name,
                style = MaterialTheme.typography.bodyMedium,
                color = DreamlandOnDark,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (guest.phone.isNotBlank()) {
                Text(
                    guest.phone,
                    style = MaterialTheme.typography.bodySmall,
                    color = DreamlandMuted,
                    maxLines = 1,
                )
            }
            if (guest.email.isNotBlank()) {
                Text(
                    guest.email,
                    style = MaterialTheme.typography.bodySmall,
                    color = DreamlandMuted,
                    fontSize = 10.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        if (guest.totalStays > 0) {
            Box(
                Modifier
                    .clip(RoundedCornerShape(4.dp))
                    .background(DreamlandGold.copy(alpha = 0.12f))
                    .padding(horizontal = 5.dp, vertical = 2.dp),
            ) {
                Text(
                    "${guest.totalStays}",
                    style = MaterialTheme.typography.labelSmall,
                    color = DreamlandGold,
                    fontSize = 9.sp,
                    fontWeight = FontWeight.SemiBold,
                )
            }
        }
    }
}

// ── Right panel ───────────────────────────────────────────────────────────────

@Composable
private fun UserDetailPlaceholder(userCount: Int) {
    Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(
            Icons.Default.Person,
            contentDescription = null,
            tint = DreamlandGold.copy(alpha = 0.25f),
            modifier = Modifier.size(52.dp),
        )
        Spacer(Modifier.height(12.dp))
        Text("Select a guest to view details", color = DreamlandMuted, style = MaterialTheme.typography.bodyMedium)
        if (userCount > 0) {
            Text("$userCount guests", color = DreamlandMuted.copy(alpha = 0.5f), style = MaterialTheme.typography.bodySmall)
        }
    }
}

@Composable
private fun UserDetailPanel(state: UsersUiState) {
    val guest = state.selectedUser ?: return
    val scrollState = rememberScrollState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(scrollState)
            .padding(28.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        // Profile card
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .background(DreamlandForestSurface)
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                Box(
                    modifier = Modifier.size(48.dp).clip(CircleShape).background(DreamlandGold.copy(alpha = 0.15f)),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        guest.name.firstOrNull()?.uppercase() ?: "?",
                        color = DreamlandGold,
                        fontWeight = FontWeight.Bold,
                        fontSize = 20.sp,
                    )
                }
                Column {
                    Text(guest.name, style = MaterialTheme.typography.titleMedium, color = DreamlandOnDark, fontWeight = FontWeight.Bold)
                    if (guest.phone.isNotBlank()) Text(guest.phone, style = MaterialTheme.typography.bodySmall, color = DreamlandMuted)
                }
                Spacer(Modifier.weight(1f))
                if (guest.totalStays > 0) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("${guest.totalStays}", style = MaterialTheme.typography.titleMedium, color = DreamlandGold, fontWeight = FontWeight.Bold)
                        Text("stays", style = MaterialTheme.typography.labelSmall, color = DreamlandMuted)
                    }
                }
            }
            HorizontalDivider(color = DreamlandGold.copy(alpha = 0.1f))
            if (guest.email.isNotBlank()) ProfileRow("Email", guest.email)
            if (guest.idType.isNotBlank()) ProfileRow(guest.idType.replaceFirstChar { it.uppercase() }, guest.idNumber)
            if (guest.nationality.isNotBlank()) ProfileRow("Nationality", guest.nationality)
            if (guest.address.isNotBlank()) ProfileRow("Address", guest.address)
        }

        // Bookings section
        if (state.isDetailLoading) {
            Box(Modifier.fillMaxWidth().height(80.dp), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = DreamlandGold, modifier = Modifier.size(24.dp))
            }
        } else {
            UserDetailSection("BOOKINGS") {
                if (state.userBookings.isEmpty()) {
                    Text("No bookings", color = DreamlandMuted, style = MaterialTheme.typography.bodySmall)
                } else {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        state.userBookings.forEach { BookingRow(it) }
                    }
                }
            }

            // Stays section
            UserDetailSection("STAYS") {
                if (state.userStays.isEmpty()) {
                    Text("No stays", color = DreamlandMuted, style = MaterialTheme.typography.bodySmall)
                } else {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        state.userStays.forEach { StayRow(it) }
                    }
                }
            }
        }
    }
}

@Composable
private fun ProfileRow(label: String, value: String) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(label, style = MaterialTheme.typography.bodySmall, color = DreamlandMuted, modifier = Modifier.width(100.dp))
        Text(value, style = MaterialTheme.typography.bodySmall, color = DreamlandOnDark, modifier = Modifier.weight(1f))
    }
}

@Composable
private fun BookingRow(booking: Booking) {
    val dateFmt = remember { SimpleDateFormat("d MMM yy", Locale.getDefault()) }
    val statusColor = bookingStatusColor(booking.status)

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(DreamlandForestSurface)
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(
                "${dateFmt.format(booking.checkIn)} → ${dateFmt.format(booking.checkOut)}",
                style = MaterialTheme.typography.bodySmall,
                color = DreamlandOnDark,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.weight(1f),
            )
            // Status badge
            Box(
                Modifier
                    .clip(RoundedCornerShape(4.dp))
                    .background(statusColor.copy(alpha = 0.15f))
                    .padding(horizontal = 6.dp, vertical = 2.dp),
            ) {
                Text(booking.status, color = statusColor, fontSize = 9.sp, fontWeight = FontWeight.SemiBold)
            }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            if (booking.roomCategoryName.isNotBlank()) {
                Text(booking.roomCategoryName, style = MaterialTheme.typography.bodySmall, color = DreamlandMuted)
            }
            if (booking.roomNumber.isNotBlank()) {
                Text("· Room ${booking.roomNumber}", style = MaterialTheme.typography.bodySmall, color = DreamlandMuted)
            }
            Spacer(Modifier.weight(1f))
            // Source chip
            if (booking.source.isNotBlank()) {
                Box(
                    Modifier
                        .clip(RoundedCornerShape(3.dp))
                        .background(DreamlandGold.copy(alpha = 0.12f))
                        .padding(horizontal = 5.dp, vertical = 2.dp),
                ) {
                    Text(booking.source, style = MaterialTheme.typography.labelSmall, color = DreamlandGold.copy(alpha = 0.8f), fontSize = 9.sp)
                }
            }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(formatRupeesUsers(booking.totalAmount), style = MaterialTheme.typography.bodySmall, color = DreamlandOnDark, fontWeight = FontWeight.SemiBold)
            if (booking.advancePaidAmount > 0) {
                Text("· ${formatRupeesUsers(booking.advancePaidAmount)} paid", style = MaterialTheme.typography.bodySmall, color = Color(0xFF2ECC71))
            }
        }
    }
}

@Composable
private fun StayRow(stay: Stay) {
    val dateFmt = remember { SimpleDateFormat("d MMM yy", Locale.getDefault()) }
    val statusColor = if (stay.status == "ACTIVE") Color(0xFF4CAF50) else DreamlandMuted
    val checkOut = stay.checkOutActual ?: stay.expectedCheckOut

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(DreamlandForestSurface)
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(
                "${dateFmt.format(stay.checkInActual)} → ${dateFmt.format(checkOut)}",
                style = MaterialTheme.typography.bodySmall,
                color = DreamlandOnDark,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.weight(1f),
            )
            Box(
                Modifier
                    .clip(RoundedCornerShape(4.dp))
                    .background(statusColor.copy(alpha = 0.15f))
                    .padding(horizontal = 6.dp, vertical = 2.dp),
            ) {
                Text(stay.status, color = statusColor, fontSize = 9.sp, fontWeight = FontWeight.SemiBold)
            }
        }
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(
                buildString {
                    if (stay.roomCategoryName.isNotBlank()) append(stay.roomCategoryName)
                    if (stay.roomNumber.isNotBlank()) append(" · Room ${stay.roomNumber}")
                },
                style = MaterialTheme.typography.bodySmall,
                color = DreamlandMuted,
                modifier = Modifier.weight(1f),
            )
            if (stay.totalBilled > 0) {
                Text(formatRupeesUsers(stay.totalBilled), style = MaterialTheme.typography.bodySmall, color = DreamlandOnDark, fontWeight = FontWeight.SemiBold)
            }
        }
    }
}

// ── Helpers ───────────────────────────────────────────────────────────────────

@Composable
private fun UserDetailSection(title: String, content: @Composable () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Box(Modifier.width(3.dp).height(14.dp).clip(RoundedCornerShape(2.dp)).background(DreamlandGold))
            Text(title, style = MaterialTheme.typography.titleSmall, color = DreamlandGold, fontWeight = FontWeight.SemiBold, letterSpacing = 0.5.sp)
        }
        content()
    }
}

private fun bookingStatusColor(status: String): Color = when (status) {
    "CONFIRMED" -> Color(0xFFF39C12)
    "COMPLETED" -> Color(0xFF2ECC71)
    "CANCELLED" -> Color(0xFFE74C3C)
    "NO_SHOW"   -> Color(0xFF95A5A6)
    else        -> Color(0xFF8FA69E)
}

private fun formatRupeesUsers(amount: Double): String =
    "₹${NumberFormat.getNumberInstance(Locale("en", "IN")).format(amount.toLong())}"
