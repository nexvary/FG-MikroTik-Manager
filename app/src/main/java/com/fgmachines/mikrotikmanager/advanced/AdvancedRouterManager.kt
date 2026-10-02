package com.fgmachines.mikrotikmanager.advanced

import com.fgmachines.mikrotikmanager.hotspot.HotspotManager
import com.fgmachines.mikrotikmanager.network.RouterOsTransport
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlinx.coroutines.NonCancellable
import java.security.MessageDigest
import java.util.UUID

typealias RouterRow = Map<String, String>
enum class CheckState { READY, NEEDS_SETUP, PROBLEM, UNKNOWN }
data class ReadinessCheck(val key: String, val ar: String, val en: String, val state: CheckState, val messageAr: String, val messageEn: String, val technical: String = "")
data class PingEvidence(val sent: Int, val received: Int, val latencyMs: Double?) {
    val lossPercent get() = if (sent > 0) 100 * (sent - received) / sent else null
}
data class ReadinessReport(val tables: Map<String, List<RouterRow>>, val checks: List<ReadinessCheck>, val clientInterface: String, val wanInterface: String, val internet: PingEvidence?, val signature: String, val unavailable: Set<String> = emptySet(), val readErrors: Map<String, String> = emptyMap()) {
    val required = setOf("route", "wan", "nat", "dns", "client", "ip", "dhcp", "dhcp-network", "pool", "hotspot", "profile", "files", "api")
    val blockers get() = checks.filter { it.key in required && it.state != CheckState.READY }
    val ready get() = blockers.isEmpty()
    fun rows(menu: String) = tables[menu].orEmpty()
    fun check(key: String) = checks.firstOrNull { it.key == key }
}
data class ClientSetupRequest(val interfaceName: String, val gatewayCidr: String, val networkCidr: String, val poolRange: String, val dnsName: String, val replacePortal: Boolean = false)
data class ConfigurationChange(val menu: String, val command: String, val attributes: Map<String, String>, val ar: String, val en: String, val undo: Map<String, String>? = null)
data class PreparationPlan(val request: ClientSetupRequest, val changes: List<ConfigurationChange>, val profileName: String, val installPortal: Boolean, val signature: String)
data class PreparationResult(val backupName: String, val changes: Int, val portalDirectory: String?, val readiness: ReadinessReport)

/** Diagnostics and reviewed repairs over the existing authenticated transport. */
class AdvancedRouterManager(private val transport: RouterOsTransport, val routerKey: String = "router") {
    private val menus = listOf("system/resource", "system/clock", "interface", "interface/bridge", "interface/list/member", "ip/address", "ip/route", "ip/firewall/nat", "ip/firewall/filter", "ip/dns", "ip/dhcp-server", "ip/dhcp-server/network", "ip/pool", "ip/hotspot", "ip/hotspot/profile", "ip/hotspot/user/profile", "ip/service", "user", "system/scheduler", "file")
    suspend fun inspect(client: String? = null, deep: Boolean = true): ReadinessReport {
        val tables = linkedMapOf<String, List<RouterRow>>()
        val errors = mutableSetOf<String>()
        val reasons = mutableMapOf<String, String>()
        for (menu in menus) try { tables[menu] = transport.read(menu) } catch (e: Exception) {
            if (e is CancellationException) throw e
            errors += menu; reasons[menu] = failureKind(e); tables[menu] = emptyList()
        }
        val probe = if (deep) ping("1.1.1.1") else null
        val dnsProbe = if (deep) ping("one.one.one.one") else null
        return evaluate(tables, errors, client, probe, dnsProbe, reasons)
    }
    suspend fun preflight(): ReadinessReport = inspect(deep = false)
    private suspend fun ping(address: String): PingEvidence? = try {
        pingEvidence(transport.execute("/ping", mapOf("address" to address, "count" to "3", "interval" to "300ms")))
    } catch (e: Exception) { if (e is CancellationException) throw e; null }

