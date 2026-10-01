package com.fgmachines.mikrotikmanager.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AccountTree
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.Dns
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.Groups
import androidx.compose.material.icons.outlined.Hub
import androidx.compose.material.icons.outlined.Language
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.NetworkCheck
import androidx.compose.material.icons.outlined.PersonAdd
import androidx.compose.material.icons.outlined.Public
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Router
import androidx.compose.material.icons.outlined.Security
import androidx.compose.material.icons.outlined.SettingsEthernet
import androidx.compose.material.icons.outlined.Speed
import androidx.compose.material.icons.outlined.ToggleOff
import androidx.compose.material.icons.outlined.ToggleOn
import androidx.compose.material.icons.outlined.Wifi
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.fgmachines.mikrotikmanager.data.RouterAdminGroup
import com.fgmachines.mikrotikmanager.data.RouterAdminModule
import com.fgmachines.mikrotikmanager.data.RouterMenuSnapshot

@Composable
fun RouterAdminScreen(
    arabic: Boolean,
    group: RouterAdminGroup,
    loading: Boolean,
    actionRunning: Boolean,
    selectedModule: RouterAdminModule?,
    snapshot: RouterMenuSnapshot?,
    error: String?,
    actionMessage: String?,
    onOpenModule: (RouterAdminModule) -> Unit,
    onRefresh: () -> Unit,
    onBack: () -> Unit,
    onCreate: (Map<String, String>) -> Unit,
    onCreateAdmin: (String, String, String, String) -> Unit,
    onUpdate: (String?, Map<String, String>) -> Unit,
    onToggle: (String, Boolean) -> Unit,
    onRemove: (String) -> Unit,
    onClearActionMessage: () -> Unit,
    modifier: Modifier = Modifier
) {
    if (selectedModule == null) {
        RouterModuleMenu(
            arabic = arabic,
            group = group,
            onOpenModule = onOpenModule,
            modifier = modifier
        )
        return
    }

    RouterModuleDetails(
        arabic = arabic,
        loading = loading,
        actionRunning = actionRunning,
        module = selectedModule,
        snapshot = snapshot,
        error = error,
        actionMessage = actionMessage,
        onRefresh = onRefresh,
        onBack = onBack,
        onCreate = onCreate,
        onCreateAdmin = onCreateAdmin,
        onUpdate = onUpdate,
        onToggle = onToggle,
        onRemove = onRemove,
        onClearActionMessage = onClearActionMessage,
        modifier = modifier
    )
}

@Composable
private fun RouterModuleMenu(
    arabic: Boolean,
    group: RouterAdminGroup,
    onOpenModule: (RouterAdminModule) -> Unit,
    modifier: Modifier
) {
    val modules = RouterAdminModule.entries.filter { it.group == group }
    val accent = if (group == RouterAdminGroup.NETWORK) FgMint else FgBlue

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(FgBlack)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(FgDeepNavy)
                .padding(horizontal = 14.dp, vertical = 9.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Icon(
                if (group == RouterAdminGroup.NETWORK) Icons.Outlined.Wifi
                else Icons.Outlined.Security,
                contentDescription = null,
                tint = accent,
                modifier = Modifier.size(28.dp)
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    if (group == RouterAdminGroup.NETWORK) {
                        if (arabic) "الشبكة والاتصال" else "Network & connectivity"
                    } else {
                        if (arabic) "النظام والأمان" else "System & security"
                    },
                    color = FgWhite,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Black
                )
                Text(
                    if (arabic) "اختر القسم لإدارته" else "Choose a section to manage",
                    color = FgSilverMuted,
                    style = MaterialTheme.typography.bodySmall
                )
            }
        }

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(7.dp)
        ) {
            item { Spacer(Modifier.height(3.dp)) }
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
            item { Spacer(Modifier.height(16.dp)) }
        }
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
            .padding(horizontal = 12.dp)
            .fillMaxWidth()
            .clickable(onClick = onClick),
        colors = CardDefaults.cardColors(containerColor = FgPanel),
        border = BorderStroke(1.2.dp, accent.copy(alpha = 0.88f)),
        shape = RoundedCornerShape(14.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 11.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(11.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(42.dp)
                    .background(accent.copy(alpha = 0.12f), RoundedCornerShape(12.dp)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = accent,
                    modifier = Modifier.size(25.dp)
                )
            }

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    title,
                    color = FgWhite,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    subtitle,
                    color = FgSilverMuted,
                    style = MaterialTheme.typography.bodySmall,
                    maxLines = 1
                )
            }

            Text(
                "›",
                color = accent,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Black
            )
        }
    }
}

