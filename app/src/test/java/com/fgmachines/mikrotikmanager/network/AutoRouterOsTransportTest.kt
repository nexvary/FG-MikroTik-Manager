package com.fgmachines.mikrotikmanager.network

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.yield
import org.junit.Assert.*
import org.junit.Test

class AutoRouterOsTransportTest {
    @Test fun automaticCandidatesIncludeApiSslBeforeRestFallback() {
        val base = com.fgmachines.mikrotikmanager.data.RouterConnectionSettings("192.168.88.1", 9999, "admin", "pw")
        val candidates = AutoRouterOsTransport.automaticCandidateSettings(base)
        assertEquals(listOf(
            com.fgmachines.mikrotikmanager.data.RouterProtocol.API,
            com.fgmachines.mikrotikmanager.data.RouterProtocol.API_SSL,
            com.fgmachines.mikrotikmanager.data.RouterProtocol.REST_HTTPS
        ), candidates.map { it.protocol })
        assertEquals(listOf(8728, 8729, 443), candidates.map { it.port })
    }

    private class Probe : RouterOsTransport {
        var probes = 0
        var creates = 0
        var closes = 0
        var unavailable = false
        var cancel = false
        var onProbe: (suspend () -> Unit)? = null
        override suspend fun read(menu: String): List<Map<String, String>> {
            if (menu == "system/identity") {
                probes++
                onProbe?.invoke()
                yield()
                if (cancel) throw CancellationException("Cancelled")
                if (unavailable) throw RouterOsException("Unavailable")
            }
            return listOf(mapOf("name" to "router"))
        }
        override suspend fun create(menu: String, attributes: Map<String, String>): List<Map<String, String>> {
            creates++
            throw RouterOsException("Reply lost after mutation")
        }
        override suspend fun execute(command: String, attributes: Map<String, String>) = create(command, attributes)
        override fun close() { closes++ }
    }
    @Test fun uncertainMutationIsNeverSentOverAnotherProtocol(): Unit = runBlocking {
        val first = Probe(); val second = Probe()
        val transport = AutoRouterOsTransport { listOf(first, second) }
        try { transport.create("ip/hotspot/user", mapOf("name" to "test")); fail("Must report uncertainty") }
        catch (expected: RouterOsException) { assertEquals("Reply lost after mutation", expected.message) }
        assertEquals(1, first.probes)
        assertEquals(1, first.creates)
        assertEquals(0, second.probes)
        assertEquals(0, second.creates)
        assertEquals("router", transport.read("system/resource").single()["name"])
        assertEquals(1, first.probes)
        transport.close()
    }
    @Test fun negotiationFallsBackOnlyDuringReadProbe(): Unit = runBlocking {
        val first = Probe().apply { unavailable = true }; val second = Probe()
        val transport = AutoRouterOsTransport { listOf(first, second) }
        assertEquals("router", transport.read("system/resource").single()["name"])
        assertEquals(1, first.probes); assertEquals(1, second.probes)
        assertEquals(0, first.creates); assertEquals(0, second.creates)
        transport.close()
    }
    @Test fun concurrentRequestsShareOneNegotiatedSession(): Unit = runBlocking {
        val first = Probe(); val second = Probe()
        val transport = AutoRouterOsTransport { listOf(first, second) }
        List(8) { async { transport.read("system/resource") } }.awaitAll()
        assertEquals(1, first.probes); assertEquals(0, second.probes)
        transport.close()
    }
    @Test fun closeDuringNegotiationCannotPublishALateSession(): Unit = runBlocking {
        val started = CompletableDeferred<Unit>(); val resume = CompletableDeferred<Unit>()
        val first = Probe().apply { onProbe = { started.complete(Unit); resume.await() } }
        val second = Probe()
        val transport = AutoRouterOsTransport { listOf(first, second) }
        val result = async { runCatching { transport.read("system/resource") } }
        started.await(); transport.close(); resume.complete(Unit)
        assertTrue(result.await().exceptionOrNull()?.message.orEmpty().contains("closed"))
        assertEquals(0, second.probes)
        assertTrue(first.closes > 0); assertTrue(second.closes > 0)
    }
    @Test fun closedAutomaticSessionDoesNotNegotiateAgain(): Unit = runBlocking {
        val candidate = Probe()
        val transport = AutoRouterOsTransport { listOf(candidate) }
        transport.read("system/resource")
        transport.close()
        try { transport.read("system/resource"); fail("Closed session must not reopen") }
        catch (expected: RouterOsException) { assertTrue(expected.message!!.contains("closed")) }
        assertEquals(1, candidate.probes)
        assertEquals(1, candidate.closes)
    }
    @Test fun cancellationClosesCandidatesWithoutFallback(): Unit = runBlocking {
        val first = Probe().apply { cancel = true }; val second = Probe()
        val transport = AutoRouterOsTransport { listOf(first, second) }
        try { transport.read("system/resource"); fail("Cancellation must propagate") }
        catch (_: CancellationException) { }
        assertEquals(0, second.probes)
        assertTrue(first.closes > 0); assertTrue(second.closes > 0)
    }
}
