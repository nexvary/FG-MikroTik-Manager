package com.fgmachines.mikrotikmanager.business

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import java.util.UUID
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

@RunWith(AndroidJUnit4::class)
class BusinessStoreTest {
    private val context=InstrumentationRegistry.getInstrumentation().targetContext
    private lateinit var helper: BusinessDatabase
    private lateinit var store: BusinessStore
    private lateinit var scope: BusinessScope
    private lateinit var name: String
    @Before fun setup() { name="test-business-${UUID.randomUUID()}.db"; helper=BusinessDatabase(context,name); store=BusinessStore(helper); scope=store.defaultScope() }
    @After fun cleanup() { store.close(); context.deleteDatabase(name) }
    private fun sub(id: String="s", currency: String="EGP")=store.addSubscriber(scope,id,"Subscriber $id","","HOTSPOT","",currency)
    private fun reject(block: ()->Unit) { try { block(); fail("Expected rejection") } catch(_: IllegalArgumentException) {} catch(_: android.database.SQLException) {} }
    @Test fun amountsReversalAndReloadPersist() {
        sub(); store.post(scope,"s","c",LedgerKind.CHARGE,10000,"Monthly charge")
        store.post(scope,"s","p",LedgerKind.PAYMENT,4000,"Cash received")
        assertEquals(6000L,store.balance(scope,"s"))
        store.reverse(scope,"s","p","r","Incorrect payment")
        assertEquals(10000L,store.balance(scope,"s"))
        store.close(); helper=BusinessDatabase(context,name); store=BusinessStore(helper)
        assertEquals(scope,store.defaultScope()); assertEquals(10000L,store.balance(scope,"s"))
        assertEquals(3,store.ledger(scope,"s").items.size)
        assertTrue(store.ledger(scope,"s").items.single { it.id=="p" }.reversed)
    }
    @Test fun replayIsIdempotentAndConflictsRejected() {
        sub(); sub(); store.post(scope,"s","p",LedgerKind.PAYMENT,4000,"Cash")
        store.post(scope,"s","p",LedgerKind.PAYMENT,4000,"Cash")
        reject { store.post(scope,"s","p",LedgerKind.PAYMENT,5000,"Cash") }
        assertEquals(-4000L,store.balance(scope,"s")); assertEquals(1,store.ledger(scope,"s").items.size)
        store.reverse(scope,"s","p","r","Undo"); store.reverse(scope,"s","p","r","Undo")
        reject { store.reverse(scope,"s","p","r2","Again") }
        reject { store.reverse(scope,"s","r","r3","Reverse reversal") }
        assertEquals(0L,store.balance(scope,"s"))
    }
    @Test fun independentConnectionsCannotDuplicatePayment() {
        sub(); val other=BusinessStore(BusinessDatabase(context,name)); val pool=Executors.newFixedThreadPool(2)
        try {
            val futures=listOf(store,other).map { st -> pool.submit { repeat(5) { st.post(scope,"s","same",LedgerKind.PAYMENT,100,"Cash") } } }
            futures.forEach { it.get(10,TimeUnit.SECONDS) }
            assertEquals(-100L,store.balance(scope,"s")); assertEquals(1,store.ledger(scope,"s").items.size)
        } finally { other.close(); pool.shutdownNow() }
    }
    @Test fun scopeAndCurrencyAreEnforcedByDatabase() {
        sub(); sub("usd","USD")
        val wrong=BusinessScope(scope.organizationId,UUID.randomUUID().toString())
        assertTrue(store.subscribers(wrong).items.isEmpty())
        reject { store.post(wrong,"s","p",LedgerKind.PAYMENT,100,"Cash") }
        reject { helper.writableDatabase.execSQL("INSERT INTO ledger(id,organization_id,branch_id,subscriber_id,kind,amount_minor,currency,note,created_at) VALUES (?,?,?,?,?,?,?,?,?)",arrayOf("bad",scope.organizationId,scope.branchId,"s","CHARGE",100,"USD","Wrong currency",1)) }
        assertEquals(0L,store.balance(scope,"s"))
    }
    @Test fun ledgerCannotBeDeletedEditedOrReversedAcrossSubscribers() {
        sub(); sub("other"); store.post(scope,"s","c",LedgerKind.CHARGE,500,"Charge")
        reject { helper.writableDatabase.execSQL("UPDATE subscribers SET currency='USD' WHERE id='s'") }
        reject { helper.writableDatabase.execSQL("UPDATE ledger SET amount_minor=1 WHERE id='c'") }
        reject { helper.writableDatabase.execSQL("DELETE FROM ledger WHERE id='c'") }
        reject { store.reverse(scope,"other","c","r","Wrong account") }
        reject { helper.writableDatabase.execSQL("INSERT INTO ledger(id,organization_id,branch_id,subscriber_id,kind,amount_minor,currency,note,created_at,reversal_of) VALUES (?,?,?,?,?,?,?,?,?,?)",arrayOf("r",scope.organizationId,scope.branchId,"other","REVERSAL",-500,"EGP","Wrong account",1,"c")) }
        assertEquals(500L,store.balance(scope,"s"))
    }
    @Test fun boundedCursorPagingHasNoDuplicatesAndSearchIsLiteral() {
        repeat(65) { sub("%03d".format(it)) }
        val first=store.subscribers(scope,limit=30); assertTrue(first.hasMore)
        val last=first.items.last(); val second=store.subscribers(scope,afterName=last.name,afterId=last.id,limit=30)
        val last2=second.items.last(); val third=store.subscribers(scope,afterName=last2.name,afterId=last2.id,limit=30)
        assertEquals(65,(first.items+second.items+third.items).map { it.id }.toSet().size); assertFalse(third.hasMore)
        assertTrue(store.subscribers(scope,"%'").items.isEmpty())
        repeat(65) { store.post(scope,"000","c$it",LedgerKind.CHARGE,1,"Charge") }
        val a=store.ledger(scope,"000"); val b=store.ledger(scope,"000",a.items.last().sequence); val c=store.ledger(scope,"000",b.items.last().sequence)
        assertEquals(65,(a.items+b.items+c.items).map { it.id }.toSet().size)
        assertEquals(65L,store.balance(scope,"000"))
    }
    @Test fun versionOneReopenPreservesUnrelatedVoucherPreferences() {
        val prefs=context.getSharedPreferences("fg_voucher_history",0)
        prefs.edit().putString("business-test-sentinel","unchanged").commit()
        try { sub(); store.close(); helper=BusinessDatabase(context,name); store=BusinessStore(helper)
            assertEquals("unchanged",prefs.getString("business-test-sentinel",null)); assertEquals("s",store.subscriber(scope,"s").id)
        } finally { prefs.edit().remove("business-test-sentinel").commit() }
    }
}
