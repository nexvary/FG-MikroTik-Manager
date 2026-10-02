package com.fgmachines.mikrotikmanager.network

import com.fgmachines.mikrotikmanager.data.RouterConnectionSettings
import com.fgmachines.mikrotikmanager.data.RouterProtocol

class AutoRouterOsTransport(
    private val settings: RouterConnectionSettings
) : RouterOsTransport {

    private var delegate: RouterOsTransport? = null

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
    ): T {
        delegate?.let { return operation(it) }

        val candidates = listOf(
            ApiRouterOsTransport(
                settings.copy(
                    port = 8728,
                    protocol = RouterProtocol.API
                )
            ),
            RestRouterOsTransport(
                settings.copy(
                    port = 443,
                    protocol = RouterProtocol.REST_HTTPS
                )
            )
        )

        var lastError: Throwable? = null
        for (candidate in candidates) {
            try {
                val result = operation(candidate)
                delegate = candidate
                candidates.filter { it !== candidate }.forEach { runCatching { it.close() } }
                return result
            } catch (t: Throwable) {
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