@Composable
private fun RouterModuleDetails(
    arabic: Boolean,
    loading: Boolean,
    actionRunning: Boolean,
    module: RouterAdminModule,
    snapshot: RouterMenuSnapshot?,
    error: String?,
    actionMessage: String?,
    onRefresh: () -> Unit,
    onBack: () -> Unit,
    onCreate: (Map<String, String>) -> Unit,
    onCreateAdmin: (String, String, String, String) -> Unit,
    onUpdate: (String?, Map<String, String>) -> Unit,
    onToggle: (String, Boolean) -> Unit,
    onRemove: (String) -> Unit,
    onClearActionMessage: () -> Unit,
    modifier: Modifier
) {
    var editorRow by remember { mutableStateOf<Map<String, String>?>(null) }
    var editorOpen by rememberSaveable { mutableStateOf(false) }
    var deleteRow by remember { mutableStateOf<Map<String, String>?>(null) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(FgBlack)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(FgDeepNavy)
                .padding(horizontal = 4.dp, vertical = 4.dp),
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
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Black,
                    color = FgWhite
                )
                Text(
                    snapshot?.menuPath?.let { "/$it" }
                        ?: if (arabic) "RouterOS" else "RouterOS",
                    style = MaterialTheme.typography.labelSmall,
                    color = FgCyan
                )
            }

            if (module.canCreate) {
                IconButton(
                    onClick = {
                        editorRow = null
                        editorOpen = true
                    },
                    enabled = !actionRunning
                ) {
                    Icon(
                        if (module == RouterAdminModule.USERS) Icons.Outlined.PersonAdd
                        else Icons.Outlined.Add,
                        contentDescription = "Add",
                        tint = FgMint
                    )
                }
            }

            IconButton(onClick = onRefresh, enabled = !loading && !actionRunning) {
                if (loading || actionRunning) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(21.dp),
                        strokeWidth = 2.dp,
                        color = FgCyan
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

        actionMessage?.let { message ->
            Card(
                modifier = Modifier
                    .padding(horizontal = 12.dp, vertical = 6.dp)
                    .fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = FgMint.copy(alpha = 0.09f)
                ),
                border = BorderStroke(1.dp, FgMint.copy(alpha = 0.7f))
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 10.dp, vertical = 7.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        message,
                        modifier = Modifier.weight(1f),
                        color = FgMint,
                        style = MaterialTheme.typography.bodySmall
                    )
                    TextButton(onClick = onClearActionMessage) {
                        Text(if (arabic) "إخفاء" else "Hide")
                    }
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
                        .padding(12.dp)
                        .fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.errorContainer
                    ),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.error)
                ) {
                    Text(
                        error,
                        modifier = Modifier.padding(12.dp),
                        color = MaterialTheme.colorScheme.onErrorContainer
                    )
                }
            }

            snapshot == null || snapshot.rows.isEmpty() -> {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            if (arabic) "لا توجد عناصر" else "No items",
                            color = FgSilver
                        )
                        if (module.canCreate) {
                            Spacer(Modifier.height(8.dp))
                            Button(
                                onClick = {
                                    editorRow = null
                                    editorOpen = true
                                }
                            ) {
                                Icon(Icons.Outlined.Add, contentDescription = null)
                                Spacer(Modifier.width(6.dp))
                                Text(if (arabic) "إضافة" else "Add")
                            }
                        }
                    }
                }
            }

            else -> {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy(7.dp)
                ) {
                    item { Spacer(Modifier.height(2.dp)) }

                    items(
                        snapshot.rows,
                        key = { row ->
                            row[".id"]
                                ?: row["name"]
                                ?: row["address"]
                                ?: row.hashCode().toString()
                        }
                    ) { row ->
                        RouterRecordCard(
                            row = row,
                            module = module,
                            accent = moduleVisual(module).accent,
                            arabic = arabic,
                            actionRunning = actionRunning,
                            onEdit = {
                                editorRow = row
                                editorOpen = true
                            },
                            onToggle = { enabled ->
                                row[".id"]?.let { onToggle(it, enabled) }
                            },
                            onDelete = { deleteRow = row }
                        )
                    }

                    item { Spacer(Modifier.height(16.dp)) }
                }
            }
        }
    }

    if (editorOpen) {
        RouterItemEditorDialog(
            arabic = arabic,
            module = module,
            existing = editorRow,
            busy = actionRunning,
            onDismiss = { editorOpen = false },
            onSave = { attributes ->
                if (module == RouterAdminModule.USERS && editorRow == null) {
                    onCreateAdmin(
                        attributes["name"].orEmpty(),
                        attributes["password"].orEmpty(),
                        attributes["group"].orEmpty().ifBlank { "full" },
                        attributes["comment"].orEmpty()
                    )
                } else if (editorRow == null) {
                    onCreate(attributes)
                } else {
                    onUpdate(editorRow?.get(".id"), attributes)
                }
                editorOpen = false
            }
        )
    }

    deleteRow?.let { row ->
        AlertDialog(
            onDismissRequest = { deleteRow = null },
            title = {
                Text(if (arabic) "تأكيد الحذف" else "Confirm delete")
            },
            text = {
                Text(
                    if (arabic) {
                        "سيتم حذف " + recordTitle(row, arabic) + " من الراوتر. لا يمكن التراجع تلقائيًا."
                    } else {
                        "This will delete " + recordTitle(row, arabic) + " from the router. It cannot be automatically undone."
                    }
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        row[".id"]?.let(onRemove)
                        deleteRow = null
                    }
                ) {
                    Text(if (arabic) "حذف" else "Delete")
                }
            },
            dismissButton = {
                TextButton(onClick = { deleteRow = null }) {
                    Text(if (arabic) "إلغاء" else "Cancel")
                }
            }
        )
    }
}

