package com.fgmachines.mikrotikmanager.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ArrowBack
import androidx.compose.material.icons.outlined.CreditCard
import androidx.compose.material.icons.outlined.Dashboard
import androidx.compose.material.icons.outlined.Language
import androidx.compose.material.icons.outlined.Logout
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.SettingsEthernet
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.VerticalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.fgmachines.mikrotikmanager.data.DashboardSnapshot
import com.fgmachines.mikrotikmanager.data.RouterConnectionSettings
import com.fgmachines.mikrotikmanager.data.RouterInterface
import java.text.DecimalFormat
import java.util.Locale

@Composable
fun RouterApp(viewModel: RouterViewModel = viewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var arabic by rememberSaveable {
        mutableStateOf(Locale.getDefault().language.equals("ar", ignoreCase = true))
    }
    var offlineStudio by rememberSaveable { mutableStateOf(false) }

    CompositionLocalProvider(
        LocalLayoutDirection provides if (arabic) LayoutDirection.Rtl else LayoutDirection.Ltr
    ) {
        FgMikroTikTheme {
            if (offlineStudio) {
                OfflineVoucherShell(
                    arabic = arabic,
                    historyCount = state.voucherHistoryCount,
                    onLanguageToggle = { arabic = !arabic },
                    onBatchGenerated = viewModel::saveGeneratedBatch,
                    onBack = { offlineStudio = false }
                )
            } else if (state.connected) {
                RouterShell(
                    state = state,
                    arabic = arabic,
                    onLanguageToggle = { arabic = !arabic },
                    onRefresh = viewModel::refresh,
                    onDisconnect = viewModel::disconnect,
                    onSection = viewModel::selectSection,
                    onVoucherModeSelected = viewModel::refreshVoucherProfiles,
                    onVoucherBatchGenerated = viewModel::saveGeneratedBatch,
                    onProvisionVouchers = viewModel::provisionVouchers,
                    onClearVoucherResult = viewModel::clearVoucherProvisionResult
                )
            } else {
                ConnectionScreen(
                    connecting = state.connecting,
                    error = state.error,
                    arabic = arabic,
                    onLanguageToggle = { arabic = !arabic },
                    onOfflineStudio = { offlineStudio = true },
                    onConnect = viewModel::connect,
                    onClearError = viewModel::clearError
                )
            }
        }
    }
}

