package com.fgmachines.mikrotikmanager.ui

import android.app.DatePickerDialog
import android.app.TimePickerDialog
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.CloudUpload
import androidx.compose.material.icons.outlined.CreditCard
import androidx.compose.material.icons.outlined.Print
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material.icons.outlined.Wifi
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.fgmachines.mikrotikmanager.voucher.CsvVoucherExporter
import com.fgmachines.mikrotikmanager.voucher.HtmlVoucherExporter
import com.fgmachines.mikrotikmanager.voucher.RouterOsScriptExporter
import com.fgmachines.mikrotikmanager.voucher.RouterVoucherProfile
import com.fgmachines.mikrotikmanager.voucher.SavedVoucherBatch
import com.fgmachines.mikrotikmanager.voucher.VoucherBatch
import com.fgmachines.mikrotikmanager.voucher.VoucherBatchRequest
import com.fgmachines.mikrotikmanager.voucher.VoucherBranding
import com.fgmachines.mikrotikmanager.voucher.VoucherGenerator
import com.fgmachines.mikrotikmanager.voucher.VoucherMode
import com.fgmachines.mikrotikmanager.voucher.VoucherPasswordMode
import com.fgmachines.mikrotikmanager.voucher.VoucherPdfExporter
import com.fgmachines.mikrotikmanager.voucher.VoucherProvisionSummary
import com.fgmachines.mikrotikmanager.voucher.VoucherQrCodeFactory
import com.fgmachines.mikrotikmanager.voucher.VoucherQrPayloadBuilder
import com.fgmachines.mikrotikmanager.voucher.VoucherShareManager
import com.fgmachines.mikrotikmanager.voucher.VoucherTimeUnit
import kotlinx.coroutines.launch
import java.text.DecimalFormat
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

