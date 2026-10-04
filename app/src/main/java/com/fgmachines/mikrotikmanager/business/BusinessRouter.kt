package com.fgmachines.mikrotikmanager.business

import com.fgmachines.mikrotikmanager.network.RouterOsTransport
import java.security.MessageDigest
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.Duration

data class NetworkAccount(val id: String,val name: String,val service: String,val profile: String,val disabled: Boolean)
data class NetworkCatalog(val fingerprint: String,val label: String,val accounts: List<NetworkAccount>,val hotspotProfiles: List<String>,val pppProfiles: List<String>)
data class NetworkTarget(val fingerprint: String,val accountId: String,val account: String,val service: String,val binding: String,val profile: String,val endDay: Long,val byteLimit: Long)

/** Reconciles absolute desired state. Does not create users, copy passwords, or retry a failed mutation. */
class BusinessRouter(private val transport: RouterOsTransport) {
    private suspend fun rows(menu: String,columns: String)=transport.execute("$menu/print",mapOf(".proplist" to columns))
    private fun menu(service: String)=when(service){"HOTSPOT"->"ip/hotspot/user";"PPPOE"->"ppp/secret";else->error("UNSUPPORTED_SERVICE")}
    suspend fun identity(): Pair<String,String> {
        val hardware=runCatching { rows("system/routerboard","serial-number").firstOrNull()?.get("serial-number") }.getOrNull()
        val license=if(hardware.isNullOrBlank()) rows("system/license","software-id,system-id").firstOrNull().orEmpty() else emptyMap()
        val stable=hardware?.takeIf { it.isNotBlank() }?.let { "serial:$it" }
            ?: license["software-id"]?.takeIf { it.isNotBlank() }?.let { "software:$it" }
            ?: license["system-id"]?.takeIf { it.isNotBlank() }?.let { "system:$it" }
            ?: error("ROUTER_ID_UNAVAILABLE")
        val hash=MessageDigest.getInstance("SHA-256").digest(stable.toByteArray()).joinToString("") { "%02x".format(it) }
        return hash to rows("system/identity","name").firstOrNull()?.get("name").orEmpty()
    }
    suspend fun catalog(): NetworkCatalog {
        val (id,label)=identity()
        val accounts=listOf("HOTSPOT","PPPOE").flatMap { service ->
            rows(menu(service),".id,name,profile,disabled,service").filter { service!="PPPOE" || it["service"] =="pppoe" }.map { r ->
                NetworkAccount(r[".id"] ?: error("INVALID_ROUTER_ROW"),r["name"] ?: error("INVALID_ROUTER_ROW"),service,r["profile"].orEmpty(),r["disabled"] in listOf("true","yes"))
            }
        }
        require(accounts.size<=5000) { "IMPORT_LIMIT" }
        return NetworkCatalog(id,label,accounts,rows("ip/hotspot/user/profile","name").mapNotNull { it["name"] },rows("ppp/profile","name").mapNotNull { it["name"] })
    }
    private suspend fun account(t: NetworkTarget): Map<String,String> {
        require(identity().first==t.fingerprint) { "WRONG_ROUTER" }
        return rows(menu(t.service),".id,name,profile,disabled,service,limit-uptime,limit-bytes-total,bytes-in,bytes-out").singleOrNull { it[".id"]==t.accountId && it["name"]==t.account && (t.service!="PPPOE" || it["service"] =="pppoe") }
            ?: error("ACCOUNT_CHANGED")
    }
    suspend fun target(fingerprint: String,accountId: String,name: String,service: String,binding: String,profile: String,endDay: Long,allowance: Long): NetworkTarget {
        require(allowance>=0 && (service=="HOTSPOT" || allowance==0L)) { "INVALID_QUOTA" }
        var t=NetworkTarget(fingerprint,accountId,name,service,binding,profile,endDay,0)
        val row=account(t)
        val limit=if(allowance==0L) 0 else Math.addExact(Math.addExact(row["bytes-in"]?.toLongOrNull() ?: error("COUNTERS_UNAVAILABLE"),row["bytes-out"]?.toLongOrNull() ?: error("COUNTERS_UNAVAILABLE")),allowance)
        t=t.copy(byteLimit=limit);validate(t);return t
    }
    private suspend fun validate(t: NetworkTarget) {
        require(t.binding.matches(Regex("[A-Za-z0-9-]{1,64}")))
        require(t.endDay>LocalDate.now().toEpochDay()) { "SUBSCRIPTION_EXPIRED" }
        require(t.account.isNotBlank() && t.account.none { it.isISOControl() }) { "INVALID_TEXT" }
        require(rows(if(t.service=="HOTSPOT")"ip/hotspot/user/profile" else "ppp/profile","name").any { it["name"]==t.profile }) { "PROFILE_NOT_FOUND" }
        val clock=rows("system/clock","date,time").single()
        val now=runCatching { LocalDateTime.of(LocalDate.parse(clock["date"]),LocalTime.parse(clock["time"])) }.getOrElse { error("MODERN_CLOCK_REQUIRED") }
        require(kotlin.math.abs(Duration.between(now,LocalDateTime.now()).seconds)<=300) { "ROUTER_CLOCK_MISMATCH" }
    }
    suspend fun apply(t: NetworkTarget) {
        account(t);validate(t)
        val name=schedulerName(t);val expected=script(t);val owner="FG MTM billing:${t.binding}"
        val existing=rows("system/scheduler",".id,name,comment,on-event,disabled,interval").filter { it["name"]==name }
        require(existing.size<=1 && existing.all { it["comment"]==owner }) { "SCHEDULER_CONFLICT" }
        val attributes=mapOf("name" to name,"comment" to owner,"on-event" to expected,"interval" to "1m","start-time" to "00:00:00","policy" to "read,write,test","disabled" to "no")
        if(existing.isEmpty()) transport.create("system/scheduler",attributes)
        else transport.execute("system/scheduler/set",attributes+(".id" to existing.single().getValue(".id")))
        // Verify expiry protection before enabling the account. A timeout leaves the durable job in REVIEW.
        check(rows("system/scheduler","name,on-event,disabled,interval").any { it["name"]==name && it["on-event"]==expected && it["disabled"] in listOf("false","no") && it["interval"] in listOf("1m","00:01:00") }) { "VERIFY_FAILED" }
        val update=mutableMapOf(".id" to t.accountId,"profile" to t.profile,"disabled" to "no")
        if(t.service=="HOTSPOT") { update["limit-uptime"]="0s";update["limit-bytes-total"]=t.byteLimit.toString() }
        transport.execute("${menu(t.service)}/set",update)
        disconnect(t)
        val after=account(t)
        check(after["profile"]==t.profile && after["disabled"] in listOf("false","no")) { "VERIFY_FAILED" }
        if(t.service=="HOTSPOT") check(after["limit-bytes-total"]?.toLongOrNull()==t.byteLimit && after["limit-uptime"] in listOf("0s","00:00:00","0")) { "VERIFY_FAILED" }
    }
    suspend fun suspendAccount(t: NetworkTarget) {
        account(t)
        transport.execute("${menu(t.service)}/set",mapOf(".id" to t.accountId,"disabled" to "yes"));disconnect(t)
        val schedulers=rows("system/scheduler",".id,name,comment").filter { it["name"]==schedulerName(t) }
        require(schedulers.size<=1 && schedulers.all { it["comment"]=="FG MTM billing:${t.binding}" }) { "SCHEDULER_CONFLICT" }
        schedulers.forEach { transport.execute("system/scheduler/set",mapOf(".id" to it.getValue(".id"),"disabled" to "yes")) }
        check(account(t)["disabled"] in listOf("true","yes")) { "VERIFY_FAILED" }
    }
    private suspend fun disconnect(t: NetworkTarget) {
        val active=if(t.service=="HOTSPOT") "ip/hotspot/active" else "ppp/active"
        val field=if(t.service=="HOTSPOT") "user" else "name"
        rows(active,".id,$field").filter { it[field]==t.account }.forEach { transport.execute("$active/remove",mapOf(".id" to it.getValue(".id"))) }
    }
    companion object {
        fun schedulerName(t: NetworkTarget)="fg-bill-"+t.binding
        fun quote(value: String): String { require(value.none { it.isISOControl() });return "\""+value.replace("\\","\\\\").replace("\"","\\\"").replace("$","\\$")+"\"" }
        fun script(t: NetworkTarget): String {
            val date=LocalDate.ofEpochDay(t.endDay).toString().replace("-","")
            val menu=if(t.service=="HOTSPOT") "/ip hotspot user" else "/ppp secret"
            val active=if(t.service=="HOTSPOT") "/ip hotspot active" else "/ppp active"
            val field=if(t.service=="HOTSPOT") "user" else "name"
            // ISO date capability is checked before installation. The recurring job also catches expiry after reboot.
            return ":local d [/system clock get date]; :if ([:len \$d] != 10) do={:error \"ISO clock required\"}; "+
                ":local n [:tonum ([:pick \$d 0 4].[:pick \$d 5 7].[:pick \$d 8 10])]; "+
                ":if (\$n >= $date) do={ :if ([$menu get ${quote(t.accountId)} name] = ${quote(t.account)}) do={ "+
                "$menu set ${quote(t.accountId)} disabled=yes; $active remove [find where $field=${quote(t.account)}]; }; }"
        }
    }
}
