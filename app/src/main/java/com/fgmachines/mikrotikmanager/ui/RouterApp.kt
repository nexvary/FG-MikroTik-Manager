package com.fgmachines.mikrotikmanager.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ArrowBack
import androidx.compose.material.icons.outlined.CreditCard
import androidx.compose.material.icons.outlined.Dashboard
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Language
import androidx.compose.material.icons.outlined.Logout
import androidx.compose.material.icons.outlined.Menu
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Router
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Wifi
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.NavigationDrawerItem
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.VerticalDivider
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.fgmachines.mikrotikmanager.data.DashboardSnapshot
import com.fgmachines.mikrotikmanager.data.DiscoveredRouter
import com.fgmachines.mikrotikmanager.data.RouterConnectionSettings
import com.fgmachines.mikrotikmanager.data.RouterProtocol
import kotlinx.coroutines.launch
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
            when {
                offlineStudio -> {
                    OfflineVoucherShell(
                        arabic = arabic,
                        historyCount = state.voucherHistoryCount,
                        recentBatches = state.recentVoucherBatches,
                        onLanguageToggle = { arabic = !arabic },
                        onBatchGenerated = viewModel::saveGeneratedBatch,
                        onBack = { offlineStudio = false }
                    )
                }

                state.connected -> {
                    RouterShell(
                        state = state,
                        arabic = arabic,
                        onLanguageToggle = { arabic = !arabic },
                        onRefresh = viewModel::refresh,
                        onDisconnect = viewModel::disconnect,
                        onSection = viewModel::selectSection,
                        onOpenAdminModule = viewModel::openAdminModule,
                        onRefreshAdminModule = viewModel::refreshAdminModule,
                        onCloseAdminModule = viewModel::closeAdminModule,
                        onVoucherModeSelected = viewModel::refreshVoucherProfiles,
                        onVoucherBatchGenerated = viewModel::saveGeneratedBatch,
                        onProvisionVouchers = viewModel::provisionVouchers,
                        onClearVoucherResult = viewModel::clearVoucherProvisionResult
                    )
                }

                else -> {
                    ConnectionScreen(
                        connecting = state.connecting,
                        discovering = state.discoveringRouters,
                        discoveredRouters = state.discoveredRouters,
                        error = state.error,
                        arabic = arabic,
                        onLanguageToggle = { arabic = !arabic },
                        onDiscover = viewModel::discoverRouters,
                        onOfflineStudio = { offlineStudio = true },
                        onConnect = viewModel::connect,
                        onClearError = viewModel::clearError
                    )
                }
            }
        }
    }
}

