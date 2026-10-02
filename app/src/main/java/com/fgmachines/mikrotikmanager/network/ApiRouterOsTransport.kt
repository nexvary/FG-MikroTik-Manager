package com.fgmachines.mikrotikmanager.network

import com.fgmachines.mikrotikmanager.data.RouterConnectionSettings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.EOFException
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.net.InetSocketAddress
import java.net.Socket

class ApiRouterOsTransport(
    private val settings: RouterConnectionSettings
) : RouterOsTransport {

    private val mutex = Mutex()
    private var socket: Socket? = null
    private var input: BufferedInputStream? = null
    private var output: BufferedOutputStream? = null
    private var loggedIn = false
    @Volatile private var disposed = false

    override suspend fun read(menu: String): List<Map<String, String>> =
        command("/" + clean(menu) + "/print")

    override suspend fun create(
        menu: String,
        attributes: Map<String, String>
    ): List<Map<String, String>> =
        command("/" + clean(menu) + "/add", attributes)

    override suspend fun execute(
        command: String,
        attributes: Map<String, String>
    ): List<Map<String, String>> {
        val normalized = if (command.startsWith("/")) command else "/" + clean(command)
        return command(normalized, attributes)
    }

    private suspend fun command(
        command: String,
        attributes: Map<String, String> = emptyMap()
    ): List<Map<String, String>> =
        withContext(Dispatchers.IO) {
            mutex.withLock {
                val words = buildList {
                    add(command)
                    attributes.forEach { (key, value) ->
                        add("=" + key + "=" + value)
                    }
                }
                // Only reads may be replayed: a lost response to an add/set/remove
                // cannot prove that RouterOS did not already apply the mutation.
                val attempts = if (command.endsWith("/print")) 2 else 1
                var result: List<Map<String, String>>? = null
                for (attempt in 0 until attempts) {
                    ensureConnected()
                    try {
                        writeSentence(words)
                        result = readReply()
                        break
                    } catch (failure: IOException) {
                        closeInternal()
                        if (attempt == attempts - 1) throw RouterOsException(
                            "RouterOS API connection lost; reconnect and verify the result before retrying changes",
                            cause = failure
                        )
                    }
                }
                result ?: throw RouterOsException("RouterOS API returned no reply")
            }
        }

    private fun ensureConnected() {
        if (disposed) throw RouterOsException("RouterOS API session was closed")
        if (socket?.isConnected == true && socket?.isClosed == false && loggedIn) {
            return
        }

        closeInternal()
        val newSocket = Socket()
        try {
            socket = newSocket
            if (disposed) throw RouterOsException("RouterOS API session was closed")
            newSocket.tcpNoDelay = true
            newSocket.keepAlive = true
            newSocket.soTimeout = 20_000
            newSocket.connect(
                InetSocketAddress(settings.normalizedHost(), settings.port),
                8_000
            )
            if (disposed) throw RouterOsException("RouterOS API session was closed")
            input = BufferedInputStream(newSocket.getInputStream())
            output = BufferedOutputStream(newSocket.getOutputStream())
            login()
            loggedIn = true
        } catch (t: Throwable) {
            runCatching { newSocket.close() }
            closeInternal()
            throw RouterOsException(
                "Unable to connect to RouterOS API at " +
                    settings.normalizedHost() + ":" + settings.port,
                cause = t
            )
        }
    }

    private fun login() {
        writeSentence(
            listOf(
                "/login",
                "=name=" + settings.username,
                "=password=" + settings.password
            )
        )
        val reply = readReply()
        if (reply.isNotEmpty()) {
            // Successful /login normally returns !done without rows.
        }
    }

    private fun writeSentence(words: List<String>) {
        val stream = output ?: throw RouterOsException("RouterOS API output is unavailable")
        words.forEach { RouterOsApiCodec.writeWord(stream, it) }
        RouterOsApiCodec.writeLength(stream, 0)
        stream.flush()
    }

    private fun readReply(): List<Map<String, String>> {
        val stream = input ?: throw RouterOsException("RouterOS API input is unavailable")
        val rows = mutableListOf<Map<String, String>>()
        var trapMessage: String? = null

        while (true) {
            val sentence = RouterOsApiCodec.readSentence(stream)
            if (sentence.isEmpty()) continue

            val kind = sentence.first()
            val attributes = RouterOsApiCodec.attributes(sentence.drop(1))

            when (kind) {
                "!re" -> rows += attributes
                "!empty" -> Unit
                "!trap" -> {
                    trapMessage = attributes["message"]
                        ?: attributes["category"]?.let { "RouterOS API error category " + it }
                        ?: "RouterOS API rejected the request"
                }
                "!fatal" -> {
                    val message = attributes["message"] ?: "RouterOS API connection closed"
                    closeInternal()
                    throw RouterOsException(message)
                }
                "!done" -> {
                    trapMessage?.let { throw RouterOsException(it) }
                    if (rows.isEmpty()) {
                        val createdId = attributes["ret"]
                        if (!createdId.isNullOrBlank()) {
                            return listOf(mapOf(".id" to createdId))
                        }
                    }
                    return rows
                }
            }
        }
    }

    private fun clean(menu: String): String =
        menu.trim().trim('/').also {
            require(it.isNotBlank()) { "RouterOS menu cannot be blank" }
        }

    override fun close() {
        disposed = true
        closeInternal()
    }

    private fun closeInternal() {
        loggedIn = false
        // Closing the socket first interrupts a blocked buffered read immediately.
        runCatching { socket?.close() }
        runCatching { input?.close() }
        runCatching { output?.close() }
        input = null
        output = null
        socket = null
    }
}

