package com.example.dreamland_reception.transfers

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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
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
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.dreamland_reception.DreamlandForest
import com.example.dreamland_reception.DreamlandForestElevated
import com.example.dreamland_reception.DreamlandForestSurface
import com.example.dreamland_reception.DreamlandGold
import com.example.dreamland_reception.DreamlandMuted
import com.example.dreamland_reception.DreamlandOnDark
import com.example.dreamland_reception.ui.viewmodel.TransferParty

private val GREEN = Color(0xFF4CAF50)
private val INK = Color(0xFF0D1F17)

/**
 * Owner money-transfer dialog. Pick a "from" and a "to" party (Cash, Bank, a
 * customer, or a vendor — searchable by name AND phone), enter an amount + notes,
 * and the ledger records `DR(to) / CR(from)`. Either side can inline-create a
 * customer or vendor.
 */
@Composable
fun TransferDialog(
    parties: List<TransferParty>,
    isSubmitting: Boolean,
    onAddCustomer: (name: String, phone: String, onCreated: (TransferParty) -> Unit) -> Unit,
    onAddVendor: (name: String, phone: String, onCreated: (TransferParty) -> Unit) -> Unit,
    onConfirm: (from: TransferParty, to: TransferParty, amount: Double, notes: String) -> Unit,
    onDismiss: () -> Unit,
) {
    var from by remember { mutableStateOf<TransferParty?>(null) }
    var to by remember { mutableStateOf<TransferParty?>(null) }
    var amountText by remember { mutableStateOf("") }
    var notesText by remember { mutableStateOf("") }

    // null = closed; "FROM"/"TO" = which side the selector is choosing for.
    var selectorSide by remember { mutableStateOf<String?>(null) }

    val amount = amountText.toDoubleOrNull() ?: 0.0
    val sameParty = from != null && to != null && from!!.kind == to!!.kind && from!!.refId == to!!.refId
    val canConfirm = from != null && to != null && amount > 0.0 && !sameParty && !isSubmitting

    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Box(
            modifier = Modifier
                .fillMaxWidth(0.42f)
                .clip(RoundedCornerShape(16.dp))
                .background(DreamlandForestSurface)
                .padding(24.dp),
        ) {
            Column(Modifier.fillMaxWidth()) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Column {
                        Text("MONEY TRANSFER", style = MaterialTheme.typography.labelLarge, color = DreamlandGold, letterSpacing = 2.sp)
                        Text("Move money", style = MaterialTheme.typography.headlineSmall, color = DreamlandOnDark)
                    }
                    TextButton(onClick = onDismiss) { Text("Cancel", color = DreamlandMuted) }
                }
                Spacer(Modifier.height(18.dp))

                Text("FROM", style = MaterialTheme.typography.labelSmall, color = DreamlandMuted, letterSpacing = 1.sp)
                Spacer(Modifier.height(6.dp))
                PartyBox(from, "Choose where money comes from") { selectorSide = "FROM" }

                Spacer(Modifier.height(10.dp))
                Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                    Text("↓", color = DreamlandGold, fontSize = 22.sp, fontWeight = FontWeight.Bold)
                }
                Spacer(Modifier.height(10.dp))

                Text("TO", style = MaterialTheme.typography.labelSmall, color = DreamlandMuted, letterSpacing = 1.sp)
                Spacer(Modifier.height(6.dp))
                PartyBox(to, "Choose where money goes") { selectorSide = "TO" }

                Spacer(Modifier.height(16.dp))
                Text("AMOUNT (₹)", style = MaterialTheme.typography.labelSmall, color = DreamlandMuted, letterSpacing = 1.sp)
                Spacer(Modifier.height(6.dp))
                TField(amountText, { amountText = it.filterMoney() }, "0")

                Spacer(Modifier.height(14.dp))
                Text("NOTES (optional)", style = MaterialTheme.typography.labelSmall, color = DreamlandMuted, letterSpacing = 1.sp)
                Spacer(Modifier.height(6.dp))
                TField(notesText, { notesText = it }, "Reason / reference")

                Spacer(Modifier.height(14.dp))
                val (msg, msgColor) = when {
                    sameParty -> "From and To can't be the same" to Color(0xFFEF5350)
                    from != null && to != null && amount > 0.0 ->
                        "₹${fmt(amount)} · ${from!!.label} → ${to!!.label}" to GREEN
                    else -> "Pick both sides and enter an amount" to DreamlandMuted
                }
                Box(Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp)).background(msgColor.copy(alpha = 0.12f)).padding(horizontal = 12.dp, vertical = 10.dp)) {
                    Text(msg, color = msgColor, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.SemiBold)
                }

                Spacer(Modifier.height(18.dp))
                Button(
                    onClick = { onConfirm(from!!, to!!, round2(amount), notesText.trim()) },
                    enabled = canConfirm,
                    modifier = Modifier.fillMaxWidth().height(46.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = GREEN, disabledContainerColor = GREEN.copy(alpha = 0.3f)),
                    shape = RoundedCornerShape(10.dp),
                ) { Text(if (isSubmitting) "Saving…" else "Record Transfer", color = INK, fontWeight = FontWeight.SemiBold) }
            }
        }
    }

    selectorSide?.let { side ->
        PartySelectorDialog(
            title = if (side == "FROM") "Money from" else "Money to",
            parties = parties,
            onAddCustomer = onAddCustomer,
            onAddVendor = onAddVendor,
            onPick = { picked ->
                if (side == "FROM") from = picked else to = picked
                selectorSide = null
            },
            onDismiss = { selectorSide = null },
        )
    }
}

