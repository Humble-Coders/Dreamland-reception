package com.example.dreamland_reception.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.dreamland_reception.data.AppContext
import com.example.dreamland_reception.data.accounting.AccountingRepository
import com.example.dreamland_reception.data.model.Bill
import com.example.dreamland_reception.data.model.BillItem
import com.example.dreamland_reception.data.model.PaymentTransaction
import com.example.dreamland_reception.data.model.Stay
import com.example.dreamland_reception.data.repository.BillRepository
import com.example.dreamland_reception.data.repository.FirestoreBillRepository
import com.example.dreamland_reception.data.repository.FirestoreOrderRepository
import com.example.dreamland_reception.data.repository.FirestoreRoomRepository
import com.example.dreamland_reception.data.repository.FirestoreStayRepository
import com.example.dreamland_reception.data.repository.OrderRepository
import com.example.dreamland_reception.data.repository.RoomRepository
import com.example.dreamland_reception.data.repository.StayRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.temporal.ChronoUnit
import java.util.Date
import java.util.UUID

// ── Accounting status ─────────────────────────────────────────────────────────

sealed class AccountingStatus {
    object Idle : AccountingStatus()
    object InProgress : AccountingStatus()
    object Synced : AccountingStatus()
    data class Failed(val message: String) : AccountingStatus()
}

// ── Add-item dialog ───────────────────────────────────────────────────────────