    fun evaluate(tables: Map<String, List<RouterRow>>, errors: Set<String> = emptySet(), client: String? = null, internet: PingEvidence? = null, dnsPing: PingEvidence? = null, readErrors: Map<String, String> = emptyMap()): ReadinessReport {
        fun rows(menu: String) = tables[menu].orEmpty()
        val route = rows("ip/route").firstOrNull { it["dst-address"] == "0.0.0.0/0" && enabled(it) && it["active"] in listOf("true", "yes") }
        val wan = route?.get("immediate-gw")?.substringAfter('%', "")?.substringBefore(',').orEmpty().ifBlank {
            rows("interface/list/member").firstOrNull { it["list"].equals("WAN", true) }?.get("interface").orEmpty()
        }
        val resolvedWan = wan.ifBlank { route?.get("gateway")?.takeIf { gateway -> rows("interface").any { it["name"] == gateway } }.orEmpty() }
        val selected = client?.takeIf { rows("interface").any { row -> row["name"] == it } }
            ?: rows("ip/hotspot").firstOrNull()?.get("interface")
            ?: rows("interface/bridge").firstOrNull { it["name"] != wan }?.get("name")
            ?: rows("ip/address").firstOrNull { it["interface"] != wan && it["dynamic"] != "true" }?.get("interface").orEmpty()
        val address = rows("ip/address").firstOrNull { it["interface"] == selected && enabled(it) }
        val dhcp = rows("ip/dhcp-server").firstOrNull { it["interface"] == selected && enabled(it) && it["invalid"] !in listOf("true", "yes") }
        val hotspot = rows("ip/hotspot").firstOrNull { it["interface"] == selected && enabled(it) && it["invalid"] !in listOf("true", "yes") }
        val profile = rows("ip/hotspot/profile").firstOrNull { it["name"] == hotspot?.get("profile") }
        val loginFiles = profile != null && portalFilesPresent(profile, rows("file"))
        val dns = rows("ip/dns").firstOrNull().orEmpty()
        val nat = rows("ip/firewall/nat").any { servesClientNat(it, address?.get("address").orEmpty(), wan) }
        val resource = rows("system/resource").firstOrNull().orEmpty()
        val api = rows("ip/service").any { enabled(it) && it["name"] in listOf("api", "api-ssl", "www-ssl", "www") }
        val checks = mutableListOf<ReadinessCheck>()
        fun check(key: String, ar: String, en: String, menu: String, ok: Boolean, missingAr: String, missingEn: String, technical: String = "", bad: Boolean = false) {
            val unknown = menu in errors
            val failure = readErrors[menu]
            val unknownAr = when (failure) {
                "connection" -> "انقطع اتصال التطبيق بالراوتر أثناء القراءة. أعد الاتصال ثم حدّث الفحص."
                "permission" -> "رفض الراوتر قراءة هذا القسم بسبب الصلاحيات. راجع مجموعة المستخدم."
                "unsupported" -> "هذا القسم غير متاح في إصدار RouterOS أو الحزمة الحالية."
                else -> "تعذّرت قراءة هذا القسم؛ لم نثبت وجود مشكلة في إعداداته."
            }
            val unknownEn = when (failure) {
                "connection" -> "Connection lost while reading; reconnect and refresh the check."
                "permission" -> "Router denied access to this section; check the user group."
                "unsupported" -> "Section unavailable on this RouterOS version or package."
                else -> "Section could not be read; its configuration has not been verified."
            }
            checks += ReadinessCheck(key, ar, en, if (unknown) CheckState.UNKNOWN else if (ok) CheckState.READY else if (bad) CheckState.PROBLEM else CheckState.NEEDS_SETUP,
                if (unknown) unknownAr else if (ok) "جاهز" else missingAr,
                if (unknown) unknownEn else if (ok) "Ready" else missingEn, technical)
        }
        checks += ReadinessCheck("internet", "الإنترنت", "Internet", if (internet == null) CheckState.UNKNOWN else if (internet.received > 0) CheckState.READY else CheckState.PROBLEM,
            if (internet == null) "لم يتم قياس الوصول إلى الإنترنت." else if (internet.received > 0) "استجاب اختبار الإنترنت." else "لم يستجب اختبار الإنترنت عبر البوابة الحالية؛ قد يكون اختبار ICMP محجوبًا.",
            if (internet == null) "Internet reachability was not measured." else if (internet.received > 0) "Internet probe responded." else "No internet probe response through the current gateway; ICMP may be blocked.")
        check("route", "مسار الإنترنت", "Default internet route", "ip/route", route != null, "لا يوجد مسار إنترنت نشط؛ اختر البوابة من إدارة الشبكة.", "No active default route; choose a gateway in Network management.", route?.get("gateway").orEmpty())
        check("wan", "شبكة الإنترنت", "WAN", "interface", resolvedWan.isNotBlank() && rows("interface").any { it["name"] == resolvedWan && enabled(it) && it["running"] in listOf("true", "yes") }, "واجهة الإنترنت غير نشطة أو لم يمكن تحديدها.", "Internet interface is inactive or could not be identified.", resolvedWan)
        check("nat", "مشاركة اتصال الإنترنت", "Internet sharing", "ip/firewall/nat", nat, "قاعدة مشاركة الإنترنت غير موجودة؛ قد تستخدم الشبكة توجيهًا مباشرًا بدلًا منها.", "No source NAT rule found; the network may use direct routing instead.")
        check("dns", "أسماء المواقع", "DNS", "ip/dns", dns["servers"].orEmpty().isNotBlank() || dns["dynamic-servers"].orEmpty().isNotBlank(), "خادم أسماء المواقع غير مضبوط.", "No DNS servers configured.", (dns["servers"].orEmpty()+" "+dns["dynamic-servers"].orEmpty()).trim())
        if (dnsPing != null) checks += ReadinessCheck("dns-probe", "اختبار أسماء المواقع", "DNS reachability probe", if (dnsPing.received > 0) CheckState.READY else CheckState.PROBLEM, if (dnsPing.received > 0) "نجح الوصول بالاسم." else "لم ينجح الوصول بالاسم؛ راجع DNS والبوابة.", if (dnsPing.received > 0) "Hostname probe succeeded." else "Hostname probe failed; review DNS and gateway.")
        check("bridge", "ربط منافذ العملاء", "Client bridge", "interface/bridge", rows("interface/bridge").any { enabled(it) }, "لا يوجد ربط للمنافذ؛ استخدام واجهة منفردة يظل ممكنًا.", "No bridge configured; a standalone interface is still supported.")
        check("client", "شبكة العملاء", "Client network", "interface", selected.isNotBlank() && rows("interface").any { it["name"] == selected && enabled(it) }, "اختر واجهة أو شبكة للعملاء.", "Select a client interface or bridge.", selected)
        check("ip", "عنوان شبكة العملاء", "Client gateway address", "ip/address", address != null, "شبكة العملاء تحتاج عنوان بوابة.", "Client network needs a gateway address.", address?.get("address").orEmpty())
        check("dhcp", "توزيع عناوين العملاء", "Client address distribution", "ip/dhcp-server", dhcp != null, "توزيع العناوين غير مفعّل لشبكة العملاء.", "No valid enabled DHCP server on the client network.")
        check("dhcp-network", "بوابة العملاء وأسماء المواقع", "Client gateway/DNS assignment", "ip/dhcp-server/network", address != null && validCidr(address["address"].orEmpty()) && rows("ip/dhcp-server/network").any { it["address"] == networkOf(address["address"].orEmpty()) && !it["gateway"].isNullOrBlank() && !it["dns-server"].isNullOrBlank() }, "توزيع العناوين يحتاج بوابة وخادم أسماء مواقع.", "DHCP network needs gateway and DNS settings.")
        check("pool", "مدى عناوين العملاء", "Client address pool", "ip/pool", dhcp != null && rows("ip/pool").any { it["name"] == dhcp["address-pool"] && !it["ranges"].isNullOrBlank() }, "مدى العناوين غير موجود أو غير مرتبط بالتوزيع.", "DHCP pool is missing or not assigned.")
        check("hotspot", "دخول العملاء بالكروت", "HotSpot", "ip/hotspot", hotspot != null, "HotSpot غير مفعّل على شبكة العملاء.", "HotSpot is not enabled on the client network.")
        check("profile", "إعداد دخول العملاء", "HotSpot profile", "ip/hotspot/profile", profile != null && rows("ip/hotspot/user/profile").isNotEmpty() && profile["login-by"].orEmpty().split(',').any { it in listOf("http-chap", "https") }, "إعداد الخادم أو باقة المستخدمين ناقصة.", "Server profile or user profiles are missing.", profile?.get("name").orEmpty())
        check("files", "صفحة دخول العملاء", "Customer login page", "file", loginFiles, "ملفات صفحة الدخول والحالة غير موجودة.", "Login/status page files are missing.")
        check("api", "اتصال إدارة الراوتر", "RouterOS API", "ip/service", api, "الخدمة غير متاحة أو لا يمكن التحقق منها.", "Management service unavailable or could not be verified.")
        check("admins", "مديرو الراوتر", "Router administrators", "user", rows("user").any { enabled(it) && it["group"] == "full" }, "لم نتحقق من وجود مدير بصلاحية كاملة.", "No enabled full administrator verified.")
        val clock = rows("system/clock").firstOrNull().orEmpty()
        val year = Regex("(?:19|20)[0-9]{2}").find(clock["date"].orEmpty())?.value?.toIntOrNull()
        check("clock", "الساعة والتاريخ", "Clock", "system/clock", year != null && year >= 2024, "اضبط ساعة الراوتر قبل استخدام الانتهاء المطلق للكروت.", "Set router clock before using absolute voucher expiry.", clock["date"].orEmpty()+" "+clock["time"].orEmpty())
        check("scheduler", "جدولة انتهاء الكروت", "Scheduler", "system/scheduler", "system/scheduler" !in errors, "تعذّر الوصول إلى الجدولة.", "Scheduler access is unavailable.")
        check("storage", "المساحة المتاحة", "Free storage", "system/resource", (resource["free-hdd-space"]?.toLongOrNull() ?: 0) > 2_000_000, "المساحة منخفضة أو غير معروفة؛ راجع الملفات قبل التثبيت.", "Free storage is low or unknown; review files before installing.", resource["free-hdd-space"].orEmpty()+" B")
        check("cpu", "المعالج", "CPU", "system/resource", (resource["cpu-load"]?.toIntOrNull() ?: 100) < 90, "حمل المعالج مرتفع أو غير معروف.", "CPU load is high or unknown.", resource["cpu-load"].orEmpty()+"%")
        val free = resource["free-memory"]?.toLongOrNull(); val total = resource["total-memory"]?.toLongOrNull()
        check("memory", "الذاكرة", "RAM", "system/resource", free != null && total != null && total > 0 && free.toDouble()/total > .1, "الذاكرة المتاحة منخفضة أو غير معروفة.", "Free memory is low or unknown.", if (free != null && total != null && total > 0) "${100-100*free/total}%" else "")
        val drops = rows("ip/firewall/filter").filter { enabled(it) && it["chain"] == "forward" && it["action"] in listOf("drop", "reject") }
        checks += ReadinessCheck("firewall", "قواعد حماية الشبكة", "Firewall review", if ("ip/firewall/filter" in errors) CheckState.UNKNOWN else if (drops.isEmpty()) CheckState.READY else CheckState.NEEDS_SETUP,
            if ("ip/firewall/filter" in errors) "تعذّرت قراءة قواعد الحماية؛ لا يمكن تحديد وجود قواعد حجب." else if (drops.isEmpty()) "لا توجد قواعد حجب مرور للمراجعة." else "توجد قواعد حجب؛ راجع ترتيبها. وجودها وحده لا يثبت تعارضًا مع HotSpot.", if ("ip/firewall/filter" in errors) "Firewall could not be read; forwarding rules are unknown." else if (drops.isEmpty()) "No forwarding drop rules to review." else "Drop rules exist; review their order. Their presence alone does not establish a HotSpot conflict.", drops.size.toString())
        return ReadinessReport(tables, checks, selected, resolvedWan, internet, signature(tables), errors, readErrors)
    }

