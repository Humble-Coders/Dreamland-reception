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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.dreamland_reception.data.model.Expense
import com.example.dreamland_reception.expenses.AddExpenseDialog
import com.example.dreamland_reception.ui.viewmodel.ExpensesViewModel
import java.text.SimpleDateFormat
import java.util.Locale

private val dtFmt = SimpleDateFormat("dd MMM, HH:mm", Locale.getDefault())

@Composable
fun ExpensesScreen(
    vm: ExpensesViewModel = DreamlandAppInitializer.getExpensesViewModel(),
) {
    val state by vm.state.collectAsStateWithLifecycle()
    val vendors by vm.vendors.collectAsStateWithLifecycle()
    var showAdd by remember { mutableStateOf(false) }
    var confirmDeleteId by remember { mutableStateOf<String?>(null) }

    Column(Modifier.fillMaxSize().background(DreamlandForest)) {
        // ── Top bar ───────────────────────────────────────────────────────────
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Column(Modifier.width(160.dp)) {
                Text("OUTGOING", style = MaterialTheme.typography.labelSmall, color = DreamlandGold, letterSpacing = 2.sp)
                Text("Expenses", style = MaterialTheme.typography.titleLarge, color = DreamlandOnDark, fontWeight = FontWeight.Bold)
            }
            OutlinedTextField(
                value = state.searchQuery,
                onValueChange = { vm.onSearch(it) },
                placeholder = { Text("Search title, vendor, notes…", color = DreamlandMuted, fontSize = 12.sp) },
                singleLine = true,
                modifier = Modifier.weight(1f),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedTextColor = DreamlandOnDark, unfocusedTextColor = DreamlandOnDark,
                    focusedBorderColor = DreamlandGold, unfocusedBorderColor = DreamlandMuted.copy(alpha = 0.4f), cursorColor = DreamlandGold,
                ),
            )
            Button(
                onClick = { showAdd = true },
                colors = ButtonDefaults.buttonColors(containerColor = DreamlandGold),
                shape = RoundedCornerShape(8.dp),
                modifier = Modifier.height(40.dp),
            ) { Text("+ New Expense", color = Color(0xFF0D1F17), fontWeight = FontWeight.SemiBold, fontSize = 13.sp) }
        }
        HorizontalDivider(color = DreamlandGold.copy(alpha = 0.15f))

        Column(Modifier.fillMaxSize().padding(horizontal = 24.dp, vertical = 12.dp)) {
            ExpenseTableHeader()
            HorizontalDivider(color = DreamlandGold.copy(alpha = 0.18f))
            when {
                state.isLoading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator(color = DreamlandGold) }
                state.filtered.isEmpty() -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("No expenses yet.", color = DreamlandMuted, style = MaterialTheme.typography.bodyMedium)
                }
                else -> LazyColumn(Modifier.weight(1f)) {
                    items(state.filtered, key = { it.id }) { expense ->
                        ExpenseRow(
                            e = expense,
                            deleting = state.deletingId == expense.id,
                            onDelete = { confirmDeleteId = expense.id },
                        )
                        HorizontalDivider(color = DreamlandMuted.copy(alpha = 0.08f))
                    }
                }
            }
            state.error?.let { err ->
                Text(err, color = Color(0xFFEF5350), style = MaterialTheme.typography.labelMedium, modifier = Modifier.padding(top = 8.dp))
            }
            Row(Modifier.fillMaxWidth().padding(vertical = 10.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("${state.expenses.size} expense${if (state.expenses.size != 1) "s" else ""}", color = DreamlandMuted, style = MaterialTheme.typography.labelMedium)
                Text("Total spent: ₹${fmtMoney(state.totalSpent)}", color = DreamlandGold, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold)
            }
        }
    }

    if (showAdd) {
        AddExpenseDialog(
            vendors = vendors,
            onAddVendor = { name, phone, onCreated -> vm.addVendor(name, phone, onCreated = onCreated) },
            onLoadBalance = { externalId -> vm.vendorBalance(externalId) },
            onConfirm = { title, notes, amount, vendorId, vendorName, cash, bank ->
                showAdd = false
                vm.createExpense(title, notes, amount, vendorId, vendorName, cash, bank)
            },
            onDismiss = { showAdd = false },
        )
    }

    confirmDeleteId?.let { id ->
        val expense = state.expenses.find { it.id == id }
        if (expense == null) {
            confirmDeleteId = null
        } else {
            AlertDialog(
                onDismissRequest = { confirmDeleteId = null },
                containerColor = DreamlandForestElevated,
                titleContentColor = DreamlandOnDark,
                textContentColor = DreamlandMuted,
                shape = RoundedCornerShape(14.dp),
                title = { Text("Delete expense?", fontWeight = FontWeight.SemiBold) },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(
                            "${expense.title.ifBlank { expense.vendorName.ifBlank { "Expense" } }}  ·  ₹${fmtMoney(expense.amount)}",
                            color = DreamlandOnDark, fontWeight = FontWeight.SemiBold,
                        )
                        Text(
                            if (expense.synced) "This reverses it in the ledger and returns the cash/bank to the till."
                            else "This removes the expense. It was never posted to the ledger.",
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                },
                confirmButton = {
                    Button(
                        onClick = { vm.deleteExpense(id); confirmDeleteId = null },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFEF5350)),
                        shape = RoundedCornerShape(8.dp),
                    ) { Text("Delete", color = Color.White, fontWeight = FontWeight.SemiBold) }
                },
                dismissButton = {
                    TextButton(onClick = { confirmDeleteId = null }) { Text("Cancel", color = DreamlandMuted) }
                },
            )
        }
    }
}