@Composable
private fun ConnectionScreen(
    connecting: Boolean,
    discovering: Boolean,
    discoveredRouters: List<DiscoveredRouter>,
    error: String?,
    arabic: Boolean,
    onLanguageToggle: () -> Unit,
    onDiscover: () -> Unit,
    onOfflineStudio: () -> Unit,
    onConnect: (RouterConnectionSettings) -> Unit,
    onClearError: () -> Unit
) {
    var host by rememberSaveable { mutableStateOf("") }
    var username by rememberSaveable { mutableStateOf("admin") }
    var password by rememberSaveable { mutableStateOf("") }

    LaunchedEffect(Unit) {
        onDiscover()
    }

    LaunchedEffect(discoveredRouters) {
        if (host.isBlank() && discoveredRouters.size == 1) {
            host = discoveredRouters.single().ipAddress
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(FgBlack)
            .padding(16.dp),
        contentAlignment = Alignment.Center
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .widthIn(max = 620.dp),
            colors = CardDefaults.cardColors(containerColor = FgDeepNavy),
            border = BorderStroke(1.5.dp, FgBlue),
            shape = RoundedCornerShape(22.dp)
        ) {
            Column(
                modifier = Modifier.padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(13.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(54.dp)
                            .background(FgBlue.copy(alpha = 0.13f), RoundedCornerShape(15.dp)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            Icons.Outlined.Router,
                            contentDescription = null,
                            tint = FgMint,
                            modifier = Modifier.size(32.dp)
                        )
                    }

                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            "FG MTM",
                            color = FgMint,
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Black
                        )
                        Text(
                            "FG MikroTik Manager",
                            color = FgWhite,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    IconButton(onClick = onLanguageToggle) {
                        Icon(
                            Icons.Outlined.Language,
                            contentDescription = "Language",
                            tint = FgCyan
                        )
                    }
                }

                Text(
                    if (arabic) {
                        "التطبيق يبحث عن MikroTik تلقائيًا. اختر الراوتر ثم اكتب كلمة المرور فقط."
                    } else {
                        "The app finds MikroTik routers automatically. Select yours, then enter the password."
                    },
                    color = FgSilver
                )

                Button(
                    onClick = onDiscover,
                    enabled = !discovering && !connecting,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    if (discovering) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(18.dp),
                            strokeWidth = 2.dp
                        )
                    } else {
                        Icon(Icons.Outlined.Search, contentDescription = null)
                    }
                    Spacer(Modifier.width(8.dp))
                    Text(
                        if (discovering) {
                            if (arabic) "جاري البحث..." else "Searching..."
                        } else {
                            if (arabic) "اكتشاف الراوترات" else "Find routers"
                        }
                    )
                }

                if (discoveredRouters.isNotEmpty()) {
                    Column(verticalArrangement = Arrangement.spacedBy(7.dp)) {
                        Text(
                            if (arabic) "الراوترات الموجودة" else "Discovered routers",
                            color = FgMint,
                            fontWeight = FontWeight.Bold
                        )

                        discoveredRouters.take(6).forEach { router ->
                            OutlinedButton(
                                onClick = {
                                    host = router.ipAddress
                                    onClearError()
                                },
                                modifier = Modifier.fillMaxWidth(),
                                border = BorderStroke(
                                    1.2.dp,
                                    if (host == router.ipAddress) FgMint else FgSilverMuted
                                )
                            ) {
                                Icon(
                                    Icons.Outlined.Wifi,
                                    contentDescription = null,
                                    tint = if (host == router.ipAddress) FgMint else FgBlue
                                )
                                Spacer(Modifier.width(10.dp))
                                Column(
                                    modifier = Modifier.weight(1f),
                                    horizontalAlignment = Alignment.Start
                                ) {
                                    Text(
                                        router.identity.ifBlank { "MikroTik" },
                                        color = FgWhite,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Text(
                                        listOfNotNull(
                                            router.boardName.takeIf { it.isNotBlank() },
                                            router.ipAddress.takeIf { it.isNotBlank() }
                                        ).joinToString(" • "),
                                        color = FgSilverMuted,
                                        style = MaterialTheme.typography.bodySmall
                                    )
                                }
                            }
                        }
                    }
                }

                OutlinedTextField(
                    value = host,
                    onValueChange = {
                        host = it
                        onClearError()
                    },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text(if (arabic) "IP الراوتر" else "Router IP") },
                    placeholder = { Text("192.168.88.1") },
                    singleLine = true
                )

                OutlinedTextField(
                    value = username,
                    onValueChange = {
                        username = it
                        onClearError()
                    },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text(if (arabic) "اسم المستخدم" else "Username") },
                    singleLine = true
                )

                OutlinedTextField(
                    value = password,
                    onValueChange = {
                        password = it
                        onClearError()
                    },
                    modifier = Modifier.fillMaxWidth(),
                    label = {
                        Text(if (arabic) "كلمة مرور MikroTik" else "MikroTik password")
                    },
                    visualTransformation = PasswordVisualTransformation(),
                    singleLine = true
                )

                if (error != null) {
                    Text(
                        error,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodyMedium
                    )
                }

                Button(
                    onClick = {
                        onConnect(
                            RouterConnectionSettings(
                                host = host,
                                port = 8728,
                                username = username,
                                password = password,
                                protocol = RouterProtocol.AUTO
                            )
                        )
                    },
                    enabled = !connecting && host.isNotBlank() && username.isNotBlank(),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    if (connecting) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(18.dp),
                            strokeWidth = 2.dp
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(if (arabic) "جاري الاتصال..." else "Connecting...")
                    } else {
                        Text(if (arabic) "اتصال بالراوتر" else "Connect")
                    }
                }

                OutlinedButton(
                    onClick = onOfflineStudio,
                    modifier = Modifier.fillMaxWidth(),
                    border = BorderStroke(1.2.dp, FgPurple)
                ) {
                    Icon(
                        Icons.Outlined.CreditCard,
                        contentDescription = null,
                        tint = FgPurple
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        if (arabic) "إنشاء كروت بدون راوتر" else "Create vouchers offline"
                    )
                }
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
    onOpenAdminModule: (com.fgmachines.mikrotikmanager.data.RouterAdminModule) -> Unit,
    onRefreshAdminModule: () -> Unit,
    onCloseAdminModule: () -> Unit,
    onVoucherModeSelected: (com.fgmachines.mikrotikmanager.voucher.VoucherMode) -> Unit,
    onVoucherBatchGenerated: (com.fgmachines.mikrotikmanager.voucher.VoucherBatch) -> Unit,
    onProvisionVouchers: (com.fgmachines.mikrotikmanager.voucher.VoucherBatch) -> Unit,
    onClearVoucherResult: () -> Unit
) {
    val wide = LocalConfiguration.current.screenWidthDp >= 840
    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
    val scope = rememberCoroutineScope()

    val content: @Composable () -> Unit = {
        Scaffold(
            containerColor = FgBlack,
            topBar = {
                TopAppBar(
                    title = {
                        Column {
                            Text(
                                "FG MikroTik Manager",
                                color = FgWhite,
                                fontWeight = FontWeight.Black
                            )
                            Text(
                                sectionLabel(state.section, arabic),
                                color = FgMint,
                                style = MaterialTheme.typography.labelMedium
                            )
                        }
                    },
                    navigationIcon = {
                        if (!wide) {
                            IconButton(onClick = { scope.launch { drawerState.open() } }) {
                                Icon(
                                    Icons.Outlined.Menu,
                                    contentDescription = "Menu",
                                    tint = FgBlue
                                )
                            }
                        }
                    },
                    actions = {
                        IconButton(onClick = onLanguageToggle) {
                            Icon(
                                Icons.Outlined.Language,
                                contentDescription = "Language",
                                tint = FgCyan
                            )
                        }

                        if (state.section == AppSection.DASHBOARD) {
                            IconButton(onClick = onRefresh, enabled = !state.refreshing) {
                                if (state.refreshing) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(19.dp),
                                        strokeWidth = 2.dp,
                                        color = FgMint
                                    )
                                } else {
                                    Icon(
                                        Icons.Outlined.Refresh,
                                        contentDescription = "Refresh",
                                        tint = FgMint
                                    )
                                }
                            }
                        }
                    }
                )
            }
        ) { padding ->
            RouterSectionContent(
                state = state,
                arabic = arabic,
                onOpenAdminModule = onOpenAdminModule,
                onRefreshAdminModule = onRefreshAdminModule,
                onCloseAdminModule = onCloseAdminModule,
                onVoucherModeSelected = onVoucherModeSelected,
                onVoucherBatchGenerated = onVoucherBatchGenerated,
                onProvisionVouchers = onProvisionVouchers,
                onClearVoucherResult = onClearVoucherResult,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
            )
        }
    }

    if (wide) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .background(FgBlack)
        ) {
            AppMenuContent(
                state = state,
                arabic = arabic,
                onSection = onSection,
                onDisconnect = onDisconnect,
                modifier = Modifier
                    .width(280.dp)
                    .fillMaxHeight()
            )

            VerticalDivider(color = FgBlue.copy(alpha = 0.45f))

            Box(modifier = Modifier.weight(1f)) {
                content()
            }
        }
    } else {
        ModalNavigationDrawer(
            drawerState = drawerState,
            drawerContent = {
                ModalDrawerSheet(
                    drawerContainerColor = FgDeepNavy,
                    drawerContentColor = FgWhite
                ) {
                    AppMenuContent(
                        state = state,
                        arabic = arabic,
                        onSection = { section ->
                            onSection(section)
                            scope.launch { drawerState.close() }
                        },
                        onDisconnect = {
                            onDisconnect()
                            scope.launch { drawerState.close() }
                        },
                        modifier = Modifier
                            .width(300.dp)
                            .fillMaxHeight()
                    )
                }
            },
            content = content
        )
    }
}