@Composable
private fun RouterRecordCard(
    row: Map<String, String>,
    module: RouterAdminModule,
    accent: Color,
    arabic: Boolean,
    actionRunning: Boolean,
    onEdit: () -> Unit,
    onToggle: (Boolean) -> Unit,
    onDelete: () -> Unit
) {
    val disabled = row["disabled"].routerBool()
    val details = preferredDetails(row)

    Card(
        modifier = Modifier
            .padding(horizontal = 12.dp)
            .fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = FgPanel),
        border = BorderStroke(1.dp, accent.copy(alpha = 0.62f)),
        shape = RoundedCornerShape(14.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(7.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        recordTitle(row, arabic),
                        color = FgWhite,
                        fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.titleMedium
                    )
                    row[".id"]?.let {
                        Text(
                            it,
                            color = FgSilverMuted,
                            style = MaterialTheme.typography.labelSmall
                        )
                    }
                }

                if (row.containsKey("disabled")) {
                    Text(
                        if (disabled) {
                            if (arabic) "متوقف" else "Disabled"
                        } else {
                            if (arabic) "يعمل" else "Enabled"
                        },
                        color = if (disabled) FgAmber else FgMint,
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            if (details.isNotEmpty()) {
                HorizontalDivider(color = accent.copy(alpha = 0.22f))
                details.take(5).forEach { (key, value) ->
                    Row(modifier = Modifier.fillMaxWidth()) {
                        Text(
                            key,
                            modifier = Modifier.weight(0.42f),
                            color = FgSilverMuted,
                            style = MaterialTheme.typography.labelSmall
                        )
                        Text(
                            value,
                            modifier = Modifier.weight(0.58f),
                            color = FgSilver,
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }
            }

            if (module.canEdit || module.canToggle || module.canDelete) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    if (module.canEdit) {
                        OutlinedButton(
                            onClick = onEdit,
                            enabled = !actionRunning
                        ) {
                            Icon(Icons.Outlined.Edit, contentDescription = null, tint = FgBlue)
                            Spacer(Modifier.width(4.dp))
                            Text(if (arabic) "تعديل" else "Edit")
                        }
                    }

                    if (module.canToggle && row[".id"] != null) {
                        OutlinedButton(
                            onClick = { onToggle(disabled) },
                            enabled = !actionRunning
                        ) {
                            Icon(
                                if (disabled) Icons.Outlined.ToggleOn else Icons.Outlined.ToggleOff,
                                contentDescription = null,
                                tint = if (disabled) FgMint else FgAmber
                            )
                            Spacer(Modifier.width(4.dp))
                            Text(
                                if (disabled) {
                                    if (arabic) "تفعيل" else "Enable"
                                } else {
                                    if (arabic) "تعطيل" else "Disable"
                                }
                            )
                        }
                    }

                    if (module.canDelete && row[".id"] != null) {
                        OutlinedButton(
                            onClick = onDelete,
                            enabled = !actionRunning,
                            border = BorderStroke(1.dp, Color(0xFFFF7B7B))
                        ) {
                            Icon(
                                Icons.Outlined.Delete,
                                contentDescription = null,
                                tint = Color(0xFFFF7B7B)
                            )
                            Spacer(Modifier.width(4.dp))
                            Text(
                                if (arabic) "حذف" else "Delete",
                                color = Color(0xFFFF7B7B)
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun RouterItemEditorDialog(
    arabic: Boolean,
    module: RouterAdminModule,
    existing: Map<String, String>?,
    busy: Boolean,
    onDismiss: () -> Unit,
    onSave: (Map<String, String>) -> Unit
) {
    val specs = fieldSpecs(module)
    val knownKeys = specs.map { it.key }.toSet()

    var values by remember(module, existing) {
        mutableStateOf(
            specs.associate { spec ->
                spec.key to existing?.get(spec.key).orEmpty()
            }
        )
    }

    var advanced by remember(module, existing) {
        mutableStateOf(
            existing
                ?.filterKeys { key ->
                    key !in knownKeys &&
                        key != ".id" &&
                        key !in setOf(
                            "dynamic", "running", "actual-mtu",
                            "last-link-up-time", "link-downs"
                        )
                }
                ?.entries
                ?.take(12)
                ?.joinToString("\n") { it.key + "=" + it.value }
                .orEmpty()
        )
    }

    val title = if (existing == null) {
        if (module == RouterAdminModule.USERS) {
            if (arabic) "إضافة Admin" else "Add admin"
        } else {
            if (arabic) "إضافة عنصر" else "Add item"
        }
    } else {
        if (arabic) "تعديل العنصر" else "Edit item"
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Column {
                Text(title)
                Text(
                    moduleTitle(module, arabic),
                    color = FgCyan,
                    style = MaterialTheme.typography.bodySmall
                )
            }
        },
        text = {
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(430.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(specs) { spec ->
                    OutlinedTextField(
                        value = values[spec.key].orEmpty(),
                        onValueChange = { newValue ->
                            values = values + (spec.key to newValue)
                        },
                        modifier = Modifier.fillMaxWidth(),
                        label = {
                            Text(if (arabic) spec.ar else spec.en)
                        },
                        singleLine = spec.singleLine
                    )
                }

                item {
                    HorizontalDivider()
                    Text(
                        if (arabic) "حقول متقدمة — كل سطر key=value" else "Advanced fields — one key=value per line",
                        color = FgAmber,
                        style = MaterialTheme.typography.labelMedium
                    )
                }

                item {
                    OutlinedTextField(
                        value = advanced,
                        onValueChange = { advanced = it },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(120.dp),
                        placeholder = {
                            Text("comment=FG MTM\ndisabled=false")
                        }
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val result = linkedMapOf<String, String>()
                    values.forEach { (key, value) ->
                        if (value.isNotBlank()) result[key] = value.trim()
                    }
                    advanced.lineSequence()
                        .map(String::trim)
                        .filter { it.isNotBlank() && it.contains("=") }
                        .forEach { line ->
                            val split = line.indexOf('=')
                            if (split > 0) {
                                result[line.substring(0, split).trim()] =
                                    line.substring(split + 1).trim()
                            }
                        }
                    onSave(result)
                },
                enabled = !busy
            ) {
                Text(if (arabic) "حفظ" else "Save")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, enabled = !busy) {
                Text(if (arabic) "إلغاء" else "Cancel")
            }
        }
    )
}

private data class FieldSpec(
    val key: String,
    val ar: String,
    val en: String,
    val singleLine: Boolean = true
)

private fun fieldSpecs(module: RouterAdminModule): List<FieldSpec> =
    when (module) {
        RouterAdminModule.INTERFACES -> listOf(
            FieldSpec("name", "الاسم", "Name"),
            FieldSpec("mtu", "MTU", "MTU"),
            FieldSpec("comment", "تعليق", "Comment")
        )
        RouterAdminModule.WIFI -> listOf(
            FieldSpec("name", "الاسم", "Name"),
            FieldSpec("ssid", "اسم الشبكة SSID", "SSID"),
            FieldSpec("disabled", "متوقف true/false", "Disabled true/false"),
            FieldSpec("comment", "تعليق", "Comment")
        )
        RouterAdminModule.BRIDGE -> listOf(
            FieldSpec("name", "اسم Bridge", "Bridge name"),
            FieldSpec("protocol-mode", "Protocol mode", "Protocol mode"),
            FieldSpec("comment", "تعليق", "Comment")
        )
        RouterAdminModule.VLAN -> listOf(
            FieldSpec("name", "الاسم", "Name"),
            FieldSpec("interface", "الواجهة", "Interface"),
            FieldSpec("vlan-id", "VLAN ID", "VLAN ID"),
            FieldSpec("mtu", "MTU", "MTU"),
            FieldSpec("comment", "تعليق", "Comment")
        )
        RouterAdminModule.WIREGUARD -> listOf(
            FieldSpec("name", "الاسم", "Name"),
            FieldSpec("listen-port", "منفذ الاستماع", "Listen port"),
            FieldSpec("mtu", "MTU", "MTU"),
            FieldSpec("comment", "تعليق", "Comment")
        )
        RouterAdminModule.ZEROTIER -> listOf(
            FieldSpec("name", "الاسم", "Name"),
            FieldSpec("instance", "Instance", "Instance"),
            FieldSpec("network", "Network ID", "Network ID"),
            FieldSpec("allow-default", "Allow default true/false", "Allow default true/false"),
            FieldSpec("comment", "تعليق", "Comment")
        )
        RouterAdminModule.IP_ADDRESSES -> listOf(
            FieldSpec("address", "العنوان/CIDR", "Address/CIDR"),
            FieldSpec("interface", "الواجهة", "Interface"),
            FieldSpec("network", "Network", "Network"),
            FieldSpec("comment", "تعليق", "Comment")
        )
        RouterAdminModule.ARP -> listOf(
            FieldSpec("address", "IP Address", "IP address"),
            FieldSpec("mac-address", "MAC", "MAC address"),
            FieldSpec("interface", "الواجهة", "Interface"),
            FieldSpec("comment", "تعليق", "Comment")
        )
        RouterAdminModule.NEIGHBORS -> emptyList()
        RouterAdminModule.DHCP -> listOf(
            FieldSpec("name", "الاسم", "Name"),
            FieldSpec("interface", "الواجهة", "Interface"),
            FieldSpec("address-pool", "Address pool", "Address pool"),
            FieldSpec("lease-time", "مدة Lease", "Lease time"),
            FieldSpec("comment", "تعليق", "Comment")
        )
        RouterAdminModule.DNS -> listOf(
            FieldSpec("servers", "DNS Servers", "DNS servers"),
            FieldSpec("allow-remote-requests", "السماح بطلبات الشبكة", "Allow remote requests"),
            FieldSpec("cache-size", "حجم Cache", "Cache size")
        )
        RouterAdminModule.ROUTES -> listOf(
            FieldSpec("dst-address", "الوجهة", "Destination"),
            FieldSpec("gateway", "Gateway", "Gateway"),
            FieldSpec("distance", "Distance", "Distance"),
            FieldSpec("comment", "تعليق", "Comment")
        )
        RouterAdminModule.FIREWALL -> listOf(
            FieldSpec("chain", "Chain", "Chain"),
            FieldSpec("action", "Action", "Action"),
            FieldSpec("src-address", "Source", "Source address"),
            FieldSpec("dst-address", "Destination", "Destination address"),
            FieldSpec("protocol", "Protocol", "Protocol"),
            FieldSpec("dst-port", "Destination port", "Destination port"),
            FieldSpec("comment", "تعليق", "Comment")
        )
        RouterAdminModule.HOTSPOT -> listOf(
            FieldSpec("name", "اسم المستخدم", "Username"),
            FieldSpec("password", "كلمة المرور", "Password"),
            FieldSpec("profile", "Profile", "Profile"),
            FieldSpec("server", "Server", "Server"),
            FieldSpec("limit-uptime", "مدة الاستخدام", "Uptime limit"),
            FieldSpec("comment", "تعليق", "Comment")
        )
        RouterAdminModule.PPP -> listOf(
            FieldSpec("name", "اسم المستخدم", "Username"),
            FieldSpec("password", "كلمة المرور", "Password"),
            FieldSpec("service", "Service", "Service"),
            FieldSpec("profile", "Profile", "Profile"),
            FieldSpec("comment", "تعليق", "Comment")
        )
        RouterAdminModule.QUEUES -> listOf(
            FieldSpec("name", "الاسم", "Name"),
            FieldSpec("target", "Target", "Target"),
            FieldSpec("max-limit", "أقصى سرعة", "Max limit"),
            FieldSpec("comment", "تعليق", "Comment")
        )
        RouterAdminModule.USERS -> listOf(
            FieldSpec("name", "اسم Admin", "Admin username"),
            FieldSpec("password", "كلمة المرور", "Password"),
            FieldSpec("group", "المجموعة full/read/write", "Group full/read/write"),
            FieldSpec("comment", "تعليق", "Comment")
        )
        RouterAdminModule.SERVICES -> listOf(
            FieldSpec("port", "المنفذ", "Port"),
            FieldSpec("address", "الشبكات المسموحة", "Allowed addresses"),
            FieldSpec("max-sessions", "أقصى جلسات", "Max sessions")
        )
        RouterAdminModule.IDENTITY -> listOf(
            FieldSpec("name", "اسم الراوتر", "Router identity")
        )
        RouterAdminModule.CLOCK -> listOf(
            FieldSpec("time-zone-name", "المنطقة الزمنية", "Time zone"),
            FieldSpec("date", "التاريخ", "Date"),
            FieldSpec("time", "الوقت", "Time")
        )
        RouterAdminModule.SCHEDULER -> listOf(
            FieldSpec("name", "الاسم", "Name"),
            FieldSpec("start-date", "تاريخ البدء", "Start date"),
            FieldSpec("start-time", "وقت البدء", "Start time"),
            FieldSpec("interval", "التكرار", "Interval"),
            FieldSpec("on-event", "الأمر", "On event", singleLine = false),
            FieldSpec("comment", "تعليق", "Comment")
        )
        RouterAdminModule.SCRIPTS -> listOf(
            FieldSpec("name", "الاسم", "Name"),
            FieldSpec("source", "الكود", "Source", singleLine = false),
            FieldSpec("comment", "تعليق", "Comment")
        )
        RouterAdminModule.CERTIFICATES -> listOf(
            FieldSpec("name", "الاسم", "Name"),
            FieldSpec("common-name", "Common name", "Common name"),
            FieldSpec("key-size", "Key size", "Key size"),
            FieldSpec("days-valid", "مدة الصلاحية بالأيام", "Days valid")
        )
        RouterAdminModule.PACKAGES -> emptyList()
        RouterAdminModule.FILES -> emptyList()
        RouterAdminModule.LOGS -> emptyList()
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
        RouterAdminModule.VLAN -> ModuleVisual(Icons.Outlined.AccountTree, FgPurple)
        RouterAdminModule.WIREGUARD -> ModuleVisual(Icons.Outlined.Lock, FgMint)
        RouterAdminModule.ZEROTIER -> ModuleVisual(Icons.Outlined.Public, FgCyan)
        RouterAdminModule.IP_ADDRESSES -> ModuleVisual(Icons.Outlined.Language, FgBlue)
        RouterAdminModule.ARP -> ModuleVisual(Icons.Outlined.NetworkCheck, FgAmber)
        RouterAdminModule.NEIGHBORS -> ModuleVisual(Icons.Outlined.Router, FgSilver)
        RouterAdminModule.DHCP -> ModuleVisual(Icons.Outlined.Dns, FgMint)
        RouterAdminModule.DNS -> ModuleVisual(Icons.Outlined.Public, FgCyan)
        RouterAdminModule.ROUTES -> ModuleVisual(Icons.Outlined.AccountTree, FgPurple)
        RouterAdminModule.FIREWALL -> ModuleVisual(Icons.Outlined.Security, FgAmber)
        RouterAdminModule.HOTSPOT -> ModuleVisual(Icons.Outlined.NetworkCheck, FgMagenta)
        RouterAdminModule.PPP -> ModuleVisual(Icons.Outlined.Lock, FgPurple)
        RouterAdminModule.QUEUES -> ModuleVisual(Icons.Outlined.Speed, FgAmber)
        RouterAdminModule.USERS -> ModuleVisual(Icons.Outlined.Groups, FgMint)
        RouterAdminModule.SERVICES -> ModuleVisual(Icons.Outlined.Router, FgBlue)
        RouterAdminModule.IDENTITY -> ModuleVisual(Icons.Outlined.Router, FgMint)
        RouterAdminModule.CLOCK -> ModuleVisual(Icons.Outlined.Description, FgCyan)
        RouterAdminModule.SCHEDULER -> ModuleVisual(Icons.Outlined.AccountTree, FgAmber)
        RouterAdminModule.SCRIPTS -> ModuleVisual(Icons.Outlined.Description, FgPurple)
        RouterAdminModule.CERTIFICATES -> ModuleVisual(Icons.Outlined.Security, FgMint)
        RouterAdminModule.PACKAGES -> ModuleVisual(Icons.Outlined.Description, FgSilver)
        RouterAdminModule.FILES -> ModuleVisual(Icons.Outlined.Folder, FgCyan)
        RouterAdminModule.LOGS -> ModuleVisual(Icons.Outlined.Description, FgSilver)
    }

private fun moduleTitle(module: RouterAdminModule, arabic: Boolean): String =
    when (module) {
        RouterAdminModule.INTERFACES -> if (arabic) "واجهات الشبكة" else "Interfaces"
        RouterAdminModule.WIFI -> if (arabic) "الواي فاي" else "Wi-Fi"
        RouterAdminModule.BRIDGE -> "Bridge"
        RouterAdminModule.VLAN -> "VLAN"
        RouterAdminModule.WIREGUARD -> "WireGuard"
        RouterAdminModule.ZEROTIER -> "ZeroTier"
        RouterAdminModule.IP_ADDRESSES -> if (arabic) "عناوين IP" else "IP Addresses"
        RouterAdminModule.ARP -> "ARP"
        RouterAdminModule.NEIGHBORS -> if (arabic) "الأجهزة المجاورة" else "Neighbors"
        RouterAdminModule.DHCP -> "DHCP"
        RouterAdminModule.DNS -> "DNS"
        RouterAdminModule.ROUTES -> if (arabic) "المسارات" else "Routes"
        RouterAdminModule.FIREWALL -> if (arabic) "الجدار الناري" else "Firewall"
        RouterAdminModule.HOTSPOT -> "HotSpot"
        RouterAdminModule.PPP -> "PPP"
        RouterAdminModule.QUEUES -> if (arabic) "السرعات والطوابير" else "Queues"
        RouterAdminModule.USERS -> if (arabic) "المستخدمون وAdmin" else "Users & admins"
        RouterAdminModule.SERVICES -> if (arabic) "خدمات الراوتر" else "IP Services"
        RouterAdminModule.IDENTITY -> if (arabic) "اسم الراوتر" else "Identity"
        RouterAdminModule.CLOCK -> if (arabic) "الوقت والمنطقة الزمنية" else "Clock"
        RouterAdminModule.SCHEDULER -> if (arabic) "المهام المجدولة" else "Scheduler"
        RouterAdminModule.SCRIPTS -> if (arabic) "السكربتات" else "Scripts"
        RouterAdminModule.CERTIFICATES -> if (arabic) "الشهادات" else "Certificates"
        RouterAdminModule.PACKAGES -> if (arabic) "الحزم" else "Packages"
        RouterAdminModule.FILES -> if (arabic) "الملفات" else "Files"
        RouterAdminModule.LOGS -> if (arabic) "السجل" else "Logs"
    }

private fun moduleSubtitle(module: RouterAdminModule, arabic: Boolean): String =
    when (module) {
        RouterAdminModule.INTERFACES ->
            if (arabic) "Ethernet وحالة الواجهات" else "Ethernet and interface status"
        RouterAdminModule.WIFI ->
            if (arabic) "SSID وإعدادات Wi-Fi" else "SSID and wireless settings"
        RouterAdminModule.BRIDGE ->
            if (arabic) "إنشاء وإدارة Bridge" else "Create and manage bridges"
        RouterAdminModule.VLAN ->
            if (arabic) "إنشاء وإدارة VLAN" else "Create and manage VLANs"
        RouterAdminModule.WIREGUARD ->
            if (arabic) "واجهات WireGuard" else "WireGuard interfaces"
        RouterAdminModule.ZEROTIER ->
            if (arabic) "شبكات ZeroTier" else "ZeroTier networks"
        RouterAdminModule.IP_ADDRESSES ->
            if (arabic) "العناوين والشبكات" else "Addresses and networks"
        RouterAdminModule.ARP ->
            if (arabic) "IP وMAC داخل جدول ARP" else "IP and MAC ARP table"
        RouterAdminModule.NEIGHBORS ->
            if (arabic) "اكتشاف أجهزة الشبكة للقراءة" else "Read-only neighbor discovery"
        RouterAdminModule.DHCP ->
            if (arabic) "الخوادم ومدة Lease" else "Servers and lease settings"
        RouterAdminModule.DNS ->
            if (arabic) "خوادم DNS والـCache" else "DNS servers and cache"
        RouterAdminModule.ROUTES ->
            if (arabic) "المسارات والبوابات" else "Routes and gateways"
        RouterAdminModule.FIREWALL ->
            if (arabic) "إضافة وتعديل وتعطيل القواعد" else "Add, edit and disable rules"
        RouterAdminModule.HOTSPOT ->
            if (arabic) "المستخدمون والباقات" else "Users and profiles"
        RouterAdminModule.PPP ->
            if (arabic) "الحسابات والاتصالات" else "Secrets and sessions"
        RouterAdminModule.QUEUES ->
            if (arabic) "السرعات وتحديد الباندويدث" else "Bandwidth management"
        RouterAdminModule.USERS ->
            if (arabic) "إضافة Admin وتحديد المجموعة" else "Add admins and select groups"
        RouterAdminModule.SERVICES ->
            if (arabic) "WinBox وAPI وSSH والمنافذ" else "WinBox, API, SSH and ports"
        RouterAdminModule.IDENTITY ->
            if (arabic) "تغيير اسم الراوتر" else "Change router identity"
        RouterAdminModule.CLOCK ->
            if (arabic) "التوقيت والمنطقة الزمنية" else "Time and time zone"
        RouterAdminModule.SCHEDULER ->
            if (arabic) "إنشاء وتشغيل مهام تلقائية" else "Create scheduled automation"
        RouterAdminModule.SCRIPTS ->
            if (arabic) "إدارة RouterOS Scripts" else "Manage RouterOS scripts"
        RouterAdminModule.CERTIFICATES ->
            if (arabic) "إدارة شهادات RouterOS" else "Manage RouterOS certificates"
        RouterAdminModule.PACKAGES ->
            if (arabic) "الحزم المثبتة للقراءة" else "Read installed packages"
        RouterAdminModule.FILES ->
            if (arabic) "عرض وحذف ملفات الراوتر" else "View and delete router files"
        RouterAdminModule.LOGS ->
            if (arabic) "سجل RouterOS للقراءة" else "Read-only RouterOS logs"
    }

private fun recordTitle(
    row: Map<String, String>,
    arabic: Boolean
): String =
    row["name"]
        ?: row["user"]
        ?: row["address"]
        ?: row["dst-address"]
        ?: row["message"]
        ?: row[".id"]
        ?: if (arabic) "عنصر RouterOS" else "RouterOS item"

private fun preferredDetails(
    row: Map<String, String>
): List<Pair<String, String>> {
    val keys = listOf(
        "interface", "type", "running", "disabled",
        "address", "network", "gateway", "dst-address",
        "chain", "action", "profile", "server",
        "group", "mac-address", "uptime", "rate-limit",
        "port", "size", "topics", "message"
    )
    val result = buildList {
        keys.forEach { key ->
            row[key]?.takeIf(String::isNotBlank)?.let { add(key to it) }
        }
        if (isEmpty()) {
            row.entries
                .filter { it.key != ".id" && it.key != "name" }
                .take(5)
                .forEach { add(it.key to it.value) }
        }
    }
    return result.distinctBy { it.first }
}

private fun String?.routerBool(): Boolean =
    when (this?.lowercase()) {
        "true", "yes", "1", "on" -> true
        else -> false
    }
