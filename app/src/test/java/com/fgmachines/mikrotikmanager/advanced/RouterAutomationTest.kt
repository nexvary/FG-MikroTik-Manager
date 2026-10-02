package com.fgmachines.mikrotikmanager.advanced

import com.fgmachines.mikrotikmanager.network.RouterOsTransport
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test
import java.time.Instant
import java.time.ZoneId

class RouterAutomationTest {
    @Test fun parsesOldAndNewClockDates() {
        val iso = mapOf("date" to "2026-10-02", "time" to "11:20:00", "gmt-offset" to "+03:00")
        val old = iso + ("date" to "oct/02/2026")
        assertEquals(Instant.parse("2026-10-02T08:20:00Z"), RouterAutomation.clockInstant(iso))
        assertEquals(RouterAutomation.clockInstant(iso), RouterAutomation.clockInstant(old))
    }
    @Test fun clockSetsDateAndTimeSeparatelyAndPreservesOwnerServers() = runTest {
        val fake = ClockFake(); fake.servers += mapOf("address" to "owner.time.local")
        assertFalse(RouterAutomation(fake).synchronizeClock(Instant.parse("2026-10-02T08:20:00Z"), ZoneId.of("Africa/Cairo")))
        assertEquals("2026-10-02", fake.clock["date"])
        assertEquals("11:20:00", fake.clock["time"])
        assertTrue(fake.writes.none { it.second.containsKey("date") && it.second.containsKey("time") })
        assertEquals(listOf("owner.time.local"), fake.servers.map { it["address"] })
        assertEquals("yes", fake.ntp["enabled"])
    }
    @Test fun clockAddsTimeServersOnlyWhenNoneExist() = runTest {
        val fake = ClockFake(); val automation = RouterAutomation(fake)
        automation.synchronizeClock(Instant.parse("2026-10-02T08:20:00Z"), ZoneId.of("UTC"))
        automation.synchronizeClock(Instant.parse("2026-10-02T08:20:00Z"), ZoneId.of("UTC"))
        assertEquals(2, fake.servers.size)
    }
    @Test fun clockRejectsUnverifiedWrite() = runTest {
        val fake = ClockFake(); fake.ignoreClock = true
        try { RouterAutomation(fake).synchronizeClock(Instant.parse("2026-10-02T08:20:00Z"), ZoneId.of("UTC")); fail("Must verify clock") } catch (_: IllegalArgumentException) {}
    }
    @Test fun clientPlanExcludesWanOccupiedAndConnectedSparePorts() {
        val report = report()
        val plan = RouterAutomation.planPorts(report, "192.168.1.104")
        assertEquals(listOf("ether2", "ether3"), plan.ports)
        assertTrue(plan.changes.any { it.menu == "ip/address" && it.attributes["interface"] == "fg-clients" && it.undo?.get("interface") == "ether2" })
        assertTrue(plan.changes.none { it.attributes["interface"] == "ether1" })
    }
    @Test fun refusesCurrentManagementInterfaceAndInterfaceFirewallRules() {
        try { RouterAutomation.planPorts(report(), "192.168.10.1"); fail("Must preserve current management") } catch (_: IllegalArgumentException) {}
        val tables = report().tables.toMutableMap(); tables["ip/firewall/filter"] = listOf(mapOf("in-interface" to "ether2"))
        try { RouterAutomation.planPorts(AdvancedRouterManager(ClockFake()).evaluate(tables), "192.168.1.104"); fail("Cannot silently rewrite firewall") } catch (_: IllegalArgumentException) {}
    }
    @Test fun existingBridgeNeverMigratesWorkingServices() {
        val tables = report().tables.toMutableMap()
        tables["interface"] = tables.getValue("interface") + mapOf("name" to "clients", "type" to "bridge")
        tables["interface/bridge"] = listOf(mapOf("name" to "clients"))
        tables["ip/hotspot"] = listOf(mapOf(".id" to "*H", "interface" to "clients"))
        tables["interface/bridge/port"] = listOf(mapOf("interface" to "ether2", "bridge" to "clients"))
        val plan = RouterAutomation.planPorts(AdvancedRouterManager(ClockFake()).evaluate(tables), "192.168.1.104")
        assertEquals("clients", plan.bridge); assertTrue(plan.changes.all { it.menu == "interface/bridge/port" })
        assertFalse("ether2" in plan.ports)
    }
    @Test fun groupingPreservesAddressesAndCanRunAgainWithoutDuplicates() = runTest {
        val fake = PortsFake(report().tables); val manager = AdvancedRouterManager(fake, "192.168.1.104")
        val plan = manager.planClientPorts(); manager.applyClientPorts(plan, "abcdefghijklmnop")
        assertEquals("192.168.10.1/24", fake.tables.getValue("ip/address").first()["address"])
        assertEquals("fg-clients", fake.tables.getValue("ip/address").first()["interface"])
        assertTrue(manager.planClientPorts().changes.isEmpty())
        assertTrue(fake.tables.getValue("interface/bridge/port").none { it["interface"] == "ether1" })
    }
    @Test fun failedPortGroupingRestoresServicesToTheirOriginalInterface() = runTest {
        val fake = PortsFake(report().tables); fake.failPorts = true
        val manager = AdvancedRouterManager(fake, "192.168.1.104"); val plan = manager.planClientPorts()
        try { manager.applyClientPorts(plan, "abcdefghijklmnop"); fail("Must reject failed port addition") } catch (_: IllegalStateException) {}
        assertEquals("ether2", fake.tables.getValue("ip/address").first()["interface"])
        assertEquals("ether2", fake.tables.getValue("ip/hotspot").first()["interface"])
        assertTrue(fake.tables["interface/bridge"].isNullOrEmpty())
    }
    private class PortsFake(initial: Map<String,List<RouterRow>>) : RouterOsTransport {
        val tables = initial.toMutableMap(); var serial = 10; var failPorts = false
        override suspend fun read(menu: String) = tables[menu].orEmpty()
        override suspend fun create(menu: String, attributes: RouterRow): List<RouterRow> {
            if(menu == "interface/bridge/port" && failPorts) error("Port unavailable")
            val row = attributes + (".id" to "*${serial++}")
            tables[menu] = tables[menu].orEmpty() + row
            if(menu == "interface/bridge") tables["interface"] = tables["interface"].orEmpty() + row
            return listOf(row)
        }
        override suspend fun execute(command: String, attributes: RouterRow): List<RouterRow> {
            if(command == "/system/backup/save") tables["file"] = tables["file"].orEmpty() + mapOf("name" to attributes.getValue("name") + ".backup")
            val menu = command.removePrefix("/").substringBeforeLast('/')
            if(command.endsWith("/set")) tables[menu] = tables[menu].orEmpty().map { if(it[".id"] == attributes[".id"]) it + attributes else it }
            if(command.endsWith("/remove")) {
                tables[menu] = tables[menu].orEmpty().filter { it[".id"] != attributes[".id"] }
                if(menu == "interface/bridge") tables["interface"] = tables["interface"].orEmpty().filter { it[".id"] != attributes[".id"] }
            }
            return emptyList()
        }
        override fun close() {}
    }
    private fun report(): ReadinessReport {
        val tables = mapOf(
            "interface" to (1..5).map { mapOf("name" to "ether$it", "type" to "ether", "running" to if(it in listOf(1,2,5)) "true" else "false") },
            "ip/route" to listOf(mapOf("dst-address" to "0.0.0.0/0", "active" to "true", "immediate-gw" to "192.168.1.1%ether1")),
            "ip/address" to listOf(mapOf(".id" to "*I", "address" to "192.168.10.1/24", "interface" to "ether2"), mapOf(".id" to "*J", "address" to "172.16.1.1/24", "interface" to "ether4")),
            "ip/hotspot" to listOf(mapOf(".id" to "*H", "interface" to "ether2")),
            "ip/dhcp-server" to listOf(mapOf(".id" to "*D", "interface" to "ether2"))
        )
        return AdvancedRouterManager(ClockFake()).evaluate(tables)
    }
    private class ClockFake : RouterOsTransport {
        var ignoreClock = false
        val clock = mutableMapOf("date" to "1970-01-02", "time" to "00:00:00", "time-zone-name" to "UTC")
        val ntp = mutableMapOf("enabled" to "no", "status" to "waiting")
        val servers = mutableListOf<RouterRow>()
        val writes = mutableListOf<Pair<String, RouterRow>>()
        override suspend fun read(menu: String) = when(menu) { "system/clock" -> listOf(clock.toMap()); "system/ntp/client" -> listOf(ntp.toMap()); "system/ntp/client/servers" -> servers.toList(); else -> emptyList() }
        override suspend fun execute(command: String, attributes: RouterRow): List<RouterRow> {
            writes += command to attributes
            if(command == "/system/clock/set" && !ignoreClock) clock.putAll(attributes)
            if(command == "/system/ntp/client/set") ntp.putAll(attributes)
            return emptyList()
        }
        override suspend fun create(menu: String, attributes: RouterRow): List<RouterRow> { servers += attributes; return listOf(attributes + (".id" to "*S")) }
        override fun close() {}
    }
}
