package com.fgmachines.mikrotikmanager.business

import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.HttpUrl.Companion.toHttpUrl
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

/** Explicit HTTPS business synchronization. Credentials and tokens are request-local. */
class BusinessCloud(private val store:BusinessStore,private val httpFactory:()->OkHttpClient={OkHttpClient.Builder().connectTimeout(10,TimeUnit.SECONDS).readTimeout(30,TimeUnit.SECONDS).callTimeout(60,TimeUnit.SECONDS).followRedirects(false).followSslRedirects(false).retryOnConnectionFailure(false).build()}) {

    data class SyncResult(val sent:Int,val imported:Int,val revision:Long)
    fun sync(url:String,username:String,password:String,joinEmpty:Boolean=false):SyncResult {
        store.authorize(null,BusinessPermission.BRANCHES)
        val base=url.trim().toHttpUrl()
        require(base.isHttps && base.username.isEmpty() && base.password.isEmpty() && base.query==null && base.fragment==null && base.encodedPath=="/"){"HTTPS_ORIGIN_REQUIRED"}
        val client=httpFactory()
        fun call(path:String,body:Any?=null,token:String?=null):JSONObject {
            val request=Request.Builder().url(base.newBuilder().encodedPath(path).build())
            if(token!=null)request.header("Authorization","Bearer $token")
            if(body!=null)request.post(body.toString().toRequestBody("application/json".toMediaType()))
            client.newCall(request.build()).execute().use {response->
                require(response.code!=409){"CLOUD_REVISION_CONFLICT"}
                require(response.code!=401 && response.code!=403){"CLOUD_ACCESS_DENIED"}
                require(response.isSuccessful){"CLOUD_REQUEST_FAILED"}
                return JSONObject(String(BusinessBackupCipher.readBounded(response.body!!.byteStream(),22*1024*1024),Charsets.UTF_8))
            }
        }
        try {
            val token=call("/v1/login",JSONObject().put("username",username).put("password",password)).getString("token")
            require(token.length in 20..100){"CLOUD_INVALID_TOKEN"}
            val identity=call("/v1/identity",token=token)
            require(identity.getString("role")=="owner"){"CLOUD_OWNER_REQUIRED"}
            val replica=BusinessReplica(store);val remote=call("/v1/business/sync",token=token);val remoteRecords=remote.getJSONArray("records")
            val own=store.defaultScope();val tenant=identity.getString("tenant");val branch=identity.getString("branch")
            if(own.organizationId!=tenant || own.branchId!=branch){require(joinEmpty){"CLOUD_SCOPE_MISMATCH"};replica.join(tenant,branch,remoteRecords)}
            val local=replica.snapshot();val merged=replica.combine(local,remoteRecords,base.toString())
            replica.validate(merged,base.toString(),local)
            // Concurrent local changes or another phone revision are explicit conflicts, never last-writer-wins.
            require(BusinessReplica.canonical(replica.snapshot())==BusinessReplica.canonical(local)){"CLOUD_LOCAL_CHANGED"}
            val acknowledged=call("/v1/business/sync",JSONObject().put("revision",remote.getLong("revision")).put("device",replica.device()).put("records",merged),token)
            require(acknowledged.getBoolean("accepted")){"CLOUD_NOT_CONFIRMED"}
            val imported=replica.merge(merged,base.toString(),local)
            val known=BusinessReplica.index(remoteRecords)
            val sent=BusinessReplica.index(merged).count{(key,r)->known[key]?.let{BusinessReplica.canonical(it)!=BusinessReplica.canonical(r)} ?: true}
            return SyncResult(sent,imported,acknowledged.getLong("revision"))
        }finally{client.dispatcher.cancelAll();client.connectionPool.evictAll();client.dispatcher.executorService.shutdown()}
    }