@Composable
fun VoucherStudioScreen(
    arabic: Boolean,
    hotspotManager: com.fgmachines.mikrotikmanager.hotspot.HotspotManager? = null,
    advancedManager: com.fgmachines.mikrotikmanager.advanced.AdvancedRouterManager? = null,
    onOpenAdvanced: () -> Unit = {},
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
    val scope = rememberCoroutineScope()

    val generator = remember { VoucherGenerator() }
    val scriptExporter = remember { RouterOsScriptExporter() }
    val csvExporter = remember { CsvVoucherExporter() }
    val htmlExporter = remember { HtmlVoucherExporter() }
    val pdfExporter = remember { VoucherPdfExporter() }

    var preflight by remember { mutableStateOf<com.fgmachines.mikrotikmanager.advanced.ReadinessReport?>(null) }
    var checkingPreflight by remember { mutableStateOf(false) }
    androidx.compose.runtime.LaunchedEffect(advancedManager) {
        if (advancedManager != null) { checkingPreflight = true; try { preflight = advancedManager.preflight() } finally { checkingPreflight = false } }
    }
    var toolsTab by remember { mutableStateOf<String?>(null) }
    var portalUrl by rememberSaveable { mutableStateOf("") }
    var mode by rememberSaveable { mutableStateOf(VoucherMode.HOTSPOT) }
    var quantity by rememberSaveable { mutableStateOf("10") }
    var usernameLength by rememberSaveable { mutableStateOf("6") }
    var passwordLength by rememberSaveable { mutableStateOf("6") }
    var samePassword by rememberSaveable { mutableStateOf(true) }

    var profile by rememberSaveable { mutableStateOf("default") }
    var durationValue by rememberSaveable { mutableStateOf("60") }
    var durationUnit by rememberSaveable { mutableStateOf(VoucherTimeUnit.MINUTES) }
    var dataMb by rememberSaveable { mutableStateOf("") }
    var priceEgp by rememberSaveable { mutableStateOf("5") }
    var networkName by rememberSaveable { mutableStateOf("FG WiFi") }
    var supportPhone by rememberSaveable { mutableStateOf("") }

    androidx.compose.runtime.LaunchedEffect(hotspotManager) {
        val designPrefs = context.getSharedPreferences("fg_portal_design", 0)
        runCatching {
            kotlinx.serialization.json.Json.decodeFromString<com.fgmachines.mikrotikmanager.hotspot.PortalDesign>(
                designPrefs.getString("design", null).orEmpty()
            )
        }.getOrNull()?.let { design ->
            networkName = design.networkName
            supportPhone = design.supportPhone
        }
        if (portalUrl.isBlank() && hotspotManager != null) {
            runCatching { hotspotManager.serverProfiles() }.getOrNull()
                ?.firstOrNull { !it["dns-name"].isNullOrBlank() }
                ?.get("dns-name")?.let { portalUrl = "http://$it/login" }
        }
    }

    var expiryEnabled by rememberSaveable { mutableStateOf(true) }
    var expiryEpochMs by rememberSaveable {
        mutableStateOf(System.currentTimeMillis() + 24L * 60L * 60L * 1000L)
    }

    var batch by remember { mutableStateOf<VoucherBatch?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var advancedOpen by rememberSaveable { mutableStateOf(false) }
    var pdfExporting by remember { mutableStateOf(false) }

    fun chooseExpiry() {
        val calendar = Calendar.getInstance().apply {
            timeInMillis = expiryEpochMs
        }

        DatePickerDialog(
            context,
            { _, year, month, day ->
                val selected = Calendar.getInstance().apply {
                    timeInMillis = expiryEpochMs
                    set(Calendar.YEAR, year)
                    set(Calendar.MONTH, month)
                    set(Calendar.DAY_OF_MONTH, day)
                }

                TimePickerDialog(
                    context,
                    { _, hour, minute ->
                        selected.set(Calendar.HOUR_OF_DAY, hour)
                        selected.set(Calendar.MINUTE, minute)
                        selected.set(Calendar.SECOND, 0)
                        selected.set(Calendar.MILLISECOND, 0)
                        expiryEpochMs = selected.timeInMillis
                        expiryEnabled = true
                    },
                    selected.get(Calendar.HOUR_OF_DAY),
                    selected.get(Calendar.MINUTE),
                    false
                ).show()
            },
            calendar.get(Calendar.YEAR),
            calendar.get(Calendar.MONTH),
            calendar.get(Calendar.DAY_OF_MONTH)
        ).show()
    }

    toolsTab?.let { tab ->
        HotspotToolsScreen(arabic, hotspotManager, recentBatches, tab, { toolsTab = null }, advancedManager=advancedManager, onAdvancedSetup={toolsTab=null;onOpenAdvanced()})
    }
    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .background(FgBlack),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(14.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        item {
            VoucherStudioHero(
                arabic = arabic,
                connected = connected,
                mode = mode,
                onModeChange = { item ->
                    mode = item
                    batch = null
                    onClearProvisionResult()
                    onModeSelected(item)
                    profiles[item]?.firstOrNull()?.let {
                        profile = it.name
                    }
                }
            )
        }

        if (connected && mode == VoucherMode.HOTSPOT) item {
            Text(if(checkingPreflight) { if(arabic) "جاري فحص جاهزية HotSpot..." else "Checking HotSpot readiness..." } else if(preflight?.ready == true) { if(arabic) "HotSpot جاهز ✓" else "HotSpot ready ✓" } else { if(arabic) "الراوتر غير جاهز لتفعيل كروت HotSpot" else "Router is not ready to activate HotSpot vouchers" }, color = if(preflight?.ready == true) FgMint else FgAmber)
            if(preflight?.ready != true && !checkingPreflight) OutlinedButton(onClick = onOpenAdvanced, modifier = Modifier.fillMaxWidth()) { Text(if(arabic) "فتح الإعداد المتقدم" else "Open Advanced Setup") }
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                OutlinedButton(onClick = { toolsTab = "design" }, modifier = Modifier.weight(1f)) { Text(if(arabic) "صفحة العملاء" else "Customer portal") }
                OutlinedButton(onClick = { toolsTab = "active" }, enabled = connected, modifier = Modifier.weight(1f)) { Text(if(arabic) "الكروت النشطة" else "Active vouchers") }
            }
            OutlinedButton(onClick = onOpenAdvanced, enabled = connected, modifier = Modifier.fillMaxWidth()) { Text(if(arabic) "إعداد HotSpot" else "Set up HotSpot") }
        }
        item {
            NeonCard(accent = FgBlue) {
                Text(
                    if (arabic) "إعدادات الكروت" else "Voucher settings",
                    color = FgBlue,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Black
                )

                Row(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                    CompactVoucherField(
                        value = quantity,
                        onValueChange = { quantity = it.filter(Char::isDigit).take(4) },
                        modifier = Modifier
                            .weight(1f)
                            ,
                        label = {
                            Text(
                                if (arabic) "العدد" else "Quantity",
                                style = MaterialTheme.typography.labelSmall
                            )
                        },
                        textStyle = MaterialTheme.typography.bodyMedium,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true
                    )
                    CompactVoucherField(
                        value = usernameLength,
                        onValueChange = { usernameLength = it.filter(Char::isDigit).take(2) },
                        modifier = Modifier
                            .weight(1f)
                            ,
                        label = {
                            Text(
                                if (arabic) "طول الكود" else "Code length",
                                style = MaterialTheme.typography.labelSmall
                            )
                        },
                        textStyle = MaterialTheme.typography.bodyMedium,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true
                    )
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(7.dp)
                ) {
                    CompactVoucherField(
                        value = durationValue,
                        onValueChange = { durationValue = it.filter(Char::isDigit).take(5) },
                        modifier = Modifier
                            .weight(0.8f)
                            ,
                        label = {
                            Text(
                                if (arabic) "المدة" else "Duration",
                                style = MaterialTheme.typography.labelSmall
                            )
                        },
                        textStyle = MaterialTheme.typography.bodyMedium,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true
                    )

                    Row(
                        modifier = Modifier
                            .weight(1.8f)
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(5.dp)
                    ) {
                        VoucherTimeUnit.entries.forEach { unit ->
                            FilterChip(
                                selected = durationUnit == unit,
                                onClick = { durationUnit = unit },
                                label = {
                                    Text(
                                        timeUnitLabel(unit, arabic),
                                        style = MaterialTheme.typography.labelSmall
                                    )
                                }
                            )
                        }
                    }
                }

                Row(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                    CompactVoucherField(
                        value = priceEgp,
                        onValueChange = {
                            priceEgp = it.filter { ch -> ch.isDigit() || ch == '.' }.take(9)
                        },
                        modifier = Modifier
                            .weight(1f)
                            ,
                        label = {
                            Text(
                                if (arabic) "السعر جنيه" else "Price EGP",
                                style = MaterialTheme.typography.labelSmall
                            )
                        },
                        textStyle = MaterialTheme.typography.bodyMedium,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        singleLine = true
                    )

                    CompactVoucherField(
                        value = dataMb,
                        onValueChange = { dataMb = it.filter(Char::isDigit).take(8) },
                        modifier = Modifier
                            .weight(1f)
                            ,
                        label = {
                            Text(
                                if (arabic) "البيانات MB" else "Data MB",
                                style = MaterialTheme.typography.labelSmall
                            )
                        },
                        textStyle = MaterialTheme.typography.bodyMedium,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            if (arabic) "انتهاء الكارت" else "Voucher expiry",
                            color = FgWhite,
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            if (expiryEnabled) formatExpiry(expiryEpochMs, arabic)
                            else if (arabic) "بدون موعد انتهاء" else "No absolute expiry",
                            color = if (expiryEnabled) FgAmber else FgSilverMuted,
                            style = MaterialTheme.typography.labelSmall
                        )
                    }

                    Switch(
                        checked = expiryEnabled,
                        onCheckedChange = { expiryEnabled = it }
                    )
                }

                if (expiryEnabled) {
                    OutlinedButton(
                        onClick = { chooseExpiry() },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(42.dp),
                        border = BorderStroke(1.2.dp, FgBlue)
                    ) {
                        Icon(
                            Icons.Outlined.Schedule,
                            contentDescription = null,
                            tint = FgBlue,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(Modifier.width(6.dp))
                        Text(
                            if (arabic) "تاريخ ووقت الانتهاء" else "Expiry date & time",
                            color = FgBlue,
                            style = MaterialTheme.typography.labelMedium
                        )
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    FilterChip(
                        selected = samePassword,
                        onClick = { samePassword = true },
                        label = {
                            Text(
                                if (arabic) "الكود = الباسورد" else "Code = password",
                                style = MaterialTheme.typography.labelSmall
                            )
                        }
                    )
                    FilterChip(
                        selected = !samePassword,
                        onClick = { samePassword = false },
                        label = {
                            Text(
                                if (arabic) "باسورد مختلف" else "Separate password",
                                style = MaterialTheme.typography.labelSmall
                            )
                        }
                    )
                }

                if (!samePassword) {
                    CompactVoucherField(
                        value = passwordLength,
                        onValueChange = { passwordLength = it.filter(Char::isDigit).take(2) },
                        modifier = Modifier
                            .fillMaxWidth()
                            ,
                        label = {
                            Text(
                                if (arabic) "طول كلمة المرور" else "Password length",
                                style = MaterialTheme.typography.labelSmall
                            )
                        },
                        textStyle = MaterialTheme.typography.bodyMedium,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true
                    )
                }
            }
        }

        if (mode != VoucherMode.OFFLINE) {
            item {
                NeonCard(accent = FgBlue) {
                    Text(
                        if (arabic) "الباقة على الراوتر" else "Router profile",
                        color = FgWhite,
                        fontWeight = FontWeight.Bold
                    )

                    val currentProfiles = profiles[mode].orEmpty()

                    if (profilesLoading) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(18.dp),
                                strokeWidth = 2.dp,
                                color = FgMint
                            )
                            Text(
                                if (arabic) "جاري قراءة الباقات..." else "Loading profiles...",
                                color = FgSilver
                            )
                        }
                    } else if (currentProfiles.isNotEmpty()) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(7.dp)
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
                    } else {
                        CompactVoucherField(
                            value = profile,
                            onValueChange = { profile = it },
                            modifier = Modifier.fillMaxWidth(),
                            label = { Text(if (arabic) "اسم الباقة" else "Profile") },
                            singleLine = true
                        )
                    }
                }
            }
        }

        item {
            NeonCard(accent = FgBlue) {
                Text(
                    if (arabic) "بيانات تظهر على الكارت" else "Printed card details",
                    color = FgWhite,
                    fontWeight = FontWeight.Bold
                )

                CompactVoucherField(
                    value = networkName,
                    onValueChange = { networkName = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text(if (arabic) "اسم الشبكة" else "Network name") },
                    singleLine = true
                )

                if (mode == VoucherMode.HOTSPOT) {
                    CompactVoucherField(portalUrl, { portalUrl = it.trim() }, Modifier.fillMaxWidth(), { Text(if(arabic) "رابط دخول الشبكة للـQR" else "HotSpot login URL for QR") })
                    Text(if(arabic) "مثال: http://wifi.local/login — لا تضع كلمة مرور في الرابط. يجب تثبيت صفحة العملاء أولًا." else "Example: http://wifi.local/login — no passwords in the URL. Install the customer portal first.", color = FgSilver, style = MaterialTheme.typography.bodySmall)
                }
                CompactVoucherField(
                    value = supportPhone,
                    onValueChange = { supportPhone = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text(if (arabic) "رقم الدعم - اختياري" else "Support phone - optional") },
                    singleLine = true
                )
            }
        }

        error?.let { message ->
            item {
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.errorContainer
                    ),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.error)
                ) {
                    Text(
                        message,
                        modifier = Modifier.padding(14.dp),
                        color = MaterialTheme.colorScheme.onErrorContainer
                    )
                }
            }
        }

        item {
            Button(
                onClick = {
                    try {
                        val value = durationValue.toIntOrNull()
                            ?: throw IllegalArgumentException(
                                if (arabic) "اكتب مدة صحيحة" else "Enter a valid duration"
                            )

                        val expiry = if (expiryEnabled) expiryEpochMs else null
                        if (expiry != null && expiry <= System.currentTimeMillis()) {
                            throw IllegalArgumentException(
                                if (arabic) "وقت الانتهاء يجب أن يكون في المستقبل"
                                else "Expiry must be in the future"
                            )
                        }

                        val price = priceEgp.toDoubleOrNull()

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
                            durationValue = value,
                            durationUnit = durationUnit,
                            absoluteExpiryEpochMs = expiry,
                            limitBytesTotal = dataMb.toLongOrNull()?.times(1024L * 1024L),
                            branding = VoucherBranding(
                                networkName = networkName,
                                supportPhone = supportPhone,
                                priceEgp = price,
                                portalLoginUrl = portalUrl
                            )
                        )

                        val generated = generator.generate(request)
                        generated.vouchers.firstOrNull()?.let { VoucherQrPayloadBuilder.build(it) }
                        batch = generated
                        onBatchGenerated(generated)
                        onClearProvisionResult()
                        error = null

                        if (connected && mode != VoucherMode.OFFLINE) {
                            onProvision(generated)
                        }
                    } catch (t: Throwable) {
                        error = t.message ?: "Invalid voucher settings"
                    }
                },
                enabled = !provisioning && !(connected && mode == VoucherMode.HOTSPOT && preflight?.ready != true),
                modifier = Modifier.fillMaxWidth()
            ) {
                if (provisioning) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(18.dp),
                        strokeWidth = 2.dp
                    )
                } else {
                    Icon(
                        if (connected && mode != VoucherMode.OFFLINE) {
                            Icons.Outlined.CloudUpload
                        } else {
                            Icons.Outlined.CreditCard
                        },
                        contentDescription = null
                    )
                }

                Spacer(Modifier.width(8.dp))

                Text(
                    when {
                        provisioning ->
                            if (arabic) "جاري التفعيل على الراوتر..." else "Provisioning..."
                        connected && mode != VoucherMode.OFFLINE ->
                            if (arabic) "إنشاء وتفعيل على الراوتر" else "Create & activate on router"
                        else ->
                            if (arabic) "إنشاء وحفظ الكروت" else "Create & save vouchers"
                    }
                )
            }
        }

        provisionResult?.let { result ->
            item {
                StatusCard(
                    result = result,
                    arabic = arabic,
                    onDismiss = onClearProvisionResult
                )
            }
        }

        batch?.vouchers?.firstOrNull()?.let { voucher ->
            item {
                VoucherPreview(
                    voucher = voucher,
                    count = batch?.vouchers?.size ?: 0,
                    arabic = arabic
                )
            }
        }

        if (batch != null) {
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf("PDF A4", "Thermal", "PNG").forEach { format ->
                        OutlinedButton(onClick = {
                            scope.launch {
                                runCatching {
                                    val generated = batch!!
                                    val file = if(format == "PNG") com.fgmachines.mikrotikmanager.voucher.VoucherImageExporter.export(context,generated.vouchers.first()) else pdfExporter.export(context,generated,"vouchers.pdf",thermal = format == "Thermal")
                                    VoucherShareManager.shareFile(context,file,if(format == "PNG") "image/png" else "application/pdf")
                                }.onFailure { error = it.message }
                            }
                        }, modifier = Modifier.weight(1f), contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 4.dp, vertical = 8.dp)) { Text(format) }
                    }
                }
            }
            item {
                NeonCard(accent = FgBlue) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column {
                            Text(
                                if (arabic) "خيارات متقدمة" else "Advanced options",
                                color = FgWhite,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                if (arabic) "التصدير والطباعة فقط" else "Export and printing only",
                                color = FgSilverMuted,
                                style = MaterialTheme.typography.bodySmall
                            )
                        }

                        OutlinedButton(onClick = { advancedOpen = !advancedOpen }) {
                            Text(
                                if (advancedOpen) {
                                    if (arabic) "إخفاء" else "Hide"
                                } else {
                                    if (arabic) "فتح" else "Open"
                                }
                            )
                        }
                    }

                    if (advancedOpen) {
                        val generated = batch!!

                        HorizontalDivider(color = FgSilverMuted.copy(alpha = 0.3f))

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(7.dp)
                        ) {
                            OutlinedButton(
                                onClick = {
                                    VoucherShareManager.shareTextFile(
                                        context,
                                        "vouchers.rsc",
                                        scriptExporter.export(generated),
                                        "text/plain"
                                    )
                                }
                            ) { Text("RSC") }

                            OutlinedButton(
                                onClick = {
                                    VoucherShareManager.shareTextFile(
                                        context,
                                        "vouchers.csv",
                                        csvExporter.export(generated),
                                        "text/csv"
                                    )
                                }
                            ) { Text("CSV") }

                            OutlinedButton(
                                onClick = {
                                    VoucherShareManager.shareTextFile(
                                        context,
                                        "vouchers.html",
                                        htmlExporter.export(generated),
                                        "text/html"
                                    )
                                }
                            ) {
                                Icon(Icons.Outlined.Print, contentDescription = null)
                                Spacer(Modifier.width(5.dp))
                                Text("HTML")
                            }

                            OutlinedButton(
                                onClick = {
                                    scope.launch {
                                        pdfExporting = true
                                        runCatching {
                                            pdfExporter.export(context, generated, "vouchers.pdf")
                                        }.onSuccess { file ->
                                            VoucherShareManager.shareFile(
                                                context,
                                                file,
                                                "application/pdf"
                                            )
                                        }.onFailure {
                                            error = it.message ?: "PDF export failed"
                                        }
                                        pdfExporting = false
                                    }
                                },
                                enabled = !pdfExporting
                            ) {
                                if (pdfExporting) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(15.dp),
                                        strokeWidth = 2.dp
                                    )
                                } else {
                                    Text("PDF")
                                }
                            }
                        }
                    }
                }
            }
        }

        if (historyCount > 0) {
            item {
                Text(
                    if (arabic) {
                        "الدفعات المحفوظة مشفّرة على الهاتف: ${historyCount}"
                    } else {
                        "Encrypted saved batches on phone: ${historyCount}"
                    },
                    color = FgSilverMuted,
                    style = MaterialTheme.typography.bodySmall
                )
            }
        }

        item {
            if (batch != null) {
                if (mode == VoucherMode.OFFLINE) Text(if(arabic) "الكارت غير مفعّل على راوتر" else "Voucher is not activated on a router", color = FgAmber)
                if (mode == VoucherMode.PPPOE) Text(if(arabic) "QR لبيانات إعداد PPPoE، وليس دخول HotSpot" else "QR is a PPPoE setup card, not a HotSpot login", color = FgAmber)
            }
            if (provisionResult?.created?.let { it > 0 } == true) OutlinedButton(onClick = { toolsTab = "active" }, modifier = Modifier.fillMaxWidth()) { Text(if(arabic) "فتح الكروت النشطة" else "Open active vouchers") }
            Spacer(Modifier.height(12.dp))
        }
    }
}

