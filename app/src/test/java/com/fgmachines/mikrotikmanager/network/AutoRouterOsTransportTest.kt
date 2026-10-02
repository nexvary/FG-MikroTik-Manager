package com.fgmachines.mikrotikmanager.network

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.yield
import org.junit.Assert.*
import org.junit.Test

class AutoRouterOsTransportTest {
    private class Probe : RouterOsTransport {
        var probes = 0
        var creates = 0
        var closes = 0
        var unavailable = false
        var cancel = false
        override suspend fun read(menu: String): List<Map<String, String>> {
            if (menu == "system/identity") {
                probes++
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
    @Test fun cancellationClosesCandidatesWithoutFallback(): Unit = runBlocking {
        val first = Probe().apply { cancel = true }; val second = Probe()
        val transport = AutoRouterOsTransport { listOf(first, second) }
        try { transport.read("system/resource"); fail("Cancellation must propagate") }
        catch (_: CancellationException) { }
        assertEquals(0, second.probes)
        assertTrue(first.closes > 0); assertTrue(second.closes > 0)
    }
}
