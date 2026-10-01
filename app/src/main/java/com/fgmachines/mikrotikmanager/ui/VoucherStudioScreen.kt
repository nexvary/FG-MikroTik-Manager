package com.fgmachines.mikrotikmanager.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.CloudUpload
import androidx.compose.material.icons.outlined.CreditCard
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.Wifi
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.fgmachines.mikrotikmanager.voucher.CsvVoucherExporter
import com.fgmachines.mikrotikmanager.voucher.HtmlVoucherExporter
import com.fgmachines.mikrotikmanager.voucher.RouterOsScriptExporter
import com.fgmachines.mikrotikmanager.voucher.VoucherBatch
import com.fgmachines.mikrotikmanager.voucher.VoucherBatchRequest
import com.fgmachines.mikrotikmanager.voucher.VoucherBranding
import com.fgmachines.mikrotikmanager.voucher.VoucherGenerator
import com.fgmachines.mikrotikmanager.voucher.VoucherMode
import com.fgmachines.mikrotikmanager.voucher.RouterVoucherProfile
import com.fgmachines.mikrotikmanager.voucher.SavedVoucherBatch
import com.fgmachines.mikrotikmanager.voucher.VoucherPasswordMode
import com.fgmachines.mikrotikmanager.voucher.VoucherProvisionSummary
import com.fgmachines.mikrotikmanager.voucher.VoucherQrCodeFactory
import com.fgmachines.mikrotikmanager.voucher.VoucherQrPayloadBuilder
import com.fgmachines.mikrotikmanager.voucher.VoucherShareManager