@Composable
private fun VoucherStudioHero(
    arabic: Boolean,
    connected: Boolean,
    mode: VoucherMode,
    onModeChange: (VoucherMode) -> Unit
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = Color.Transparent),
        border = BorderStroke(1.6.dp, FgBlue),
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    Brush.horizontalGradient(
                        listOf(
                            Color(0xFF061824),
                            Color(0xFF0A2638),
                            Color(0xFF102638)
                        )
                    )
                )
                .padding(horizontal = 11.dp, vertical = 9.dp),
            verticalArrangement = Arrangement.spacedBy(7.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(9.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .background(
                            Brush.linearGradient(listOf(FgBlue, FgMint)),
                            RoundedCornerShape(11.dp)
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        Icons.Outlined.CreditCard,
                        contentDescription = null,
                        tint = FgBlack,
                        modifier = Modifier.size(24.dp)
                    )
                }

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        if (arabic) "استوديو الكروت" else "Voucher Studio",
                        color = FgBlue,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Black
                    )
                    Text(
                        if (arabic) "إنشاء • تفعيل • طباعة • QR"
                        else "Create • activate • print • QR",
                        color = FgSilver,
                        style = MaterialTheme.typography.labelSmall
                    )
                }

                Text(
                    if (connected) {
                        if (arabic) "متصل" else "Online"
                    } else {
                        if (arabic) "بدون راوتر" else "Offline"
                    },
                    color = if (connected) FgMint else FgAmber,
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold
                )
            }

            Text(
                if (arabic) "نوع الكارت" else "Voucher type",
                color = FgSilver,
                style = MaterialTheme.typography.labelSmall
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(7.dp)
            ) {
                VoucherModeCard(
                    mode = VoucherMode.HOTSPOT,
                    selectedMode = mode,
                    arabic = arabic,
                    onModeChange = onModeChange,
                    modifier = Modifier.weight(1f)
                )
                VoucherModeCard(
                    mode = VoucherMode.USER_MANAGER,
                    selectedMode = mode,
                    arabic = arabic,
                    onModeChange = onModeChange,
                    modifier = Modifier.weight(1f)
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(7.dp)
            ) {
                VoucherModeCard(
                    mode = VoucherMode.PPPOE,
                    selectedMode = mode,
                    arabic = arabic,
                    onModeChange = onModeChange,
                    modifier = Modifier.weight(1f)
                )
                VoucherModeCard(
                    mode = VoucherMode.OFFLINE,
                    selectedMode = mode,
                    arabic = arabic,
                    onModeChange = onModeChange,
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}

@Composable
private fun VoucherModeCard(
    mode: VoucherMode,
    selectedMode: VoucherMode,
    arabic: Boolean,
    onModeChange: (VoucherMode) -> Unit,
    modifier: Modifier = Modifier
) {
    val selected = mode == selectedMode

    Card(
        modifier = modifier,
        onClick = { onModeChange(mode) },
        colors = CardDefaults.cardColors(
            containerColor = if (selected) {
                FgBlue.copy(alpha = 0.18f)
            } else {
                FgPanel.copy(alpha = 0.92f)
            }
        ),
        border = BorderStroke(
            if (selected) 1.8.dp else 1.2.dp,
            FgBlue
        ),
        shape = RoundedCornerShape(12.dp)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp)
                .padding(horizontal = 8.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                modeLabel(mode, arabic),
                color = if (selected) FgBlue else FgWhite,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = if (selected) FontWeight.Black else FontWeight.SemiBold
            )
        }
    }
}

