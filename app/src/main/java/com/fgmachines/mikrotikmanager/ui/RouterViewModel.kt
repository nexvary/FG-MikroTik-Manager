package com.fgmachines.mikrotikmanager.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.fgmachines.mikrotikmanager.data.DashboardSnapshot
import com.fgmachines.mikrotikmanager.data.RouterConnectionSettings
import com.fgmachines.mikrotikmanager.data.RouterInterface
import com.fgmachines.mikrotikmanager.data.RouterRepository
import com.fgmachines.mikrotikmanager.voucher.VoucherBatch
import com.fgmachines.mikrotikmanager.voucher.WcgCardSettings
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

enum class AppSection(val title: String) {
    DASHBOARD("Dashboard"),
    CARDS("Cards"),
    INTERFACES("Interfaces")
}

data class RouterUiState(
    val connecting: Boolean = false,
    val refreshing: Boolean = false,
    val connected: Boolean = false,
    val error: String? = null,
    val dashboard: DashboardSnapshot? = null,
    val interfaces: List<RouterInterface> = emptyList(),
    val section: AppSection = AppSection.DASHBOARD,
    val uploadingVouchers: Boolean = false,
    val voucherUploadMessage: String? = null
)

class RouterViewModel : ViewModel() {
    private val _state = MutableStateFlow(RouterUiState())
    val state: StateFlow<RouterUiState> = _state.asStateFlow()

    private var repository: RouterRepository? = null

    fun connect(settings: RouterConnectionSettings) {
        if (_state.value.connecting) return

        viewModelScope.launch {
            _state.value = _state.value.copy(connecting = true, error = null)
            try {
                repository?.close()
                val newRepository = RouterRepository.create(settings)
                val dashboard = newRepository.loadDashboard()
                repository = newRepository
                _state.value = RouterUiState(
                    connected = true,
                    dashboard = dashboard,
                    interfaces = dashboard.interfaces
                )
            } catch (t: Throwable) {
                repository?.close()
                repository = null
                _state.value = RouterUiState(
                    error = t.message ?: "Connection failed"
                )
            }
        }
    }

    fun refresh() {
        val repo = repository ?: return
        if (_state.value.refreshing) return

        viewModelScope.launch {
            _state.value = _state.value.copy(refreshing = true, error = null)
            try {
                val dashboard = repo.loadDashboard()
                _state.value = _state.value.copy(
                    refreshing = false,
                    dashboard = dashboard,
                    interfaces = dashboard.interfaces
                )
            } catch (t: Throwable) {
                _state.value = _state.value.copy(
                    refreshing = false,
                    error = t.message ?: "Refresh failed"
                )
            }
        }
    }

    fun selectSection(section: AppSection) {
        _state.value = _state.value.copy(section = section)
    }

    fun uploadVouchers(
        settings: WcgCardSettings,
        batch: VoucherBatch
    ) {
        val repo = repository ?: return
        if (_state.value.uploadingVouchers) return

        viewModelScope.launch {
            _state.value = _state.value.copy(
                uploadingVouchers = true,
                voucherUploadMessage = null
            )

            val result = repo.writeVoucherBatch(settings, batch)
            val message = if (result.isSuccess) {
                "Uploaded " + result.succeededCommands + " RouterOS commands successfully"
            } else {
                "Uploaded " + result.succeededCommands + "/" + result.totalCommands +
                    " commands; " + result.failures.size + " failed"
            }

            _state.value = _state.value.copy(
                uploadingVouchers = false,
                voucherUploadMessage = message
            )
        }
    }

    fun clearVoucherUploadMessage() {
        _state.value = _state.value.copy(voucherUploadMessage = null)
    }

    fun disconnect() {
        repository?.close()
        repository = null
        _state.value = RouterUiState()
    }

    fun clearError() {
        _state.value = _state.value.copy(error = null)
    }

    override fun onCleared() {
        repository?.close()
        super.onCleared()
    }
}
