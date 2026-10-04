package com.fgmachines.mikrotikmanager.business

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.util.UUID

@RunWith(AndroidJUnit4::class)
class BusinessSyncTest {
    private val context=InstrumentationRegistry.getInstrumentation().targetContext
    @Test fun twoStoresMergeReplayImmutableMoneyAuditAndBranches() {
        val a="sync-a-${UUID.randomUUID()}";val b="sync-b-${UUID.randomUUID()}"
        try {BusinessStore(BusinessDatabase(context,a)).use{source->BusinessStore(BusinessDatabase(context,b)).use{target->
            val s=source.defaultScope();source.addSubscriber(s,"customer","Subscriber","","HOTSPOT","account","EGP")
            source.post(s,"customer","charge",LedgerKind.CHARGE,100,"charge")
            source.post(s,"customer","payment",LedgerKind.PAYMENT,25,"receipt",PaymentMethod.INSTAPAY,"ref")
            val one=BusinessReplica(source);val two=BusinessReplica(target);val records=one.snapshot()
            two.join(s.organizationId,s.branchId,records)
            val initial=two.snapshot();two.validate(records,"https://test",initial)
            assertEquals(0,target.subscribers(target.defaultScope()).items.size)
            assertTrue(two.merge(records,"https://test",initial)>0);assertEquals(75L,target.balance(s,"customer"))
            val baseline=two.snapshot();val merged=two.combine(baseline,records,"https://test")
            assertEquals(0,two.merge(merged,"https://test",baseline));assertEquals(75L,target.balance(s,"customer"))
            val audit=BusinessOperations(target).audit(s).items
            assertTrue(audit.any{it.device==one.device()});assertTrue(audit.any{it.device==two.device()})
            val changed=JSONArray(records.toString());for(i in 0 until changed.length()){val r=changed.getJSONObject(i);if(r.getString("table")=="ledger"&&r.getString("id")=="charge")r.getJSONObject("body").put("amount_minor",200)}
            assertTrue(runCatching{two.combine(two.snapshot(),changed,"https://test")}.isFailure)
            assertEquals(75L,target.balance(s,"customer"))
            val foreign=JSONArray(records.toString());for(i in 0 until foreign.length()){val row=foreign.getJSONObject(i).getJSONObject("body");if(row.has("branch_id"))row.put("branch_id","foreign")}
            assertTrue(runCatching{two.merge(foreign,"https://test",two.snapshot())}.isFailure)
            assertTrue(runCatching{target.helper.writableDatabase.execSQL("UPDATE ledger SET amount_minor=2")}.isFailure)
            assertTrue(runCatching{target.helper.writableDatabase.execSQL("DELETE FROM audit_details")}.isFailure)
        }}}
        finally {context.deleteDatabase(a);context.deleteDatabase(b)}
    }
    @Test fun concurrentLocalWritesAndWrongJoinAreRejected() {
        val name="sync-${UUID.randomUUID()}"
        try {BusinessStore(BusinessDatabase(context,name)).use{store->
            val replica=BusinessReplica(store);val before=replica.snapshot();val s=store.defaultScope()
            store.addSubscriber(s,"s","User","","OTHER","","EGP")
            assertTrue(runCatching{replica.merge(before,"https://test",before)}.isFailure)
            assertTrue(runCatching{replica.join("other","branch",before)}.isFailure)
            assertEquals(1,store.subscribers(s).items.size)
        }}finally{context.deleteDatabase(name)}
    }
    @Test fun versionSixUpgradePreservesMoneyAndAddsReplicaMetadata() {
        val name="sync-upgrade-${UUID.randomUUID()}"
        try {
            BusinessStore(BusinessDatabase(context,name)).use{s->val scope=s.defaultScope();s.addSubscriber(scope,"s","User","","OTHER","","EGP");s.post(scope,"s","c",LedgerKind.CHARGE,20,"test")
                s.helper.writableDatabase.execSQL("DROP TABLE cloud_baseline");s.helper.writableDatabase.execSQL("DROP TABLE cloud_audit_ids");s.helper.writableDatabase.version=6}
            BusinessStore(BusinessDatabase(context,name)).use{s->assertEquals(7,s.helper.readableDatabase.version);assertEquals(20L,s.balance(s.defaultScope(),"s"));assertTrue(BusinessReplica(s).snapshot().length()>0)}
        }finally{context.deleteDatabase(name)}
    }
}