@Composable
private fun NeonCard(
    accent: Color,
    content: @Composable ColumnScope.() -> Unit
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = FgPanel),
        border = BorderStroke(1.5.dp, FgBlue),
        shape = RoundedCornerShape(14.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(11.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            content = content
        )
    }
}

@Composable
private fun StatusCard(
    result: VoucherProvisionSummary,
    arabic: Boolean,
    onDismiss: () -> Unit
) {
    val ok = result.failed == 0

    Card(
        colors = CardDefaults.cardColors(
            containerColor = if (ok) {
                FgMint.copy(alpha = 0.10f)
            } else {
                MaterialTheme.colorScheme.errorContainer
            }
        ),
        border = BorderStroke(
            1.4.dp,
            if (ok) FgMint else MaterialTheme.colorScheme.error
        ),
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    Icons.Outlined.CheckCircle,
                    contentDescription = null,
                    tint = if (ok) FgMint else MaterialTheme.colorScheme.error
                )
                Text(
                    if (arabic) "نتيجة التفعيل" else "Activation result",
                    color = FgWhite,
                    fontWeight = FontWeight.Black
                )
            }

            Text(
                if (arabic) {
                    "تم إنشاء وتفعيل ${result.created} من ${result.total} على MikroTik • مكرر ${result.duplicates} • فشل ${result.failed}"
                } else {
                    "Created and activated ${result.created} of ${result.total} on MikroTik • duplicates ${result.duplicates} • failed ${result.failed}"
                },
                color = FgSilver
            )

            result.items.filter { it.status.name == "FAILED" }.forEach {
                Text(
                    it.username + ": " + it.message,
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall
                )
            }

            OutlinedButton(onClick = onDismiss) {
                Text(if (arabic) "إغلاق" else "Dismiss")
            }
        }
    }
}

