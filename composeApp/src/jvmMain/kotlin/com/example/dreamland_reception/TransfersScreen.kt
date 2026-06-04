package com.example.dreamland_reception

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.dreamland_reception.data.model.Transfer
import com.example.dreamland_reception.transfers.TransferDialog
import com.example.dreamland_reception.ui.viewmodel.TransfersViewModel
import java.text.SimpleDateFormat
import java.util.Locale

private val tFmt = SimpleDateFormat("dd MMM, HH:mm", Locale.getDefault())

@Composable
fun TransfersScreen(
    vm: TransfersViewModel = DreamlandAppInitializer.getTransfersViewModel(),
) {
    val state by vm.state.collectAsStateWithLifecycle()
    var showAdd by remember { mutableStateOf(false) }

    Column(Modifier.fillMaxSize().background(DreamlandForest)) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Column(Modifier.width(170.dp)) {
                Text("MONEY", style = MaterialTheme.typography.labelSmall, color = DreamlandGold, letterSpacing = 2.sp)
                Text("Transfers", style = MaterialTheme.typography.titleLarge, color = DreamlandOnDark, fontWeight = FontWeight.Bold)
            }
            OutlinedTextField(
                value = state.searchQuery,
                onValueChange = { vm.onSearch(it) },
                placeholder = { Text("Search party or notes…", color = DreamlandMuted, fontSize = 12.sp) },
                singleLine = true,
                modifier = Modifier.weight(1f),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedTextColor = DreamlandOnDark, unfocusedTextColor = DreamlandOnDark,
                    focusedBorderColor = DreamlandGold, unfocusedBorderColor = DreamlandMuted.copy(alpha = 0.4f), cursorColor = DreamlandGold,
                ),
            )
            Button(
                onClick = { vm.loadParties(); showAdd = true },
                colors = ButtonDefaults.buttonColors(containerColor = DreamlandGold),
                shape = RoundedCornerShape(8.dp),
                modifier = Modifier.height(40.dp),
            ) { Text("+ New Transfer", color = Color(0xFF0D1F17), fontWeight = FontWeight.SemiBold, fontSize = 13.sp) }
        }
        HorizontalDivider(color = DreamlandGold.copy(alpha = 0.15f))

        Column(Modifier.fillMaxSize().padding(horizontal = 24.dp, vertical = 12.dp)) {
            TransferTableHeader()
            HorizontalDivider(color = DreamlandGold.copy(alpha = 0.18f))
            when {
                state.isLoading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator(color = DreamlandGold) }
                state.filtered.isEmpty() -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("No transfers yet.", color = DreamlandMuted, style = MaterialTheme.typography.bodyMedium)
                }
                else -> LazyColumn(Modifier.weight(1f)) {
                    items(state.filtered, key = { it.id }) { t ->
                        TransferRow(t)
                        HorizontalDivider(color = DreamlandMuted.copy(alpha = 0.08f))
                    }
                }
            }
            Row(Modifier.fillMaxWidth().padding(vertical = 10.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("${state.transfers.size} transfer${if (state.transfers.size != 1) "s" else ""}", color = DreamlandMuted, style = MaterialTheme.typography.labelMedium)
            }
        }
    }

    if (showAdd) {
        TransferDialog(
            parties = state.parties,
            isSubmitting = state.isSubmitting,
            onAddCustomer = { name, phone, onCreated -> vm.addCustomer(name, phone, onCreated) },
            onAddVendor = { name, phone, onCreated -> vm.addVendor(name, phone, onCreated) },
            onConfirm = { from, to, amount, notes ->
                vm.createTransfer(from, to, amount, notes) { ok -> if (ok) showAdd = false }
            },
            onDismiss = { showAdd = false },
        )
    }
}

private const val W_DATE = 1.2f
private const val W_FROM = 2.0f
private const val W_TO = 2.0f
private const val W_NOTES = 2.2f
private const val W_AMOUNT = 1.1f
private const val W_STATUS = 1.0f

@Composable
private fun TransferTableHeader() {
    Row(Modifier.fillMaxWidth().padding(vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
        THeader("DATE", W_DATE)
        THeader("FROM", W_FROM)
        THeader("TO", W_TO)
        THeader("NOTES", W_NOTES)
        THeader("AMOUNT", W_AMOUNT)
        THeader("STATUS", W_STATUS)
    }
}

@Composable
private fun androidx.compose.foundation.layout.RowScope.THeader(text: String, weight: Float) {
    Text(text, modifier = Modifier.weight(weight), style = MaterialTheme.typography.labelSmall, color = DreamlandGold, letterSpacing = 1.sp, fontWeight = FontWeight.SemiBold)
}

@Composable
private fun TransferRow(t: Transfer) {
    Row(Modifier.fillMaxWidth().padding(vertical = 14.dp), verticalAlignment = Alignment.Top) {
        Text(tFmt.format(t.createdAt), modifier = Modifier.weight(W_DATE).padding(end = 8.dp), color = DreamlandMuted, style = MaterialTheme.typography.bodySmall)
        Text(t.fromName, modifier = Modifier.weight(W_FROM).padding(end = 8.dp), color = DreamlandOnDark, style = MaterialTheme.typography.bodySmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
        Text(t.toName, modifier = Modifier.weight(W_TO).padding(end = 8.dp), color = DreamlandOnDark, style = MaterialTheme.typography.bodySmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
        Text(t.notes.ifBlank { "—" }, modifier = Modifier.weight(W_NOTES).padding(end = 8.dp), color = if (t.notes.isBlank()) DreamlandMuted else DreamlandOnDark, style = MaterialTheme.typography.bodySmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
        Text("₹${fmtMoneyT(t.amount)}", modifier = Modifier.weight(W_AMOUNT), color = DreamlandGold, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.SemiBold)
        Box(Modifier.weight(W_STATUS)) { TStatusChip(t) }
    }
}

@Composable
private fun TStatusChip(t: Transfer) {
    val (label, color) = when {
        t.synced -> "Synced" to Color(0xFF4CAF50)
        t.syncError.isNotBlank() -> "Failed" to Color(0xFFEF5350)
        else -> "Pending" to Color(0xFFFFC107)
    }
    Box(Modifier.clip(RoundedCornerShape(20.dp)).background(color.copy(alpha = 0.14f)).padding(horizontal = 9.dp, vertical = 3.dp)) {
        Text(label, color = color, fontSize = 9.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.5.sp)
    }
}

private fun fmtMoneyT(d: Double): String {
    val r = Math.round(d * 100.0) / 100.0
    return if (r == r.toLong().toDouble()) "%,d".format(r.toLong()) else "%,.2f".format(r)
}
