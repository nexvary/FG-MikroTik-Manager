package com.fgmachines.mikrotikmanager.data

import com.fgmachines.mikrotikmanager.network.RouterOsTransport
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test

class NetworkInventoryTest {
    @Test fun mergesThreeSourcesAndKeepsConflictingMacsSeparate() {
        val rows = NetworkInventory.merge(listOf(
            "ip/neighbor" to mapOf("identity" to "AP", "mac-address" to "aa-bb-cc-dd-ee-01", "address" to "10.0.0.2"),
            "ip/dhcp-server/lease" to mapOf("mac-address" to "AA:BB:CC:DD:EE:01", "address" to "10.0.0.2"),
            "ip/arp" to mapOf("mac-address" to "AA:BB:CC:DD:EE:01", "address" to "10.0.0.3"),
            "ip/arp" to mapOf("mac-address" to "AA:BB:CC:DD:EE:02", "address" to "10.0.0.2")
        ))
        assertEquals(2, rows.size)
        assertEquals("AP", rows[0]["name"])
        assertEquals("Neighbor • DHCP • ARP", rows[0]["sources"])
        assertEquals("10.0.0.2 • 10.0.0.3", rows[0]["address"])
        assertNotEquals(rows[0][".id"], rows[1][".id"])
    }

    @Test fun partialFailureKeepsAvailableResultsAndNeverWrites() = runTest {
        val transport = Fake { menu ->
            if (menu == "ip/neighbor") error("permission denied")
            listOf(mapOf("mac-address" to "AA:BB:CC:DD:EE:01", "address" to "10.0.0.2"))
        }
        val result = NetworkInventory.load(transport)
        assertEquals(listOf("ip/neighbor"), result.warnings)
        assertEquals(1, result.rows.size)
        assertEquals(3, transport.reads)
    }

    @Test fun missingMacDoesNotMergeOnReusedIp() {
        val rows = NetworkInventory.merge(listOf(
            "ip/arp" to mapOf("address" to "10.0.0.2"),
            "ip/neighbor" to mapOf("address" to "10.0.0.2")
        ))
        assertEquals(2, rows.size)
    }

    @Test fun allFailuresAreNotReportedAsEmptySuccess() = runTest {
        try {
            NetworkInventory.load(Fake { error("offline") })
            fail("Expected scan failure")
        } catch (expected: IllegalStateException) {
            assertTrue(expected.message!!.contains("Network scan unavailable"))
        }
    }

    @Test fun cancellationStopsRemainingReads() = runTest {
        val transport = Fake { throw CancellationException("stop") }
        try {
            NetworkInventory.load(transport)
            fail("Cancellation must propagate")
        } catch (_: CancellationException) { assertEquals(1, transport.reads) }
    }

    @Test fun inventoriesNeverLeakBetweenRouterSessions() = runTest {
        val first = NetworkInventory.load(Fake { listOf(mapOf("host-name" to "first")) })
        val second = NetworkInventory.load(Fake { emptyList() })
        assertEquals(3, first.rows.size)
        assertTrue(second.rows.isEmpty())
    }

    private class Fake(val reader: (String) -> List<Map<String, String>>) : RouterOsTransport {
        var reads = 0
        override suspend fun read(menu: String): List<Map<String, String>> { reads++; return reader(menu) }
        override suspend fun create(menu: String, attributes: Map<String, String>): List<Map<String, String>> = error("Inventory must not write")
        override suspend fun execute(command: String, attributes: Map<String, String>): List<Map<String, String>> = error("Inventory must not execute")
        override fun close() = Unit
    }
}
