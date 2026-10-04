package com.fgmachines.mikrotikmanager.data

data class RouterInterface(
    val id: String,
    val name: String,
    val type: String,
    val running: Boolean,
    val disabled: Boolean,
    val rxBytes: Long?,
    val txBytes: Long?
)

data class DashboardSnapshot(
    val identity: String,
    val version: String,
    val boardName: String,
    val architecture: String,
    val cpuLoadPercent: Int?,
    val freeMemoryBytes: Long?,
    val totalMemoryBytes: Long?,
    val uptime: String,
    val interfaces: List<RouterInterface>
)
