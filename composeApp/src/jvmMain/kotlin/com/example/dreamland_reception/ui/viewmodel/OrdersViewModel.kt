package com.example.dreamland_reception.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.dreamland_reception.data.AppContext
import com.example.dreamland_reception.data.model.Order
import com.example.dreamland_reception.data.model.OrderItem
import com.example.dreamland_reception.data.model.StaffMember
import com.example.dreamland_reception.data.model.Stay
import com.example.dreamland_reception.data.repository.FirestoreFoodItemRepository
import com.example.dreamland_reception.data.repository.FirestoreOrderRepository
import com.example.dreamland_reception.data.repository.FirestoreServiceRepository
import com.example.dreamland_reception.data.repository.FirestoreStaffRepository
import com.example.dreamland_reception.data.repository.FirestoreStayRepository
import com.example.dreamland_reception.data.repository.FoodItemRepository
import com.example.dreamland_reception.data.repository.OrderRepository
import com.example.dreamland_reception.data.repository.ServiceRepository
import com.example.dreamland_reception.data.repository.StaffRepository
import com.example.dreamland_reception.data.repository.StayRepository
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.awt.Toolkit
import java.util.Date

// ── Backward-compat sealed state (used by DashboardViewModel, refreshAllViewModels) ─────────

sealed interface OrdersUiState {
    data object Loading : OrdersUiState
    data class Success(val orders: List<Order>) : OrdersUiState
    data class Error(val message: String) : OrdersUiState
}

// ── Screen state ──────────────────────────────────────────────────────────────────────────────

data class OrdersScreenState(
    val orders: List<Order> = emptyList(),
    val isLoading: Boolean = false,
    val error: String? = null,
    val selectedTab: Int = 0,       // 0=NEW, 1=ASSIGNED, 2=COMPLETED
    val searchQuery: String = "",
    val roomFilter: String = "",
    val staffFilter: String = "",
    val selectedOrderId: String? = null,
) {
    val newOrders: List<Order>       get() = orders.filter { it.status == "NEW" }
    val assignedOrders: List<Order>  get() = orders.filter { it.status == "ASSIGNED" }
    val completedOrders: List<Order> get() = orders.filter { it.status == "COMPLETED" }

    val activeTabOrders: List<Order> get() = when (selectedTab) {
        0 -> newOrders; 1 -> assignedOrders; else -> completedOrders
    }

    val filtered: List<Order> get() {
        val q = searchQuery.trim().lowercase()
        return activeTabOrders.filter { o ->
            (q.isEmpty() || o.guestName.lowercase().contains(q) ||
                o.roomNumber.lowercase().contains(q) ||
                o.items.any { it.name.lowercase().contains(q) }) &&
            (roomFilter.isBlank() || o.roomNumber == roomFilter) &&
            (staffFilter.isBlank() || o.assignedTo == staffFilter)
        }
    }

    val uniqueRooms: List<String>
        get() = orders.map { it.roomNumber }.distinct().sorted()

    val uniqueStaff: List<Pair<String, String>>
        get() = orders.filter { it.assignedTo.isNotBlank() }
                      .map { it.assignedTo to it.assignedToName }
                      .distinctBy { it.first }
                      .sortedBy { it.second }
}

// ── Create Order dialog state ─────────────────────────────────────────────────────────────────

data class CreateOrderDialogState(
    val isOpen: Boolean = false,
    val selectedStayId: String = "",
    val selectedStayDisplay: String = "",
    val stayQuery: String = "",
    val activeStays: List<Stay> = emptyList(),
    val catalogItems: List<CatalogItem> = emptyList(),
    val items: List<OrderItemEntry> = listOf(OrderItemEntry()),
    val notes: String = "",
    val isSaving: Boolean = false,
    val error: String? = null,
    val isLoadingStays: Boolean = false,
    val isLoadingCatalog: Boolean = false,
) {
    val categories: List<String>
        get() = catalogItems.flatMap { it.category.split(",").map(String::trim) }
            .filter { it.isNotBlank() }.distinct()

    val filteredStays: List<Stay>
        get() = if (stayQuery.isBlank()) activeStays
        else activeStays.filter {
            it.roomNumber.contains(stayQuery, ignoreCase = true) ||
                it.guestName.contains(stayQuery, ignoreCase = true)
        }
}

// ── Assign Staff dialog state ─────────────────────────────────────────────────────────────────

data class AssignStaffDialogState(
    val isOpen: Boolean = false,
    val orderId: String = "",
    val activeStaff: List<StaffMember> = emptyList(),
    val isLoading: Boolean = false,
    val error: String? = null,
)