@Composable
private fun ConnectionScreen(
    connecting: Boolean,
    error: String?,
    arabic: Boolean,
    onLanguageToggle: () -> Unit,
    onOfflineStudio: () -> Unit,
    onConnect: (RouterConnectionSettings) -> Unit,
    onClearError: () -> Unit
) {
    var host by remember { mutableStateOf("192.168.88.1") }
    var port by remember { mutableStateOf("443") }
    var username by remember { mutableStateOf("admin") }
    var password by remember { mutableStateOf("") }

    Box(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        contentAlignment = Alignment.Center
    ) {
        Card(modifier = Modifier.fillMaxWidth().widthIn(max = 560.dp)) {
            Column(
                modifier = Modifier.padding(24.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = "FG MikroTik Manager",
                            style = MaterialTheme.typography.headlineMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = if (arabic) "إدارة الراوتر والكروت من مكان واحد"
                                else "Router management and vouchers in one place",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    IconButton(onClick = onLanguageToggle) {
                        Icon(Icons.Outlined.Language, contentDescription = "Language")
                    }
                }

                OutlinedTextField(
                    value = host,
                    onValueChange = {
                        host = it
                        onClearError()
                    },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text(if (arabic) "IP الراوتر أو اسم المضيف" else "Router IP or hostname") },
                    singleLine = true
                )

                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedTextField(
                        value = port,
                        onValueChange = { port = it.filter(Char::isDigit) },
                        modifier = Modifier.weight(0.34f),
                        label = { Text(if (arabic) "منفذ HTTPS" else "HTTPS port") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true
                    )
                    OutlinedTextField(
                        value = username,
                        onValueChange = { username = it },
                        modifier = Modifier.weight(0.66f),
                        label = { Text(if (arabic) "اسم المستخدم" else "Username") },
                        singleLine = true
                    )
                }

                OutlinedTextField(
                    value = password,
                    onValueChange = { password = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text(if (arabic) "كلمة المرور" else "Password") },
                    visualTransformation = PasswordVisualTransformation(),
                    singleLine = true
                )

                if (error != null) {
                    Text(
                        text = error,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodyMedium
                    )
                }

                Button(
                    onClick = {
                        val parsedPort = port.toIntOrNull() ?: 443
                        onConnect(
                            RouterConnectionSettings(
                                host = host,
                                port = parsedPort,
                                username = username,
                                password = password
                            )
                        )
                    },
                    enabled = !connecting &&
                        host.isNotBlank() &&
                        username.isNotBlank() &&
                        (port.toIntOrNull()?.let { it in 1..65535 } == true),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    if (connecting) {
                        CircularProgressIndicator(
                            modifier = Modifier.width(18.dp).height(18.dp),
                            strokeWidth = 2.dp
                        )
                        Spacer(Modifier.width(10.dp))
                        Text(if (arabic) "جاري الاتصال" else "Connecting")
                    } else {
                        Text(if (arabic) "اتصال بالراوتر" else "Connect")
                    }
                }

                OutlinedButton(
                    onClick = onOfflineStudio,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Outlined.CreditCard, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text(
                        if (arabic) "إنشاء كروت بدون الاتصال بالراوتر"
                        else "Create vouchers offline"
                    )
                }

                Text(
                    text = if (arabic) "الاتصال الآمن يستخدم HTTPS. سنضيف API-SSL لتوافق أوسع مع RouterOS." else "Secure connection uses HTTPS. API-SSL support is being added for wider RouterOS compatibility.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun RouterShell(
    state: RouterUiState,
    arabic: Boolean,
    onLanguageToggle: () -> Unit,
    onRefresh: () -> Unit,
    onDisconnect: () -> Unit,
    onSection: (AppSection) -> Unit,
    onVoucherModeSelected: (com.fgmachines.mikrotikmanager.voucher.VoucherMode) -> Unit,
    onVoucherBatchGenerated: (com.fgmachines.mikrotikmanager.voucher.VoucherBatch) -> Unit,
    onProvisionVouchers: (com.fgmachines.mikrotikmanager.voucher.VoucherBatch) -> Unit,
    onClearVoucherResult: () -> Unit
) {
    val wide = LocalConfiguration.current.screenWidthDp >= 840
    val identity = state.dashboard?.identity ?: "RouterOS"

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(identity)
                        Text(
                            text = sectionLabel(state.section, arabic),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                },
                actions = {
                    IconButton(onClick = onLanguageToggle) {
                        Icon(Icons.Outlined.Language, contentDescription = "Language")
                    }
                    IconButton(onClick = onRefresh, enabled = !state.refreshing) {
                        if (state.refreshing) {
                            CircularProgressIndicator(
                                modifier = Modifier.width(20.dp).height(20.dp),
                                strokeWidth = 2.dp
                            )
                        } else {
                            Icon(Icons.Outlined.Refresh, contentDescription = "Refresh")
                        }
                    }
                    IconButton(onClick = onDisconnect) {
                        Icon(Icons.Outlined.Logout, contentDescription = "Disconnect")
                    }
                }
            )
        },
        bottomBar = {
            if (!wide) {
                NavigationBar {
                    AppSection.entries.forEach { section ->
                        NavigationBarItem(
                            selected = section == state.section,
                            onClick = { onSection(section) },
                            icon = { SectionIcon(section) },
                            label = { Text(sectionLabel(section, arabic)) }
                        )
                    }
                }
            }
        }
    ) { padding ->
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            if (wide) {
                NavigationRail {
                    AppSection.entries.forEach { section ->
                        NavigationRailItem(
                            selected = section == state.section,
                            onClick = { onSection(section) },
                            icon = { SectionIcon(section) },
                            label = { Text(sectionLabel(section, arabic)) }
                        )
                    }
                }
                VerticalDivider(
                    modifier = Modifier.fillMaxHeight()
                )
            }

            Box(modifier = Modifier.fillMaxSize()) {
                when (state.section) {
                    AppSection.DASHBOARD ->
                        DashboardScreen(state.dashboard, state.error, arabic)
                    AppSection.VOUCHERS ->
                        VoucherStudioScreen(
                            arabic = arabic,
                            connected = true,
                            profiles = state.voucherProfiles,
                            profilesLoading = state.voucherProfilesLoading,
                            provisioning = state.voucherProvisioning,
                            provisionResult = state.voucherProvisionResult,
                            historyCount = state.voucherHistoryCount,
                            onModeSelected = onVoucherModeSelected,
                            onBatchGenerated = onVoucherBatchGenerated,
                            onProvision = onProvisionVouchers,
                            onClearProvisionResult = onClearVoucherResult,
                            modifier = Modifier.fillMaxSize()
                        )
                    AppSection.INTERFACES ->
                        InterfacesScreen(state.interfaces, wide, state.error, arabic)
                }
            }
        }
    }
}

@Composable
private fun SectionIcon(section: AppSection) {
    when (section) {
        AppSection.DASHBOARD ->
            Icon(Icons.Outlined.Dashboard, contentDescription = null)
        AppSection.VOUCHERS ->
            Icon(Icons.Outlined.CreditCard, contentDescription = null)
        AppSection.INTERFACES ->
            Icon(Icons.Outlined.SettingsEthernet, contentDescription = null)
    }
}

