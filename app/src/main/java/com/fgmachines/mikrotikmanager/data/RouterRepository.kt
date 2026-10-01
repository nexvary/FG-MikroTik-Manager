package com.fgmachines.mikrotikmanager.data

import com.fgmachines.mikrotikmanager.network.RestRouterOsTransport
import com.fgmachines.mikrotikmanager.network.RouterOsTransport
import com.fgmachines.mikrotikmanager.voucher.RouterVoucherProfile
import com.fgmachines.mikrotikmanager.voucher.VoucherBatch
import com.fgmachines.mikrotikmanager.voucher.VoucherMode
import com.fgmachines.mikrotikmanager.voucher.VoucherProvisionItem
import com.fgmachines.mikrotikmanager.voucher.VoucherProvisionStatus
import com.fgmachines.mikrotikmanager.voucher.VoucherProvisionSummary
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope

class RouterRepository private constructor(
    private val transport: RouterOsTransport
) : AutoCloseable {

    suspend fun loadDashboard(): DashboardSnapshot = coroutineScope {
        val identityRequest = async { transport.read("system/identity") }
        val resourceRequest = async { transport.read("system/resource") }
        val interfaceRequest = async { transport.read("interface") }

        val identity = identityRequest.await().firstOrNull().orEmpty()
        val resource = resourceRequest.await().firstOrNull().orEmpty()
        val interfaces = interfaceRequest.await().map { it.toRouterInterface() }

        DashboardSnapshot(
            identity = identity["name"].orEmpty().ifBlank { "RouterOS" },
            version = resource["version"].orEmpty(),
            boardName = resource["board-name"].orEmpty(),
            architecture = resource["architecture-name"].orEmpty(),
            cpuLoadPercent = resource["cpu-load"]?.toIntOrNull(),
            freeMemoryBytes = resource["free-memory"]?.toLongOrNull(),
            totalMemoryBytes = resource["total-memory"]?.toLongOrNull(),
            uptime = resource["uptime"].orEmpty(),
            interfaces = interfaces
        )
    }

    suspend fun loadInterfaces(): List<RouterInterface> =
        transport.read("interface").map { it.toRouterInterface() }

    suspend fun loadVoucherProfiles(mode: VoucherMode): List<RouterVoucherProfile> {
        val menu = when (mode) {
            VoucherMode.HOTSPOT -> "ip/hotspot/user/profile"
            VoucherMode.PPPOE -> "ppp/profile"
            VoucherMode.USER_MANAGER -> "user-manager/profile"
            VoucherMode.OFFLINE -> return emptyList()
        }

        return transport.read(menu)
            .mapNotNull { record ->
                val name = record["name"].orEmpty()
                if (name.isBlank()) {
                    null
                } else {
                    RouterVoucherProfile(
                        name = name,
                        rateLimit = record["rate-limit"].orEmpty(),
                        sharedUsers = record["shared-users"]?.toIntOrNull(),
                        sessionTimeout = record["session-timeout"].orEmpty()
                    )
                }
            }
            .sortedBy { it.name.lowercase() }
    }

    suspend fun provisionVoucherBatch(batch: VoucherBatch): VoucherProvisionSummary {
        if (batch.request.mode == VoucherMode.OFFLINE) {
            val skipped = batch.vouchers.map {
                VoucherProvisionItem(
                    username = it.username,
                    status = VoucherProvisionStatus.SKIPPED,
                    message = "Offline batch"
                )
            }
            return skipped.toSummary(batch.request.mode)
        }

        val menu = when (batch.request.mode) {
            VoucherMode.HOTSPOT -> "ip/hotspot/user"
            VoucherMode.PPPOE -> "ppp/secret"
            VoucherMode.USER_MANAGER -> "user-manager/user"
            VoucherMode.OFFLINE -> error("Handled above")
        }

        val existingNames = transport.read(menu)
            .mapNotNull { it["name"]?.takeIf(String::isNotBlank) }
            .toMutableSet()

        val results = ArrayList<VoucherProvisionItem>(batch.vouchers.size)

        for (voucher in batch.vouchers) {
            if (voucher.username in existingNames) {
                results += VoucherProvisionItem(
                    username = voucher.username,
                    status = VoucherProvisionStatus.DUPLICATE,
                    message = "Username already exists on router"
                )
                continue
            }

            try {
                val created = when (batch.request.mode) {
                    VoucherMode.HOTSPOT ->
                        transport.create(
                            "ip/hotspot/user",
                            buildMap {
                                put("name", voucher.username)
                                put("password", voucher.password)
                                put("profile", voucher.profile)
                                put("server", voucher.server)
                                if (voucher.comment.isNotBlank()) {
                                    put("comment", voucher.comment)
                                }
                                voucher.limitUptime?.takeIf(String::isNotBlank)?.let {
                                    put("limit-uptime", it)
                                }
                                voucher.limitBytesTotal?.let {
                                    put("limit-bytes-total", it.toString())
                                }
                            }
                        )

                    VoucherMode.PPPOE ->
                        transport.create(
                            "ppp/secret",
                            buildMap {
                                put("name", voucher.username)
                                put("password", voucher.password)
                                put("service", "pppoe")
                                put("profile", voucher.profile)
                                if (voucher.comment.isNotBlank()) {
                                    put("comment", voucher.comment)
                                }
                            }
                        )

                    VoucherMode.USER_MANAGER -> {
                        val user = transport.create(
                            "user-manager/user",
                            buildMap {
                                put("name", voucher.username)
                                put("password", voucher.password)
                                if (voucher.comment.isNotBlank()) {
                                    put("comment", voucher.comment)
                                }
                            }
                        )

                        if (voucher.profile.isNotBlank() &&
                            !voucher.profile.equals("No Profile", ignoreCase = true)
                        ) {
                            transport.create(
                                "user-manager/user-profile",
                                mapOf(
                                    "user" to voucher.username,
                                    "profile" to voucher.profile
                                )
                            )
                        }

                        user
                    }

                    VoucherMode.OFFLINE -> emptyList()
                }

                existingNames += voucher.username
                results += VoucherProvisionItem(
                    username = voucher.username,
                    status = VoucherProvisionStatus.CREATED,
                    message = "Created",
                    routerId = created.firstOrNull()?.get(".id")
                )
            } catch (t: Throwable) {
                results += VoucherProvisionItem(
                    username = voucher.username,
                    status = VoucherProvisionStatus.FAILED,
                    message = t.message ?: "RouterOS rejected voucher"
                )
            }
        }

        return results.toSummary(batch.request.mode)
    }

    override fun close() = transport.close()

    companion object {
        fun create(settings: RouterConnectionSettings): RouterRepository {
            val transport = when (settings.protocol) {
                RouterProtocol.REST_HTTP,
                RouterProtocol.REST_HTTPS -> RestRouterOsTransport(settings)
                RouterProtocol.API_SSL,
                RouterProtocol.API -> error("Native RouterOS API transport is scheduled for phase 2")
            }
            return RouterRepository(transport)
        }

        internal fun forTesting(transport: RouterOsTransport): RouterRepository =
            RouterRepository(transport)
    }
}

private fun List<VoucherProvisionItem>.toSummary(
    mode: VoucherMode
): VoucherProvisionSummary =
    VoucherProvisionSummary(
        mode = mode,
        total = size,
        created = count { it.status == VoucherProvisionStatus.CREATED },
        duplicates = count { it.status == VoucherProvisionStatus.DUPLICATE },
        failed = count { it.status == VoucherProvisionStatus.FAILED },
        skipped = count { it.status == VoucherProvisionStatus.SKIPPED },
        items = this
    )

private fun Map<String, String>.toRouterInterface(): RouterInterface =
    RouterInterface(
        id = this[".id"].orEmpty(),
        name = this["name"].orEmpty(),
        type = this["type"].orEmpty(),
        running = this["running"].toBoolean(),
        disabled = this["disabled"].toBoolean(),
        rxBytes = this["rx-byte"]?.toLongOrNull(),
        txBytes = this["tx-byte"]?.toLongOrNull()
    )
