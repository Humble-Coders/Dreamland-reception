package com.example.dreamland_reception.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.dreamland_reception.data.loading.DreamlandLoadCoordinator
import kotlinx.coroutines.launch

fun ViewModel.launchWithGlobalLoading(block: suspend () -> Unit) {
    viewModelScope.launch {
        DreamlandLoadCoordinator.begin()
        try {
            block()
        } finally {
            DreamlandLoadCoordinator.end()
        }
    }
}
