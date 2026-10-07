package com.fgmachines.mikrotikmanager.advanced

import com.fgmachines.mikrotikmanager.network.RouterOsTransport
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test

class AdvancedRouterManagerTest {
    @Test fun emptyRouterNeverReportsReady() {
        val report=AdvancedRouterManager(Fake()).evaluate(emptyMap())
        assertFalse(report.ready);assertTrue(report.blockers.any{it.key=="hotspot"});assertEquals(CheckState.UNKNOWN,report.check("internet")?.state)
    }
    @Test fun permissionFailureIsUnknownRatherThanMissing() {
        val report=AdvancedRouterManager(Fake()).evaluate(emptyMap(),setOf("ip/dns"))
        assertEquals(CheckState.UNKNOWN,report.check("dns")?.state)
    }
    @Test fun configuredRouterKeepsItsExistingItems() = runTest {
        val t=Fake(configured());val m=AdvancedRouterManager(t)
        val report=m.preflight();assertTrue(report.ready)
        val plan=m.plan(ClientSetupRequest("ether2","192.168.10.1/24","192.168.10.0/24","192.168.10.10-192.168.10.250","wifi.local"))
        assertTrue(plan.changes.isEmpty());assertFalse(plan.installPortal)
    }
    @Test fun failedBackupAbortsBeforeAnyConfigurationWrite() = runTest {
        val t=Fake(configured().toMutableMap().apply{this["ip/hotspot"]=emptyList()});t.backupFails=true
        val m=AdvancedRouterManager(t);val plan=m.plan(ClientSetupRequest("ether2","192.168.10.1/24","192.168.10.0/24","192.168.10.10-192.168.10.250","wifi.local"))
        try { m.apply(plan,emptyMap(),"abcdefghijklmnop");fail("Expected backup failure") } catch(_: IllegalStateException){}
        assertEquals(listOf("/system/backup/save"),t.calls)
    }
    @Test fun changedConfigurationInvalidatesReviewedPlan() = runTest {
        val t=Fake(configured());val m=AdvancedRouterManager(t)
        val plan=m.plan(ClientSetupRequest("ether2","192.168.10.1/24","192.168.10.0/24","192.168.10.10-192.168.10.250","wifi.local"))
        t.tables["ip/dns"]=listOf(mapOf("servers" to "8.8.8.8"))
        try { m.apply(plan,emptyMap(),"abcdefghijklmnop");fail("Expected stale plan rejection") } catch(_: IllegalArgumentException){}
        assertTrue(t.calls.isEmpty())
    }
    @Test fun refusesTheWanAndPoolsContainingGateway() = runTest {
        val m=AdvancedRouterManager(Fake(configured()))
        try { m.plan(ClientSetupRequest("ether1","10.0.2.15/24","10.0.2.0/24","10.0.2.100-10.0.2.200","wifi.local"));fail("WAN must not become HotSpot") } catch(_: IllegalArgumentException){}
        try { m.plan(ClientSetupRequest("ether2","192.168.10.100/24","192.168.10.0/24","192.168.10.10-192.168.10.250","wifi.local"));fail("Pool includes gateway") } catch(_: IllegalArgumentException){}
    }
    @Test fun pingUsesActualPacketOutcomesAndLatency() {
        val probe=AdvancedRouterManager.pingEvidence(listOf(mapOf("time" to "12ms500us"),mapOf("status" to "timeout"),mapOf("time" to "20ms")))
        assertEquals(3,probe.sent);assertEquals(2,probe.received);assertEquals(33,probe.lossPercent);assertEquals(16.25,probe.latencyMs!!,0.001)
    }
    @Test fun wanDetectionUsesRouteDistanceAndDhcpFallback() {
        val routes = mapOf(
            "interface" to listOf(mapOf("name" to "ether1", "running" to "true"), mapOf("name" to "ether2", "running" to "true")),
            "ip/route" to listOf(
                mapOf("dst-address" to "0.0.0.0/0", "active" to "true", "distance" to "5", "immediate-gw" to "10.0.0.1%ether2"),
                mapOf("dst-address" to "0.0.0.0/0", "active" to "true", "distance" to "1", "routing-table" to "main", "immediate-gw" to "192.168.1.1%ether1")
            )
        )
        assertEquals("ether1", AdvancedRouterManager.detectWan(routes).interfaceName)
        val dhcp = routes.toMutableMap().apply { this["ip/route"] = emptyList(); this["ip/dhcp-client"] = listOf(mapOf("interface" to "ether2", "status" to "bound")) }
        val detected = AdvancedRouterManager.detectWan(dhcp)
        assertEquals("ether2", detected.interfaceName); assertEquals("Bound DHCP client", detected.source)
    }
    @Test fun wanDetectionSupportsPppoeLteAndCurrentFgClientsFixture() {
        val pppoe = mapOf("interface" to listOf(mapOf("name" to "pppoe-out1", "running" to "true"), mapOf("name" to "fg-clients", "running" to "true")), "interface/pppoe-client" to listOf(mapOf("interface" to "pppoe-out1", "running" to "true")))
        assertEquals("pppoe-out1", AdvancedRouterManager.detectWan(pppoe).interfaceName)
        val lte = mapOf("interface" to listOf(mapOf("name" to "lte1", "type" to "lte", "running" to "true"), mapOf("name" to "fg-clients", "type" to "bridge", "running" to "true")))
        assertEquals("lte1", AdvancedRouterManager.detectWan(lte).interfaceName)
        val current = configured().toMutableMap().apply {
            this["interface"] = listOf(mapOf("name" to "ether1", "type" to "ether", "running" to "true"), mapOf("name" to "fg-clients", "type" to "bridge", "running" to "true"))
            this["ip/address"] = listOf(mapOf(".id" to "*I", "interface" to "fg-clients", "address" to "192.168.10.1/24"))
            this["ip/dhcp-server"] = listOf(mapOf(".id" to "*D", "name" to "clients", "interface" to "fg-clients", "address-pool" to "clients"))
            this["ip/hotspot"] = listOf(mapOf("interface" to "fg-clients", "profile" to "clients"))
        }
        val report = AdvancedRouterManager(Fake()).evaluate(current, client = "fg-clients")
        assertEquals("ether1", report.wanInterface); assertEquals("fg-clients", report.clientInterface); assertEquals("Active default route", report.wanSource)
    }
    @Test fun non24SubnetSuggestionPreservesExistingGateway() {
        val t=configured().toMutableMap();t["ip/address"]=listOf(mapOf("interface" to "ether2","address" to "172.16.4.1/16"))
        val m=AdvancedRouterManager(Fake());val request=m.suggestion(m.evaluate(t,client="ether2"))
        assertEquals("172.16.4.1/16",request.gatewayCidr);assertEquals("172.16.0.0/16",request.networkCidr)
    }
    @Test fun runtimeChangesAndUnrelatedFilesDoNotInvalidatePlan() {
        val m=AdvancedRouterManager(Fake())
        val before=configured()
        val after=before.toMutableMap()
        after["interface"]=before.getValue("interface").map { it+("running" to "false") }
        after["ip/route"]=before.getValue("ip/route").map { it+("active" to "false") } + mapOf("dynamic" to "true", "dst-address" to "10.0.0.0/24")
        after["file"]=before.getValue("file") + mapOf("name" to "automatic.backup", ".id" to "*99")
        assertEquals(m.evaluate(before).signature,m.evaluate(after).signature)
    }
    @Test fun portalRemovalStillInvalidatesPlan() {
        val m=AdvancedRouterManager(Fake());val before=configured()
        val after=before.toMutableMap().apply { this["file"]=emptyList() }
        assertNotEquals(m.evaluate(before).signature,m.evaluate(after).signature)
    }
    @Test fun failedFirewallReadDoesNotClaimNoRules() {
        val report=AdvancedRouterManager(Fake()).evaluate(emptyMap(),setOf("ip/firewall/filter"))
        assertEquals(CheckState.UNKNOWN,report.check("firewall")?.state)
        assertTrue(report.check("firewall")!!.messageEn.contains("unknown"))
    }
    @Test fun connectionAndPermissionMessagesAreDistinct() {
        val m=AdvancedRouterManager(Fake())
        val connection=m.evaluate(emptyMap(),setOf("ip/dns"),readErrors=mapOf("ip/dns" to "connection"))
        val permission=m.evaluate(emptyMap(),setOf("ip/dns"),readErrors=mapOf("ip/dns" to "permission"))
        assertTrue(connection.check("dns")!!.messageEn.contains("Connection lost"))
        assertTrue(permission.check("dns")!!.messageEn.contains("denied access"))
    }
    @Test fun workingHotspotCanProvisionWhenPortalFilesAreNotVerified() {
        val tables=configured().toMutableMap();tables["file"]=emptyList()
        val report=AdvancedRouterManager(Fake()).evaluate(tables)
        assertFalse(report.ready);assertTrue(report.voucherReady)
        tables["ip/hotspot"]=emptyList()
        assertFalse(AdvancedRouterManager(Fake()).evaluate(tables).voucherReady)
    }
    private fun configured(): Map<String,List<RouterRow>> = mapOf(
        "interface" to listOf(mapOf("name" to "ether1","running" to "true"),mapOf("name" to "ether2","running" to "true")),
        "ip/route" to listOf(mapOf("dst-address" to "0.0.0.0/0","active" to "true","immediate-gw" to "10.0.2.2%ether1")),
        "ip/address" to listOf(mapOf(".id" to "*I","interface" to "ether2","address" to "192.168.10.1/24")),
        "ip/dns" to listOf(mapOf("servers" to "1.1.1.1")),
        "ip/dhcp-server" to listOf(mapOf(".id" to "*D","name" to "clients","interface" to "ether2","address-pool" to "clients")),
        "ip/dhcp-server/network" to listOf(mapOf("address" to "192.168.10.0/24","gateway" to "192.168.10.1","dns-server" to "192.168.10.1")),
        "ip/pool" to listOf(mapOf("name" to "clients","ranges" to "192.168.10.10-192.168.10.250")),
        "ip/firewall/nat" to listOf(mapOf("chain" to "srcnat","action" to "masquerade")),
        "ip/hotspot" to listOf(mapOf("interface" to "ether2","profile" to "clients")),
        "ip/hotspot/profile" to listOf(mapOf("name" to "clients","html-directory" to "hotspot","login-by" to "http-chap")),
        "ip/hotspot/user/profile" to listOf(mapOf("name" to "default")),
        "ip/service" to listOf(mapOf("name" to "api")),
        "file" to listOf(mapOf("name" to "hotspot/login.html"),mapOf("name" to "hotspot/status.html")))
    private class Fake(initial:Map<String,List<RouterRow>> = emptyMap()):RouterOsTransport {
        val tables=initial.toMutableMap();val calls=mutableListOf<String>();var backupFails=false
        override suspend fun read(menu:String)=tables[menu].orEmpty()
        override suspend fun create(menu:String,attributes:RouterRow):List<RouterRow>{calls+="/$menu/add";return listOf(attributes+(".id" to "*X"))}
        override suspend fun execute(command:String,attributes:RouterRow):List<RouterRow>{calls+=command;if(command=="/system/backup/save"&&backupFails)error("Backup failed");return emptyList()}
        override fun close(){}
    }
}
