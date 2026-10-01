package com.fgmachines.mikrotikmanager.network

interface RouterOsTransport : AutoCloseable {
    suspend fun read(menu: String): List<Map<String, String>>

    suspend fun execute(
        command: String,
        attributes: Map<String, String> = emptyMap()
    ): List<Map<String, String>>

    override fun close()
}

class RouterOsException(
    message: String,
    val statusCode: Int? = null,
    cause: Throwable? = null
) : Exception(message, cause)
