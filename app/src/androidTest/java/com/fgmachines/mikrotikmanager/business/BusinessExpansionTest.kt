package com.fgmachines.mikrotikmanager.business

import android.database.sqlite.SQLiteDatabase
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import java.io.StringWriter
import java.util.UUID
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

@RunWith(AndroidJUnit4::class)
class BusinessExpansionTest {
    private val context=InstrumentationRegistry.getInstrumentation().targetContext
    private lateinit var name: String;private lateinit var helper: BusinessDatabase;private lateinit var store: BusinessStore
    private lateinit var ops: BusinessOperations;private lateinit var scope: BusinessScope
    @Before fun setup() { name="expansion-${UUID.randomUUID()}.db";helper=BusinessDatabase(context,name);store=BusinessStore(helper);ops=BusinessOperations(store);scope=store.defaultScope() }
    @After fun cleanup() { store.close();context.deleteDatabase(name) }
    private fun seed() { store.addSubscriber(scope,"s","Customer","","HOTSPOT","account","EGP");ops.addPlan(scope,"plan","Monthly","HOTSPOT","EGP",10000,30) }
    private fun reject(block: ()->Unit) { var rejected=false;try { block() } catch(_: Exception) { rejected=true };assertTrue("Must reject invalid operation",rejected) }
    @Test fun renewalAtomicReplayAndPeriods() {
        seed();val first=ops.renew(scope,"one","s","plan",4000,20000)
        assertEquals(20000L,first.start);assertEquals(20030L,first.end);assertEquals(6000L,store.balance(scope,"s"))
        ops.renew(scope,"one","s","plan",4000,20099);assertEquals(20030L,ops.subscriptionEnd(scope,"s"));assertEquals(2,store.ledger(scope,"s").items.size)
        reject { ops.renew(scope,"one","s","plan",3000,20000) }
        val second=ops.renew(scope,"two","s","plan",0,20010);assertEquals(20030L,second.start);assertEquals(20060L,second.end)
        reject { ops.cancelInvoice(scope,"one","cancel1","Wrong order") }
        ops.cancelInvoice(scope,"two","cancel2","Correction");assertEquals(20030L,ops.subscriptionEnd(scope,"s"))
        ops.cancelInvoice(scope,"one","cancel1","Correction");ops.cancelInvoice(scope,"one","cancel1","Correction")
        assertNull(ops.subscriptionEnd(scope,"s"));assertEquals(0L,store.balance(scope,"s"))
    }
    @Test fun mismatchedPlansAndPartialInvoiceFailureRollback() {
        seed();ops.addPlan(scope,"usd","USD","HOTSPOT","USD",100,30)
        reject { ops.renew(scope,"bad","s","usd",0,20000) };assertEquals(0L,store.balance(scope,"s"))
        store.post(scope,"s","collision:p",LedgerKind.PAYMENT,1,"existing")
        reject { ops.renew(scope,"collision","s","plan",4000,20000) }
        assertNull(ops.invoice(scope,"collision"));assertEquals(-1L,store.balance(scope,"s"));assertEquals(1,store.ledger(scope,"s").items.size)
    }
    @Test fun linkedLedgerMustBeCanceledAsInvoice() {
        seed();ops.renew(scope,"i","s","plan",4000,20000)
        reject { store.reverse(scope,"s","i:c","manual","Wrong path") }
        reject { helper.writableDatabase.execSQL("UPDATE invoices SET paid_minor=0") }
        reject { helper.writableDatabase.execSQL("DELETE FROM invoices") }
        ops.cancelInvoice(scope,"i","v","Canceled");assertEquals(0L,store.balance(scope,"s"))
    }
    @Test fun concurrentRenewalIsExactlyOnce() {
        seed();val other=BusinessStore(BusinessDatabase(context,name));val pool=Executors.newFixedThreadPool(2)
        try { listOf(ops,BusinessOperations(other)).map { o -> pool.submit { o.renew(scope,"same","s","plan",10000,20000) } }.forEach { it.get(15,TimeUnit.SECONDS) }
            assertEquals(1,ops.invoices(scope).items.size);assertEquals(2,store.ledger(scope,"s").items.size);assertEquals(20030L,ops.subscriptionEnd(scope,"s"))
        } finally { other.close();pool.shutdownNow() }
    }
    @Test fun expenseReversalReportsSeparateCurrenciesAndDates() {
        seed();ops.renew(scope,"i","s","plan",4000,20000)
        ops.expense(scope,"e","Rent",500,"EGP","Office");ops.expense(scope,"usd","Hosting",700,"USD","Hosting")
        ops.reverseExpense(scope,"e","r","Correction");ops.reverseExpense(scope,"e","r","Correction")
        reject { ops.reverseExpense(scope,"e","again","Again") }
        val report=ops.totals(scope,0,Long.MAX_VALUE)
        val egp=report.single { it.currency=="EGP" };assertEquals(10000L,egp.charges);assertEquals(4000L,egp.receipts);assertEquals(0L,egp.expenses);assertEquals(6000L,egp.balance)
        assertEquals(700L,report.single { it.currency=="USD" }.expenses)
        val future=ops.totals(scope,System.currentTimeMillis()+100000,Long.MAX_VALUE).single { it.currency=="EGP" };assertEquals(0L,future.receipts);assertEquals(6000L,future.balance)
    }
    @Test fun branchBoundariesApplyToAllOperations() {
        seed();ops.renew(scope,"i","s","plan",0,20000);ops.addBranch(scope,"b","Second")
        val second=BusinessScope(scope.organizationId,"b")
        assertTrue(ops.plans(second).items.isEmpty());assertTrue(ops.invoices(second).items.isEmpty());assertNull(ops.invoice(second,"i"))
        reject { ops.renew(second,"bad","s","plan",0,20000) };reject { ops.cancelInvoice(second,"i","v","Wrong branch") }
        ops.selectBranch(scope,"b");assertEquals(second,store.defaultScope());assertEquals(10000L,store.balance(scope,"s"))
    }
    @Test fun importPreviewConflictsReplayAndRollback() {
        val t=BusinessTransfer(store);val text="name,phone,service,account,currency\nأحمد,,HOTSPOT,one,EGP\nSara,,PPPOE,two,USD\n"
        assertTrue(t.preview(scope,text).errors.isEmpty());assertEquals(2,t.import(scope,text));assertEquals(2,t.import(scope,text))
        assertEquals(2,store.subscribers(scope).items.size)
        val bad="name,phone,service,account,currency\nNew,,HOTSPOT,new,EGP\nDuplicate,,HOTSPOT,one,EGP\n"
        assertEquals(listOf("3"),t.preview(scope,bad).errors);reject { t.import(scope,bad) };assertEquals(2,store.subscribers(scope).items.size)
        reject { t.preview(scope,"wrong,header\nx,y") }
    }
    @Test fun exportIsScopedEscapedAndReconciles() {
        seed();store.post(scope,"s","c",LedgerKind.CHARGE,123,"=HYPERLINK(\"bad\")")
        store.post(scope,"s","payment",LedgerKind.PAYMENT,23,"Cash")
        ops.expense(scope,"e","Office",23,"EGP","Paper, pens")
        val writer=StringWriter();BusinessTransfer(store).export(scope,0,Long.MAX_VALUE,writer)
        val rows=BusinessCsv.parse(writer.toString());assertEquals(4,rows.size);assertTrue(rows.any { it[4]=="-23" });assertTrue(rows.any { it[6].startsWith("'=HYPERLINK") });assertTrue(rows.any { it[6]=="Paper, pens" })
    }
    @Test fun encryptedBackupRoundTripWrongPasswordTamperAndNoOverwrite() {
        seed();ops.renew(scope,"i","s","plan",4000,20000);ops.cancelInvoice(scope,"i","v","Correction");ops.expense(scope,"e","Rent",100,"EGP","Office")
        val password="Long backup password!".toCharArray();val bytes=BusinessBackup(store).export(password)
        assertFalse(String(bytes,Charsets.ISO_8859_1).contains("Customer"))
        reject { BusinessBackup(store).restore(bytes,password) };assertEquals(0L,store.balance(scope,"s"))
        val otherName="restored-${UUID.randomUUID()}.db";val other=BusinessStore(BusinessDatabase(context,otherName))
        try {
            reject { BusinessBackup(other).restore(bytes,"wrong password!".toCharArray()) };assertTrue(other.subscribers(other.defaultScope()).items.isEmpty())
            val corrupted=bytes.clone();corrupted[corrupted.lastIndex]=(corrupted.last().toInt() xor 1).toByte();reject { BusinessBackup(other).restore(corrupted,password) }
            BusinessBackup(other).restore(bytes,password);assertEquals(scope,other.defaultScope());assertEquals("Customer",other.subscriber(scope,"s").name)
            assertTrue(BusinessOperations(other).invoice(scope,"i")!!.voided);assertEquals(4,other.ledger(scope,"s").items.size);assertEquals(100L,BusinessOperations(other).expenses(scope).items.single().amount)
            assertTrue(BusinessOperations(other).audit(scope).items.any { it.action=="RESTORE" })
        } finally { other.close();context.deleteDatabase(otherName);password.fill('\u0000') }
    }
    @Test fun invalidAuthenticatedSnapshotRollsBackDataAndAuditTriggers() {
        seed();ops.renew(scope,"invoice","s","plan",100,20000)
        val password="Backup rollback password!".toCharArray()
        val bytes=BusinessBackup(store).export(password)
        val root=org.json.JSONObject(String(BusinessBackupCipher.decrypt(bytes,password),Charsets.UTF_8))
        root.getJSONObject("tables").put("subscribers",org.json.JSONArray())
        val broken=BusinessBackupCipher.encrypt(root.toString().toByteArray(Charsets.UTF_8),password)
        val otherName="rollback-${UUID.randomUUID()}.db";val other=BusinessStore(BusinessDatabase(context,otherName))
        try {
            val initial=other.defaultScope()
            reject { BusinessBackup(other).restore(broken,password) }
            assertEquals(initial,other.defaultScope())
            assertTrue(other.subscribers(initial).items.isEmpty())
            other.addSubscriber(initial,"new","New","","OTHER","","EGP")
            assertEquals(1,BusinessOperations(other).audit(initial).items.size)
        } finally { other.close();context.deleteDatabase(otherName);password.fill('\u0000') }
    }
    @Test fun auditImmutableAndCursorsDoNotDuplicate() {
        repeat(65) { ops.expense(scope,"e$it","Office",1,"EGP","Paper") }
        val a=ops.audit(scope);val b=ops.audit(scope,a.items.last().sequence);val c=ops.audit(scope,b.items.last().sequence)
        assertEquals(65,(a.items+b.items+c.items).map { it.sequence }.toSet().size);assertFalse(c.hasMore)
        reject { helper.writableDatabase.execSQL("DELETE FROM audit") };reject { helper.writableDatabase.execSQL("UPDATE audit SET action='changed'") }
    }
    @Test fun actualVersionOneUpgradePreservesIdsBalancesAndTriggers() {
        store.close();context.deleteDatabase(name)
        val legacy=context.openOrCreateDatabase(name,0,null)
        // Fixture is the exact schema built from the pre-migration implementation, checked in as a test asset.
        val sql=context.assets.open("business-v1.sql").bufferedReader().use { it.readText() }
        sql.split("\n-- statement\n").filter { it.isNotBlank() }.forEach { legacy.execSQL(it.trim()) }
        legacy.version=1;legacy.close()
        helper=BusinessDatabase(context,name);store=BusinessStore(helper);ops=BusinessOperations(store);scope=store.defaultScope()
        assertEquals("legacy-org",scope.organizationId);assertEquals("legacy-sub",store.subscribers(scope).items.single().id)
        assertEquals(700L,store.balance(scope,"legacy-sub"));assertEquals(3,helper.readableDatabase.version)
        ops.addPlan(scope,"p","New","HOTSPOT","EGP",100,10);ops.renew(scope,"i","legacy-sub","p",0,20000)
        assertEquals(800L,store.balance(scope,"legacy-sub"));reject { helper.writableDatabase.execSQL("DELETE FROM ledger") }
    }
}