data class AddBillItemDialog(
    val show: Boolean = false,
    val step: Int = 0,             // 0 = choose type, 1 = enter details
    val type: String = "CUSTOM",   // ROOM | ORDER | SERVICE | CUSTOM
    val roomNumber: String = "",   // only used when type == ROOM
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

// ── Edit-payment dialog ───────────────────────────────────────────────────────

data class EditPaymentDialog(
    val show: Boolean = false,
    val txId: String = "",
    val amount: String = "",
    val method: String = "CASH",
    val isSaving: Boolean = false,
    val error: String? = null,
)

// ── Edit-bill-item dialog ─────────────────────────────────────────────────────

data class EditBillItemDialog(
    val show: Boolean = false,
    val itemId: String = "",
    val type: String = "CUSTOM",
    val name: String = "",
    val quantity: String = "1",
    val unitPrice: String = "",
    val notes: String = "",
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
    val editBillItemDialog: EditBillItemDialog = EditBillItemDialog(),
    val addPaymentDialog: AddPaymentDialog = AddPaymentDialog(),
    val editPaymentDialog: EditPaymentDialog = EditPaymentDialog(),
    val taxDiscountDialog: TaxDiscountDialog = TaxDiscountDialog(),
    val confirmPaymentDialog: ConfirmPaymentDialog = ConfirmPaymentDialog(),
    val accountingStatus: AccountingStatus = AccountingStatus.Idle,
)

// ── ViewModel ─────────────────────────────────────────────────────────────────

class StayBillingViewModel(
    private val billRepo: BillRepository = FirestoreBillRepository,
    private val stayRepo: StayRepository = FirestoreStayRepository,
    private val orderRepo: OrderRepository = FirestoreOrderRepository,
    private val roomRepo: RoomRepository = FirestoreRoomRepository,
    private val accountingRepo: AccountingRepository = AccountingRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(StayBillingState())
    val state: StateFlow<StayBillingState> = _state.asStateFlow()

    fun loadByBillId(billId: String) {
        if (billId.isBlank()) return
        launchWithGlobalLoading {
            _state.update { it.copy(isLoading = true, error = null) }
            val bill = runCatching { billRepo.getById(billId) }.getOrNull()
            _state.update { it.copy(isLoading = false, stay = null, bill = bill, error = if (bill == null) "Bill not found" else null) }
        }
    }

    fun load(stayId: String) {
        if (stayId.isBlank()) return
        launchWithGlobalLoading {
            _state.update { it.copy(isLoading = true, error = null) }
            val stay = runCatching { stayRepo.getById(stayId) }.getOrNull()
            var bill = runCatching { billRepo.getByStay(stayId) }.getOrNull()

            // For ACTIVE stays: build an in-memory preview only — no Firestore write.
            // The real bill document is created in "bills" at checkout by StaysViewModel.
            // For COMPLETED stays: if no bill exists yet (edge case), compute and persist it.
            if (bill == null && stay != null) {
                val room = runCatching { roomRepo.getById(stay.roomCategoryId) }.getOrNull()
                val roomPricePerNight = room?.pricePerNight ?: 0.0
                val taxPercentage = room?.taxPercentage ?: 0.0
                val checkOutDate = stay.checkOutActual ?: stay.expectedCheckOut
                val nights = ChronoUnit.DAYS.between(
                    stay.checkInActual.toInstant(), checkOutDate.toInstant()
                ).coerceAtLeast(1)
                val roomCharges = roomPricePerNight * nights
                val breakfastCharge = if (stay.breakfast) (room?.breakfastPrice ?: 0.0) * stay.adults * nights else 0.0
                val orders = runCatching { orderRepo.getByStay(stayId) }.getOrElse { emptyList() }

                val items = buildList {
                    if (roomCharges > 0) add(BillItem(
                        name = "Room Charges ($nights night${if (nights > 1) "s" else ""} × ₹${roomPricePerNight.toLong()})",
                        type = "ROOM", quantity = nights.toInt(), unitPrice = roomPricePerNight, total = roomCharges,
                    ))
                    if (breakfastCharge > 0) add(BillItem(name = "Breakfast", type = "SERVICE", quantity = 1, unitPrice = breakfastCharge, total = breakfastCharge))
                    if (stay.earlyCheckInCharge > 0) add(BillItem(name = "Early Check-in", type = "SERVICE", quantity = 1, unitPrice = stay.earlyCheckInCharge, total = stay.earlyCheckInCharge))
                    if (stay.lateCheckOutCharge > 0) add(BillItem(name = "Late Check-out", type = "SERVICE", quantity = 1, unitPrice = stay.lateCheckOutCharge, total = stay.lateCheckOutCharge))
                    for (order in orders) {
                        if (order.totalAmount > 0) add(BillItem(
                            name = order.items.joinToString(", ") { it.name }.ifBlank { "Order" },
                            type = "ORDER", quantity = 1, unitPrice = order.totalAmount, total = order.totalAmount, refId = order.id, notes = order.notes,
                        ))
                    }
                }
                val advance = stay.advanceAmount
                val subtotal = items.sumOf { it.total }
                val taxEnabled = taxPercentage > 0
                val taxAmount = if (taxEnabled) Math.round(subtotal * taxPercentage / 100.0).toDouble() else 0.0
                val total = subtotal + taxAmount
                val pending = (total - advance).coerceAtLeast(0.0)
                val status = when {
                    pending <= 0 && total > 0 -> "PAID"
                    advance > 0 -> "PARTIAL"
                    else -> "PENDING"
                }
                val computed = Bill(
                    hotelId = AppContext.hotelId, stayId = stayId,
                    guestName = stay.guestName, roomNumber = stay.roomNumber,
                    checkInDate = stay.checkInActual, checkOutDate = checkOutDate,
                    items = items, taxEnabled = taxEnabled, taxPercentage = taxPercentage,
                    subtotal = subtotal, taxAmount = taxAmount, totalAmount = total,
                    advancePayment = advance, pendingAmount = pending, status = status,
                )
                bill = if (stay.status == "ACTIVE") {
                    // In-memory preview — id is blank, so edit/payment actions are intentionally disabled
                    computed
                } else {
                    // Completed stay with no bill (edge case fallback) — persist it
                    val newId = runCatching { billRepo.createForStay(computed) }.getOrNull() ?: ""
                    computed.copy(id = newId)
                }
            }

            _state.update { it.copy(isLoading = false, stay = stay, bill = bill) }
        }
    }

    // ── Add item ──────────────────────────────────────────────────────────────

    fun updateDates(checkIn: java.util.Date, checkOut: java.util.Date) {
        val bill = _state.value.bill ?: return
        _state.update { it.copy(bill = bill.copy(checkInDate = checkIn, checkOutDate = checkOut)) }
        if (bill.id.isBlank()) return
        launchWithGlobalLoading {
            runCatching { billRepo.updateDates(bill.id, checkIn, checkOut) }
        }
    }

    fun openAddItem() = _state.update { it.copy(addItemDialog = AddBillItemDialog(show = true)) }
    fun closeAddItem() = _state.update { it.copy(addItemDialog = AddBillItemDialog()) }
    fun onAddItemType(type: String) = _state.update { it.copy(addItemDialog = it.addItemDialog.copy(type = type, step = 1, name = defaultNameForType(type))) }
    fun onAddItemRoomNumber(v: String) = _state.update { it.copy(addItemDialog = it.addItemDialog.copy(roomNumber = v)) }
    fun onAddItemName(v: String) = _state.update { it.copy(addItemDialog = it.addItemDialog.copy(name = v)) }
    fun onAddItemQty(v: String) = _state.update { it.copy(addItemDialog = it.addItemDialog.copy(quantity = v.filter(Char::isDigit))) }
    fun onAddItemPrice(v: String) = _state.update { it.copy(addItemDialog = it.addItemDialog.copy(unitPrice = v.filter { c -> c.isDigit() || c == '.' })) }
    fun onAddItemNotes(v: String) = _state.update { it.copy(addItemDialog = it.addItemDialog.copy(notes = v)) }
    fun onAddItemBackToTypeSelect() = _state.update { it.copy(addItemDialog = it.addItemDialog.copy(step = 0)) }

    fun submitAddItem() {
        val d = _state.value.addItemDialog
        val bill = _state.value.bill ?: return
        if (bill.id.isBlank() || d.name.isBlank()) return
        val qty = d.quantity.toIntOrNull()?.coerceAtLeast(1) ?: 1
        val price = d.unitPrice.toDoubleOrNull() ?: 0.0
        _state.update { it.copy(addItemDialog = d.copy(isSaving = true)) }
        launchWithGlobalLoading {
            val itemName = if (d.type == "ROOM" && d.roomNumber.isNotBlank())
                "Room ${d.roomNumber.trim()} — ${d.name.trim()}"
            else d.name.trim()
            val newItem = BillItem(
                id = UUID.randomUUID().toString(),
                name = itemName,
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
        if (bill.id.isBlank()) return
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

    // ── Edit item ─────────────────────────────────────────────────────────────

    fun openEditItem(item: BillItem) = _state.update {
        it.copy(editBillItemDialog = EditBillItemDialog(
            show = true, itemId = item.id, type = item.type,
            name = item.name, quantity = item.quantity.toString(),
            unitPrice = if (item.unitPrice > 0) item.unitPrice.toLong().toString() else "",
            notes = item.notes,
        ))
    }
    fun closeEditItem() = _state.update { it.copy(editBillItemDialog = EditBillItemDialog()) }
    fun onEditItemName(v: String) = _state.update { it.copy(editBillItemDialog = it.editBillItemDialog.copy(name = v)) }
    fun onEditItemQty(v: String) = _state.update { it.copy(editBillItemDialog = it.editBillItemDialog.copy(quantity = v.filter(Char::isDigit))) }
    fun onEditItemPrice(v: String) = _state.update { it.copy(editBillItemDialog = it.editBillItemDialog.copy(unitPrice = v.filter { c -> c.isDigit() || c == '.' })) }
    fun onEditItemNotes(v: String) = _state.update { it.copy(editBillItemDialog = it.editBillItemDialog.copy(notes = v)) }

    fun submitEditItem() {
        val d = _state.value.editBillItemDialog
        val bill = _state.value.bill ?: return
        if (bill.id.isBlank() || d.name.isBlank()) return
        val qty = d.quantity.toIntOrNull()?.coerceAtLeast(1) ?: 1
        val price = d.unitPrice.toDoubleOrNull() ?: 0.0
        _state.update { it.copy(editBillItemDialog = d.copy(isSaving = true)) }
        launchWithGlobalLoading {
            val updatedItems = bill.items.map { item ->
                if (item.id == d.itemId) item.copy(name = d.name.trim(), quantity = qty, unitPrice = price, total = price * qty, notes = d.notes.trim())
                else item
            }
            val totals = recalculate(bill.copy(items = updatedItems))
            runCatching {
                billRepo.updateItems(bill.id, updatedItems, totals.subtotal, totals.taxAmount, totals.discountAmount, totals.totalAmount, totals.pendingAmount, totals.status)
            }.onSuccess {
                _state.update { s -> s.copy(bill = s.bill?.let { totals }, editBillItemDialog = EditBillItemDialog()) }
            }.onFailure {
                _state.update { it.copy(editBillItemDialog = d.copy(isSaving = false)) }
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
        if (bill.id.isBlank()) return
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
            val newPending = (bill.totalAmount - newTotalPaid - bill.advancePayment).coerceAtLeast(0.0)
            val newStatus = when {
                newPending <= 0 -> "PAID"
                newTotalPaid + bill.advancePayment > 0 -> "PARTIAL"
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

    // ── Edit payment ─────────────────────────────────────────────────────────

    fun openEditPayment(tx: PaymentTransaction) = _state.update {
        it.copy(editPaymentDialog = EditPaymentDialog(
            show = true, txId = tx.id,
            amount = if (tx.amount > 0) tx.amount.toLong().toString() else "",
            method = tx.method,
        ))
    }
    fun closeEditPayment() = _state.update { it.copy(editPaymentDialog = EditPaymentDialog()) }
    fun onEditPaymentAmount(v: String) = _state.update { it.copy(editPaymentDialog = it.editPaymentDialog.copy(amount = v.filter { c -> c.isDigit() || c == '.' })) }
    fun onEditPaymentMethod(v: String) = _state.update { it.copy(editPaymentDialog = it.editPaymentDialog.copy(method = v)) }

    fun submitEditPayment() {
        val d = _state.value.editPaymentDialog
        val bill = _state.value.bill ?: return
        if (bill.id.isBlank()) return
        val newAmount = d.amount.toDoubleOrNull() ?: return
        if (newAmount <= 0) return
        _state.update { it.copy(editPaymentDialog = d.copy(isSaving = true, error = null)) }
        launchWithGlobalLoading {
            val updatedTxs = bill.transactions.map { tx ->
                if (tx.id == d.txId) tx.copy(amount = newAmount, method = d.method) else tx
            }
            val newTotalPaid = updatedTxs.sumOf { it.amount }
            val newPending = (bill.totalAmount - newTotalPaid - bill.advancePayment).coerceAtLeast(0.0)
            val newStatus = when {
                newPending <= 0 -> "PAID"
                newTotalPaid + bill.advancePayment > 0 -> "PARTIAL"
                else -> "PENDING"
            }
            runCatching {
                billRepo.updateTransactions(bill.id, updatedTxs, newTotalPaid, newPending, newStatus)
            }.onSuccess {
                _state.update { s ->
                    s.copy(
                        bill = s.bill?.copy(transactions = updatedTxs, totalPaid = newTotalPaid, pendingAmount = newPending, status = newStatus),
                        editPaymentDialog = EditPaymentDialog(),
                    )
                }
            }.onFailure { e ->
                _state.update { it.copy(editPaymentDialog = d.copy(isSaving = false, error = e.message ?: "Failed")) }
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
        if (bill.id.isBlank()) return
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
        // Preview-mode bills (active stay, not yet in Firestore) cannot be finalized here.
        if (bill.id.isBlank()) {
            _state.update { it.copy(confirmPaymentDialog = it.confirmPaymentDialog.copy(error = "Bill is generated at checkout — finalize after check-out")) }
            return
        }
        _state.update { it.copy(confirmPaymentDialog = it.confirmPaymentDialog.copy(isProcessing = true, error = null)) }
        launchWithGlobalLoading {
            // Bill status in "bills" is already current from prior addTransaction/updateItems writes.
            // confirmPayment's role is to trigger accounting and officially close the billing session.
            _state.update { it.copy(
                confirmPaymentDialog = it.confirmPaymentDialog.copy(isProcessing = false, done = true),
                accountingStatus = AccountingStatus.InProgress,
            ) }
            com.example.dreamland_reception.DreamlandAppInitializer.getBillingViewModel().load()

            // Accounting runs in a sibling coroutine — failure is surfaced via accountingStatus only.
            val settledBill = bill
            val guestPhone = _state.value.stay?.guestPhone ?: ""
            viewModelScope.launch {
                accountingRepo.settle(settledBill, guestPhone)
                    .onSuccess { _state.update { it.copy(accountingStatus = AccountingStatus.Synced) } }
                    .onFailure { e ->
                        _state.update { it.copy(accountingStatus = AccountingStatus.Failed(e.message ?: "Accounting sync failed")) }
                    }
            }
        }
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private fun recalculate(bill: Bill): Bill {
        val subtotal = bill.items.sumOf { it.total }
        val taxAmount = if (bill.taxEnabled) Math.round(subtotal * bill.taxPercentage / 100.0).toDouble() else 0.0
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
