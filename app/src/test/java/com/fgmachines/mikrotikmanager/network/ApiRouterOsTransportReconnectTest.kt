package com.fgmachines.mikrotikmanager.network

import com.fgmachines.mikrotikmanager.data.RouterConnectionSettings
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import java.net.ServerSocket
import java.net.Socket
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

class ApiRouterOsTransportReconnectTest {
    @Test fun readReconnectsAfterPeerClosesWithoutAReply() = exercise(false)
    @Test fun lostMutationReplyIsNotReplayedAndNextReadReconnects() = exercise(true)

    private fun exercise(mutation: Boolean): Unit = runBlocking {
        val listener = ServerSocket(0).apply { soTimeout = 5000 }
        val executor = Executors.newSingleThreadExecutor()
        val transport = ApiRouterOsTransport(RouterConnectionSettings("127.0.0.1", listener.localPort, "admin", "test-only"))
        val server = executor.submit {
            listener.accept().use { socket ->
                login(socket)
                val command = RouterOsApiCodec.readSentence(socket.getInputStream()).first()
                assertEquals(if (mutation) "/ip/hotspot/user/add" else "/system/resource/print", command)
                // Drop the reply. A mutation may already have been applied.
            }
            listener.accept().use { socket ->
                login(socket)
                assertEquals("/system/resource/print", RouterOsApiCodec.readSentence(socket.getInputStream()).first())
                reply(socket, listOf("!re", "=version=7.16.2"))
                reply(socket, listOf("!done"))
            }
        }
        try {
            if (mutation) {
                try { transport.create("ip/hotspot/user", mapOf("name" to "voucher")); fail("Mutation must not be replayed") }
                catch (expected: RouterOsException) { assertTrue(expected.message!!.contains("verify the result")) }
            }
            assertEquals("7.16.2", transport.read("system/resource").single()["version"])
            server.get(6, TimeUnit.SECONDS)
            Unit
        } finally {
            transport.close(); listener.close(); executor.shutdownNow()
        }
    }
    private fun login(socket: Socket) {
        socket.soTimeout = 5000
        assertEquals("/login", RouterOsApiCodec.readSentence(socket.getInputStream()).first())
        reply(socket, listOf("!done"))
    }
    private fun reply(socket: Socket, words: List<String>) {
        val stream = socket.getOutputStream()
        words.forEach { RouterOsApiCodec.writeWord(stream, it) }
        RouterOsApiCodec.writeLength(stream, 0); stream.flush()
    }
}