@Composable
fun VoucherStudioScreen(
    arabic: Boolean,
    connected: Boolean = false,
    profiles: Map<VoucherMode, List<RouterVoucherProfile>> = emptyMap(),
    profilesLoading: Boolean = false,
    provisioning: Boolean = false,
    provisionResult: VoucherProvisionSummary? = null,
    historyCount: Int = 0,
    recentBatches: List<SavedVoucherBatch> = emptyList(),
    onModeSelected: (VoucherMode) -> Unit = {},
    onBatchGenerated: (VoucherBatch) -> Unit = {},
    onProvision: (VoucherBatch) -> Unit = {},
    onClearProvisionResult: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val generator = remember { VoucherGenerator() }
    val exporter = remember { RouterOsScriptExporter() }
    val csvExporter = remember { CsvVoucherExporter() }
    val htmlExporter = remember { HtmlVoucherExporter() }

    var mode by remember { mutableStateOf(VoucherMode.HOTSPOT) }
    var quantity by remember { mutableStateOf("10") }
    var usernameLength by remember { mutableStateOf("6") }
    var passwordLength by remember { mutableStateOf("6") }
    var profile by remember { mutableStateOf("default") }
    var uptime by remember { mutableStateOf("1d") }
    var dataMb by remember { mutableStateOf("") }
    var networkName by remember { mutableStateOf("FG WiFi") }
    var supportPhone by remember { mutableStateOf("") }
    var price by remember { mutableStateOf("") }
    var samePassword by remember { mutableStateOf(true) }
    var batch by remember { mutableStateOf<VoucherBatch?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var scriptPreview by remember { mutableStateOf("") }

    Column(
        modifier = modifier
            .background(MaterialTheme.colorScheme.background)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 18.dp, vertical = 14.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        HeroHeader(arabic)

        if (recentBatches.isNotEmpty()) {
            Column(verticalArrangement = Arrangement.spacedBy(7.dp)) {
                Text(
                    if (arabic) "آخر الدفعات المحفوظة" else "Recent saved batches",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    recentBatches.take(12).forEach { saved ->
                        val savedProfile = saved.batch.request.profile.ifBlank { "—" }
                        FilterChip(
                            selected = false,
                            onClick = {
                                batch = saved.batch
                                mode = saved.batch.request.mode
                                profile = saved.batch.request.profile
                                scriptPreview = exporter.export(saved.batch)
                                onClearProvisionResult()
                            },
                            label = {
                                Text(
                                    modeLabel(saved.batch.request.mode, arabic) +
                                        " • " + saved.batch.vouchers.size +
                                        " • " + savedProfile
                                )
                            },
                            leadingIcon = {
                                Icon(Icons.Outlined.History, contentDescription = null)
                            }
                        )
                    }
                }
            }
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            VoucherMode.entries.forEach { item ->
                FilterChip(
                    selected = mode == item,
                    onClick = {
                        mode = item
                        batch = null
                        scriptPreview = ""
                        onClearProvisionResult()
                        onModeSelected(item)
                        profiles[item]?.firstOrNull()?.let { selected ->
                            profile = selected.name
                        }
                    },
                    label = { Text(modeLabel(item, arabic)) },
                    leadingIcon = {
                        Icon(
                            imageVector = when (item) {
                                VoucherMode.HOTSPOT -> Icons.Outlined.Wifi
                                VoucherMode.USER_MANAGER -> Icons.Outlined.Person
                                VoucherMode.PPPOE -> Icons.Outlined.Lock
                                VoucherMode.OFFLINE -> Icons.Outlined.CreditCard
                            },
                            contentDescription = null
                        )
                    }
                )
            }
        }

        val currentProfiles = profiles[mode].orEmpty()
        if (profilesLoading && mode != VoucherMode.OFFLINE) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                CircularProgressIndicator(
                    modifier = Modifier.width(18.dp).height(18.dp),
                    strokeWidth = 2.dp
                )
                Text(
                    if (arabic) "جاري قراءة الباقات من الراوتر..."
                    else "Loading profiles from router...",
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        } else if (currentProfiles.isNotEmpty()) {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    if (arabic) "الباقات الموجودة على الراوتر" else "Router profiles",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    currentProfiles.forEach { item ->
                        FilterChip(
                            selected = profile == item.name,
                            onClick = { profile = item.name },
                            label = {
                                Text(
                                    if (item.rateLimit.isBlank()) item.name
                                    else item.name + " • " + item.rateLimit
                                )
                            }
                        )
                    }
                }
            }
        }

        Card(
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.96f)
            ),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.45f)),
            shape = RoundedCornerShape(18.dp)
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = if (arabic) "إعداد دفعة الكروت" else "Voucher batch setup",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )

                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedTextField(
                        value = quantity,
                        onValueChange = { quantity = it.filter(Char::isDigit).take(4) },
                        modifier = Modifier.weight(1f),
                        label = { Text(if (arabic) "العدد" else "Quantity") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true
                    )
                    OutlinedTextField(
                        value = usernameLength,
                        onValueChange = { usernameLength = it.filter(Char::isDigit).take(2) },
                        modifier = Modifier.weight(1f),
                        label = { Text(if (arabic) "طول الكود" else "Code length") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true
                    )
                }

                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedTextField(
                        value = profile,
                        onValueChange = { profile = it },
                        modifier = Modifier.weight(1f),
                        label = { Text(if (arabic) "البروفايل" else "Profile") },
                        singleLine = true
                    )
                    OutlinedTextField(
                        value = uptime,
                        onValueChange = { uptime = it },
                        modifier = Modifier.weight(1f),
                        label = { Text(if (arabic) "المدة" else "Time limit") },
                        singleLine = true
                    )
                }

                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedTextField(
                        value = dataMb,
                        onValueChange = { dataMb = it.filter(Char::isDigit) },
                        modifier = Modifier.weight(1f),
                        label = { Text(if (arabic) "الحجم MB" else "Data MB") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true
                    )
                    OutlinedTextField(
                        value = price,
                        onValueChange = { price = it },
                        modifier = Modifier.weight(1f),
                        label = { Text(if (arabic) "السعر" else "Price") },
                        singleLine = true
                    )
                }

                OutlinedTextField(
                    value = networkName,
                    onValueChange = { networkName = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text(if (arabic) "اسم الشبكة على الكارت" else "Network name on card") },
                    singleLine = true
                )

                OutlinedTextField(
                    value = supportPhone,
                    onValueChange = { supportPhone = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text(if (arabic) "رقم الدعم" else "Support phone") },
                    singleLine = true
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    FilterChip(
                        selected = samePassword,
                        onClick = { samePassword = true },
                        label = {
                            Text(
                                if (arabic) "الكود = الباسورد"
                                else "Code = password"
                            )
                        }
                    )
                    FilterChip(
                        selected = !samePassword,
                        onClick = { samePassword = false },
                        label = {
                            Text(
                                if (arabic) "باسورد مختلف"
                                else "Separate password"
                            )
                        }
                    )
                }

                if (!samePassword) {
                    OutlinedTextField(
                        value = passwordLength,
                        onValueChange = { passwordLength = it.filter(Char::isDigit).take(2) },
                        modifier = Modifier.fillMaxWidth(),
                        label = {
                            Text(
                                if (arabic) "طول الباسورد"
                                else "Password length"
                            )
                        },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true
                    )
                }

                error?.let {
                    Text(
                        text = it,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodyMedium
                    )
                }

                Button(
                    onClick = {
                        try {
                            val request = VoucherBatchRequest(
                                quantity = quantity.toIntOrNull() ?: 10,
                                usernameLength = usernameLength.toIntOrNull() ?: 6,
                                passwordMode = if (samePassword) {
                                    VoucherPasswordMode.SAME_AS_USERNAME
                                } else {
                                    VoucherPasswordMode.RANDOM
                                },
                                passwordLength = passwordLength.toIntOrNull() ?: 6,
                                mode = mode,
                                profile = if (mode == VoucherMode.OFFLINE) {
                                    profile.ifBlank { "offline" }
                                } else {
                                    profile
                                },
                                limitUptime = uptime.ifBlank { null },
                                limitBytesTotal = dataMb.toLongOrNull()?.times(1024L * 1024L),
                                branding = VoucherBranding(
                                    networkName = networkName,
                                    supportPhone = supportPhone,
                                    priceText = price
                                )
                            )
                            val generated = generator.generate(request)
                            batch = generated
                            scriptPreview = exporter.export(generated)
                            onBatchGenerated(generated)
                            onClearProvisionResult()
                            error = null
                        } catch (t: Throwable) {
                            error = t.message ?: "Invalid voucher settings"
                            batch = null
                            scriptPreview = ""
                        }
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Outlined.CreditCard, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text(
                        if (arabic) "إنشاء الكروت والمعاينة"
                        else "Generate vouchers & preview"
                    )
                }
            }
        }

        batch?.let { generated ->
            val first = generated.vouchers.firstOrNull()
            if (first != null) {
                VoucherPreview(
                    arabic = arabic,
                    networkName = first.branding.networkName,
                    username = first.username,
                    password = first.password,
                    profile = first.profile,
                    uptime = first.limitUptime.orEmpty(),
                    price = first.branding.priceText,
                    supportPhone = first.branding.supportPhone,
                    count = generated.vouchers.size
                )
            }
        }

        batch?.let { generated ->
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.96f)
                ),
                border = BorderStroke(
                    1.dp,
                    MaterialTheme.colorScheme.primary.copy(alpha = 0.42f)
                ),
                shape = RoundedCornerShape(18.dp)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(Icons.Outlined.History, contentDescription = null)
                            Text(
                                if (arabic) "دفعات محفوظة: ${historyCount}"
                                else "Saved batches: ${historyCount}",
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                        Text(
                            if (arabic) "الحفظ مشفّر" else "Encrypted",
                            color = MaterialTheme.colorScheme.primary,
                            style = MaterialTheme.typography.labelMedium
                        )
                    }

                    if (connected && generated.request.mode != VoucherMode.OFFLINE) {
                        Button(
                            onClick = { onProvision(generated) },
                            enabled = !provisioning,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            if (provisioning) {
                                CircularProgressIndicator(
                                    modifier = Modifier.width(18.dp).height(18.dp),
                                    strokeWidth = 2.dp
                                )
                            } else {
                                Icon(Icons.Outlined.CloudUpload, contentDescription = null)
                            }
                            Spacer(Modifier.width(8.dp))
                            Text(
                                if (arabic) {
                                    if (provisioning) "جاري رفع الكروت..." else "رفع ${generated.vouchers.size} كارت إلى الراوتر"
                                } else {
                                    if (provisioning) "Provisioning vouchers..." else "Provision ${generated.vouchers.size} vouchers to router"
                                }
                            )
                        }
                    } else {
                        Text(
                            if (arabic) {
                                "تم حفظ الدفعة محليًا ويمكن تصديرها أو رفعها لاحقًا."
                            } else {
                                "Batch saved locally and can be exported or provisioned later."
                            },
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }

        provisionResult?.let { result ->
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = if (result.failed == 0) {
                        MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f)
                    } else {
                        MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.45f)
                    }
                ),
                border = BorderStroke(
                    1.dp,
                    if (result.failed == 0) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.error
                ),
                shape = RoundedCornerShape(18.dp)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(Icons.Outlined.CheckCircle, contentDescription = null)
                        Text(
                            if (arabic) "نتيجة الرفع" else "Provision result",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    Text(
                        if (arabic) {
                            "تم إنشاء ${result.created} • مكرر ${result.duplicates} • فشل ${result.failed}"
                        } else {
                            "Created ${result.created} • duplicates ${result.duplicates} • failed ${result.failed}"
                        }
                    )
                    result.items.firstOrNull { it.status.name == "FAILED" }?.let { failed ->
                        Text(
                            failed.username + ": " + failed.message,
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                    OutlinedButton(onClick = onClearProvisionResult) {
                        Text(if (arabic) "إغلاق النتيجة" else "Dismiss")
                    }
                }
            }
        }

        if (scriptPreview.isNotBlank()) {
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f)
                ),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.35f)),
                shape = RoundedCornerShape(16.dp)
            ) {
                Column(
                    modifier = Modifier.padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        if (arabic) "معاينة ملف RouterOS (.rsc)" else "RouterOS script preview (.rsc)",
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        scriptPreview.lineSequence().take(7).joinToString("\n"),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    OutlinedButton(
                        onClick = {
                            scriptPreview = exporter.export(batch!!)
                        }
                    ) {
                        Text(if (arabic) "تحديث المعاينة" else "Refresh preview")
                    }

                    batch?.let { generated ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            OutlinedButton(
                                onClick = {
                                    VoucherShareManager.shareTextFile(
                                        context = context,
                                        fileName = "vouchers.rsc",
                                        content = exporter.export(generated),
                                        mimeType = "text/plain"
                                    )
                                }
                            ) {
                                Text("RSC")
                            }
                            OutlinedButton(
                                onClick = {
                                    VoucherShareManager.shareTextFile(
                                        context = context,
                                        fileName = "vouchers.csv",
                                        content = csvExporter.export(generated),
                                        mimeType = "text/csv"
                                    )
                                }
                            ) {
                                Text("CSV")
                            }
                            OutlinedButton(
                                onClick = {
                                    VoucherShareManager.shareTextFile(
                                        context = context,
                                        fileName = "vouchers.html",
                                        content = htmlExporter.export(generated),
                                        mimeType = "text/html"
                                    )
                                }
                            ) {
                                Text(if (arabic) "HTML للطباعة" else "Print HTML")
                            }
                        }
                    }
                }
            }
        }

        Spacer(Modifier.height(12.dp))
    }
}

