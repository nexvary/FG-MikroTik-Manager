package com.fgmachines.mikrotikmanager.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.fgmachines.mikrotikmanager.command.CommandExecutionResult
import com.fgmachines.mikrotikmanager.command.ParsedRouterCommand
import com.fgmachines.mikrotikmanager.data.DashboardSnapshot
import com.fgmachines.mikrotikmanager.data.DiscoveredRouter
import com.fgmachines.mikrotikmanager.data.RouterAdminModule
import com.fgmachines.mikrotikmanager.data.RouterMenuSnapshot
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
    MENU("Menu"),
    ADVANCED("Advanced Setup"),
    NETWORK("Network"),
    SYSTEM("System"),
    VOUCHERS("Vouchers"),
    BUSINESS("Business"),
    ABOUT("About")
}

data class RouterUiState(
    val connecting: Boolean = false,
    val refreshing: Boolean = false,
    val connected: Boolean = false,
    val error: String? = null,
    val dashboard: DashboardSnapshot? = null,
    val interfaces: List<RouterInterface> = emptyList(),
    val section: AppSection = AppSection.MENU,
    val voucherProfiles: Map<VoucherMode, List<RouterVoucherProfile>> = emptyMap(),
    val voucherProfilesLoading: Boolean = false,
    val voucherProvisioning: Boolean = false,
    val voucherProvisionResult: VoucherProvisionSummary? = null,
    val voucherHistoryCount: Int = 0,
    val recentVoucherBatches: List<SavedVoucherBatch> = emptyList(),
    val discoveringRouters: Boolean = false,
    val discoveredRouters: List<DiscoveredRouter> = emptyList(),
    val adminLoading: Boolean = false,
    val adminModule: RouterAdminModule? = null,
    val adminSnapshot: RouterMenuSnapshot? = null,
    val adminError: String? = null,
    val adminActionRunning: Boolean = false,
    val adminActionMessage: String? = null,
    val commandRunning: Boolean = false,
    val commandResults: List<CommandExecutionResult> = emptyList(),
    val commandHistory: List<CommandExecutionResult> = emptyList()
)

class RouterViewModel(application: Application) : AndroidViewModel(application) {
    private val _state = MutableStateFlow(RouterUiState())
    val state: StateFlow<RouterUiState> = _state.asStateFlow()

    private var repository: RouterRepository? = null
    val businessRouter get() = repository?.business
    val advancedManager get() = repository?.advanced
    val hotspotManager get() = repository?.hotspot
    private val voucherHistory = VoucherHistoryStore(application)
    private suspend fun archiveCount()=runCatching{voucherHistory.count()}.getOrDefault(0)
    private suspend fun archiveRecent()=runCatching{voucherHistory.recent()}.getOrDefault(emptyList())
    private val mndpDiscovery = MndpDiscovery(application)

