package com.fgmachines.mikrotikmanager.data

enum class RouterAdminGroup {
    NETWORK,
    SYSTEM
}

enum class RouterAdminModule(
    val menuCandidates: List<String>,
    val group: RouterAdminGroup,
    val canCreate: Boolean = true,
    val canEdit: Boolean = true,
    val canToggle: Boolean = true,
    val canDelete: Boolean = true
) {
    INTERFACES(
        listOf("interface"),
        RouterAdminGroup.NETWORK,
        canCreate = false
    ),
    WIFI(
        listOf("interface/wifi", "interface/wireless"),
        RouterAdminGroup.NETWORK,
        canCreate = false
    ),
    BRIDGE(listOf("interface/bridge"), RouterAdminGroup.NETWORK),
    IP_ADDRESSES(listOf("ip/address"), RouterAdminGroup.NETWORK),
    DHCP(listOf("ip/dhcp-server", "ip/dhcp-client"), RouterAdminGroup.NETWORK),
    DNS(
        listOf("ip/dns"),
        RouterAdminGroup.NETWORK,
        canCreate = false,
        canDelete = false
    ),
    ROUTES(listOf("ip/route"), RouterAdminGroup.NETWORK),
    HOTSPOT(listOf("ip/hotspot/user", "ip/hotspot/active"), RouterAdminGroup.NETWORK),
    PPP(listOf("ppp/secret", "ppp/active"), RouterAdminGroup.NETWORK),
    QUEUES(listOf("queue/simple"), RouterAdminGroup.NETWORK),

    FIREWALL(listOf("ip/firewall/filter"), RouterAdminGroup.SYSTEM),
    USERS(listOf("user"), RouterAdminGroup.SYSTEM),
    SERVICES(
        listOf("ip/service"),
        RouterAdminGroup.SYSTEM,
        canCreate = false,
        canDelete = false
    ),
    FILES(
        listOf("file"),
        RouterAdminGroup.SYSTEM,
        canCreate = false,
        canEdit = false,
        canToggle = false
    ),
    LOGS(
        listOf("log"),
        RouterAdminGroup.SYSTEM,
        canCreate = false,
        canEdit = false,
        canToggle = false,
        canDelete = false
    )
}

data class RouterMenuSnapshot(
    val module: RouterAdminModule,
    val menuPath: String,
    val rows: List<Map<String, String>>
)

data class RouterAdminActionResult(
    val success: Boolean,
    val message: String
)
