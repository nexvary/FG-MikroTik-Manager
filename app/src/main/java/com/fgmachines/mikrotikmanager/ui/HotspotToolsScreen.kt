package com.fgmachines.mikrotikmanager.ui

import android.graphics.BitmapFactory
import android.webkit.WebView
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.fgmachines.mikrotikmanager.hotspot.*
import com.fgmachines.mikrotikmanager.voucher.*
import kotlinx.coroutines.launch
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.ByteArrayOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HotspotToolsScreen(
    arabic: Boolean,
    manager: HotspotManager?,
    batches: List<SavedVoucherBatch>,
    initialTab: String,
    onDismiss: () -> Unit,
    demo: Boolean = false,
    advancedManager: com.fgmachines.mikrotikmanager.advanced.AdvancedRouterManager? = null,
    onAdvancedSetup: (() -> Unit)? = null
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val prefs = remember { context.getSharedPreferences("fg_portal_design", 0) }
    var design by remember { mutableStateOf(runCatching { Json.decodeFromString<PortalDesign>(prefs.getString("design", null).orEmpty()) }.getOrDefault(PortalDesign())) }
    var tab by remember { mutableStateOf(initialTab) }
    var snapshot by remember { mutableStateOf<HotspotSnapshot?>(null) }
    var serverProfiles by remember { mutableStateOf<List<Map<String, String>>>(emptyList()) }
    var busy by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf("") }
    var action by remember { mutableStateOf<(() -> Unit)?>(null) }
    var confirmText by remember { mutableStateOf("") }
    var selectedProfile by remember { mutableStateOf("") }
    var selectedUser by remember { mutableStateOf<Map<String, String>?>(null) }
    var qrVoucher by remember { mutableStateOf<VoucherDraft?>(null) }
    var addMinutes by remember { mutableStateOf("30") }
    var newProfile by remember { mutableStateOf("") }
    var preview by remember { mutableStateOf<String?>(when(initialTab) { "portal-login" -> "login.html"; "portal-status" -> "status.html"; else -> null }) }
    var interfaceName by remember { mutableStateOf("") }
    var gateway by remember { mutableStateOf("192.168.88.1/24") }
    var network by remember { mutableStateOf("192.168.88.0/24") }
    var pool by remember { mutableStateOf("192.168.88.100-192.168.88.200") }
    var dns by remember { mutableStateOf("wifi.local") }
    var dhcp by remember { mutableStateOf(true) }
    fun label(ar: String, en: String) = if (arabic) ar else en
    suspend fun reload() {
        if (manager != null) {
            snapshot = manager.load()
            serverProfiles = manager.serverProfiles()
            if (selectedProfile.isBlank()) selectedProfile = manager.activeServerProfiles().singleOrNull()?.get(".id").orEmpty()
        }
    }
    fun run(work: suspend () -> String) {
        if (busy) return
        scope.launch {
            busy = true
            try { message = work(); reload() } catch (e: Exception) { message = e.message ?: "Operation failed" } finally { busy = false }
        }
    }
    fun confirm(text: String, work: () -> Unit) { confirmText = text; action = work }
    fun assets() = context.assets.list("hotspot").orEmpty().associateWith { context.assets.open("hotspot/$it").bufferedReader().use { reader -> reader.readText() } }
    val logoPicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        uri?.let {
            runCatching {
                val raw = context.contentResolver.openInputStream(uri)?.use { stream -> stream.readBytes() } ?: error("Cannot read image")
                require(raw.size < 10_000_000) { "Image is too large" }
                val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                BitmapFactory.decodeByteArray(raw, 0, raw.size, options)
                val ratio = (maxOf(options.outWidth, options.outHeight) / 160).coerceAtLeast(1)
                val bitmap = BitmapFactory.decodeByteArray(raw, 0, raw.size, BitmapFactory.Options().apply { inSampleSize = ratio }) ?: error("Invalid image")
                val output = ByteArrayOutputStream(); bitmap.compress(android.graphics.Bitmap.CompressFormat.JPEG, 65, output); bitmap.recycle()
                require(output.size() < 24_000) { "Use a smaller logo" }
                design = design.copy(logoDataUri = "data:image/jpeg;base64," + android.util.Base64.encodeToString(output.toByteArray(), android.util.Base64.NO_WRAP))
            }.onFailure { message = it.message.orEmpty() }
        }
    }
    LaunchedEffect(manager) {
        if (demo) {
            snapshot = HotspotSnapshot(listOf(mapOf(".id" to "*A", "name" to "123456", "profile" to "Kids-60m", "limit-uptime" to "1h", "uptime" to "12m", "bytes-in" to "1048576", "bytes-out" to "25165824", "limit-bytes-total" to "104857600", "disabled" to "false")), listOf(mapOf("user" to "123456", "uptime" to "2m")), emptyList(), emptyList(), emptyList())
        } else if (manager != null) run { reload(); label("تم التحديث", "Refreshed") }
    }
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Scaffold(containerColor = FgBlack, topBar = {
            Row(Modifier.fillMaxWidth().statusBarsPadding().padding(horizontal = 12.dp), verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                Text("FG MTM", color = FgBlue, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                TextButton(onClick = onDismiss) { Text(label("رجوع", "Back")) }
            }
        }) { padding ->
            LazyColumn(Modifier.fillMaxSize().padding(padding), contentPadding = PaddingValues(12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                item {
                    Row(horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                        listOf("active" to label("الكروت", "Vouchers"), "design" to label("صفحة العملاء", "Portal"), "setup" to label("إعداد", "Setup")).forEach { (key, title) ->
                            FilterChip(tab == key, { if(key == "setup" && onAdvancedSetup != null) onAdvancedSetup() else tab = key }, label = { Text(title) }, modifier = Modifier.weight(1f))
                        }
                    }
                    if (demo) Text(label("معاينة واجهة — بيانات اختبار", "UI preview — test data"), color = FgAmber)
                    if (busy) LinearProgressIndicator(Modifier.fillMaxWidth())
                    if (message.isNotBlank()) Text(message, color = FgSilver)
                }
                if (tab == "active") {
                    item {
                        Text(label("الكروت النشطة", "Active vouchers"), style = MaterialTheme.typography.titleLarge, color = FgBlue)
                        OutlinedButton(onClick = { run { reload(); label("تم التحديث", "Refreshed") } }, enabled = manager != null && !busy) { Text(label("تحديث", "Refresh")) }
                    }
                    if (snapshot?.users.isNullOrEmpty()) item { Text(label("لا توجد كروت HotSpot. أنشئ كروتًا وفعّلها على الراوتر أولًا.", "No HotSpot vouchers. Create and provision a batch first."), color = FgSilver) }
                    items(snapshot?.users.orEmpty(), key = { it[".id"].orEmpty() }) { row ->
                        val username = row["name"].orEmpty(); val id = row[".id"].orEmpty()
                        val known = batches.flatMap { it.batch.vouchers }.firstOrNull { it.username == username && it.mode == VoucherMode.HOTSPOT }
                        val limit = RouterDuration.seconds(row["limit-uptime"].orEmpty())
                        val sessions = snapshot?.active.orEmpty().filter { it["user"] == username }
                        val used = (RouterDuration.seconds(row["uptime"].orEmpty()) ?: 0) + sessions.sumOf { RouterDuration.seconds(it["uptime"].orEmpty()) ?: 0 }
                        val session = snapshot?.active?.firstOrNull { it["user"] == username }
                        val upload = (row["bytes-in"]?.toLongOrNull() ?: 0) + sessions.sumOf { it["bytes-in"]?.toLongOrNull() ?: 0 }
                        val download = (row["bytes-out"]?.toLongOrNull() ?: 0) + sessions.sumOf { it["bytes-out"]?.toLongOrNull() ?: 0 }
                        val usedBytes = upload + download
                        val byteLimit = row["limit-bytes-total"]?.toLongOrNull() ?: 0
                        val expired = known?.absoluteExpiryEpochMs?.let { it < System.currentTimeMillis() } == true || (limit != null && limit > 0 && used != null && used >= limit) || (byteLimit > 0 && usedBytes >= byteLimit)
                        val disabled = row["disabled"] in listOf("true", "yes")
                        val status = when { expired -> label("منتهي", "Expired"); disabled -> label("معطل", "Disabled"); session != null -> label("متصل", "Active"); else -> label("غير متصل", "Offline") }
                        Card(border = BorderStroke(1.dp, FgBlue), colors = CardDefaults.cardColors(containerColor = FgPanel)) {
                            Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                Text(username, color = FgWhite, fontWeight = FontWeight.Bold)
                                Text(status, color = if (session != null && !expired) FgMint else FgAmber)
                                Text(label("الباقة: ", "Profile: ") + row["profile"].orEmpty(), color = FgSilver)
                                Text(label("مدة الاستخدام: ", "Usage allowance: ") + row["limit-uptime"].orEmpty() + " • " + label("المستخدم: ", "Used: ") + "${used/60} min", color = FgSilver)
                                Text(label("المتبقي: ", "Remaining: ") + if (limit != null && limit > 0 && used != null) "${(limit-used).coerceAtLeast(0)/60} min" else label("غير محدد", "Unlimited / unknown"), color = FgSilver)
                                Text("↑ $upload B  ↓ $download B", color = FgSilver)
                                Text(label("البيانات: ", "Data: ") + if (byteLimit > 0) "${byteLimit/1048576} MB • ${((byteLimit-usedBytes).coerceAtLeast(0))/1048576} MB " + label("متبقي", "left") else label("غير محددة", "Unlimited"), color = FgSilver)
                                known?.let { voucher ->
                                    Text(voucher.branding.formattedPrice() + " • " + (voucher.displayExpiry(arabic) ?: label("بدون انتهاء مطلق", "No absolute expiry")), color = FgSilver)
                                    val createdAt = batches.firstOrNull { batch -> batch.batch.vouchers.any { it.username == username } }?.createdAtEpochMs
                                    createdAt?.let { Text(label("أُنشئ: ", "Created: ") + SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.ENGLISH).format(Date(it)), color = FgSilver) }
                                }
                                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                    OutlinedButton(onClick = { run { manager!!.setEnabled(id, username, disabled); label("تم تعديل الحالة", "State updated") } }, enabled = manager != null && !busy, modifier = Modifier.weight(1f)) { Text(if (disabled) label("تفعيل", "Enable") else label("تعطيل", "Disable")) }
                                    OutlinedButton(onClick = { selectedUser = row; newProfile = row["profile"].orEmpty() }, enabled = manager != null && !busy, modifier = Modifier.weight(1f)) { Text(label("+ وقت / باقة", "+ Time / profile")) }
                                }
                                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                    TextButton(onClick = { if (known != null) qrVoucher = known.copy(
                                        profile = row["profile"].orEmpty(), limitUptime = row["limit-uptime"],
                                        durationValue = if (limit != null && limit > 0) (limit / 60).coerceIn(1, 100000).toInt() else known.durationValue,
                                        durationUnit = if (limit != null && limit > 0) VoucherTimeUnit.MINUTES else known.durationUnit,
                                        limitBytesTotal = byteLimit.takeIf { it > 0 }
                                    ) else message = label("الكارت غير موجود في سجل هذا الهاتف؛ كلمة المرور غير متاحة لإعادة الطباعة.", "This voucher is not in this phone's archive; password is unavailable for reprint.") }, modifier = Modifier.weight(1f)) { Text("QR / " + label("طباعة", "Print")) }
                                    TextButton(onClick = { confirm(label("حذف الكارت وتسجيل خروج أي جلسة نشطة؟", "Delete voucher and disconnect its active session?")) { run { manager!!.delete(id, username); label("تم الحذف", "Deleted") } } }, enabled = manager != null && !busy, modifier = Modifier.weight(1f)) { Text(label("حذف", "Delete"), color = MaterialTheme.colorScheme.error) }
                                }
                            }
                        }
                    }
                }
                if (tab == "design") {
                    item { Text(label("تصميم صفحة العملاء", "Customer portal design"), color = FgBlue, style = MaterialTheme.typography.titleLarge) }
                    item { CompactVoucherField(design.networkName, { design = design.copy(networkName = it) }, Modifier.fillMaxWidth(), { Text(label("اسم الشبكة", "Network name")) }) }
                    item { CompactVoucherField(design.supportPhone, { design = design.copy(supportPhone = it) }, Modifier.fillMaxWidth(), { Text(label("رقم الدعم", "Support phone")) }) }
                    item { CompactVoucherField(design.welcome, { design = design.copy(welcome = it) }, Modifier.fillMaxWidth(), { Text(label("رسالة الترحيب", "Welcome message")) }) }
                    item { CompactVoucherField(design.color, { design = design.copy(color = it) }, Modifier.fillMaxWidth(), { Text(label("اللون الرئيسي #159DFF", "Primary color #159DFF")) }) }
                    item { CompactVoucherField(design.terms, { design = design.copy(terms = it) }, Modifier.fillMaxWidth(), { Text(label("شروط الاستخدام", "Usage terms")) }, singleLine = false) }
                    item { CompactVoucherField(design.website, { design = design.copy(website = it) }, Modifier.fillMaxWidth(), { Text("Website / Facebook — HTTPS") }) }
                    item { OutlinedButton(onClick = { logoPicker.launch("image/*") }) { Text(label("اختيار شعار", "Choose logo")) } }
                    item { Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(onClick = { runCatching { PortalTemplates.render(assets(), design); prefs.edit().putString("design", Json.encodeToString(design)).apply(); message = label("تم حفظ التصميم", "Design saved") }.onFailure { message = it.message.orEmpty() } }) { Text(label("حفظ", "Save")) }
                        OutlinedButton(onClick = { preview = "login.html" }) { Text(label("معاينة الدخول", "Login preview")) }
                    } }
                    item { OutlinedButton(onClick = { preview = "status.html" }) { Text(label("معاينة الحالة", "Status preview")) } }
                    items(serverProfiles) { profile ->
                        FilterChip(selectedProfile == profile[".id"], { selectedProfile = profile[".id"].orEmpty() }, label = { Text(label("Profile الخادم: ", "Server profile: ") + profile["name"].orEmpty()) })
                    }
                    item {
                        Button(onClick = { confirm(label("سيتم تثبيت الصفحة في مجلد جديد وربط Profile المختار به. تبقى الملفات السابقة محفوظة للرجوع. متابعة؟", "Install into a new directory and switch the selected server profile? Old files remain available for rollback.")) { run { if(advancedManager != null) {
                                        val secret=java.util.UUID.randomUUID().toString().replace("-", "")
                                        val file=advancedManager.backup(secret)
                                        val vault=com.fgmachines.mikrotikmanager.advanced.RouterChangeVault(context,advancedManager.routerKey)
                                        vault.rememberBackup(file,secret);vault.record("نسخة قبل تثبيت صفحة العملاء","Backup before portal installation",true,file)
                                    }
                                    val directory = manager!!.installPortal(selectedProfile, PortalTemplates.render(assets(), design)); advancedManager?.let { com.fgmachines.mikrotikmanager.advanced.RouterChangeVault(context,it.routerKey).record("تثبيت صفحة العملاء","Install customer portal",true) }; prefs.edit().putString("design", Json.encodeToString(design)).apply(); label("تم التثبيت في ", "Installed in ") + directory } } }, enabled = manager != null && selectedProfile.isNotBlank() && !busy, modifier = Modifier.fillMaxWidth()) { Text(label("تثبيت صفحة HotSpot على الراوتر", "Install HotSpot portal on router")) }
                    }
                }
                if (tab == "setup") {
                    item { Text(label("إعداد HotSpot", "HotSpot setup"), color = FgBlue, style = MaterialTheme.typography.titleLarge) }
                    item { Text(label("اختر شبكة ضيوف مجهزة بعنوان IP. لا يغيّر المعالج عناوينك أو Firewall أو NAT. تأكد من DNS والاتصال الخارجي أولًا. تشغيل HotSpot قد يقطع اتصال الهاتف؛ استخدم منفذ إدارة منفصلًا، ويمكن حذف الخادم الجديد من قسم HotSpot للرجوع.", "Choose a guest network with an existing gateway IP. This wizard preserves addresses, firewall and NAT. Verify DNS and internet routing first. HotSpot may disconnect this phone; use a separate management interface. Remove the new HotSpot server to roll back."), color = FgAmber) }
                    items(snapshot?.interfaces.orEmpty()) { row -> FilterChip(interfaceName == row["name"], { interfaceName = row["name"].orEmpty() }, label = { Text(row["name"].orEmpty()) }) }
                    item { CompactVoucherField(gateway, { gateway = it }, Modifier.fillMaxWidth(), { Text(label("عنوان البوابة الموجود / CIDR", "Existing gateway / CIDR")) }) }
                    item { CompactVoucherField(network, { network = it }, Modifier.fillMaxWidth(), { Text(label("الشبكة / CIDR", "Network / CIDR")) }) }
                    item { CompactVoucherField(pool, { pool = it }, Modifier.fillMaxWidth(), { Text(label("مدى عناوين العملاء", "Client address pool")) }) }
                    item { CompactVoucherField(dns, { dns = it }, Modifier.fillMaxWidth(), { Text("DNS Name") }) }
                    item { Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) { Checkbox(dhcp, { dhcp = it }); Text(label("إنشاء DHCP إذا لم يكن موجودًا", "Create DHCP if absent")) } }
                    item { Text("CHAP • " + label("دخول آمن بالكارت", "Voucher login"), color = FgMint) }
                    item { Button(onClick = { confirm("Interface: $interfaceName\nGateway: $gateway\nNetwork: $network\nPool: $pool\nDNS: $dns\nDHCP: $dhcp\n" + label("قد ينقطع الاتصال. هل تريد التنفيذ؟", "Connectivity may be interrupted. Apply?")) { run { val name = manager!!.setup(HotspotSetup(interfaceName,gateway,network,pool,dns,dhcp)); label("تم إنشاء ", "Created ") + name + label(". ثبّت صفحة العملاء من تبويب التصميم.", ". Install the portal from the Portal tab.") } } }, enabled = manager != null && interfaceName.isNotBlank() && !busy, modifier = Modifier.fillMaxWidth()) { Text(label("مراجعة وتنفيذ الإعداد", "Review and apply setup")) } }
                }
                item { Spacer(Modifier.height(24.dp)) }
            }
        }
    }
    action?.let { pending -> AlertDialog(onDismissRequest = { action = null }, title = { Text(label("تأكيد", "Confirm")) }, text = { Text(confirmText) }, confirmButton = { Button(onClick = { action = null; pending() }) { Text(label("متابعة", "Continue")) } }, dismissButton = { TextButton(onClick = { action = null }) { Text(label("إلغاء", "Cancel")) } }) }
    selectedUser?.let { row ->
        AlertDialog(onDismissRequest = { selectedUser = null }, title = { Text(row["name"].orEmpty()) }, text = {
            Column(Modifier.heightIn(max = 420.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(label("إضافة وقت استخدام لا تغيّر موعد الانتهاء المطلق.", "Adding usage time preserves absolute expiry."), color = FgAmber)
                Row { listOf(30,60,1440).forEach { minutes -> TextButton(onClick = { addMinutes = minutes.toString() }) { Text("+$minutes m") } } }
                CompactVoucherField(addMinutes,{ addMinutes = it.filter(Char::isDigit) },Modifier.fillMaxWidth(),{ Text(label("دقائق إضافية", "Additional minutes")) })
                CompactVoucherField(newProfile,{ newProfile = it },Modifier.fillMaxWidth(),{ Text(label("الباقة", "Profile")) })
                snapshot?.profiles?.forEach { profile -> TextButton(onClick = { newProfile = profile["name"].orEmpty() }) { Text(profile["name"].orEmpty()) } }
            }
        }, confirmButton = { Button(onClick = { val minutes = addMinutes.toLongOrNull(); if (minutes == null) message = "Invalid time" else run { manager!!.addTime(row[".id"].orEmpty(), minutes*60); label("تمت إضافة الوقت", "Time added") }; selectedUser = null }, enabled = !busy) { Text(label("إضافة وقت", "Add time")) } }, dismissButton = { TextButton(onClick = { run { manager!!.update(row[".id"].orEmpty(),mapOf("profile" to newProfile)); label("تم تغيير الباقة", "Profile changed") }; selectedUser = null }, enabled = !busy && newProfile.isNotBlank()) { Text(label("تغيير الباقة", "Change profile")) } })
    }
    qrVoucher?.let { voucher ->
        AlertDialog(onDismissRequest = { qrVoucher = null }, title = { Text(voucher.username) }, text = {
            Column {
                Image(VoucherQrCodeFactory.create(VoucherQrPayloadBuilder.build(voucher),240).asImageBitmap(),"Voucher QR",Modifier.size(200.dp))
                Text(voucher.branding.networkName + " • " + voucher.displayDuration(arabic))
                Text(if(voucher.username == voucher.password) label("الكود = كلمة المرور", "Code = password") else "Password: " + voucher.password)
            }
        }, confirmButton = { TextButton(onClick = { val request=VoucherBatchRequest(1,6,profile=voucher.profile,mode=voucher.mode); run { val file=VoucherPdfExporter().export(context,VoucherBatch(request,listOf(voucher))); VoucherShareManager.shareFile(context,file,"application/pdf"); label("تم تجهيز الطباعة", "Print file ready") } }) { Text("PDF / " + label("طباعة", "Print")) } }, dismissButton = { TextButton(onClick = { qrVoucher = null }) { Text(label("رجوع", "Back")) } })
    }
    preview?.let { page ->
        Dialog(onDismissRequest = { preview = null }, properties = DialogProperties(usePlatformDefaultWidth = false)) {
            Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding()) {
                TextButton(onClick = { preview = null }) { Text(label("معاينة فقط — رجوع", "Preview only — Back")) }
                AndroidView(factory = { ctx -> WebView(ctx).apply {
                    settings.javaScriptEnabled = true
                    val rendered = PortalTemplates.render(assets(), design)
                    val html = rendered.getValue(page)
                    // Preview uses the real assets with explicit sample servlet values; it cannot contact RouterOS.
                    val sample = mapOf("username" to "123456", "uptime" to "12m", "session-time-left" to "48m", "bytes-in-nice" to "1 MiB", "bytes-out-nice" to "24 MiB", "remain-bytes-in" to "", "remain-bytes-out" to "", "ip" to "192.168.88.101", "link-login-only" to "#", "link-logout" to "#", "link-status" to "#", "chap-id" to "", "chap-challenge" to "", "ssl-login" to "yes", "plain-passwd" to "no")
                    var previewHtml = html.replace(Regex("\\$\\(if error\\).*?\\$\\(endif\\)",RegexOption.DOT_MATCHES_ALL),"")
                    previewHtml = previewHtml.replace(Regex("\\$\\(if ([a-z-]+)\\)(.*?)\\$\\(else\\)(.*?)\\$\\(endif\\)",RegexOption.DOT_MATCHES_ALL)) { match -> if(sample[match.groupValues[1]].isNullOrBlank()) match.groupValues[3] else match.groupValues[2] }
                    sample.forEach { (key,value) -> previewHtml = previewHtml.replace("\$("+key+")",value) }
                    previewHtml = previewHtml.replace("<link rel=\"stylesheet\" href=\"fg.css\">","<style>"+rendered.getValue("fg.css")+"</style>").replace("<script src=\"fg.js\"></script>","<script>"+rendered.getValue("fg.js")+"</script>").replace("<script src=\"md5.js\"></script>","<script>"+rendered.getValue("md5.js")+"</script>")
                    loadDataWithBaseURL(null,previewHtml,"text/html","UTF-8",null)
                } }, modifier = Modifier.weight(1f).fillMaxWidth())
            }
        }
    }
}
