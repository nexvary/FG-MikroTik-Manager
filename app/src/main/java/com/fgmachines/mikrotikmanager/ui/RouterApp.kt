package com.fgmachines.mikrotikmanager.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Router
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Terminal
import androidx.compose.material.icons.outlined.Wifi
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.fgmachines.mikrotikmanager.command.ParsedRouterCommand
import com.fgmachines.mikrotikmanager.data.DashboardSnapshot
import com.fgmachines.mikrotikmanager.data.DiscoveredRouter
import com.fgmachines.mikrotikmanager.data.RouterConnectionSettings
import com.fgmachines.mikrotikmanager.data.RouterInterface
import com.fgmachines.mikrotikmanager.data.RouterProtocol
import com.fgmachines.mikrotikmanager.voucher.RouterVoucherProfile
import com.fgmachines.mikrotikmanager.voucher.VoucherMode
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
                offlineStudio -> OfflineVoucherShell(
                    arabic = arabic,
                    historyCount = state.voucherHistoryCount,
                    recentBatches = state.recentVoucherBatches,
                    onLanguageToggle = { arabic = !arabic },
                    onBatchGenerated = viewModel::saveGeneratedBatch,
                    onBack = { offlineStudio = false }
                )

                state.connected -> RouterShell(
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
                    onClearVoucherResult = viewModel::clearVoucherProvisionResult,
                    onRunCommands = viewModel::runCommands,
                    onClearCommandResults = viewModel::clearCommandResults
                )

                else -> ConnectionScreen(
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

@Composable
fun RouterDemoApp(screen: String) {
    val section = when (screen.lowercase(Locale.ENGLISH)) {
        "dashboard" -> AppSection.DASHBOARD
        "winbox", "commands" -> AppSection.WINBOX
        "vouchers" -> AppSection.VOUCHERS
        "about" -> AppSection.ABOUT
        else -> AppSection.MENU
    }

    val dashboard = DashboardSnapshot(
        identity = "MikroTik",
        version = "7.16.2 (stable)",
        boardName = "hAP ac²",
        architecture = "arm",
        cpuLoadPercent = 1,
        freeMemoryBytes = 185L * 1024L * 1024L,
        totalMemoryBytes = 256L * 1024L * 1024L,
        uptime = "4d23h49m15s",
        interfaces = listOf(
            RouterInterface("*1", "ether1", "ether", true, false, 214_490_000, 157_380_000),
            RouterInterface("*2", "ether2", "ether", false, false, 0, 0),
            RouterInterface("*3", "bridge", "bridge", true, false, 0, 0)
        )
    )

    val demoState = RouterUiState(
        connected = true,
        dashboard = dashboard,
        interfaces = dashboard.interfaces,
        section = section,
        voucherProfiles = mapOf(
            VoucherMode.HOTSPOT to listOf(
                RouterVoucherProfile("default"),
                RouterVoucherProfile("Kids-60m", rateLimit = "5M/5M"),
                RouterVoucherProfile("Kids-120m", rateLimit = "8M/8M")
            )
        )
    )

    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
        FgMikroTikTheme {
            RouterShell(
                state = demoState,
                arabic = true,
                onLanguageToggle = {},
                onRefresh = {},
                onDisconnect = {},
                onSection = {},
                onOpenAdminModule = {},
                onRefreshAdminModule = {},
                onCloseAdminModule = {},
                onVoucherModeSelected = {},
                onVoucherBatchGenerated = {},
                onProvisionVouchers = {},
                onClearVoucherResult = {},
                onRunCommands = {},
                onClearCommandResults = {},
                commandCenterInitiallyOpen = screen.equals("commands", ignoreCase = true),
                commandCenterInitialText = "/ip address print\n/ip service disable telnet"
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
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

    LaunchedEffect(Unit) { onDiscover() }
    LaunchedEffect(discoveredRouters) {
        if (host.isBlank() && discoveredRouters.size == 1) {
            host = discoveredRouters.single().ipAddress
        }
    }

    Scaffold(
        containerColor = FgBlack,
        topBar = {
            TopAppBar(
                colors = TopAppBarDefaults.topAppBarColors(containerColor = FgDeepNavy),
                title = {
                    Column {
                        Text(
                            "FG MikroTik Manager",
                            color = FgWhite,
                            fontWeight = FontWeight.Black
                        )
                        Text(
                            "FG MTM",
                            color = FgMint,
                            style = MaterialTheme.typography.labelMedium
                        )
                    }
                },
                actions = {
                    IconButton(onClick = onLanguageToggle) {
                        Icon(Icons.Outlined.Language, contentDescription = "Language", tint = FgCyan)
                    }
                }
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .background(FgBlack),
            contentPadding = PaddingValues(14.dp),
            verticalArrangement = Arrangement.spacedBy(11.dp)
        ) {
            item {
                Text(
                    if (arabic) "اتصال بالراوتر" else "Connect to router",
                    color = FgMint,
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Black
                )
                Text(
                    if (arabic) {
                        "اختر الراوتر الموجود على الشبكة ثم اكتب كلمة المرور."
                    } else {
                        "Select a discovered router, then enter its password."
                    },
                    color = FgSilver
                )
            }

            item {
                Button(
                    onClick = onDiscover,
                    enabled = !discovering && !connecting,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    if (discovering) {
                        CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
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
            }

            if (discoveredRouters.isNotEmpty()) {
                item {
                    Text(
                        if (arabic) "الراوترات الموجودة" else "Discovered routers",
                        color = FgMint,
                        fontWeight = FontWeight.Bold
                    )
                }

                items(discoveredRouters.take(6)) { router ->
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                host = router.ipAddress
                                onClearError()
                            },
                        colors = CardDefaults.cardColors(containerColor = FgPanel),
                        border = BorderStroke(
                            1.5.dp,
                            if (host == router.ipAddress) FgMint else FgBlue.copy(alpha = 0.65f)
                        ),
                        shape = RoundedCornerShape(16.dp)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(14.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Icon(
                                Icons.Outlined.Wifi,
                                contentDescription = null,
                                tint = if (host == router.ipAddress) FgMint else FgBlue,
                                modifier = Modifier.size(30.dp)
                            )
                            Column(modifier = Modifier.weight(1f)) {
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
                                    color = FgSilverMuted
                                )
                            }
                        }
                    }
                }
            }

            item {
                OutlinedTextField(
                    value = host,
                    onValueChange = { host = it; onClearError() },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text(if (arabic) "IP الراوتر" else "Router IP") },
                    placeholder = { Text("192.168.88.1") },
                    singleLine = true
                )
            }

            item {
                OutlinedTextField(
                    value = username,
                    onValueChange = { username = it; onClearError() },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text(if (arabic) "اسم المستخدم" else "Username") },
                    singleLine = true
                )
            }

            item {
                OutlinedTextField(
                    value = password,
                    onValueChange = { password = it; onClearError() },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text(if (arabic) "كلمة مرور MikroTik" else "MikroTik password") },
                    visualTransformation = PasswordVisualTransformation(),
                    singleLine = true
                )
            }

            error?.let { message ->
                item {
                    Text(
                        message,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            }

            item {
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
                        CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                        Spacer(Modifier.width(8.dp))
                    }
                    Text(if (arabic) "اتصال بالراوتر" else "Connect")
                }
            }

            item {
                OutlinedButton(
                    onClick = onOfflineStudio,
                    modifier = Modifier.fillMaxWidth(),
                    border = BorderStroke(1.2.dp, FgPurple)
                ) {
                    Icon(Icons.Outlined.CreditCard, contentDescription = null, tint = FgPurple)
                    Spacer(Modifier.width(8.dp))
                    Text(
                        if (arabic) "إنشاء كروت بدون راوتر" else "Create vouchers offline",
                        color = FgPurple
                    )
                }
            }

            item { Spacer(Modifier.height(8.dp)) }
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
    onVoucherModeSelected: (VoucherMode) -> Unit,
    onVoucherBatchGenerated: (com.fgmachines.mikrotikmanager.voucher.VoucherBatch) -> Unit,
    onProvisionVouchers: (com.fgmachines.mikrotikmanager.voucher.VoucherBatch) -> Unit,
    onClearVoucherResult: () -> Unit,
    onRunCommands: (List<ParsedRouterCommand>) -> Unit,
    onClearCommandResults: () -> Unit,
    commandCenterInitiallyOpen: Boolean = false,
    commandCenterInitialText: String = ""
) {
    val wide = LocalConfiguration.current.screenWidthDp >= 840
    var commandCenterOpen by rememberSaveable { mutableStateOf(commandCenterInitiallyOpen) }

    val goBack: () -> Unit = {
        if (state.section == AppSection.WINBOX && state.adminModule != null) {
            onCloseAdminModule()
        } else {
            onSection(AppSection.MENU)
        }
    }

    BackHandler(enabled = state.section != AppSection.MENU || state.adminModule != null) {
        goBack()
    }

    if (wide) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .background(FgBlack)
        ) {
            PersistentMainMenu(
                state = state,
                arabic = arabic,
                onSection = onSection,
                onDisconnect = onDisconnect,
                modifier = Modifier
                    .width(280.dp)
                    .fillMaxHeight()
            )
            VerticalDivider(color = FgBlue.copy(alpha = 0.35f))
            RouterContent(
                state = state,
                arabic = arabic,
                onRefresh = onRefresh,
                onOpenAdminModule = onOpenAdminModule,
                onRefreshAdminModule = onRefreshAdminModule,
                onCloseAdminModule = onCloseAdminModule,
                onVoucherModeSelected = onVoucherModeSelected,
                onVoucherBatchGenerated = onVoucherBatchGenerated,
                onProvisionVouchers = onProvisionVouchers,
                onClearVoucherResult = onClearVoucherResult,
                modifier = Modifier.weight(1f)
            )

            if (state.section == AppSection.WINBOX) {
                FloatingActionButton(
                    onClick = { commandCenterOpen = true },
                    containerColor = FgMint,
                    contentColor = FgBlack,
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(20.dp)
                ) {
                    Icon(Icons.Outlined.Terminal, contentDescription = "Command Center")
                }
            }
        }
        return
    }

    Scaffold(
        containerColor = FgBlack,
        floatingActionButton = {
            if (state.section == AppSection.WINBOX) {
                FloatingActionButton(
                    onClick = { commandCenterOpen = true },
                    containerColor = FgMint,
                    contentColor = FgBlack
                ) {
                    Icon(Icons.Outlined.Terminal, contentDescription = "Command Center")
                }
            }
        },
        topBar = {
            TopAppBar(
                colors = TopAppBarDefaults.topAppBarColors(containerColor = FgDeepNavy),
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
                    if (state.section != AppSection.MENU || state.adminModule != null) {
                        IconButton(onClick = goBack) {
                            Icon(
                                Icons.Outlined.ArrowBack,
                                contentDescription = "Back",
                                tint = FgMint
                            )
                        }
                    }
                },
                actions = {
                    if (state.section == AppSection.DASHBOARD) {
                        IconButton(onClick = onRefresh, enabled = !state.refreshing) {
                            if (state.refreshing) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(20.dp),
                                    strokeWidth = 2.dp,
                                    color = FgMint
                                )
                            } else {
                                Icon(Icons.Outlined.Refresh, contentDescription = "Refresh", tint = FgBlue)
                            }
                        }
                    }
                    IconButton(onClick = onLanguageToggle) {
                        Icon(Icons.Outlined.Language, contentDescription = "Language", tint = FgCyan)
                    }
                }
            )
        }
    ) { padding ->
        RouterContent(
            state = state,
            arabic = arabic,
            onRefresh = onRefresh,
            onOpenAdminModule = onOpenAdminModule,
            onRefreshAdminModule = onRefreshAdminModule,
            onCloseAdminModule = onCloseAdminModule,
            onVoucherModeSelected = onVoucherModeSelected,
            onVoucherBatchGenerated = onVoucherBatchGenerated,
            onProvisionVouchers = onProvisionVouchers,
            onClearVoucherResult = onClearVoucherResult,
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            onSection = onSection,
            onDisconnect = onDisconnect
        )
    }

    if (commandCenterOpen) {
        CommandCenterSheet(
            arabic = arabic,
            running = state.commandRunning,
            results = state.commandResults,
            history = state.commandHistory,
            onRun = onRunCommands,
            onClearResults = onClearCommandResults,
            onDismiss = { commandCenterOpen = false },
            initialText = commandCenterInitialText
        )
    }
}