// ── ViewModel ─────────────────────────────────────────────────────────────────────────────────

class OrdersViewModel(
    private val orderRepo: OrderRepository = FirestoreOrderRepository,
    private val staffRepo: StaffRepository = FirestoreStaffRepository,
    private val stayRepo: StayRepository = FirestoreStayRepository,
    private val foodItemRepo: FoodItemRepository = FirestoreFoodItemRepository,
    private val serviceRepo: ServiceRepository = FirestoreServiceRepository,
) : ViewModel() {

    private val _screenState = MutableStateFlow(OrdersScreenState(isLoading = true))
    val screenState: StateFlow<OrdersScreenState> = _screenState.asStateFlow()

    private val _createOrderDialog = MutableStateFlow(CreateOrderDialogState())
    val createOrderDialog: StateFlow<CreateOrderDialogState> = _createOrderDialog.asStateFlow()

    private val _assignStaffDialog = MutableStateFlow(AssignStaffDialogState())
    val assignStaffDialog: StateFlow<AssignStaffDialogState> = _assignStaffDialog.asStateFlow()

    // Backward-compat sealed state
    private val _uiState = MutableStateFlow<OrdersUiState>(OrdersUiState.Loading)
    val uiState: StateFlow<OrdersUiState> = _uiState.asStateFlow()

    private var listenerJob: Job? = null
    private var previousOrderIds: Set<String> = emptySet()

    init { startListener() }

    // ── Real-time listener ────────────────────────────────────────────────────

    private fun startListener() {
        val hotelId = AppContext.hotelId
        if (hotelId.isBlank()) {
            _screenState.update { it.copy(isLoading = false) }
            return
        }
        listenerJob?.cancel()
        listenerJob = viewModelScope.launch {
            runCatching {
                orderRepo.listenByHotel(hotelId).collect { orders ->
                    val currentIds = orders.map { it.id }.toSet()
                    val firstEmit = previousOrderIds.isEmpty() && _screenState.value.orders.isEmpty()
                    val newArrivals = currentIds - previousOrderIds
                    if (!firstEmit && newArrivals.isNotEmpty()) {
                        runCatching { Toolkit.getDefaultToolkit().beep() }
                    }
                    previousOrderIds = currentIds
                    _screenState.update { it.copy(orders = orders, isLoading = false, error = null) }
                    _uiState.value = OrdersUiState.Success(orders.filter { it.status != "COMPLETED" })
                }
            }.onFailure { e ->
                _screenState.update { it.copy(isLoading = false, error = e.message ?: "Failed to load orders") }
                _uiState.value = OrdersUiState.Error(e.message ?: "Failed to load orders")
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
        listenerJob?.cancel()
    }

    // Backward-compat shim — called by refreshAllViewModels()
    fun loadPending() { startListener() }

    // ── Tab / filter ──────────────────────────────────────────────────────────

    fun onTabSelected(i: Int) = _screenState.update { it.copy(selectedTab = i, selectedOrderId = null) }
    fun onSearch(q: String) = _screenState.update { it.copy(searchQuery = q) }
    fun onRoomFilter(r: String) = _screenState.update { it.copy(roomFilter = r) }
    fun onStaffFilter(s: String) = _screenState.update { it.copy(staffFilter = s) }
    fun selectOrder(id: String?) = _screenState.update { it.copy(selectedOrderId = id) }

    // ── Status update ─────────────────────────────────────────────────────────

    fun updateStatus(orderId: String, status: String) {
        launchWithGlobalLoading {
            val order = _screenState.value.orders.find { it.id == orderId }
            runCatching { orderRepo.updateStatus(orderId, status) }
                .onSuccess {
                    // Free up the staff member when an order is completed
                    if (status == "COMPLETED" && order?.assignedTo?.isNotBlank() == true) {
                        runCatching { staffRepo.setAvailability(order.assignedTo, true) }
                    }
                }
                .onFailure { e -> _screenState.update { it.copy(error = e.message) } }
        }
    }

    // ── Create Order dialog ───────────────────────────────────────────────────

    fun openCreateOrder() {
        val hotelId = AppContext.hotelId
        _createOrderDialog.value = CreateOrderDialogState(
            isOpen = true, isLoadingStays = true, isLoadingCatalog = true,
        )
        launchWithGlobalLoading {
            val stays = runCatching { stayRepo.getActive(hotelId) }.getOrElse { emptyList() }
            val foodItems = runCatching { foodItemRepo.getByHotel(hotelId) }.getOrElse { emptyList() }
            val services = runCatching { serviceRepo.getByHotel(hotelId) }.getOrElse { emptyList() }
            val catalog = foodItems.filter { it.isAvailable }
                .map { CatalogItem(it.name, it.price, it.category.ifBlank { "Food" }) } +
                services.filter { it.isActive }
                .map { CatalogItem(it.name, it.price, "Services") }
            val firstCategory = catalog.map { it.category }.distinct().firstOrNull() ?: ""
            _createOrderDialog.update { it.copy(
                activeStays = stays,
                catalogItems = catalog,
                items = listOf(OrderItemEntry(category = firstCategory)),
                isLoadingStays = false,
                isLoadingCatalog = false,
            ) }
        }
    }

    fun closeCreateOrder() { _createOrderDialog.value = CreateOrderDialogState() }

    fun refreshCreateOrderCatalog() {
        val hotelId = AppContext.hotelId
        if (hotelId.isBlank() || !_createOrderDialog.value.isOpen) return
        launchWithGlobalLoading {
            val foodItems = runCatching { foodItemRepo.getByHotel(hotelId) }.getOrElse { return@launchWithGlobalLoading }
            val services = runCatching { serviceRepo.getByHotel(hotelId) }.getOrElse { return@launchWithGlobalLoading }
            val catalog = foodItems.filter { it.isAvailable }
                .map { CatalogItem(it.name, it.price, it.category.ifBlank { "Food" }) } +
                services.filter { it.isActive }
                .map { CatalogItem(it.name, it.price, "Services") }
            // Re-filter suggestions for all current items so "Add new" disappears immediately
            // for any item whose name now exists in the refreshed catalog
            _createOrderDialog.update { s ->
                val updatedItems = s.items.map { entry ->
                    if (entry.name.isBlank()) entry
                    else {
                        val q = entry.name.trim().lowercase()
                        val newSuggestions = catalog.filter { item ->
                            item.name.lowercase().contains(q) &&
                                (entry.category.isBlank() || item.category.split(",").map(String::trim).any { it == entry.category })
                        }.take(6)
                        entry.copy(suggestions = newSuggestions, showSuggestions = newSuggestions.isNotEmpty())
                    }
                }
                s.copy(catalogItems = catalog, items = updatedItems)
            }
        }
    }

    fun onCreateOrderStayQuery(q: String) = _createOrderDialog.update { s ->
        s.copy(
            stayQuery = q,
            // Clear selection when user edits the field after a stay was already picked
            selectedStayId = if (q != s.selectedStayDisplay) "" else s.selectedStayId,
            selectedStayDisplay = if (q != s.selectedStayDisplay) "" else s.selectedStayDisplay,
        )
    }

    fun onCreateOrderStaySelected(stayId: String) {
        val stay = _createOrderDialog.value.activeStays.find { it.id == stayId } ?: return
        val display = "Room ${stay.roomNumber} — ${stay.guestName}"
        _createOrderDialog.update { it.copy(
            selectedStayId = stayId,
            selectedStayDisplay = display,
            stayQuery = display,
        ) }
    }

    fun onCreateOrderItemCategory(index: Int, category: String) = _createOrderDialog.update { s ->
        s.copy(items = s.items.toMutableList().also {
            it[index] = it[index].copy(category = category, suggestions = emptyList(), showSuggestions = false)
        })
    }

    fun onCreateOrderNotes(v: String) = _createOrderDialog.update { it.copy(notes = v) }

    fun onCreateOrderItemName(index: Int, v: String) = _createOrderDialog.update { s ->
        val itemCategory = s.items.getOrNull(index)?.category ?: ""
        val suggestions = if (v.isBlank()) emptyList() else {
            val q = v.trim().lowercase()
            s.catalogItems.filter { item ->
                item.name.lowercase().contains(q) &&
                    (itemCategory.isBlank() || item.category.split(",").map(String::trim).any { it == itemCategory })
            }.take(6)
        }
        // Auto-fill price when typed name exactly matches a catalog item and price is still blank
        val exactMatch = s.catalogItems.find { it.name.equals(v.trim(), ignoreCase = true) }
        val currentEntry = s.items[index]
        val autoPrice = if (exactMatch != null && currentEntry.price.isBlank() && exactMatch.price > 0)
            exactMatch.price.toLong().toString() else currentEntry.price
        s.copy(items = s.items.toMutableList().also {
            it[index] = it[index].copy(
                name = v,
                price = autoPrice,
                suggestions = suggestions,
                showSuggestions = suggestions.isNotEmpty(),
            )
        })
    }

    fun selectCreateOrderItemSuggestion(index: Int, suggestion: CatalogItem) = _createOrderDialog.update { s ->
        s.copy(items = s.items.toMutableList().also {
            it[index] = it[index].copy(
                name = suggestion.name,
                price = if (suggestion.price > 0) suggestion.price.toLong().toString() else "",
                suggestions = emptyList(),
                showSuggestions = false,
            )
        })
    }

    fun dismissCreateOrderItemSuggestions(index: Int) = _createOrderDialog.update { s ->
        s.copy(items = s.items.toMutableList().also {
            it[index] = it[index].copy(showSuggestions = false)
        })
    }

    fun onCreateOrderItemQty(index: Int, qty: Int) = _createOrderDialog.update { s ->
        s.copy(items = s.items.toMutableList().also {
            it[index] = it[index].copy(quantity = qty.coerceAtLeast(1))
        })
    }

    fun onCreateOrderItemPrice(index: Int, v: String) = _createOrderDialog.update { s ->
        s.copy(items = s.items.toMutableList().also {
            it[index] = it[index].copy(price = v.filter { c -> c.isDigit() || c == '.' })
        })
    }

    fun addCreateOrderItem() = _createOrderDialog.update { s ->
        val firstCategory = s.categories.firstOrNull() ?: ""
        s.copy(items = s.items + OrderItemEntry(category = firstCategory))
    }

    fun removeCreateOrderItem(index: Int) = _createOrderDialog.update { s ->
        if (s.items.size <= 1) return@update s
        s.copy(items = s.items.toMutableList().also { it.removeAt(index) })
    }

    fun submitCreateOrder() {
        val s = _createOrderDialog.value
        if (s.selectedStayId.isBlank()) {
            _createOrderDialog.update { it.copy(error = "Please select a stay") }
            return
        }
        val validItems = s.items.filter { it.name.isNotBlank() }
        if (validItems.isEmpty()) {
            _createOrderDialog.update { it.copy(error = "Add at least one item") }
            return
        }
        val stay = s.activeStays.find { it.id == s.selectedStayId } ?: return
        _createOrderDialog.update { it.copy(isSaving = true, error = null) }
        launchWithGlobalLoading {
            val orderItems = validItems.map {
                OrderItem(it.name.trim(), it.quantity, it.price.toDoubleOrNull() ?: 0.0)
            }
            val total = orderItems.sumOf { it.price * it.quantity }
            val orderType = if (validItems.any { it.category == "Services" }) "SERVICE" else "ROOM_SERVICE"
            runCatching {
                orderRepo.add(Order(
                    hotelId = AppContext.hotelId,
                    stayId = stay.id,
                    roomNumber = stay.roomNumber,
                    guestName = stay.guestName,
                    items = orderItems,
                    type = orderType,
                    totalAmount = total,
                    notes = s.notes.trim(),
                    orderedAt = Date(),
                ))
            }.onSuccess {
                _createOrderDialog.value = CreateOrderDialogState()
                // Listener auto-refreshes
            }.onFailure { e ->
                _createOrderDialog.update { it.copy(isSaving = false, error = e.message ?: "Failed to place order") }
            }
        }
    }

    // ── Assign Staff dialog ───────────────────────────────────────────────────

    fun openAssignStaff(orderId: String) {
        _assignStaffDialog.value = AssignStaffDialogState(isOpen = true, orderId = orderId, isLoading = true)
        launchWithGlobalLoading {
            // Only show active + available staff for assignment
            val staff = runCatching { staffRepo.getActive() }.getOrElse { emptyList() }
                .filter { it.isAvailable }
            _assignStaffDialog.update { it.copy(activeStaff = staff, isLoading = false) }
        }
    }

    fun closeAssignStaff() { _assignStaffDialog.value = AssignStaffDialogState() }

    fun assignStaff(staffId: String, staffName: String) {
        val orderId = _assignStaffDialog.value.orderId
        launchWithGlobalLoading {
            runCatching { orderRepo.updateAssignment(orderId, staffId, staffName) }
                .onSuccess {
                    // Mark the staff member as busy
                    runCatching { staffRepo.setAvailability(staffId, false) }
                    _assignStaffDialog.value = AssignStaffDialogState()
                }
                .onFailure { e -> _assignStaffDialog.update { it.copy(error = e.message) } }
        }
    }
}
