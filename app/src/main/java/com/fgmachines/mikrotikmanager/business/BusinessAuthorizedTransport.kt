package com.fgmachines.mikrotikmanager.business

import com.fgmachines.mikrotikmanager.network.RouterOsTransport
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.UUID

/** Every physical transport call is authorized, including advanced/direct RouterOS tools. */
class BusinessAuthorizedTransport(private val delegate:RouterOsTransport,private val store:BusinessStore):RouterOsTransport {
    @Volatile private var closed=false
    private data class Attempt(val id:String,val scope:BusinessScope,val actor:String,val command:String)
    private fun scope():BusinessScope {check(!closed){"SESSION_CLOSED"};val s=store.defaultScope();store.authorize(s,BusinessPermission.ROUTER);return s}
    private fun attempt(command:String):Attempt = store.transaction { db->
        val s=scope();val actor=store.identity.principal()?.id ?: "local-app"
        val safe=command.takeIf{it.matches(Regex("[A-Za-z0-9/_-]{1,160}"))} ?: "command"
        val a=Attempt(UUID.randomUUID().toString(),s,actor,safe)
        db.execSQL("INSERT INTO audit(organization_id,branch_id,entity,entity_id,action,actor,created_at) VALUES(?,?,'router_commands',?,?,?,?)",arrayOf(s.organizationId,s.branchId,a.id,"PENDING:$safe",actor,System.currentTimeMillis()))
        a
    }
    /** Finish only the already-authorized attempt, even if sign-in expires during the request. */
    private fun finish(a:Attempt,status:String) {
        if(closed)return // PENDING remains explicit if disposal interrupted the request.
        val db=store.helper.writableDatabase;db.beginTransaction()
        try{
            db.execSQL("INSERT INTO audit(organization_id,branch_id,entity,entity_id,action,actor,created_at) VALUES(?,?,'router_commands',?,?,?,?)",arrayOf(a.scope.organizationId,a.scope.branchId,a.id,"$status:${a.command}",a.actor,System.currentTimeMillis()))
            db.setTransactionSuccessful()
        }finally{db.endTransaction()}
    }
    override suspend fun read(menu:String)=withContext(Dispatchers.IO){scope();delegate.read(menu)}
    override suspend fun create(menu:String,attributes:Map<String,String>)=withContext(Dispatchers.IO){
        val a=attempt("$menu/add")
        try{delegate.create(menu,attributes).also{finish(a,"ACKNOWLEDGED")}}catch(t:Throwable){finish(a,"REVIEW");throw t}
    }
    override suspend fun execute(command:String,attributes:Map<String,String>)=withContext(Dispatchers.IO){
        scope()
        val readOnly=command.substringAfterLast('/') in setOf("print","get","monitor","ping","traceroute")
        if(readOnly)delegate.execute(command,attributes)
        else{
            val a=attempt(command)
            try{delegate.execute(command,attributes).also{finish(a,"ACKNOWLEDGED")}}catch(t:Throwable){finish(a,"REVIEW");throw t}
        }
    }
    override fun close(){closed=true;try{delegate.close()}finally{store.close()}}
}
