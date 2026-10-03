package com.fgmachines.mikrotikmanager.business

/** Local app permissions. RouterOS credentials still govern access outside this app. */
enum class BusinessPermission { READ, CUSTOMER, POST, REVERSE, CONFIGURE, IMPORT, EXPORT, TEAM, WALLET, ROUTER, VOUCHERS, BRANCHES, AUTH }
object BusinessAccess {
    fun permissions(role: String): Set<BusinessPermission> = when(role) {
        "OWNER" -> BusinessPermission.entries.toSet()
        "ADMIN" -> BusinessPermission.entries.toSet()-BusinessPermission.AUTH
        "MANAGER" -> BusinessPermission.entries.toSet()-setOf(BusinessPermission.AUTH,BusinessPermission.BRANCHES,BusinessPermission.VOUCHERS)
        "TECHNICIAN" -> setOf(BusinessPermission.READ,BusinessPermission.CUSTOMER,BusinessPermission.IMPORT,BusinessPermission.ROUTER)
        "CASHIER" -> setOf(BusinessPermission.READ,BusinessPermission.CUSTOMER,BusinessPermission.POST)
        "READ_ONLY" -> setOf(BusinessPermission.READ)
        "RESELLER" -> emptySet()
        else -> emptySet()
    }
    fun permits(role: String,permission: BusinessPermission)=permission in permissions(role)
    fun sameScope(role: String,assigned: BusinessScope,requested: BusinessScope)=
        assigned.organizationId==requested.organizationId && (role in setOf("OWNER","ADMIN") || assigned.branchId==requested.branchId)
}