internal object RouterOsApiCodec {
    private const val MAX_WORD_LENGTH = 64 * 1024 * 1024

    fun writeWord(output: OutputStream, word: String) {
        val bytes = word.toByteArray(Charsets.UTF_8)
        writeLength(output, bytes.size)
        output.write(bytes)
    }

    fun writeLength(output: OutputStream, length: Int) {
        require(length >= 0)
        when {
            length < 0x80 -> output.write(length)
            length < 0x4000 -> {
                val value = length or 0x8000
                output.write((value ushr 8) and 0xFF)
                output.write(value and 0xFF)
            }
            length < 0x200000 -> {
                val value = length or 0xC00000
                output.write((value ushr 16) and 0xFF)
                output.write((value ushr 8) and 0xFF)
                output.write(value and 0xFF)
            }
            length < 0x10000000 -> {
                val value = length or 0xE0000000.toInt()
                output.write((value ushr 24) and 0xFF)
                output.write((value ushr 16) and 0xFF)
                output.write((value ushr 8) and 0xFF)
                output.write(value and 0xFF)
            }
            else -> {
                output.write(0xF0)
                output.write((length ushr 24) and 0xFF)
                output.write((length ushr 16) and 0xFF)
                output.write((length ushr 8) and 0xFF)
                output.write(length and 0xFF)
            }
        }
    }

    fun readSentence(input: InputStream): List<String> {
        val words = mutableListOf<String>()
        while (true) {
            val length = readLength(input)
            if (length == 0) return words
            require(length in 1..MAX_WORD_LENGTH) { "Invalid RouterOS API word length" }
            val bytes = ByteArray(length)
            readFully(input, bytes)
            words += bytes.toString(Charsets.UTF_8)
        }
    }

    fun readLength(input: InputStream): Int {
        val first = input.read()
        if (first < 0) throw EOFException("RouterOS API connection closed")

        return when {
            (first and 0x80) == 0 -> first
            (first and 0xC0) == 0x80 ->
                ((first and 0x3F) shl 8) or readByte(input)
            (first and 0xE0) == 0xC0 ->
                ((first and 0x1F) shl 16) or
                    (readByte(input) shl 8) or
                    readByte(input)
            (first and 0xF0) == 0xE0 ->
                ((first and 0x0F) shl 24) or
                    (readByte(input) shl 16) or
                    (readByte(input) shl 8) or
                    readByte(input)
            first == 0xF0 ->
                (readByte(input) shl 24) or
                    (readByte(input) shl 16) or
                    (readByte(input) shl 8) or
                    readByte(input)
            else -> throw RouterOsException(
                "Unsupported RouterOS API control byte 0x" + first.toString(16)
            )
        }
    }

    fun attributes(words: List<String>): Map<String, String> =
        buildMap {
            words.forEach { word ->
                when {
                    word.startsWith("=") -> {
                        val split = word.indexOf('=', startIndex = 1)
                        if (split > 1) {
                            put(word.substring(1, split), word.substring(split + 1))
                        }
                    }
                    word.startsWith(".tag=") -> put(".tag", word.substringAfter(".tag="))
                }
            }
        }

    private fun readByte(input: InputStream): Int {
        val value = input.read()
        if (value < 0) throw EOFException("RouterOS API connection closed")
        return value
    }

    private fun readFully(input: InputStream, target: ByteArray) {
        var offset = 0
        while (offset < target.size) {
            val count = input.read(target, offset, target.size - offset)
            if (count < 0) throw EOFException("RouterOS API connection closed")
            offset += count
        }
    }
}
