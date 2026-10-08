package com.fgmachines.mikrotikmanager.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.fgmachines.mikrotikmanager.advanced.*
import com.fgmachines.mikrotikmanager.data.DashboardSnapshot
import com.fgmachines.mikrotikmanager.data.RouterAdminModule
import com.fgmachines.mikrotikmanager.hotspot.*
import com.fgmachines.mikrotikmanager.voucher.SavedVoucherBatch
import kotlinx.coroutines.launch
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun AdvancedSetupScreen(
    arabic: Boolean, snapshot: DashboardSnapshot?, manager: AdvancedRouterManager?, hotspot: HotspotManager?,
    batches: List<SavedVoucherBatch>, onRefresh: () -> Unit, onAdmin: (RouterAdminModule) -> Unit,
    onTerminal: () -> Unit, onManualNetwork: () -> Unit, modifier: Modifier = Modifier,
    initialPanel: String = "home", demo: Boolean = false
) {
    val context = LocalContext.current; val scope = rememberCoroutineScope()
    val vault = remember(manager) { RouterChangeVault(context, manager?.routerKey ?: "preview") }
    val prefs = remember { context.getSharedPreferences("fg_portal_design", 0) }
    var design by remember { mutableStateOf(runCatching { Json.decodeFromString<PortalDesign>(prefs.getString("design", null).orEmpty()) }.getOrDefault(PortalDesign())) }
    var panel by rememberSaveable { mutableStateOf(initialPanel) }
    var panelHistory by rememberSaveable { mutableStateOf(arrayListOf<String>()) }
    fun openPanel(next: String) {
        if (next != panel) { panelHistory = ArrayList(panelHistory + panel); panel = next }
    }
    fun backPanel() {
        panel = panelHistory.lastOrNull() ?: "home"
        panelHistory = ArrayList(panelHistory.dropLast(1))
    }
    if(panel=="protection") { DnsProtectionScreen(arabic,manager,{backPanel()},modifier);return }
    var report by remember { mutableStateOf<ReadinessReport?>(null) }
    var busy by remember { mutableStateOf(false) }; var message by remember { mutableStateOf("") }
    var details by rememberSaveable { mutableStateOf(false) }
    var confirmation by remember { mutableStateOf<Pair<String, () -> Unit>?>(null) }
    var backups by remember { mutableStateOf<List<RouterRow>>(emptyList()) }
    var journal by remember { mutableStateOf(runCatching { vault.changes() }.getOrDefault(emptyList())) }
    var tools by remember { mutableStateOf<String?>(null) }
    var wizardStep by rememberSaveable { mutableStateOf(0) }
    var request by remember { mutableStateOf<ClientSetupRequest?>(null) }
    var wanAuto by rememberSaveable { mutableStateOf(true) }
    var wanMenu by remember { mutableStateOf(false) }
    var plan by remember { mutableStateOf<PreparationPlan?>(null) }
    var restoreFile by remember { mutableStateOf<String?>(null) }
    var restorePassword by remember { mutableStateOf("") }
    var backupSecret by remember { mutableStateOf<String?>(null) }
    fun label(ar: String, en: String) = if (arabic) ar else en
    fun record(ar: String, en: String, success: Boolean, backup: String = "") { vault.record(ar,en,success,backup); journal = vault.changes() }
    fun run(ar: String, en: String, reload: Boolean = true, work: suspend () -> String) {
        if (manager == null || busy) return
        scope.launch {
            busy = true
            try { message = work(); record(ar,en,true); if (reload) { report = manager.inspect(request?.interfaceName, false); backups = manager.listBackups() }; onRefresh() }
            catch(e: Exception) { message = friendlyAdvancedError(e.message.orEmpty(),arabic) + if(details) "\n"+e.message.orEmpty() else ""; runCatching { record(ar,en,false) } }
            finally { busy = false }
        }
    }
    fun confirm(ar: String, en: String, action: () -> Unit) { confirmation = label(ar,en) to action }
    fun freshSecret() = UUID.randomUUID().toString().replace("-", "")
    suspend fun protectedBackup(password: String): String { val file = manager!!.backup(password); vault.rememberBackup(file,password); record("إنشاء نسخة احتياطية", "Create encrypted backup", true,file); return file }
    fun checkNetwork(next: String) { openPanel(next); run("فحص الشبكة", "Inspect network", false) { report = manager!!.inspect(request?.interfaceName, true); label("اكتمل الفحص", "Inspection completed") } }
    fun startWizard() { openPanel("wizard"); wizardStep=0; plan=null; wanAuto=true; request=report?.let { manager?.suggestion(it) } }
    LaunchedEffect(manager) {
        if (manager != null) run("فحص جاهزية الراوتر", "Router readiness", false) { report=manager.inspect(deep=true); backups=manager.listBackups(); label("تم الفحص", "Checked") }
        if (demo) {
            val rows=mapOf("interface" to listOf(mapOf("name" to "bridge-clients","running" to "true")), "ip/address" to listOf(mapOf("interface" to "bridge-clients","address" to "192.168.10.1/24")), "system/resource" to listOf(mapOf("free-hdd-space" to "25000000","cpu-load" to "3","free-memory" to "100000000","total-memory" to "128000000")), "system/clock" to listOf(mapOf("date" to "2026-10-01", "time" to "22:10:00")))
            val fake = object: com.fgmachines.mikrotikmanager.network.RouterOsTransport { override suspend fun read(menu:String)=emptyList<RouterRow>();override suspend fun create(menu:String,attributes:RouterRow)=emptyList<RouterRow>();override suspend fun execute(command:String,attributes:RouterRow)=emptyList<RouterRow>();override fun close(){} }
            report=AdvancedRouterManager(fake).evaluate(rows,client="bridge-clients")
            request=ClientSetupRequest("bridge-clients","192.168.10.1/24","192.168.10.0/24","192.168.10.10-192.168.10.250","wifi.local")
        }
    }
    LaunchedEffect(panel,wizardStep,request) {
        if (panel=="wizard" && wizardStep==5 && request!=null && manager!=null) run("مراجعة خطة الإعداد", "Preview setup plan", false) { plan=manager.plan(request!!); label("الخطة جاهزة للمراجعة", "Plan ready for review") }
    }
    BackHandler(enabled = tools != null || panel != "home") {
        when {
            tools != null -> tools = null
            panel == "wizard" && wizardStep > 0 -> wizardStep--
            else -> backPanel()
        }
    }
    LazyColumn(modifier.fillMaxSize(),contentPadding=PaddingValues(14.dp),verticalArrangement=Arrangement.spacedBy(10.dp)) {
        item {
            Text(label("حالة الراوتر وصحة الشبكة", "Router status & network health"),color=FgBlue,fontWeight=FontWeight.Bold)
            if(demo) Text(label("معاينة واجهة — بيانات اختبار", "UI preview — test data"),color=FgAmber)
            AdvancedCard {
                Text(snapshot?.boardName?.ifBlank { "MikroTik" } ?: "MikroTik",fontWeight=FontWeight.Bold,color=FgWhite)
                Text((snapshot?.identity.orEmpty())+" • RouterOS "+snapshot?.version.orEmpty(),color=FgSilver)
                val ram=if(snapshot?.freeMemoryBytes!=null && snapshot.totalMemoryBytes!=null && snapshot.totalMemoryBytes>0) "${100-100*snapshot.freeMemoryBytes/snapshot.totalMemoryBytes}%" else "—"
                Text("CPU: ${snapshot?.cpuLoadPercent ?: "—"}%  •  RAM: $ram",color=FgSilver)
                Text("Uptime: "+snapshot?.uptime.orEmpty(),color=FgSilver)
                FlowRow(horizontalArrangement=Arrangement.spacedBy(6.dp)) {
                    listOf("internet" to "Internet","wan" to "WAN","dns" to "DNS","dhcp" to "DHCP","hotspot" to "HotSpot","api" to "API").forEach { (key,title) ->
                        val state=report?.check(key)?.state
                        Text("$title ${if(state==CheckState.READY) "✓" else if(state==CheckState.PROBLEM) "✕" else if(state==null || state==CheckState.UNKNOWN) "…" else "⚠"}",color=if(state==CheckState.READY) FgMint else if(state==null || state==CheckState.UNKNOWN) FgSilver else FgAmber,modifier=Modifier.padding(vertical=4.dp))
                    }
                }
                Row(horizontalArrangement=Arrangement.spacedBy(6.dp)) {
                    TextButton(onClick={ details=!details },modifier=Modifier.weight(1f)) { Text(label("عرض التفاصيل", "Technical details")) }
                    TextButton(onClick={ checkNetwork(panel) },enabled=manager!=null&&!busy,modifier=Modifier.weight(1f)) { Text(label("تحديث", "Refresh")) }
                }
                if(details) {
                    listOf("Architecture" to snapshot?.architecture.orEmpty(),"Storage" to (report?.rows("system/resource")?.firstOrNull()?.get("free-hdd-space").orEmpty()+" B free"),"LAN IP" to report?.check("ip")?.technical.orEmpty(),"WAN" to report?.wanInterface.orEmpty(),"API" to report?.check("api")?.messageEn.orEmpty()).forEach { (key,value) -> Text("$key: $value",color=FgSilver) }
                    Text("Interfaces: ${snapshot?.interfaces?.size ?: 0}",color=FgSilver)
                }
            }
            if(busy) LinearProgressIndicator(Modifier.fillMaxWidth())
            if(message.isNotBlank()) Text(message,color=FgSilver)
            if(panel!="home") TextButton(onClick={ backPanel() }) { Text(label("رجوع للصفحة السابقة", "Back to previous page")) }
        }
        if(panel=="home") {
            report?.let { result -> item {
                Text(if(result.voucherReady) label("الراوتر جاهز لإنشاء الكروت ✓", "Router is ready for vouchers ✓") else label("الراوتر يحتاج ${result.voucherBlockers.size} خطوات قبل إنشاء الكروت", "Router needs ${result.voucherBlockers.size} setup steps"),color=if(result.voucherReady) FgMint else FgAmber)
                result.voucherBlockers.take(3).forEach { Text(if(arabic) it.messageAr else it.messageEn,color=FgSilver) }
                if (result.voucherReady && !result.ready) {
                    Text(label("صفحة العملاء تحتاج تحققًا؛ إنشاء الكروت متاح.", "Customer portal needs verification; vouchers are available."), color=FgAmber)
                    OutlinedButton(onClick={tools="design"}) { Text(label("تثبيت صفحة العملاء", "Install customer portal")) }
                }
                if(!result.voucherReady) Button(onClick={ startWizard() },enabled=manager!=null&&!busy,modifier=Modifier.fillMaxWidth()) { Text(label("جهّز الراوتر للكروت", "Prepare router for vouchers")) }
            } }
            items(listOf("readiness" to label("فحص جاهزية الراوتر", "Router readiness check"),"wizard" to label("إعداد HotSpot لأول مرة", "First-time HotSpot setup"),"doctor" to label("تشخيص الشبكة", "Network Doctor"),"repair" to label("إصلاح تلقائي", "Auto repair"),"client" to label("اختبار شبكة العملاء", "Test client network"),"portal" to label("صفحة HotSpot للعملاء", "Customer HotSpot portal"),"backup" to label("نسخة احتياطية واستعادة", "Backup & recovery"),"protection" to label("حظر الإباحية والإعلانات والتتبع", "Block adult content, ads & trackers"),"quick" to label("أدوات سريعة", "Quick tools"),"technical" to label("الإعدادات التقنية", "Technical settings"))) { (key,title) ->
                OutlinedButton(onClick={ when(key) { "wizard" -> startWizard(); "portal" -> tools="design"; "readiness","doctor","client" -> checkNetwork(key); else -> openPanel(key) } },modifier=Modifier.fillMaxWidth(),contentPadding=PaddingValues(horizontal=12.dp,vertical=12.dp)) { Text(title,modifier=Modifier.weight(1f));Icon(Icons.Outlined.ChevronRight,null) }
            }
        }
        if(panel in listOf("readiness","doctor","client")) {
            item { Text(when(panel){"doctor"->label("تشخيص الشبكة", "Network Doctor");"client"->label("اختبار شبكة العملاء", "Client network test");else->label("فحص الجاهزية", "Readiness check")},color=FgBlue,fontWeight=FontWeight.Bold) }
            report?.let { result ->
                item {
                    Text(if(result.ready) label("إعداد دخول العملاء جاهز ✓", "Client login configuration ready ✓") else label("الشبكة تحتاج إلى تدخل", "Network needs attention"),color=if(result.ready) FgMint else FgAmber)
                    result.internet?.let { ping -> Text("Packet loss: ${ping.lossPercent ?: "—"}% • Latency: ${ping.latencyMs?.let { "%.2f ms".format(Locale.ENGLISH,it) } ?: "—"}",color=FgSilver) }
                    OutlinedButton(onClick={ startWizard() },enabled=manager!=null&&!busy) { Text(label("إصلاح / تجهيز شبكة العملاء", "Repair / prepare client network")) }
                }
                val checks=if(panel=="client") result.checks.filter { it.key in result.required || it.key in listOf("internet","wan","nat") } else result.checks
                items(checks,key={it.key}) { check ->
                    AdvancedCard {
                        Text((if(check.state==CheckState.READY) "✓ " else if(check.state==CheckState.PROBLEM) "✕ " else "⚠ ")+(if(arabic) check.ar else check.en),color=if(check.state==CheckState.READY) FgMint else FgAmber,fontWeight=FontWeight.Bold)
                        Text(if(arabic) check.messageAr else check.messageEn,color=FgSilver)
                        if(details&&check.technical.isNotBlank()) Text(check.technical,color=FgSilver)
                    }
                }
            }
        }
        if(panel=="repair") {
            item {
                Text(label("الإصلاحات المقترحة", "Suggested repairs"),color=FgBlue,fontWeight=FontWeight.Bold)
                Text(label("آمن: تنظيف ذاكرة أسماء المواقع. يحتاج تأكيد: ضبط DNS وتفعيل DHCP وتجهيز HotSpot. خطر: تغيير الإنترنت أو الجسور أو الحماية؛ افتح الإدارة اليدوية لمراجعتها.", "Safe: flush DNS cache. Confirmation required: DNS, DHCP and HotSpot setup. High risk: WAN, bridges and firewall changes require manual review."),color=FgAmber)
            }
            item { OutlinedButton(onClick={ run("تنظيف DNS", "Flush DNS cache") { manager!!.flushDns();label("تم التنظيف", "Cache cleared") } },enabled=manager!=null&&!busy,modifier=Modifier.fillMaxWidth()) { Text(label("تنظيف ذاكرة أسماء المواقع — آمن", "Flush DNS cache — safe")) } }
            if(report?.check("dns")?.state==CheckState.NEEDS_SETUP) item { Button(onClick={ confirm("سيتم حفظ نسخة احتياطية وتعيين 1.1.1.1 و9.9.9.9 كخوادم DNS دون فتح الخدمة للإنترنت. تنفيذ؟", "Back up and set DNS servers to 1.1.1.1 and 9.9.9.9 without opening a public resolver. Apply?") { run("ضبط DNS", "Configure DNS") { val secret=freshSecret();val file=manager!!.repairDns(secret){vault.rememberBackup(it,secret);record("نسخة قبل DNS", "Backup before DNS",true,it)};label("تم الإصلاح؛ النسخة: ", "Repaired; backup: ")+file } } },enabled=!busy,modifier=Modifier.fillMaxWidth()) { Text(label("إصلاح إعداد أسماء المواقع", "Repair DNS configuration")) } }
            item { OutlinedButton(onClick={ startWizard() },enabled=manager!=null&&!busy,modifier=Modifier.fillMaxWidth()) { Text(label("إصلاح DHCP وHotSpot والملفات الناقصة", "Repair missing DHCP, HotSpot and portal")) } }
            item { OutlinedButton(onClick={ onAdmin(RouterAdminModule.SERVICES) },modifier=Modifier.fillMaxWidth()) { Text(label("مراجعة خدمات الإدارة — يحتاج تأكيد", "Review management services — confirmation required")) } }
            item { TextButton(onClick=onManualNetwork) { Text(label("مراجعة التغييرات الخطرة يدويًا", "Review high-risk changes manually")) } }
        }
        if(panel=="backup") {
            item {
                Text(label("النسخ الاحتياطي والاستعادة", "Backup & recovery"),color=FgBlue,fontWeight=FontWeight.Bold)
                Text(label("النسخ مشفّرة على الراوتر. تحفظ كلمة فتح النسخة محمية بمفتاح Android على هذا الهاتف. احتفظ بها إذا احتجت الاستعادة من هاتف آخر.", "Router backups are encrypted. Unlock passwords are protected by Android Keystore on this phone. Keep a copy if restoring from another phone."),color=FgSilver)
                Button(onClick={ run("إنشاء نسخة احتياطية", "Create backup") { val secret=freshSecret();val file=protectedBackup(secret);backupSecret=secret;label("تم إنشاء ", "Created ")+file } },enabled=manager!=null&&!busy,modifier=Modifier.fillMaxWidth()) { Text(label("إنشاء Backup الآن", "Backup now")) }
                OutlinedButton(onClick={ run("تصدير الإعدادات", "Export configuration") { manager!!.exportConfiguration() } },enabled=manager!=null&&!busy,modifier=Modifier.fillMaxWidth()) { Text(label("تصدير الإعدادات دون كلمات المرور", "Export configuration without passwords")) }
            }
            items(backups,key={it["name"].orEmpty()}) { row ->
                AdvancedCard {
                    val name=row["name"].orEmpty();Text(name,color=FgWhite);Text(row["creation-time"].orEmpty()+" • "+row["size"].orEmpty()+" B",color=FgSilver)
                    if(name.endsWith(".backup")) Row {
                        TextButton(onClick={ restoreFile=name;restorePassword=runCatching { vault.password(name) }.getOrNull().orEmpty() },enabled=manager!=null&&!busy) { Text(label("استعادة", "Restore")) }
                        TextButton(onClick={ backupSecret=runCatching { vault.password(name) }.getOrNull();if(backupSecret==null) message=label("كلمة فتح هذه النسخة غير محفوظة على الهاتف.", "This backup password is not saved on this phone.") }) { Text(label("كلمة فتح النسخة", "Backup password")) }
                    }
                }
            }
        }
        if(panel in listOf("quick","technical","repair","readiness")) {
            item { OutlinedButton(onClick={ run("ضبط الوقت تلقائيًا", "Automatic clock setup") {
                protectedBackup(freshSecret())
                val synced = manager!!.synchronizeClock()
                label(if(synced) "تم ضبط الوقت والتاريخ وتأكيد المزامنة بالإنترنت." else "تم ضبط الوقت والتاريخ من الهاتف وتفعيل المزامنة المستمرة؛ مزامنة الإنترنت قيد الانتظار.", if(synced) "Clock set and NTP synchronization confirmed." else "Clock set from phone; continuous NTP enabled, awaiting synchronization.")
            } },enabled=manager!=null&&!busy,modifier=Modifier.fillMaxWidth()) { Text(label("ضبط الوقت والتاريخ تلقائيًا", "Set date and time automatically")) } }
            item { OutlinedButton(onClick={ run("اكتشاف منافذ العملاء", "Detect customer ports",false) {
                val ports=manager!!.planClientPorts()
                if(ports.changes.isEmpty()) label("منافذ العملاء مجهزة بالفعل", "Customer ports already configured") else {
                    confirm("سيتم تجهيز شبكة العملاء: ${ports.bridge}\nالمنافذ: ${ports.ports.joinToString()}\n${ports.changes.joinToString("\n") { it.ar }}\nسيتم حفظ نسخة احتياطية. لن يُضم منفذ الإنترنت. قد ينقطع اتصال العملاء مؤقتًا. تنفيذ؟", "Customer bridge: ${ports.bridge}\nPorts: ${ports.ports.joinToString()}\nBack up and preserve WAN. Customers may briefly disconnect. Apply?") {
                        run("تجهيز منافذ العملاء", "Configure customer ports") { val secret=freshSecret();manager!!.applyClientPorts(ports,secret) { vault.rememberBackup(it,secret);record("نسخة قبل تجهيز المنافذ", "Backup before port setup",true,it) } }
                    }
                    label("اكتمل اكتشاف المنافذ؛ راجع الخطة", "Ports detected; review the plan")
                }
            } },enabled=manager!=null&&!busy,modifier=Modifier.fillMaxWidth()) { Text(label("تجهيز منافذ العملاء تلقائيًا", "Configure customer ports automatically")) } }
        }
        if(panel in listOf("quick","technical")) {
            item { Text(label("الأدوات والإعدادات", "Tools & settings"),color=FgBlue,fontWeight=FontWeight.Bold) }
            item { OutlinedButton(onClick={ openPanel("backup") },modifier=Modifier.fillMaxWidth()) { Text("Backup Now") } }
            item { OutlinedButton(onClick={ confirm("إعادة التشغيل ستقطع الاتصال. سيتم حفظ نسخة احتياطية أولًا. متابعة؟", "Restart disconnects this phone. Create a backup first and restart?") { run("إعادة تشغيل الراوتر", "Restart router",false) { protectedBackup(freshSecret());manager!!.reboot();label("أُرسل طلب إعادة التشغيل؛ أعد الاتصال بعد عودة الراوتر.", "Restart requested; reconnect when the router returns.") } } },enabled=manager!=null&&!busy,modifier=Modifier.fillMaxWidth()) { Text(label("إعادة تشغيل الراوتر", "Restart router")) } }
            item { OutlinedButton(onClick={ run("تنظيف DNS", "Flush DNS cache") { manager!!.flushDns();label("تم التنظيف", "Cache cleared") } },enabled=manager!=null&&!busy,modifier=Modifier.fillMaxWidth()) { Text("Flush DNS Cache") } }
            item { OutlinedButton(onClick={ checkNetwork("client") },modifier=Modifier.fillMaxWidth()) { Text(label("تحديث توزيع عناوين العملاء", "Refresh DHCP status")) } }
            item { OutlinedButton(onClick={ onAdmin(RouterAdminModule.USERS) },modifier=Modifier.fillMaxWidth()) { Text(label("إضافة مدير للراوتر", "Add router administrator")) } }
            item { OutlinedButton(onClick=onTerminal,modifier=Modifier.fillMaxWidth()) { Text(label("فتح Command Center", "Open Command Center")) } }
            item { OutlinedButton(onClick={ checkNetwork("doctor") },modifier=Modifier.fillMaxWidth()) { Text(label("اختبار الإنترنت", "Test internet")) } }
            item { OutlinedButton(onClick=onManualNetwork,modifier=Modifier.fillMaxWidth()) { Text(label("فتح إدارة الشبكة", "Open Network management")) } }
        }
        if(panel=="wizard") {
            val steps=listOf(label("شبكة العملاء", "Client network"),label("عنوان الشبكة", "Client address"),label("مدى العناوين", "Address pool"),"HotSpot",label("صفحة العملاء", "Customer portal"),label("مراجعة", "Review"),label("تنفيذ", "Apply"),label("اختبار", "Test"))
            item { Text("${wizardStep+1}/8 — ${steps[wizardStep]}",color=FgBlue,fontWeight=FontWeight.Bold) }
            if(wizardStep==0) {
                item { Text(label("تم اقتراح شبكة العملاء تلقائيًا. يمكنك متابعة الإعداد بالقيمة المختارة أو تغييرها.", "Customer network detected automatically; continue with the selection or change it."), color=FgSilver) }
                item {
                    val detected=report?.let { AdvancedRouterManager.detectWan(it.tables,request?.interfaceName.orEmpty()) }
                    val selected=request?.wanInterface.orEmpty()
                    AdvancedCard {
                        Text(label("واجهة الإنترنت / WAN", "Internet interface / WAN"),color=FgWhite,fontWeight=FontWeight.Bold)
                        Row { Checkbox(wanAuto,{ auto -> wanAuto=auto;request=request?.copy(wanInterface=if(auto) detected?.interfaceName.orEmpty() else "");plan=null });Text(label("اكتشاف تلقائي", "Auto detect"),color=FgSilver) }
                        if(wanAuto) Text(if(selected.isNotBlank()) label("تم اكتشاف واجهة الإنترنت تلقائيًا: ","Internet interface detected: ")+selected else label("تعذر تحديد واجهة الإنترنت تلقائيًا. اختر الوضع اليدوي.","WAN detection failed. Choose Manual."),color=if(selected.isBlank()) FgAmber else FgMint)
                        if(!wanAuto) {
                            Box {
                                OutlinedButton(onClick={wanMenu=true},enabled=!busy,modifier=Modifier.fillMaxWidth()) { Text(selected.ifBlank { label("اختيار واجهة الإنترنت", "Choose WAN interface") }) }
                                DropdownMenu(expanded=wanMenu,onDismissRequest={wanMenu=false}) {
                                    report?.let { WanResolver.candidates(it.tables) }.orEmpty().forEach { row -> val name=row["name"].orEmpty()
                                        DropdownMenuItem(text={Text(name)},onClick={request=request?.copy(wanInterface=name);wanMenu=false;plan=null})
                                    }
                                }
                            }
                        }
                        if(selected.isBlank()) Text(label("حدد واجهة الإنترنت قبل بدء إعداد HotSpot.","Select the Internet interface before HotSpot setup."),color=FgAmber)
                        if(selected.isNotBlank()&&selected==request?.interfaceName) Text(label("واجهة العملاء لا يمكن أن تكون واجهة الإنترنت نفسها.","WAN and client interface are the same."),color=FgAmber)
                        if(selected.isNotBlank()&&report?.let{WanResolver.hasRoute(it.tables,selected)}!=true) Text(label("الواجهة المختارة لا تبدو متصلة بمسار افتراضي للإنترنت.","No active default route on selected WAN."),color=FgAmber)
                        if(details&&detected!=null) Text("Source: ${detected.source}\nConfidence: ${detected.confidence}\nGateway: ${detected.gateway}\nImmediate gateway: ${detected.immediateGateway}\nParent: ${detected.parentInterface}",color=FgSilver)
                    }
                }
                item { Text(label("اختر شبكة العملاء. لا تختَر منفذ الإنترنت أو اتصال الإدارة الحالي؛ تشغيل HotSpot قد يفصل الهاتف.", "Select the client network. Avoid the internet or current management interface; enabling HotSpot may disconnect this phone."),color=FgAmber) }
                items(report?.let { RouterAutomation.clientCandidates(it) }.orEmpty()) { row ->
                    val name=row["name"].orEmpty();OutlinedButton(onClick={ request=(manager?.suggestion(report!!,name) ?: request?.copy(interfaceName=name))?.let { it.copy(wanInterface=if(wanAuto) AdvancedRouterManager.detectWan(report!!.tables,name).interfaceName else request?.wanInterface.orEmpty()) };plan=null },modifier=Modifier.fillMaxWidth()) { Text((if(request?.interfaceName==name) "✓ " else "")+name) }
                }
            }
            request?.let { req ->
                if(wizardStep==1) {
                    item { CompactVoucherField(req.gatewayCidr,{request=req.copy(gatewayCidr=it);runCatching{AdvancedRouterManager.networkOf(it)}.getOrNull()?.let{ n->request=request!!.copy(networkCidr=n)}},Modifier.fillMaxWidth(),{Text(label("عنوان البوابة", "Gateway address / CIDR"))}) }
                    item { CompactVoucherField(req.networkCidr,{request=req.copy(networkCidr=it)},Modifier.fillMaxWidth(),{Text(label("شبكة العملاء", "Client subnet / CIDR"))}) }
                }
                if(wizardStep==2) item { CompactVoucherField(req.poolRange,{request=req.copy(poolRange=it)},Modifier.fillMaxWidth(),{Text(label("مدى عناوين العملاء", "Client address range"))}) }
                if(wizardStep==3) {
                    item { Text(label("الواجهة: ", "Interface: ")+req.interfaceName,color=FgSilver);Text(label("سيتم استخدام الخادم والإعدادات الحالية وإنشاء الناقص فقط.", "Existing server/profile settings are reused; only missing items are created."),color=FgSilver) }
                    item { CompactVoucherField(req.dnsName,{request=req.copy(dnsName=it)},Modifier.fillMaxWidth(),{Text(label("اسم صفحة دخول الشبكة", "Customer login DNS name"))}) }
                }
                if(wizardStep==4) {
                    item { CompactVoucherField(design.networkName,{design=design.copy(networkName=it)},Modifier.fillMaxWidth(),{Text(label("اسم الشبكة التجاري", "Network brand name"))}) }
                    item { CompactVoucherField(design.supportPhone,{design=design.copy(supportPhone=it)},Modifier.fillMaxWidth(),{Text(label("رقم الدعم", "Support phone"))}) }
                    item { CompactVoucherField(design.welcome,{design=design.copy(welcome=it)},Modifier.fillMaxWidth(),{Text(label("رسالة الترحيب", "Welcome message"))}) }
                    item { OutlinedButton(onClick={ prefs.edit().putString("design",Json.encodeToString(design)).apply();tools="design" }) { Text(label("الشعار والألوان وبقية التصميم", "Logo, colors and full design")) };Row { Checkbox(req.replacePortal,{request=req.copy(replacePortal=it)});Text(label("تثبيت هذا التصميم حتى لو توجد صفحة حالية", "Install this design even if a portal exists"),color=FgSilver) } }
                }
                if(wizardStep in listOf(5,6)) item {
                    plan?.let { proposed -> AdvancedCard {
                        Text(label("سيتم تنفيذ:", "Planned changes:"),color=FgWhite)
                        Text("${proposed.request.interfaceName} → ${proposed.request.wanInterface}\nGateway: ${proposed.request.gatewayCidr}\nNetwork: ${proposed.request.networkCidr}\nPool: ${proposed.request.poolRange}\nLogin DNS: ${proposed.request.dnsName}",color=FgSilver)
                        Text("NAT: "+if(proposed.changes.any{it.menu=="ip/firewall/nat"}) label("سيتم إنشاؤه","Will create") else label("موجود","Existing"),color=FgMint)
                        Text("HotSpot: "+if(proposed.changes.any{it.menu=="ip/hotspot"&&it.command=="add"}) label("سيتم إنشاؤه","Will create") else label("موجود","Existing"),color=FgMint)
                        if(proposed.request.synchronizeTime) Text(label("• ضبط الوقت والتاريخ تلقائيًا وتفعيل المزامنة بالإنترنت", "• Set clock automatically and enable continuous NTP"),color=FgSilver)
                        proposed.changes.forEach { Text("• "+if(arabic) it.ar else it.en,color=FgSilver) }
                        if(proposed.installPortal) Text(label("• تثبيت صفحة العملاء في مجلد جديد", "• Install customer portal into a new directory"),color=FgSilver)
                        if(proposed.changes.isEmpty()&&!proposed.installPortal&&!proposed.request.synchronizeTime) Text(label("لا توجد إعدادات ناقصة لإنشائها.", "No missing setup items to create."),color=FgMint)
                        Text(label("سيتم حفظ Backup مشفّر أولًا. لن يتم تعديل اتصال الإنترنت أو المديرين أو قواعد المشاركة العاملة. قد ينقطع اتصال الهاتف على شبكة العملاء؛ استخدم منفذ إدارة منفصلًا. الرجوع: النسخة الاحتياطية محفوظة في تبويب الاستعادة.", "An encrypted backup is required first. WAN, administrators and existing working NAT rules are preserved. Client-network connectivity may be interrupted; use a separate management interface. Recovery: restore the saved backup from Backup & recovery."),color=FgAmber)
                    } }
                }
                if(wizardStep==6) item { Button(onClick={ val chosen=plan?:return@Button;confirm("تطبيق الخطة على شبكة العملاء مع حفظ Backup؟ قد ينقطع الاتصال.", "Apply the reviewed client-network plan after backup? Connectivity may be interrupted.") { run("تجهيز الراوتر للكروت", "Prepare router for vouchers",false) { val secret=freshSecret();val assets=context.assets.list("hotspot").orEmpty().associateWith { context.assets.open("hotspot/$it").bufferedReader().use{it.readText()} };val result=manager!!.apply(chosen,PortalTemplates.render(assets,design),secret,onChange={change,success,backup->record(change.ar,change.en,success,backup)}){file->vault.rememberBackup(file,secret);record("نسخة قبل إعداد الشبكة", "Backup before network setup",true,file)};report=result.readiness;record("تطبيق خطة شبكة العملاء", "Apply client setup",result.readiness.ready,result.backupName);prefs.edit().putString("design",Json.encodeToString(design)).apply();wizardStep=7;label("اكتمل التنفيذ؛ النسخة: ", "Applied; backup: ")+result.backupName } } },enabled=manager!=null&&plan!=null&&plan?.request==req&&!busy,modifier=Modifier.fillMaxWidth()) { Text(label("تنفيذ", "Apply")) } }
                if(wizardStep==7) {
                    item { Text(if(report?.ready==true) label("شبكة العملاء جاهزة للكروت ✓", "Client network ready for vouchers ✓") else label("تم التنفيذ، لكن توجد نقاط تحتاج مراجعة.", "Applied, with remaining items to review."),color=if(report?.ready==true) FgMint else FgAmber);Text(label("اختبر عميل Wi-Fi فعليًا قبل توزيع الكروت.", "Test an actual Wi-Fi client before distributing vouchers."),color=FgSilver) }
                    items(report?.blockers.orEmpty()) { Text(if(arabic) it.messageAr else it.messageEn,color=FgAmber) }
                    item { OutlinedButton(onClick={ checkNetwork("client") },modifier=Modifier.fillMaxWidth()) { Text(label("اختبار شبكة العملاء", "Test client network")) } }
                }
                if(wizardStep<6) item { Row(horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                    if(wizardStep>0) OutlinedButton(onClick={wizardStep--},enabled=!busy,modifier=Modifier.weight(1f)) { Text(label("السابق", "Previous")) }
                    Button(onClick={ if(wizardStep==4)prefs.edit().putString("design",Json.encodeToString(design)).apply();wizardStep++ },enabled=!busy&&req.interfaceName.isNotBlank()&&req.wanInterface.isNotBlank()&&req.wanInterface!=req.interfaceName&&report?.let{WanResolver.hasRoute(it.tables,req.wanInterface)}==true&&(wizardStep!=5||plan!=null),modifier=Modifier.weight(1f)) { Text(label("التالي", "Next")) }
                } }
            }
        }
        if(journal.isNotEmpty()&&panel in listOf("home","backup","quick")) {
            item { Text(label("آخر التغييرات", "Recent changes"),color=FgBlue,fontWeight=FontWeight.Bold) }
            items(journal.take(10)) { entry -> Text(SimpleDateFormat("HH:mm",Locale.ENGLISH).format(Date(entry.at))+" — "+(if(arabic)entry.ar else entry.en)+" • "+(if(entry.success)"✓" else "✕")+if(entry.backup.isNotBlank()) "\nBackup: ${entry.backup}" else "",color=FgSilver) }
        }
        item { Spacer(Modifier.height(20.dp)) }
    }
    confirmation?.let { (text,action) -> AlertDialog(onDismissRequest={confirmation=null},title={Text(label("تأكيد التغيير", "Confirm change"))},text={Text(text)},confirmButton={Button(onClick={confirmation=null;action()}){Text(label("تنفيذ", "Apply"))}},dismissButton={TextButton(onClick={confirmation=null}){Text(label("إلغاء", "Cancel"))}}) }
    restoreFile?.let { file -> AlertDialog(onDismissRequest={restoreFile=null;restorePassword=""},title={Text(label("استعادة النسخة", "Restore backup"))},text={Column { Text(file);Text(label("قد يستبدل إعدادات الشبكة والمديرين ويعيد تشغيل الراوتر. سيتم حفظ نسخة حالية أولًا.", "May replace networking and administrators and reboot the router. Back up the current state first."),color=FgAmber);OutlinedTextField(restorePassword,{restorePassword=it},label={Text(label("كلمة فتح النسخة", "Backup password"))},visualTransformation=PasswordVisualTransformation(),singleLine=true) }},confirmButton={Button(onClick={val password=restorePassword;restoreFile=null;restorePassword="";run("طلب استعادة النسخة", "Request backup restore",false){protectedBackup(freshSecret());manager!!.restore(file,password);label("أُرسل طلب الاستعادة؛ أعد الاتصال وتحقق من الإعدادات.", "Restore requested; reconnect and verify configuration.")}},enabled=restorePassword.isNotBlank()&&!busy){Text(label("استعادة وإعادة تشغيل", "Restore and restart"))}},dismissButton={TextButton(onClick={restoreFile=null;restorePassword=""}){Text(label("إلغاء", "Cancel"))}}) }
    backupSecret?.let { secret -> AlertDialog(onDismissRequest={backupSecret=null},title={Text(label("كلمة فتح Backup", "Backup unlock password"))},text={androidx.compose.foundation.text.selection.SelectionContainer { Text(secret) }},confirmButton={TextButton(onClick={backupSecret=null}){Text(label("تم", "Done"))}}) }
    tools?.let { tab -> HotspotToolsScreen(arabic,hotspot,batches,tab,{tools=null;design=runCatching{Json.decodeFromString<PortalDesign>(prefs.getString("design",null).orEmpty())}.getOrDefault(design)}, advancedManager=manager, onAdvancedSetup={tools=null;startWizard()}) }
}
@Composable private fun AdvancedCard(content: @Composable ColumnScope.() -> Unit) {
    Card(border=BorderStroke(1.dp,FgBlue),colors=CardDefaults.cardColors(containerColor=FgPanel)) { Column(Modifier.fillMaxWidth().padding(12.dp),verticalArrangement=Arrangement.spacedBy(6.dp),content=content) }
}

