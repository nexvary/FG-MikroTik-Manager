package com.fgmachines.mikrotikmanager.business

import android.os.SystemClock
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.fgmachines.mikrotikmanager.network.RouterOsTransport
import kotlinx.coroutines.runBlocking
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import java.util.UUID

@RunWith(AndroidJUnit4::class)
class BusinessIdentityTest {
    private val context=InstrumentationRegistry.getInstrumentation().targetContext
    private lateinit var name:String;private lateinit var store:BusinessStore;private lateinit var scope:BusinessScope
    private lateinit var identity:BusinessIdentity
    private var wall=System.currentTimeMillis();private var elapsed=SystemClock.elapsedRealtime()
    private val password="Test password 123"
    @Before fun setup(){name="identity-${UUID.randomUUID()}.db";store=BusinessStore(BusinessDatabase(context,name));scope=store.defaultScope();identity=BusinessIdentity(store,{wall},{elapsed})}
    @After fun cleanup(){identity.lock();store.close();context.deleteDatabase(name)}
    private fun reject(block:()->Unit){var failed=false;try{block()}catch(_:Exception){failed=true};assertTrue("Unauthorized operation was accepted",failed)}
    private fun member(id:String,role:String,s:BusinessScope=scope){BusinessTeam(store).add(s,id,id,"",role,"EGP",0)}
    private fun credential(id:String,role:String,s:BusinessScope=scope){member(id,role,s);identity.create(s,id,"login.$id",password.toCharArray())}
    private fun owner(){identity.login("owner",password.toCharArray())}