@Composable
private fun DashboardScreen(
    snapshot: DashboardSnapshot?,
    error: String?,
    arabic: Boolean
) {
    if (snapshot == null) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator()
        }
        return
    }

    LazyVerticalGrid(
        columns = GridCells.Adaptive(180.dp),
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(18.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        if (error != null) {
            item {
                MetricCard("Last error", error)
            }
        }

        items(
            listOf(
                (if (arabic) "اسم الراوتر" else "Identity") to snapshot.identity,
                "RouterOS" to snapshot.version.ifBlank { if (arabic) "غير معروف" else "Unknown" },
                (if (arabic) "الموديل" else "Board") to snapshot.boardName.ifBlank { if (arabic) "غير معروف" else "Unknown" },
                (if (arabic) "المعمارية" else "Architecture") to snapshot.architecture.ifBlank { if (arabic) "غير معروف" else "Unknown" },
                (if (arabic) "حمل المعالج" else "CPU load") to (snapshot.cpuLoadPercent?.let { it.toString() + "%" } ?: "—"),
                (if (arabic) "الذاكرة المتاحة" else "Memory free") to formatBytes(snapshot.freeMemoryBytes),
                (if (arabic) "مدة التشغيل" else "Uptime") to snapshot.uptime.ifBlank { "—" },
                (if (arabic) "واجهات الشبكة" else "Interfaces") to snapshot.interfaces.size.toString()
            )
        ) { metric ->
            MetricCard(metric.first, metric.second)
        }
    }
}

@Composable
private fun MetricCard(label: String, value: String) {
    Card {
        Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = value,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold
            )
        }
    }
}

@Composable
private fun InterfacesScreen(
    interfaces: List<RouterInterface>,
    wide: Boolean,
    error: String?,
    arabic: Boolean
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(18.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        if (error != null) {
            item {
                Text(error, color = MaterialTheme.colorScheme.error)
            }
        }

        items(interfaces, key = { it.id.ifBlank { it.name } }) { item ->
            Card {
                if (wide) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(16.dp),
                        horizontalArrangement = Arrangement.spacedBy(18.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(item.name, modifier = Modifier.weight(1.2f), fontWeight = FontWeight.SemiBold)
                        Text(item.type.ifBlank { "—" }, modifier = Modifier.weight(1f))
                        Text(if (item.running) { if (arabic) "يعمل" else "Running" } else { if (arabic) "متوقف" else "Down" }, modifier = Modifier.weight(0.8f))
                        Text("RX " + formatBytes(item.rxBytes), modifier = Modifier.weight(1f))
                        Text("TX " + formatBytes(item.txBytes), modifier = Modifier.weight(1f))
                    }
                } else {
                    Column(
                        modifier = Modifier.fillMaxWidth().padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(5.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(item.name, fontWeight = FontWeight.SemiBold)
                            Text(if (item.running) { if (arabic) "يعمل" else "Running" } else { if (arabic) "متوقف" else "Down" })
                        }
                        Text(
                            item.type.ifBlank { if (arabic) "نوع غير معروف" else "Unknown type" },
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text("RX " + formatBytes(item.rxBytes) + "   •   TX " + formatBytes(item.txBytes))
                    }
                }
            }
        }
    }
}

private fun formatBytes(value: Long?): String {
    if (value == null) return "—"
    if (value < 1024) return value.toString() + " B"

    val units = arrayOf("KB", "MB", "GB", "TB")
    var amount = value.toDouble()
    var unitIndex = -1
    while (amount >= 1024 && unitIndex < units.lastIndex) {
        amount /= 1024
        unitIndex++
    }
    return DecimalFormat("0.##").format(amount) + " " + units[unitIndex]
}


private fun sectionLabel(section: AppSection, arabic: Boolean): String =
    when (section) {
        AppSection.DASHBOARD -> if (arabic) "الرئيسية" else "Dashboard"
        AppSection.VOUCHERS -> if (arabic) "الكروت" else "Vouchers"
        AppSection.INTERFACES -> if (arabic) "الواجهات" else "Interfaces"
    }


@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun OfflineVoucherShell(
    arabic: Boolean,
    historyCount: Int,
    onLanguageToggle: () -> Unit,
    onBatchGenerated: (com.fgmachines.mikrotikmanager.voucher.VoucherBatch) -> Unit,
    onBack: () -> Unit
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            if (arabic) "وضع الكروت بدون راوتر"
                            else "Offline Voucher Studio"
                        )
                        Text(
                            if (arabic) "أنشئ وصدّر الكروت ثم ارفعها لاحقًا"
                            else "Generate and export now, provision later",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Outlined.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(onClick = onLanguageToggle) {
                        Icon(Icons.Outlined.Language, contentDescription = "Language")
                    }
                }
            )
        }
    ) { padding ->
        VoucherStudioScreen(
            arabic = arabic,
            connected = false,
            historyCount = historyCount,
            onBatchGenerated = onBatchGenerated,
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        )
    }
}
