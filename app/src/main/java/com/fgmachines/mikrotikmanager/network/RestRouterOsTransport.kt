package com.fgmachines.mikrotikmanager.network

import com.fgmachines.mikrotikmanager.data.RouterConnectionSettings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import okhttp3.Credentials
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException
import java.util.concurrent.TimeUnit

class RestRouterOsTransport(
    private val settings: RouterConnectionSettings
) : RouterOsTransport {

    private val json = Json { ignoreUnknownKeys = true }

    private val client = OkHttpClient.Builder()
        .connectTimeout(8, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .writeTimeout(15, TimeUnit.SECONDS)
        .callTimeout(20, TimeUnit.SECONDS)
        .build()

    private val authorization =
        Credentials.basic(settings.username, settings.password, Charsets.UTF_8)

    override suspend fun read(menu: String): List<Map<String, String>> =
        request(
            Request.Builder()
                .url(urlFor(menu))
                .header("Authorization", authorization)
                .header("Accept", "application/json")
                .get()
                .build()
        )

    override suspend fun create(
        menu: String,
        attributes: Map<String, String>
    ): List<Map<String, String>> {
        val payload = jsonPayload(attributes)

        return request(
            Request.Builder()
                .url(urlFor(menu))
                .header("Authorization", authorization)
                .header("Accept", "application/json")
                .put(payload.toRequestBody(JSON_MEDIA_TYPE))
                .build()
        )
    }

    override suspend fun execute(
        command: String,
        attributes: Map<String, String>
    ): List<Map<String, String>> {
        val payload = jsonPayload(attributes)

        return request(
            Request.Builder()
                .url(urlFor(command))
                .header("Authorization", authorization)
                .header("Accept", "application/json")
                .post(payload.toRequestBody(JSON_MEDIA_TYPE))
                .build()
        )
    }

    private fun jsonPayload(attributes: Map<String, String>): String =
        buildJsonObject {
            attributes.forEach { (key, value) -> put(key, value) }
        }.toString()

    private suspend fun request(request: Request): List<Map<String, String>> =
        withContext(Dispatchers.IO) {
            try {
                client.newCall(request).execute().use { response ->
                    val body = response.body?.string().orEmpty()

                    if (!response.isSuccessful) {
                        val detail = body.take(240).ifBlank { response.message }
                        throw RouterOsException(
                            message = "RouterOS returned HTTP " + response.code + ": " + detail,
                            statusCode = response.code
                        )
                    }

                    parseBody(body)
                }
            } catch (e: RouterOsException) {
                throw e
            } catch (e: IOException) {
                throw RouterOsException(
                    message = "Unable to reach RouterOS at " +
                        settings.normalizedHost() + ":" + settings.port,
                    cause = e
                )
            }
        }

    private fun parseBody(body: String): List<Map<String, String>> {
        if (body.isBlank()) return emptyList()

        return when (val element = json.parseToJsonElement(body)) {
            is JsonArray -> element.mapNotNull { (it as? JsonObject)?.toStringMap() }
            is JsonObject -> listOf(element.toStringMap())
            else -> emptyList()
        }
    }

    private fun JsonObject.toStringMap(): Map<String, String> =
        entries.associate { (key, value) -> key to value.asText() }

    private fun JsonElement.asText(): String =
        when (this) {
            is JsonPrimitive -> content
            else -> toString()
        }

    private fun urlFor(menu: String): String {
        val clean = menu.trim().trim('/')
        require(clean.isNotBlank()) { "RouterOS menu cannot be blank" }
        return settings.restBaseUrl() + "/" + clean
    }

    override fun close() {
        client.dispatcher.executorService.shutdown()
        client.connectionPool.evictAll()
        client.cache?.close()
    }

    private companion object {
        val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()
    }
}
