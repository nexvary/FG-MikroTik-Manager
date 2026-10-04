package com.fgmachines.mikrotikmanager.data

enum class RouterProtocol {
    AUTO,
    API,
    API_SSL,
    REST_HTTPS,
    REST_HTTP;

    val defaultPort: Int get() = when (this) {
        AUTO, API -> 8728
        API_SSL -> 8729
        REST_HTTPS -> 443
        REST_HTTP -> 80
    }
}

data class RouterConnectionSettings(
    val host: String,
    val port: Int = 8728,
    val username: String,
    val password: String,
    val protocol: RouterProtocol = RouterProtocol.AUTO
) {
    init {
        require(host.isNotBlank()) { "Router host is required" }
        require(port in 1..65535) { "Port must be between 1 and 65535" }
        require(username.isNotBlank()) { "Username is required" }
    }

    fun normalizedHost(): String =
        host.trim()
            .removePrefix("https://")
            .removePrefix("http://")
            .trimEnd('/')

    fun restBaseUrl(): String {
        val scheme = when (protocol) {
            RouterProtocol.REST_HTTP -> "http"
            else -> "https"
        }
        return scheme + "://" + normalizedHost() + ":" + port + "/rest"
    }
}

data class DiscoveredRouter(
    val identity: String,
    val ipAddress: String,
    val macAddress: String = "",
    val boardName: String = "",
    val version: String = "",
    val interfaceName: String = ""
)