    init {
        viewModelScope.launch {
            _state.value = _state.value.copy(
                voucherHistoryCount = archiveCount(),
                recentVoucherBatches = archiveRecent()
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
                val newRepository = RouterRepository.create(settings) { com.fgmachines.mikrotikmanager.business.BusinessAuthorizedTransport(it,com.fgmachines.mikrotikmanager.business.BusinessStore(com.fgmachines.mikrotikmanager.business.BusinessDatabase(getApplication()))) }
                repository = newRepository
                val dashboard = newRepository.loadDashboard()
                _state.value = RouterUiState(
                    connected = true,
                    dashboard = dashboard,
                    interfaces = dashboard.interfaces,
                    voucherHistoryCount = archiveCount(),
                    recentVoucherBatches = archiveRecent()
                )
                refreshVoucherProfiles(VoucherMode.HOTSPOT)
            } catch (t: Throwable) {
                repository?.close()
                repository = null
                _state.value = RouterUiState(
                    error = t.message ?: "Connection failed",
                    voucherHistoryCount = archiveCount(),
                    recentVoucherBatches = archiveRecent()
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
        _state.value = _state.value.copy(
            section = section,
            adminModule = null,
            adminSnapshot = null,
            adminError = null,
            adminActionMessage = null
        )
        if (section == AppSection.VOUCHERS &&
            _state.value.voucherProfiles[VoucherMode.HOTSPOT].isNullOrEmpty()
        ) {
            refreshVoucherProfiles(VoucherMode.HOTSPOT)
        }
    }

    fun openAdminModule(module: RouterAdminModule) {
        val repo = repository ?: return

        viewModelScope.launch {
            _state.value = _state.value.copy(
                adminLoading = true,
                adminModule = module,
                adminSnapshot = null,
                adminError = null
            )
            runCatching {
                repo.loadAdminModule(module)
            }.onSuccess { snapshot ->
                _state.value = _state.value.copy(
                    adminLoading = false,
                    adminSnapshot = snapshot,
                    adminError = null
                )
            }.onFailure { throwable ->
                _state.value = _state.value.copy(
                    adminLoading = false,
                    adminSnapshot = null,
                    adminError = throwable.message ?: "Unable to open RouterOS menu"
                )
            }
        }
    }

    fun refreshAdminModule() {
        _state.value.adminModule?.let(::openAdminModule)
    }

    fun closeAdminModule() {
        _state.value = _state.value.copy(
            adminModule = null,
            adminSnapshot = null,
            adminError = null,
            adminLoading = false
        )
    }

    fun createAdminItem(
        attributes: Map<String, String>
    ) {
        val repo = repository ?: return
        val snapshot = _state.value.adminSnapshot ?: return
        if (_state.value.adminActionRunning) return

        viewModelScope.launch {
            _state.value = _state.value.copy(
                adminActionRunning = true,
                adminActionMessage = null
            )
            val backupFile = runCatching { backupBeforeAdminChange(repo, snapshot.module) }.getOrElse {
                _state.value = _state.value.copy(adminActionRunning=false, adminActionMessage="لم يتم التعديل لأن النسخة الاحتياطية لم تُحفظ / Change aborted: backup failed — " + it.message.orEmpty())
                return@launch
            }
            val result = repo.createAdminItem(snapshot.menuPath, attributes)
            _state.value = _state.value.copy(
                adminActionRunning = false,
                adminActionMessage = result.message
            )
            logAdminChange(repo, snapshot.module, result.success, backupFile)
            if (result.success) refreshAdminModule()
        }
    }

    fun createRouterAdmin(
        username: String,
        password: String,
        group: String,
        comment: String
    ) {
        val repo = repository ?: return
        if (_state.value.adminActionRunning) return

        viewModelScope.launch {
            _state.value = _state.value.copy(
                adminActionRunning = true,
                adminActionMessage = null
            )
            val backupFile = runCatching { backupBeforeAdminChange(repo, RouterAdminModule.USERS) }.getOrElse {
                _state.value = _state.value.copy(adminActionRunning=false, adminActionMessage="لم يتم التعديل لأن النسخة الاحتياطية لم تُحفظ / Change aborted: backup failed — " + it.message.orEmpty())
                return@launch
            }
            val result = repo.createRouterAdmin(
                username = username,
                password = password,
                group = group,
                comment = comment
            )
            _state.value = _state.value.copy(
                adminActionRunning = false,
                adminActionMessage = result.message
            )
            logAdminChange(repo, RouterAdminModule.USERS, result.success, backupFile)
            if (result.success) refreshAdminModule()
        }
    }

    fun updateAdminItem(
        rowId: String?,
        attributes: Map<String, String>
    ) {
        val repo = repository ?: return
        val snapshot = _state.value.adminSnapshot ?: return
        if (_state.value.adminActionRunning) return

        viewModelScope.launch {
            _state.value = _state.value.copy(
                adminActionRunning = true,
                adminActionMessage = null
            )
            val backupFile = runCatching { backupBeforeAdminChange(repo, snapshot.module) }.getOrElse {
                _state.value = _state.value.copy(adminActionRunning=false, adminActionMessage="لم يتم التعديل لأن النسخة الاحتياطية لم تُحفظ / Change aborted: backup failed — " + it.message.orEmpty())
                return@launch
            }
            val result = repo.updateAdminItem(
                snapshot.menuPath,
                rowId,
                attributes
            )
            _state.value = _state.value.copy(
                adminActionRunning = false,
                adminActionMessage = result.message
            )
            logAdminChange(repo, snapshot.module, result.success, backupFile)
            if (result.success) refreshAdminModule()
        }
    }

    fun removeAdminItem(rowId: String) {
        val repo = repository ?: return
        val snapshot = _state.value.adminSnapshot ?: return
        if (_state.value.adminActionRunning) return

        viewModelScope.launch {
            _state.value = _state.value.copy(
                adminActionRunning = true,
                adminActionMessage = null
            )
            val backupFile = runCatching { backupBeforeAdminChange(repo, snapshot.module) }.getOrElse {
                _state.value = _state.value.copy(adminActionRunning=false, adminActionMessage="لم يتم التعديل لأن النسخة الاحتياطية لم تُحفظ / Change aborted: backup failed — " + it.message.orEmpty())
                return@launch
            }
            val result = repo.removeAdminItem(snapshot.menuPath, rowId)
            _state.value = _state.value.copy(
                adminActionRunning = false,
                adminActionMessage = result.message
            )
            logAdminChange(repo, snapshot.module, result.success, backupFile)
            if (result.success) refreshAdminModule()
        }
    }

    fun setAdminItemEnabled(
        rowId: String,
        enabled: Boolean
    ) {
        val repo = repository ?: return
        val snapshot = _state.value.adminSnapshot ?: return
        if (_state.value.adminActionRunning) return

        viewModelScope.launch {
            _state.value = _state.value.copy(
                adminActionRunning = true,
                adminActionMessage = null
            )
            val backupFile = runCatching { backupBeforeAdminChange(repo, snapshot.module) }.getOrElse {
                _state.value = _state.value.copy(adminActionRunning=false, adminActionMessage="لم يتم التعديل لأن النسخة الاحتياطية لم تُحفظ / Change aborted: backup failed — " + it.message.orEmpty())
                return@launch
            }
            val result = repo.setAdminItemEnabled(
                snapshot.menuPath,
                rowId,
                enabled
            )
            _state.value = _state.value.copy(
                adminActionRunning = false,
                adminActionMessage = result.message
            )
            logAdminChange(repo, snapshot.module, result.success, backupFile)
            if (result.success) refreshAdminModule()
        }
    }

    private suspend fun backupBeforeAdminChange(repo: RouterRepository, module: RouterAdminModule): String {
        val sensitive = module.group == com.fgmachines.mikrotikmanager.data.RouterAdminGroup.NETWORK || module in setOf(RouterAdminModule.FIREWALL, RouterAdminModule.USERS, RouterAdminModule.SERVICES)
        if (!sensitive) return ""
        val secret = java.util.UUID.randomUUID().toString().replace("-", "")
        val file = repo.advanced.backup(secret)
        val vault = com.fgmachines.mikrotikmanager.advanced.RouterChangeVault(getApplication(), repo.advanced.routerKey)
        vault.rememberBackup(file, secret)
        vault.record("نسخة قبل تعديل " + module.name, "Backup before " + module.name, true, file)
        return file
    }

    private fun logAdminChange(repo: RouterRepository, module: RouterAdminModule, success: Boolean, backup: String) {
        runCatching { com.fgmachines.mikrotikmanager.advanced.RouterChangeVault(getApplication(),repo.advanced.routerKey).record("تعديل " + module.name,"Change " + module.name,success,backup) }
    }

    fun clearAdminActionMessage() {
        _state.value = _state.value.copy(adminActionMessage = null)
    }

    fun runCommands(commands: List<ParsedRouterCommand>) {
        val repo = repository ?: return
        if (_state.value.commandRunning || commands.isEmpty()) return

        viewModelScope.launch {
            _state.value = _state.value.copy(
                commandRunning = true,
                commandResults = emptyList()
            )

            val results = mutableListOf<CommandExecutionResult>()
            for (command in commands) {
                val result = repo.executeCommand(command)
                results += result
                _state.value = _state.value.copy(
                    commandResults = results.toList()
                )
            }

            val newHistory = (results.asReversed() + _state.value.commandHistory)
                .take(100)

            _state.value = _state.value.copy(
                commandRunning = false,
                commandResults = results,
                commandHistory = newHistory
            )

            if ((_state.value.section == AppSection.NETWORK ||
                    _state.value.section == AppSection.SYSTEM) &&
                _state.value.adminModule != null
            ) {
                refreshAdminModule()
            }
        }
    }

    fun clearCommandResults() {
        _state.value = _state.value.copy(commandResults = emptyList())
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
            val saved = runCatching { voucherHistory.save(batch) }
            _state.value = _state.value.copy(
                voucherHistoryCount = archiveCount(),
                recentVoucherBatches = archiveRecent(),
                error = saved.exceptionOrNull()?.let { "Could not save voucher archive: " + it.message } ?: _state.value.error
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
                if (batch.request.mode == VoucherMode.HOTSPOT) require(repo.advanced.preflight().voucherReady) { "الراوتر غير جاهز لتفعيل كروت HotSpot — افتح الإعداد المتقدم / Router is not ready; open Advanced Setup" }
                repo.provisionVoucherBatch(batch)
            }.onSuccess { summary ->
                _state.value = _state.value.copy(
                    voucherProvisioning = false,
                    voucherProvisionResult = summary,
                    voucherHistoryCount = archiveCount(),
                    recentVoucherBatches = archiveRecent()
                )
            }.onFailure { throwable ->
                _state.value = _state.value.copy(
                    voucherProvisioning = false,
                    error = throwable.message ?: "Voucher provisioning failed",
                    voucherHistoryCount = archiveCount(),
                    recentVoucherBatches = archiveRecent()
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

    fun refreshArchive() { viewModelScope.launch {
        _state.value=_state.value.copy(voucherHistoryCount=archiveCount(),recentVoucherBatches=archiveRecent())
    } }

    fun clearError() {
        _state.value = _state.value.copy(error = null)
    }

    override fun onCleared() {
        repository?.close()
        super.onCleared()
    }
}