    data class Result(val count:Int,val remaining:Boolean,val statuses:String)
    fun upload(url:String,username:String,password:String):Result {
        store.authorize(null,BusinessPermission.BRANCHES)
        val base=url.trim().toHttpUrl()
        require(base.isHttps && base.username.isEmpty() && base.password.isEmpty() && base.query==null && base.fragment==null && base.encodedPath=="/"){"HTTPS_ORIGIN_REQUIRED"}
        val client=OkHttpClient.Builder().connectTimeout(10,TimeUnit.SECONDS).readTimeout(20,TimeUnit.SECONDS).callTimeout(30,TimeUnit.SECONDS).followRedirects(false).followSslRedirects(false).build()
        fun call(path:String,body:Any?=null,token:String?=null):JSONObject {
            val request=Request.Builder().url(base.newBuilder().encodedPath(path).build())
            if(token!=null)request.header("Authorization","Bearer $token")
            if(body!=null)request.post(body.toString().toRequestBody("application/json".toMediaType()))
            client.newCall(request.build()).execute().use { response->
                require(response.isSuccessful){"CLOUD_REQUEST_FAILED"}
                val source=response.body ?: error("CLOUD_RESPONSE_MISSING")
                val data=BusinessBackupCipher.readBounded(source.byteStream(),1024*1024)
                return JSONObject(String(data,Charsets.UTF_8))
            }
        }
        try {
            val login=call("/v1/login",JSONObject().put("username",username).put("password",password))
            val token=login.getString("token")
            val identity=call("/v1/identity",token=token)
            val scope=store.defaultScope()
            require(identity.getString("tenant")==scope.organizationId && identity.getString("branch")==scope.branchId){"CLOUD_SCOPE_MISMATCH"}
            // Progress is derived from server acknowledgements; no local cursor can skip lost requests.
            val known=mutableMapOf<String,JSONObject>();val states=mutableMapOf<String,Int>();var after=0L;var pages=0
            while(true) {
                require(pages++<100){"CLOUD_HISTORY_LIMIT"}
                val response=client.newCall(Request.Builder().url(base.newBuilder().encodedPath("/v1/events").addQueryParameter("after",after.toString()).build()).header("Authorization","Bearer $token").build()).execute().use { r->
                    require(r.isSuccessful){"CLOUD_REQUEST_FAILED"};JSONObject(String(BusinessBackupCipher.readBounded(r.body!!.byteStream(),1024*1024),Charsets.UTF_8)).getJSONArray("events")
                }
                if(response.length()==0)break
                for(i in 0 until response.length()) {val e=response.getJSONObject(i);val next=e.getLong("seq");require(next>after){"INVALID_CLOUD_CURSOR"};after=next;if(e.getString("kind")=="ledger.append"){known[e.getString("event_id")]=e.getJSONObject("body");val status=e.getString("state");states[status]=(states[status] ?: 0)+1}}
            }
            val events=JSONArray();var remaining=false
            store.transaction { db->
                store.authorize(scope,BusinessPermission.BRANCHES)
                val device=db.rawQuery("SELECT device FROM audit_installation WHERE id=1",null).use{it.moveToFirst();it.getString(0)}
                db.rawQuery("SELECT id,subscriber_id,currency,amount_minor,reversal_of,note FROM ledger WHERE organization_id=? AND branch_id=? ORDER BY sequence",arrayOf(scope.organizationId,scope.branchId)).use { c->while(c.moveToNext()) {
                    val body=JSONObject().put("subscriber",c.getString(1)).put("currency",c.getString(2)).put("amount_minor",c.getLong(3)).put("reversal_of",if(c.isNull(4))JSONObject.NULL else c.getString(4)).put("note",c.getString(5))
                    known[c.getString(0)]?.let { old ->
                        require(listOf("subscriber","currency","amount_minor","reversal_of","note").all{old.get(it).toString()==body.get(it).toString()}){"CLOUD_LEDGER_CONFLICT"}
                    }
                    if(c.getString(0) in known)continue
                    if(events.length()>=100){remaining=true;break}
                    events.put(JSONObject().put("version",1).put("id",c.getString(0)).put("device",device).put("kind","ledger.append").put("body",body))
                } }
            }
            store.authorize(scope,BusinessPermission.BRANCHES)
            if(events.length()==0)return Result(0,false,states.entries.joinToString { "${it.key}: ${it.value}" }.ifBlank{"NO_NEW_ENTRIES"})
            val results=call("/v1/events",events,token).getJSONArray("events")
            return Result(events.length(),remaining,(0 until results.length()).map{results.getJSONObject(it).getString("state")}.groupingBy{it}.eachCount().entries.joinToString { "${it.key}: ${it.value}" })
        } finally {client.dispatcher.cancelAll();client.connectionPool.evictAll();client.dispatcher.executorService.shutdown()}
    }
}
