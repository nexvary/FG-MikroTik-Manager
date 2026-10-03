package com.fgmachines.mikrotikmanager.business

import android.database.sqlite.SQLiteDatabase
import android.os.SystemClock
import android.util.Base64
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec

data class LocalPrincipal(val id:String,val username:String,val role:String,val scope:BusinessScope,val member:String?,val revision:Int)
data class LocalAccount(val id:String,val username:String,val role:String,val active:Boolean,val member:String?)
internal data class IdentitySession(val principal:LocalPrincipal,val expires:Long)
internal object IdentitySessions {
    val sessions=ConcurrentHashMap<String,IdentitySession>()
    fun lock(path:String){sessions.remove(path)}
}

/** Password verifiers only. Sessions are process-local and expire after 15 minutes. */
class BusinessIdentity(private val store:BusinessStore,private val wallClock:()->Long=System::currentTimeMillis,private val monotonic:()->Long=SystemClock::elapsedRealtime) {
    private val db get()=store.helper.writableDatabase
    private val path get()=db.path
    fun enabled()=db.rawQuery("SELECT 1 FROM local_accounts WHERE owner=1",null).use{it.moveToFirst()}
    fun lock(){IdentitySessions.lock(path)}
    fun principal():LocalPrincipal? {
        val session=IdentitySessions.sessions[path] ?: return null
        val current=find(session.principal.username)?.first
        if(monotonic()>=session.expires || current!=session.principal) {lock();return null}
        return current
    }
    fun require(s:BusinessScope?,p:BusinessPermission):LocalPrincipal? {
        if(!enabled())return null
        val user=principal() ?: error("LOGIN_REQUIRED")
        require(BusinessAccess.permits(user.role,p) && (s==null || BusinessAccess.sameScope(user.role,user.scope,s))){"ACCESS_DENIED"}
        return user
    }
    fun ownWallet(s:BusinessScope,member:String):Boolean {
        if(!enabled())return false
        val user=principal() ?: error("LOGIN_REQUIRED")
        return user.role=="RESELLER" && user.member==member && user.scope==s
    }
    private fun hash(password:CharArray,salt:ByteArray):ByteArray {
        val spec=PBEKeySpec(password,salt,210000,256)
        return try{SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).encoded}finally{spec.clearPassword()}
    }
    private fun verifier(password:CharArray):Pair<String,String> {
        require(password.size in 12..128){"PASSWORD_LENGTH"}
        val salt=ByteArray(16).also{SecureRandom().nextBytes(it)};val value=hash(password,salt)
        return try{Base64.encodeToString(salt,Base64.NO_WRAP) to Base64.encodeToString(value,Base64.NO_WRAP)}finally{value.fill(0);salt.fill(0)}
    }
    private fun username(name:String)=name.trim().lowercase(java.util.Locale.ROOT).also {
        require(it.matches(Regex("[a-z0-9._-]{3,40}"))){"INVALID_USERNAME"}
    }
    private fun find(username:String):Pair<LocalPrincipal,Pair<String,String>>? = db.rawQuery("""SELECT a.id,a.username,CASE WHEN a.owner=1 THEN 'OWNER' ELSE m.role END,
        a.organization_id,a.branch_id,a.member_id,a.revision,a.salt,a.verifier FROM local_accounts a LEFT JOIN team_members m ON m.id=a.member_id
        WHERE a.username=? AND (a.owner=1 OR (m.active=1 AND m.organization_id=a.organization_id AND m.branch_id=a.branch_id))""",arrayOf(username)).use{c->
        if(!c.moveToFirst())null else LocalPrincipal(c.getString(0),c.getString(1),c.getString(2),BusinessScope(c.getString(3),c.getString(4)),if(c.isNull(5))null else c.getString(5),c.getInt(6)) to (c.getString(7) to c.getString(8))
    }
    private fun <T> transaction(work:(SQLiteDatabase)->T):T {
        db.beginTransaction();try{val result=work(db);db.setTransactionSuccessful();return result}finally{db.endTransaction()}
    }
    private fun audit(d:SQLiteDatabase,s:BusinessScope,id:String,action:String,actor:String) {
        d.execSQL("INSERT INTO audit(organization_id,branch_id,entity,entity_id,action,actor,created_at) VALUES(?,?,'local_accounts',?,?,?,?)",arrayOf(s.organizationId,s.branchId,id,action,actor,wallClock()))
    }
    fun bootstrap(password:CharArray) {
        try {
            val value=verifier(password)
            transaction{d->
                require(!enabled()){"OWNER_EXISTS"}
                val s=d.rawQuery("SELECT organization_id,branch_id FROM settings WHERE id=1",null).use{it.moveToFirst();BusinessScope(it.getString(0),it.getString(1))}
                val id=UUID.randomUUID().toString()
                d.execSQL("INSERT INTO local_accounts(id,username,organization_id,branch_id,member_id,owner,salt,verifier) VALUES(?,'owner',?,?,NULL,1,?,?)",arrayOf(id,s.organizationId,s.branchId,value.first,value.second))
                audit(d,s,id,"OWNER_SETUP",id)
            }
            login("owner",password)
        } finally {password.fill('\u0000')}
    }
    fun login(name:String,password:CharArray) {
        try {
            val normalized=runCatching{username(name)}.getOrDefault("")
            // Rate limit survives process restarts and applies to unknown usernames too.
            val accepted=transaction{d->
                require(enabled()){"OWNER_NOT_CONFIGURED"}
                val throttle=d.rawQuery("SELECT failures,until_ms FROM login_throttle WHERE id=1",null).use{it.moveToFirst();it.getInt(0) to it.getLong(1)}
                require(wallClock()>=throttle.second){"LOGIN_THROTTLED"}
                val record=find(normalized)
                val salt=record?.second?.first?.let{Base64.decode(it,Base64.NO_WRAP)} ?: ByteArray(16)
                val bounded=password.take(128).toCharArray()
                val computed=try{hash(bounded,salt)}finally{bounded.fill('\u0000')}
                val expected=record?.second?.second?.let{Base64.decode(it,Base64.NO_WRAP)} ?: ByteArray(32)
                val valid=try{password.size<=128 && MessageDigest.isEqual(computed,expected) && record!=null}finally{computed.fill(0);expected.fill(0);salt.fill(0)}
                if(!valid) {
                    val failures=if(throttle.second>0)1 else throttle.first+1
                    d.execSQL("UPDATE login_throttle SET failures=?,until_ms=? WHERE id=1",arrayOf(failures,if(failures>=5)wallClock()+30000 else 0L))
                    null
                }else{
                    d.execSQL("UPDATE login_throttle SET failures=0,until_ms=0 WHERE id=1")
                    val user=record!!.first
                    d.execSQL("UPDATE settings SET branch_id=? WHERE id=1 AND organization_id=?",arrayOf(user.scope.branchId,user.scope.organizationId))
                    audit(d,user.scope,user.id,"LOGIN",user.id)
                    user
                }
            }
            require(accepted!=null){"INVALID_LOGIN"}
            IdentitySessions.sessions[path]=IdentitySession(accepted,monotonic()+15*60*1000L)
        }finally{password.fill('\u0000')}
    }
    fun accounts():List<LocalAccount> {
        require(null,BusinessPermission.AUTH)
        return db.rawQuery("SELECT a.id,a.username,CASE WHEN a.owner=1 THEN 'OWNER' ELSE m.role END,CASE WHEN a.owner=1 THEN 1 ELSE m.active END,a.member_id FROM local_accounts a LEFT JOIN team_members m ON m.id=a.member_id ORDER BY a.username",null).use{c->buildList{while(c.moveToNext())add(LocalAccount(c.getString(0),c.getString(1),c.getString(2),c.getInt(3)==1,if(c.isNull(4))null else c.getString(4)))}}
    }
    fun create(s:BusinessScope,member:String,name:String,password:CharArray) {
        try {
            val actor=require(s,BusinessPermission.AUTH) ?: error("OWNER_NOT_CONFIGURED")
            val n=username(name);val v=verifier(password)
            transaction{d->
                require(s,BusinessPermission.AUTH)
                require(d.rawQuery("SELECT 1 FROM team_members WHERE id=? AND organization_id=? AND branch_id=? AND active=1",arrayOf(member,s.organizationId,s.branchId)).use{it.moveToFirst()}){"INACTIVE_MEMBER"}
                val id=UUID.randomUUID().toString()
                d.execSQL("INSERT INTO local_accounts(id,username,organization_id,branch_id,member_id,owner,salt,verifier) VALUES(?,?,?,?,?,0,?,?)",arrayOf(id,n,s.organizationId,s.branchId,member,v.first,v.second))
                audit(d,s,id,"LOGIN_CREATED",actor.id)
            }
        }finally{password.fill('\u0000')}
    }
    fun reset(id:String,password:CharArray) {
        try {
            val actor=require(null,BusinessPermission.AUTH) ?: error("OWNER_NOT_CONFIGURED");val v=verifier(password)
            transaction{d->
                require(null,BusinessPermission.AUTH)
                val s=d.rawQuery("SELECT organization_id,branch_id FROM local_accounts WHERE id=?",arrayOf(id)).use{require(it.moveToFirst()){"ACCOUNT_NOT_FOUND"};BusinessScope(it.getString(0),it.getString(1))}
                d.execSQL("UPDATE local_accounts SET salt=?,verifier=?,revision=revision+1 WHERE id=?",arrayOf(v.first,v.second,id))
                audit(d,s,id,"PASSWORD_RESET",actor.id)
            }
            if(actor.id==id)lock()
        }finally{password.fill('\u0000')}
    }
}
