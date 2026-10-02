package com.fgmachines.mikrotikmanager.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.fgmachines.mikrotikmanager.voucher.*

@Composable
fun VoucherShareDialog(vouchers: List<VoucherDraft>, arabic: Boolean, activated: Boolean, onDismiss: () -> Unit) {
    val context = LocalContext.current
    var selected by remember { mutableStateOf<VoucherDraft?>(vouchers.singleOrNull()) }
    var error by remember { mutableStateOf<String?>(null) }
    val voucher = selected
    val text = voucher?.let { VoucherShareText.build(it,arabic,activated) }.orEmpty()
    fun label(ar:String,en:String) = if(arabic) ar else en
    AlertDialog(onDismissRequest = onDismiss,
        title = { Text(label("مشاركة الكارت", "Share voucher")) },
        text = { Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            if(voucher == null) {
                Text(label("اختار الكارت الذي تريد إرساله", "Choose the voucher to send"))
                LazyColumn(Modifier.heightIn(max = 320.dp)) {
                    items(vouchers, key = { it.username }) { item ->
                        TextButton(onClick = { selected = item }) { Text(item.username + " • " + item.displayDuration(arabic)) }
                    }
                }
            } else {
                Text(label("سيتم إرسال الكود وكلمة المرور للكارت المختار.", "The selected voucher's code and password will be shared."))
                LazyColumn(Modifier.heightIn(max = 260.dp)) { item { Text(text) } }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = { runCatching { VoucherShareManager.shareVoucherText(context,text,label("مشاركة الكارت", "Share voucher")) }.onFailure { error = it.message } }, modifier = Modifier.weight(1f)) { Text(label("نص", "Text")) }
                    OutlinedButton(onClick = { runCatching { VoucherShareManager.shareFile(context,VoucherImageExporter.export(context,voucher),"image/png") }.onFailure { error = it.message } }, modifier = Modifier.weight(1f)) { Text(label("صورة + QR", "Image + QR")) }
                }
            }
            error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        } },
        confirmButton = { TextButton(onClick = onDismiss) { Text(label("إغلاق", "Close")) } },
        dismissButton = { if(voucher != null && vouchers.size > 1) TextButton(onClick = { selected = null }) { Text(label("كارت آخر", "Another voucher")) } })
}
