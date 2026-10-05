package com.fgmachines.mikrotikmanager.advanced

import com.fgmachines.mikrotikmanager.network.RouterOsTransport
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test

class DnsProtectionManagerTest {
    private class Fake:RouterOsTransport {
        val tables=mutableMapOf(
            "interface" to listOf(mapOf("type" to "ether","mac-address" to "00:11:22:33:44:55")),
            "ip/dns" to listOf(mapOf("servers" to "9.9.9.9","use-doh-server" to "https://old.example/dns-query","allow-remote-requests" to "no","dynamic-servers" to "")),
            "ip/dhcp-server/network" to listOf(mapOf(".id" to "*1","address" to "192.168.10.0/24","dns-server" to "192.168.10.1","dns-none" to "yes"),mapOf(".id" to "*2","address" to "192.168.20.0/24","dns-server" to "8.8.8.8")),
            "ip/dhcp-client" to listOf(mapOf(".id" to "*3","interface" to "ether1","use-peer-dns" to "yes")),
            "interface/pppoe-client" to emptyList()
        )
        val calls=mutableListOf<String>();var loseReply=false;var ignoreSet=false;var readFails=false
        override suspend fun read(menu:String):List<RouterRow>{if(readFails)error("NO_READ_PERMISSION");return tables[menu].orEmpty()}
        override suspend fun create(menu:String,attributes:RouterRow):List<RouterRow> = error("NO_CREATE")
        override suspend fun execute(command:String,attributes:RouterRow):List<RouterRow>{
            calls+=command
            if(command.endsWith("/set")){
                val menu=command.removePrefix("/").removeSuffix("/set")
                if(!ignoreSet)tables[menu]=tables.getValue(menu).map{row->if(menu=="ip/dns" || row[".id"]==attributes[".id"])row+attributes else row}
                if(loseReply){loseReply=false;throw java.io.IOException("Lost acknowledgement")}
            };return emptyList()
        }
        override fun close(){}
    }
    @Test fun familyConfiguresSelectedNetworkAndRouterWithoutOpeningDnsAndRestoresEverything()=runTest {
        val t=Fake();val before=t.tables.toMap();val m=DnsProtectionManager(t,"router")
        val plan=m.plan(m.inspect(),setOf("*1"),DnsProtectionMode.FAMILY)
        var saved:DnsProtectionReceipt?=null;var backedUp=false
        val receipt=m.apply(plan,{backedUp=true;"encrypted.backup"}){saved=it}!!
        assertTrue(backedUp);assertEquals("ACTIVE",saved!!.state);assertTrue(m.verified(receipt,m.inspect()))
        assertEquals("94.140.14.15,94.140.15.16",t.tables.getValue("ip/dns").single()["servers"])
        assertEquals("no",t.tables.getValue("ip/dns").single()["allow-remote-requests"])
        assertEquals("",t.tables.getValue("ip/dns").single()["use-doh-server"])
        assertEquals(before.getValue("ip/dhcp-server/network")[1],t.tables.getValue("ip/dhcp-server/network")[1])
        assertTrue(t.calls.none{it.contains("firewall") || it.endsWith("/add")})
        m.restore(receipt){saved=it};assertNull(saved)
        for(menu in before.keys)assertEquals(before[menu],t.tables[menu])
    }
    @Test fun stalePlanReadFailureAndFailedBackupNeverMutateConfiguration()=runTest {
        val t=Fake();val m=DnsProtectionManager(t,"router");val p=m.plan(m.inspect(),setOf("*1"),DnsProtectionMode.ADS_TRACKERS)
        t.tables["ip/dns"]=t.tables.getValue("ip/dns").map{it+("servers" to "1.1.1.1")}
        assertTrue(runCatching{m.apply(p,{error("Should not back up")}){}}.isFailure);assertTrue(t.calls.isEmpty())
        val fresh=m.plan(m.inspect(),setOf("*1"),DnsProtectionMode.FAMILY)
        assertTrue(runCatching{m.apply(fresh,{error("BACKUP_FAILED")}){}}.isFailure);assertTrue(t.calls.isEmpty())
        t.readFails=true;assertTrue(runCatching{m.inspect()}.isFailure)
    }
    @Test fun lostReplyRollsBackAlreadyAppliedCommandUsingDurableJournal()=runTest {
        val t=Fake();val original=t.tables.getValue("ip/dns").single();val m=DnsProtectionManager(t,"router")
        val plan=m.plan(m.inspect(),setOf("*1"),DnsProtectionMode.FAMILY);t.loseReply=true
        val states=mutableListOf<DnsProtectionReceipt?>()
        assertTrue(runCatching{m.apply(plan,{"encrypted.backup"}){states+=it}}.isFailure)
        assertEquals("APPLYING",states.first()!!.state);assertNull(states.last());assertEquals(original,t.tables.getValue("ip/dns").single())
    }
    @Test fun laterDnsEditsAndReplacementRouterAreNeverOverwrittenByRestore()=runTest {
        val t=Fake();val m=DnsProtectionManager(t,"router");val plan=m.plan(m.inspect(),setOf("*1"),DnsProtectionMode.FAMILY)
        val r=m.apply(plan,{"encrypted.backup"}){}!!
        t.tables["ip/dns"]=t.tables.getValue("ip/dns").map{it+("servers" to "1.1.1.1")}
        assertFalse(m.verified(r,m.inspect()))
        val n=t.calls.size;assertTrue(runCatching{m.restore(r){}}.isFailure);assertEquals(n,t.calls.size)
        t.tables["ip/dns"]=t.tables.getValue("ip/dns").map{it+("servers" to DnsProtectionMode.FAMILY.servers)}
        t.tables["interface"]=listOf(mapOf("type" to "ether","mac-address" to "AA:BB:CC:DD:EE:FF"))
        assertTrue(runCatching{m.restore(r){}}.isFailure);assertEquals(n,t.calls.size)
    }
    @Test fun unverifiedSetAndUnfilteredDynamicFallbackNeverReportActive()=runTest {
        for(ignore in listOf(true,false)){
            val t=Fake();val m=DnsProtectionManager(t,"router");t.ignoreSet=ignore
            if(!ignore)t.tables["ip/dns"]=t.tables.getValue("ip/dns").map{it+("dynamic-servers" to "8.8.8.8")}
            val plan=m.plan(m.inspect(),setOf("*1"),DnsProtectionMode.FAMILY);val states=mutableListOf<DnsProtectionReceipt?>()
            assertTrue(runCatching{m.apply(plan,{"encrypted.backup"}){states+=it}}.isFailure)
            assertFalse(states.any{it?.state=="ACTIVE"})
        }
    }
    @Test fun recoveryJournalFailurePreventsFirstWriteAndCustomOptionsNeedReview()=runTest {
        val t=Fake();val m=DnsProtectionManager(t,"router");val p=m.plan(m.inspect(),setOf("*1"),DnsProtectionMode.FAMILY)
        assertTrue(runCatching{m.apply(p,{"encrypted.backup"}){error("JOURNAL_FAILED")}}.isFailure);assertTrue(t.calls.isEmpty())
        t.tables["ip/dhcp-server/network"]=t.tables.getValue("ip/dhcp-server/network").map{it+("dhcp-option" to "dns-override")}
        assertTrue(runCatching{m.plan(m.inspect(),setOf("*1"),DnsProtectionMode.FAMILY)}.isFailure)
        assertTrue(runCatching{m.plan(m.inspect(),setOf("*WAN"),DnsProtectionMode.FAMILY)}.isFailure)
    }
    @Test fun networkAlreadyFilteredStillAllowsGlobalDnsChangeAndNoDuplicateWrites()=runTest {
        val t=Fake();val m=DnsProtectionManager(t,"router")
        t.tables["ip/dhcp-server/network"]=t.tables.getValue("ip/dhcp-server/network").map{if(it[".id"]=="*1")it+mapOf("dns-server" to DnsProtectionMode.FAMILY.servers,"dns-none" to "no") else it}
        val p=m.plan(m.inspect(),setOf("*1"),DnsProtectionMode.FAMILY);assertTrue(p.changes.none{it.menu=="ip/dhcp-server/network"})
        m.apply(p,{"encrypted.backup"}){}
        val p2=m.plan(m.inspect(),setOf("*1"),DnsProtectionMode.FAMILY);assertTrue(p2.changes.isEmpty())
        val n=t.calls.size;assertNull(m.apply(p2,{error("No backup needed")}){});assertEquals(n,t.calls.size)
    }
    @Test fun onlyCanonicalPrivateAndCarrierSubnetsAreEligible() {
        for(s in listOf("10.0.0.0/8","172.16.0.0/12","192.168.1.0/24","100.64.0.0/10"))assertTrue(s,DnsProtectionManager.privateSubnet(s))
        for(s in listOf("0.0.0.0/0","8.8.8.0/24","192.168.1.1/24","172.0.0.0/8","192.168.0.0/8","100.0.0.0/8","10.0.0.0/32","10.0.0.0/abc"))assertFalse(s,DnsProtectionManager.privateSubnet(s))
    }
}
