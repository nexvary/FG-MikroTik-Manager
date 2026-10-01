package com.fgmachines.mikrotikmanager.data

import com.fgmachines.mikrotikmanager.network.RestRouterOsTransport
import com.fgmachines.mikrotikmanager.network.RouterOsTransport
import com.fgmachines.mikrotikmanager.voucher.RouterVoucherMapper
import com.fgmachines.mikrotikmanager.voucher.VoucherBatch
import com.fgmachines.mikrotikmanager.voucher.VoucherWriteFailure
import com.fgmachines.mikrotikmanager.voucher.VoucherWriteResult
import com.fgmachines.mikrotikmanager.voucher.WcgCardSettings
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

    suspend fun writeVoucherBatch(
        settings: WcgCardSettings,
        batch: VoucherBatch
    ): VoucherWriteResult {
        val commands = RouterVoucherMapper.commands(settings, batch)
        val failures = mutableListOf<VoucherWriteFailure>()
        var succeeded = 0

        commands.forEachIndexed { index, command ->
            try {
                transport.create(
                    menu = command.path,
                    attributes = command.attributes
                )
                succeeded++
            } catch (t: Throwable) {
                failures += VoucherWriteFailure(
                    commandIndex = index,
                    path = command.path,
                    message = t.message ?: "RouterOS write failed"
                )
            }
        }

        return VoucherWriteResult(
            totalCommands = commands.size,
            succeededCommands = succeeded,
            failures = failures
        )
    }

    override fun close() = transport.close()

    companion object {
        fun create(settings: RouterConnectionSettings): RouterRepository {
            val transport = when (settings.protocol) {
                RouterProtocol.REST_HTTPS -> RestRouterOsTransport(settings)
                RouterProtocol.API_SSL,
                RouterProtocol.API -> error("Native RouterOS API transport is scheduled for phase 2")
            }
            return RouterRepository(transport)
        }
    }
}

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