    @Test fun enrollmentRetainsMoneyAndReadOnlyCannotMutateEvenViaServices() {
        store.addSubscriber(scope,"sub","Customer","","OTHER","","EGP");store.post(scope,"sub","charge",LedgerKind.CHARGE,100,"Charge")
        val chars=password.toCharArray();identity.bootstrap(chars);assertTrue(chars.all{it=='\u0000'})
        assertEquals(100L,store.balance(scope,"sub"));credential("reader","READ_ONLY")
        val rows=store.helper.readableDatabase.rawQuery("SELECT salt,verifier FROM local_accounts",null).use{c->buildList{while(c.moveToNext())add(c.getString(0)+c.getString(1))}}
        assertTrue(rows.none{password in it});assertNotEquals(rows[0],rows[1])
        identity.login("login.reader",password.toCharArray());assertEquals(100L,store.balance(scope,"sub"))
        reject{store.post(scope,"sub","blocked",LedgerKind.PAYMENT,20,"Denied")}
        reject{BusinessOperations(store).addPlan(scope,"plan","No","OTHER","EGP",100,1)}
        reject{BusinessTeam(store).add(scope,"no","No","","ADMIN","EGP",0)}
        reject{BusinessTransfer(store).import(scope,BusinessTransfer.TEMPLATE)}
        reject{BusinessBackup(store).export(password.toCharArray())}
        reject{identity.create(scope,"reader","escalation",password.toCharArray())}
        assertEquals(100L,store.balance(scope,"sub"));assertEquals(1,store.ledger(scope,"sub").items.size)
    }
    @Test fun cashierIsBranchBoundAndCannotReverseOrConfigure() {
        val ops=BusinessOperations(store);ops.addBranch(scope,"other","Other");val other=BusinessScope(scope.organizationId,"other")
        store.addSubscriber(scope,"sub","Main","","OTHER","","EGP");store.addSubscriber(other,"otherSub","Other","","OTHER","","EGP")
        identity.bootstrap(password.toCharArray());credential("cashier","CASHIER")
        identity.login("login.cashier",password.toCharArray())
        store.post(scope,"sub","cash",LedgerKind.PAYMENT,20,"Received")
        reject{store.subscriber(other,"otherSub")};reject{store.post(other,"otherSub","cross",LedgerKind.CHARGE,20,"Denied")}
        reject{ops.selectBranch(scope,"other")};assertEquals(listOf(scope.branchId),ops.branches(scope).map{it.id})
        reject{store.reverse(scope,"sub","cash","reverse","Denied")};reject{ops.expense(scope,"expense","No",10,"EGP","No")}
        assertEquals(-20L,store.balance(scope,"sub"))
        val actor=identity.principal()!!.id
        assertEquals(actor,ops.audit(scope).items.first{it.entity=="ledger"}.actor)
    }
    @Test fun resellerCanReadOnlyOwnWalletAndDisabledMembershipRevokesSession() {
        member("first","RESELLER");member("second","RESELLER");BusinessTeam(store).post(scope,"first","deposit","DEPOSIT",500,"Deposit")
        identity.bootstrap(password.toCharArray());identity.create(scope,"first","reseller.first",password.toCharArray())
        identity.login("reseller.first",password.toCharArray());val team=BusinessTeam(store)
        assertEquals(listOf("first"),team.members(scope).map{it.id});assertEquals(500L,team.balance(scope,"first"))
        reject{team.balance(scope,"second")};reject{team.post(scope,"first","wrong","DEPOSIT",100,"Not permitted")}
        reject{store.subscribers(scope)}
        // A separate authorized session disables this membership; a stale session is rejected on revalidation.
        val stale=IdentitySessions.sessions[store.helper.readableDatabase.path]!!
        owner();team.activate(scope,"first",false)
        IdentitySessions.sessions[store.helper.readableDatabase.path]=stale
        assertNull(identity.principal());reject{team.balance(scope,"first")}
        reject{identity.login("reseller.first",password.toCharArray())}
    }
    @Test fun throttlePersistsAndExpiryAndPasswordResetInvalidateSessions() {
        identity.bootstrap(password.toCharArray());credential("reader","READ_ONLY");identity.lock()
        repeat(5){reject{identity.login("nonexistent","Incorrect password".toCharArray())}}
        store.close();store=BusinessStore(BusinessDatabase(context,name));identity=BusinessIdentity(store,{wall},{elapsed})
        reject{owner()};wall+=30001;owner()
        identity.login("login.reader",password.toCharArray());val old=IdentitySessions.sessions[store.helper.readableDatabase.path]!!
        owner();val id=identity.accounts().first{it.username=="login.reader"}.id
        identity.reset(id,"Different password 456".toCharArray())
        IdentitySessions.sessions[store.helper.readableDatabase.path]=old;assertNull(identity.principal())
        reject{identity.login("login.reader",password.toCharArray())}
        identity.login("login.reader","Different password 456".toCharArray());elapsed+=15*60*1000L+1;assertNull(identity.principal())
        reject{store.subscribers(scope)}
    }
    @Test fun credentialsStayOutOfBackupAndVersionFourUpgradePreservesAudit() {
        store.addSubscriber(scope,"sub","Keep","","OTHER","","EGP");identity.bootstrap(password.toCharArray())
        val bytes=BusinessBackup(store).export(password.toCharArray());val clear=BusinessBackupCipher.decrypt(bytes,password.toCharArray())
        val payload=String(clear);clear.fill(0);assertFalse(org.json.JSONObject(payload).getJSONObject("tables").has("local_accounts"));assertFalse(payload.contains("verifier"));assertFalse(payload.contains(password))
        reject{BusinessBackup(store).restore(bytes,password.toCharArray())}
        identity.lock();store.close();context.deleteDatabase(name)
        val db=context.openOrCreateDatabase(name,0,null)
        context.assets.open("business-v1.sql").bufferedReader().use{it.readText()}.split("\n-- statement\n").filter{it.isNotBlank()}.forEach{db.execSQL(it.trim())}
        BusinessSchemaV2.install(db);BusinessSchemaV3.install(db);BusinessSchemaV4.install(db);db.version=4;db.close()
        store=BusinessStore(BusinessDatabase(context,name));identity=BusinessIdentity(store,{wall},{elapsed});scope=store.defaultScope()
        assertFalse(identity.enabled());assertEquals(700L,store.balance(scope,"legacy-sub"));assertEquals(6,store.helper.readableDatabase.version)
        store.post(scope,"legacy-sub","new",LedgerKind.PAYMENT,20,"Still usable")
        assertEquals("local-app",BusinessOperations(store).audit(scope).items.first().actor)
    }
    @Test fun routerTransportDeniesCashierAndAuditsCommandsWithoutPasswords()=runBlocking {
        identity.bootstrap(password.toCharArray());credential("cashier","CASHIER");credential("tech","TECHNICIAN")
        var calls=0
        val transport=object:RouterOsTransport {
            override suspend fun read(menu:String):List<Map<String,String>> {calls++;return emptyList()}
            override suspend fun create(menu:String,attributes:Map<String,String>):List<Map<String,String>> {calls++;return emptyList()}
            override suspend fun execute(command:String,attributes:Map<String,String>):List<Map<String,String>> {calls++;return emptyList()}
            override fun close(){}
        }
        val guarded=BusinessAuthorizedTransport(transport,store)
        identity.login("login.cashier",password.toCharArray())
        var denied=false;try{guarded.execute("/user/add",mapOf("password" to "Never in audit"))}catch(_:Exception){denied=true}
        assertTrue(denied);assertEquals(0,calls)
        identity.login("login.tech",password.toCharArray())
        guarded.execute("/user/add",mapOf("password" to "Never in audit"));assertEquals(1,calls)
        val entries=BusinessOperations(store).audit(scope).items.filter{it.entity=="router_commands"}
        assertEquals(2,entries.size);assertTrue(entries.all{it.actor==identity.principal()!!.id});assertTrue(entries.none{"Never in audit" in it.toString()})
        assertTrue(entries.any{it.action=="PENDING:/user/add"});assertTrue(entries.any{it.action=="ACKNOWLEDGED:/user/add"})
    }
}
