package com.fgmachines.mikrotikmanager.business

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import java.util.UUID

@RunWith(AndroidJUnit4::class)
class BusinessTeamTest {
    private val context=InstrumentationRegistry.getInstrumentation().targetContext
    private lateinit var store:BusinessStore;private lateinit var name:String;private lateinit var s:BusinessScope;private lateinit var team:BusinessTeam
    @Before fun setup(){name="team-${UUID.randomUUID()}.db";store=BusinessStore(BusinessDatabase(context,name));s=store.defaultScope();team=BusinessTeam(store)}
    @After fun cleanup(){store.close();context.deleteDatabase(name)}
    private fun member(){team.add(s,"m","Reseller","","RESELLER","EGP",1000)}
    private fun reject(block:()->Unit){var failed=false;try{block()}catch(_:Exception){failed=true};assertTrue(failed)}
    @Test fun walletScopeReplayAndOverdraft(){
        member();team.post(s,"m","d","DEPOSIT",1000,"Deposit");team.post(s,"m","d","DEPOSIT",1000,"Deposit")
        assertEquals(1000L,team.balance(s,"m"));reject{team.post(s,"m","d","DEPOSIT",2000,"Deposit")}
        reject{team.post(s,"m","w","WITHDRAWAL",1001,"Too much")}
        BusinessOperations(store).addBranch(s,"other","Other");reject{team.post(BusinessScope(s.organizationId,"other"),"m","x","DEPOSIT",10,"Wrong branch")}
        team.post(s,"m","w","WITHDRAWAL",400,"Settlement");assertEquals(600L,team.balance(s,"m"))
        reject{team.post(s,"m","r","REVERSAL",0,"Reverse deposit",reversal="d")}
        team.post(s,"m","rw","REVERSAL",0,"Reverse withdrawal",reversal="w");team.post(s,"m","r","REVERSAL",0,"Reverse deposit",reversal="d")
        assertEquals(0L,team.balance(s,"m"));reject{store.helper.writableDatabase.execSQL("DELETE FROM reseller_entries")}
        team.activate(s,"m",false);reject{team.post(s,"m","disabled","DEPOSIT",10,"Disabled")}
    }
    @Test fun commissionSnapshotAndSaleCancellation(){
        member();store.addSubscriber(s,"c","Customer","","OTHER","","EGP")
        val sales=BusinessSales(store);sales.sell(s,"sale","c",listOf(SaleLine("Item",1,1000)),600,PaymentMethod.CASH,"")
        team.post(s,"m","commission","COMMISSION",0,"Sale commission",sale="sale");assertEquals(60L,team.balance(s,"m"))
        reject{team.post(s,"m","duplicate","COMMISSION",0,"Again",sale="sale")};reject{sales.cancel(s,"sale","cancel","Cancel")}
        team.post(s,"m","reverse","REVERSAL",0,"Reverse commission",reversal="commission");sales.cancel(s,"sale","cancel","Cancel")
        assertEquals(0L,team.balance(s,"m"));assertEquals(0L,store.balance(s,"c"))
    }
    @Test fun backupPreservesDisabledResellerAndReversedCommission(){
        commissionSnapshotAndSaleCancellation();team.activate(s,"m",false)
        val password="Wallet backup password".toCharArray();val bytes=BusinessBackup(store).export(password)
        val otherName="team-restore-${UUID.randomUUID()}.db";val other=BusinessStore(BusinessDatabase(context,otherName))
        try { BusinessBackup(other).restore(bytes,password);val t=BusinessTeam(other);assertFalse(t.members(s).single().active);assertEquals(2,t.entries(s,"m").size);assertEquals(0L,t.balance(s,"m")) }
        finally{other.close();context.deleteDatabase(otherName);password.fill('\u0000')}
    }
    @Test fun versionThreeUpgradeKeepsExistingBalances(){
        store.close();context.deleteDatabase(name);val db=context.openOrCreateDatabase(name,0,null)
        context.assets.open("business-v1.sql").bufferedReader().use{it.readText()}.split("\n-- statement\n").filter{it.isNotBlank()}.forEach{db.execSQL(it.trim())}
        BusinessSchemaV2.install(db);BusinessSchemaV3.install(db);db.version=3;db.close()
        store=BusinessStore(BusinessDatabase(context,name));s=store.defaultScope();team=BusinessTeam(store)
        assertEquals(700L,store.balance(s,"legacy-sub"));member();assertEquals(1,team.members(s).size);assertEquals(5,store.helper.readableDatabase.version)
    }
}