@Composable
private fun AppMenuContent(
    state: RouterUiState,
    arabic: Boolean,
    onSection: (AppSection) -> Unit,
    onDisconnect: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .background(FgDeepNavy)
            .padding(horizontal = 12.dp, vertical = 16.dp)
    ) {
        Text(
            "FG MTM",
            color = FgMint,
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Black
        )
        Text(
            "FG MikroTik Manager",
            color = FgSilver,
            style = MaterialTheme.typography.bodyMedium
        )

        state.dashboard?.let { snapshot ->
            Text(
                snapshot.identity,
                modifier = Modifier.padding(top = 5.dp),
                color = FgCyan,
                style = MaterialTheme.typography.labelLarge
            )
        }

        HorizontalDivider(
            modifier = Modifier.padding(vertical = 14.dp),
            color = FgSilverMuted.copy(alpha = 0.32f)
        )

        AppSection.entries.forEach { section ->
            val accent = sectionAccent(section)
            NavigationDrawerItem(
                selected = state.section == section,
                onClick = { onSection(section) },
                icon = {
                    Icon(
                        sectionIcon(section),
                        contentDescription = null,
                        tint = accent
                    )
                },
                label = {
                    Text(
                        sectionLabel(section, arabic),
                        fontWeight = if (state.section == section) {
                            FontWeight.Bold
                        } else {
                            FontWeight.Medium
                        }
                    )
                },
                modifier = Modifier.padding(vertical = 3.dp)
            )
        }

        Spacer(Modifier.weight(1f))

        OutlinedButton(
            onClick = onDisconnect,
            modifier = Modifier.fillMaxWidth(),
            border = BorderStroke(1.2.dp, FgAmber)
        ) {
            Icon(
                Icons.Outlined.Logout,
                contentDescription = null,
                tint = FgAmber
            )
            Spacer(Modifier.width(8.dp))
            Text(
                if (arabic) "قطع الاتصال" else "Disconnect",
                color = FgAmber
            )
        }
    }
}