@Composable
private fun VoucherPreview(
    voucher: com.fgmachines.mikrotikmanager.voucher.VoucherDraft,
    count: Int,
    arabic: Boolean
) {
    val qrBitmap = remember(voucher) {
        VoucherQrCodeFactory.create(
            VoucherQrPayloadBuilder.build(voucher),
            size = 240
        )
    }

    Card(
        colors = CardDefaults.cardColors(containerColor = Color.Transparent),
        border = BorderStroke(1.6.dp, FgBlue),
        shape = RoundedCornerShape(18.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    Brush.horizontalGradient(
                        listOf(
                            Color(0xFF071C2B),
                            Color(0xFF0B2E3D),
                            Color(0xFF101D2F)
                        )
                    )
                )
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.Top,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(50.dp)
                        .background(
                            Brush.linearGradient(
                                listOf(FgBlue, FgMint)
                            ),
                            RoundedCornerShape(14.dp)
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        Icons.Outlined.Wifi,
                        contentDescription = null,
                        tint = FgBlack,
                        modifier = Modifier.size(30.dp)
                    )
                }

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        voucher.branding.networkName.ifBlank { "WiFi" },
                        color = FgWhite,
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Black
                    )
                    Text(
                        if (arabic) "كارت إنترنت • الدفعة $count"
                        else "Internet voucher • batch $count",
                        color = FgCyan,
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.SemiBold
                    )
                }

                Box(
                    modifier = Modifier
                        .background(
                            FgAmber.copy(alpha = 0.14f),
                            RoundedCornerShape(12.dp)
                        )
                        .padding(horizontal = 10.dp, vertical = 7.dp)
                ) {
                    Text(
                        voucher.branding.priceEgp?.let {
                            if (arabic) {
                                DecimalFormat("0.##").format(it) + " جنيه"
                            } else {
                                DecimalFormat("0.##").format(it) + " EGP"
                            }
                        } ?: if (arabic) "بدون سعر" else "No price",
                        color = FgAmber,
                        fontWeight = FontWeight.Black
                    )
                }
            }

            HorizontalDivider(color = FgCyan.copy(alpha = 0.28f))

            Row(horizontalArrangement = Arrangement.spacedBy(9.dp)) {
                PreviewValue(
                    modifier = Modifier.weight(1f),
                    label = if (arabic) "الكود" else "Username",
                    value = voucher.username,
                    accent = FgMint,
                    emphasized = true
                )
                PreviewValue(
                    modifier = Modifier.weight(1f),
                    label = if (arabic) "كلمة المرور" else "Password",
                    value = voucher.password,
                    accent = FgBlue,
                    emphasized = true
                )
            }

            Row(horizontalArrangement = Arrangement.spacedBy(9.dp)) {
                PreviewValue(
                    modifier = Modifier.weight(1f),
                    label = if (arabic) "المدة" else "Duration",
                    value = voucher.displayDuration(arabic),
                    accent = FgPurple
                )
                PreviewValue(
                    modifier = Modifier.weight(1f),
                    label = if (arabic) "الباقة" else "Profile",
                    value = voucher.profile.ifBlank { "—" },
                    accent = FgCyan
                )
            }

            voucher.absoluteExpiryEpochMs?.let { expiry ->
                PreviewValue(
                    modifier = Modifier.fillMaxWidth(),
                    label = if (arabic) "ينتهي في" else "Expires",
                    value = formatExpiry(expiry, arabic),
                    accent = FgAmber
                )
            }

            Box(
                modifier = Modifier
                    .align(Alignment.CenterHorizontally)
                    .background(Color.White, RoundedCornerShape(14.dp))
                    .padding(8.dp)
            ) {
                Image(
                    bitmap = qrBitmap.asImageBitmap(),
                    contentDescription = "QR",
                    modifier = Modifier.size(118.dp)
                )
            }

            if (voucher.branding.supportPhone.isNotBlank()) {
                Text(
                    (if (arabic) "الدعم: " else "Support: ") +
                        voucher.branding.supportPhone,
                    modifier = Modifier.align(Alignment.CenterHorizontally),
                    color = FgSilver,
                    style = MaterialTheme.typography.bodySmall
                )
            }
        }
    }
}

