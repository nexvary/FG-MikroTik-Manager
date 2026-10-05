package com.fgmachines.mikrotikmanager.advanced

import com.fgmachines.mikrotikmanager.network.RouterOsTransport
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import java.security.MessageDigest

enum class DnsProtectionMode(val servers:String) {
    FAMILY("94.140.14.15,94.140.15.16"), ADS_TRACKERS("94.140.14.14,94.140.15.15")
}
@Serializable data class DnsProtectionChange(val menu:String,val id:String,val address:String="",val before:Map<String,String>,val after:Map<String,String>)
@Serializable data class DnsProtectionReceipt(val router:String,val fingerprint:String,val mode:String,val changes:List<DnsProtectionChange>,val backup:String,val state:String="APPLYING")
data class DnsProtectionInspection(val fingerprint:String,val signature:String,val tables:Map<String,List<RouterRow>>) {
    val networks get()=tables["ip/dhcp-server/network"].orEmpty().filter{!it[".id"].isNullOrBlank() && DnsProtectionManager.privateSubnet(it["address"].orEmpty())}
}
data class DnsProtectionPlan(val router:String,val inspection:DnsProtectionInspection,val mode:DnsProtectionMode,val networks:Set<String>,val changes:List<DnsProtectionChange>)

/** DNS-based filtering only: no HTTPS interception, public resolver exposure or firewall replacement. */
class DnsProtectionManager(private val transport:RouterOsTransport,private val router:String) {
    private val fields=linkedMapOf(
        "ip/dns" to listOf("servers","use-doh-server","allow-remote-requests","dynamic-servers"),
        "ip/dhcp-server/network" to listOf(".id","address","gateway","dns-server","dns-none","dhcp-option","dhcp-option-set"),
        "ip/dhcp-client" to listOf(".id","interface","disabled","use-peer-dns"),
        "interface/pppoe-client" to listOf(".id","interface","disabled","use-peer-dns")
    )
    suspend fun inspect():DnsProtectionInspection {
        val tables=fields.keys.associateWith{transport.read(it)}
        require(tables.getValue("ip/dns").size==1){"DNS_READ_REQUIRED"}
        val macs=transport.read("interface").filter{it["type"]=="ether"}.mapNotNull{it["mac-address"]?.uppercase()}.distinct().sorted()
        require(macs.isNotEmpty()){ "ROUTER_IDENTITY_REQUIRED" }
        val fingerprint=hash(macs.joinToString(","))
        val signature=hash(fields.entries.joinToString(";"){(menu,keys)->menu+":"+tables.getValue(menu).map{row->keys.joinToString("|"){k->k+"="+row[k].orEmpty()}}.sorted().joinToString(";")})
        return DnsProtectionInspection(fingerprint,signature,tables)
    }
    fun plan(inspection:DnsProtectionInspection,networkIds:Set<String>,mode:DnsProtectionMode):DnsProtectionPlan {
        require(networkIds.isNotEmpty() && networkIds.size<=32){"SELECT_CLIENT_NETWORK"}
        val selected=inspection.networks.filter{it[".id"] in networkIds}
        require(selected.size==networkIds.size){"SELECT_CLIENT_NETWORK"}
        require(selected.none{!it["dhcp-option"].isNullOrBlank() || !it["dhcp-option-set"].isNullOrBlank()}){"DHCP_OPTIONS_REVIEW"}
        val changes=mutableListOf<DnsProtectionChange>()
        fun change(menu:String,row:RouterRow,values:Map<String,String>) {
            val after=values.filter{(k,v)->normalize(row[k].orEmpty())!=normalize(v)}
            if(after.isNotEmpty())changes+=DnsProtectionChange(menu,row[".id"].orEmpty(),if(menu=="ip/dhcp-server/network")row["address"].orEmpty() else "",after.keys.associateWith{row[it].orEmpty()},after)
        }
        val dns=inspection.tables.getValue("ip/dns").single()
        change("ip/dns",dns,buildMap{put("servers",mode.servers);if(dns.containsKey("use-doh-server"))put("use-doh-server","")})
        selected.forEach{row->change("ip/dhcp-server/network",row,buildMap{put("dns-server",mode.servers);if(row.containsKey("dns-none"))put("dns-none","no")})}
        for(menu in listOf("ip/dhcp-client","interface/pppoe-client"))for(row in inspection.tables.getValue(menu)) {
            if(AdvancedRouterManager.enabled(row) && row["use-peer-dns"] in listOf("yes","true")) {
                require(!row[".id"].isNullOrBlank()){"DNS_READ_REQUIRED"};change(menu,row,mapOf("use-peer-dns" to "no"))
            }
        }
        require(changes.size<=128){"DNS_CHANGE_LIMIT"}
        return DnsProtectionPlan(router,inspection,mode,networkIds,changes)
    }
    suspend fun apply(plan:DnsProtectionPlan,backup:suspend()->String,save:(DnsProtectionReceipt?)->Unit):DnsProtectionReceipt? {
        require(plan.router==router){"WRONG_ROUTER"}
        val fresh=inspect()
        require(fresh.fingerprint==plan.inspection.fingerprint && fresh.signature==plan.inspection.signature){"DNS_CONFIGURATION_CHANGED"}
        require(plan.changes==this.plan(fresh,plan.networks,plan.mode).changes){"DNS_PLAN_CHANGED"}
        if(plan.changes.isEmpty())return null
        val file=backup();require(file.isNotBlank()){ "DNS_BACKUP_REQUIRED" }
        // A durable journal precedes the first mutation, including a command that applies but loses its reply.
        var receipt=DnsProtectionReceipt(router,fresh.fingerprint,plan.mode.name,plan.changes,file)
        save(receipt)
        try {
            for(change in plan.changes){ensureValues(change,change.before);write(change,change.after);ensureValues(change,change.after)}
            val verified=inspect()
            require(ownedValues(receipt,verified,false)){"DNS_VERIFY_FAILED"}
            val dynamic=verified.tables.getValue("ip/dns").single()["dynamic-servers"].orEmpty().split(',').map{it.trim()}.filter{it.isNotEmpty()}
            require(dynamic.all{it in plan.mode.servers.split(',')}){"DYNAMIC_DNS_REVIEW"}
            transport.execute("/ip/dns/cache/flush")
            receipt=receipt.copy(state="ACTIVE");save(receipt);return receipt
        }catch(failure:Exception) {
            withContext(NonCancellable){try{restore(receipt,save)}catch(_:Exception){save(receipt.copy(state="REVIEW"))}}
            if(failure is CancellationException)throw failure
            throw IllegalStateException("DNS_APPLY_FAILED_CHECK_RECOVERY",failure)
        }
    }
    suspend fun restore(receipt:DnsProtectionReceipt,save:(DnsProtectionReceipt?)->Unit) {
        require(receipt.router==router){"WRONG_ROUTER"}
        require(ownedValues(receipt,inspect(),true)){"DNS_RESTORE_CONFLICT"}
        for(change in receipt.changes.asReversed()){
            ensureValues(change,change.before,change.after);write(change,change.before);ensureValues(change,change.before)
        }
        val current=inspect()
        require(receipt.changes.all{change->val row=row(current,change);change.before.all{(k,v)->normalize(row[k].orEmpty())==normalize(v)}}){"DNS_RESTORE_VERIFY_FAILED"}
        transport.execute("/ip/dns/cache/flush");save(null)
    }
    private suspend fun ensureValues(change:DnsProtectionChange,values:Map<String,String>,alternate:Map<String,String>?=null) {
        val rows=transport.read(change.menu)
        val current=if(change.menu=="ip/dns")rows.singleOrNull() else rows.singleOrNull{it[".id"]==change.id && (change.address.isEmpty() || it["address"]==change.address)}
        require(current!=null && values.all{(k,v)->(normalize(current[k].orEmpty())==normalize(v) || alternate!=null && normalize(current[k].orEmpty())==normalize(alternate.getValue(k)))}){"DNS_CONFIGURATION_CHANGED"}
    }
    private suspend fun write(change:DnsProtectionChange,values:Map<String,String>) {
        transport.execute("/"+change.menu+"/set",values+if(change.menu=="ip/dns")emptyMap() else mapOf(".id" to change.id))
    }
    private fun row(current:DnsProtectionInspection,change:DnsProtectionChange):RouterRow {
        val rows=current.tables[change.menu].orEmpty()
        val row=if(change.menu=="ip/dns")rows.singleOrNull() else rows.singleOrNull{it[".id"]==change.id}
        require(row!=null && (change.address.isEmpty() || row["address"]==change.address)){"DNS_TARGET_CHANGED"};return row
    }
    private fun ownedValues(receipt:DnsProtectionReceipt,current:DnsProtectionInspection,allowBefore:Boolean):Boolean {
        if(receipt.fingerprint!=current.fingerprint)return false
        return receipt.changes.all{change->val row=row(current,change);change.after.all{(k,v)->normalize(row[k].orEmpty())==normalize(v) || allowBefore && normalize(row[k].orEmpty())==normalize(change.before.getValue(k))}}
    }
    companion object {
        private fun hash(value:String)=MessageDigest.getInstance("SHA-256").digest(value.toByteArray()).joinToString(""){"%02x".format(it)}
        private fun normalize(value:String)=when(value.trim()){ "true"->"yes";"false"->"no";else->value.replace(" ","") }
        fun privateSubnet(value:String):Boolean {
            val parts=value.split('/');if(parts.size!=2)return false
            val prefix=parts[1].toIntOrNull() ?: return false
            val octets=parts[0].split('.').map{it.toIntOrNull() ?: return false}
            if(octets.size!=4 || octets.any{it !in 0..255} || prefix !in 8..30)return false
            val private=octets[0]==10 || octets[0]==172 && octets[1] in 16..31 && prefix>=12 || octets[0]==192 && octets[1]==168 && prefix>=16 || octets[0]==100 && octets[1] in 64..127 && prefix>=10
            val address=octets.fold(0L){sum,n->(sum shl 8)+n};val mask=(0xFFFFFFFFL shl (32-prefix)) and 0xFFFFFFFFL
            return private && (address and mask)==address
        }
    }
}
