package com.fgmachines.mikrotikmanager.ui

import androidx.activity.compose.BackHandler
import androidx.activity.compose.LocalOnBackPressedDispatcherOwner
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.statusBarsPadding
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
import androidx.compose.material.icons.outlined.ConfirmationNumber
import androidx.compose.material.icons.outlined.Dashboard
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material.icons.outlined.People
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Language
import androidx.compose.material.icons.outlined.Logout
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Router
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Security
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Terminal
import androidx.compose.material.icons.outlined.Wifi
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.OutlinedTextFieldDefaults
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
import com.fgmachines.mikrotikmanager.data.RouterAdminGroup
import com.fgmachines.mikrotikmanager.data.RouterAdminModule
import com.fgmachines.mikrotikmanager.data.RouterMenuSnapshot
import com.fgmachines.mikrotikmanager.data.RouterConnectionSettings
import com.fgmachines.mikrotikmanager.data.RouterInterface
import com.fgmachines.mikrotikmanager.data.RouterProtocol
import com.fgmachines.mikrotikmanager.voucher.RouterVoucherProfile
import com.fgmachines.mikrotikmanager.voucher.VoucherMode
import java.util.Locale

@Composable
fun RouterApp(viewModel: RouterViewModel = viewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val accessLanguage=LocalAccessLanguage.current
    var fallbackArabic by rememberSaveable {
        mutableStateOf(Locale.getDefault().language.equals("ar", ignoreCase = true))
    }
    val arabic=accessLanguage?.first ?: fallbackArabic
    fun toggleLanguage(){val next=!arabic;if(accessLanguage==null)fallbackArabic=next else accessLanguage.second(next)}
    var offlineStudio by rememberSaveable { mutableStateOf(false) }
    var businessOpen by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(businessOpen) { if(!businessOpen) viewModel.refreshArchive() }

    CompositionLocalProvider(
        LocalLayoutDirection provides if (arabic) LayoutDirection.Rtl else LayoutDirection.Ltr
    ) {
        FgMikroTikTheme {
            when {
                businessOpen -> BusinessScreen(arabic, { businessOpen=false }, { toggleLanguage() },router=viewModel.businessRouter)
                offlineStudio -> OfflineVoucherShell(
                    arabic = arabic,
                    historyCount = state.voucherHistoryCount,
                    recentBatches = state.recentVoucherBatches,
                    onLanguageToggle = { toggleLanguage() },
                    onBatchGenerated = viewModel::saveGeneratedBatch,
                    onBack = { offlineStudio = false }
                )

                state.connected -> RouterShell(
                    state = state,
                    hotspotManager = viewModel.hotspotManager,
                    advancedManager = viewModel.advancedManager,
                    arabic = arabic,
                    onLanguageToggle = { toggleLanguage() },
                    onRefresh = viewModel::refresh,
                    onDisconnect = viewModel::disconnect,
                    onSection = { if(it==AppSection.BUSINESS) businessOpen=true else viewModel.selectSection(it) },
                    onOpenAdminModule = viewModel::openAdminModule,
                    onRefreshAdminModule = viewModel::refreshAdminModule,
                    onCloseAdminModule = viewModel::closeAdminModule,
                    onVoucherModeSelected = viewModel::refreshVoucherProfiles,
                    onVoucherBatchGenerated = viewModel::saveGeneratedBatch,
                    onProvisionVouchers = viewModel::provisionVouchers,
                    onClearVoucherResult = viewModel::clearVoucherProvisionResult,
                    onRunCommands = viewModel::runCommands,
                    onClearCommandResults = viewModel::clearCommandResults,
                    onCreateAdminItem = viewModel::createAdminItem,
                    onCreateRouterAdmin = viewModel::createRouterAdmin,
                    onUpdateAdminItem = viewModel::updateAdminItem,
                    onToggleAdminItem = viewModel::setAdminItemEnabled,
                    onRemoveAdminItem = viewModel::removeAdminItem,
                    onClearAdminActionMessage = viewModel::clearAdminActionMessage
                )

                else -> ConnectionScreen(
                    connecting = state.connecting,
                    discovering = state.discoveringRouters,
                    discoveredRouters = state.discoveredRouters,
                    error = state.error,
                    arabic = arabic,
                    onLanguageToggle = { toggleLanguage() },
                    onDiscover = viewModel::discoverRouters,
                    onOfflineStudio = { offlineStudio = true },
                    onConnect = viewModel::connect,
                    onClearError = viewModel::clearError,
                    onBusiness = { businessOpen=true }
                )
            }
        }
    }
}