// Shared column weights so header + rows align (Logs-table pattern).
private const val W_DATE = 1.1f
private const val W_TITLE = 2.2f
private const val W_VENDOR = 1.5f
private const val W_PAID = 1.7f
private const val W_AMOUNT = 1.0f
private const val W_STATUS = 1.0f
private const val W_ACTION = 0.6f

@Composable
private fun ExpenseTableHeader() {
    Row(Modifier.fillMaxWidth().padding(vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
        HeaderCell("DATE", W_DATE)
        HeaderCell("TITLE", W_TITLE)
        HeaderCell("VENDOR", W_VENDOR)
        HeaderCell("PAID (cash / bank)", W_PAID)
        HeaderCell("AMOUNT", W_AMOUNT)
        HeaderCell("STATUS", W_STATUS)
        HeaderCell("", W_ACTION)
    }
}

@Composable
private fun androidx.compose.foundation.layout.RowScope.HeaderCell(text: String, weight: Float) {
    Text(text, modifier = Modifier.weight(weight), style = MaterialTheme.typography.labelSmall, color = DreamlandGold, letterSpacing = 1.sp, fontWeight = FontWeight.SemiBold)
}

@Composable
private fun ExpenseRow(e: Expense, deleting: Boolean = false, onDelete: () -> Unit = {}) {
    Row(Modifier.fillMaxWidth().padding(vertical = 14.dp), verticalAlignment = Alignment.Top) {
        Text(dtFmt.format(e.createdAt), modifier = Modifier.weight(W_DATE).padding(end = 8.dp), color = DreamlandMuted, style = MaterialTheme.typography.bodySmall)
        Column(Modifier.weight(W_TITLE).padding(end = 8.dp)) {
            Text(e.title.ifBlank { "Expense" }, color = DreamlandOnDark, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (e.notes.isNotBlank()) Text(e.notes, color = DreamlandMuted, style = MaterialTheme.typography.labelSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        Text(e.vendorName.ifBlank { "Direct" }, modifier = Modifier.weight(W_VENDOR).padding(end = 8.dp), color = if (e.vendorName.isBlank()) DreamlandMuted else DreamlandOnDark, style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Column(Modifier.weight(W_PAID).padding(end = 8.dp)) {
            if (e.cashPaid > 0.0) Text("Cash ₹${fmtMoney(e.cashPaid)}", color = DreamlandOnDark, style = MaterialTheme.typography.labelMedium)
            if (e.bankPaid > 0.0) Text("Bank ₹${fmtMoney(e.bankPaid)}", color = DreamlandOnDark, style = MaterialTheme.typography.labelMedium)
            if (e.cashPaid <= 0.0 && e.bankPaid <= 0.0) Text("—", color = DreamlandMuted, style = MaterialTheme.typography.labelMedium)
        }
        Text("₹${fmtMoney(e.amount)}", modifier = Modifier.weight(W_AMOUNT), color = DreamlandGold, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.SemiBold)
        Box(Modifier.weight(W_STATUS)) { StatusChip(e) }
        Box(Modifier.weight(W_ACTION), contentAlignment = Alignment.CenterEnd) {
            if (deleting) {
                CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp, color = Color(0xFFEF5350))
            } else {
                IconButton(onClick = onDelete, modifier = Modifier.size(30.dp)) {
                    Icon(Icons.Filled.Delete, contentDescription = "Delete expense", tint = Color(0xFFEF5350).copy(alpha = 0.75f), modifier = Modifier.size(18.dp))
                }
            }
        }
    }
}

@Composable
private fun StatusChip(e: Expense) {
    val (label, color) = when {
        e.synced -> "Synced" to Color(0xFF4CAF50)
        e.syncError.isNotBlank() -> "Failed" to Color(0xFFEF5350)
        else -> "Pending" to Color(0xFFFFC107)
    }
    Box(Modifier.clip(RoundedCornerShape(20.dp)).background(color.copy(alpha = 0.14f)).padding(horizontal = 9.dp, vertical = 3.dp)) {
        Text(label, color = color, fontSize = 9.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.5.sp)
    }
}

private fun fmtMoney(d: Double): String {
    val r = Math.round(d * 100.0) / 100.0
    return if (r == r.toLong().toDouble()) "%,d".format(r.toLong()) else "%,.2f".format(r)
}