@Composable
private fun RouterSectionContent(
    state: RouterUiState,
    arabic: Boolean,
    onOpenAdminModule: (com.fgmachines.mikrotikmanager.data.RouterAdminModule) -> Unit,
    onRefreshAdminModule: () -> Unit,
    onCloseAdminModule: () -> Unit,
    onVoucherModeSelected: (com.fgmachines.mikrotikmanager.voucher.VoucherMode) -> Unit,
    onVoucherBatchGenerated: (com.fgmachines.mikrotikmanager.voucher.VoucherBatch) -> Unit,
    onProvisionVouchers: (com.fgmachines.mikrotikmanager.voucher.VoucherBatch) -> Unit,
    onClearVoucherResult: () -> Unit,
    modifier: Modifier
) {
    when (state.section) {
        AppSection.DASHBOARD -> {
            DashboardScreen(
                snapshot = state.dashboard,
                error = state.error,
                arabic = arabic,
                modifier = modifier
            )
        }

        AppSection.WINBOX -> {
            RouterAdminScreen(
                arabic = arabic,
                loading = state.adminLoading,
                selectedModule = state.adminModule,
                snapshot = state.adminSnapshot,
                error = state.adminError,
                onOpenModule = onOpenAdminModule,
                onRefresh = onRefreshAdminModule,
                onBack = onCloseAdminModule,
                modifier = modifier
            )
        }

        AppSection.VOUCHERS -> {
            VoucherStudioScreen(
                arabic = arabic,
                connected = true,
                profiles = state.voucherProfiles,
                profilesLoading = state.voucherProfilesLoading,
                provisioning = state.voucherProvisioning,
                provisionResult = state.voucherProvisionResult,
                historyCount = state.voucherHistoryCount,
                recentBatches = state.recentVoucherBatches,
                onModeSelected = onVoucherModeSelected,
                onBatchGenerated = onVoucherBatchGenerated,
                onProvision = onProvisionVouchers,
                onClearProvisionResult = onClearVoucherResult,
                modifier = modifier
            )
        }

        AppSection.ABOUT -> {
            AboutDeveloperScreen(
                arabic = arabic,
                modifier = modifier
            )
        }
    }
}

