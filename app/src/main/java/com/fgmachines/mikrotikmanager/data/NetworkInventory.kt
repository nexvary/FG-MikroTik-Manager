package com.fgmachines.mikrotikmanager.data

import com.fgmachines.mikrotikmanager.network.RouterOsTransport
import kotlinx.coroutines.CancellationException
import java.util.Locale

/** Read-only evidence, scoped to one router session. No device capability is inferred. */
object NetworkInventory {
    data class Result(val rows: List<Map<String, String>>, val warnings: List<String>)

    suspend fun load(transport: RouterOsTransport): Result {
        val evidence = mutableListOf<Pair<String, Map<String, String>>>()
        val warnings = mutableListOf<String>()
        var successes = 0
        for (menu in listOf("ip/neighbor", "ip/dhcp-server/lease", "ip/arp")) {
            try {
                transport.read(menu).forEach { evidence += menu to it }
                successes++
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                warnings += menu
            }
        }
        check(successes > 0) { "Network scan unavailable: ${warnings.joinToString()}" }
        return Result(merge(evidence), warnings)
    }

    internal fun merge(evidence: List<Pair<String, Map<String, String>>>): List<Map<String, String>> {
        val devices = linkedMapOf<String, LinkedHashMap<String, String>>()
        evidence.forEachIndexed { index, (source, row) ->
            fun first(vararg keys: String) = keys.firstNotNullOfOrNull { row[it]?.trim()?.takeIf(String::isNotEmpty) }.orEmpty()
            val rawMac = first("active-mac-address", "mac-address").replace("-", ":").uppercase(Locale.ROOT)
            val mac = rawMac.takeIf { it.matches(Regex("(?:[0-9A-F]{2}:){5}[0-9A-F]{2}")) && it != "00:00:00:00:00:00" }.orEmpty()
            val ip = first("active-address", "address", "ip-address")
            // IP alone is never enough to combine records belonging to distinct MACs.
            val key = if (mac.isNotEmpty()) "mac:$mac" else "record:$source:$index"
            val device = devices.getOrPut(key) { linkedMapOf(".id" to key) }
            fun add(field: String, value: String) {
                if (value.isNotBlank()) {
                    val values = device[field].orEmpty().split(" • ").filter(String::isNotBlank)
                    device[field] = (values + value).distinct().joinToString(" • ")
                }
            }
            add("address", ip)
            add("mac-address", mac)
            add("interface", first("interface"))
            add("platform", first("platform"))
            add("board", first("board", "board-name"))
            add("sources", when (source) { "ip/neighbor" -> "Neighbor"; "ip/dhcp-server/lease" -> "DHCP"; else -> "ARP" })
            val name = first("identity", "host-name", "name")
            if (name.isNotEmpty() && device["name"].isNullOrBlank()) device["name"] = name
        }
        return devices.values.map { device ->
            if (device["name"].isNullOrBlank()) device["name"] = device["address"] ?: device["mac-address"] ?: "Unknown device"
            device.toMap()
        }
    }
}
