package com.fgmachines.mikrotikmanager.accesspoint

import java.util.Locale

typealias ApRow = Map<String, String>
/** Evidence describes what this router can see, never what a hidden downstream network contains. */
object AccessPointEngine {
    val menus = listOf("ip/neighbor", "ip/arp", "ip/dhcp-server/lease", "interface/bridge/host", "interface/ethernet", "ip/hotspot/active", "ip/hotspot/host")
    fun mac(value: String?) = value.orEmpty().trim().replace('-', ':').uppercase(Locale.ROOT).takeIf {
        it.matches(Regex("(?:[0-9A-F]{2}:){5}[0-9A-F]{2}")) && it != "00:00:00:00:00:00" && it.substring(0,2).toInt(16) and 1 == 0
    }.orEmpty()
    private fun tokens(value: String?) = value.orEmpty().lowercase(Locale.ROOT).split(Regex("[,; ]+")).filter(String::isNotBlank)
    private fun yes(value: String?) = value in listOf("true", "yes")
    fun discover(data: Map<String,List<ApRow>>, mappings: Map<String,ApRow> = emptyMap()): List<ApRow> {
        val groups = linkedMapOf<String, MutableList<Pair<String,ApRow>>>()
        for (source in menus.take(4)) for (row in data[source].orEmpty()) {
            if(source == "interface/bridge/host" && yes(row["local"])) continue
            val key = mac(row["active-mac-address"].orEmpty().ifBlank { row["mac-address"].orEmpty() })
            if(key.isNotEmpty()) groups.getOrPut(key){ mutableListOf() }.add(source to row)
        }
        val physical = data["interface/ethernet"].orEmpty().mapNotNull{it["name"]}.toSet()
        return groups.map { (key,evidence) ->
            val neighbors=evidence.filter{it.first=="ip/neighbor"}.map{it.second}
            val hosts=evidence.filter{it.first=="interface/bridge/host"}.map{it.second}
            fun field(vararg names:String)= evidence.firstNotNullOfOrNull { (_,r)-> names.firstNotNullOfOrNull { r[it]?.takeIf(String::isNotBlank) } }.orEmpty()
            val enabled=neighbors.flatMap{tokens(it["system-caps-enabled"])}.toSet()
            val capable=neighbors.flatMap{tokens(it["system-caps"])}.toSet()
            val apTokens=setOf("wlan-access-point","wlan","access-point")
            val confirmed=enabled.any{it in apTokens}
            val likely=capable.any{it in apTokens} && neighbors.any{it["platform"].orEmpty().isNotBlank()}
            val mapping=mappings[key].orEmpty()
            val classification=when {mapping["confirmed"]=="true"->"Confirmed AP";confirmed->"Confirmed AP";likely->"Likely AP";enabled.contains("station-only")->"Other client/device";else->"Unknown network device"}
            val ports=(hosts.mapNotNull{it["on-interface"]}+neighbors.flatMap{it["interface"].orEmpty().split(',')}).map(String::trim).filter{it in physical}.toSet()
            val port=ports.singleOrNull().orEmpty()
            val bridges=hosts.mapNotNull{it["bridge"]}.distinct()
            val reason=when{mapping["confirmed"]=="true"->"User verified";confirmed->"LLDP enabled WLAN capability";likely->"LLDP WLAN capability and platform";else->"Insufficient AP evidence"}
            linkedMapOf("mac" to key,"name" to field("identity","host-name","name").ifBlank{key},"hostname" to field("host-name"),"ip" to field("active-address","address","ip-address"),"platform" to field("platform"),"port" to port,"bridge" to (bridges.singleOrNull().orEmpty()),"classification" to classification,"reason" to reason,"sources" to evidence.map{it.first}.distinct().joinToString(", "),"protocols" to neighbors.mapNotNull{it["discovered-by"]}.distinct().joinToString(", "),"shop" to mapping["shop"].orEmpty(),"mode" to mapping["mode"].orEmpty().ifBlank{"Unknown"},"portConfidence" to if(port.isEmpty()) "Unknown" else "Derived", "state" to "Observed", "directConnection" to "Unknown")
        }
    }
    fun correlate(data: Map<String,List<ApRow>>, devices:List<ApRow>):List<ApRow> {
        val aps=devices.filter{it["classification"]=="Confirmed AP" && it["port"].orEmpty().isNotEmpty()}
        val hosts=data["interface/bridge/host"].orEmpty().filterNot{yes(it["local"])}
        return data["ip/hotspot/active"].orEmpty().map{ session ->
            val client=mac(session["mac-address"])
            val paths=hosts.filter{mac(it["mac-address"])==client}.map{it["on-interface"].orEmpty()}.filter(String::isNotBlank).toSet()
            val candidates=if(paths.size==1) aps.filter{it["port"]==paths.single()} else emptyList()
            val ap=candidates.singleOrNull()?.takeIf{it["mode"]!="NAT" && it["mac"]!=client}
            // Same upstream port can contain a switch: this is always inferred, never confirmed.
            mapOf("id" to session[".id"].orEmpty(),"account" to session["user"].orEmpty(),"client" to client,"ap" to ap?.get("mac").orEmpty(),"confidence" to if(ap==null) "Unknown" else "Inferred","upload" to session["bytes-in"].orEmpty(),"download" to session["bytes-out"].orEmpty(),"uptime" to session["uptime"].orEmpty(),"server" to session["server"].orEmpty())
        }
    }
}
