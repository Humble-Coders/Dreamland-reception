package com.example.dreamland_reception.shift

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.key.utf16CodePoint
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.dreamland_reception.DreamlandForestElevated
import com.example.dreamland_reception.DreamlandForestSurface
import com.example.dreamland_reception.DreamlandGold
import com.example.dreamland_reception.DreamlandMuted
import com.example.dreamland_reception.DreamlandOnDark
import com.example.dreamland_reception.data.AppContext
import com.example.dreamland_reception.data.repository.FirestoreLiquidityRepository
import com.example.dreamland_reception.data.repository.LiquidityBalance
import kotlinx.coroutines.launch

private val GREEN = Color(0xFF4CAF50)
private val BLUE = Color(0xFF42A5F5)
private val RED = Color(0xFFEF5350)

/**
 * Top-bar (left side) live Cash & Bank pills reflecting the Firestore `Liquidity` till —
 * the on-device mirror of the ledger's cash/bank. Styled identically to the ledger pills on
 * the right; fed by a live Firestore snapshot listener so the balances update in real time.
 *
 * Clicking the CASH pill opens a manual till-adjustment dialog (miscellaneous cash in/out that we
 * deliberately keep OUT of the ledger).
 */
@Composable
fun LiquidityPills() {
    val flow = remember { FirestoreLiquidityRepository.listen() }
    val balance by flow.collectAsStateWithLifecycle(initialValue = null as LiquidityBalance?)

    // Which till's adjust dialog is open: null | "cash" | "bank".
    var adjustAccount by remember { mutableStateOf<String?>(null) }

    balance?.let { bal ->
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            LiquidityPill("CASH", bal.cash, GREEN, onClick = { adjustAccount = "cash" })
            LiquidityPill("BANK", bal.bank, BLUE, onClick = { adjustAccount = "bank" })
        }
        when (adjustAccount) {
            "cash" -> TillAdjustDialog("cash", "Cash", bal.cash) { adjustAccount = null }
            "bank" -> TillAdjustDialog("bank", "Bank", bal.bank) { adjustAccount = null }
        }
    }
}

@Composable
private fun LiquidityPill(label: String, amount: Double, color: Color, onClick: (() -> Unit)? = null) {
    Row(
        Modifier
            .clip(RoundedCornerShape(20.dp))
            .background(DreamlandForestElevated)
            .border(1.dp, color.copy(alpha = 0.4f), RoundedCornerShape(20.dp))
            .let { if (onClick != null) it.clickable { onClick() } else it }
            .padding(horizontal = 10.dp, vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp),
    ) {
        Box(Modifier.size(6.dp).clip(CircleShape).background(color))
        Text(label, color = DreamlandMuted, fontSize = 9.sp, fontWeight = FontWeight.SemiBold)
        Text("₹${fmtMoneyLiquidity(amount)}", color = color, fontSize = 11.sp, fontWeight = FontWeight.Bold)
    }
}

// ── Cash adjustment dialog ──────────────────────────────────────────────────────

/**
 * Calculator-style manual till adjustment for [account] ("cash"/"bank", shown as [accountLabel]).
 * Adds/removes from the Firestore till ONLY — the accounting ledger is intentionally untouched
 * (for miscellaneous expenses we don't want in it). Records the on-duty manager + a note in
 * `registerTransactions`.
 */
