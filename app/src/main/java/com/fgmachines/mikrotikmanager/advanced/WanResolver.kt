package com.fgmachines.mikrotikmanager.advanced

/** Pure RouterOS evidence resolver; never infers WAN from a port's spelling. */
object WanResolver {
    private fun yes(row: RouterRow, key: String) = row[key] in listOf("yes", "true")
    fun candidates(t: Map<String, List<RouterRow>>) = t["interface"].orEmpty().filter {
        AdvancedRouterManager.enabled(it) && it["type"] !in setOf("loopback", "dummy") && it["name"].orEmpty() !in setOf("lo", "loopback") && it["name"].orEmpty().isNotBlank()
    }
    private fun usable(t: Map<String, List<RouterRow>>, name: String) = candidates(t).any { it["name"] == name }
    fun routeNames(t: Map<String, List<RouterRow>>, r: RouterRow): Set<String> {
        fun direct(value: String): Set<String> = value.split(',').map { token -> token.trim().let {
            when { '%' in it -> it.substringAfter('%').substringBefore('@'); usable(t, it) -> it; else -> "" }
        }}.filter { usable(t, it) }.toSet()
        direct(r["immediate-gw"].orEmpty()).takeIf { it.isNotEmpty() }?.let { return it }
        // RouterOS v6 emits 'gateway reachable via interface' instead of immediate-gw.
        val status = r["gateway-status"].orEmpty()
        if ("reachable via " in status && "unreachable" !in status) direct(status.substringAfter("reachable via ")).takeIf { it.isNotEmpty() }?.let { return it }
        direct(r["gateway"].orEmpty()).takeIf { it.isNotEmpty() }?.let { return it }
        val gateway = r["gateway"].orEmpty().substringBefore('@').trim()
        if (!AdvancedRouterManager.validIp(gateway)) return emptySet()
        val dhcp = t["ip/dhcp-client"].orEmpty().filter { AdvancedRouterManager.enabled(it) && it["status"] == "bound" && it["gateway"] == gateway }.mapNotNull { it["interface"] }.filter { usable(t,it) }.toSet()
        if (dhcp.size == 1) return dhcp
        fun ipv4(value: String) = value.split('.').fold(0L) { n, part -> (n shl 8) + part.toLong() }
        val matches = t["ip/address"].orEmpty().filter { a ->
            val cidr = a["address"].orEmpty(); val prefix = cidr.substringAfter('/', "").toIntOrNull()
            AdvancedRouterManager.enabled(a) && usable(t,a["interface"].orEmpty()) && AdvancedRouterManager.validIp(cidr.substringBefore('/')) && prefix != null && prefix in 0..32 &&
                (ipv4(gateway) shr (32-prefix)) == (ipv4(cidr.substringBefore('/')) shr (32-prefix))
        }
        val prefix = matches.maxOfOrNull { it.getValue("address").substringAfter('/').toInt() } ?: return emptySet()
        return matches.filter { it.getValue("address").substringAfter('/').toInt() == prefix }.mapNotNull { it["interface"] }.toSet().takeIf { it.size == 1 } ?: emptySet()
    }
    fun activeRoutes(t: Map<String, List<RouterRow>>) = t["ip/route"].orEmpty().filter { it["dst-address"] == "0.0.0.0/0" && AdvancedRouterManager.enabled(it) && yes(it,"active") && !yes(it,"blackhole") && !yes(it,"unreachable") && !yes(it,"prohibit") }
    fun hasRoute(t: Map<String, List<RouterRow>>, name: String) = activeRoutes(t).any { r ->
        r["routing-table"].orEmpty().let { it.isBlank() || it == "main" } && r["routing-mark"].orEmpty().isBlank() && name in routeNames(t,r)
    }
    fun detect(t: Map<String, List<RouterRow>>, client: String = ""): WanDetection {
        val routes = activeRoutes(t).filter { it["routing-table"].orEmpty().let { v -> v.isBlank() || v == "main" } && it["routing-mark"].orEmpty().isBlank() }
        if (routes.isNotEmpty()) {
            val distance = routes.minOf { it["distance"]?.toIntOrNull() ?: 1 }
            val best = routes.filter { (it["distance"]?.toIntOrNull() ?: 1) == distance }
            val names = best.flatMap { routeNames(t,it) }.toSet()
            if (best.any { routeNames(t,it).isEmpty() } || names.size != 1 || names.single() == client) return WanDetection()
            val name = names.single(); val r = best.first()
            return WanDetection(name,"Active default route",distance,r["routing-table"].orEmpty(),"high",r["gateway"].orEmpty(),r["immediate-gw"].orEmpty(),t["interface/pppoe-client"].orEmpty().firstOrNull { it["name"] == name }?.get("interface").orEmpty())
        }
        fun unique(rows: List<RouterRow>, key: String, source: String, confidence: String): WanDetection? {
            val names=rows.mapNotNull { it[key] }.filter { it != client && usable(t,it) }.toSet()
            return if (names.size == 1) WanDetection(names.single(),source,confidence=confidence) else if(names.size>1) WanDetection() else null
        }
        unique(t["ip/dhcp-client"].orEmpty().filter { AdvancedRouterManager.enabled(it) && it["status"] == "bound" },"interface","Bound DHCP client","high")?.let { return it }
        unique(t["interface/pppoe-client"].orEmpty().filter { AdvancedRouterManager.enabled(it) && (yes(it,"running") || it["status"] == "connected") },"name","Connected PPPoE client","high")?.let { return it }
        unique(candidates(t).filter { it["type"] == "lte" && yes(it,"running") },"name","Running LTE interface","medium")?.let { return it }
        return WanDetection()
    }
}
