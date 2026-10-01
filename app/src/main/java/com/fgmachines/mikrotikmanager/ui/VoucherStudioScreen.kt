package com.fgmachines.mikrotikmanager.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.fgmachines.mikrotikmanager.voucher.VoucherAccessMode
import com.fgmachines.mikrotikmanager.voucher.VoucherCharacterSet
import com.fgmachines.mikrotikmanager.voucher.VoucherGenerator
import com.fgmachines.mikrotikmanager.voucher.VoucherPasswordMode
import com.fgmachines.mikrotikmanager.voucher.VoucherExportBundle
import com.fgmachines.mikrotikmanager.voucher.WcgCardSettings
import com.fgmachines.mikrotikmanager.voucher.WcgExporter

@Composable
fun VoucherStudioScreen(
    modifier: Modifier = Modifier
) {
    var mode by remember { mutableStateOf(VoucherAccessMode.HOTSPOT) }
    var quantity by remember { mutableStateOf("20") }
    var usernameLength by remember { mutableStateOf("6") }
    var passwordLength by remember { mutableStateOf("6") }
    var samePassword by remember { mutableStateOf(true) }
    var alphanumeric by remember { mutableStateOf(false) }
    var networkName by remember { mutableStateOf("FG WiFi") }
    var supportPhone by remember { mutableStateOf("") }
    var price by remember { mutableStateOf("") }
    var profile by remember { mutableStateOf("default") }
    var server by remember { mutableStateOf("all") }
    var uptime by remember { mutableStateOf("") }
    var dataLimitGb by remember { mutableStateOf("") }
    var prefix by remember { mutableStateOf("") }
    var comment by remember { mutableStateOf("") }
    var loginUrl by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    var export by remember { mutableStateOf<VoucherExportBundle?>(null) }

    val generator = remember { VoucherGenerator() }

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Text(
                text = "مولد كروت MikroTik",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = "HotSpot / User Manager / PPPoE — يعمل بدون اتصال ويمكن تصدير RSC وCSV وHTML.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        item {
            Card {
                Column(
                    modifier = Modifier.padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text("نظام الكروت", fontWeight = FontWeight.SemiBold)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        VoucherAccessMode.entries.forEach { item ->
                            OutlinedButton(
                                onClick = {
                                    mode = item
                                    export = null
                                },
                                modifier = Modifier.weight(1f)
                            ) {
                                Text(
                                    when (item) {
                                        VoucherAccessMode.HOTSPOT -> "HotSpot"
                                        VoucherAccessMode.USER_MANAGER -> "User Manager"
                                        VoucherAccessMode.PPPOE -> "PPPoE"
                                    }
                                )
                            }
                        }
                    }
                    Text(
                        text = "المحدد: " + when (mode) {
                            VoucherAccessMode.HOTSPOT -> "HotSpot"
                            VoucherAccessMode.USER_MANAGER -> "User Manager"
                            VoucherAccessMode.PPPOE -> "PPPoE"
                        },
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }
        }

        item {
            Card {
                Column(
                    modifier = Modifier.padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text("بيانات الكارت", fontWeight = FontWeight.SemiBold)

                    OutlinedTextField(
                        value = networkName,
                        onValueChange = { networkName = it },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("اسم الشبكة") },
                        singleLine = true
                    )

                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(
                            value = supportPhone,
                            onValueChange = { supportPhone = it },
                            modifier = Modifier.weight(1f),
                            label = { Text("رقم الدعم") },
                            singleLine = true
                        )
                        OutlinedTextField(
                            value = price,
                            onValueChange = { price = it },
                            modifier = Modifier.weight(1f),
                            label = { Text("السعر") },
                            singleLine = true
                        )
                    }

                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(
                            value = profile,
                            onValueChange = { profile = it },
                            modifier = Modifier.weight(1f),
                            label = { Text("Profile") },
                            singleLine = true
                        )
                        OutlinedTextField(
                            value = server,
                            onValueChange = { server = it },
                            modifier = Modifier.weight(1f),
                            label = { Text("Server") },
                            enabled = mode == VoucherAccessMode.HOTSPOT,
                            singleLine = true
                        )
                    }
                }
            }
        }

        item {
            Card {
                Column(
                    modifier = Modifier.padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text("توليد الأكواد", fontWeight = FontWeight.SemiBold)

                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(
                            value = quantity,
                            onValueChange = { quantity = it.filter(Char::isDigit) },
                            modifier = Modifier.weight(1f),
                            label = { Text("عدد الكروت") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            singleLine = true
                        )
                        OutlinedTextField(
                            value = usernameLength,
                            onValueChange = { usernameLength = it.filter(Char::isDigit) },
                            modifier = Modifier.weight(1f),
                            label = { Text("طول الكود") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            singleLine = true
                        )
                    }

                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(
                            onClick = { samePassword = !samePassword },
                            modifier = Modifier.weight(1f)
                        ) {
                            Text(if (samePassword) "الباسورد = الكود" else "باسورد مستقل")
                        }
                        OutlinedButton(
                            onClick = { alphanumeric = !alphanumeric },
                            modifier = Modifier.weight(1f)
                        ) {
                            Text(if (alphanumeric) "حروف + أرقام" else "أرقام فقط")
                        }
                    }

                    if (!samePassword) {
                        OutlinedTextField(
                            value = passwordLength,
                            onValueChange = { passwordLength = it.filter(Char::isDigit) },
                            modifier = Modifier.fillMaxWidth(),
                            label = { Text("طول كلمة المرور") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            singleLine = true
                        )
                    }

                    OutlinedTextField(
                        value = prefix,
                        onValueChange = { prefix = it },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("Prefix اختياري") },
                        singleLine = true
                    )
                }
            }
        }

        item {
            Card {
                Column(
                    modifier = Modifier.padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text("الصلاحية والاستهلاك", fontWeight = FontWeight.SemiBold)

                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(
                            value = uptime,
                            onValueChange = { uptime = it },
                            modifier = Modifier.weight(1f),
                            label = { Text("المدة مثل 1h / 1d") },
                            singleLine = true
                        )
                        OutlinedTextField(
                            value = dataLimitGb,
                            onValueChange = { dataLimitGb = it.filter { ch -> ch.isDigit() || ch == '.' } },
                            modifier = Modifier.weight(1f),
                            label = { Text("الحجم GB") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                            singleLine = true
                        )
                    }

                    OutlinedTextField(
                        value = comment,
                        onValueChange = { comment = it },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("اسم الدفعة / تعليق") },
                        singleLine = true
                    )

                    OutlinedTextField(
                        value = loginUrl,
                        onValueChange = { loginUrl = it },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("رابط صفحة HotSpot للـ QR - اختياري") },
                        enabled = mode == VoucherAccessMode.HOTSPOT,
                        singleLine = true
                    )
                }
            }
        }

        item {
            Button(
                onClick = {
                    error = null
                    export = null
                    try {
                        val bytes = dataLimitGb
                            .toDoubleOrNull()
                            ?.takeIf { it > 0 }
                            ?.let { (it * 1024.0 * 1024.0 * 1024.0).toLong() }

                        val settings = WcgCardSettings(
                            accessMode = mode,
                            networkName = networkName,
                            supportPhone = supportPhone,
                            priceText = price,
                            profile = profile.trim(),
                            server = server.trim().ifBlank { "all" },
                            quantity = quantity.toIntOrNull() ?: 0,
                            usernameLength = usernameLength.toIntOrNull() ?: 0,
                            passwordLength = passwordLength.toIntOrNull() ?: 0,
                            passwordMode = if (samePassword) {
                                VoucherPasswordMode.SAME_AS_USERNAME
                            } else {
                                VoucherPasswordMode.RANDOM
                            },
                            characterSet = if (alphanumeric) {
                                VoucherCharacterSet.ALPHANUMERIC
                            } else {
                                VoucherCharacterSet.NUMERIC
                            },
                            prefix = prefix,
                            limitUptime = uptime.trim().ifBlank { null },
                            dataLimitBytes = bytes,
                            comment = comment,
                            hotspotLoginUrl = loginUrl
                        )

                        val batch = generator.generate(settings.toBatchRequest())
                        export = WcgExporter.export(settings, batch)
                    } catch (t: Throwable) {
                        error = t.message ?: "تعذر إنشاء الكروت"
                    }
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("إنشاء الكروت")
            }
        }

        error?.let { message ->
            item {
                Text(
                    text = message,
                    color = MaterialTheme.colorScheme.error
                )
            }
        }

        export?.let { bundle ->
            item {
                Card {
                    Column(
                        modifier = Modifier.padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            "تم إنشاء الدفعة بنجاح",
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Text("RSC وCSV وHTML جاهزة داخل محرك التصدير.")
                        Text(
                            text = bundle.rsc.lineSequence().take(8).joinToString("\n"),
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }
            }
        }
    }
}
