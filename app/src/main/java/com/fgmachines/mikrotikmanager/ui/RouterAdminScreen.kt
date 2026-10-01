package com.fgmachines.mikrotikmanager.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AccountTree
import androidx.compose.material.icons.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.Dns
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.Groups
import androidx.compose.material.icons.outlined.Hub
import androidx.compose.material.icons.outlined.Language
import androidx.compose.material.icons.outlined.ListAlt
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.NetworkCheck
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.Public
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Router
import androidx.compose.material.icons.outlined.Security
import androidx.compose.material.icons.outlined.SettingsEthernet
import androidx.compose.material.icons.outlined.Speed
import androidx.compose.material.icons.outlined.Wifi
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.fgmachines.mikrotikmanager.data.RouterAdminModule
import com.fgmachines.mikrotikmanager.data.RouterMenuSnapshot

@Composable
fun RouterAdminScreen(
    arabic: Boolean,
    loading: Boolean,
    selectedModule: RouterAdminModule?,
    snapshot: RouterMenuSnapshot?,
    error: String?,
    onOpenModule: (RouterAdminModule) -> Unit,
    onRefresh: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    if (selectedModule == null) {
        RouterModuleMenu(
            arabic = arabic,
            onOpenModule = onOpenModule,
            modifier = modifier
        )
        return
    }

    RouterModuleDetails(
        arabic = arabic,
        loading = loading,
        module = selectedModule,
        snapshot = snapshot,
        error = error,
        onRefresh = onRefresh,
        onBack = onBack,
        modifier = modifier
    )
}

@Composable
private fun RouterModuleMenu(
    arabic: Boolean,
    onOpenModule: (RouterAdminModule) -> Unit,
    modifier: Modifier
) {
    val modules = RouterAdminModule.entries

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(FgBlack)
    ) {
        RouterAdminHeader(
            title = if (arabic) "إدارة الراوتر" else "Router Manager",
            subtitle = if (arabic) {
                "واجهة WinBox مبسطة — اختر القسم المطلوب"
            } else {
                "Simplified WinBox-style management — choose a section"
            }
        )

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            item { Spacer(Modifier.height(4.dp)) }

            items(modules, key = { it.name }) { module ->
                val visual = moduleVisual(module)
                RouterModuleRow(
                    title = moduleTitle(module, arabic),
                    subtitle = moduleSubtitle(module, arabic),
                    icon = visual.icon,
                    accent = visual.accent,
                    onClick = { onOpenModule(module) }
                )
            }

            item { Spacer(Modifier.height(18.dp)) }
        }
    }
}

@Composable
private fun RouterAdminHeader(
    title: String,
    subtitle: String
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(FgDeepNavy)
            .padding(horizontal = 18.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Black,
            color = FgWhite
        )
        Text(
            text = subtitle,
            style = MaterialTheme.typography.bodyMedium,
            color = FgSilver
        )
    }
}

@Composable
private fun RouterModuleRow(
    title: String,
    subtitle: String,
    icon: ImageVector,
    accent: Color,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .padding(horizontal = 14.dp)
            .fillMaxWidth()
            .clickable(onClick = onClick),
        colors = CardDefaults.cardColors(
            containerColor = FgPanel
        ),
        border = BorderStroke(1.4.dp, accent.copy(alpha = 0.92f)),
        shape = RoundedCornerShape(16.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 13.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(13.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(46.dp)
                    .background(accent.copy(alpha = 0.12f), RoundedCornerShape(13.dp)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = accent,
                    modifier = Modifier.size(28.dp)
                )
            }

            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(3.dp)
            ) {
                Text(
                    title,
                    color = FgWhite,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    subtitle,
                    color = FgSilverMuted,
                    style = MaterialTheme.typography.bodySmall
                )
            }

            Text(
                "›",
                color = accent,
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Black
            )
        }
    }
}