@Composable
private fun PreviewValue(
    modifier: Modifier,
    label: String,
    value: String,
    accent: Color = FgBlue,
    emphasized: Boolean = false
) {
    Column(
        modifier = modifier
            .background(accent.copy(alpha = 0.09f), RoundedCornerShape(13.dp))
            .padding(horizontal = 11.dp, vertical = 9.dp),
        verticalArrangement = Arrangement.spacedBy(3.dp)
    ) {
        Text(
            label,
            color = accent,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.SemiBold
        )
        Text(
            value,
            color = FgWhite,
            fontWeight = FontWeight.Black,
            style = if (emphasized) {
                MaterialTheme.typography.titleLarge
            } else {
                MaterialTheme.typography.bodyLarge
            }
        )
    }
}

private fun modeLabel(mode: VoucherMode, arabic: Boolean): String =
    when (mode) {
        VoucherMode.HOTSPOT -> "HotSpot"
        VoucherMode.USER_MANAGER -> "User Manager"
        VoucherMode.PPPOE -> "PPPoE"
        VoucherMode.OFFLINE -> if (arabic) "بدون راوتر" else "Offline"
    }

private fun timeUnitLabel(unit: VoucherTimeUnit, arabic: Boolean): String =
    when (unit) {
        VoucherTimeUnit.MINUTES -> if (arabic) "دقيقة" else "Min"
        VoucherTimeUnit.HOURS -> if (arabic) "ساعة" else "Hour"
        VoucherTimeUnit.DAYS -> if (arabic) "يوم" else "Day"
    }

private fun formatExpiry(epochMs: Long, arabic: Boolean): String {
    val locale = if (arabic) Locale("ar") else Locale.ENGLISH
    return SimpleDateFormat("dd/MM/yyyy  hh:mm a", locale).format(Date(epochMs))
}
