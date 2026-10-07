package com.fgmachines.mikrotikmanager.accesspoint

import org.junit.Assert.*
import org.junit.Test
class AccessPointEngineTest {
    private val a="02:11:22:33:44:01"
    private val b="02:11:22:33:44:02"
    private fun fixture(count:Int=1):Map<String,List<ApRow>> = mapOf(
        "interface/ethernet" to (1..count).map{mapOf("name" to "ether${it+1}")},
        "ip/neighbor" to (1..count).map{mapOf("mac-address" to "02:11:22:33:44:0$it","interface" to "ether${it+1}","system-caps-enabled" to "wlan-access-point")})
    @Test fun emptyIsEmpty(){assertTrue(AccessPointEngine.discover(emptyMap()).isEmpty())}
    @Test fun threeWithoutDhcp(){val result=AccessPointEngine.discover(fixture(3));assertEquals(3,result.size);assertTrue(result.all{it["classification"]=="Confirmed AP"});assertEquals("ether2",result[0]["port"])}
    @Test fun neighborOnlyCannotInventPort(){val data=fixture().minus("interface/ethernet");assertEquals("",AccessPointEngine.discover(data).single()["port"])}
    @Test fun deduplicatesMac(){val data=fixture()+mapOf("ip/arp" to listOf(mapOf("mac-address" to a.lowercase(),"address" to "192.168.1.2")));assertEquals(1,AccessPointEngine.discover(data).size)}
    @Test fun ordinaryClientAndSpoofedHostnameNotAp(){val row=mapOf("mac-address" to a,"host-name" to "TP-Link Access Point");assertEquals("Unknown network device",AccessPointEngine.discover(mapOf("ip/dhcp-server/lease" to listOf(row))).single()["classification"])}
    @Test fun bridgeHostDoesNotProveAp(){assertEquals("Unknown network device",AccessPointEngine.discover(mapOf("interface/bridge/host" to listOf(mapOf("mac-address" to a,"on-interface" to "ether2")))).single()["classification"])}
    @Test fun multicastAndLocalIgnored(){assertTrue(AccessPointEngine.discover(mapOf("ip/arp" to listOf(mapOf("mac-address" to "FF:FF:FF:FF:FF:FF")),"interface/bridge/host" to listOf(mapOf("mac-address" to a,"local" to "true")))).isEmpty())}
    @Test fun sharedIpDoesNotMergeDistinctDevices(){assertEquals(2,AccessPointEngine.discover(mapOf("ip/arp" to listOf(mapOf("mac-address" to a,"address" to "192.168.1.2"),mapOf("mac-address" to b,"address" to "192.168.1.2")))).size)}
    @Test fun correlationIsInferredAndNatUnknown(){val data=fixture()+mapOf("interface/bridge/host" to listOf(mapOf("mac-address" to b,"on-interface" to "ether2")),"ip/hotspot/active" to listOf(mapOf("mac-address" to b,"user" to "card1",".id" to "*1")));val devices=AccessPointEngine.discover(data);assertEquals("Inferred",AccessPointEngine.correlate(data,devices).single()["confidence"]);assertEquals("Unknown",AccessPointEngine.correlate(data,devices.map{it+mapOf("mode" to "NAT")}).single()["confidence"])}
    @Test fun twoApsOnPortAreAmbiguous(){val data=fixture(2).toMutableMap();data["ip/neighbor"]=data.getValue("ip/neighbor").map{it+mapOf("interface" to "ether2")};data["interface/bridge/host"]=listOf(mapOf("mac-address" to "02:11:22:33:44:03","on-interface" to "ether2"));data["ip/hotspot/active"]=listOf(mapOf("mac-address" to "02:11:22:33:44:03"));assertEquals("Unknown",AccessPointEngine.correlate(data,AccessPointEngine.discover(data)).single()["confidence"])}
    @Test fun sampledAnalyticsAvoidDoubleCounting(){
        val device=mapOf("mac" to a,"classification" to "Confirmed AP","name" to "AP1","shop" to "Main")
        val records=listOf(
            mapOf("kind" to "session","ap" to a,"id" to "*1","account" to "card1","client" to b,"upload" to "100","download" to "500","uptime" to "10m","profile" to "1h","at" to "1735689600000"),
            mapOf("kind" to "session","ap" to a,"id" to "*1","account" to "card1","client" to b,"upload" to "150","download" to "700","uptime" to "20m","profile" to "1h","at" to "1735693200000"),
            mapOf("kind" to "session","ap" to a,"id" to "*2","account" to "card2","client" to "02:11:22:33:44:03","upload" to "50","download" to "100","uptime" to "5m","profile" to "2h","at" to "1735693200000"))
        val row=AccessPointStore.summary(records,listOf(device)).single()
        assertEquals("2",row["observedAccounts"]);assertEquals("2",row["observedSessions"]);assertEquals("200",row["uploadBytes"]);assertEquals("800",row["downloadBytes"]);assertEquals("1000",row["totalTrafficBytes"]);assertEquals("750",row["averageSessionSeconds"]);assertEquals("1",row["rankByObservedAccounts"])
    }
    @Test fun csvNeutralizesFormulasAndEscapesQuotes(){val csv=ApExport.csv(listOf(mapOf("shop" to "=1+1","name" to "a\"b")));assertTrue(csv.contains("'=1+1"));assertTrue(csv.contains("a\"\"b"));assertTrue(csv.startsWith("\uFEFF"))}
}