@Composable
private fun DashboardScreen(
    snapshot: DashboardSnapshot?,
    error: String?,
    arabic: Boolean,
    modifier: Modifier
) {
    if (snapshot == null) {
        Box(
            modifier = modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            CircularProgressIndicator(color = FgMint)
        }
        return
    }

    val metrics = listOf(
        (if (arabic) "اسم الراوتر" else "Router identity") to snapshot.identity,
        "RouterOS" to snapshot.version.ifBlank { "—" },
        (if (arabic) "الموديل" else "Board") to snapshot.boardName.ifBlank { "—" },
        (if (arabic) "المعمارية" else "Architecture") to snapshot.architecture.ifBlank { "—" },
        (if (arabic) "حمل المعالج" else "CPU load") to
            (snapshot.cpuLoadPercent?.let { "$it%" } ?: "—"),
        (if (arabic) "مدة التشغيل" else "Uptime") to snapshot.uptime.ifBlank { "—" },
        (if (arabic) "عدد الواجهات" else "Interfaces") to snapshot.interfaces.size.toString()
    )

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .background(FgBlack),
        contentPadding = PaddingValues(14.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        item {
            Text(
                if (arabic) "حالة الراوتر" else "Router status",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Black,
                color = FgMint
            )
        }

        if (error != null) {
            item {
                Text(
                    error,
                    color = MaterialTheme.colorScheme.error
                )
            }
        }

        items(metrics) { metric ->
            Card(
                colors = CardDefaults.cardColors(containerColor = FgPanel),
                border = BorderStroke(1.dp, FgBlue.copy(alpha = 0.45f)),
                shape = RoundedCornerShape(14.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 15.dp, vertical = 13.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        metric.first,
                        color = FgSilver,
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Text(
                        metric.second,
                        color = FgWhite,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }

        item { Spacer(Modifier.height(10.dp)) }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun OfflineVoucherShell(
    arabic: Boolean,
    historyCount: Int,
    recentBatches: List<com.fgmachines.mikrotikmanager.voucher.SavedVoucherBatch>,
    onLanguageToggle: () -> Unit,
    onBatchGenerated: (com.fgmachines.mikrotikmanager.voucher.VoucherBatch) -> Unit,
    onBack: () -> Unit
) {
    Scaffold(
        containerColor = FgBlack,
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            "FG MikroTik Manager",
                            color = FgWhite,
                            fontWeight = FontWeight.Black
                        )
                        Text(
                            if (arabic) "الكروت بدون راوتر" else "Offline vouchers",
                            color = FgMint,
                            style = MaterialTheme.typography.labelMedium
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            Icons.Outlined.ArrowBack,
                            contentDescription = "Back",
                            tint = FgMint
                        )
                    }
                },
                actions = {
                    IconButton(onClick = onLanguageToggle) {
                        Icon(
                            Icons.Outlined.Language,
                            contentDescription = "Language",
                            tint = FgCyan
                        )
                    }
                }
            )
        }
    ) { padding ->
        VoucherStudioScreen(
            arabic = arabic,
            connected = false,
            historyCount = historyCount,
            recentBatches = recentBatches,
            onBatchGenerated = onBatchGenerated,
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        )
    }
}

private fun sectionLabel(section: AppSection, arabic: Boolean): String =
    when (section) {
        AppSection.DASHBOARD -> if (arabic) "الرئيسية" else "Home"
        AppSection.WINBOX -> if (arabic) "إدارة الراوتر" else "Router Manager"
        AppSection.VOUCHERS -> if (arabic) "الكروت" else "Vouchers"
        AppSection.ABOUT -> if (arabic) "عن المطور" else "About developer"
    }

private fun sectionIcon(section: AppSection) =
    when (section) {
        AppSection.DASHBOARD -> Icons.Outlined.Dashboard
        AppSection.WINBOX -> Icons.Outlined.Router
        AppSection.VOUCHERS -> Icons.Outlined.CreditCard
        AppSection.ABOUT -> Icons.Outlined.Info
    }

private fun sectionAccent(section: AppSection) =
    when (section) {
        AppSection.DASHBOARD -> FgBlue
        AppSection.WINBOX -> FgMint
        AppSection.VOUCHERS -> FgPurple
        AppSection.ABOUT -> FgCyan
    }