@Composable
private fun PartyBox(party: TransferParty?, placeholder: String, onClick: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp))
            .border(1.dp, DreamlandMuted.copy(alpha = 0.35f), RoundedCornerShape(10.dp))
            .background(DreamlandForestElevated).clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 13.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (party != null) {
            KindTag(party.groupLabel)
            Text(
                party.label, color = DreamlandOnDark, style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.weight(1f).padding(start = 10.dp), maxLines = 1, overflow = TextOverflow.Ellipsis,
            )
        } else {
            Text(placeholder, color = DreamlandMuted, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
        }
        Text("▾", color = DreamlandMuted)
    }
}

@Composable
private fun PartySelectorDialog(
    title: String,
    parties: List<TransferParty>,
    onAddCustomer: (name: String, phone: String, onCreated: (TransferParty) -> Unit) -> Unit,
    onAddVendor: (name: String, phone: String, onCreated: (TransferParty) -> Unit) -> Unit,
    onPick: (TransferParty) -> Unit,
    onDismiss: () -> Unit,
) {
    var query by remember { mutableStateOf("") }
    // null = closed; "CUSTOMER"/"VENDOR" = which kind to inline-create.
    var addKind by remember { mutableStateOf<String?>(null) }

    val filtered = remember(query, parties) { parties.filter { it.matches(query) } }

    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Box(Modifier.fillMaxWidth(0.36f).clip(RoundedCornerShape(16.dp)).background(DreamlandForestElevated).padding(20.dp)) {
            Column(Modifier.fillMaxWidth()) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Text(title.uppercase(), style = MaterialTheme.typography.labelLarge, color = DreamlandGold, letterSpacing = 2.sp)
                    TextButton(onClick = onDismiss) { Text("Close", color = DreamlandMuted) }
                }
                Spacer(Modifier.height(12.dp))
                TField(query, { query = it }, "Search name or phone…")
                Spacer(Modifier.height(10.dp))

                if (filtered.isEmpty()) {
                    Box(Modifier.fillMaxWidth().padding(vertical = 24.dp), contentAlignment = Alignment.Center) {
                        Text("No matches", color = DreamlandMuted, style = MaterialTheme.typography.bodySmall)
                    }
                } else {
                    LazyColumn(Modifier.fillMaxWidth().heightIn(max = 320.dp)) {
                        items(filtered, key = { it.kind + "|" + it.refId }) { p ->
                            Row(
                                Modifier.fillMaxWidth().clickable { onPick(p) }.padding(vertical = 11.dp, horizontal = 4.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                KindTag(p.groupLabel)
                                Column(Modifier.weight(1f).padding(start = 10.dp)) {
                                    Text(p.name, color = DreamlandOnDark, style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    if (p.phone.isNotBlank()) Text(p.phone, color = DreamlandMuted, style = MaterialTheme.typography.labelSmall)
                                }
                            }
                            HorizontalDivider(color = DreamlandMuted.copy(alpha = 0.08f))
                        }
                    }
                }

                Spacer(Modifier.height(12.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlineAction("+ Add customer", Modifier.weight(1f)) { addKind = "CUSTOMER" }
                    OutlineAction("+ Add vendor", Modifier.weight(1f)) { addKind = "VENDOR" }
                }
            }
        }
    }

    addKind?.let { kind ->
        AddPartyDialog(
            kind = kind,
            onSubmit = { name, phone ->
                val cb: (TransferParty) -> Unit = { created -> addKind = null; onPick(created) }
                if (kind == "CUSTOMER") onAddCustomer(name, phone, cb) else onAddVendor(name, phone, cb)
            },
            onDismiss = { addKind = null },
        )
    }
}

