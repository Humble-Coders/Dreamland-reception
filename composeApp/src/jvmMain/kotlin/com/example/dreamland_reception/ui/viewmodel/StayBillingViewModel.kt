package com.example.dreamland_reception.ui.viewmodel

import androidx.lifecycle.ViewModel
import com.example.dreamland_reception.data.AppContext
import com.example.dreamland_reception.data.model.Bill
import com.example.dreamland_reception.data.model.BillItem
import com.example.dreamland_reception.data.model.PaymentTransaction
import com.example.dreamland_reception.data.model.Stay
import com.example.dreamland_reception.data.repository.BillingRepository
import com.example.dreamland_reception.data.repository.BillRepository
import com.example.dreamland_reception.data.repository.FirestoreBillingRepository
import com.example.dreamland_reception.data.repository.FirestoreBillRepository
import com.example.dreamland_reception.data.repository.FirestoreOrderRepository
import com.example.dreamland_reception.data.repository.FirestoreStayRepository
import com.example.dreamland_reception.data.repository.OrderRepository
import com.example.dreamland_reception.data.repository.StayRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import java.util.UUID

// ── Add-item dialog ───────────────────────────────────────────────────────────

data class AddBillItemDialog(
    val show: Boolean = false,
    val step: Int = 0,             // 0 = choose type, 1 = enter details
    val type: String = "CUSTOM",   // ROOM | ORDER | SERVICE | CUSTOM
    val name: String = "",
    val quantity: String = "1",
    val unitPrice: String = "",
    val notes: String = "",
    val isSaving: Boolean = false,
)

// ── Add-payment dialog ────────────────────────────────────────────────────────

data class AddPaymentDialog(
    val show: Boolean = false,
    val amount: String = "",
    val method: String = "CASH",   // CASH | UPI | CARD
    val isSaving: Boolean = false,
)

// ── Tax/discount dialog ───────────────────────────────────────────────────────

data class TaxDiscountDialog(
    val show: Boolean = false,
    val taxEnabled: Boolean = false,
    val taxPercentage: String = "18",
    val discountType: String = "FLAT",
    val discountValue: String = "0",
    val isSaving: Boolean = false,
)

// ── Confirm payment dialog ────────────────────────────────────────────────────

data class ConfirmPaymentDialog(
    val show: Boolean = false,
    val isProcessing: Boolean = false,
    val error: String? = null,
    val done: Boolean = false,
)

// ── Main state ────────────────────────────────────────────────────────────────

data class StayBillingState(
    val isLoading: Boolean = true,
    val error: String? = null,
    val stay: Stay? = null,
    val bill: Bill? = null,

    // dialogs
    val addItemDialog: AddBillItemDialog = AddBillItemDialog(),
    val addPaymentDialog: AddPaymentDialog = AddPaymentDialog(),
    val taxDiscountDialog: TaxDiscountDialog = TaxDiscountDialog(),
    val confirmPaymentDialog: ConfirmPaymentDialog = ConfirmPaymentDialog(),
)

// ── ViewModel ─────────────────────────────────────────────────────────────────

