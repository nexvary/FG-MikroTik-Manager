package com.fgmachines.mikrotikmanager.hotspot

import com.fgmachines.mikrotikmanager.network.RouterOsTransport
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test

class HotspotManagerTest {
    @Test fun addsToAllowanceWithoutResettingUptime() = runTest {
        val t = Fake(); HotspotManager(t).addTime("*A", 1800)
        assertEquals("5400s",t.executed.last().second["limit-uptime"])
        assertFalse(t.executed.last().second.containsKey("uptime"))
    }
    @Test fun disablesAndDisconnectsTheSession() = runTest {
        val t=Fake(); HotspotManager(t).setEnabled("*A","123456",false)
        assertEquals(listOf("/ip/hotspot/user/disable","/ip/hotspot/active/remove"),t.executed.map{it.first})
    }
    @Test fun uploadFailureNeverSwitchesLiveProfile() = runTest {
        val t=Fake();t.failFile=true
        try { HotspotManager(t).installPortal("*P",mapOf("login.html" to "login","status.html" to "status","md5.js" to "md5")); fail("Expected error") } catch(_: IllegalArgumentException) {}
        assertFalse(t.executed.any{it.first == "/ip/hotspot/profile/set"})
    }
    @Test fun portalPreservesOldFilesAndSavesRollbackBeforeSwitch() = runTest {
        val t=Fake(); HotspotManager(t).installPortal("*P",mapOf("login.html" to "login","status.html" to "status","md5.js" to "md5"))
        assertEquals("/ip/hotspot/profile/set",t.executed.last().first)
        assertTrue(t.files.any{it["name"]?.endsWith("rollback.txt")==true && it["contents"]?.contains("html-directory=hotspot")==true})
        assertFalse(t.executed.any{it.first.endsWith("remove")})
    }
    @Test fun templateEscapesUntrustedBranding() {
        val html=PortalTemplates.render(mapOf("login.html" to "@@NETWORK@@ @@WEBSITE@@ @@LOGO@@"),PortalDesign(networkName="<script>alert(1)</script>",website="javascript:alert(1)",logoDataUri="javascript:alert(2)"))["login.html"]!!
        assertFalse(html.contains("<script>"));assertFalse(html.contains("javascript:"))
    }
    @Test fun durationsHandleRouterOsUnitsAndUnknownFormats() {
        assertEquals(93784L,RouterDuration.seconds("1d2h3m4s")); assertEquals(3723L,RouterDuration.seconds("01:02:03")); assertNull(RouterDuration.seconds("bad"))
    }
    private class Fake:RouterOsTransport {
        val executed=mutableListOf<Pair<String,Map<String,String>>>()
        val files=mutableListOf<MutableMap<String,String>>()
        var failFile=false
        override suspend fun read(menu:String):List<Map<String,String>> = when(menu) {
            "ip/hotspot/user" -> listOf(mapOf(".id" to "*A","name" to "123456","limit-uptime" to "1h","uptime" to "12m"))
            "ip/hotspot/active" -> listOf(mapOf(".id" to "*B","user" to "123456"))
            "ip/hotspot/profile" -> listOf(mapOf(".id" to "*P","name" to "default","html-directory" to "hotspot"))
            "file" -> files
            else -> emptyList()
        }
        override suspend fun create(menu:String,attributes:Map<String,String>):List<Map<String,String>> {
            val row=(attributes+(".id" to "*"+files.size)).toMutableMap();if(menu=="file")files+=row;return listOf(row)
        }
        override suspend fun execute(command:String,attributes:Map<String,String>):List<Map<String,String>> {
            executed+=command to attributes
            if(command=="/file/set"&&!failFile)files.first{it[".id"]==attributes[".id"]}.putAll(attributes)
            return emptyList()
        }
        override fun close(){}
    }
}