@Composable
private fun RouterContent(
    state: RouterUiState,
    arabic: Boolean,
    onRefresh: () -> Unit,
    onOpenAdminModule: (com.fgmachines.mikrotikmanager.data.RouterAdminModule) -> Unit,
    onRefreshAdminModule: () -> Unit,
    onCloseAdminModule: () -> Unit,
    onVoucherModeSelected: (VoucherMode) -> Unit,
    onVoucherBatchGenerated: (com.fgmachines.mikrotikmanager.voucher.VoucherBatch) -> Unit,
    onProvisionVouchers: (com.fgmachines.mikrotikmanager.voucher.VoucherBatch) -> Unit,
    onClearVoucherResult: () -> Unit,
    modifier: Modifier,
    onSection: (AppSection) -> Unit = {},
    onDisconnect: () -> Unit = {}
) {
    when (state.section) {
        AppSection.MENU -> MainMenuScreen(
            state = state,
            arabic = arabic,
            onSection = onSection,
            onDisconnect = onDisconnect,
            modifier = modifier
        )

        AppSection.DASHBOARD -> DashboardScreen(
            snapshot = state.dashboard,
            error = state.error,
            arabic = arabic,
            modifier = modifier
        )

        AppSection.WINBOX -> RouterAdminScreen(
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

        AppSection.VOUCHERS -> VoucherStudioScreen(
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

        AppSection.ABOUT -> AboutDeveloperScreen(
            arabic = arabic,
            modifier = modifier
        )
    }
}

@Composable
private fun MainMenuScreen(
    state: RouterUiState,
    arabic: Boolean,
    onSection: (AppSection) -> Unit,
    onDisconnect: () -> Unit,
    modifier: Modifier = Modifier
) {
    val items = listOf(
        MenuEntry(
            AppSection.DASHBOARD,
            if (arabic) "حالة الراوتر" else "Router status",
            if (arabic) "الموديل، RouterOS، المعالج، الذاكرة والواجهات" else "Model, RouterOS, CPU, memory and interfaces",
            Icons.Outlined.Dashboard,
            FgBlue
        ),
        MenuEntry(
            AppSection.WINBOX,
            if (arabic) "إدارة الراوتر" else "Router Manager",
            if (arabic) "واجهة WinBox: Wi-Fi، Firewall، DHCP، IP، PPP والمزيد" else "WinBox-style access to Wi-Fi, Firewall, DHCP, IP, PPP and more",
            Icons.Outlined.Router,
            FgMint
        ),
        MenuEntry(
            AppSection.VOUCHERS,
            if (arabic) "الكروت" else "Vouchers",
            if (arabic) "إنشاء وتفعيل الكروت وتحديد المدة والسعر والانتهاء" else "Create and activate vouchers with duration, price and expiry",
            Icons.Outlined.CreditCard,
            FgPurple
        ),
        MenuEntry(
            AppSection.ABOUT,
            if (arabic) "عن المطور" else "About developer",
            if (arabic) "FG Machines ومعلومات المطور والروابط" else "FG Machines, developer information and links",
            Icons.Outlined.Info,
            FgCyan
        )
    )

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .background(FgBlack),
        contentPadding = PaddingValues(14.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = FgDeepNavy),
                border = BorderStroke(1.4.dp, FgSilverMuted),
                shape = RoundedCornerShape(18.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text(
                        state.dashboard?.identity ?: "MikroTik",
                        color = FgWhite,
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Black
                    )
                    Text(
                        listOfNotNull(
                            state.dashboard?.boardName?.takeIf { it.isNotBlank() },
                            state.dashboard?.version?.takeIf { it.isNotBlank() }
                        ).joinToString(" • "),
                        color = FgSilver
                    )
                    Text(
                        if (arabic) "اختر القسم المطلوب" else "Choose a section",
                        color = FgMint,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }

        items(items) { entry ->
            MainMenuRow(entry = entry, onClick = { onSection(entry.section) })
        }

        item {
            OutlinedButton(
                onClick = onDisconnect,
                modifier = Modifier.fillMaxWidth(),
                border = BorderStroke(1.2.dp, FgAmber)
            ) {
                Icon(Icons.Outlined.Logout, contentDescription = null, tint = FgAmber)
                Spacer(Modifier.width(8.dp))
                Text(if (arabic) "قطع الاتصال" else "Disconnect", color = FgAmber)
            }
        }

        item { Spacer(Modifier.height(8.dp)) }
    }
}

private data class MenuEntry(
    val section: AppSection,
    val title: String,
    val subtitle: String,
    val icon: ImageVector,
    val accent: Color
)

@Composable
private fun MainMenuRow(
    entry: MenuEntry,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        colors = CardDefaults.cardColors(containerColor = FgPanel),
        border = BorderStroke(1.4.dp, entry.accent.copy(alpha = 0.92f)),
        shape = RoundedCornerShape(16.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .background(entry.accent.copy(alpha = 0.12f), RoundedCornerShape(14.dp)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    entry.icon,
                    contentDescription = null,
                    tint = entry.accent,
                    modifier = Modifier.size(29.dp)
                )
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    entry.title,
                    color = FgWhite,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    entry.subtitle,
                    color = FgSilverMuted,
                    style = MaterialTheme.typography.bodySmall
                )
            }
            Text(
                "›",
                color = entry.accent,
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Black
            )
        }
    }
}

