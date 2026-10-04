package com.fgmachines.mikrotikmanager.advanced

import com.fgmachines.mikrotikmanager.network.RouterOsTransport
import java.time.*
import java.time.format.DateTimeFormatter
import java.util.Locale

data class ClientPortsPlan(val bridge: String, val ports: List<String>, val changes: List<ConfigurationChange>, val signature: String)

/** Small, reviewed automation using the application's existing authenticated transport. */
class RouterAutomation(private val transport: RouterOsTransport) {
    suspend fun synchronizeClock(now: Instant = Instant.now(), zone: ZoneId = ZoneId.systemDefault()): Boolean {
        val previous = transport.read("system/clock").firstOrNull() ?: error("Cannot read router clock")
        val ntp = transport.read("system/ntp/client").firstOrNull() ?: error("Cannot read time synchronization settings")
        val servers = transport.read("system/ntp/client/servers")
        val local = now.atZone(zone)
        val format = if (previous["date"].orEmpty().contains('-')) DateTimeFormatter.ISO_LOCAL_DATE else DateTimeFormatter.ofPattern("MMM/dd/yyyy", Locale.ENGLISH)
        transport.execute("/system/clock/set", mapOf("time-zone-autodetect" to "no", "time-zone-name" to zone.id))
        // Date and time must be separate across daylight-saving changes.
        transport.execute("/system/clock/set", mapOf("date" to local.format(format).lowercase(Locale.ENGLISH)))
        transport.execute("/system/clock/set", mapOf("time" to local.format(DateTimeFormatter.ofPattern("HH:mm:ss"))))
        if (servers.none { !it["address"].isNullOrBlank() && it["enabled"] !in listOf("no", "false") }) {
            listOf("time.cloudflare.com", "time.google.com").forEach {
                val existing = servers.firstOrNull { row -> row["address"] == it }
                if (existing == null) transport.create("system/ntp/client/servers", mapOf("address" to it))
                else if (existing["enabled"] in listOf("no", "false")) {
                    require(!existing[".id"].isNullOrBlank()) { "Cannot enable time server without its ID" }
                    transport.execute("/system/ntp/client/servers/set", mapOf(".id" to existing.getValue(".id"), "enabled" to "yes"))
                }
            }
        }
        if (ntp["enabled"] !in listOf("yes", "true")) transport.execute("/system/ntp/client/set", mapOf("enabled" to "yes"))
        val clock = transport.read("system/clock").firstOrNull() ?: error("Clock verification failed")
        val verified = clockInstant(clock, zone) ?: error("Clock verification failed")
        require(kotlin.math.abs(Duration.between(now, verified).seconds) < 120) { "Router clock did not match the phone after setup" }
        require(transport.read("system/ntp/client").firstOrNull()?.get("enabled") in listOf("yes", "true")) { "Automatic time synchronization could not be verified" }
        return transport.read("system/ntp/client").firstOrNull()?.get("status") == "synchronized"
    }

