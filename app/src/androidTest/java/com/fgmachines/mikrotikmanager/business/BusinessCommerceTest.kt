package com.fgmachines.mikrotikmanager.business

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import java.util.UUID
import java.time.LocalDate

@RunWith(AndroidJUnit4::class)
class BusinessCommerceTest {
    private val context=InstrumentationRegistry.getInstrumentation().targetContext
    private lateinit var store: BusinessStore;private lateinit var name: String;private lateinit var scope: BusinessScope
    @Before fun setup(){name="commerce-${UUID.randomUUID()}.db";store=BusinessStore(BusinessDatabase(context,name));scope=store.defaultScope()}
    @After fun cleanup(){store.close();context.deleteDatabase(name)}
    private fun sub()=store.addSubscriber(scope,"s","Customer <script>","","HOTSPOT","demo","EGP")
    private fun reject(block:()->Unit){var failed=false;try{block()}catch(_:Exception){failed=true};assertTrue(failed)}
    @Test fun paymentMethodsReplayExportAndSaleCancellation(){
        sub();val sales=BusinessSales(store);val lines=listOf(SaleLine("Cable",2,150),SaleLine("Setup",1,700))
        sales.sell(scope,"sale","s",lines,400,PaymentMethod.INSTAPAY,"ref-1");sales.sell(scope,"sale","s",lines,400,PaymentMethod.INSTAPAY,"ref-1")
        assertEquals(600L,store.balance(scope,"s"));assertEquals(1,sales.page(scope).items.size)
        reject{sales.sell(scope,"sale","s",lines,400,PaymentMethod.BANK,"ref-1")}
        reject{store.reverse(scope,"s","sale:c","manual","Wrong path")}
        val html=BusinessReceipts(store).html(scope,"sale",true,true)
        assertTrue(html.contains("&lt;script&gt;"));assertFalse(html.contains("Customer <script>"));assertTrue(html.contains("INSTAPAY"))
        val out=java.io.StringWriter();BusinessTransfer(store).export(scope,0,Long.MAX_VALUE,out);assertTrue(out.toString().contains("INSTAPAY"))
        sales.cancel(scope,"sale","cancel","Correction");sales.cancel(scope,"sale","cancel","Correction");assertEquals(0L,store.balance(scope,"s"))
    }
    @Test fun routerImportLinksExistingAndRejectsCrossBranchDuplicates(){
        sub();val ns=BusinessNetworkStore(store);val a=NetworkAccount("*1","demo","HOTSPOT","default",false)
        val c=NetworkCatalog("fingerprint","Demo router",listOf(a),listOf("default"),emptyList())
        assertEquals(1,ns.importAccounts(scope,c,"EGP",listOf(a)));assertEquals(0,ns.importAccounts(scope,c,"EGP",listOf(a)))
        assertEquals(1,store.subscribers(scope).items.size);assertEquals("s",ns.binding(scope,"s")!!.subscriber)
        BusinessOperations(store).addBranch(scope,"b","Second")
        reject{ns.importAccounts(BusinessScope(scope.organizationId,"b"),c,"EGP",listOf(a))}
        reject{ns.importAccounts(scope,c.copy(accounts=listOf(a.copy(id="*2"))),"EGP",listOf(a.copy(id="*2")))}
    }
    @Test fun networkIntentIsDurableAndInvoiceLockedUntilSuspended(){
        sub();val ops=BusinessOperations(store);ops.addPlan(scope,"p","Plan","HOTSPOT","EGP",500,30)
        val inv=ops.renew(scope,"i","s","p",100,method=PaymentMethod.VODAFONE_CASH,reference="r")
        val ns=BusinessNetworkStore(store);val a=NetworkAccount("*1","demo","HOTSPOT","default",false)
        ns.importAccounts(scope,NetworkCatalog("fp","Router",listOf(a),listOf("default"),emptyList()),"EGP",listOf(a))
        val b=ns.binding(scope,"s")!!;val target=NetworkTarget("fp","*1","demo","HOTSPOT",b.id,"default",inv.end,1000)
        ns.save(scope,"i",target);ns.save(scope,"i",target);ns.result(scope,"i","REVIEW")
        reject{ops.cancelInvoice(scope,"i","v","Needs network suspension")}
        store.close();store=BusinessStore(BusinessDatabase(context,name));val reopened=BusinessNetworkStore(store)
        assertEquals("REVIEW",reopened.job(scope,"i")!!.state)
        reopened.result(scope,"i","SUSPENDED");BusinessOperations(store).cancelInvoice(scope,"i","v","Suspended first")
        reject{reopened.validateLatest(scope,"i")}
        assertEquals(0L,store.balance(scope,"s"))
    }
    @Test fun backupV3IncludesSalesAndOlderV2CanRestore(){
        sub();BusinessSales(store).sell(scope,"sale","s",listOf(SaleLine("Item",1,500)),100,PaymentMethod.BANK,"bank-ref")
        val password="Long commerce password!".toCharArray();val bytes=BusinessBackup(store).export(password)
        val otherName="restore-${UUID.randomUUID()}.db";val other=BusinessStore(BusinessDatabase(context,otherName))
        try{BusinessBackup(other).restore(bytes,password);assertEquals(500L,BusinessSales(other).sale(scope,"sale")!!.total);assertTrue(BusinessReceipts(other).html(scope,"sale",true,false).contains("bank-ref"))}finally{other.close();context.deleteDatabase(otherName)}
        // A v2-shaped snapshot has no v3 data; use an independent empty legacy dataset.
        val freshName="legacy-${UUID.randomUUID()}.db";val fresh=BusinessStore(BusinessDatabase(context,freshName))
        try{val clear=BusinessBackupCipher.decrypt(BusinessBackup(fresh).export(password),password);val root=org.json.JSONObject(String(clear));root.put("schema",2);BusinessSchemaV3.tables.forEach{root.getJSONObject("tables").remove(it)}
            val v2=BusinessBackupCipher.encrypt(root.toString().toByteArray(),password);BusinessBackup(fresh).restore(v2,password)
            assertEquals(3,fresh.helper.readableDatabase.version)
        }finally{fresh.close();context.deleteDatabase(freshName);password.fill('\u0000')}
    }
    @Test fun versionTwoUpgradePreservesExistingFinancialHistory(){
        store.close();context.deleteDatabase(name)
        val db=context.openOrCreateDatabase(name,0,null)
        context.assets.open("business-v1.sql").bufferedReader().use{it.readText()}.split("\n-- statement\n").filter{it.isNotBlank()}.forEach{db.execSQL(it.trim())}
        BusinessSchemaV2.install(db);db.version=2;db.close()
        store=BusinessStore(BusinessDatabase(context,name));scope=store.defaultScope();assertEquals(700L,store.balance(scope,"legacy-sub"));assertEquals(3,store.helper.readableDatabase.version)
        store.post(scope,"legacy-sub","p",LedgerKind.PAYMENT,200,"Received",PaymentMethod.BANK,"R");assertEquals(500L,store.balance(scope,"legacy-sub"))
    }
}
