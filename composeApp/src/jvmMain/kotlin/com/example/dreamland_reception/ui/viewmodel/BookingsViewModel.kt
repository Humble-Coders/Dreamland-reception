package com.example.dreamland_reception.ui.viewmodel

import androidx.lifecycle.ViewModel
import com.example.dreamland_reception.data.model.Booking
import com.example.dreamland_reception.data.repository.BookingRepository
import com.example.dreamland_reception.data.repository.FirestoreBookingRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

sealed interface BookingsUiState {
    data object Loading : BookingsUiState
    data class Success(val bookings: List<Booking>) : BookingsUiState
    data class Error(val message: String) : BookingsUiState
}

class BookingsViewModel(
    private val repo: BookingRepository = FirestoreBookingRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow<BookingsUiState>(BookingsUiState.Loading)
    val uiState: StateFlow<BookingsUiState> = _uiState.asStateFlow()

    init {
        load()
    }

    fun load() {
        launchWithGlobalLoading {
            _uiState.value = BookingsUiState.Loading
            runCatching { repo.getAll() }
                .onSuccess { _uiState.value = BookingsUiState.Success(it) }
                .onFailure { _uiState.value = BookingsUiState.Error(it.message ?: "Unknown error") }
        }
    }

    fun deleteBooking(id: String) {
        launchWithGlobalLoading {
            runCatching { repo.delete(id) }.onSuccess { fetchAll() }
        }
    }

    private suspend fun fetchAll() {
        _uiState.value = BookingsUiState.Loading
        runCatching { repo.getAll() }
            .onSuccess { _uiState.value = BookingsUiState.Success(it) }
            .onFailure { _uiState.value = BookingsUiState.Error(it.message ?: "Unknown error") }
    }
}
