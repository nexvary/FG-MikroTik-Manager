package com.fgmachines.mikrotikmanager.data

enum class RouterAdminModule(
    val menuCandidates: List<String>
) {
    INTERFACES(listOf("interface")),
    WIFI(listOf("interface/wifi", "interface/wireless")),
    BRIDGE(listOf("interface/bridge")),
    IP_ADDRESSES(listOf("ip/address")),
    DHCP(listOf("ip/dhcp-server", "ip/dhcp-client")),
    DNS(listOf("ip/dns")),
    ROUTES(listOf("ip/route")),
    FIREWALL(listOf("ip/firewall/filter")),
    HOTSPOT(listOf("ip/hotspot/user", "ip/hotspot/active")),
    PPP(listOf("ppp/secret", "ppp/active")),
    QUEUES(listOf("queue/simple")),
    USERS(listOf("user")),
    SERVICES(listOf("ip/service")),
    FILES(listOf("file")),
    LOGS(listOf("log"))
}

data class RouterMenuSnapshot(
    val module: RouterAdminModule,
    val menuPath: String,
    val rows: List<Map<String, String>>
)
