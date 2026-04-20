package com.example.dreamland_reception.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.dreamland_reception.DreamlandAppInitializer
import com.example.dreamland_reception.data.AppContext
import com.example.dreamland_reception.data.firebase.FirebaseManager
import com.example.dreamland_reception.data.model.ComplaintType
import com.example.dreamland_reception.data.model.FoodItem
import com.example.dreamland_reception.data.model.Hotel
import com.example.dreamland_reception.data.model.Service
import com.example.dreamland_reception.data.repository.ComplaintTypeRepository
import com.example.dreamland_reception.data.repository.FirestoreComplaintTypeRepository
import com.example.dreamland_reception.data.repository.FirestoreFoodItemRepository
import com.example.dreamland_reception.data.repository.FirestoreHotelRepository
import com.example.dreamland_reception.data.repository.FirestoreServiceRepository
import com.example.dreamland_reception.data.repository.FoodItemRepository
import com.example.dreamland_reception.data.repository.HotelRepository
import com.example.dreamland_reception.data.repository.ServiceRepository
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

// ── Add-item dialog states ────────────────────────────────────────────────────

data class AddServiceDialog(
    val show: Boolean = false,
    val name: String = "",
    val price: String = "",
    val isSaving: Boolean = false,
)

data class AddFoodDialog(
    val show: Boolean = false,
    val name: String = "",
    val price: String = "",
    val categories: Set<String> = emptySet(),
    val isSaving: Boolean = false,
)

data class AddComplaintTypeDialog(
    val show: Boolean = false,
    val name: String = "",
    val type: String = "MAINTENANCE",
    val description: String = "",
    val isSaving: Boolean = false,
)

// ── Main UI state ─────────────────────────────────────────────────────────────

data class SettingsUiState(
    // Firebase status
    val firebaseConnected: Boolean = FirebaseManager.isConnected,
    val firebaseError: String? = FirebaseManager.initError,
    val projectId: String = "dreamland-baba0",

    // Hotel selector
    val hotels: List<Hotel> = emptyList(),
    val selectedHotelId: String = AppContext.hotelId,
    val selectedHotelName: String = AppContext.hotelName,

    // All hotel config — from hotels collection; null until a hotel is selected & loaded
    val selectedHotel: Hotel? = null,

    // Lists (scoped to selected hotel)
    val services: List<Service> = emptyList(),
    val foodItems: List<FoodItem> = emptyList(),
    val complaintTypes: List<ComplaintType> = emptyList(),

    // Async states
    val isLoadingHotels: Boolean = false,
    val isLoadingData: Boolean = false,
    val error: String? = null,

    // Dialog states
    val addServiceDialog: AddServiceDialog = AddServiceDialog(),
    val addFoodDialog: AddFoodDialog = AddFoodDialog(),
    val addComplaintTypeDialog: AddComplaintTypeDialog = AddComplaintTypeDialog(),
)

// ── ViewModel ─────────────────────────────────────────────────────────────────

