package com.example.dreamland_reception.stays

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
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
import androidx.compose.runtime.LaunchedEffect
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
import com.example.dreamland_reception.ui.viewmodel.CheckOutState
import com.example.dreamland_reception.ui.viewmodel.StaysViewModel

@Composable
fun CheckOutDialog(
    state: CheckOutState,
    vm: StaysViewModel,
    onNavigateToBilling: () -> Unit = {},
) {
    // Navigate to Billing after successful checkout
    LaunchedEffect(state.navigateToBilling) {
        if (state.navigateToBilling) {
            onNavigateToBilling()
            vm.onNavigateToBillingHandled()
        }
    }

    if (!state.isOpen) return

    Dialog(
        onDismissRequest = { if (!state.isProcessing) vm.closeCheckOut() },
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth(0.5f)
                .clip(RoundedCornerShape(16.dp))
                .background(DreamlandForestSurface)
                .padding(24.dp),
        ) {
            Column {
                Text("CHECK-OUT", style = MaterialTheme.typography.labelLarge, color = DreamlandGold, letterSpacing = 2.sp)
                state.stay?.let { stay ->
                    Text(stay.guestName, style = MaterialTheme.typography.headlineMedium, color = DreamlandOnDark, fontWeight = FontWeight.Bold)
                    Text("Room ${stay.roomNumber}", color = DreamlandMuted, style = MaterialTheme.typography.bodyMedium)
                }

                // ── Late checkout warning ──────────────────────────────────
                if (state.isLateCheckout) {
                    Spacer(Modifier.height(12.dp))
                    Card(
                        colors = CardDefaults.cardColors(containerColor = Color(0xFFEF5350).copy(alpha = 0.12f)),
                        shape = RoundedCornerShape(10.dp),
                    ) {
                        Column(Modifier.fillMaxWidth().padding(12.dp)) {
                            Text(
                                "Late Check-out",
                                color = Color(0xFFEF5350),
                                fontWeight = FontWeight.SemiBold,
                                style = MaterialTheme.typography.bodyMedium,
                            )
                            Text(
                                if (state.lateCheckoutCharge > 0)
                                    "Guest is checking out past the scheduled time. Late checkout charge of ₹${state.lateCheckoutCharge.toLong()} will be added."
                                else
                                    "Guest is checking out past the scheduled checkout time.",
                                color = Color(0xFFEF5350).copy(alpha = 0.85f),
                                style = MaterialTheme.typography.bodySmall,
                            )
                        }
                    }
                }

                Spacer(Modifier.height(20.dp))

                // ── Bill Summary ───────────────────────────────────────────
                Card(
                    colors = CardDefaults.cardColors(containerColor = DreamlandForestElevated),
                    shape = RoundedCornerShape(12.dp),
                ) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("Bill Summary", style = MaterialTheme.typography.titleMedium, color = DreamlandGold, fontWeight = FontWeight.SemiBold)
                        HorizontalDivider(color = DreamlandGold.copy(alpha = 0.2f))
                        val bill = state.bill
                        if (bill != null) {
                            CheckOutBillRow("Room Charges", bill.roomCharges)
                            if (bill.serviceCharges > 0) CheckOutBillRow("Service Charges", bill.serviceCharges)
                            if (bill.earlyCheckInCharge > 0) CheckOutBillRow("Early Check-in", bill.earlyCheckInCharge)
                            // Show current late checkout charge (may not be on bill yet)
                            val lateCharge = if (state.lateCheckoutCharge > 0) state.lateCheckoutCharge else bill.lateCheckOutCharge
                            if (lateCharge > 0) CheckOutBillRow("Late Check-out", lateCharge)
                            if (bill.discount > 0) CheckOutBillRow("Discount", -bill.discount)
                            HorizontalDivider(color = DreamlandMuted.copy(alpha = 0.3f))
                            val displayTotal = bill.totalAmount + (if (state.lateCheckoutCharge > 0 && bill.lateCheckOutCharge == 0.0) state.lateCheckoutCharge else 0.0)
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text("Total", color = DreamlandOnDark, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                                Text("₹${displayTotal.toLong()}", color = DreamlandGold, fontWeight = FontWeight.Bold, fontSize = 20.sp)
                            }
                            if (bill.amountPaid > 0) CheckOutBillRow("Already Paid", bill.amountPaid)
                            val pending = displayTotal - bill.amountPaid
                            if (pending > 0) {
                                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                    Text("Amount Due", color = Color(0xFFEF5350), fontWeight = FontWeight.Bold, fontSize = 15.sp)
                                    Text("₹${pending.toLong()}", color = Color(0xFFEF5350), fontWeight = FontWeight.Bold, fontSize = 16.sp)
                                }
                            }
                        } else {
                            Text("No billing record found", color = DreamlandMuted, style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }

                Spacer(Modifier.height(16.dp))

                if (state.error != null) {
                    Text(state.error, color = Color(0xFFEF5350), style = MaterialTheme.typography.bodySmall)
                    Spacer(Modifier.height(8.dp))
                }

                Spacer(Modifier.height(8.dp))

                // ── CTAs ───────────────────────────────────────────────────
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    TextButton(
                        onClick = { vm.closeCheckOut() },
                        enabled = !state.isProcessing,
                        modifier = Modifier.weight(1f),
                    ) {
                        Text("Cancel", color = DreamlandMuted)
                    }
                    Button(
                        onClick = { vm.confirmCheckOut() },
                        enabled = !state.isProcessing,
                        modifier = Modifier.weight(2f).height(52.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFEF5350)),
                        shape = RoundedCornerShape(10.dp),
                    ) {
                        if (state.isProcessing) {
                            CircularProgressIndicator(color = Color.White, modifier = Modifier.width(20.dp).height(20.dp), strokeWidth = 2.dp)
                        } else {
                            Text("Confirm Check-out", color = Color.White, fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun CheckOutBillRow(label: String, amount: Double) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, color = DreamlandMuted, style = MaterialTheme.typography.bodySmall)
        Text(
            text = if (amount < 0) "-₹${(-amount).toLong()}" else "₹${amount.toLong()}",
            color = DreamlandOnDark,
            style = MaterialTheme.typography.bodySmall,
        )
    }
}
