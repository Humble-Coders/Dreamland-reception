package com.example.dreamland_reception.shift

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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.dreamland_reception.DreamlandAppInitializer
import com.example.dreamland_reception.DreamlandForestElevated
import com.example.dreamland_reception.DreamlandForestSurface
import com.example.dreamland_reception.DreamlandGold
import com.example.dreamland_reception.DreamlandMuted
import com.example.dreamland_reception.DreamlandOnDark
import com.example.dreamland_reception.data.accounting.AccountingRepository
import com.example.dreamland_reception.data.accounting.CashBankBalance
import com.example.dreamland_reception.data.model.ReceptionManager
import com.example.dreamland_reception.ui.viewmodel.ShiftViewModel
import kotlinx.coroutines.delay

private val GREEN = Color(0xFF4CAF50)
private val BLUE = Color(0xFF42A5F5)

/**
 * Top-bar control: live Cash & Bank pills + the on-duty manager dropdown. Switching
 * managers requires the incoming manager's password and logs the handover (with the
 * balances at that point). Rendered in the app shell so it's on every screen.
 */
@Composable
fun ShiftBar(vm: ShiftViewModel = DreamlandAppInitializer.getShiftViewModel()) {
    val currentManager by vm.currentManager.collectAsStateWithLifecycle()
    val managers by vm.managers.collectAsStateWithLifecycle()

    var balance by remember { mutableStateOf<CashBankBalance?>(null) }
    LaunchedEffect(Unit) {
        while (true) {
            balance = runCatching { AccountingRepository.fetchCashBankBalance() }.getOrNull() ?: balance
            delay(5_000)
        }
    }

    var dropdownOpen by remember { mutableStateOf(false) }
    var handoverTarget by remember { mutableStateOf<ReceptionManager?>(null) }
    var showAdd by remember { mutableStateOf(false) }

    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        balance?.let { bal ->
            Pill("CASH", bal.cash, GREEN)
            Pill("BANK", bal.bank, BLUE)
        }

        // On-duty manager dropdown
        Box {
            Row(
                modifier = Modifier
                    .clip(RoundedCornerShape(20.dp))
                    .background(DreamlandGold.copy(alpha = 0.15f))
                    .border(1.dp, DreamlandGold.copy(alpha = 0.45f), RoundedCornerShape(20.dp))
                    .clickable { dropdownOpen = true }
                    .padding(horizontal = 11.dp, vertical = 5.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(5.dp),
            ) {
                Icon(Icons.Filled.Person, contentDescription = null, tint = DreamlandGold, modifier = Modifier.size(14.dp))
                Text("ON DUTY", color = DreamlandMuted, fontSize = 8.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 0.5.sp)
                Text(currentManager.ifBlank { "Set manager" }, color = DreamlandOnDark, fontSize = 12.sp, fontWeight = FontWeight.Bold, maxLines = 1)
                Text("▾", color = DreamlandGold, fontSize = 11.sp)
            }
            DropdownMenu(expanded = dropdownOpen, onDismissRequest = { dropdownOpen = false }, modifier = Modifier.background(DreamlandForestElevated)) {
                if (managers.isEmpty()) {
                    DropdownMenuItem(enabled = false, text = { Text("No managers yet", color = DreamlandMuted) }, onClick = {})
                }
                managers.forEach { m ->
                    val onDuty = m.name.equals(currentManager, ignoreCase = true)
                    DropdownMenuItem(
                        text = { Text(if (onDuty) "${m.name}  • on duty" else m.name, color = if (onDuty) DreamlandGold else DreamlandOnDark) },
                        onClick = { dropdownOpen = false; if (!onDuty) handoverTarget = m },
                    )
                }
                HorizontalDivider()
                DropdownMenuItem(
                    text = { Text("+ Add manager", color = DreamlandGold, fontWeight = FontWeight.SemiBold) },
                    onClick = { dropdownOpen = false; showAdd = true },
                )
            }
        }
    }

    handoverTarget?.let { target ->
        HandoverPasswordDialog(
            manager = target,
            onVerify = { pw, cb -> vm.handover(target.name, pw, cb) },
            onDismiss = { handoverTarget = null },
        )
    }
    if (showAdd) {
        AddManagerDialog(
            onAdd = { name, pw, cb -> vm.addManager(name, pw, cb) },
            onDismiss = { showAdd = false },
        )
    }
}