@Composable
private fun RouterModuleDetails(
    arabic: Boolean,
    loading: Boolean,
    module: RouterAdminModule,
    snapshot: RouterMenuSnapshot?,
    error: String?,
    onRefresh: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(FgBlack)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(FgDeepNavy)
                .padding(horizontal = 8.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) {
                Icon(
                    Icons.Outlined.ArrowBack,
                    contentDescription = "Back",
                    tint = FgMint
                )
            }

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    moduleTitle(module, arabic),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Black,
                    color = FgWhite
                )
                Text(
                    snapshot?.menuPath?.let { "/$it" }
                        ?: if (arabic) "قراءة البيانات من RouterOS" else "Reading RouterOS data",
                    style = MaterialTheme.typography.bodySmall,
                    color = FgCyan
                )
            }

            IconButton(onClick = onRefresh, enabled = !loading) {
                if (loading) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(22.dp),
                        strokeWidth = 2.dp,
                        color = FgMint
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

        when {
            loading && snapshot == null -> {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator(color = FgMint)
                }
            }

            error != null -> {
                Card(
                    modifier = Modifier
                        .padding(16.dp)
                        .fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.errorContainer
                    ),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.error)
                ) {
                    Text(
                        error,
                        modifier = Modifier.padding(16.dp),
                        color = MaterialTheme.colorScheme.onErrorContainer
                    )
                }
            }

            snapshot == null || snapshot.rows.isEmpty() -> {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        if (arabic) "لا توجد عناصر في هذا القسم" else "No items in this section",
                        color = FgSilver
                    )
                }
            }

            else -> {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    item { Spacer(Modifier.height(4.dp)) }

                    items(
                        snapshot.rows,
                        key = { row ->
                            row[".id"]
                                ?: row["name"]
                                ?: row["address"]
                                ?: row.hashCode().toString()
                        }
                    ) { row ->
                        RouterRecordRow(
                            row = row,
                            accent = moduleVisual(module).accent,
                            arabic = arabic
                        )
                    }

                    item { Spacer(Modifier.height(18.dp)) }
                }
            }
        }
    }
}

@Composable
private fun RouterRecordRow(
    row: Map<String, String>,
    accent: Color,
    arabic: Boolean
) {
    val title = row["name"]
        ?: row["user"]
        ?: row["address"]
        ?: row["dst-address"]
        ?: row["message"]
        ?: row[".id"]
        ?: if (arabic) "عنصر RouterOS" else "RouterOS item"

    val preferredKeys = listOf(
        "interface",
        "type",
        "running",
        "disabled",
        "address",
        "network",
        "gateway",
        "dst-address",
        "chain",
        "action",
        "profile",
        "server",
        "mac-address",
        "uptime",
        "session-time-left",
        "rate-limit",
        "port",
        "size",
        "topics",
        "message"
    )

    val details = buildList {
        preferredKeys.forEach { key ->
            row[key]?.takeIf { it.isNotBlank() }?.let { add(key to it) }
        }
        if (isEmpty()) {
            row.entries
                .asSequence()
                .filter { it.key != ".id" && it.key != "name" }
                .take(5)
                .forEach { add(it.key to it.value) }
        }
    }.take(6)

    Card(
        modifier = Modifier
            .padding(horizontal = 14.dp)
            .fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = FgPanel),
        border = BorderStroke(1.dp, accent.copy(alpha = 0.55f)),
        shape = RoundedCornerShape(14.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(7.dp)
        ) {
            Text(
                title,
                color = FgWhite,
                fontWeight = FontWeight.Bold,
                style = MaterialTheme.typography.titleMedium
            )

            HorizontalDivider(color = accent.copy(alpha = 0.25f))

            details.forEach { (key, value) ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        key,
                        modifier = Modifier.weight(0.42f),
                        color = FgSilverMuted,
                        style = MaterialTheme.typography.labelMedium
                    )
                    Text(
                        value,
                        modifier = Modifier.weight(0.58f),
                        color = if (value.equals("true", true) || value.equals("yes", true)) {
                            FgMint
                        } else {
                            FgSilver
                        },
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            }
        }
    }
}

private data class ModuleVisual(
    val icon: ImageVector,
    val accent: Color
)