@Composable
private fun PersistentMainMenu(
    state: RouterUiState,
    arabic: Boolean,
    onSection: (AppSection) -> Unit,
    onDisconnect: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .background(FgDeepNavy)
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text("FG MTM", color = FgMint, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Black)
        Text("FG MikroTik Manager", color = FgSilver)
        Text(state.dashboard?.identity ?: "MikroTik", color = FgCyan)

        HorizontalDivider(color = FgSilverMuted.copy(alpha = 0.28f))

        listOf(
            AppSection.DASHBOARD,
            AppSection.WINBOX,
            AppSection.VOUCHERS,
            AppSection.ABOUT
        ).forEach { section ->
            val selected = state.section == section
            val accent = sectionAccent(section)
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onSection(section) },
                colors = CardDefaults.cardColors(
                    containerColor = if (selected) accent.copy(alpha = 0.12f) else FgPanel
                ),
                border = BorderStroke(1.dp, if (selected) accent else FgSilverMuted.copy(alpha = 0.35f)),
                shape = RoundedCornerShape(12.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(11.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Icon(sectionIcon(section), contentDescription = null, tint = accent)
                    Text(sectionLabel(section, arabic), color = FgWhite, fontWeight = FontWeight.Bold)
                }
            }
        }

        Spacer(Modifier.weight(1f))

        OutlinedButton(
            onClick = onDisconnect,
            modifier = Modifier.fillMaxWidth(),
            border = BorderStroke(1.dp, FgAmber)
        ) {
            Icon(Icons.Outlined.Logout, contentDescription = null, tint = FgAmber)
            Spacer(Modifier.width(6.dp))
            Text(if (arabic) "قطع الاتصال" else "Disconnect", color = FgAmber)
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
        Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator(color = FgMint)
        }
        return
    }

    val metrics = listOf(
        (if (arabic) "اسم الراوتر" else "Router identity") to snapshot.identity,
        "RouterOS" to snapshot.version.ifBlank { "—" },
        (if (arabic) "الموديل" else "Board") to snapshot.boardName.ifBlank { "—" },
        (if (arabic) "المعمارية" else "Architecture") to snapshot.architecture.ifBlank { "—" },
        (if (arabic) "حمل المعالج" else "CPU load") to (snapshot.cpuLoadPercent?.let { "$it%" } ?: "—"),
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
                color = FgMint,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Black
            )
        }

        error?.let { message ->
            item { Text(message, color = MaterialTheme.colorScheme.error) }
        }

        items(metrics) { metric ->
            Card(
                colors = CardDefaults.cardColors(containerColor = FgPanel),
                border = BorderStroke(1.dp, FgBlue.copy(alpha = 0.55f)),
                shape = RoundedCornerShape(14.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 15.dp, vertical = 13.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(metric.first, color = FgSilver)
                    Text(metric.second, color = FgWhite, fontWeight = FontWeight.Bold)
                }
            }
        }

        item { Spacer(Modifier.height(8.dp)) }
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
                colors = TopAppBarDefaults.topAppBarColors(containerColor = FgDeepNavy),
                title = {
                    Column {
                        Text("FG MikroTik Manager", color = FgWhite, fontWeight = FontWeight.Black)
                        Text(
                            if (arabic) "الكروت بدون راوتر" else "Offline vouchers",
                            color = FgMint,
                            style = MaterialTheme.typography.labelMedium
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Outlined.ArrowBack, contentDescription = "Back", tint = FgMint)
                    }
                },
                actions = {
                    IconButton(onClick = onLanguageToggle) {
                        Icon(Icons.Outlined.Language, contentDescription = "Language", tint = FgCyan)
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
        AppSection.MENU -> if (arabic) "القائمة الرئيسية" else "Main menu"
        AppSection.DASHBOARD -> if (arabic) "حالة الراوتر" else "Router status"
        AppSection.WINBOX -> if (arabic) "إدارة الراوتر" else "Router Manager"
        AppSection.VOUCHERS -> if (arabic) "الكروت" else "Vouchers"
        AppSection.ABOUT -> if (arabic) "عن المطور" else "About developer"
    }

private fun sectionIcon(section: AppSection): ImageVector =
    when (section) {
        AppSection.MENU -> Icons.Outlined.Settings
        AppSection.DASHBOARD -> Icons.Outlined.Dashboard
        AppSection.WINBOX -> Icons.Outlined.Router
        AppSection.VOUCHERS -> Icons.Outlined.CreditCard
        AppSection.ABOUT -> Icons.Outlined.Info
    }

private fun sectionAccent(section: AppSection): Color =
    when (section) {
        AppSection.MENU -> FgSilver
        AppSection.DASHBOARD -> FgBlue
        AppSection.WINBOX -> FgMint
        AppSection.VOUCHERS -> FgPurple
        AppSection.ABOUT -> FgCyan
    }
