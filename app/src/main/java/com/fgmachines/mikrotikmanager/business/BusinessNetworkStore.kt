package com.fgmachines.mikrotikmanager.business

import android.content.ContentValues
import java.util.UUID

data class RouterBinding(val id: String,val subscriber: String,val fingerprint: String,val label: String,val accountId: String,val account: String,val service: String,val profile: String)
data class NetworkJob(val id: String,val invoice: String,val target: NetworkTarget,val state: String)
class BusinessNetworkStore(private val store: BusinessStore) {
    private val db get()=store.helper.readableDatabase
    fun binding(scope: BusinessScope,sub: String): RouterBinding?=db.rawQuery("SELECT id,subscriber_id,fingerprint,label,account_id,account,service,profile FROM router_bindings WHERE organization_id=? AND branch_id=? AND subscriber_id=?",arrayOf(scope.organizationId,scope.branchId,sub)).use { c -> if(!c.moveToFirst()) null else RouterBinding(c.getString(0),c.getString(1),c.getString(2),c.getString(3),c.getString(4),c.getString(5),c.getString(6),c.getString(7)) }
    fun importAccounts(s: BusinessScope,catalog: NetworkCatalog,currency: String,selected: List<NetworkAccount>): Int=store.transaction { d ->
        require(selected.isNotEmpty() && selected.size<=1000 && selected.all { it in catalog.accounts }) { "IMPORT_LIMIT" }
        require(currency in BusinessMoney.currencies)
        var imported=0
        for(a in selected) {
            val existing=d.rawQuery("SELECT branch_id,account_id FROM router_bindings WHERE organization_id=? AND fingerprint=? AND service=? AND account=?",arrayOf(s.organizationId,catalog.fingerprint,a.service,a.name)).use { if(it.moveToFirst()) it.getString(0) to it.getString(1) else null }
            if(existing!=null) { require(existing.first==s.branchId && existing.second==a.id) { "BINDING_CONFLICT" };continue }
            val matching=d.rawQuery("SELECT id FROM subscribers WHERE organization_id=? AND branch_id=? AND service=? AND account=?",arrayOf(s.organizationId,s.branchId,a.service,a.name)).use { c -> buildList { while(c.moveToNext()) add(c.getString(0)) } }
            require(matching.size<=1) { "BINDING_CONFLICT" }
            val id=matching.singleOrNull() ?: UUID.randomUUID().toString().also { store.addSubscriber(s,it,a.name,"",a.service,a.name,currency) }
            val old=binding(s,id);require(old==null) { "BINDING_CONFLICT" }
            d.insertOrThrow("router_bindings",null,ContentValues().apply { put("id",UUID.randomUUID().toString());put("organization_id",s.organizationId);put("branch_id",s.branchId);put("subscriber_id",id);put("fingerprint",catalog.fingerprint);put("label",catalog.label);put("account_id",a.id);put("account",a.name);put("service",a.service);put("profile",a.profile);put("created_at",System.currentTimeMillis()) });imported++
        };imported
    }
    fun job(s: BusinessScope,invoice: String): NetworkJob?=db.rawQuery("""SELECT j.id,j.invoice_id,b.fingerprint,b.account_id,b.account,b.service,b.id,j.profile,j.end_day,j.byte_limit,
        COALESCE((SELECT state FROM network_results r WHERE r.job_id=j.id ORDER BY sequence DESC LIMIT 1),'PENDING')
        FROM network_jobs j JOIN router_bindings b ON b.id=j.binding_id WHERE j.organization_id=? AND j.branch_id=? AND j.invoice_id=?""",arrayOf(s.organizationId,s.branchId,invoice)).use { c -> if(!c.moveToFirst()) null else NetworkJob(c.getString(0),c.getString(1),NetworkTarget(c.getString(2),c.getString(3),c.getString(4),c.getString(5),c.getString(6),c.getString(7),c.getLong(8),c.getLong(9)),c.getString(10)) }
    fun validateLatest(s: BusinessScope,invoice: String) {
        val ops=BusinessOperations(store);val inv=ops.invoice(s,invoice) ?: error("INVOICE_NOT_FOUND")
        require(!inv.voided && ops.subscriptionEnd(s,inv.subscriberId)==inv.end) { "LATEST_INVOICE_REQUIRED" }
    }
    fun save(s: BusinessScope,invoice: String,target: NetworkTarget): NetworkJob=store.transaction { d ->
        validateLatest(s,invoice)
        val inv=BusinessOperations(store).invoice(s,invoice)!!;val b=binding(s,inv.subscriberId) ?: error("NOT_BOUND")
        require(target.binding==b.id && target.endDay==inv.end && target.fingerprint==b.fingerprint && target.accountId==b.accountId && target.account==b.account && target.service==b.service)
        job(s,invoice)?.let { require(it.target==target) { "IDEMPOTENCY_CONFLICT" };return@transaction it }
        d.insertOrThrow("network_jobs",null,ContentValues().apply { put("id",UUID.randomUUID().toString());put("organization_id",s.organizationId);put("branch_id",s.branchId);put("invoice_id",invoice);put("binding_id",b.id);put("profile",target.profile);put("end_day",target.endDay);put("byte_limit",target.byteLimit);put("created_at",System.currentTimeMillis()) });job(s,invoice)!!
    }
    fun result(s: BusinessScope,invoice: String,state: String)=store.transaction { d ->
        val j=job(s,invoice) ?: error("JOB_NOT_FOUND")
        d.execSQL("INSERT INTO network_results(job_id,state,created_at) VALUES(?,?,?)",arrayOf(j.id,state,System.currentTimeMillis()))
        d.execSQL("INSERT INTO audit(organization_id,branch_id,entity,entity_id,action,created_at) VALUES(?,?,'network_jobs',?,?,?)",arrayOf(s.organizationId,s.branchId,j.id,state,System.currentTimeMillis()))
    }
}