class StayBillingViewModel(
    private val billRepo: BillRepository = FirestoreBillRepository,
    private val stayRepo: StayRepository = FirestoreStayRepository,
    private val invoiceRepo: BillingRepository = FirestoreBillingRepository,
    private val orderRepo: OrderRepository = FirestoreOrderRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(StayBillingState())
    val state: StateFlow<StayBillingState> = _state.asStateFlow()

    fun load(stayId: String) {
        if (stayId.isBlank()) return
        launchWithGlobalLoading {
            _state.update { it.copy(isLoading = true, error = null) }
            val stay = runCatching { stayRepo.getById(stayId) }.getOrNull()
            var bill = runCatching { billRepo.getByStay(stayId) }.getOrNull()

            // Create a fresh bill if none exists yet for this stay
            if (bill == null && stay != null) {
                val invoice = runCatching { invoiceRepo.getByStay(stayId) }.getOrNull()
                val orders = runCatching { orderRepo.getByStay(stayId) }.getOrElse { emptyList() }
                val items = buildList {
                    if (invoice != null) {
                        if (invoice.roomCharges > 0) add(BillItem(
                            name = "Room Charges",
                            type = "ROOM",
                            quantity = 1,
                            unitPrice = invoice.roomCharges,
                            total = invoice.roomCharges,
                        ))
                        if (invoice.serviceCharges > 0) add(BillItem(
                            name = "Service Charges",
                            type = "SERVICE",
                            quantity = 1,
                            unitPrice = invoice.serviceCharges,
                            total = invoice.serviceCharges,
                        ))
                        if (invoice.earlyCheckInCharge > 0) add(BillItem(
                            name = "Early Check-in",
                            type = "SERVICE",
                            quantity = 1,
                            unitPrice = invoice.earlyCheckInCharge,
                            total = invoice.earlyCheckInCharge,
                        ))
                        if (invoice.lateCheckOutCharge > 0) add(BillItem(
                            name = "Late Check-out",
                            type = "SERVICE",
                            quantity = 1,
                            unitPrice = invoice.lateCheckOutCharge,
                            total = invoice.lateCheckOutCharge,
                        ))
                    }
                    // Add each order from the stay as a separate ORDER bill item
                    for (order in orders) {
                        if (order.totalAmount > 0) add(BillItem(
                            name = order.items.joinToString(", ") { it.name }.ifBlank { "Order" },
                            type = "ORDER",
                            quantity = 1,
                            unitPrice = order.totalAmount,
                            total = order.totalAmount,
                            refId = order.id,
                            notes = order.notes,
                        ))
                    }
                }
                val advance = invoice?.amountPaid ?: 0.0
                val subtotal = items.sumOf { it.total }
                val pending = (subtotal - advance).coerceAtLeast(0.0)
                val status = when {
                    pending <= 0 && subtotal > 0 -> "PAID"
                    advance > 0 -> "PARTIAL"
                    else -> "PENDING"
                }
                val newBill = Bill(
                    hotelId = AppContext.hotelId,
                    stayId = stayId,
                    guestName = stay.guestName,
                    roomNumber = stay.roomNumber,
                    checkInDate = stay.checkInActual,
                    checkOutDate = stay.checkOutActual ?: stay.expectedCheckOut,
                    items = items,
                    subtotal = subtotal,
                    totalAmount = subtotal,
                    advancePayment = advance,
                    pendingAmount = pending,
                    status = status,
                )
                val newId = runCatching { billRepo.createForStay(newBill) }.getOrNull() ?: ""
                bill = newBill.copy(id = newId)
            }

            _state.update { it.copy(isLoading = false, stay = stay, bill = bill) }
        }
    }

    // ── Add item ──────────────────────────────────────────────────────────────

    fun openAddItem() = _state.update { it.copy(addItemDialog = AddBillItemDialog(show = true)) }
    fun closeAddItem() = _state.update { it.copy(addItemDialog = AddBillItemDialog()) }
    fun onAddItemType(type: String) = _state.update { it.copy(addItemDialog = it.addItemDialog.copy(type = type, step = 1, name = defaultNameForType(type))) }
    fun onAddItemName(v: String) = _state.update { it.copy(addItemDialog = it.addItemDialog.copy(name = v)) }
    fun onAddItemQty(v: String) = _state.update { it.copy(addItemDialog = it.addItemDialog.copy(quantity = v.filter(Char::isDigit))) }
    fun onAddItemPrice(v: String) = _state.update { it.copy(addItemDialog = it.addItemDialog.copy(unitPrice = v.filter { c -> c.isDigit() || c == '.' })) }
    fun onAddItemNotes(v: String) = _state.update { it.copy(addItemDialog = it.addItemDialog.copy(notes = v)) }
    fun onAddItemBackToTypeSelect() = _state.update { it.copy(addItemDialog = it.addItemDialog.copy(step = 0)) }

    fun submitAddItem() {
        val d = _state.value.addItemDialog
        val bill = _state.value.bill ?: return
        if (d.name.isBlank()) return
        val qty = d.quantity.toIntOrNull()?.coerceAtLeast(1) ?: 1
        val price = d.unitPrice.toDoubleOrNull() ?: 0.0
        _state.update { it.copy(addItemDialog = d.copy(isSaving = true)) }
        launchWithGlobalLoading {
            val newItem = BillItem(
                id = UUID.randomUUID().toString(),
                name = d.name.trim(),
                type = d.type,
                quantity = qty,
                unitPrice = price,
                total = price * qty,
                notes = d.notes.trim(),
            )
            val updatedItems = bill.items + newItem
            val totals = recalculate(bill.copy(items = updatedItems))
            runCatching {
                billRepo.updateItems(bill.id, updatedItems, totals.subtotal, totals.taxAmount, totals.discountAmount, totals.totalAmount, totals.pendingAmount, totals.status)
            }.onSuccess {
                _state.update { s -> s.copy(bill = s.bill?.let { totals }, addItemDialog = AddBillItemDialog()) }
            }.onFailure {
                _state.update { it.copy(addItemDialog = d.copy(isSaving = false)) }
            }
        }
    }

    fun removeItem(itemId: String) {
        val bill = _state.value.bill ?: return
        launchWithGlobalLoading {
            val updatedItems = bill.items.filter { it.id != itemId }
            val totals = recalculate(bill.copy(items = updatedItems))
            runCatching {
                billRepo.updateItems(bill.id, updatedItems, totals.subtotal, totals.taxAmount, totals.discountAmount, totals.totalAmount, totals.pendingAmount, totals.status)
            }.onSuccess {
                _state.update { s -> s.copy(bill = s.bill?.let { totals }) }
            }
        }
    }

    // ── Add payment ───────────────────────────────────────────────────────────

    fun openAddPayment() = _state.update { it.copy(addPaymentDialog = AddPaymentDialog(show = true)) }
    fun closeAddPayment() = _state.update { it.copy(addPaymentDialog = AddPaymentDialog()) }
    fun onPaymentAmount(v: String) = _state.update { it.copy(addPaymentDialog = it.addPaymentDialog.copy(amount = v.filter { c -> c.isDigit() || c == '.' })) }
    fun onPaymentMethod(v: String) = _state.update { it.copy(addPaymentDialog = it.addPaymentDialog.copy(method = v)) }

    fun submitPayment() {
        val d = _state.value.addPaymentDialog
        val bill = _state.value.bill ?: return
        val amount = d.amount.toDoubleOrNull() ?: return
        if (amount <= 0) return
        _state.update { it.copy(addPaymentDialog = d.copy(isSaving = true)) }
        launchWithGlobalLoading {
            val tx = PaymentTransaction(
                id = UUID.randomUUID().toString(),
                amount = amount,
                method = d.method,
            )
            val newTotalPaid = bill.totalPaid + amount
            val newPending = (bill.totalAmount - newTotalPaid).coerceAtLeast(0.0)
            val newStatus = when {
                newPending <= 0 -> "PAID"
                newTotalPaid > 0 -> "PARTIAL"
                else -> "PENDING"
            }
            runCatching {
                billRepo.addTransaction(bill.id, tx, newTotalPaid, newPending, newStatus)
            }.onSuccess {
                _state.update { s ->
                    s.copy(
                        bill = s.bill?.copy(
                            transactions = s.bill.transactions + tx,
                            totalPaid = newTotalPaid,
                            pendingAmount = newPending,
                            status = newStatus,
                        ),
                        addPaymentDialog = AddPaymentDialog(),
                    )
                }
            }.onFailure {
                _state.update { it.copy(addPaymentDialog = d.copy(isSaving = false)) }
            }
        }
    }

    // ── Tax / Discount ────────────────────────────────────────────────────────

    fun openTaxDiscount() {
        val bill = _state.value.bill ?: return
        _state.update {
            it.copy(taxDiscountDialog = TaxDiscountDialog(
                show = true,
                taxEnabled = bill.taxEnabled,
                taxPercentage = bill.taxPercentage.toString(),
                discountType = bill.discountType,
                discountValue = bill.discountValue.toString(),
            ))
        }
    }

    fun closeTaxDiscount() = _state.update { it.copy(taxDiscountDialog = TaxDiscountDialog()) }
    fun onTaxEnabled(v: Boolean) = _state.update { it.copy(taxDiscountDialog = it.taxDiscountDialog.copy(taxEnabled = v)) }
    fun onTaxPercentage(v: String) = _state.update { it.copy(taxDiscountDialog = it.taxDiscountDialog.copy(taxPercentage = v.filter { c -> c.isDigit() || c == '.' })) }
    fun onDiscountType(v: String) = _state.update { it.copy(taxDiscountDialog = it.taxDiscountDialog.copy(discountType = v)) }
    fun onDiscountValue(v: String) = _state.update { it.copy(taxDiscountDialog = it.taxDiscountDialog.copy(discountValue = v.filter { c -> c.isDigit() || c == '.' })) }

    fun submitTaxDiscount() {
        val d = _state.value.taxDiscountDialog
        val bill = _state.value.bill ?: return
        _state.update { it.copy(taxDiscountDialog = d.copy(isSaving = true)) }
        launchWithGlobalLoading {
            val updatedBill = bill.copy(
                taxEnabled = d.taxEnabled,
                taxPercentage = d.taxPercentage.toDoubleOrNull() ?: 18.0,
                discountType = d.discountType,
                discountValue = d.discountValue.toDoubleOrNull() ?: 0.0,
            )
            val totals = recalculate(updatedBill)
            runCatching {
                billRepo.updateTaxDiscount(
                    bill.id,
                    d.taxEnabled, d.taxPercentage.toDoubleOrNull() ?: 18.0,
                    d.discountType, d.discountValue.toDoubleOrNull() ?: 0.0,
                    totals.subtotal, totals.taxAmount, totals.discountAmount,
                    totals.totalAmount, totals.pendingAmount, totals.status,
                )
            }.onSuccess {
                _state.update { s -> s.copy(bill = s.bill?.let { totals }, taxDiscountDialog = TaxDiscountDialog()) }
            }.onFailure {
                _state.update { it.copy(taxDiscountDialog = d.copy(isSaving = false)) }
            }
        }
    }

    // ── Confirm payment ───────────────────────────────────────────────────────

    fun openConfirmPayment() = _state.update { it.copy(confirmPaymentDialog = ConfirmPaymentDialog(show = true)) }
    fun closeConfirmPayment() = _state.update { it.copy(confirmPaymentDialog = ConfirmPaymentDialog()) }

    fun confirmPayment() {
        val bill = _state.value.bill ?: return
        _state.update { it.copy(confirmPaymentDialog = it.confirmPaymentDialog.copy(isProcessing = true, error = null)) }
        launchWithGlobalLoading {
            val amountPaid = bill.totalPaid + bill.advancePayment
            runCatching {
                val existing = invoiceRepo.getByStay(bill.stayId)
                if (existing != null) {
                    invoiceRepo.finalizeFromBill(existing.id, bill.totalAmount, amountPaid, bill.status)
                } else {
                    invoiceRepo.add(com.example.dreamland_reception.data.model.BillingInvoice(
                        stayId = bill.stayId,
                        guestName = bill.guestName,
                        roomNumber = bill.roomNumber,
                        totalAmount = bill.totalAmount,
                        amountPaid = amountPaid,
                        status = bill.status,
                    ))
                }
            }.onSuccess {
                _state.update { it.copy(confirmPaymentDialog = it.confirmPaymentDialog.copy(isProcessing = false, done = true)) }
                com.example.dreamland_reception.DreamlandAppInitializer.getBillingViewModel().load()
            }.onFailure { e ->
                _state.update { it.copy(confirmPaymentDialog = it.confirmPaymentDialog.copy(isProcessing = false, error = e.message ?: "Failed")) }
            }
        }
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private fun recalculate(bill: Bill): Bill {
        val subtotal = bill.items.sumOf { it.total }
        val taxAmount = if (bill.taxEnabled) subtotal * bill.taxPercentage / 100.0 else 0.0
        val discountAmount = when (bill.discountType) {
            "PERCENT" -> subtotal * bill.discountValue / 100.0
            else -> bill.discountValue
        }
        val total = (subtotal + taxAmount - discountAmount).coerceAtLeast(0.0)
        val pending = (total - bill.totalPaid - bill.advancePayment).coerceAtLeast(0.0)
        val status = when {
            pending <= 0 -> "PAID"
            bill.totalPaid + bill.advancePayment > 0 -> "PARTIAL"
            else -> "PENDING"
        }
        return bill.copy(
            subtotal = subtotal,
            taxAmount = taxAmount,
            discountAmount = discountAmount,
            totalAmount = total,
            pendingAmount = pending,
            status = status,
        )
    }

    private fun defaultNameForType(type: String) = when (type) {
        "ROOM" -> "Room Charges"
        "ORDER" -> "Food & Beverage"
        "SERVICE" -> "Hotel Service"
        else -> ""
    }
}