class SettingsViewModel(
    private val hotelRepo: HotelRepository = FirestoreHotelRepository,
    private val serviceRepo: ServiceRepository = FirestoreServiceRepository,
    private val foodRepo: FoodItemRepository = FirestoreFoodItemRepository,
    private val complaintTypeRepo: ComplaintTypeRepository = FirestoreComplaintTypeRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(SettingsUiState())
    val state: StateFlow<SettingsUiState> = _state.asStateFlow()

    init {
        loadHotels()
        if (AppContext.hotelId.isNotBlank()) loadHotelData(AppContext.hotelId)
    }

    // ── Hotel loading ─────────────────────────────────────────────────────────

    fun loadHotels() {
        viewModelScope.launch {
            _state.update { it.copy(isLoadingHotels = true, error = null) }
            runCatching { hotelRepo.getAll() }
                .onSuccess { hotels -> _state.update { it.copy(hotels = hotels, isLoadingHotels = false) } }
                .onFailure { e -> _state.update { it.copy(isLoadingHotels = false, error = e.message) } }
        }
    }

    fun selectHotel(hotelId: String) {
        val hotel = _state.value.hotels.find { it.id == hotelId } ?: return
        AppContext.setHotel(hotelId, hotel.name)
        _state.update { it.copy(selectedHotelId = hotelId, selectedHotelName = hotel.name, selectedHotel = hotel) }
        loadHotelData(hotelId)
        DreamlandAppInitializer.refreshAllViewModels()
    }

    private fun loadHotelData(hotelId: String) {
        viewModelScope.launch {
            _state.update { it.copy(isLoadingData = true, error = null) }
            val hotel = runCatching { hotelRepo.getById(hotelId) }.getOrNull()
                ?: _state.value.hotels.find { it.id == hotelId }
            val services = runCatching { serviceRepo.getByHotel(hotelId) }.getOrElse { emptyList() }
            val food = runCatching { foodRepo.getByHotel(hotelId) }.getOrElse { emptyList() }
            val complaints = runCatching { complaintTypeRepo.getByHotel(hotelId) }.getOrElse { emptyList() }
            _state.update {
                it.copy(
                    selectedHotel = hotel,
                    services = services,
                    foodItems = food,
                    complaintTypes = complaints,
                    isLoadingData = false,
                )
            }
        }
    }

    fun refresh() {
        loadHotels()
        val hotelId = _state.value.selectedHotelId
        if (hotelId.isNotBlank()) loadHotelData(hotelId)
        _state.update { it.copy(firebaseConnected = FirebaseManager.isConnected, firebaseError = FirebaseManager.initError) }
    }

    // ── Services ──────────────────────────────────────────────────────────────

    fun openAddService(name: String = "") = _state.update { it.copy(addServiceDialog = AddServiceDialog(show = true, name = name)) }
    fun closeAddService() = _state.update { it.copy(addServiceDialog = AddServiceDialog()) }
    fun onAddServiceName(v: String) = _state.update { it.copy(addServiceDialog = it.addServiceDialog.copy(name = v)) }
    fun onAddServicePrice(v: String) = _state.update { it.copy(addServiceDialog = it.addServiceDialog.copy(price = v.filter { c -> c.isDigit() || c == '.' })) }

    fun submitAddService() {
        val d = _state.value.addServiceDialog
        val hotelId = _state.value.selectedHotelId
        if (d.name.isBlank() || hotelId.isBlank()) return
        _state.update { it.copy(addServiceDialog = d.copy(isSaving = true)) }
        launchWithGlobalLoading {
            runCatching {
                serviceRepo.add(Service(hotelId = hotelId, name = d.name.trim(), price = d.price.toDoubleOrNull() ?: 0.0))
            }.onSuccess {
                val updated = runCatching { serviceRepo.getByHotel(hotelId) }.getOrElse { _state.value.services }
                _state.update { it.copy(services = updated, addServiceDialog = AddServiceDialog()) }
            }.onFailure { _ ->
                _state.update { it.copy(addServiceDialog = d.copy(isSaving = false)) }
            }
        }
    }

    fun toggleService(id: String, isActive: Boolean) {
        launchWithGlobalLoading {
            runCatching { serviceRepo.toggleActive(id, isActive) }
            _state.update { s -> s.copy(services = s.services.map { if (it.id == id) it.copy(isActive = isActive) else it }) }
        }
    }

    fun deleteService(id: String) {
        launchWithGlobalLoading {
            runCatching { serviceRepo.delete(id) }
            _state.update { it.copy(services = it.services.filter { s -> s.id != id }) }
        }
    }

    fun updateServicePrice(id: String, price: Double) {
        _state.update { s -> s.copy(services = s.services.map { if (it.id == id) it.copy(price = price) else it }) }
        viewModelScope.launch {
            delay(800)
            val service = _state.value.services.find { it.id == id } ?: return@launch
            runCatching { serviceRepo.update(service) }
        }
    }

    // ── Food Items ────────────────────────────────────────────────────────────

    fun openAddFood(name: String = "") = _state.update { it.copy(addFoodDialog = AddFoodDialog(show = true, name = name)) }
    fun closeAddFood() = _state.update { it.copy(addFoodDialog = AddFoodDialog()) }
    fun onAddFoodName(v: String) = _state.update { it.copy(addFoodDialog = it.addFoodDialog.copy(name = v)) }
    fun onAddFoodPrice(v: String) = _state.update { it.copy(addFoodDialog = it.addFoodDialog.copy(price = v.filter { c -> c.isDigit() || c == '.' })) }
    fun onAddFoodCategory(v: String) = _state.update { s ->
        val current = s.addFoodDialog.categories
        s.copy(addFoodDialog = s.addFoodDialog.copy(
            categories = if (v in current) current - v else current + v,
        ))
    }

    fun submitAddFood() {
        val d = _state.value.addFoodDialog
        val hotelId = _state.value.selectedHotelId
        if (d.name.isBlank() || hotelId.isBlank()) return
        _state.update { it.copy(addFoodDialog = d.copy(isSaving = true)) }
        launchWithGlobalLoading {
            val categoryStr = d.categories.joinToString(",")
            runCatching {
                foodRepo.add(FoodItem(hotelId = hotelId, name = d.name.trim(), price = d.price.toDoubleOrNull() ?: 0.0, category = categoryStr))
            }.onSuccess {
                val updated = runCatching { foodRepo.getByHotel(hotelId) }.getOrElse { _state.value.foodItems }
                _state.update { it.copy(foodItems = updated, addFoodDialog = AddFoodDialog()) }
            }.onFailure { _ ->
                _state.update { it.copy(addFoodDialog = d.copy(isSaving = false)) }
            }
        }
    }

    fun toggleFoodItem(id: String, isAvailable: Boolean) {
        launchWithGlobalLoading {
            runCatching { foodRepo.toggleAvailable(id, isAvailable) }
            _state.update { s -> s.copy(foodItems = s.foodItems.map { if (it.id == id) it.copy(isAvailable = isAvailable) else it }) }
        }
    }

    fun deleteFoodItem(id: String) {
        launchWithGlobalLoading {
            runCatching { foodRepo.delete(id) }
            _state.update { it.copy(foodItems = it.foodItems.filter { f -> f.id != id }) }
        }
    }

    fun updateFoodPrice(id: String, price: Double) {
        _state.update { s -> s.copy(foodItems = s.foodItems.map { if (it.id == id) it.copy(price = price) else it }) }
        viewModelScope.launch {
            delay(800)
            val item = _state.value.foodItems.find { it.id == id } ?: return@launch
            runCatching { foodRepo.update(item) }
        }
    }

    // ── Complaint Types ───────────────────────────────────────────────────────

    fun openAddComplaintType() = _state.update { it.copy(addComplaintTypeDialog = AddComplaintTypeDialog(show = true)) }
    fun closeAddComplaintType() = _state.update { it.copy(addComplaintTypeDialog = AddComplaintTypeDialog()) }
    fun onAddComplaintTypeName(v: String) = _state.update { it.copy(addComplaintTypeDialog = it.addComplaintTypeDialog.copy(name = v)) }
    fun onAddComplaintTypeType(v: String) = _state.update { it.copy(addComplaintTypeDialog = it.addComplaintTypeDialog.copy(type = v)) }
    fun onAddComplaintTypeDesc(v: String) = _state.update { it.copy(addComplaintTypeDialog = it.addComplaintTypeDialog.copy(description = v)) }

    fun submitAddComplaintType() {
        val d = _state.value.addComplaintTypeDialog
        val hotelId = _state.value.selectedHotelId
        if (d.name.isBlank() || hotelId.isBlank()) return
        _state.update { it.copy(addComplaintTypeDialog = d.copy(isSaving = true)) }
        launchWithGlobalLoading {
            runCatching {
                complaintTypeRepo.add(ComplaintType(hotelId = hotelId, name = d.name.trim(), type = d.type, description = d.description.trim()))
            }.onSuccess {
                val updated = runCatching { complaintTypeRepo.getByHotel(hotelId) }.getOrElse { _state.value.complaintTypes }
                _state.update { it.copy(complaintTypes = updated, addComplaintTypeDialog = AddComplaintTypeDialog()) }
            }.onFailure { _ ->
                _state.update { it.copy(addComplaintTypeDialog = d.copy(isSaving = false)) }
            }
        }
    }

    fun toggleComplaintType(id: String, isActive: Boolean) {
        launchWithGlobalLoading {
            runCatching { complaintTypeRepo.toggleActive(id, isActive) }
            _state.update { s -> s.copy(complaintTypes = s.complaintTypes.map { if (it.id == id) it.copy(isActive = isActive) else it }) }
        }
    }

    fun deleteComplaintType(id: String) {
        launchWithGlobalLoading {
            runCatching { complaintTypeRepo.delete(id) }
            _state.update { it.copy(complaintTypes = it.complaintTypes.filter { c -> c.id != id }) }
        }
    }
}
