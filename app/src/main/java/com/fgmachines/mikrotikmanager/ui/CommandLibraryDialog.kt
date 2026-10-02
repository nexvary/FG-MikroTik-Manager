package com.fgmachines.mikrotikmanager.ui

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.unit.dp
import com.fgmachines.mikrotikmanager.command.*

@Composable
fun CommandLibraryDialog(arabic: Boolean, onSelect: (String) -> Unit, onDismiss: () -> Unit) {
    var search by remember { mutableStateOf("") }
    var category by remember { mutableStateOf("") }
    var selected by remember { mutableStateOf<CommandTemplate?>(null) }
    var fields by remember { mutableStateOf<Map<String,String>>(emptyMap()) }
    fun label(ar: String, en: String) = if (arabic) ar else en
    val filtered = remember(search, category) { CommandTemplates.items.filter {
        (category.isBlank() || it.categoryEn == category) &&
            listOf(it.titleAr, it.titleEn, it.command, it.descriptionAr, it.descriptionEn).any { text -> text.contains(search, true) }
    } }
    val fieldLabels = mapOf("name" to ("الاسم" to "Name"), "zone" to ("المنطقة مثل Africa/Cairo" to "Zone e.g. Africa/Cairo"),
        "server" to ("خادم الوقت" to "Time server"), "user" to ("اسم الكارت أو المعرّف" to "Voucher name or ID"),
        "profile" to ("اسم الباقة" to "Profile name"), "duration" to ("المدة مثل 6h" to "Duration e.g. 6h"),
        "bytes" to ("حد البيانات بالبايت" to "Data limit in bytes"), "id" to ("معرّف العنصر مثل *A" to "Item ID e.g. *A"))
    AlertDialog(onDismissRequest = onDismiss,
        title = { Text(label("مكتبة الأوامر — ", "Command library — ") + CommandTemplates.items.size) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (selected == null) {
                    OutlinedTextField(search, { search = it }, label = { Text(label("ابحث بالاسم أو الأمر", "Search title or command")) }, singleLine = true)
                    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                        FilterChip(category.isBlank(), { category = "" }, label = { Text(label("الكل", "All")) })
                        CommandTemplates.items.distinctBy { it.categoryEn }.forEach { item ->
                            FilterChip(category == item.categoryEn, { category = item.categoryEn }, label = { Text(label(item.categoryAr, item.categoryEn)) })
                        }
                    }
                    Text(label("اختيار الأمر يفتح معاينته؛ لا ينفذه. بعض الأوامر تحتاج حزمة أو جهازًا داعمًا.", "Selection previews a command; it does not execute it. Some commands require a supporting package or device."), style = MaterialTheme.typography.bodySmall)
                    LazyColumn(Modifier.heightIn(max = 340.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(filtered, key = { it.command }) { item ->
                            OutlinedCard(onClick = { selected = item; fields = emptyMap() }) {
                                Column(Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                    Text(label(item.titleAr,item.titleEn), color = FgBlue)
                                    Text(item.command, fontFamily = FontFamily.Monospace, style = MaterialTheme.typography.bodySmall.copy(textDirection = TextDirection.Ltr))
                                    Text(label(item.descriptionAr,item.descriptionEn), style = MaterialTheme.typography.bodySmall)
                                    Text(if(item.risk == CommandRisk.SAFE) label("عرض فقط", "Read only") else label("يغيّر الإعدادات — راجع قبل التنفيذ", "Changes settings — review before running"), color = if(item.risk == CommandRisk.SAFE) FgMint else FgAmber, style = MaterialTheme.typography.labelSmall)
                                }
                            }
                        }
                        if(filtered.isEmpty()) item { Text(label("لا توجد نتائج", "No results")) }
                    }
                } else {
                    val template = selected!!
                    Text(label(template.titleAr,template.titleEn), color = FgBlue)
                    Text(label(template.descriptionAr,template.descriptionEn))
                    LazyColumn(Modifier.heightIn(max = 300.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(Regex("\\{\\{([a-z]+)\\}\\}").findAll(template.command).map { it.groupValues[1] }.distinct().toList()) { key ->
                            OutlinedTextField(fields[key].orEmpty(), { fields = fields + (key to it) }, label = { val title = fieldLabels[key]; Text(title?.let { label(it.first,it.second) } ?: key) }, singleLine = true)
                        }
                        item { Text(template.command, fontFamily = FontFamily.Monospace, style = MaterialTheme.typography.bodySmall.copy(textDirection = TextDirection.Ltr)) }
                    }
                }
            }
        },
        confirmButton = {
            selected?.let { template ->
                val command = CommandTemplateValues.fill(template.command, fields)
                TextButton(enabled = command != null, onClick = { command?.let(onSelect) }) { Text(label("إضافة للمعاينة", "Add to preview")) }
            }
        },
        dismissButton = { TextButton(onClick = { if(selected != null) selected = null else onDismiss() }) { Text(label("رجوع", "Back")) } })
}