@Composable
private fun AddPartyDialog(kind: String, onSubmit: (name: String, phone: String) -> Unit, onDismiss: () -> Unit) {
    var name by remember { mutableStateOf("") }
    var phone by remember { mutableStateOf("") }
    val label = if (kind == "CUSTOMER") "CUSTOMER" else "VENDOR"
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Box(Modifier.fillMaxWidth(0.3f).clip(RoundedCornerShape(16.dp)).background(DreamlandForestSurface).padding(22.dp)) {
            Column(Modifier.fillMaxWidth()) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Text("ADD $label", style = MaterialTheme.typography.labelLarge, color = DreamlandGold, letterSpacing = 2.sp)
                    TextButton(onClick = onDismiss) { Text("Cancel", color = DreamlandMuted) }
                }
                Spacer(Modifier.height(16.dp))
                TField(name, { name = it }, "Name")
                Spacer(Modifier.height(10.dp))
                TField(phone, { phone = it.filter { c -> c.isDigit() || c == '+' || c == ' ' } }, "Phone (optional)")
                Spacer(Modifier.height(18.dp))
                Button(
                    onClick = { onSubmit(name.trim(), phone.trim()) },
                    enabled = name.trim().isNotEmpty(),
                    modifier = Modifier.fillMaxWidth().height(44.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = DreamlandGold, disabledContainerColor = DreamlandGold.copy(alpha = 0.3f)),
                    shape = RoundedCornerShape(8.dp),
                ) { Text("Add ${if (kind == "CUSTOMER") "customer" else "vendor"}", color = INK, fontWeight = FontWeight.SemiBold) }
            }
        }
    }
}

@Composable
private fun OutlineAction(text: String, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Box(
        modifier.clip(RoundedCornerShape(8.dp)).border(1.dp, DreamlandGold.copy(alpha = 0.5f), RoundedCornerShape(8.dp))
            .clickable(onClick = onClick).padding(vertical = 11.dp),
        contentAlignment = Alignment.Center,
    ) { Text(text, color = DreamlandGold, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold) }
}

@Composable
private fun KindTag(label: String) {
    val color = when (label) {
        "Accounts" -> DreamlandGold
        "Customers" -> Color(0xFF5C9DFF)
        else -> Color(0xFFE0A458)
    }
    Box(Modifier.clip(RoundedCornerShape(6.dp)).background(color.copy(alpha = 0.16f)).padding(horizontal = 7.dp, vertical = 3.dp)) {
        Text(label, color = color, fontSize = 9.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.5.sp)
    }
}

@Composable
private fun TField(value: String, onChange: (String) -> Unit, label: String) {
    OutlinedTextField(
        value = value, onValueChange = onChange,
        placeholder = { Text(label, color = DreamlandMuted, fontSize = 12.sp) },
        singleLine = true, modifier = Modifier.fillMaxWidth(),
        colors = OutlinedTextFieldDefaults.colors(
            focusedTextColor = DreamlandOnDark, unfocusedTextColor = DreamlandOnDark,
            focusedBorderColor = DreamlandGold, unfocusedBorderColor = DreamlandMuted.copy(alpha = 0.4f), cursorColor = DreamlandGold,
        ),
    )
}

private fun String.filterMoney(): String {
    val cleaned = filter { it.isDigit() || it == '.' }
    val firstDot = cleaned.indexOf('.')
    return if (firstDot < 0) cleaned else cleaned.substring(0, firstDot + 1) + cleaned.substring(firstDot + 1).replace(".", "")
}

private fun round2(d: Double): Double = Math.round(d * 100.0) / 100.0
private fun fmt(d: Double): String {
    val r = round2(d)
    return if (r == r.toLong().toDouble()) "%,d".format(r.toLong()) else "%,.2f".format(r)
}