    companion object {
        fun clockInstant(row: RouterRow, fallback: ZoneId = ZoneId.systemDefault()): Instant? = runCatching {
            val date = row["date"].orEmpty()
            val day = if (date.contains('-')) LocalDate.parse(date) else LocalDate.parse(date, java.time.format.DateTimeFormatterBuilder().parseCaseInsensitive().appendPattern("MMM/dd/uuuu").toFormatter(Locale.ENGLISH))
            val time = LocalTime.parse(row["time"].orEmpty())
            val zone = row["gmt-offset"]?.let { runCatching { ZoneOffset.of(it) }.getOrNull() }
                ?: row["time-zone-name"]?.let { runCatching { ZoneId.of(it) }.getOrNull() } ?: fallback
            day.atTime(time).atZone(zone).toInstant()
        }.getOrNull()

        fun clientCandidates(report: ReadinessReport): List<RouterRow> {
            val protected = report.rows("interface/bridge/port").mapNotNull { it["interface"] }.toSet() +
                report.rows("interface/list/member").filter { it["list"].equals("WAN", true) }.mapNotNull { it["interface"] } +
                report.rows("ip/dhcp-client").mapNotNull { it["interface"] } +
                report.rows("interface/pppoe-client").mapNotNull { it["interface"] } + report.wanInterface
            return report.rows("interface").filter {
                val name = it["name"].orEmpty()
                AdvancedRouterManager.enabled(it) && name !in protected &&
                    (name == report.clientInterface || it["type"] in listOf("ether", "bridge", "wlan", "wifi") || name.matches(Regex("ether[0-9]+")))
            }
        }

        fun planPorts(report: ReadinessReport, managementKey: String): ClientPortsPlan {
            val needed = setOf("interface", "interface/bridge", "interface/bridge/port", "interface/vlan", "interface/list/member", "ip/dhcp-client", "interface/pppoe-client", "ip/address", "ip/dhcp-server", "ip/hotspot", "ip/firewall/filter", "ip/firewall/nat")
            require(report.unavailable.intersect(needed).isEmpty()) { "Cannot safely detect customer ports; reconnect and read configuration" }
            require(report.wanInterface.isNotBlank() && report.clientInterface.isNotBlank()) { "Internet and customer networks must be detected first" }
            val selected = report.clientInterface
            val address = report.rows("ip/address").filter { it["interface"] == selected }
            require(address.none { managementKey.contains(it["address"].orEmpty().substringBefore('/').ifBlank { "<unknown>" }) }) { "Use the internet-side management connection before grouping customer ports" }
            // Rules tied to the old physical interface need human review, never silently rewrite them.
            val existingBridge = report.rows("interface/bridge").any { it["name"] == selected }
            if (!existingBridge) require(report.rows("ip/firewall/filter").none { it["in-interface"] == selected || it["out-interface"] == selected } && report.rows("ip/firewall/nat").none { it["in-interface"] == selected || it["out-interface"] == selected }) { "Interface-specific firewall rules require review before grouping ports" }
            val occupied = report.rows("interface/bridge/port").mapNotNull { it["interface"] }.toSet() +
                report.rows("interface/vlan").mapNotNull { it["interface"] } +
                report.rows("ip/address").filter { it["interface"] != selected }.mapNotNull { it["interface"] } +
                report.rows("ip/dhcp-server").filter { it["interface"] != selected }.mapNotNull { it["interface"] } +
                report.rows("ip/hotspot").filter { it["interface"] != selected }.mapNotNull { it["interface"] }
            val spare = clientCandidates(report).filter { row ->
                val name = row["name"].orEmpty()
                name != selected && name !in occupied && (row["type"] == "ether" || name.matches(Regex("ether[0-9]+"))) && row["running"] !in listOf("true", "yes")
            }.map { it.getValue("name") }
            val bridge = if (existingBridge) selected else "fg-clients"
            require(existingBridge || report.rows("interface").none { it["name"] == bridge }) { "Customer bridge name is already used; review existing network" }
            val changes = mutableListOf<ConfigurationChange>()
            fun add(menu: String, args: RouterRow, ar: String) { changes += ConfigurationChange(menu, "add", args, ar, ar) }
            if (!existingBridge) {
                require(report.rows("interface").any { it["name"] == selected && (it["type"] == "ether" || selected.matches(Regex("ether[0-9]+"))) }) { "Automatic grouping supports a physical customer port or existing bridge" }
                require(selected !in occupied && clientCandidates(report).any { it["name"] == selected }) { "Customer port already belongs to another network" }
                add("interface/bridge", mapOf("name" to bridge, "comment" to "FG MTM customer network"), "إنشاء شبكة موحدة لمنافذ العملاء")
                for (menu in listOf("ip/address", "ip/dhcp-server", "ip/hotspot", "interface/list/member")) for (row in report.rows(menu).filter { it["interface"] == selected }) {
                    require(!row[".id"].isNullOrBlank() && row["dynamic"] !in listOf("yes", "true")) { "Cannot migrate a dynamic or unidentified customer setting" }
                    changes += ConfigurationChange(menu, "set", mapOf(".id" to row.getValue(".id"), "interface" to bridge), "ربط إعداد العملاء الحالي بالشبكة الموحدة", "Move existing customer settings to bridge", mapOf(".id" to row.getValue(".id"), "interface" to selected))
                }
            }
            val ports = if (existingBridge) spare else listOf(selected) + spare
            ports.forEach { add("interface/bridge/port", mapOf("bridge" to bridge, "interface" to it), "ضم منفذ العملاء $it") }
            return ClientPortsPlan(bridge, ports, changes, report.signature)
        }
    }
}
