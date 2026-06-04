package com.example.dreamland_reception.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.dreamland_reception.data.AppContext
import com.example.dreamland_reception.data.accounting.AccountingRepository
import com.example.dreamland_reception.data.billing.HumbleBillEngine
import com.example.dreamland_reception.data.model.Bill
import com.example.dreamland_reception.data.model.BillItem
import com.example.dreamland_reception.data.model.PaymentTransaction
import com.example.dreamland_reception.data.model.Stay
import com.example.dreamland_reception.data.model.RoomInstance
import com.example.dreamland_reception.data.repository.BillRepository
import com.example.dreamland_reception.data.repository.FirestoreUserRepository
import com.example.dreamland_reception.data.repository.UserRepository
import com.example.dreamland_reception.data.repository.FirestoreBillRepository
import com.example.dreamland_reception.data.repository.FirestoreFoodItemRepository
import com.example.dreamland_reception.data.repository.FirestoreOrderRepository
import com.example.dreamland_reception.data.repository.FirestoreRoomInstanceRepository
import com.example.dreamland_reception.data.repository.FirestoreRoomRepository
import com.example.dreamland_reception.data.repository.FirestoreServiceRepository
import com.example.dreamland_reception.data.repository.FirestoreStayRepository
import com.example.dreamland_reception.data.repository.FoodItemRepository
import com.example.dreamland_reception.data.repository.OrderRepository
import com.example.dreamland_reception.data.repository.RoomInstanceRepository
import com.example.dreamland_reception.data.repository.RoomRepository
import com.example.dreamland_reception.data.repository.ServiceRepository
import com.example.dreamland_reception.data.repository.StayRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.awt.image.BufferedImage
import java.time.temporal.ChronoUnit
import java.util.Date
import java.util.UUID
import com.example.dreamland_reception.util.normalizePhoneE164

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
    val roomNumber: String = "",
    val roomCategory: String = "",
    val name: String = "",
    val nameQuery: String = "",
    val showNameDropdown: Boolean = false,
    val showAddFoodDialog: Boolean = false,
    val showAddServiceDialog: Boolean = false,
    val quantity: String = "1",
    val unitPrice: String = "",
    val taxPct: String = "",
    val notes: String = "",
    val isSaving: Boolean = false,
)

// ── Add-payment dialog ────────────────────────────────────────────────────────

data class AddPaymentDialog(
    val show: Boolean = false,
    val amount: String = "",
    val method: String = "CASH",   // CASH | UPI | CARD
    val cashAmount: String = "",
    val bankAmount: String = "",
    val isSaving: Boolean = false,
)