@Composable
fun RouterDemoApp(screen: String) {
    if(screen in setOf("command-library", "voucher-share")) {
        CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
            FgMikroTikTheme {
                if(screen == "command-library") CommandLibraryDialog(true, {}, {})
                else VoucherShareDialog(listOf(com.fgmachines.mikrotikmanager.voucher.VoucherDraft(
                    "123456", "123456", "6h", "all", "", "6h", 524288000,
                    branding=com.fgmachines.mikrotikmanager.voucher.VoucherBranding(networkName="FG Machines WiFi",portalLoginUrl="http://192.168.10.1/login"),
                    durationValue=6, durationUnit=com.fgmachines.mikrotikmanager.voucher.VoucherTimeUnit.HOURS)),true,true,{})
            }
        }
        return
    }
    if (screen == "login" || screen == "login-en") {
        val previewArabic = screen != "login-en"
        CompositionLocalProvider(LocalLayoutDirection provides if(previewArabic) LayoutDirection.Rtl else LayoutDirection.Ltr) {
            FgMikroTikTheme {
                ConnectionScreen(false, false, listOf(DiscoveredRouter("MikroTik", "192.168.1.104")), null, previewArabic, {}, {}, {}, {}, {})
            }
        }
        return
    }
    if (screen in setOf("active-vouchers", "portal-login", "portal-status", "portal-design")) {
        CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
            FgMikroTikTheme { HotspotToolsScreen(true, null, emptyList(), if(screen == "active-vouchers") "active" else if(screen == "portal-design") "design" else screen, {}, demo = true) }
        }
        return
    }
    val section = when (screen.lowercase(Locale.ENGLISH)) {
        "dashboard", "advanced", "readiness", "doctor", "wizard", "repair", "backup" -> AppSection.ADVANCED
        "winbox", "network", "routes" -> AppSection.NETWORK
        "system", "commands", "admin-users" -> AppSection.SYSTEM
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

    val demoRoutes = screen.equals("routes", ignoreCase = true)
    val demoUsers = screen.equals("admin-users", ignoreCase = true)
    val demoState = RouterUiState(
        connected = true,
        dashboard = dashboard,
        interfaces = dashboard.interfaces,
        section = section,
        adminModule = if (demoUsers) RouterAdminModule.USERS else if (demoRoutes) RouterAdminModule.ROUTES else null,
        adminSnapshot = if (demoUsers) {
            RouterMenuSnapshot(
                module = RouterAdminModule.USERS,
                menuPath = "user",
                rows = listOf(
                    mapOf(
                        ".id" to "*1",
                        "name" to "admin",
                        "group" to "full",
                        "disabled" to "false",
                        "comment" to "Main administrator"
                    ),
                    mapOf(
                        ".id" to "*2",
                        "name" to "support",
                        "group" to "read",
                        "disabled" to "true",
                        "comment" to "Support account"
                    )
                )
            )
        } else if (demoRoutes) {
            RouterMenuSnapshot(RouterAdminModule.ROUTES, "ip/route", listOf(
                mapOf(".id" to "*1", "dst-address" to "0.0.0.0/0", "gateway" to "192.168.88.1", "distance" to "1", "disabled" to "false"),
                mapOf(".id" to "*2", "dst-address" to "10.20.0.0/24", "gateway" to "192.168.88.2", "distance" to "2", "disabled" to "true")
            ))
        } else {
            null
        },
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
                onCreateAdminItem = {},
                onCreateRouterAdmin = { _, _, _, _ -> },
                onUpdateAdminItem = { _, _ -> },
                onToggleAdminItem = { _, _ -> },
                onRemoveAdminItem = {},
                onClearAdminActionMessage = {},
                commandCenterInitiallyOpen = screen.equals("commands", ignoreCase = true),
                commandCenterInitialText = "/ip address print\n/ip service disable telnet",
                initialAdvancedPanel = if(screen in setOf("readiness","doctor","wizard","repair","backup")) screen else "home",
                advancedDemo = true
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
    onClearError: () -> Unit,
    onBusiness: () -> Unit = {}
) {
    val canArchive=com.fgmachines.mikrotikmanager.business.BusinessPermission.VOUCHERS in LocalBusinessPermissions.current
    var host by rememberSaveable { mutableStateOf("") }
    var username by rememberSaveable { mutableStateOf("admin") }
    var password by androidx.compose.runtime.remember { mutableStateOf("") }
    var advancedConnection by rememberSaveable { mutableStateOf(false) }
    var protocolName by rememberSaveable { mutableStateOf(RouterProtocol.AUTO.name) }
    var portText by rememberSaveable { mutableStateOf("8728") }
    val protocol = RouterProtocol.valueOf(protocolName)
    val port = if (protocol == RouterProtocol.AUTO) protocol.defaultPort else portText.toIntOrNull()
    val validPort = port != null && port in 1..65535
    var profilesOpen by rememberSaveable { mutableStateOf(false) }
    if(profilesOpen) RouterProfilesDialog(arabic,
        current=if(host.isNotBlank() && username.isNotBlank() && validPort) RouterConnectionSettings(host,port!!,username,"",protocol) else null,
        onSelect={ profile -> host=profile.host;username=profile.username;password="";protocolName=profile.protocol.name;portText=profile.port.toString();advancedConnection=profile.protocol!=RouterProtocol.AUTO;profilesOpen=false;onClearError() },
        onDismiss={profilesOpen=false})
    val keyboard = LocalSoftwareKeyboardController.current
    fun connect() {
        if (connecting || host.isBlank() || username.isBlank() || !validPort) return
        keyboard?.hide()
        onConnect(RouterConnectionSettings(host = host, port = port!!, username = username,
            password = password, protocol = protocol))
    }

    LaunchedEffect(Unit) { onDiscover() }
    LaunchedEffect(discoveredRouters) {
        if (host.isBlank() && discoveredRouters.size == 1) {
            host = discoveredRouters.single().ipAddress
        }
    }

    Scaffold(
        containerColor = FgBlack,
        topBar = {
            CompactAppHeader(
                subtitle = if (arabic) "اتصال بالراوتر" else "Connect",
                showBack = false,
                onBack = {},
                onLanguageToggle = onLanguageToggle
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .consumeWindowInsets(padding)
                .imePadding()
                .background(FgBlack),
            contentPadding = PaddingValues(14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            item {
                Text(
                    if (arabic) {
                        "اختر الراوتر الموجود على الشبكة ثم اكتب كلمة المرور."
                    } else {
                        "Select a discovered router, then enter its password."
                    },
                    color = FgSilver,
                    style = MaterialTheme.typography.bodySmall
                )
            }

            item {
                Button(
                    onClick = onDiscover,
                    colors = ButtonDefaults.buttonColors(containerColor = FgRoyalBlue, contentColor = FgWhite),
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

            item { OutlinedButton(onClick={profilesOpen=true},enabled=!connecting,modifier=Modifier.fillMaxWidth()) { Text(if(arabic) "مركز الراوترات" else "Router center") } }

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
                    colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = FgMetalSilver, unfocusedBorderColor = FgMetalSilver, focusedLabelColor = FgSilver, cursorColor = FgBlue),
                    textStyle = MaterialTheme.typography.bodyLarge.copy(textDirection = androidx.compose.ui.text.style.TextDirection.Ltr),
                    value = host,
                    onValueChange = { host = it; onClearError() },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text(if (arabic) "عنوان الراوتر أو اسم النطاق" else "Router IP or hostname") },
                    placeholder = { Text("192.168.88.1") },
                    singleLine = true
                )
            }

            item {
                OutlinedTextField(
                    colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = FgMetalSilver, unfocusedBorderColor = FgMetalSilver, focusedLabelColor = FgSilver, cursorColor = FgBlue),
                    textStyle = MaterialTheme.typography.bodyLarge.copy(textDirection = androidx.compose.ui.text.style.TextDirection.Ltr),
                    value = username,
                    onValueChange = { username = it; onClearError() },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text(if (arabic) "اسم المستخدم" else "Username") },
                    singleLine = true
                )
            }

            item {
                OutlinedTextField(
                    colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = FgMetalSilver, unfocusedBorderColor = FgMetalSilver, focusedLabelColor = FgSilver, cursorColor = FgBlue),
                    value = password,
                    onValueChange = { password = it; onClearError() },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text(if (arabic) "كلمة مرور MikroTik" else "MikroTik password") },
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = { connect() }),
                    singleLine = true
                )
            }

            item {
                OutlinedButton(
                    onClick = { advancedConnection = !advancedConnection },
                    enabled = !connecting,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(if (arabic) "إعدادات الاتصال • ${protocol.name}" else "Connection settings • ${protocol.name}")
                }
            }
            if (advancedConnection) {
                item {
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        listOf(RouterProtocol.AUTO, RouterProtocol.API, RouterProtocol.API_SSL, RouterProtocol.REST_HTTPS).forEach { option ->
                            androidx.compose.material3.FilterChip(
                                selected = protocol == option,
                                onClick = {
                                    protocolName = option.name
                                    portText = option.defaultPort.toString()
                                    onClearError()
                                },
                                enabled = !connecting,
                                label = { Text(when (option) {
                                    RouterProtocol.AUTO -> if (arabic) "تلقائي • API ثم HTTPS" else "Automatic • API then HTTPS"
                                    RouterProtocol.API -> if (arabic) "API • شبكة محلية موثوقة" else "API • trusted local network"
                                    RouterProtocol.API_SSL -> "API-SSL • TLS"
                                    else -> "REST • HTTPS"
                                }) },
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                        if (protocol != RouterProtocol.AUTO) {
                            OutlinedTextField(
                                value = portText,
                                onValueChange = { portText = it; onClearError() },
                                enabled = !connecting,
                                modifier = Modifier.fillMaxWidth(),
                                label = { Text(if (arabic) "المنفذ" else "Port") },
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                singleLine = true,
                                isError = !validPort,
                                supportingText = { if (!validPort) Text(if (arabic) "اكتب رقمًا من 1 إلى 65535" else "Enter a number from 1 to 65535") }
                            )
                        }
                        Text(
                            if (protocol == RouterProtocol.API_SSL || protocol == RouterProtocol.REST_HTTPS) {
                                if (arabic) "يلزم شهادة موثوقة تطابق عنوان الراوتر. لن يتحول الاتصال المشفر إلى اتصال غير مشفر عند الفشل."
                                else "Requires a trusted certificate matching the router address. Secure connections do not fall back to plaintext."
                            } else {
                                if (arabic) "API العادي غير مشفر؛ استخدمه على شبكة موثوقة أو VPN. اختر API-SSL للاتصال المشفر."
                                else "Plain API is unencrypted; use a trusted network or VPN. Select API-SSL for encryption."
                            },
                            color = FgSilver, style = MaterialTheme.typography.bodySmall
                        )
                    }
                }
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
                    onClick = { connect() },
                    colors = ButtonDefaults.buttonColors(containerColor = FgRoyalBlue, contentColor = FgWhite),
                    enabled = !connecting && host.isNotBlank() && username.isNotBlank() && validPort,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    if (connecting) {
                        CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                        Spacer(Modifier.width(8.dp))
                    }
                    Text(if (arabic) "اتصال بالراوتر" else "Connect")
                }
            }

            if (canArchive) item {
                OutlinedButton(
                    onClick = onOfflineStudio,
                    modifier = Modifier.fillMaxWidth(),
                    border = BorderStroke(1.2.dp, FgPurple)
                ) {
                    Icon(Icons.Outlined.ConfirmationNumber, contentDescription = null, tint = FgPurple)
                    Spacer(Modifier.width(8.dp))
                    Text(
                        if (arabic) "إنشاء كروت بدون راوتر" else "Create vouchers offline",
                        color = FgPurple
                    )
                }
            }

            item {
                OutlinedButton(onClick=onBusiness,enabled=!connecting,modifier=Modifier.fillMaxWidth()) {
                    Text(if(arabic) "المشتركون والحسابات" else "Subscribers & accounts")
                }
            }
            item { Spacer(Modifier.height(8.dp)) }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun RouterShell(
    state: RouterUiState,
    advancedManager: com.fgmachines.mikrotikmanager.advanced.AdvancedRouterManager? = null,
    hotspotManager: com.fgmachines.mikrotikmanager.hotspot.HotspotManager? = null,
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
    onCreateAdminItem: (Map<String, String>) -> Unit,
    onCreateRouterAdmin: (String, String, String, String) -> Unit,
    onUpdateAdminItem: (String?, Map<String, String>) -> Unit,
    onToggleAdminItem: (String, Boolean) -> Unit,
    onRemoveAdminItem: (String) -> Unit,
    onClearAdminActionMessage: () -> Unit,
    commandCenterInitiallyOpen: Boolean = false,
    commandCenterInitialText: String = "",
    initialAdvancedPanel: String = "home",
    advancedDemo: Boolean = false
) {
    val wide = LocalConfiguration.current.screenWidthDp >= 840
    var commandCenterOpen by rememberSaveable { mutableStateOf(commandCenterInitiallyOpen) }

    var sectionHistory by rememberSaveable { mutableStateOf(arrayListOf<String>()) }
    val contentState = rememberSaveableStateHolder()
    val backDispatcher = LocalOnBackPressedDispatcherOwner.current?.onBackPressedDispatcher
    val navigate: (AppSection) -> Unit = { target ->
        if (target != state.section) {
            sectionHistory = if (target == AppSection.BUSINESS) sectionHistory else if (target == AppSection.MENU) arrayListOf()
                else ArrayList(sectionHistory + state.section.name)
            onSection(target)
        }
    }
    val goBack: () -> Unit = {
        when {
            commandCenterOpen -> commandCenterOpen = false
            state.adminModule != null -> onCloseAdminModule()
            sectionHistory.isNotEmpty() -> {
                val previous = AppSection.valueOf(sectionHistory.last())
                sectionHistory = ArrayList(sectionHistory.dropLast(1))
                onSection(previous)
            }
            else -> onSection(AppSection.MENU)
        }
    }

    BackHandler(enabled = commandCenterOpen || state.section != AppSection.MENU || state.adminModule != null) {
        goBack()
    }

    if (wide) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(FgBlack)
        ) {
            Row(modifier = Modifier.fillMaxSize()) {
                PersistentMainMenu(
                    state = state,
                    arabic = arabic,
                    onSection = navigate,
                    onDisconnect = onDisconnect,
                    modifier = Modifier
                        .width(280.dp)
                        .fillMaxHeight()
                )
                VerticalDivider(color = FgBlue.copy(alpha = 0.35f))
                contentState.SaveableStateProvider(state.section.name) {
                RouterContent(
                    state = state,
                    hotspotManager = hotspotManager,
                    advancedManager = advancedManager,
                    initialAdvancedPanel = initialAdvancedPanel, advancedDemo = advancedDemo,
                    onSection = navigate,
                    arabic = arabic,
                    onRefresh = onRefresh,
                    onOpenAdminModule = onOpenAdminModule,
                    onOpenTerminal = { commandCenterOpen = true },
                    onRefreshAdminModule = onRefreshAdminModule,
                    onCloseAdminModule = onCloseAdminModule,
                    onVoucherModeSelected = onVoucherModeSelected,
                    onVoucherBatchGenerated = onVoucherBatchGenerated,
                    onProvisionVouchers = onProvisionVouchers,
                    onClearVoucherResult = onClearVoucherResult,
                    onCreateAdminItem = onCreateAdminItem,
                    onCreateRouterAdmin = onCreateRouterAdmin,
                    onUpdateAdminItem = onUpdateAdminItem,
                    onToggleAdminItem = onToggleAdminItem,
                    onRemoveAdminItem = onRemoveAdminItem,
                    onClearAdminActionMessage = onClearAdminActionMessage,
                    modifier = Modifier.weight(1f)
                )
                }
            }

            if ((state.section == AppSection.NETWORK || state.section == AppSection.SYSTEM)) {
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
        return
    }

    Scaffold(
        containerColor = FgBlack,
        floatingActionButton = {
            if ((state.section == AppSection.NETWORK || state.section == AppSection.SYSTEM)) {
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
            CompactAppHeader(
                subtitle = sectionLabel(state.section, arabic),
                showBack = state.section != AppSection.MENU || state.adminModule != null,
                onBack = { backDispatcher?.onBackPressed() ?: goBack() },
                onLanguageToggle = onLanguageToggle,
                showRefresh = state.section == AppSection.ADVANCED,
                refreshing = state.refreshing,
                onRefresh = onRefresh
            )
        }
    ) { padding ->
        contentState.SaveableStateProvider(state.section.name) {
        RouterContent(
            state = state,
            hotspotManager = hotspotManager,
            advancedManager = advancedManager,
            initialAdvancedPanel = initialAdvancedPanel, advancedDemo = advancedDemo,
            arabic = arabic,
            onRefresh = onRefresh,
            onOpenAdminModule = onOpenAdminModule,
            onOpenTerminal = { commandCenterOpen = true },
            onRefreshAdminModule = onRefreshAdminModule,
            onCloseAdminModule = onCloseAdminModule,
            onVoucherModeSelected = onVoucherModeSelected,
            onVoucherBatchGenerated = onVoucherBatchGenerated,
            onProvisionVouchers = onProvisionVouchers,
            onClearVoucherResult = onClearVoucherResult,
            onCreateAdminItem = onCreateAdminItem,
            onCreateRouterAdmin = onCreateRouterAdmin,
            onUpdateAdminItem = onUpdateAdminItem,
            onToggleAdminItem = onToggleAdminItem,
            onRemoveAdminItem = onRemoveAdminItem,
            onClearAdminActionMessage = onClearAdminActionMessage,
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            onSection = navigate,
            onDisconnect = onDisconnect
        )
        }
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
    advancedManager: com.fgmachines.mikrotikmanager.advanced.AdvancedRouterManager? = null,
    hotspotManager: com.fgmachines.mikrotikmanager.hotspot.HotspotManager?,
    arabic: Boolean,
    onRefresh: () -> Unit,
    onOpenAdminModule: (com.fgmachines.mikrotikmanager.data.RouterAdminModule) -> Unit,
    onOpenTerminal: () -> Unit,
    onRefreshAdminModule: () -> Unit,
    onCloseAdminModule: () -> Unit,
    onVoucherModeSelected: (VoucherMode) -> Unit,
    onVoucherBatchGenerated: (com.fgmachines.mikrotikmanager.voucher.VoucherBatch) -> Unit,
    onProvisionVouchers: (com.fgmachines.mikrotikmanager.voucher.VoucherBatch) -> Unit,
    onClearVoucherResult: () -> Unit,
    onCreateAdminItem: (Map<String, String>) -> Unit,
    onCreateRouterAdmin: (String, String, String, String) -> Unit,
    onUpdateAdminItem: (String?, Map<String, String>) -> Unit,
    onToggleAdminItem: (String, Boolean) -> Unit,
    onRemoveAdminItem: (String) -> Unit,
    onClearAdminActionMessage: () -> Unit,
    modifier: Modifier,
    onSection: (AppSection) -> Unit = {},
    onDisconnect: () -> Unit = {},
    initialAdvancedPanel: String = "home",
    advancedDemo: Boolean = false
) {
    when (state.section) {
        AppSection.MENU -> MainMenuScreen(
            state = state,
            arabic = arabic,
            onSection = onSection,
            onDisconnect = onDisconnect,
            modifier = modifier
        )

        AppSection.ADVANCED -> AdvancedSetupScreen(
            arabic = arabic, snapshot = state.dashboard, manager = advancedManager,
            hotspot = hotspotManager, batches = state.recentVoucherBatches, onRefresh = onRefresh,
            onAdmin = { module -> onSection(if(module.group == RouterAdminGroup.SYSTEM) AppSection.SYSTEM else AppSection.NETWORK); onOpenAdminModule(module) },
            onTerminal = onOpenTerminal, onManualNetwork = { onSection(AppSection.NETWORK) },
            modifier = modifier, initialPanel = initialAdvancedPanel, demo = advancedDemo
        )

        AppSection.NETWORK,
        AppSection.SYSTEM -> RouterAdminScreen(
            arabic = arabic,
            group = if (state.section == AppSection.NETWORK) {
                RouterAdminGroup.NETWORK
            } else {
                RouterAdminGroup.SYSTEM
            },
            loading = state.adminLoading,
            actionRunning = state.adminActionRunning,
            selectedModule = state.adminModule,
            snapshot = state.adminSnapshot,
            error = state.adminError,
            actionMessage = state.adminActionMessage,
            onOpenModule = onOpenAdminModule,
            onOpenTerminal = onOpenTerminal,
            onRefresh = onRefreshAdminModule,
            onBack = onCloseAdminModule,
            onCreate = onCreateAdminItem,
            onCreateAdmin = onCreateRouterAdmin,
            onUpdate = onUpdateAdminItem,
            onToggle = onToggleAdminItem,
            onRemove = onRemoveAdminItem,
            onClearActionMessage = onClearAdminActionMessage,
            modifier = modifier
        )

        AppSection.VOUCHERS -> VoucherStudioScreen(
            arabic = arabic,
            hotspotManager = hotspotManager,
            advancedManager = advancedManager,
            onOpenAdvanced = { onSection(AppSection.ADVANCED) },
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

        AppSection.BUSINESS -> BusinessScreen(arabic, { onSection(AppSection.MENU) }, {})
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
            AppSection.ADVANCED,
            if (arabic) "الإعداد المتقدم" else "Advanced Setup",
            if (arabic) "حالة الراوتر • إعداد HotSpot • تشخيص الشبكة • إصلاح المشاكل" else "Router status • HotSpot • Diagnostics • Repairs",
            Icons.Outlined.Tune,
            FgBlue
        ),
        MenuEntry(
            AppSection.NETWORK,
            if (arabic) "الشبكة والاتصال" else "Network & connectivity",
            if (arabic) "Wi-Fi، Interfaces، IP، DHCP، DNS، Routes، HotSpot وPPP" else "Wi-Fi, interfaces, IP, DHCP, DNS, routes, HotSpot and PPP",
            Icons.Outlined.Wifi,
            FgMint
        ),
        MenuEntry(
            AppSection.SYSTEM,
            if (arabic) "النظام والأمان" else "System & security",
            if (arabic) "Firewall، Admin، Services، Files، Logs وCommand Center" else "Firewall, admins, services, files, logs and Command Center",
            Icons.Outlined.Security,
            FgAmber
        ),
        MenuEntry(
            AppSection.VOUCHERS,
            if (arabic) "إنشاء الكروت" else "Voucher Studio",
            if (arabic) "كروت احترافية مع المدة والسعر والانتهاء والتفعيل المباشر" else "Professional vouchers with duration, price, expiry and direct activation",
            Icons.Outlined.ConfirmationNumber,
            FgPurple
        ),
        MenuEntry(
            AppSection.BUSINESS,
            if (arabic) "المشتركون والحسابات" else "Subscribers & accounts",
            if (arabic) "سجل المشتركين • المستحقات • المدفوعات • كشف الحساب" else "Subscribers • Charges • Payments • Account ledger",
            Icons.Outlined.People,
            FgMint
        ),
        MenuEntry(
            AppSection.ABOUT,
            if (arabic) "عن المطور" else "About developer",
            if (arabic) "FG Machines ومعلومات المطور والروابط" else "FG Machines, developer information and links",
            Icons.Outlined.Info,
            FgCyan
        )
    ).filter { it.section != AppSection.VOUCHERS || com.fgmachines.mikrotikmanager.business.BusinessPermission.VOUCHERS in LocalBusinessPermissions.current }
    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .background(FgBlack),
        contentPadding = PaddingValues(14.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        item {
            Text(
                if (arabic) "القائمة الرئيسية" else "Main menu",
                color = FgMint,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Black
            )
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
        border = BorderStroke(1.2.dp, FgBlue),
        shape = RoundedCornerShape(16.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 10.dp),
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
            AppSection.ADVANCED,
            AppSection.NETWORK,
            AppSection.SYSTEM,
            AppSection.VOUCHERS,
            AppSection.BUSINESS,
            AppSection.ABOUT
        ).filter { it != AppSection.VOUCHERS || com.fgmachines.mikrotikmanager.business.BusinessPermission.VOUCHERS in LocalBusinessPermissions.current }.forEach { section ->
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
            CompactAppHeader(
                subtitle = if (arabic) "الكروت بدون راوتر" else "Offline vouchers",
                showBack = true,
                onBack = onBack,
                onLanguageToggle = onLanguageToggle
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

@Composable
private fun CompactAppHeader(
    subtitle: String,
    showBack: Boolean,
    onBack: () -> Unit,
    onLanguageToggle: () -> Unit,
    showRefresh: Boolean = false,
    refreshing: Boolean = false,
    onRefresh: () -> Unit = {}
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(FgDeepNavy)
            .statusBarsPadding()
            .height(48.dp)
            .padding(horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (showBack) {
            IconButton(
                onClick = onBack,
                modifier = Modifier.size(42.dp)
            ) {
                Icon(
                    Icons.Outlined.ArrowBack,
                    contentDescription = "Back",
                    tint = FgMint
                )
            }
        }

        Column(
            modifier = Modifier
                .weight(1f)
                .padding(horizontal = 6.dp),
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                "FG MTM",
                color = FgBlue,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Black
            )
            Text(
                subtitle,
                color = FgSilver,
                style = MaterialTheme.typography.labelSmall,
                maxLines = 1
            )
        }

        if (showRefresh) {
            IconButton(
                onClick = onRefresh,
                enabled = !refreshing,
                modifier = Modifier.size(42.dp)
            ) {
                if (refreshing) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(18.dp),
                        strokeWidth = 2.dp,
                        color = FgBlue
                    )
                } else {
                    Icon(
                        Icons.Outlined.Refresh,
                        contentDescription = "Refresh",
                        tint = FgBlue
                    )
                }
            }
        }

        IconButton(
            onClick = onLanguageToggle,
            modifier = Modifier.size(42.dp)
        ) {
            Icon(
                Icons.Outlined.Language,
                contentDescription = "Language",
                tint = FgCyan
            )
        }
    }
}

private fun sectionLabel(section: AppSection, arabic: Boolean): String =
    when (section) {
        AppSection.MENU -> if (arabic) "القائمة الرئيسية" else "Main menu"
        AppSection.ADVANCED -> if (arabic) "الإعداد المتقدم" else "Advanced Setup"
        AppSection.NETWORK -> if (arabic) "الشبكة والاتصال" else "Network & connectivity"
        AppSection.SYSTEM -> if (arabic) "النظام والأمان" else "System & security"
        AppSection.VOUCHERS -> if (arabic) "الكروت" else "Vouchers"
        AppSection.BUSINESS -> if(arabic) "المشتركون والحسابات" else "Subscribers & accounts"
        AppSection.ABOUT -> if (arabic) "عن المطور" else "About developer"
    }

private fun sectionIcon(section: AppSection): ImageVector =
    when (section) {
        AppSection.MENU -> Icons.Outlined.Settings
        AppSection.ADVANCED -> Icons.Outlined.Tune
        AppSection.NETWORK -> Icons.Outlined.Wifi
        AppSection.SYSTEM -> Icons.Outlined.Security
        AppSection.VOUCHERS -> Icons.Outlined.ConfirmationNumber
        AppSection.BUSINESS -> Icons.Outlined.People
        AppSection.ABOUT -> Icons.Outlined.Info
    }

private fun sectionAccent(section: AppSection): Color =
    when (section) {
        AppSection.MENU -> FgSilver
        AppSection.ADVANCED -> FgBlue
        AppSection.NETWORK -> FgMint
        AppSection.SYSTEM -> FgAmber
        AppSection.VOUCHERS -> FgPurple
        AppSection.BUSINESS -> FgMint
        AppSection.ABOUT -> FgCyan
    }
