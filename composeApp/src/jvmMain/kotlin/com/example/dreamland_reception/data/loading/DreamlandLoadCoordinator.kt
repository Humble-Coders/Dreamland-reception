package com.example.dreamland_reception.data.loading

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.concurrent.atomic.AtomicInteger

/**
 * Global loading overlay (reference: `JewelryApp` loading when `viewModel.loading`).
 * Nested [begin]/[end] calls are reference-counted so overlapping jobs stay covered.
 */
object DreamlandLoadCoordinator {
    private val counter = AtomicInteger(0)
    private val _loading = MutableStateFlow(false)
    val loading: StateFlow<Boolean> = _loading.asStateFlow()

    fun begin() {
        if (counter.incrementAndGet() == 1) {
            _loading.value = true
        }
    }

    fun end() {
        val v = counter.decrementAndGet()
        if (v <= 0) {
            counter.set(0)
            _loading.value = false
        }
    }
}
