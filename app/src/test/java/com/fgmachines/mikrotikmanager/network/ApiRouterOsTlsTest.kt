package com.fgmachines.mikrotikmanager.network

import com.fgmachines.mikrotikmanager.data.RouterConnectionSettings
import com.fgmachines.mikrotikmanager.data.RouterProtocol
import kotlinx.coroutines.runBlocking
import okhttp3.tls.HandshakeCertificates
import okhttp3.tls.HeldCertificate
import org.junit.Assert.*
import org.junit.Test
import java.net.ServerSocket
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import javax.net.ssl.SSLException
import javax.net.ssl.SSLServerSocket
import javax.net.ssl.SSLSocket

class ApiRouterOsTlsTest {
    @Test fun trustedMatchingCertificateAllowsLoginAndRead() = exercise(accepted = true)
    @Test fun untrustedCertificateRejectsBeforeCredentials() = exercise(trusted = false)
    @Test fun wrongHostnameRejectsBeforeCredentials() = exercise(hostMatches = false)
    @Test fun expiredCertificateRejectsBeforeCredentials() = exercise(expired = true)

    private fun exercise(
        accepted: Boolean = false,
        trusted: Boolean = true,
        hostMatches: Boolean = true,
        expired: Boolean = false
    ): Unit = runBlocking {
        val ca = HeldCertificate.Builder().certificateAuthority(0).commonName("FG MTM test CA").build()
        val now = System.currentTimeMillis()
        val leaf = HeldCertificate.Builder().commonName("FG MTM fixture")
            .addSubjectAlternativeName(if (hostMatches) "127.0.0.1" else "wrong.example")
            .signedBy(ca)
            .validityInterval(now - 120_000, if (expired) now - 60_000 else now + 3_600_000)
            .build()
        val serverCertificates = HandshakeCertificates.Builder().heldCertificate(leaf, ca.certificate).build()
        val clientCertificates = HandshakeCertificates.Builder().apply {
            if (trusted) addTrustedCertificate(ca.certificate) else addPlatformTrustedCertificates()
        }.build()
        val listener = (serverCertificates.sslContext().serverSocketFactory.createServerSocket(0) as SSLServerSocket)
            .apply { soTimeout = 5000 }
        val executor = Executors.newSingleThreadExecutor()
        val transport = ApiRouterOsTransport(
            RouterConnectionSettings("127.0.0.1", listener.localPort, "admin", "test-only", RouterProtocol.API_SSL),
            clientCertificates.sslSocketFactory()
        )
        val server = executor.submit {
            (listener.accept() as SSLSocket).use { socket ->
                socket.soTimeout = 5000
                if (accepted) {
                    socket.startHandshake()
                    assertEquals(listOf("/login", "=name=admin", "=password=test-only"), RouterOsApiCodec.readSentence(socket.inputStream))
                    reply(socket, listOf("!done"))
                    assertEquals(listOf("/system/identity/print"), RouterOsApiCodec.readSentence(socket.inputStream))
                    reply(socket, listOf("!re", "=name=secure-fixture"))
                    reply(socket, listOf("!done"))
                } else {
                    // A failed handshake must yield no application bytes, especially no password.
                    try {
                        socket.startHandshake()
                        assertEquals(-1, socket.inputStream.read())
                    } catch (_: SSLException) { }
                }
            }
        }
        try {
            if (accepted) {
                assertEquals("secure-fixture", transport.read("system/identity").single()["name"])
            } else {
                try { transport.read("system/identity"); fail("Certificate must be rejected") }
                catch (expected: RouterOsException) {
                    assertTrue(expected.message!!.contains("API-SSL"))
                    assertFalse(expected.message!!.contains("test-only"))
                }
            }
            server.get(6, TimeUnit.SECONDS)
        } finally { transport.close(); listener.close(); executor.shutdownNow() }
    }

    @Test fun plaintextServerDoesNotReceiveLoginFromTlsClient(): Unit = runBlocking {
        val listener = ServerSocket(0).apply { soTimeout = 5000 }
        val executor = Executors.newSingleThreadExecutor()
        val server = executor.submit<Int> {
            listener.accept().use { socket ->
                socket.soTimeout = 5000
                socket.inputStream.read() // TLS handshake record, never RouterOS /login.
            }
        }
        val transport = ApiRouterOsTransport(RouterConnectionSettings(
            "127.0.0.1", listener.localPort, "admin", "test-only", RouterProtocol.API_SSL
        ))
        try {
            try { transport.read("system/identity"); fail("TLS cannot connect to plaintext server") }
            catch (_: RouterOsException) { }
            assertEquals(22, server.get(6, TimeUnit.SECONDS).toInt())
        } finally { transport.close(); listener.close(); executor.shutdownNow() }
    }

    private fun reply(socket: SSLSocket, words: List<String>) {
        words.forEach { RouterOsApiCodec.writeWord(socket.outputStream, it) }
        RouterOsApiCodec.writeLength(socket.outputStream, 0)
        socket.outputStream.flush()
    }
}