@Composable
private fun HeroHeader(arabic: Boolean) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                brush = Brush.horizontalGradient(
                    listOf(
                        MaterialTheme.colorScheme.primary.copy(alpha = 0.28f),
                        MaterialTheme.colorScheme.surface,
                        MaterialTheme.colorScheme.tertiary.copy(alpha = 0.20f)
                    )
                ),
                shape = RoundedCornerShape(22.dp)
            )
            .padding(18.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Box(
                modifier = Modifier
                    .background(
                        MaterialTheme.colorScheme.primary.copy(alpha = 0.18f),
                        RoundedCornerShape(16.dp)
                    )
                    .padding(12.dp),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.Outlined.CreditCard,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary
                )
            }
            Column {
                Text(
                    text = if (arabic) "استوديو كروت ميكروتيك" else "MikroTik Voucher Studio",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Black
                )
                Text(
                    text = if (arabic) {
                        "HotSpot • User Manager • PPPoE • Offline"
                    } else {
                        "HotSpot • User Manager • PPPoE • Offline"
                    },
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun VoucherPreview(
    arabic: Boolean,
    networkName: String,
    username: String,
    password: String,
    profile: String,
    uptime: String,
    price: String,
    supportPhone: String,
    count: Int
) {
    val qrBitmap = remember(username, password, networkName, profile, supportPhone) {
        val previewVoucher = com.fgmachines.mikrotikmanager.voucher.VoucherDraft(
            username = username,
            password = password,
            profile = profile,
            server = "all",
            comment = "",
            limitUptime = uptime.ifBlank { null },
            limitBytesTotal = null,
            branding = VoucherBranding(
                networkName = networkName,
                supportPhone = supportPhone,
                priceText = price
            )
        )
        VoucherQrCodeFactory.create(
            VoucherQrPayloadBuilder.build(previewVoucher),
            size = 260
        )
    }

    Card(
        colors = CardDefaults.cardColors(containerColor = Color(0xFF111A22)),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.tertiary.copy(alpha = 0.75f)),
        shape = RoundedCornerShape(20.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    Brush.linearGradient(
                        listOf(
                            Color(0xFF0C1319),
                            Color(0xFF17232E),
                            Color(0xFF0F171E)
                        )
                    )
                )
                .padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        networkName.ifBlank {
                            if (arabic) "شبكة WiFi" else "WiFi Network"
                        },
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Black
                    )
                    Text(
                        if (arabic) "معاينة الكارت • عدد الدفعة $count"
                        else "Card preview • batch $count",
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Icon(
                    Icons.Outlined.Wifi,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary
                )
            }

            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                CredentialBox(
                    modifier = Modifier.weight(1f),
                    icon = { Icon(Icons.Outlined.Person, contentDescription = null) },
                    label = if (arabic) "اسم المستخدم" else "Username",
                    value = username
                )
                CredentialBox(
                    modifier = Modifier.weight(1f),
                    icon = { Icon(Icons.Outlined.Lock, contentDescription = null) },
                    label = if (arabic) "كلمة المرور" else "Password",
                    value = password
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    (if (arabic) "الباقة: " else "Plan: ") + profile,
                    color = MaterialTheme.colorScheme.secondary
                )
                if (uptime.isNotBlank()) {
                    Text(
                        (if (arabic) "المدة: " else "Time: ") + uptime,
                        color = MaterialTheme.colorScheme.secondary
                    )
                }
            }

            Image(
                bitmap = qrBitmap.asImageBitmap(),
                contentDescription = if (arabic) "رمز QR للكارت" else "Voucher QR code",
                modifier = Modifier
                    .size(132.dp)
                    .align(Alignment.CenterHorizontally)
            )
            Text(
                if (arabic) "امسح QR لعرض بيانات الكارت" else "Scan QR to view voucher credentials",
                modifier = Modifier.align(Alignment.CenterHorizontally),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            if (price.isNotBlank() || supportPhone.isNotBlank()) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    if (price.isNotBlank()) {
                        Text(
                            price,
                            color = MaterialTheme.colorScheme.tertiary,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    if (supportPhone.isNotBlank()) {
                        Text(
                            supportPhone,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun CredentialBox(
    modifier: Modifier,
    icon: @Composable () -> Unit,
    label: String,
    value: String
) {
    Column(
        modifier = modifier
            .background(
                MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
                RoundedCornerShape(14.dp)
            )
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            icon()
            Text(
                label,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Text(
            value,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold
        )
    }
}

private fun modeLabel(mode: VoucherMode, arabic: Boolean): String =
    when (mode) {
        VoucherMode.HOTSPOT -> if (arabic) "هوت سبوت" else "HotSpot"
        VoucherMode.USER_MANAGER -> if (arabic) "يوزر مانجر" else "User Manager"
        VoucherMode.PPPOE -> "PPPoE"
        VoucherMode.OFFLINE -> if (arabic) "بدون راوتر" else "Offline"
    }
