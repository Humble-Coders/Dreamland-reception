package com.example.dreamland_reception.ui.viewmodel

import androidx.lifecycle.ViewModel
import com.example.dreamland_reception.data.model.BillingInvoice
import com.example.dreamland_reception.data.repository.BillingRepository
import com.example.dreamland_reception.data.repository.FirestoreBillingRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

sealed interface BillingUiState {
    data object Loading : BillingUiState
    data class Success(val invoices: List<BillingInvoice>) : BillingUiState
    data class Error(val message: String) : BillingUiState
}

class BillingViewModel(
    private val repo: BillingRepository = FirestoreBillingRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow<BillingUiState>(BillingUiState.Loading)
    val uiState: StateFlow<BillingUiState> = _uiState.asStateFlow()

    init {
        load()
    }

    fun load() {
        launchWithGlobalLoading {
            _uiState.value = BillingUiState.Loading
            runCatching { repo.getAll() }
                .onSuccess { _uiState.value = BillingUiState.Success(it) }
                .onFailure { _uiState.value = BillingUiState.Error(it.message ?: "Unknown error") }
        }
    }

    fun markPaid(invoiceId: String, method: String) {
        launchWithGlobalLoading {
            runCatching { repo.markPaid(invoiceId, method) }.onSuccess { refetch() }
        }
    }

    private suspend fun refetch() {
        _uiState.value = BillingUiState.Loading
        runCatching { repo.getAll() }
            .onSuccess { _uiState.value = BillingUiState.Success(it) }
            .onFailure { _uiState.value = BillingUiState.Error(it.message ?: "Unknown error") }
    }
}
