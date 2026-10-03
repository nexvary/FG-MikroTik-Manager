package com.fgmachines.mikrotikmanager.business

import com.fgmachines.mikrotikmanager.network.RouterOsTransport
import kotlinx.coroutines.test.runTest
import org.junit.Test
import org.junit.Assert.*
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter

class BusinessRouterTest {
    private class Fake: RouterOsTransport {
        val calls=mutableListOf<String>();val user=mutableMapOf(".id" to "*1","name" to "demo","profile" to "old","disabled" to "true","limit-uptime" to "1h","limit-bytes-total" to "500","bytes-in" to "100","bytes-out" to "200")
        var scheduler: MutableMap<String,String>?=null;var timeout=false;var serial="TEST-ROUTER";var wrongClock=false
        override suspend fun read(menu: String): List<Map<String,String>> = error("Projected reads required")
        override suspend fun create(menu: String,attributes: Map<String,String>): List<Map<String,String>> {
            calls+="create:$menu";scheduler=attributes.toMutableMap().apply { put(".id","*S");put("disabled","false") }
            if(timeout){timeout=false;throw java.io.IOException("Lost acknowledgment")};return emptyList()
        }
        override suspend fun execute(command: String,attributes: Map<String,String>): List<Map<String,String>> {
            calls+=command
            if(command.endsWith("/print")) {
                assertTrue(attributes.containsKey(".proplist"));assertFalse(attributes.getValue(".proplist").split(',').contains("password"))
                return when(command.removeSuffix("/print")) {
                    "system/routerboard"->listOf(mapOf("serial-number" to serial))
                    "system/identity"->listOf(mapOf("name" to "Demo router"))
                    "system/clock"->listOf(mapOf("date" to (if(wrongClock)"1970-01-02" else LocalDate.now().toString()),"time" to LocalTime.now().format(DateTimeFormatter.ofPattern("HH:mm:ss"))))
                    "ip/hotspot/user"->listOf(user.toMap())
                    "ip/hotspot/user/profile","ppp/profile"->listOf(mapOf("name" to "speed-10M"))
                    "system/scheduler"->listOfNotNull(scheduler?.toMap())
                    else->emptyList()
                }
            }
            if(command=="system/scheduler/set") scheduler!!.putAll(attributes+mapOf("disabled" to if(attributes["disabled"]=="yes")"true" else "false"))
            if(command=="ip/hotspot/user/set")user.putAll(attributes+mapOf("disabled" to if(attributes["disabled"]=="yes")"true" else "false"))
            return emptyList()
        }
        override fun close() {}
    }
    private suspend fun target(r: BusinessRouter)=r.target(r.identity().first,"*1","demo","HOTSPOT","binding-1","speed-10M",LocalDate.now().plusDays(30).toEpochDay(),1000)
    @Test fun installsExpiryBeforeEnableAndReconcilesWithoutAddingQuota()=runTest {
        val f=Fake();val r=BusinessRouter(f);val t=target(r);assertEquals(1300L,t.byteLimit)
        r.apply(t);r.apply(t)
        assertEquals(1,f.calls.count { it=="create:system/scheduler" });assertEquals("1300",f.user["limit-bytes-total"])
        assertTrue(f.calls.indexOf("create:system/scheduler")<f.calls.indexOf("ip/hotspot/user/set"))
        r.suspendAccount(t);assertEquals("true",f.user["disabled"]);assertEquals("true",f.scheduler!!["disabled"])
    }
    @Test fun timeoutIsNotReplayedAndRetryReadsExistingScheduler()=runTest {
        val f=Fake();val r=BusinessRouter(f);val t=target(r);f.timeout=true
        try { r.apply(t);fail("Expected timeout") }catch(_: java.io.IOException){}
        assertEquals("true",f.user["disabled"]);r.apply(t);assertEquals(1,f.calls.count { it=="create:system/scheduler" })
    }
    @Test fun wrongRouterClockOrChangedAccountBlockMutations()=runTest {
        val f=Fake();val r=BusinessRouter(f);val t=target(r)
        f.serial="OTHER";try {r.apply(t);fail("Wrong router")}catch(_: IllegalArgumentException){}
        f.serial="TEST-ROUTER";f.wrongClock=true;try {r.apply(t);fail("Wrong clock")}catch(_: IllegalArgumentException){}
        f.wrongClock=false;f.user["name"]="changed";try {r.apply(t);fail("Changed account")}catch(_: IllegalStateException){}
        assertFalse(f.calls.any { it.contains("/set") || it.startsWith("create:") })
    }
    @Test fun scriptsEscapeNamesAndUseRecurringCalendarCheck() {
        val t=NetworkTarget("x","*1","x\"; \$bad","HOTSPOT","b","p",LocalDate.of(2030,1,2).toEpochDay(),0)
        val script=BusinessRouter.script(t)
        assertTrue(script.contains("20300102"));assertTrue(script.contains("[:pick"));assertTrue(script.contains("disabled=yes"))
        assertTrue(BusinessRouter.quote("a\"b").contains("\\\""))
        try {BusinessRouter.quote("x\ny");fail("Control characters")}catch(_: IllegalArgumentException){}
    }
}