@Composable
private fun TillAdjustDialog(account: String, accountLabel: String, currentCash: Double, onDismiss: () -> Unit) {
    val scope = rememberCoroutineScope()
    var isAdd by remember { mutableStateOf(false) }   // default: removing money (an expense)
    var amountText by remember { mutableStateOf("") }
    var note by remember { mutableStateOf("") }
    var isSaving by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    val manager = AppContext.currentManager
    val amount = amountText.toDoubleOrNull() ?: 0.0
    val newBalance = if (isAdd) currentCash + amount else currentCash - amount
    val overdraw = !isAdd && amount > currentCash + 0.0001
    val canConfirm = !isSaving && amount > 0.0 && note.isNotBlank() && !overdraw

    fun append(ch: Char) {
        val dotIndex = amountText.indexOf('.')
        when {
            ch == '.' && dotIndex >= 0 -> return
            ch == '.' -> amountText = if (amountText.isEmpty()) "0." else "$amountText."
            dotIndex >= 0 && amountText.length - dotIndex > 2 -> return   // max 2 decimals
            ch == '0' && amountText == "0" -> return                     // no leading zeros
            else -> amountText = if (amountText == "0") ch.toString() else amountText + ch
        }
        error = null
    }

    fun backspace() {
        if (amountText.isNotEmpty()) amountText = amountText.dropLast(1)
        error = null
    }

    fun submit() {
        val amt = amountText.toDoubleOrNull() ?: 0.0
        if (isSaving || amt <= 0.0 || note.isBlank() || (!isAdd && amt > currentCash + 0.0001)) return
        isSaving = true
        error = null
        scope.launch {
            FirestoreLiquidityRepository.recordManualAdjustment(
                account = account, amount = amt, isAdd = isAdd, note = note.trim(),
                manager = manager.ifBlank { "reception_unknown" }, hotelId = AppContext.hotelId,
            ).onSuccess { onDismiss() }
                .onFailure { isSaving = false; error = "Couldn't save. Please try again." }
        }
    }

    val focusRequester = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { focusRequester.requestFocus() } }

    Dialog(onDismissRequest = { if (!isSaving) onDismiss() }, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Box(
            Modifier
                .width(360.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(DreamlandForestSurface)
                .padding(20.dp)
                // Accept physical-keyboard input for the keypad. Uses bubbling onKeyEvent, so when
                // the Notes field is focused it handles typing and these never fire.
                .focusRequester(focusRequester)
                .focusable()
                .onKeyEvent { ev ->
                    if (ev.type != KeyEventType.KeyDown) return@onKeyEvent false
                    val ch = ev.utf16CodePoint.let { if (it in 33..126) it.toChar() else null }
                    when {
                        ev.key == Key.Backspace -> { backspace(); true }
                        ev.key == Key.Enter || ev.key == Key.NumPadEnter -> { submit(); true }
                        ch != null && ch.isDigit() -> { append(ch); true }
                        ch == '.' -> { append('.'); true }
                        else -> false
                    }
                },
        ) {
            Column {
                Text("${accountLabel.uppercase()} TILL", style = MaterialTheme.typography.labelLarge, color = DreamlandGold, letterSpacing = 2.sp)
                Spacer(Modifier.height(2.dp))
                Text("Not posted to the ledger", color = DreamlandMuted, fontSize = 11.sp)
                Spacer(Modifier.height(12.dp))

                // Current balance
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("Current $accountLabel", color = DreamlandMuted, fontSize = 13.sp)
                    Text("₹${fmtMoneyLiquidity(currentCash)}", color = DreamlandOnDark, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                }
                Spacer(Modifier.height(12.dp))

                // Add / Remove toggle
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    ModeButton("＋ Add", selected = isAdd, color = GREEN, modifier = Modifier.weight(1f)) { isAdd = true; error = null }
                    ModeButton("－ Remove", selected = !isAdd, color = RED, modifier = Modifier.weight(1f)) { isAdd = false; error = null }
                }
                Spacer(Modifier.height(14.dp))

                // Big amount display
                Box(
                    Modifier.fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(DreamlandForestElevated)
                        .padding(vertical = 16.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        "${if (isAdd) "+" else "−"} ₹${if (amountText.isEmpty()) "0" else amountText}",
                        color = if (isAdd) GREEN else RED, fontSize = 30.sp, fontWeight = FontWeight.Bold,
                    )
                }
                Spacer(Modifier.height(10.dp))

                // Keypad
                val rows = listOf(listOf('1', '2', '3'), listOf('4', '5', '6'), listOf('7', '8', '9'), listOf('.', '0', '⌫'))
                rows.forEach { row ->
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        row.forEach { key ->
                            KeypadKey(key.toString(), Modifier.weight(1f)) {
                                if (key == '⌫') backspace() else append(key)
                            }
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                }

                Spacer(Modifier.height(4.dp))
                // New balance preview
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("New $accountLabel balance", color = DreamlandMuted, fontSize = 13.sp)
                    Text(
                        "₹${fmtMoneyLiquidity(newBalance)}",
                        color = if (overdraw) RED else DreamlandGold, fontSize = 15.sp, fontWeight = FontWeight.Bold,
                    )
                }
                if (overdraw) {
                    Spacer(Modifier.height(4.dp))
                    Text("Can't remove more than the current $accountLabel (₹${fmtMoneyLiquidity(currentCash)}).", color = RED, fontSize = 11.sp)
                }
                Spacer(Modifier.height(12.dp))

                // Notes (required)
                OutlinedTextField(
                    value = note,
                    onValueChange = { note = it.take(200); error = null },
                    enabled = !isSaving,
                    label = { Text("Notes", color = DreamlandMuted) },
                    placeholder = { Text("e.g. staff tea & snacks", color = DreamlandMuted.copy(alpha = 0.5f)) },
                    singleLine = false,
                    minLines = 2,
                    maxLines = 3,
                    modifier = Modifier.fillMaxWidth(),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = DreamlandOnDark, unfocusedTextColor = DreamlandOnDark,
                        focusedBorderColor = DreamlandGold, unfocusedBorderColor = DreamlandMuted,
                        cursorColor = DreamlandGold,
                    ),
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    "Recorded by: ${manager.ifBlank { "—" }}",
                    color = DreamlandMuted, fontSize = 11.sp,
                )

                error?.let {
                    Spacer(Modifier.height(8.dp))
                    Text(it, color = RED, fontSize = 12.sp)
                }

                Spacer(Modifier.height(16.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    TextButton(onClick = onDismiss, enabled = !isSaving, modifier = Modifier.weight(1f)) {
                        Text("Cancel", color = DreamlandMuted)
                    }
                    Button(
                        onClick = { submit() },
                        enabled = canConfirm,
                        modifier = Modifier.weight(1.6f).height(46.dp),
                        shape = RoundedCornerShape(10.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (isAdd) GREEN else RED,
                            disabledContainerColor = DreamlandForestElevated,
                        ),
                    ) {
                        if (isSaving) {
                            CircularProgressIndicator(Modifier.size(18.dp), color = Color.White, strokeWidth = 2.dp)
                        } else {
                            Text(if (isAdd) "Add to $accountLabel" else "Remove from $accountLabel", color = Color.White, fontWeight = FontWeight.SemiBold)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ModeButton(label: String, selected: Boolean, color: Color, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Box(
        modifier
            .height(40.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(if (selected) color.copy(alpha = 0.18f) else DreamlandForestElevated)
            .border(1.dp, if (selected) color else DreamlandMuted.copy(alpha = 0.3f), RoundedCornerShape(10.dp))
            .clickable { onClick() },
        contentAlignment = Alignment.Center,
    ) {
        Text(label, color = if (selected) color else DreamlandMuted, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
    }
}

@Composable
private fun KeypadKey(label: String, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Box(
        modifier
            .height(46.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(DreamlandForestElevated)
            .border(1.dp, DreamlandGold.copy(alpha = 0.15f), RoundedCornerShape(10.dp))
            .clickable { onClick() },
        contentAlignment = Alignment.Center,
    ) {
        Text(label, color = DreamlandOnDark, fontSize = 18.sp, fontWeight = FontWeight.Medium)
    }
}

private fun fmtMoneyLiquidity(d: Double): String {
    val r = Math.round(d * 100.0) / 100.0
    return if (r == r.toLong().toDouble()) "%,d".format(r.toLong()) else "%,.2f".format(r)
}
