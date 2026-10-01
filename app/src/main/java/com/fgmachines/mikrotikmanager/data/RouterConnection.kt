package com.fgmachines.mikrotikmanager.data

enum class RouterProtocol {
    REST_HTTPS,
    API_SSL,
    API
}

data class RouterConnectionSettings(
    val host: String,
    val port: Int = 443,
    val username: String,
    val password: String,
    val protocol: RouterProtocol = RouterProtocol.REST_HTTPS
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

    fun restBaseUrl(): String =
        "https://" + normalizedHost() + ":" + port + "/rest"
}
