package com.fgmachines.mikrotikmanager.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.fgmachines.mikrotikmanager.data.DashboardSnapshot
import com.fgmachines.mikrotikmanager.data.DiscoveredRouter
import com.fgmachines.mikrotikmanager.data.RouterConnectionSettings
import com.fgmachines.mikrotikmanager.data.RouterInterface
import com.fgmachines.mikrotikmanager.data.RouterRepository
import com.fgmachines.mikrotikmanager.voucher.RouterVoucherProfile
import com.fgmachines.mikrotikmanager.voucher.SavedVoucherBatch
import com.fgmachines.mikrotikmanager.voucher.VoucherBatch
import com.fgmachines.mikrotikmanager.voucher.VoucherHistoryStore
import com.fgmachines.mikrotikmanager.voucher.VoucherMode
import com.fgmachines.mikrotikmanager.voucher.VoucherProvisionSummary
import com.fgmachines.mikrotikmanager.network.MndpDiscovery
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

enum class AppSection(val title: String) {
    DASHBOARD("Dashboard"),
    VOUCHERS("Vouchers"),
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
    val voucherProfiles: Map<VoucherMode, List<RouterVoucherProfile>> = emptyMap(),
    val voucherProfilesLoading: Boolean = false,
    val voucherProvisioning: Boolean = false,
    val voucherProvisionResult: VoucherProvisionSummary? = null,
    val voucherHistoryCount: Int = 0,
    val recentVoucherBatches: List<SavedVoucherBatch> = emptyList(),
    val discoveringRouters: Boolean = false,
    val discoveredRouters: List<DiscoveredRouter> = emptyList()
)

class RouterViewModel(application: Application) : AndroidViewModel(application) {
    private val _state = MutableStateFlow(RouterUiState())
    val state: StateFlow<RouterUiState> = _state.asStateFlow()

    private var repository: RouterRepository? = null
    private val voucherHistory = VoucherHistoryStore(application)
    private val mndpDiscovery = MndpDiscovery(application)

    init {
        viewModelScope.launch {
            _state.value = _state.value.copy(
                voucherHistoryCount = voucherHistory.count(),
                recentVoucherBatches = voucherHistory.recent()
            )
        }
    }

    fun discoverRouters() {
        if (_state.value.discoveringRouters) return

        viewModelScope.launch {
            _state.value = _state.value.copy(
                discoveringRouters = true,
                error = null
            )
            runCatching {
                mndpDiscovery.discover()
            }.onSuccess { routers ->
                _state.value = _state.value.copy(
                    discoveringRouters = false,
                    discoveredRouters = routers,
                    error = if (routers.isEmpty()) {
                        "No MikroTik routers were discovered on this local network"
                    } else {
                        null
                    }
                )
            }.onFailure { throwable ->
                _state.value = _state.value.copy(
                    discoveringRouters = false,
                    error = throwable.message ?: "Router discovery failed"
                )
            }
        }
    }

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
                    interfaces = dashboard.interfaces,
                    voucherHistoryCount = voucherHistory.count(),
                    recentVoucherBatches = voucherHistory.recent()
                )
                refreshVoucherProfiles(VoucherMode.HOTSPOT)
            } catch (t: Throwable) {
                repository?.close()
                repository = null
                _state.value = RouterUiState(
                    error = t.message ?: "Connection failed",
                    voucherHistoryCount = voucherHistory.count(),
                    recentVoucherBatches = voucherHistory.recent()
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
        if (section == AppSection.VOUCHERS &&
            _state.value.voucherProfiles[VoucherMode.HOTSPOT].isNullOrEmpty()
        ) {
            refreshVoucherProfiles(VoucherMode.HOTSPOT)
        }
    }

    fun refreshVoucherProfiles(mode: VoucherMode) {
        val repo = repository ?: return
        if (mode == VoucherMode.OFFLINE) return

        viewModelScope.launch {
            _state.value = _state.value.copy(voucherProfilesLoading = true)
            val result = runCatching { repo.loadVoucherProfiles(mode) }
            val current = _state.value.voucherProfiles.toMutableMap()

            result.onSuccess { current[mode] = it }

            _state.value = _state.value.copy(
                voucherProfilesLoading = false,
                voucherProfiles = current,
                error = result.exceptionOrNull()?.let { throwable ->
                    if (mode == VoucherMode.USER_MANAGER) {
                        null
                    } else {
                        throwable.message ?: "Unable to load voucher profiles"
                    }
                } ?: _state.value.error
            )
        }
    }

    fun saveGeneratedBatch(batch: VoucherBatch) {
        viewModelScope.launch {
            runCatching { voucherHistory.save(batch) }
            _state.value = _state.value.copy(
                voucherHistoryCount = voucherHistory.count(),
                recentVoucherBatches = voucherHistory.recent()
            )
        }
    }

    fun provisionVouchers(batch: VoucherBatch) {
        val repo = repository ?: return
        if (_state.value.voucherProvisioning) return

        viewModelScope.launch {
            _state.value = _state.value.copy(
                voucherProvisioning = true,
                voucherProvisionResult = null,
                error = null
            )

            runCatching {
                repo.provisionVoucherBatch(batch)
            }.onSuccess { summary ->
                _state.value = _state.value.copy(
                    voucherProvisioning = false,
                    voucherProvisionResult = summary,
                    voucherHistoryCount = voucherHistory.count(),
                    recentVoucherBatches = voucherHistory.recent()
                )
            }.onFailure { throwable ->
                _state.value = _state.value.copy(
                    voucherProvisioning = false,
                    error = throwable.message ?: "Voucher provisioning failed",
                    voucherHistoryCount = voucherHistory.count(),
                    recentVoucherBatches = voucherHistory.recent()
                )
            }
        }
    }

    fun clearVoucherProvisionResult() {
        _state.value = _state.value.copy(voucherProvisionResult = null)
    }

    fun disconnect() {
        repository?.close()
        repository = null
        val previous = _state.value
        _state.value = RouterUiState(
            voucherHistoryCount = previous.voucherHistoryCount,
            recentVoucherBatches = previous.recentVoucherBatches
        )
    }

    fun clearError() {
        _state.value = _state.value.copy(error = null)
    }

    override fun onCleared() {
        repository?.close()
        super.onCleared()
    }
}