@Composable
private fun Pill(label: String, amount: Double, color: Color) {
    Row(
        Modifier.clip(RoundedCornerShape(20.dp)).background(DreamlandForestElevated)
            .border(1.dp, color.copy(alpha = 0.4f), RoundedCornerShape(20.dp)).padding(horizontal = 10.dp, vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp),
    ) {
        Box(Modifier.size(6.dp).clip(CircleShape).background(color))
        Text(label, color = DreamlandMuted, fontSize = 9.sp, fontWeight = FontWeight.SemiBold)
        Text("₹${fmtMoney(amount)}", color = color, fontSize = 11.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun HandoverPasswordDialog(
    manager: ReceptionManager,
    onVerify: (password: String, onResult: (Boolean, String?) -> Unit) -> Unit,
    onDismiss: () -> Unit,
) {
    var password by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }
    Dialog(onDismissRequest = { if (!busy) onDismiss() }, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Box(Modifier.fillMaxWidth(0.32f).clip(RoundedCornerShape(16.dp)).background(DreamlandForestSurface).padding(24.dp)) {
            Column(Modifier.fillMaxWidth()) {
                Text("HAND OVER DESK", style = MaterialTheme.typography.labelLarge, color = DreamlandGold, letterSpacing = 2.sp)
                Spacer(Modifier.height(4.dp))
                Text("To ${manager.name}", style = MaterialTheme.typography.headlineSmall, color = DreamlandOnDark)
                Spacer(Modifier.height(16.dp))
                ShiftField(password, { password = it; error = null }, "${manager.name}'s password", isPassword = true)
                if (error != null) {
                    Spacer(Modifier.height(8.dp))
                    Text(error!!, color = Color(0xFFEF5350), style = MaterialTheme.typography.bodySmall)
                }
                Spacer(Modifier.height(18.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    TextButton(onClick = onDismiss, enabled = !busy, modifier = Modifier.weight(1f).height(44.dp)) { Text("Cancel", color = DreamlandMuted) }
                    Button(
                        onClick = {
                            busy = true
                            onVerify(password) { ok, err -> busy = false; if (ok) onDismiss() else error = err }
                        },
                        enabled = !busy && password.isNotEmpty(),
                        modifier = Modifier.weight(1.4f).height(44.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = DreamlandGold, disabledContainerColor = DreamlandGold.copy(alpha = 0.3f)),
                        shape = RoundedCornerShape(8.dp),
                    ) { Text("Confirm Handover", color = Color(0xFF0D1F17), fontWeight = FontWeight.SemiBold) }
                }
            }
        }
    }
}

@Composable
private fun AddManagerDialog(
    onAdd: (name: String, password: String, onResult: (Boolean, String?) -> Unit) -> Unit,
    onDismiss: () -> Unit,
) {
    var name by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }
    Dialog(onDismissRequest = { if (!busy) onDismiss() }, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Box(Modifier.fillMaxWidth(0.32f).clip(RoundedCornerShape(16.dp)).background(DreamlandForestSurface).padding(24.dp)) {
            Column(Modifier.fillMaxWidth()) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Text("ADD MANAGER", style = MaterialTheme.typography.labelLarge, color = DreamlandGold, letterSpacing = 2.sp)
                    TextButton(onClick = onDismiss, enabled = !busy) { Text("Cancel", color = DreamlandMuted) }
                }
                Spacer(Modifier.height(16.dp))
                ShiftField(name, { name = it; error = null }, "Manager name")
                Spacer(Modifier.height(10.dp))
                ShiftField(password, { password = it; error = null }, "Set a password", isPassword = true)
                if (error != null) {
                    Spacer(Modifier.height(8.dp))
                    Text(error!!, color = Color(0xFFEF5350), style = MaterialTheme.typography.bodySmall)
                }
                Spacer(Modifier.height(18.dp))
                Button(
                    onClick = {
                        busy = true
                        onAdd(name, password) { ok, err -> busy = false; if (ok) onDismiss() else error = err }
                    },
                    enabled = !busy && name.trim().isNotEmpty() && password.isNotEmpty(),
                    modifier = Modifier.fillMaxWidth().height(44.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = DreamlandGold, disabledContainerColor = DreamlandGold.copy(alpha = 0.3f)),
                    shape = RoundedCornerShape(8.dp),
                ) { Text("Add manager", color = Color(0xFF0D1F17), fontWeight = FontWeight.SemiBold) }
            }
        }
    }
}

@Composable
private fun ShiftField(value: String, onChange: (String) -> Unit, label: String, isPassword: Boolean = false) {
    OutlinedTextField(
        value = value, onValueChange = onChange,
        placeholder = { Text(label, color = DreamlandMuted, fontSize = 12.sp) },
        singleLine = true, modifier = Modifier.fillMaxWidth(),
        visualTransformation = if (isPassword) androidx.compose.ui.text.input.PasswordVisualTransformation() else androidx.compose.ui.text.input.VisualTransformation.None,
        colors = OutlinedTextFieldDefaults.colors(
            focusedTextColor = DreamlandOnDark, unfocusedTextColor = DreamlandOnDark,
            focusedBorderColor = DreamlandGold, unfocusedBorderColor = DreamlandMuted.copy(alpha = 0.4f), cursorColor = DreamlandGold,
        ),
    )
}

private fun fmtMoney(d: Double): String {
    val r = Math.round(d * 100.0) / 100.0
    return if (r == r.toLong().toDouble()) "%,d".format(r.toLong()) else "%,.2f".format(r)
}