private fun moduleVisual(module: RouterAdminModule): ModuleVisual =
    when (module) {
        RouterAdminModule.INTERFACES -> ModuleVisual(Icons.Outlined.SettingsEthernet, FgBlue)
        RouterAdminModule.WIFI -> ModuleVisual(Icons.Outlined.Wifi, FgMint)
        RouterAdminModule.BRIDGE -> ModuleVisual(Icons.Outlined.Hub, FgCyan)
        RouterAdminModule.IP_ADDRESSES -> ModuleVisual(Icons.Outlined.Language, FgBlue)
        RouterAdminModule.DHCP -> ModuleVisual(Icons.Outlined.Dns, FgMint)
        RouterAdminModule.DNS -> ModuleVisual(Icons.Outlined.Public, FgCyan)
        RouterAdminModule.ROUTES -> ModuleVisual(Icons.Outlined.AccountTree, FgPurple)
        RouterAdminModule.FIREWALL -> ModuleVisual(Icons.Outlined.Security, FgAmber)
        RouterAdminModule.HOTSPOT -> ModuleVisual(Icons.Outlined.NetworkCheck, FgMagenta)
        RouterAdminModule.PPP -> ModuleVisual(Icons.Outlined.Lock, FgPurple)
        RouterAdminModule.QUEUES -> ModuleVisual(Icons.Outlined.Speed, FgAmber)
        RouterAdminModule.USERS -> ModuleVisual(Icons.Outlined.Groups, FgMint)
        RouterAdminModule.SERVICES -> ModuleVisual(Icons.Outlined.Router, FgBlue)
        RouterAdminModule.FILES -> ModuleVisual(Icons.Outlined.Folder, FgCyan)
        RouterAdminModule.LOGS -> ModuleVisual(Icons.Outlined.Description, FgSilver)
    }

private fun moduleTitle(module: RouterAdminModule, arabic: Boolean): String =
    when (module) {
        RouterAdminModule.INTERFACES -> if (arabic) "واجهات الشبكة" else "Interfaces"
        RouterAdminModule.WIFI -> if (arabic) "الواي فاي" else "Wi‑Fi"
        RouterAdminModule.BRIDGE -> "Bridge"
        RouterAdminModule.IP_ADDRESSES -> if (arabic) "عناوين IP" else "IP Addresses"
        RouterAdminModule.DHCP -> "DHCP"
        RouterAdminModule.DNS -> "DNS"
        RouterAdminModule.ROUTES -> if (arabic) "المسارات" else "Routes"
        RouterAdminModule.FIREWALL -> if (arabic) "الجدار الناري" else "Firewall"
        RouterAdminModule.HOTSPOT -> "HotSpot"
        RouterAdminModule.PPP -> "PPP"
        RouterAdminModule.QUEUES -> if (arabic) "السرعات والطوابير" else "Queues"
        RouterAdminModule.USERS -> if (arabic) "مستخدمو الراوتر" else "Router Users"
        RouterAdminModule.SERVICES -> if (arabic) "خدمات الراوتر" else "IP Services"
        RouterAdminModule.FILES -> if (arabic) "الملفات" else "Files"
        RouterAdminModule.LOGS -> if (arabic) "السجل" else "Logs"
    }

private fun moduleSubtitle(module: RouterAdminModule, arabic: Boolean): String =
    when (module) {
        RouterAdminModule.INTERFACES ->
            if (arabic) "Ethernet وواجهات الشبكة وحالتها" else "Ethernet and network interface status"
        RouterAdminModule.WIFI ->
            if (arabic) "شبكات وأجهزة Wi‑Fi" else "Wireless and Wi‑Fi interfaces"
        RouterAdminModule.BRIDGE ->
            if (arabic) "إدارة الـ Bridge" else "Bridge configuration"
        RouterAdminModule.IP_ADDRESSES ->
            if (arabic) "عناوين الراوتر والشبكات" else "Router addresses and networks"
        RouterAdminModule.DHCP ->
            if (arabic) "خوادم وعملاء DHCP" else "DHCP servers and clients"
        RouterAdminModule.DNS ->
            if (arabic) "إعدادات DNS" else "DNS configuration"
        RouterAdminModule.ROUTES ->
            if (arabic) "جدول التوجيه والبوابات" else "Routing table and gateways"
        RouterAdminModule.FIREWALL ->
            if (arabic) "قواعد Filter" else "Filter rules"
        RouterAdminModule.HOTSPOT ->
            if (arabic) "المستخدمون والجلسات" else "Users and active sessions"
        RouterAdminModule.PPP ->
            if (arabic) "الحسابات والاتصالات" else "Secrets and active sessions"
        RouterAdminModule.QUEUES ->
            if (arabic) "تحديد السرعات" else "Bandwidth limits"
        RouterAdminModule.USERS ->
            if (arabic) "حسابات إدارة RouterOS" else "RouterOS admin accounts"
        RouterAdminModule.SERVICES ->
            if (arabic) "WinBox وAPI وSSH وغيرها" else "WinBox, API, SSH and more"
        RouterAdminModule.FILES ->
            if (arabic) "ملفات الراوتر" else "Router files"
        RouterAdminModule.LOGS ->
            if (arabic) "أحداث وسجل RouterOS" else "RouterOS event log"
    }
