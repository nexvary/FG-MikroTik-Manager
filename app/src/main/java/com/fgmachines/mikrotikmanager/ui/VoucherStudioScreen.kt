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

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .background(FgBlack),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(14.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        item {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    if (arabic) "الكروت" else "Vouchers",
                    color = FgMint,
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Black
                )
                Text(
                    if (arabic) {
                        "أنشئ الكروت وفعّلها على MikroTik مباشرة"
                    } else {
                        "Create vouchers and provision them directly to MikroTik"
                    },
                    color = FgSilver
                )
            }
        }

        item {
            NeonCard(accent = FgBlue) {
                Text(
                    if (arabic) "نوع الكارت" else "Voucher type",
                    color = FgWhite,
                    fontWeight = FontWeight.Bold
                )

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
                                onClearProvisionResult()
                                onModeSelected(item)
                                profiles[item]?.firstOrNull()?.let {
                                    profile = it.name
                                }
                            },
                            label = { Text(modeLabel(item, arabic)) }
                        )
                    }
                }
            }
        }

        item {
            NeonCard(accent = FgMint) {
                Text(
                    if (arabic) "إعدادات الكروت" else "Voucher settings",
                    color = FgWhite,
                    fontWeight = FontWeight.Bold
                )

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = quantity,
                        onValueChange = { quantity = it.filter(Char::isDigit).take(4) },
                        modifier = Modifier.weight(1f),
                        label = { Text(if (arabic) "عدد الكروت" else "Quantity") },
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

                Text(
                    if (arabic) "مدة الاستخدام" else "Usage duration",
                    color = FgSilver,
                    style = MaterialTheme.typography.labelLarge
                )

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = durationValue,
                        onValueChange = { durationValue = it.filter(Char::isDigit).take(5) },
                        modifier = Modifier.weight(1f),
                        label = { Text(if (arabic) "المدة" else "Duration") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true
                    )

                    Row(
                        modifier = Modifier
                            .weight(1.7f)
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        VoucherTimeUnit.entries.forEach { unit ->
                            FilterChip(
                                selected = durationUnit == unit,
                                onClick = { durationUnit = unit },
                                label = { Text(timeUnitLabel(unit, arabic)) }
                            )
                        }
                    }
                }

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = priceEgp,
                        onValueChange = {
                            priceEgp = it.filter { ch -> ch.isDigit() || ch == '.' }.take(9)
                        },
                        modifier = Modifier.weight(1f),
                        label = { Text(if (arabic) "السعر بالجنيه" else "Price EGP") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        singleLine = true
                    )

                    OutlinedTextField(
                        value = dataMb,
                        onValueChange = { dataMb = it.filter(Char::isDigit).take(8) },
                        modifier = Modifier.weight(1f),
                        label = { Text(if (arabic) "البيانات MB" else "Data MB") },
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
                            if (arabic) "وقت انتهاء فعلي" else "Absolute expiry",
                            color = FgWhite,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            if (expiryEnabled) formatExpiry(expiryEpochMs, arabic)
                            else if (arabic) "بدون موعد انتهاء" else "No absolute expiry",
                            color = if (expiryEnabled) FgAmber else FgSilverMuted,
                            style = MaterialTheme.typography.bodySmall
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
                        modifier = Modifier.fillMaxWidth(),
                        border = BorderStroke(1.2.dp, FgAmber)
                    ) {
                        Icon(
                            Icons.Outlined.Schedule,
                            contentDescription = null,
                            tint = FgAmber
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            if (arabic) "اختيار تاريخ ووقت الانتهاء" else "Choose expiry date & time",
                            color = FgAmber
                        )
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    FilterChip(
                        selected = samePassword,
                        onClick = { samePassword = true },
                        label = {
                            Text(if (arabic) "الكود = الباسورد" else "Code = password")
                        }
                    )
                    FilterChip(
                        selected = !samePassword,
                        onClick = { samePassword = false },
                        label = {
                            Text(if (arabic) "باسورد مختلف" else "Separate password")
                        }
                    )
                }

                if (!samePassword) {
                    OutlinedTextField(
                        value = passwordLength,
                        onValueChange = { passwordLength = it.filter(Char::isDigit).take(2) },
                        modifier = Modifier.fillMaxWidth(),
                        label = {
                            Text(if (arabic) "طول كلمة المرور" else "Password length")
                        },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true
                    )
                }
            }
        }

        if (mode != VoucherMode.OFFLINE) {
            item {
                NeonCard(accent = FgCyan) {
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
                        OutlinedTextField(
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
            NeonCard(accent = FgPurple) {
                Text(
                    if (arabic) "بيانات تظهر على الكارت" else "Printed card details",
                    color = FgWhite,
                    fontWeight = FontWeight.Bold
                )

                OutlinedTextField(
                    value = networkName,
                    onValueChange = { networkName = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text(if (arabic) "اسم الشبكة" else "Network name") },
                    singleLine = true
                )

                OutlinedTextField(
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
                                priceEgp = price
                            )
                        )

                        val generated = generator.generate(request)
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
                enabled = !provisioning,
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
                NeonCard(accent = FgSilverMuted) {
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

        item { Spacer(Modifier.height(12.dp)) }
    }
}

@Composable
private fun NeonCard(
    accent: Color,
    content: @Composable ColumnScope.() -> Unit
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = FgPanel),
        border = BorderStroke(1.25.dp, accent.copy(alpha = 0.85f)),
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
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
                    "تم تفعيل ${result.created} • مكرر ${result.duplicates} • فشل ${result.failed}"
                } else {
                    "Activated ${result.created} • duplicates ${result.duplicates} • failed ${result.failed}"
                },
                color = FgSilver
            )

            result.items.firstOrNull { it.status.name == "FAILED" }?.let {
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
        colors = CardDefaults.cardColors(containerColor = FgDeepNavy),
        border = BorderStroke(1.4.dp, FgCyan),
        shape = RoundedCornerShape(18.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(15.dp),
            verticalArrangement = Arrangement.spacedBy(9.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column {
                    Text(
                        voucher.branding.networkName.ifBlank { "WiFi" },
                        color = FgWhite,
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Black
                    )
                    Text(
                        if (arabic) "معاينة الكارت • الدفعة ${count}"
                        else "Voucher preview • batch ${count}",
                        color = FgSilverMuted,
                        style = MaterialTheme.typography.bodySmall
                    )
                }

                Icon(
                    Icons.Outlined.Wifi,
                    contentDescription = null,
                    tint = FgMint
                )
            }

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                PreviewValue(
                    modifier = Modifier.weight(1f),
                    label = if (arabic) "الكود" else "Username",
                    value = voucher.username
                )
                PreviewValue(
                    modifier = Modifier.weight(1f),
                    label = if (arabic) "الباسورد" else "Password",
                    value = voucher.password
                )
            }

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                PreviewValue(
                    modifier = Modifier.weight(1f),
                    label = if (arabic) "المدة" else "Duration",
                    value = voucher.displayDuration(arabic)
                )
                PreviewValue(
                    modifier = Modifier.weight(1f),
                    label = if (arabic) "السعر" else "Price",
                    value = voucher.branding.priceEgp?.let {
                        if (arabic) {
                            DecimalFormat("0.##").format(it) + " جنيه"
                        } else {
                            DecimalFormat("0.##").format(it) + " EGP"
                        }
                    } ?: "—"
                )
            }

            voucher.absoluteExpiryEpochMs?.let { expiry ->
                PreviewValue(
                    modifier = Modifier.fillMaxWidth(),
                    label = if (arabic) "ينتهي في" else "Expires",
                    value = formatExpiry(expiry, arabic)
                )
            }

            Image(
                bitmap = qrBitmap.asImageBitmap(),
                contentDescription = "QR",
                modifier = Modifier
                    .size(126.dp)
                    .align(Alignment.CenterHorizontally)
            )
        }
    }
}

@Composable
private fun PreviewValue(
    modifier: Modifier,
    label: String,
    value: String
) {
    Column(
        modifier = modifier
            .background(FgPanel, RoundedCornerShape(12.dp))
            .padding(10.dp),
        verticalArrangement = Arrangement.spacedBy(3.dp)
    ) {
        Text(
            label,
            color = FgSilverMuted,
            style = MaterialTheme.typography.labelSmall
        )
        Text(
            value,
            color = FgWhite,
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