    fun suggestion(report: ReadinessReport, interfaceName: String = report.clientInterface): ClientSetupRequest {
        val existing = report.rows("ip/address").firstOrNull { it["interface"] == interfaceName && validCidr(it["address"].orEmpty()) }?.get("address")
        val cidr = existing ?: (10..250).firstOrNull { n -> report.rows("ip/address").none { validCidr(it["address"].orEmpty()) && overlaps(it["address"].orEmpty(), "192.168.$n.1/24") } }?.let { "192.168.$it.1/24" } ?: "172.31.250.1/24"
        val base = ipNumber(networkOf(cidr).substringBefore('/'))
        val broadcast = base + (1L shl (32-cidr.substringAfter('/').toInt())) - 1
        val gateway = ipNumber(cidr.substringBefore('/'))
        var first = base + 10; var last = minOf(base+250, broadcast-1)
        if (first > last) first=base+1
        if (gateway in first..last) { if(gateway-first > last-gateway) last=gateway-1 else first=gateway+1 }
        return ClientSetupRequest(interfaceName, cidr, networkOf(cidr), numberIp(first)+"-"+numberIp(last), "wifi.local")
    }

    suspend fun plan(request: ClientSetupRequest): PreparationPlan {
        require(validCidr(request.gatewayCidr) && validCidr(request.networkCidr) && networkOf(request.gatewayCidr) == networkOf(request.networkCidr)) { "Check client gateway and network addresses" }
        val range = request.poolRange.replace(" ", "").split('-')
        require(range.size == 2 && range.all { validIp(it) && belongs(it, request.networkCidr) } && ipNumber(range[0]) <= ipNumber(range[1]) && ipNumber(request.gatewayCidr.substringBefore('/')) !in ipNumber(range[0])..ipNumber(range[1])) { "Client pool must fit the selected subnet and exclude its gateway" }
        require(request.dnsName.matches(Regex("[A-Za-z0-9](?:[A-Za-z0-9.-]*[A-Za-z0-9])?"))) { "Invalid customer DNS name" }
        val base = ipNumber(networkOf(request.networkCidr).substringBefore('/'))
        val broadcast = base + (1L shl (32-request.networkCidr.substringAfter('/').toInt())) - 1
        require(ipNumber(range[0]) > base && ipNumber(range[1]) < broadcast) { "Address pool cannot include network or broadcast addresses" }
        val report = inspect(request.interfaceName, deep = false)
        require(report.unavailable.intersect(setOf("interface", "ip/address", "ip/route", "ip/pool", "ip/dns", "ip/dhcp-server", "ip/dhcp-server/network", "ip/hotspot", "ip/hotspot/profile", "file", "ip/firewall/nat")).isEmpty()) { "Configuration could not be verified; check account permissions before setup" }
        require(report.rows("interface").any { it["name"] == request.interfaceName && enabled(it) }) { "Choose an enabled client interface" }
        require(request.interfaceName != report.wanInterface || report.wanInterface.isBlank()) { "The internet interface cannot be used as the client network" }
        require(report.rows("ip/address").none { it["interface"] != request.interfaceName && validCidr(it["address"].orEmpty()) && overlaps(it["address"].orEmpty(), request.networkCidr) }) { "Client subnet overlaps another interface; choose a separate network" }
        val changes = mutableListOf<ConfigurationChange>()
        val tag = "fg-mtm-" + UUID.randomUUID().toString().take(8)
        fun add(menu: String, args: RouterRow, ar: String, en: String) { changes += ConfigurationChange(menu, "add", args, ar, en) }
        fun set(menu: String, args: RouterRow, old: RouterRow, ar: String, en: String) { changes += ConfigurationChange(menu, "set", args, ar, en, old) }
        val addressRows = report.rows("ip/address").filter { it["interface"] == request.interfaceName && enabled(it) }
        require(addressRows.isEmpty() || addressRows.any { it["address"] == request.gatewayCidr }) { "Preserve the existing gateway: select its current address" }
        if (addressRows.isEmpty()) add("ip/address", mapOf("address" to request.gatewayCidr, "interface" to request.interfaceName, "comment" to "FG MTM client gateway"), "إضافة عنوان شبكة العملاء", "Add client gateway")
        val dhcp = report.rows("ip/dhcp-server").firstOrNull { it["interface"] == request.interfaceName }
        val pool = dhcp?.get("address-pool")?.takeIf { name -> report.rows("ip/pool").any { it["name"] == name } }
            ?: report.rows("ip/pool").firstOrNull { it["ranges"] == request.poolRange.replace(" ", "") }?.get("name")
        val poolName = pool ?: tag
        if (pool == null) add("ip/pool", mapOf("name" to poolName, "ranges" to request.poolRange.replace(" ", "")), "إنشاء مدى العناوين الناقص", "Create missing address pool")
        val dns = report.rows("ip/dns").firstOrNull().orEmpty()
        if (dns["servers"].isNullOrBlank() && dns["dynamic-servers"].isNullOrBlank()) set("ip/dns", mapOf("servers" to "1.1.1.1,9.9.9.9"), mapOf("servers" to dns["servers"].orEmpty()), "ضبط خوادم أسماء المواقع دون فتح الخدمة للإنترنت", "Configure DNS servers without exposing a public resolver")
        if (report.rows("ip/dhcp-server/network").none { it["address"] == request.networkCidr }) add("ip/dhcp-server/network", mapOf("address" to request.networkCidr, "gateway" to request.gatewayCidr.substringBefore('/'), "dns-server" to if (dns["allow-remote-requests"] in listOf("yes", "true")) request.gatewayCidr.substringBefore('/') else "1.1.1.1,9.9.9.9"), "إعداد توزيع شبكة العملاء", "Add client DHCP network")
        if (dhcp == null) add("ip/dhcp-server", mapOf("name" to tag, "interface" to request.interfaceName, "address-pool" to poolName, "disabled" to "no"), "إنشاء موزع العناوين الناقص", "Create missing DHCP server")
        else if (!enabled(dhcp)) set("ip/dhcp-server", mapOf(".id" to dhcp.getValue(".id"), "disabled" to "no"), mapOf(".id" to dhcp.getValue(".id"), "disabled" to "yes"), "تفعيل توزيع العناوين الحالي", "Enable existing DHCP server")
        else require(dhcp["invalid"] !in listOf("yes", "true")) { "Existing DHCP is invalid; review its interface before automatic changes" }
        if (dhcp != null && pool == null) set("ip/dhcp-server", mapOf(".id" to dhcp.getValue(".id"), "address-pool" to poolName), mapOf(".id" to dhcp.getValue(".id"), "address-pool" to dhcp["address-pool"].orEmpty()), "ربط مدى العناوين الحالي", "Assign the missing DHCP pool")
        if (report.rows("ip/firewall/nat").none { servesClientNat(it, request.networkCidr, report.wanInterface) } && report.wanInterface.isNotBlank()) add("ip/firewall/nat", mapOf("chain" to "srcnat", "action" to "masquerade", "src-address" to request.networkCidr, "out-interface" to report.wanInterface, "comment" to "FG MTM client internet"), "إضافة مشاركة الإنترنت لشبكة العملاء فقط", "Add missing NAT for the client subnet only")
        val server = report.rows("ip/hotspot").firstOrNull { it["interface"] == request.interfaceName }
        val profile = report.rows("ip/hotspot/profile").firstOrNull { it["name"] == server?.get("profile") }
        val profileName = profile?.get("name") ?: tag
        if (profile == null) add("ip/hotspot/profile", mapOf("name" to profileName, "hotspot-address" to request.gatewayCidr.substringBefore('/'), "dns-name" to request.dnsName, "login-by" to "http-chap"), "إنشاء إعداد دخول العملاء الناقص", "Create missing HotSpot profile")
        if (profile != null && profile["login-by"].orEmpty().split(',').none { it in listOf("http-chap", "https") }) set("ip/hotspot/profile", mapOf(".id" to profile.getValue(".id"), "login-by" to (profile["login-by"].orEmpty().split(',').filter(String::isNotBlank) + "http-chap").joinToString(",")), mapOf(".id" to profile.getValue(".id"), "login-by" to profile["login-by"].orEmpty()), "إضافة دخول آمن بالكروت", "Add CHAP voucher authentication")
        if (report.rows("ip/hotspot/user/profile").isEmpty()) add("ip/hotspot/user/profile", mapOf("name" to "fg-default", "shared-users" to "1"), "إنشاء باقة افتراضية للكروت", "Create default voucher profile")
        if (server == null) add("ip/hotspot", mapOf("name" to tag, "interface" to request.interfaceName, "profile" to profileName, "address-pool" to poolName, "disabled" to "no"), "إنشاء خادم دخول العملاء الناقص", "Create missing HotSpot server")
        else if (!enabled(server)) set("ip/hotspot", mapOf(".id" to server.getValue(".id"), "disabled" to "no"), mapOf(".id" to server.getValue(".id"), "disabled" to "yes"), "تفعيل دخول العملاء الحالي", "Enable existing HotSpot server")
        else require(server["invalid"] !in listOf("true", "yes")) { "Existing HotSpot is invalid; inspect the client interface" }
        return PreparationPlan(request, changes, profileName, request.replacePortal || profile == null || !portalFilesPresent(profile, report.rows("file")), report.signature)
    }