data class GuestNameOption(
    val name: String,
    val phone: String,
    val roomNumber: String,
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
    val taxPct: String = "",
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

// ── Invoice PDF viewer ────────────────────────────────────────────────────────

data class InvoicePdfState(
    val show: Boolean = false,
    val isGenerating: Boolean = false,
    val error: String? = null,
    val url: String = "",
    val pages: List<BufferedImage> = emptyList(),
    val availablePrinters: List<String> = emptyList(),
    val selectedPrinter: String = "",
    val isPrinting: Boolean = false,
    val printError: String? = null,
    val emailDialogOpen: Boolean = false,
    val guestEmail: String = "",
    val isSending: Boolean = false,
    val sendError: String? = null,
    val sendSuccess: Boolean = false,
    val showQrDialog: Boolean = false,
    val qrImage: BufferedImage? = null,
)

// ── Main state ────────────────────────────────────────────────────────────────

data class StayBillingState(
    val isLoading: Boolean = true,
    val error: String? = null,
    val stay: Stay? = null,
    val bill: Bill? = null,
    val billGuestName: String = "",  // editable guest name for the bill header
    val billGuestPhone: String = "",  // editable guest phone for the bill header
    val pendingGuestNameOverride: String = "",  // set before load(); applied on bill load then cleared
    val editableTaxPct: String = "",         // inline-editable tax percentage in summary
    val editableAdvancePaid: String = "",    // inline-editable advance paid in summary
    val editableDiscountType: String = "FLAT",  // FLAT | PERCENT
    val editableDiscountValue: String = "",      // inline-editable discount value
    val nightsMismatchCount: Int? = null,  // non-null when ROOM nights ≠ other rooms or bill dates
    val guestPickerOpen: Boolean = false,
    val billGuests: List<GuestNameOption> = emptyList(),

    // catalog data for add-item dropdowns
    val roomInstances: List<RoomInstance> = emptyList(),
    val rooms: List<com.example.dreamland_reception.data.model.Room> = emptyList(),
    val foodItems: List<com.example.dreamland_reception.data.model.FoodItem> = emptyList(),
    val services: List<com.example.dreamland_reception.data.model.Service> = emptyList(),

    // dialogs
    val addItemDialog: AddBillItemDialog = AddBillItemDialog(),
    val editBillItemDialog: EditBillItemDialog = EditBillItemDialog(),
    val addPaymentDialog: AddPaymentDialog = AddPaymentDialog(),
    val editPaymentDialog: EditPaymentDialog = EditPaymentDialog(),
    val taxDiscountDialog: TaxDiscountDialog = TaxDiscountDialog(),
    val confirmPaymentDialog: ConfirmPaymentDialog = ConfirmPaymentDialog(),
    val accountingStatus: AccountingStatus = AccountingStatus.Idle,
    val invoicePdf: InvoicePdfState = InvoicePdfState(),
)

// ── ViewModel ─────────────────────────────────────────────────────────────────

class StayBillingViewModel(
    private val billRepo: BillRepository = FirestoreBillRepository,
    private val stayRepo: StayRepository = FirestoreStayRepository,
    private val orderRepo: OrderRepository = FirestoreOrderRepository,
    private val roomRepo: RoomRepository = FirestoreRoomRepository,
    private val instanceRepo: RoomInstanceRepository = FirestoreRoomInstanceRepository,
    private val foodRepo: FoodItemRepository = FirestoreFoodItemRepository,
    private val serviceRepo: ServiceRepository = FirestoreServiceRepository,
    private val userRepo: UserRepository = FirestoreUserRepository,
    private val accountingRepo: AccountingRepository = AccountingRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(StayBillingState())
    val state: StateFlow<StayBillingState> = _state.asStateFlow()

    fun loadByBillId(billId: String) {
        if (billId.isBlank()) return
        launchWithGlobalLoading {
            _state.update { it.copy(isLoading = true, error = null, addPaymentDialog = AddPaymentDialog()) }
            val bill = runCatching { billRepo.getById(billId) }.getOrNull()
                ?.let { recalculate(it) }
                ?.let { migrateRoomItemTaxRates(it) }
                ?.let { migrateFoodServiceItemTaxRates(it) }
            val pd = initPaymentAmountsFrom(bill)
            val override = _state.value.pendingGuestNameOverride
            val guestName = override.takeIf { it.isNotBlank() } ?: bill?.guestName ?: ""
            val guestPhone = bill?.guestPhone?.ifBlank { null } ?: ""
            val hotelId = AppContext.hotelId
            val instances = runCatching { instanceRepo.getByHotel(hotelId) }.getOrElse { emptyList() }
            val allRooms = runCatching { roomRepo.getByHotel(hotelId) }.getOrElse { emptyList() }
            val foods = runCatching { foodRepo.getByHotel(hotelId) }.getOrElse { emptyList() }
            val svcs = runCatching { serviceRepo.getByHotel(hotelId) }.getOrElse { emptyList() }
            _state.update { it.copy(isLoading = false, stay = null, bill = bill, billGuestName = guestName, billGuestPhone = guestPhone, pendingGuestNameOverride = "",
                editableTaxPct = bill?.taxPercentage?.let { if (it > 0) "%.0f".format(it) else "" } ?: "",
                editableAdvancePaid = bill?.advancePayment?.let { if (it > 0) "%.2f".format(it) else "" } ?: "",
                editableDiscountType = bill?.discountType ?: "FLAT",
                editableDiscountValue = bill?.discountValue?.let { if (it > 0) "%.2f".format(it) else "" } ?: "",
                roomInstances = instances, rooms = allRooms, foodItems = foods, services = svcs,
                addPaymentDialog = pd, confirmPaymentDialog = ConfirmPaymentDialog(), accountingStatus = AccountingStatus.Idle,
                invoicePdf = InvoicePdfState(), error = if (bill == null) "Bill not found" else null) }
        }
    }

    fun load(stayId: String) {
        if (stayId.isBlank()) return
        launchWithGlobalLoading {
            _state.update { it.copy(isLoading = true, error = null, addPaymentDialog = AddPaymentDialog()) }
            val stay = runCatching { stayRepo.getById(stayId) }.getOrNull()
            var bill = runCatching { billRepo.getByStay(stayId) }.getOrNull()
                ?.let { recalculate(it) }
                ?.let { migrateRoomItemTaxRates(it) }
                ?.let { migrateFoodServiceItemTaxRates(it) }

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
                        name = run {
                            val catName = (room?.type ?: stay.roomCategoryName).ifBlank { null }
                            "Room ${stay.roomNumber}${if (catName != null) " · $catName" else ""} — Room Charges ($nights night${if (nights > 1) "s" else ""} × ₹${roomPricePerNight.toLong()})"
                        },
                        type = "ROOM", quantity = nights.toInt(), unitPrice = roomPricePerNight, total = roomCharges,
                        taxPercentage = taxPercentage,
                    ))
                    if (breakfastCharge > 0) add(BillItem(name = "Breakfast", type = "SERVICE", quantity = 1, unitPrice = breakfastCharge, total = breakfastCharge))
                    if (stay.earlyCheckInCharge > 0) add(BillItem(name = "Early Check-in", type = "SERVICE", quantity = 1, unitPrice = stay.earlyCheckInCharge, total = stay.earlyCheckInCharge))
                    if (stay.lateCheckOutCharge > 0) add(BillItem(name = "Late Check-out", type = "SERVICE", quantity = 1, unitPrice = stay.lateCheckOutCharge, total = stay.lateCheckOutCharge))
                    for (order in orders) {
                        if (order.totalAmount > 0) {
                            // Use pre-tax subtotal; fall back to per-item subtotals for legacy orders
                            val orderBase = when {
                                order.subtotalAmount > 0 -> order.subtotalAmount
                                order.items.any { it.subtotal > 0 } -> order.items.sumOf { it.subtotal }
                                else -> order.totalAmount
                            }
                            val orderTax = when {
                                order.totalTaxAmount > 0 -> order.totalTaxAmount
                                order.items.any { it.taxAmount > 0 } -> order.items.sumOf { it.taxAmount }
                                else -> 0.0
                            }
                            val effectiveTaxPct = if (orderBase > 0 && orderTax > 0)
                                orderTax / orderBase * 100.0 else 0.0
                            add(BillItem(
                                name = order.items.joinToString(", ") { it.name }.ifBlank { "Order" },
                                type = "ORDER", quantity = 1, unitPrice = orderBase, total = orderBase,
                                taxPercentage = effectiveTaxPct,
                                refId = order.id, notes = order.notes,
                            ))
                        }
                    }
                }
                val advance = stay.advancePaidAmount
                val subtotal = items.sumOf { it.total }
                val taxEnabled = taxPercentage > 0
                val taxAmount = if (taxEnabled) subtotal * taxPercentage / 100.0 else 0.0
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
                    advancePayment = advance, advancePaymentMethod = stay.advancePaymentMethod,
                    pendingAmount = pending, status = status,
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

            val pd = initPaymentAmountsFrom(bill)
            val override = _state.value.pendingGuestNameOverride
            val guestName = override.takeIf { it.isNotBlank() } ?: bill?.guestName ?: stay?.guestName ?: ""
            val guestPhone2 = bill?.guestPhone?.ifBlank { null } ?: stay?.guestPhone ?: ""
            val hotelId2 = AppContext.hotelId
            val instances2 = runCatching { instanceRepo.getByHotel(hotelId2) }.getOrElse { emptyList() }
            val allRooms2 = runCatching { roomRepo.getByHotel(hotelId2) }.getOrElse { emptyList() }
            val foods2 = runCatching { foodRepo.getByHotel(hotelId2) }.getOrElse { emptyList() }
            val svcs2 = runCatching { serviceRepo.getByHotel(hotelId2) }.getOrElse { emptyList() }
            _state.update { it.copy(isLoading = false, stay = stay, bill = bill, billGuestName = guestName, billGuestPhone = guestPhone2, pendingGuestNameOverride = "",
                editableTaxPct = bill?.taxPercentage?.let { if (it > 0) "%.0f".format(it) else "" } ?: "",
                editableAdvancePaid = bill?.advancePayment?.let { if (it > 0) "%.2f".format(it) else "" } ?: "",
                editableDiscountType = bill?.discountType ?: "FLAT",
                editableDiscountValue = bill?.discountValue?.let { if (it > 0) "%.2f".format(it) else "" } ?: "",
                roomInstances = instances2, rooms = allRooms2, foodItems = foods2, services = svcs2,
                addPaymentDialog = pd, confirmPaymentDialog = ConfirmPaymentDialog(), accountingStatus = AccountingStatus.Idle,
                invoicePdf = InvoicePdfState()) }
        }
    }

    private suspend fun migrateRoomItemTaxRates(bill: Bill): Bill {
        if (bill.items.none { it.type == "ROOM" && it.taxPercentage == 0.0 }) return bill
        val stayIds = bill.stayIds.ifEmpty { listOfNotNull(bill.stayId.takeIf { it.isNotBlank() }) }
        val stays = stayIds.mapNotNull { id -> runCatching { stayRepo.getById(id) }.getOrNull() }
        if (stays.isEmpty()) return bill
        val roomTaxMap = mutableMapOf<String, Double>()
        for (stay in stays) {
            val room = runCatching { roomRepo.getById(stay.roomCategoryId) }.getOrNull()
            if (room != null && room.taxPercentage > 0) roomTaxMap[stay.roomNumber] = room.taxPercentage
        }
        if (roomTaxMap.isEmpty()) return bill
        val updatedItems = bill.items.map { item ->
            if (item.type != "ROOM" || item.taxPercentage > 0) item
            else {
                val roomNum = if (item.name.startsWith("Room ")) {
                    item.name.removePrefix("Room ").trim().split(" ", "·", "—").firstOrNull()?.trim() ?: ""
                } else ""
                item.copy(taxPercentage = roomTaxMap[roomNum] ?: 0.0)
            }
        }
        val updatedBill = recalculate(bill.copy(items = updatedItems))
        if (bill.id.isNotBlank()) {
            runCatching {
                billRepo.updateItems(bill.id, updatedItems, updatedBill.subtotal, updatedBill.taxAmount,
                    updatedBill.discountAmount, updatedBill.totalAmount, updatedBill.pendingAmount, updatedBill.status)
            }
        }
        return updatedBill
    }

    private suspend fun migrateFoodServiceItemTaxRates(bill: Bill): Bill {
        // Always recompute ORDER items that have a refId (blended rate may be stale);
        // for SERVICE items only fix if rate is missing (0)
        val needsMigration = bill.items.any {
            (it.type == "ORDER" && it.refId.isNotBlank()) ||
            (it.type == "SERVICE" && it.taxPercentage == 0.0)
        }
        if (!needsMigration) return bill
        val hotelId = bill.hotelId.ifBlank { AppContext.hotelId }
        val foodItems = runCatching { foodRepo.getByHotel(hotelId) }.getOrElse { emptyList() }
        val services = runCatching { serviceRepo.getByHotel(hotelId) }.getOrElse { emptyList() }
        val foodTaxByName = foodItems.filter { it.taxPercentage > 0 }.associate { it.name.trim().lowercase() to it.taxPercentage }
        val serviceTaxByName = services.filter { it.taxPercentage > 0 }.associate { it.name.trim().lowercase() to it.taxPercentage }

        // Pre-fetch orders for ORDER items that need migration (avoid suspend inside map lambda)
        // Pre-fetch all ORDER items with a refId (always recompute their blended rate)
        val orderRefIds = bill.items.filter { it.type == "ORDER" && it.refId.isNotBlank() }
            .map { it.refId }.distinct()
        val fetchedOrders = mutableMapOf<String, com.example.dreamland_reception.data.model.Order>()
        for (id in orderRefIds) {
            runCatching { orderRepo.getById(id) }.getOrNull()?.let { fetchedOrders[id] = it }
        }

        var changed = false
        val updatedItems = bill.items.map { item ->
            // For ORDER items with a refId, always recompute; for SERVICE, skip if rate already set
            if (item.taxPercentage > 0 && !(item.type == "ORDER" && item.refId.isNotBlank())) return@map item
            val order = if (item.type == "ORDER" && item.refId.isNotBlank()) fetchedOrders[item.refId] else null
            val rate: Double? = when (item.type) {
                "ORDER" -> {
                    foodTaxByName[item.name.trim().lowercase()]
                        ?: serviceTaxByName[item.name.trim().lowercase()]
                        ?: if (order != null) {
                            var taxSum = 0.0; var baseSum = 0.0
                            for (oi in order.items) {
                                // Use stored rate first (from when order was placed); catalog as fallback
                                val r = if (oi.taxPercentage > 0) oi.taxPercentage
                                        else (foodTaxByName[oi.name.trim().lowercase()]
                                            ?: serviceTaxByName[oi.name.trim().lowercase()]
                                            ?: 0.0)
                                val base = if (oi.subtotal > 0) oi.subtotal else oi.basePrice
                                taxSum += base * r / 100.0
                                baseSum += base
                            }
                            if (baseSum > 0 && taxSum > 0) taxSum / baseSum * 100.0 else null
                        } else null
                }
                "SERVICE" -> serviceTaxByName[item.name.trim().lowercase()]
                    ?: foodTaxByName[item.name.trim().lowercase()]
                else -> null
            } ?: return@map item
            changed = true
            // For ORDER items resolved via order fetch, also correct the pre-tax base total
            val correctBase = if (order != null) when {
                order.subtotalAmount > 0 -> order.subtotalAmount
                order.items.any { it.subtotal > 0 } -> order.items.sumOf { it.subtotal }
                else -> item.total
            } else item.total
            item.copy(taxPercentage = rate!!, total = correctBase, unitPrice = correctBase)
        }
        if (!changed) return bill
        val updatedBill = recalculate(bill.copy(items = updatedItems))
        if (bill.id.isNotBlank()) {
            runCatching {
                billRepo.updateItems(bill.id, updatedItems, updatedBill.subtotal, updatedBill.taxAmount,
                    updatedBill.discountAmount, updatedBill.totalAmount, updatedBill.pendingAmount, updatedBill.status)
            }
        }
        return updatedBill
    }

    private fun initPaymentAmountsFrom(bill: Bill?): AddPaymentDialog {
        val txs = bill?.transactions ?: return AddPaymentDialog()
        val rawCash = txs.filter { it.method == "CASH" }.sumOf { it.amount }
        val rawBank = txs.filter { it.method == "BANK" }.sumOf { it.amount }
        // Cap to live remaining balance so stale duplicate transactions don't cause overpayment display
        val liveTotal = if (bill != null) recalculate(bill).totalAmount else 0.0
        val remaining = (liveTotal - (bill?.advancePayment ?: 0.0)).coerceAtLeast(0.0)
        val cash = minOf(rawCash, remaining)
        val bank = minOf(rawBank, (remaining - cash).coerceAtLeast(0.0))
        return AddPaymentDialog(
            cashAmount = if (cash > 0) "%.2f".format(cash) else "",
            bankAmount = if (bank > 0) "%.2f".format(bank) else "",
        )
    }

    // ── Add item ──────────────────────────────────────────────────────────────

    fun updateDates(checkIn: java.util.Date, checkOut: java.util.Date) {
        _state.update { it.copy(nightsMismatchCount = null) }
        val bill = _state.value.bill ?: return
        // Recalculate ROOM items based on the new number of nights
        val nights = ChronoUnit.DAYS.between(checkIn.toInstant(), checkOut.toInstant()).coerceAtLeast(1)
        val updatedItems = bill.items.map { item ->
            if (item.type != "ROOM") item
            else {
                val newTotal = item.unitPrice * nights
                val roomPrefix = if (item.name.contains(" — Room Charges")) item.name.substringBefore(" — Room Charges") + " — " else ""
                val newName = "${roomPrefix}Room Charges ($nights night${if (nights != 1L) "s" else ""} × ₹${item.unitPrice.toLong()})"
                item.copy(name = newName, quantity = nights.toInt(), total = newTotal)
            }
        }
        val updatedBill = recalculate(bill.copy(checkInDate = checkIn, checkOutDate = checkOut, items = updatedItems))
        _state.update { it.copy(bill = updatedBill) }
        if (bill.id.isBlank()) return
        launchWithGlobalLoading {
            runCatching {
                billRepo.updateDates(bill.id, checkIn, checkOut)
                billRepo.updateItems(bill.id, updatedItems, updatedBill.subtotal, updatedBill.taxAmount, updatedBill.discountAmount, updatedBill.totalAmount, updatedBill.pendingAmount, updatedBill.status)
            }
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

    // ROOM dropdowns
    fun onAddItemRoomCategory(cat: String) = _state.update { s ->
        s.copy(addItemDialog = s.addItemDialog.copy(roomCategory = cat, roomNumber = ""))
    }
    fun onAddItemRoomInstanceSelected(instance: RoomInstance) {
        val room = _state.value.rooms.find { it.id == instance.categoryId }
        // Derive category name from Room.type if categoryName on the instance is blank
        val catName = instance.categoryName.ifBlank { room?.type ?: "" }
        _state.update { s -> s.copy(addItemDialog = s.addItemDialog.copy(
            roomNumber = instance.roomNumber,
            roomCategory = catName,
            unitPrice = if (room != null && room.pricePerNight > 0) room.pricePerNight.toLong().toString() else "",
            taxPct = if (room != null && room.taxPercentage > 0) "%.0f".format(room.taxPercentage) else "",
        )) }
    }

    // ORDER/SERVICE autocomplete
    fun onAddItemNameQuery(v: String) = _state.update { s ->
        s.copy(addItemDialog = s.addItemDialog.copy(nameQuery = v, name = v, showNameDropdown = true))
    }
    fun onAddItemNameDropdownDismiss() = _state.update { s ->
        s.copy(addItemDialog = s.addItemDialog.copy(showNameDropdown = false))
    }
    fun onAddItemTaxPct(v: String) = _state.update { s ->
        s.copy(addItemDialog = s.addItemDialog.copy(taxPct = v.filter { c -> c.isDigit() || c == '.' }))
    }
    fun onAddItemFoodSelected(item: com.example.dreamland_reception.data.model.FoodItem) = _state.update { s ->
        s.copy(addItemDialog = s.addItemDialog.copy(
            name = item.name, nameQuery = item.name, showNameDropdown = false,
            unitPrice = if (item.price > 0) item.price.toLong().toString() else "",
            taxPct = if (item.taxPercentage > 0) "%.0f".format(item.taxPercentage) else "",
        ))
    }
    fun onAddItemServiceSelected(svc: com.example.dreamland_reception.data.model.Service) = _state.update { s ->
        s.copy(addItemDialog = s.addItemDialog.copy(
            name = svc.name, nameQuery = svc.name, showNameDropdown = false,
            unitPrice = if (svc.price > 0) svc.price.toLong().toString() else "",
            taxPct = if (svc.taxPercentage > 0) "%.0f".format(svc.taxPercentage) else "",
        ))
    }

    // Open Settings dialogs for adding new food/service items
    fun onAddItemAddNewFood(name: String) {
        _state.update { s -> s.copy(addItemDialog = s.addItemDialog.copy(showAddFoodDialog = true)) }
        com.example.dreamland_reception.DreamlandAppInitializer.getSettingsViewModel().openAddFood(name)
    }
    fun onAddItemFoodDialogClosed() {
        launchWithGlobalLoading {
            val updated = runCatching { foodRepo.getByHotel(AppContext.hotelId) }.getOrElse { _state.value.foodItems }
            val lastAdded = updated.sortedByDescending { it.createdAt }.firstOrNull()
            _state.update { s -> s.copy(
                foodItems = updated,
                addItemDialog = s.addItemDialog.copy(
                    showAddFoodDialog = false,
                    name = lastAdded?.name ?: s.addItemDialog.name,
                    nameQuery = lastAdded?.name ?: s.addItemDialog.nameQuery,
                    unitPrice = lastAdded?.price?.toLong()?.toString() ?: s.addItemDialog.unitPrice,
                    taxPct = if (lastAdded != null && lastAdded.taxPercentage > 0) "%.0f".format(lastAdded.taxPercentage) else s.addItemDialog.taxPct,
                    showNameDropdown = false,
                )
            ) }
        }
    }
    fun onAddItemAddNewService(name: String) {
        _state.update { s -> s.copy(addItemDialog = s.addItemDialog.copy(showAddServiceDialog = true)) }
        com.example.dreamland_reception.DreamlandAppInitializer.getSettingsViewModel().openAddService(name)
    }
    fun onAddItemServiceDialogClosed() {
        launchWithGlobalLoading {
            val updated = runCatching { serviceRepo.getByHotel(AppContext.hotelId) }.getOrElse { _state.value.services }
            val lastAdded = updated.sortedByDescending { it.createdAt }.firstOrNull()
            _state.update { s -> s.copy(
                services = updated,
                addItemDialog = s.addItemDialog.copy(
                    showAddServiceDialog = false,
                    name = lastAdded?.name ?: s.addItemDialog.name,
                    nameQuery = lastAdded?.name ?: s.addItemDialog.nameQuery,
                    unitPrice = lastAdded?.price?.toLong()?.toString() ?: s.addItemDialog.unitPrice,
                    taxPct = if (lastAdded != null && lastAdded.taxPercentage > 0) "%.0f".format(lastAdded.taxPercentage) else s.addItemDialog.taxPct,
                    showNameDropdown = false,
                )
            ) }
        }
    }

    fun submitAddItem() {
        val d = _state.value.addItemDialog
        val bill = _state.value.bill ?: return
        if (bill.id.isBlank() || d.name.isBlank()) return
        val qty = d.quantity.toIntOrNull()?.coerceAtLeast(1) ?: 1
        val price = d.unitPrice.toDoubleOrNull() ?: 0.0
        _state.update { it.copy(addItemDialog = d.copy(isSaving = true)) }
        launchWithGlobalLoading {
            val itemName = if (d.type == "ROOM" && d.roomNumber.isNotBlank()) {
                val catPart = if (d.roomCategory.isNotBlank()) " · ${d.roomCategory.trim()}" else ""
                "Room ${d.roomNumber.trim()}$catPart — ${d.name.trim()}"
            } else d.name.trim()
            val newItem = BillItem(
                id = UUID.randomUUID().toString(),
                name = itemName,
                type = d.type,
                quantity = qty,
                unitPrice = price,
                total = price * qty,
                taxPercentage = d.taxPct.toDoubleOrNull() ?: 0.0,
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
            taxPct = if (item.taxPercentage > 0) "%.0f".format(item.taxPercentage) else "",
            notes = item.notes,
        ))
    }
    fun closeEditItem() = _state.update { it.copy(editBillItemDialog = EditBillItemDialog()) }
    fun onEditItemName(v: String) = _state.update { it.copy(editBillItemDialog = it.editBillItemDialog.copy(name = v)) }
    fun onEditItemQty(v: String) = _state.update { s ->
        val qty = v.filter(Char::isDigit)
        val d = s.editBillItemDialog.copy(quantity = qty)
        val updated = if (d.type == "ROOM") {
            val nights = qty.toIntOrNull() ?: 1
            val price = d.unitPrice.toDoubleOrNull() ?: 0.0
            val roomPrefix = if (d.name.contains(" — Room Charges")) d.name.substringBefore(" — Room Charges") + " — " else ""
            d.copy(name = "${roomPrefix}Room Charges ($nights night${if (nights != 1) "s" else ""} × ₹${price.toLong()})")
        } else d
        s.copy(editBillItemDialog = updated)
    }
    fun onEditItemPrice(v: String) = _state.update { s ->
        val price = v.filter { c -> c.isDigit() || c == '.' }
        val d = s.editBillItemDialog.copy(unitPrice = price)
        val updated = if (d.type == "ROOM") {
            val nights = d.quantity.toIntOrNull() ?: 1
            val priceVal = price.toDoubleOrNull() ?: 0.0
            val roomPrefix = if (d.name.contains(" — Room Charges")) d.name.substringBefore(" — Room Charges") + " — " else ""
            d.copy(name = "${roomPrefix}Room Charges ($nights night${if (nights != 1) "s" else ""} × ₹${priceVal.toLong()})")
        } else d
        s.copy(editBillItemDialog = updated)
    }
    fun onEditItemTaxPct(v: String) = _state.update { it.copy(editBillItemDialog = it.editBillItemDialog.copy(taxPct = v.filter { c -> c.isDigit() || c == '.' })) }
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
                if (item.id == d.itemId) item.copy(name = d.name.trim(), quantity = qty, unitPrice = price, total = price * qty, notes = d.notes.trim(), taxPercentage = d.taxPct.toDoubleOrNull() ?: item.taxPercentage)
                else item
            }
            val totals = recalculate(bill.copy(items = updatedItems))
            // Detect mismatches when editing a ROOM item
            val mismatch: Int? = if (d.type == "ROOM") {
                val dateNights = if (bill.checkInDate != null && bill.checkOutDate != null)
                    ChronoUnit.DAYS.between(bill.checkInDate.toInstant(), bill.checkOutDate.toInstant()).toInt()
                else null
                val otherRoomQtys = updatedItems.filter { it.type == "ROOM" && it.id != d.itemId }.map { it.quantity }.distinct()
                val hasCrossRoomMismatch = otherRoomQtys.isNotEmpty() && otherRoomQtys != listOf(qty)
                val hasDateMismatch = dateNights != null && qty != dateNights
                if (hasCrossRoomMismatch || hasDateMismatch) qty else null
            } else null
            runCatching {
                billRepo.updateItems(bill.id, updatedItems, totals.subtotal, totals.taxAmount, totals.discountAmount, totals.totalAmount, totals.pendingAmount, totals.status)
            }.onSuccess {
                _state.update { s -> s.copy(bill = s.bill?.let { totals }, editBillItemDialog = EditBillItemDialog(), nightsMismatchCount = mismatch) }
            }.onFailure {
                _state.update { it.copy(editBillItemDialog = d.copy(isSaving = false)) }
            }
        }
    }

    // ── Nights mismatch actions ───────────────────────────────────────────────

    fun syncAllRoomsToNights() {
        val nights = _state.value.nightsMismatchCount ?: return
        val bill = _state.value.bill ?: return
        val updatedItems = bill.items.map { item ->
            if (item.type != "ROOM") item
            else {
                val roomPrefix = if (item.name.contains(" — Room Charges")) item.name.substringBefore(" — Room Charges") + " — " else ""
                item.copy(
                    name = "${roomPrefix}Room Charges ($nights night${if (nights != 1) "s" else ""} × ₹${item.unitPrice.toLong()})",
                    quantity = nights,
                    total = item.unitPrice * nights,
                )
            }
        }
        val totals = recalculate(bill.copy(items = updatedItems))
        _state.update { it.copy(bill = totals, nightsMismatchCount = null) }
        if (bill.id.isBlank()) return
        viewModelScope.launch {
            runCatching { billRepo.updateItems(bill.id, updatedItems, totals.subtotal, totals.taxAmount, totals.discountAmount, totals.totalAmount, totals.pendingAmount, totals.status) }
        }
    }

    fun fixCheckoutDateForNights() {
        val bill = _state.value.bill ?: return
        val nights = _state.value.nightsMismatchCount ?: return
        val checkIn = bill.checkInDate ?: return
        val newCheckOut = java.util.Date(checkIn.time + nights * 86_400_000L)
        _state.update { it.copy(nightsMismatchCount = null) }
        updateDates(checkIn, newCheckOut)
    }

    fun dismissNightsMismatch() = _state.update { it.copy(nightsMismatchCount = null) }

    // ── Add payment ───────────────────────────────────────────────────────────

    fun openAddPayment() = _state.update { it.copy(addPaymentDialog = AddPaymentDialog(show = true)) }
    fun closeAddPayment() = _state.update { it.copy(addPaymentDialog = AddPaymentDialog()) }
    fun onPaymentAmount(v: String) = _state.update { it.copy(addPaymentDialog = it.addPaymentDialog.copy(amount = v.filter { c -> c.isDigit() || c == '.' })) }
    fun onPaymentMethod(v: String) = _state.update { it.copy(addPaymentDialog = it.addPaymentDialog.copy(method = v)) }
    fun onCashAmount(v: String) = _state.update { it.copy(addPaymentDialog = it.addPaymentDialog.copy(cashAmount = v.filter { c -> c.isDigit() || c == '.' })) }
    fun onBankAmount(v: String) = _state.update { it.copy(addPaymentDialog = it.addPaymentDialog.copy(bankAmount = v.filter { c -> c.isDigit() || c == '.' })) }

    fun updateMethodTotal(method: String) {
        val d = _state.value.addPaymentDialog
        val bill = _state.value.bill ?: return
        if (bill.id.isBlank()) return
        val raw = if (method == "CASH") d.cashAmount else d.bankAmount
        val newTotal = raw.toDoubleOrNull() ?: 0.0
        // Drop existing transactions for this method and replace with one at newTotal
        val kept = bill.transactions.filter { it.method != method }
        val updated = if (newTotal > 0)
            kept + PaymentTransaction(id = UUID.randomUUID().toString(), amount = newTotal, method = method)
        else kept
        val newTotalPaid = updated.sumOf { it.amount }
        val recalcTotals = recalculate(bill)  // use live total from current items/tax
        val newPending = (recalcTotals.totalAmount - newTotalPaid - bill.advancePayment).coerceAtLeast(0.0)
        val newStatus = when {
            newPending <= 0 && recalcTotals.totalAmount > 0 -> "PAID"
            newTotalPaid + bill.advancePayment > 0 -> "PARTIAL"
            else -> "PENDING"
        }
        _state.update { it.copy(addPaymentDialog = d.copy(isSaving = true)) }
        launchWithGlobalLoading {
            runCatching {
                billRepo.updateTransactions(bill.id, updated, newTotalPaid, newPending, newStatus)
            }.onSuccess {
                _state.update { s ->
                    s.copy(
                        bill = s.bill?.copy(
                            transactions = updated,
                            totalPaid = newTotalPaid,
                            pendingAmount = newPending,
                            status = newStatus,
                        ),
                        addPaymentDialog = d.copy(isSaving = false),
                    )
                }
            }.onFailure {
                _state.update { it.copy(addPaymentDialog = d.copy(isSaving = false)) }
            }
        }
    }

    fun submitPaymentForMethod(method: String) {
        val d = _state.value.addPaymentDialog
        val bill = _state.value.bill ?: return
        if (bill.id.isBlank()) return
        val raw = if (method == "CASH") d.cashAmount else d.bankAmount
        val amount = raw.toDoubleOrNull() ?: return
        if (amount <= 0) return
        _state.update { it.copy(addPaymentDialog = d.copy(isSaving = true)) }
        launchWithGlobalLoading {
            val tx = PaymentTransaction(id = UUID.randomUUID().toString(), amount = amount, method = method)
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
                    val cleared = if (method == "CASH") s.addPaymentDialog.copy(cashAmount = "")
                                  else s.addPaymentDialog.copy(bankAmount = "")
                    s.copy(
                        bill = s.bill?.copy(
                            transactions = s.bill.transactions + tx,
                            totalPaid = newTotalPaid,
                            pendingAmount = newPending,
                            status = newStatus,
                        ),
                        addPaymentDialog = cleared.copy(isSaving = false),
                    )
                }
            }.onFailure {
                _state.update { it.copy(addPaymentDialog = d.copy(isSaving = false)) }
            }
        }
    }

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

    // ── Guest name ───────────────────────────────────────────────────────────

    fun setPendingGuestName(name: String) = _state.update { it.copy(pendingGuestNameOverride = name) }

    fun payViaQr() {
        val bill = _state.value.bill ?: return
        // Compute tax using per-item rates first (same logic as BillSummaryCard)
        // This handles multi-rate bills (e.g. 12% ROOM + 18% ORDER) correctly
        val perItemTax = bill.items.filter { it.taxPercentage > 0 }.sumOf { it.total * it.taxPercentage / 100.0 }
        val liveTax = if (perItemTax > 0) {
            perItemTax
        } else {
            val liveRate = _state.value.editableTaxPct.toDoubleOrNull() ?: bill.taxPercentage
            bill.subtotal * liveRate / 100.0
        }
        val discV = _state.value.editableDiscountValue.toDoubleOrNull() ?: bill.discountValue
        val discA = when (_state.value.editableDiscountType) {
            "PERCENT" -> bill.subtotal * discV / 100.0
            else -> discV
        }
        val liveTotal = (bill.subtotal + liveTax - discA).coerceAtLeast(0.0)
        val liveAdv = _state.value.editableAdvancePaid.toDoubleOrNull() ?: bill.advancePayment
        val typedCash = _state.value.addPaymentDialog.cashAmount.toDoubleOrNull() ?: 0.0
        val pending = (liveTotal - liveAdv - typedCash).coerceAtLeast(0.0)
        if (pending <= 0) return
        _state.update { it.copy(addPaymentDialog = it.addPaymentDialog.copy(bankAmount = "%.2f".format(pending))) }
    }

    fun onBillGuestName(v: String) = _state.update { it.copy(billGuestName = v) }

    fun onBillGuestPhone(v: String) = _state.update { it.copy(billGuestPhone = v.filter { c -> c.isDigit() }.take(10)) }

    fun saveTaxRateForGroup(oldRate: Double, newRate: Double) {
        val bill = _state.value.bill?.takeIf { it.id.isNotBlank() } ?: return
        val updatedItems = bill.items.map { item ->
            if (item.taxPercentage == oldRate) item.copy(taxPercentage = newRate) else item
        }
        val updatedBill = recalculate(bill.copy(taxEnabled = updatedItems.any { it.taxPercentage > 0 }, items = updatedItems))
        _state.update { it.copy(bill = updatedBill) }
        viewModelScope.launch {
            runCatching {
                billRepo.updateItems(bill.id, updatedItems, updatedBill.subtotal, updatedBill.taxAmount,
                    updatedBill.discountAmount, updatedBill.totalAmount, updatedBill.pendingAmount, updatedBill.status)
            }
        }
    }

    fun onTaxPctInline(v: String) = _state.update { it.copy(editableTaxPct = v.filter { c -> c.isDigit() || c == '.' }) }

    fun saveTaxPctInline() {
        val pct = _state.value.editableTaxPct.toDoubleOrNull() ?: return
        val bill = _state.value.bill?.takeIf { it.id.isNotBlank() } ?: return
        val updatedItems = bill.items.map { item ->
            if (pct > 0) item.copy(taxPercentage = if (item.type == "ROOM") pct else item.taxPercentage)
            else item.copy(taxPercentage = 0.0)
        }
        val updatedBill = bill.copy(taxEnabled = pct > 0, taxPercentage = pct, items = updatedItems)
        val totals = recalculate(updatedBill)
        viewModelScope.launch {
            runCatching {
                billRepo.updateTaxDiscount(bill.id, pct > 0, pct, bill.discountType, bill.discountValue,
                    totals.subtotal, totals.taxAmount, totals.discountAmount, totals.totalAmount, totals.pendingAmount, totals.status)
                billRepo.updateItems(bill.id, updatedItems, totals.subtotal, totals.taxAmount, totals.discountAmount, totals.totalAmount, totals.pendingAmount, totals.status)
            }.onSuccess { _state.update { s -> s.copy(bill = totals) } }
        }
    }

    fun onAdvancePaidInline(v: String) = _state.update { it.copy(editableAdvancePaid = v.filter { c -> c.isDigit() || c == '.' }) }

    fun saveAdvancePaidInline() {
        val amount = _state.value.editableAdvancePaid.toDoubleOrNull() ?: return
        val bill = _state.value.bill?.takeIf { it.id.isNotBlank() } ?: return
        val updatedBill = bill.copy(advancePayment = amount)
        val totals = recalculate(updatedBill)
        viewModelScope.launch {
            runCatching { billRepo.updateAdvancePaid(bill.id, amount, totals.pendingAmount, totals.status) }
                .onSuccess { _state.update { s -> s.copy(bill = totals) } }
        }
    }

    fun saveBillGuestName() {
        val name = _state.value.billGuestName.trim()
        val billId = _state.value.bill?.id ?: return
        if (billId.isBlank()) return
        viewModelScope.launch {
            runCatching { billRepo.updateGuestName(billId, name) }
                .onSuccess { _state.update { s -> s.copy(bill = s.bill?.copy(guestName = name)) } }
        }
    }

    fun saveBillGuestPhone() {
        val phone = _state.value.billGuestPhone.trim()
        val billId = _state.value.bill?.id ?: return
        if (billId.isBlank()) return
        viewModelScope.launch {
            runCatching { billRepo.updateGuestPhone(billId, phone) }
                .onSuccess { _state.update { s -> s.copy(bill = s.bill?.copy(guestPhone = phone)) } }
        }
    }

    fun openGuestPicker() {
        val bill = _state.value.bill ?: return
        viewModelScope.launch {
            val stayIds = bill.stayIds.ifEmpty { listOfNotNull(bill.stayId.takeIf { it.isNotBlank() }) }
            val guests = stayIds.flatMap { stayId ->
                val stay = runCatching { stayRepo.getById(stayId) }.getOrNull()
                    ?: return@flatMap emptyList<GuestNameOption>()
                buildList {
                    if (stay.guestName.isNotBlank()) {
                        add(GuestNameOption(name = stay.guestName, phone = stay.guestPhone, roomNumber = stay.roomNumber))
                    }
                    stay.guests.forEach { g ->
                        if (g.name.isNotBlank() && g.name != stay.guestName) {
                            add(GuestNameOption(name = g.name, phone = g.phone, roomNumber = stay.roomNumber))
                        }
                    }
                }
            }.distinctBy { it.name }
            _state.update { it.copy(guestPickerOpen = true, billGuests = guests) }
        }
    }

    fun closeGuestPicker() = _state.update { it.copy(guestPickerOpen = false) }

    fun selectBillGuest(option: GuestNameOption) {
        _state.update { it.copy(billGuestName = option.name, guestPickerOpen = false) }
        saveBillGuestName()
    }

    fun onDiscountTypeInline(v: String) = _state.update { it.copy(editableDiscountType = v) }
    fun onDiscountValueInline(v: String) = _state.update { it.copy(editableDiscountValue = v.filter { c -> c.isDigit() || c == '.' }) }

    fun saveDiscountInline() {
        val type = _state.value.editableDiscountType
        val value = _state.value.editableDiscountValue.toDoubleOrNull() ?: 0.0
        val bill = _state.value.bill?.takeIf { it.id.isNotBlank() } ?: return
        val updatedBill = recalculate(bill.copy(discountType = type, discountValue = value))
        _state.update { it.copy(bill = updatedBill) }
        viewModelScope.launch {
            runCatching {
                billRepo.updateTaxDiscount(bill.id, bill.taxEnabled, bill.taxPercentage, type, value,
                    updatedBill.subtotal, updatedBill.taxAmount, updatedBill.discountAmount,
                    updatedBill.totalAmount, updatedBill.pendingAmount, updatedBill.status)
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
            val newTaxRate = d.taxPercentage.toDoubleOrNull() ?: 18.0
            val updatedItems = if (d.taxEnabled) {
                bill.items.map { item -> if (item.type == "ROOM") item.copy(taxPercentage = newTaxRate) else item }
            } else {
                bill.items.map { item -> item.copy(taxPercentage = 0.0) }
            }
            val updatedBill = bill.copy(
                taxEnabled = d.taxEnabled,
                taxPercentage = newTaxRate,
                discountType = d.discountType,
                discountValue = d.discountValue.toDoubleOrNull() ?: 0.0,
                items = updatedItems,
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
            // Build transactions from the current dialog payment fields (not stale Firestore data).
            // "Paid via QR" / manual entry only update local state; we commit them here.
            val pd = _state.value.addPaymentDialog
            val cashTotal = pd.cashAmount.toDoubleOrNull() ?: 0.0
            val bankTotal = pd.bankAmount.toDoubleOrNull() ?: 0.0
            val otherTxs = bill.transactions.filter { it.method != "CASH" && it.method != "BANK" }
            val cashTx = if (cashTotal > 0) PaymentTransaction(
                id = bill.transactions.firstOrNull { it.method == "CASH" }?.id ?: UUID.randomUUID().toString(),
                method = "CASH", amount = cashTotal, status = "PAID", createdAt = Date()
            ) else null
            val bankTx = if (bankTotal > 0) PaymentTransaction(
                id = bill.transactions.firstOrNull { it.method == "BANK" }?.id ?: UUID.randomUUID().toString(),
                method = "BANK", amount = bankTotal, status = "PAID", createdAt = Date()
            ) else null
            val updatedTxs = otherTxs + listOfNotNull(cashTx, bankTx)
            val newTotalPaid = updatedTxs.sumOf { it.amount }

            // Recalculate totals using correct transactions and per-item tax rates.
            val billWithTxs = bill.copy(transactions = updatedTxs, totalPaid = newTotalPaid)
            val settledBill = recalculate(billWithTxs)

            // Write everything atomically in a single Firestore transaction.
            runCatching {
                billRepo.finalizeTransaction(
                    bill.id, settledBill.items,
                    settledBill.subtotal, settledBill.taxAmount,
                    settledBill.discountAmount, settledBill.totalAmount,
                    updatedTxs, newTotalPaid,
                    settledBill.pendingAmount, settledBill.status,
                )
            }

            _state.update { it.copy(
                bill = settledBill.copy(transactions = updatedTxs, totalPaid = newTotalPaid),
                confirmPaymentDialog = it.confirmPaymentDialog.copy(isProcessing = false, done = true),
                accountingStatus = AccountingStatus.InProgress,
            ) }
            com.example.dreamland_reception.DreamlandAppInitializer.getBillingViewModel().load()

            // Resolve the correct phone for the billing guest (settledBill.guestName).
            // For group bills the loaded stay may belong to a different guest — fetch the
            // primary stay by bill.stayId to get the correct phone so the accounting system
            // and invoice Lambda create the record under the right customer name.
            val loadedStay = _state.value.stay
            val guestPhone: String = settledBill.guestPhone.ifBlank {
                if (loadedStay?.guestName == settledBill.guestName) {
                    loadedStay.guestPhone
                } else {
                    var found: String? = null
                    if (settledBill.stayId.isNotBlank()) {
                        try {
                            val s = stayRepo.getById(settledBill.stayId)
                            if (s?.guestName == settledBill.guestName) found = s.guestPhone
                        } catch (_: Exception) {}
                    }
                    if (found == null) {
                        for (id in settledBill.stayIds) {
                            try {
                                val s = stayRepo.getById(id)
                                if (s?.guestName == settledBill.guestName) { found = s.guestPhone; break }
                            } catch (_: Exception) {}
                        }
                    }
                    found ?: ""
                }
            }
            // Settle to Humble Ledger FIRST (awaited) so we have the authoritative
            // invoice number, then render the bill with it. The Firestore bill is
            // already finalized above, so only accounting + the PDF wait here — never
            // the guest. Doing it in one settle avoids racing two concurrent posts of
            // the same sourceId (which would leave the invoice number blank).
            val settleResult = syncLedger(settledBill, guestPhone)
            val billForInvoice = if (settleResult != null && settleResult.invoiceNumber.isNotBlank()) {
                settledBill.copy(
                    ledgerSynced = true,
                    ledgerInvoiceId = settleResult.invoiceId,
                    ledgerInvoiceNumber = settleResult.invoiceNumber,
                )
            } else settledBill

            // Generate the branded invoice PDF and open it in the in-app viewer.
            generateInvoicePdf(billForInvoice, guestPhone)
        }
    }

    // ── Ledger sync (durable) ──────────────────────────────────────────────────

    /**
     * Posts the double-entry settlement to Humble Ledger and persists the outcome
     * on the bill document. On success the ledger invoice number is stored (and
     * used on the printed bill). On failure the error is persisted so the bill can
     * be retried later — safe because every posting uses a deterministic sourceId,
     * so the ledger dedupes anything that already landed.
     */
    private suspend fun syncLedger(bill: Bill, guestPhone: String): AccountingRepository.SettleResult? {
        if (bill.id.isBlank()) return null
        _state.update { it.copy(accountingStatus = AccountingStatus.InProgress) }
        val guestUid = resolveGuestUid(bill.guestName, guestPhone)
        // Use the authoritative display name from the users collection for accounting,
        // so the ledger record matches the registered guest identity.
        val normalizedPhone = normalizePhoneE164(guestPhone) ?: guestPhone
        val accountingName = if (normalizedPhone.isNotBlank()) {
            runCatching { userRepo.findNameByPhone(normalizedPhone) }.getOrNull()
        } else null
        val billForAccounting = if (!accountingName.isNullOrBlank()) bill.copy(guestName = accountingName) else bill
        return accountingRepo.settle(billForAccounting, guestPhone, guestUid)
            .onSuccess { r ->
                if (r.invoiceId.isNotBlank()) {
                    runCatching { billRepo.markLedgerSynced(bill.id, r.invoiceId, r.invoiceNumber) }
                    _state.update { s ->
                        s.copy(
                            bill = s.bill?.takeIf { it.id == bill.id }?.copy(
                                ledgerSynced = true,
                                ledgerInvoiceId = r.invoiceId,
                                ledgerInvoiceNumber = r.invoiceNumber,
                                ledgerSyncError = "",
                            ) ?: s.bill,
                            accountingStatus = AccountingStatus.Synced,
                        )
                    }
                } else {
                    // Accounting not configured — treat as a no-op, not a failure.
                    _state.update { it.copy(accountingStatus = AccountingStatus.Idle) }
                }
            }
            .onFailure { e ->
                runCatching { billRepo.markLedgerSyncFailed(bill.id, e.message ?: "sync failed") }
                _state.update { it.copy(accountingStatus = AccountingStatus.Failed(e.message ?: "Accounting sync failed")) }
            }
            // A successful settle with a real invoice number; null otherwise (failure /
            // not configured) — caller falls back to its own settle-if-missing.
            .getOrNull()
            ?.takeIf { it.invoiceId.isNotBlank() }
    }

    /**
     * Resolves the correct phone for the bill's guest. For group bills the loaded
     * stay may belong to a different guest, so we fall back to the stay(s) backing
     * the bill to find the one whose name matches.
     */
    private suspend fun resolveGuestPhone(bill: Bill, loadedStay: Stay?): String {
        if (bill.guestPhone.isNotBlank()) return bill.guestPhone
        if (loadedStay != null && loadedStay.guestName == bill.guestName) return loadedStay.guestPhone
        if (bill.stayId.isNotBlank()) {
            runCatching { stayRepo.getById(bill.stayId) }.getOrNull()
                ?.takeIf { it.guestName == bill.guestName }?.let { return it.guestPhone }
        }
        for (id in bill.stayIds) {
            runCatching { stayRepo.getById(id) }.getOrNull()
                ?.takeIf { it.guestName == bill.guestName }?.let { return it.guestPhone }
        }
        return ""
    }

    /**
     * Resolves the guest's stable UID — the `users` doc id, keyed by phone — to use
     * as the Humble Ledger `externalId` (the cross-system identity). Returns the
     * existing guest's UID, creating the guest record if one doesn't exist yet, or
     * "" when there's no phone to key on (the ledger then resolves by phone/name).
     */
    private suspend fun resolveGuestUid(name: String, phone: String): String {
        if (phone.isBlank()) return ""
        val normalized = normalizePhoneE164(phone) ?: phone
        return runCatching {
            userRepo.findIdByPhone(normalized) ?: userRepo.createGuestUser(name.ifBlank { normalized }, normalized)
        }.getOrElse { "" }
    }

    /**
     * Retries the ledger sync for a finalized-but-unsynced bill. Called on load
     * (durable recovery after a failed/lost sync) and by the manual Retry button.
     * No-op for in-memory previews, already-synced bills, and not-yet-finalized
     * (PENDING) bills.
     */
    fun retryLedgerSync() {
        val bill = _state.value.bill ?: return
        if (bill.id.isBlank() || bill.ledgerSynced || bill.status == "PENDING") return
        val loadedStay = _state.value.stay
        viewModelScope.launch {
            val phone = resolveGuestPhone(bill, loadedStay)
            syncLedger(bill, phone)
        }
    }

    // ── Invoice PDF ───────────────────────────────────────────────────────────

    /**
     * Requests a branded invoice PDF from the Humble Bill Engine and renders it for the
     * in-app viewer. Runs in its own coroutine (not [launchWithGlobalLoading]) so the
     * ~3–15s server-side render does not block the UI behind the global dim overlay;
     * progress is shown inside the viewer dialog instead.
     */
    fun generateInvoicePdf(bill: Bill, guestPhone: String) {
        if (bill.id.isBlank()) return
        // Recompute taxPercentage as rounded effective blended rate so the invoice Lambda
        // shows consistent CGST/SGST rate labels (e.g. 4.86% not the stale stored value).
        val effectiveTaxPct = if (bill.taxEnabled && bill.subtotal > 0)
            Math.round(bill.taxAmount / bill.subtotal * 10000.0) / 100.0
        else bill.taxPercentage
        _state.update { it.copy(invoicePdf = InvoicePdfState(show = true, isGenerating = true)) }
        viewModelScope.launch {
            // The printed invoice number MUST be the Humble Ledger number (e.g. INV-000044),
            // never the Firestore bill id. If we don't have it yet, settle (idempotent) to
            // obtain it before rendering; if it still can't be resolved, fail rather than
            // print a wrong number.
            var invNo = bill.ledgerInvoiceNumber
            var invId = bill.ledgerInvoiceId
            if (invNo.isBlank()) {
                val uid = resolveGuestUid(bill.guestName, guestPhone)
                accountingRepo.settle(bill, guestPhone, uid).onSuccess { r ->
                    if (r.invoiceNumber.isNotBlank()) {
                        invNo = r.invoiceNumber
                        invId = r.invoiceId
                        runCatching { billRepo.markLedgerSynced(bill.id, r.invoiceId, r.invoiceNumber) }
                        _state.update { s ->
                            s.copy(bill = s.bill?.takeIf { it.id == bill.id }?.copy(
                                ledgerSynced = true, ledgerInvoiceId = r.invoiceId, ledgerInvoiceNumber = r.invoiceNumber,
                            ) ?: s.bill)
                        }
                    }
                }
            }
            if (invNo.isBlank()) {
                _state.update { it.copy(invoicePdf = it.invoicePdf.copy(
                    isGenerating = false,
                    error = "Invoice number not ready — accounting sync pending. Please retry in a moment.",
                )) }
                return@launch
            }
            val billForInvoice = bill.copy(
                taxPercentage = effectiveTaxPct,
                ledgerInvoiceNumber = invNo,
                ledgerInvoiceId = invId,
            )
            runCatching {
                val url = HumbleBillEngine.generateInvoiceUrl(billForInvoice, guestPhone)
                // Persist the PDF URL in the bills collection (best-effort, non-blocking)
                runCatching { billRepo.updateInvoiceUrl(bill.id, url) }
                url to HumbleBillEngine.renderPdfPages(url)
            }.onSuccess { (url, pages) ->
                _state.update { it.copy(
                    invoicePdf = it.invoicePdf.copy(isGenerating = false, url = url, pages = pages),
                    bill = it.bill?.copy(invoiceUrl = url),
                ) }
            }.onFailure { e ->
                _state.update { it.copy(invoicePdf = it.invoicePdf.copy(isGenerating = false, error = e.message ?: "Failed to generate invoice")) }
            }
        }
    }

    fun closeInvoicePdf() = _state.update { s -> s.copy(invoicePdf = s.invoicePdf.copy(show = false)) }

    fun openInvoicePdf() {
        val bill = _state.value.bill ?: return
        val loadedStay = _state.value.stay
        viewModelScope.launch {
            val guestPhone: String = bill.guestPhone.ifBlank {
                if (loadedStay?.guestName == bill.guestName) {
                    loadedStay.guestPhone
                } else {
                    var found: String? = null
                    if (bill.stayId.isNotBlank()) {
                        try {
                            val s = stayRepo.getById(bill.stayId)
                            if (s?.guestName == bill.guestName) found = s.guestPhone
                        } catch (_: Exception) {}
                    }
                    if (found == null) {
                        for (id in bill.stayIds) {
                            try {
                                val s = stayRepo.getById(id)
                                if (s?.guestName == bill.guestName) { found = s.guestPhone; break }
                            } catch (_: Exception) {}
                        }
                    }
                    found ?: ""
                }
            }
            generateInvoicePdf(bill, guestPhone)
        }
    }

    // ── Email invoice ─────────────────────────────────────────────────────────

    fun openEmailDialog() {
        // Pre-fill with empty; fetch from users collection if userId available
        val userId = _state.value.stay?.userId ?: ""
        _state.update { s -> s.copy(invoicePdf = s.invoicePdf.copy(emailDialogOpen = true, guestEmail = "", sendError = null, sendSuccess = false)) }
        if (userId.isBlank()) return
        viewModelScope.launch(Dispatchers.IO) {
            val email = runCatching { userRepo.getByIds(setOf(userId)).firstOrNull()?.email ?: "" }.getOrElse { "" }
            _state.update { s -> s.copy(invoicePdf = s.invoicePdf.copy(guestEmail = email)) }
        }
    }

    fun onGuestEmailChanged(email: String) = _state.update { s ->
        s.copy(invoicePdf = s.invoicePdf.copy(guestEmail = email, sendError = null, sendSuccess = false))
    }

    fun closeEmailDialog() = _state.update { s ->
        s.copy(invoicePdf = s.invoicePdf.copy(emailDialogOpen = false, sendError = null, sendSuccess = false))
    }

    fun openQrDialog() {
        val url = _state.value.invoicePdf.url
        if (url.isBlank()) return
        viewModelScope.launch(Dispatchers.IO) {
            val qrImage = runCatching {
                val writer = com.google.zxing.qrcode.QRCodeWriter()
                val matrix = writer.encode(url, com.google.zxing.BarcodeFormat.QR_CODE, 400, 400)
                com.google.zxing.client.j2se.MatrixToImageWriter.toBufferedImage(matrix)
            }.getOrNull()
            _state.update { s -> s.copy(invoicePdf = s.invoicePdf.copy(showQrDialog = true, qrImage = qrImage)) }
        }
    }

    fun closeQrDialog() = _state.update { s -> s.copy(invoicePdf = s.invoicePdf.copy(showQrDialog = false)) }

    fun sendInvoiceEmail() {
        val ipd = _state.value.invoicePdf
        val email = ipd.guestEmail.trim()
        if (email.isBlank() || ipd.url.isBlank()) return
        _state.update { s -> s.copy(invoicePdf = s.invoicePdf.copy(isSending = true, sendError = null, sendSuccess = false)) }
        viewModelScope.launch(Dispatchers.IO) {
            runCatching {
                // Download PDF bytes
                val pdfBytes = java.net.URL(ipd.url).readBytes()
                val base64Pdf = java.util.Base64.getEncoder().encodeToString(pdfBytes)
                val bill = _state.value.bill
                val guestName = bill?.guestName ?: "Guest"
                val hotelName = "Hotel Dreamland"
                val resendApiKey = "re_cqBLhngi_CofomAF7AEc3k8fxPD9gR9Aq" // ← paste your Resend API key here
                val senderEmail = com.example.dreamland_reception.data.LocalConfig.senderEmail
                    .ifBlank { "noreply@bookmydreamland.com" }
                val body = """
                    {
                      "from": "$senderEmail",
                      "to": ["$email"],
                      "subject": "Your Tax Invoice - $hotelName",
                      "html": "<p>Dear $guestName,</p><p>Please find attached your tax invoice.</p><p>Thank you for staying with us!</p><p>Regards,<br/>$hotelName</p>",
                      "attachments": [{"filename": "invoice.pdf", "content": "$base64Pdf"}]
                    }
                """.trimIndent()
                val connection = java.net.URL("https://api.resend.com/emails").openConnection() as java.net.HttpURLConnection
                connection.requestMethod = "POST"
                connection.setRequestProperty("Authorization", "Bearer $resendApiKey")
                connection.setRequestProperty("Content-Type", "application/json")
                connection.doOutput = true
                connection.outputStream.use { it.write(body.toByteArray()) }
                val responseCode = connection.responseCode
                if (responseCode !in 200..299) {
                    val err = connection.errorStream?.bufferedReader()?.readText() ?: "HTTP $responseCode"
                    throw Exception(err)
                }
            }.onSuccess {
                _state.update { s -> s.copy(invoicePdf = s.invoicePdf.copy(isSending = false, sendSuccess = true, emailDialogOpen = false)) }
            }.onFailure { e ->
                _state.update { s -> s.copy(invoicePdf = s.invoicePdf.copy(isSending = false, sendError = e.message ?: "Failed to send email")) }
            }
        }
    }

    fun loadPrinters() {
        viewModelScope.launch(Dispatchers.IO) {
            val names = javax.print.PrintServiceLookup.lookupPrintServices(null, null).map { it.name }
            _state.update { s -> s.copy(invoicePdf = s.invoicePdf.copy(
                availablePrinters = names,
                selectedPrinter = s.invoicePdf.selectedPrinter.ifBlank { names.firstOrNull() ?: "" },
            )) }
        }
    }

    fun selectPrinter(name: String) = _state.update { s ->
        s.copy(invoicePdf = s.invoicePdf.copy(selectedPrinter = name))
    }

    fun printInvoice() {
        val ipd = _state.value.invoicePdf
        if (ipd.url.isBlank() || ipd.selectedPrinter.isBlank()) return
        viewModelScope.launch(Dispatchers.IO) {
            _state.update { s -> s.copy(invoicePdf = s.invoicePdf.copy(isPrinting = true, printError = null)) }
            runCatching {
                val bytes = java.net.URL(ipd.url).readBytes()
                val printer = javax.print.PrintServiceLookup.lookupPrintServices(null, null)
                    .firstOrNull { it.name == ipd.selectedPrinter }
                    ?: throw Exception("Printer '${ipd.selectedPrinter}' not found")
                val doc = org.apache.pdfbox.pdmodel.PDDocument.load(bytes)
                val job = java.awt.print.PrinterJob.getPrinterJob()
                job.setPrintService(printer)
                job.setPageable(org.apache.pdfbox.printing.PDFPageable(doc))
                job.print()
                doc.close()
            }.onSuccess {
                _state.update { s -> s.copy(invoicePdf = s.invoicePdf.copy(isPrinting = false)) }
            }.onFailure { e ->
                _state.update { s -> s.copy(invoicePdf = s.invoicePdf.copy(isPrinting = false, printError = e.message)) }
            }
        }
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private fun recalculate(bill: Bill): Bill {
        val subtotal = bill.items.sumOf { it.total }
        // Use per-item tax rates when items carry them; fall back to bill-level rate if all items have 0
        val taxAmount = if (bill.taxEnabled) {
            val perItemTax = bill.items.sumOf { it.total * it.taxPercentage / 100.0 }
            if (perItemTax > 0.0) perItemTax else subtotal * bill.taxPercentage / 100.0
        } else 0.0
        val discountAmount = when (bill.discountType) {
            "PERCENT" -> subtotal * bill.discountValue / 100.0
            else -> bill.discountValue
        }
        val total = (subtotal + taxAmount - discountAmount).coerceAtLeast(0.0)
        val pending = (total - bill.totalPaid - bill.advancePayment).coerceAtLeast(0.0)
        val status = when {
            pending <= 0 && total > 0 -> "PAID"
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
