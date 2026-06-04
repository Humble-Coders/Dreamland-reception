package com.example.dreamland_reception.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.dreamland_reception.data.AppContext
import com.example.dreamland_reception.data.accounting.AccountingRepository
import com.example.dreamland_reception.data.model.ReceptionManager
import com.example.dreamland_reception.data.model.ShiftHandover
import com.example.dreamland_reception.data.repository.FirestoreShiftRepository
import com.example.dreamland_reception.data.repository.ShiftRepository
import com.example.dreamland_reception.data.repository.hashManagerPassword
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * Owns the reception desk handover state: who's on duty, the list of managers, and
 * the password-verified handover that logs the shift change (with Cash & Bank at
 * that moment). The on-duty manager is stamped onto every Humble Ledger entry.
 */
class ShiftViewModel(
    private val shiftRepo: ShiftRepository = FirestoreShiftRepository,
    private val accountingRepo: AccountingRepository = AccountingRepository,
) : ViewModel() {

    private val _currentManager = MutableStateFlow(AppContext.currentManager)
    val currentManager: StateFlow<String> = _currentManager.asStateFlow()

    private val _managers = MutableStateFlow<List<ReceptionManager>>(emptyList())
    val managers: StateFlow<List<ReceptionManager>> = _managers.asStateFlow()

    // False on every fresh app launch (this ViewModel is a process-scoped singleton),
    // so the desk is locked until a manager signs in for the session. It never
    // persists — closing and reopening the app always re-locks.
    private val _sessionStarted = MutableStateFlow(false)
    val sessionStarted: StateFlow<Boolean> = _sessionStarted.asStateFlow()

    init { loadManagers() }

    fun loadManagers() {
        val hotelId = AppContext.hotelId
        if (hotelId.isBlank()) return
        viewModelScope.launch {
            runCatching { shiftRepo.listManagers(hotelId) }.onSuccess { _managers.value = it }
        }
    }

    /** Creates a new manager (password stored only as a salted hash). */
    fun addManager(name: String, password: String, onResult: (ok: Boolean, error: String?) -> Unit) {
        val hotelId = AppContext.hotelId
        val n = name.trim()
        when {
            hotelId.isBlank() -> { onResult(false, "No hotel selected"); return }
            n.isEmpty() -> { onResult(false, "Name is required"); return }
            password.isEmpty() -> { onResult(false, "Password is required"); return }
            _managers.value.any { it.name.equals(n, ignoreCase = true) } -> { onResult(false, "A manager named \"$n\" already exists"); return }
        }
        viewModelScope.launch {
            runCatching { shiftRepo.addManager(hotelId, n, password) }
                .onSuccess { created ->
                    _managers.update { list -> (list + created).sortedBy { it.name.lowercase() } }
                    onResult(true, null)
                }
                .onFailure { e -> onResult(false, e.message ?: "Failed to add manager") }
        }
    }

    /**
     * Signs a manager in for this app session (the launch-time desk lock). Verifies
     * the password, records the shift start (with Cash & Bank, unless the same person
     * is resuming), makes them on duty, and unlocks the app. Distinct from [handover]
     * in that it has no "already on duty" guard — at launch nobody is signed in yet.
     */
    fun startSession(managerName: String, password: String, onResult: (ok: Boolean, error: String?) -> Unit) {
        val manager = _managers.value.firstOrNull { it.name.equals(managerName.trim(), ignoreCase = true) }
        if (manager == null) { onResult(false, "Manager not found"); return }
        if (password.isEmpty()) { onResult(false, "Password is required"); return }
        if (manager.passwordHash != hashManagerPassword(manager.name, password)) { onResult(false, "Incorrect password"); return }

        viewModelScope.launch {
            val from = AppContext.currentManager
            // Log the shift start as a handover from whoever was last on duty — unless
            // the same manager is simply resuming after a restart. Best-effort.
            if (!manager.name.equals(from, ignoreCase = true)) {
                val bal = runCatching { accountingRepo.fetchCashBankBalance() }.getOrNull()
                runCatching {
                    shiftRepo.logHandover(
                        ShiftHandover(
                            hotelId = AppContext.hotelId,
                            fromManager = from,
                            toManager = manager.name,
                            cashAtHandover = bal?.cash,
                            bankAtHandover = bal?.bank,
                        )
                    )
                }
            }
            AppContext.setManager(manager.name)
            _currentManager.value = manager.name
            _sessionStarted.value = true
            onResult(true, null)
        }
    }

    /**
     * Verifies [password] against the incoming manager and, if correct, logs the
     * handover (with the current Cash & Bank balances) and makes them on duty.
     */
    fun handover(toManagerName: String, password: String, onResult: (ok: Boolean, error: String?) -> Unit) {
        val manager = _managers.value.firstOrNull { it.name.equals(toManagerName.trim(), ignoreCase = true) }
        if (manager == null) { onResult(false, "Manager not found"); return }
        if (manager.name.equals(_currentManager.value, ignoreCase = true)) { onResult(false, "${manager.name} is already on duty"); return }
        if (password.isEmpty()) { onResult(false, "Password is required"); return }
        if (manager.passwordHash != hashManagerPassword(manager.name, password)) { onResult(false, "Incorrect password"); return }

        viewModelScope.launch {
            val from = AppContext.currentManager
            // Snapshot Cash & Bank at the moment of handover (best-effort).
            val bal = runCatching { accountingRepo.fetchCashBankBalance() }.getOrNull()
            // Log the handover — best-effort; never block the desk change on it.
            runCatching {
                shiftRepo.logHandover(
                    ShiftHandover(
                        hotelId = AppContext.hotelId,
                        fromManager = from,
                        toManager = manager.name,
                        cashAtHandover = bal?.cash,
                        bankAtHandover = bal?.bank,
                    )
                )
            }
            AppContext.setManager(manager.name)
            _currentManager.value = manager.name
            onResult(true, null)
        }
    }
}
