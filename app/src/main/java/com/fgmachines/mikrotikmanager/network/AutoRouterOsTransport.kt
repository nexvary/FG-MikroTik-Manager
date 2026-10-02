package com.fgmachines.mikrotikmanager.network

import com.fgmachines.mikrotikmanager.data.RouterConnectionSettings
import com.fgmachines.mikrotikmanager.data.RouterProtocol
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

class AutoRouterOsTransport internal constructor(
    private val candidatesFactory: () -> List<RouterOsTransport>
) : RouterOsTransport {

    constructor(settings: RouterConnectionSettings) : this({ listOf(
        ApiRouterOsTransport(settings.copy(port = 8728, protocol = RouterProtocol.API)),
        RestRouterOsTransport(settings.copy(port = 443, protocol = RouterProtocol.REST_HTTPS))
    ) })

    private var delegate: RouterOsTransport? = null
    private val selectionMutex = Mutex()

    override suspend fun read(menu: String): List<Map<String, String>> =
        withTransport { it.read(menu) }

    override suspend fun create(
        menu: String,
        attributes: Map<String, String>
    ): List<Map<String, String>> =
        withTransport { it.create(menu, attributes) }

    override suspend fun execute(
        command: String,
        attributes: Map<String, String>
    ): List<Map<String, String>> =
        withTransport { it.execute(command, attributes) }

    private suspend fun <T> withTransport(
        operation: suspend (RouterOsTransport) -> T
    ): T = operation(selectTransport())

    private suspend fun selectTransport(): RouterOsTransport = selectionMutex.withLock {
        delegate?.let { return@withLock it }
        val candidates = candidatesFactory()
        var lastError: Throwable? = null
        for (candidate in candidates) {
            try {
                // Negotiate with a read only. Never replay a possibly applied mutation
                // over another protocol when its response is lost.
                candidate.read("system/identity")
                delegate = candidate
                candidates.filter { it !== candidate }.forEach { runCatching { it.close() } }
                return@withLock candidate
            } catch (cancelled: CancellationException) {
                candidates.forEach { runCatching { it.close() } }
                throw cancelled
            } catch (t: Exception) {
                lastError = t
                runCatching { candidate.close() }
            }
        }

        throw RouterOsException(
            message = "Unable to connect automatically. Check the router password and local network.",
            cause = lastError
        )
    }

    override fun close() {
        delegate?.close()
        delegate = null
    }
}