private fun friendlyAdvancedError(message: String,arabic: Boolean): String {
    if(!arabic)return message.ifBlank{"Operation could not be completed; check connection and permissions."}
    return when {
        message.contains("management connection",true)->"أنت متصل عبر شبكة العملاء المراد تعديلها. اتصل بالراوتر من شبكة الإنترنت الرئيسية أولًا حتى نحافظ على اتصال الإدارة أثناء تجهيز المنافذ."
        message.contains("firewall rules",true)->"توجد قواعد حماية مرتبطة بمنفذ العملاء مباشرة؛ لا يمكن نقلها تلقائيًا دون مراجعة حتى لا تتعطل الشبكة."
        message.contains("clock",true)||message.contains("time synchronization",true)->"لم نتمكن من تأكيد ضبط الساعة أو تفعيل المزامنة. تأكد من صحة وقت الهاتف وصلاحية الحساب؛ لن نعرض الساعة جاهزة دون تحقق."
        message.contains("safely detect",true)||message.contains("must be detected",true)->"تعذّر تحديد منافذ الإنترنت والعملاء بأمان. أعد فحص الشبكة؛ لن نضم منافذ مجهولة أو مستخدمة لشبكة أخرى."
        message.contains("connection",true)||message.contains("broken pipe",true)||message.contains("read failed",true)->"تعذّرت قراءة الراوتر أو انقطع الاتصال. أعد الاتصال ثم افتح خطة جديدة؛ لا تكرر أوامر التعديل قبل التحقق من نتيجتها."
        message.contains("backup",true)->"تعذّر إكمال العملية أو حفظ النسخة الاحتياطية. لم يعتمد التطبيق نجاح التعديل؛ راجع الاتصال والصلاحيات وآخر التغييرات."
        message.contains("overlap",true)->"شبكة العملاء تتداخل مع شبكة أخرى. اختر نطاقًا منفصلًا."
        message.contains("existing gateway",true)->"اختر عنوان البوابة الموجود على هذه الواجهة للحفاظ على اتصال الشبكة."
        message.contains("pool",true)->"راجع مدى عناوين العملاء: يجب أن يقع داخل الشبكة ولا يشمل البوابة أو عنوان الشبكة أو البث."
        message.contains("internet interface",true)->"لا يمكن استخدام منفذ الإنترنت كشبكة للعملاء؛ اختر واجهة أخرى."
        message.contains("DNS name",true)->"اكتب اسمًا صالحًا لصفحة دخول الشبكة مثل wifi.local."
        message.contains("changed after",true)->"تغيّرت إعدادات الراوتر بعد المراجعة. افتح خطة جديدة قبل التنفيذ."
        message.contains("permission",true)->"لم نتمكن من التحقق من الإعدادات. راجع صلاحية الحساب والاتصال أولًا."
        else->"تعذّر إكمال العملية. راجع الاتصال وإعدادات شبكة العملاء؛ التفاصيل متاحة في العرض التقني."
    }
}