    suspend fun apply(plan: PreparationPlan, portalFiles: Map<String, String>, backupPassword: String, onChange: (ConfigurationChange, Boolean, String) -> Unit = { _,_,_ -> }, onBackup: (String) -> Unit = {}): PreparationResult {
        val current = inspect(plan.request.interfaceName, deep = false)
        require(current.unavailable.isEmpty()) { "Configuration read failed; reconnect and review a fresh plan" }
        require(current.signature == plan.signature) { "Router configuration changed after preview; review a fresh plan" }
        val backup = backup(backupPassword)
        onBackup(backup)
        val undo = mutableListOf<suspend () -> Unit>()
        var directory: String? = null
        try {
            for (change in plan.changes) {
                if (change.command == "add") {
                    val result = transport.create(change.menu, change.attributes)
                    val id = result.firstOrNull()?.get(".id") ?: transport.read(change.menu).firstOrNull { row -> change.attributes["name"]?.let { row["name"] == it } ?: (row["address"] == change.attributes["address"] && row["interface"] == change.attributes["interface"]) }?.get(".id") ?: error("Cannot verify a created item")
                    undo += { transport.execute("/${change.menu}/remove", mapOf(".id" to id)); Unit }
                } else {
                    transport.execute("/${change.menu}/${change.command}", change.attributes)
                    change.undo?.let { previous -> undo += { transport.execute("/${change.menu}/set", previous); Unit } }
                }
                onChange(change, true, backup)
            }
            if (plan.installPortal) {
                val profile = transport.read("ip/hotspot/profile").first { it["name"] == plan.profileName }
                directory = HotspotManager(transport).installPortal(profile.getValue(".id"), portalFiles)
            }
            return PreparationResult(backup, plan.changes.size, directory, inspect(plan.request.interfaceName, deep = false))
        } catch (failure: Exception) {
            val failedRollback = mutableListOf<String>()
            withContext(NonCancellable) { for (action in undo.asReversed()) try { action() } catch (_: Exception) { failedRollback += "rollback" } }
            if (failure is CancellationException) throw failure
            throw IllegalStateException("${failure.message} — ${if (failedRollback.isEmpty()) "new changes rolled back" else "rollback needs review"}; backup: $backup", failure)
        }
    }
    suspend fun backup(password: String): String {
        require(password.length >= 12) { "Backup password needs at least 12 characters" }
        val name = "fg-mtm-" + System.currentTimeMillis() + "-" + UUID.randomUUID().toString().take(4)
        transport.execute("/system/backup/save", mapOf("name" to name, "password" to password, "encryption" to "aes-sha256"))
        repeat(5) { if (transport.read("file").any { it["name"] == "$name.backup" }) return "$name.backup"; delay(200) }
        error("Backup could not be verified; configuration changes were not applied")
    }
    suspend fun exportConfiguration(): String {
        val name = "fg-mtm-export-" + System.currentTimeMillis()
        val version = transport.read("system/resource").firstOrNull()?.get("version").orEmpty()
        transport.execute("/export", buildMap { put("file", name); if (version.startsWith("6.")) put("hide-sensitive", "yes") })
        require(transport.read("file").any { it["name"] == "$name.rsc" }) { "Export could not be verified" }
        return "$name.rsc"
    }
    suspend fun listBackups() = transport.read("file").filter { it["name"]?.endsWith(".backup") == true || it["name"]?.endsWith(".rsc") == true }
    suspend fun restore(file: String, password: String) {
        require(transport.read("file").any { it["name"] == file && file.endsWith(".backup") }) { "Choose an existing router backup" }
        transport.execute("/system/backup/load", mapOf("name" to file, "password" to password))
    }
    suspend fun reboot() { transport.execute("/system/reboot") }
    suspend fun flushDns() { transport.execute("/ip/dns/cache/flush") }
    suspend fun repairDns(password: String, onBackup: (String) -> Unit = {}): String {
        val row = transport.read("ip/dns").firstOrNull().orEmpty()
        require(row["servers"].isNullOrBlank() && row["dynamic-servers"].isNullOrBlank()) { "DNS already configured; no changes needed" }
        val file = backup(password)
        onBackup(file)
        transport.execute("/ip/dns/set", mapOf("servers" to "1.1.1.1,9.9.9.9"))
        return file
    }
    companion object {
        fun enabled(row: RouterRow) = row["disabled"] !in listOf("yes", "true")
        fun portalFilesPresent(profile: RouterRow, files: List<RouterRow>): Boolean {
            val path = profile["html-directory-override"].orEmpty().ifBlank { profile["html-directory"].orEmpty().ifBlank { "hotspot" } }.trimEnd('/')
            return listOf("login.html", "status.html").all { file -> files.any { it["name"] == "$path/$file" || it["name"] == "flash/$path/$file" } }
        }
        fun pingEvidence(rows: List<RouterRow>): PingEvidence {
            val summary = rows.lastOrNull { it["sent"]?.toIntOrNull() != null }
            if (summary != null) return PingEvidence(summary["sent"]!!.toInt(), summary["received"]?.toIntOrNull() ?: 0, milliseconds(summary["avg-rtt"].orEmpty()))
            val responses = rows.filter { it["time"].orEmpty().isNotBlank() && it["status"].isNullOrBlank() }
            return PingEvidence(rows.size, responses.size, responses.mapNotNull { milliseconds(it["time"].orEmpty()) }.takeIf { it.isNotEmpty() }?.average())
        }
        private fun milliseconds(value: String): Double? {
            if (value.isBlank()) return null
            val ms = Regex("([0-9.]+)ms").find(value)?.groupValues?.get(1)?.toDoubleOrNull() ?: 0.0
            val us = Regex("([0-9.]+)us").find(value)?.groupValues?.get(1)?.toDoubleOrNull() ?: 0.0
            return if (ms > 0 || us > 0) ms + us / 1000 else value.toDoubleOrNull()
        }
        private fun failureKind(error: Throwable): String {
            val causes = generateSequence(error) { it.cause }.take(8).toList()
            if (causes.any { it is java.io.IOException }) return "connection"
            val message = causes.joinToString(" ") { it.message.orEmpty() }.lowercase()
            return when {
                "permission" in message || "not allowed" in message -> "permission"
                "no such command" in message || "unknown command" in message -> "unsupported"
                "connection" in message || "broken pipe" in message -> "connection"
                else -> "unknown"
            }
        }
        private fun signature(tables: Map<String, List<RouterRow>>): String {
            val keys = setOf(".id", "name", "interface", "address", "ranges", "profile", "address-pool", "disabled", "servers", "allow-remote-requests", "chain", "action", "src-address", "out-interface", "dst-address", "gateway", "login-by", "dns-name", "html-directory", "html-directory-override", "group", "network", "lease-time", "dns-server", "netmask", "port", "certificate", "hotspot-address", "in-interface", "out-interface-list", "in-interface-list", "protocol", "dst-port", "src-port", "connection-state", "to-addresses", "to-ports", "list")
            val text = tables.filterKeys { it !in listOf("system/resource", "system/clock", "system/scheduler") }.toSortedMap().map { (menu, rows) -> menu + rows.filter { row -> row["dynamic"] !in listOf("true", "yes") && (menu != "file" || row["name"].orEmpty().substringAfterLast('/') in setOf("login.html", "status.html", "md5.js")) }.map { row -> row.filterKeys { it in keys }.toSortedMap().toString() }.sorted().joinToString() }.joinToString()
            return MessageDigest.getInstance("SHA-256").digest(text.toByteArray()).joinToString("") { "%02x".format(it) }
        }
        private fun servesClientNat(row: RouterRow, cidr: String, wan: String): Boolean {
            if (!enabled(row) || row["chain"] != "srcnat" || row["action"] !in listOf("masquerade", "src-nat")) return false
            val output = row["out-interface"].orEmpty()
            if (output.isNotBlank() && wan.isNotBlank() && output != wan) return false
            val source = row["src-address"].orEmpty()
            return source.isBlank() || source == "0.0.0.0/0" || (validCidr(source) && validCidr(cidr) && belongs(cidr.substringBefore('/'), source) && source.substringAfter('/').toInt() <= cidr.substringAfter('/').toInt())
        }
        fun validIp(value: String) = value.split('.').let { parts -> parts.size == 4 && parts.all { it.toIntOrNull()?.let { n -> n in 0..255 } == true } }
        fun validCidr(value: String) = value.split('/').let { it.size == 2 && validIp(it[0]) && it[1].toIntOrNull()?.let { n -> n in 1..30 } == true }
        private fun numberIp(value: Long) = (3 downTo 0).joinToString(".") { ((value shr (it*8)) and 255).toString() }
        private fun overlaps(a: String,b: String) = belongs(a.substringBefore('/'),b) || belongs(b.substringBefore('/'),a)
        private fun ipNumber(ip: String): Long = ip.split('.').fold(0L) { n, part -> (n shl 8) + part.toLong() }
        fun networkOf(cidr: String): String {
            require(validCidr(cidr)); val prefix = cidr.substringAfter('/').toInt(); val mask = (0xffffffffL shl (32-prefix)) and 0xffffffffL
            val value = ipNumber(cidr.substringBefore('/')) and mask
            return (3 downTo 0).joinToString(".") { ((value shr (it*8)) and 255).toString() } + "/$prefix"
        }
        private fun belongs(ip: String, cidr: String) = networkOf("$ip/${cidr.substringAfter('/')}") == networkOf(cidr)
    }
}
