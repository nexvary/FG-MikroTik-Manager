package com.fgmachines.mikrotikmanager.business

import android.content.ContentValues
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import org.json.JSONArray
import org.json.JSONObject

/** Full branch business replica. Local authentication, device secrets and physical confirmations stay local. */
internal class BusinessReplica(private val store:BusinessStore) {
    companion object {
        val tables=listOf("organizations","branches","subscribers","plans","ledger","invoices","invoice_voids","expenses","import_batches","payment_details","router_bindings","network_jobs","sales","sale_voids","team_members","reseller_entries","audit")
        fun install(db:SQLiteDatabase){
            db.execSQL("CREATE TABLE cloud_audit_ids(sequence INTEGER PRIMARY KEY REFERENCES audit(sequence), external_id TEXT NOT NULL UNIQUE)")
            db.execSQL("CREATE TABLE cloud_baseline(origin TEXT NOT NULL,organization_id TEXT NOT NULL,branch_id TEXT NOT NULL,record_key TEXT NOT NULL,body TEXT NOT NULL,PRIMARY KEY(origin,organization_id,branch_id,record_key))")
        }
        fun canonical(value:Any?):String=when(value){
            null,JSONObject.NULL->"null"
            is JSONObject->value.keys().asSequence().sorted().joinToString(prefix="{",postfix="}"){JSONObject.quote(it)+":"+canonical(value.get(it))}
            is JSONArray->(0 until value.length()).joinToString(prefix="[",postfix="]"){canonical(value.get(it))}
            is String->JSONObject.quote(value)
            is Number-> {require(value is Int || value is Long){"CLOUD_INVALID_NUMBER"};value.toString()}
            else->error("CLOUD_INVALID_VALUE")
        }
        fun key(record:JSONObject)=record.getString("table")+":"+record.getString("id")
        fun index(records:JSONArray):LinkedHashMap<String,JSONObject> {
            require(records.length()<=50000){"CLOUD_LIMIT"}
            val result=linkedMapOf<String,JSONObject>()
            for(i in 0 until records.length()){
                val r=records.getJSONObject(i);require(r.keys().asSequence().toSet()==setOf("table","id","body") && r.getString("table") in tables && r.getString("id").length in 1..120){"CLOUD_INVALID_RECORD"}
                require(result.put(key(r),r)==null){"CLOUD_DUPLICATE"};r.getJSONObject("body")
            };return result
        }
    }
    fun device()=store.helper.readableDatabase.rawQuery("SELECT device FROM audit_installation WHERE id=1",null).use{it.moveToFirst();it.getString(0)}
    private fun row(c:Cursor):JSONObject=JSONObject().apply {for(i in 0 until c.columnCount){val name=c.getColumnName(i);if(name=="sequence")continue;put(name,when(c.getType(i)){Cursor.FIELD_TYPE_NULL->JSONObject.NULL;Cursor.FIELD_TYPE_INTEGER->c.getLong(i);else->c.getString(i)})}}
    fun snapshot():JSONArray=store.transaction { db->
        store.authorize(null,BusinessPermission.BRANCHES);val s=store.defaultScope();val records=JSONArray()
        for(table in tables){
            val sql=when(table){
                "organizations"->"SELECT * FROM organizations WHERE id=?"
                "branches"->"SELECT * FROM branches WHERE organization_id=? AND id=?"
                "payment_details"->"SELECT p.* FROM payment_details p JOIN ledger l ON l.id=p.ledger_id WHERE l.organization_id=? AND l.branch_id=?"
                else->"SELECT * FROM $table WHERE organization_id=? AND branch_id=? ORDER BY rowid"
            }
            val args=if(table=="organizations")arrayOf(s.organizationId)else arrayOf(s.organizationId,s.branchId)
            db.rawQuery(sql,args).use{c->while(c.moveToNext()){
                val body=row(c)
                val id=when(table){
                    "payment_details"->body.getString("ledger_id")
                    "audit"->{
                        val seq=c.getLong(c.getColumnIndexOrThrow("sequence"))
                        val external=db.rawQuery("SELECT external_id FROM cloud_audit_ids WHERE sequence=?",arrayOf(seq.toString())).use{if(it.moveToFirst())it.getString(0)else "audit-"+device()+"-"+seq}
                        db.execSQL("INSERT OR IGNORE INTO cloud_audit_ids VALUES(?,?)",arrayOf(seq,external))
                        val detail=db.rawQuery("SELECT device,before_value,after_value FROM audit_details WHERE sequence=?",arrayOf(seq.toString())).use{if(it.moveToFirst())row(it)else null}
                        body.put("detail",detail ?: JSONObject.NULL);external
                    }
                    else->body.getString("id")
                }
                records.put(JSONObject().put("table",table).put("id",id).put("body",body))
            }}
        }
        require(records.toString().toByteArray(Charsets.UTF_8).size<=BusinessBackupCipher.MAX_BYTES){"CLOUD_LIMIT"};records
    }
    fun combine(local:JSONArray,remote:JSONArray,origin:String):JSONArray {
        val s=store.defaultScope();val out=index(remote);val own=index(local)
        for((key,record) in own){
            val other=out[key]
            if(other==null){out[key]=record;continue}
            if(canonical(record)==canonical(other))continue
            require(record.getString("table")=="team_members"){"CLOUD_RECORD_CONFLICT"}
            val a=record.getJSONObject("body");val b=other.getJSONObject("body")
            require(a.keys().asSequence().toSet()==b.keys().asSequence().toSet() && a.keys().asSequence().filter{it!="active"}.all{canonical(a.get(it))==canonical(b.get(it))}){"CLOUD_RECORD_CONFLICT"}
            val baseline=store.helper.readableDatabase.rawQuery("SELECT body FROM cloud_baseline WHERE origin=? AND organization_id=? AND branch_id=? AND record_key=?",arrayOf(origin,s.organizationId,s.branchId,key)).use{if(it.moveToFirst())JSONObject(it.getString(0))else null}
            require(baseline!=null){"CLOUD_TEAM_CONFLICT"}
            val old=baseline.getJSONObject("body").getInt("active")
            require(a.getInt("active")==old || b.getInt("active")==old){"CLOUD_TEAM_CONFLICT"}
            if(b.getInt("active")==old)out[key]=record
        }
        return JSONArray().apply {out.toSortedMap().values.forEach(::put)}
    }
    fun join(tenant:String,branch:String,records:JSONArray) = store.transaction {db->
        require(!store.identity.enabled()) {"CLOUD_JOIN_BEFORE_ENROLLMENT"}
        require(tenant.length in 1..120 && branch.length in 1..120)
        for(t in tables.filter{it !in listOf("organizations","branches","audit","payment_details")})db.rawQuery("SELECT COUNT(*) FROM $t",null).use{it.moveToFirst();require(it.getLong(0)==0L){"CLOUD_JOIN_NEEDS_EMPTY_STORE"}}
        db.rawQuery("SELECT COUNT(*) FROM branches",null).use{it.moveToFirst();require(it.getInt(0)==1){"CLOUD_JOIN_NEEDS_EMPTY_STORE"}}
        db.rawQuery("SELECT COUNT(*) FROM audit",null).use{it.moveToFirst();require(it.getInt(0)==0){"CLOUD_JOIN_NEEDS_EMPTY_STORE"}}
        val recordsByKey=index(records)
        val organization=recordsByKey["organizations:$tenant"]?.getJSONObject("body") ?: error("CLOUD_SCOPE_MISSING")
        val branchRow=recordsByKey["branches:$branch"]?.getJSONObject("body") ?: error("CLOUD_SCOPE_MISSING")
        require(branchRow.getString("organization_id")==tenant)
        db.execSQL("DELETE FROM settings");db.execSQL("DELETE FROM branches");db.execSQL("DELETE FROM organizations")
        db.insertOrThrow("organizations",null,values(organization));db.insertOrThrow("branches",null,values(branchRow))
        db.execSQL("INSERT INTO settings VALUES(1,?,?)",arrayOf(tenant,branch))
    }
    fun validate(records:JSONArray,origin:String,local:JSONArray) {
        val db=store.helper.writableDatabase;db.beginTransaction()
        try { merge(records,origin,local) } finally { db.endTransaction() } // Intentionally roll back this preview.
    }
    private fun values(row:JSONObject):ContentValues=ContentValues().apply {for(k in row.keys()){when(val v=row.get(k)){JSONObject.NULL->putNull(k);is Int->put(k,v);is Long->put(k,v);is String->put(k,v);else->error("CLOUD_INVALID_FIELD")}}}
    /** All inserts, metadata and validation share one transaction; immutable UPDATE/DELETE guards are never dropped. */
    fun merge(records:JSONArray,origin:String,expectedLocal:JSONArray):Int=store.transaction { db->
        val s=store.defaultScope();store.authorize(s,BusinessPermission.BRANCHES)
        require(canonical(snapshot())==canonical(expectedLocal)){"CLOUD_LOCAL_CHANGED"}
        val incoming=index(records);var added=0
        val local=index(expectedLocal)
        require(local.keys.all{it in incoming}){"CLOUD_RECORD_MISSING"}
        for((key,record) in incoming){
            val table=record.getString("table");val body=record.getJSONObject("body")
            if(body.has("organization_id"))require(body.getString("organization_id")==s.organizationId){"CLOUD_SCOPE_MISMATCH"}
            if(body.has("branch_id"))require(body.getString("branch_id")==s.branchId){"CLOUD_SCOPE_MISMATCH"}
            if(table=="organizations")require(body.getString("id")==s.organizationId)
            if(table=="branches")require(body.getString("id")==s.branchId)
            val columns=db.rawQuery("SELECT * FROM $table LIMIT 0",null).use{it.columnNames.toSet()-"sequence"}
            require(body.keys().asSequence().toSet()==if(table=="audit")columns+"detail" else columns){"CLOUD_INVALID_FIELDS"}
            local[key]?.let{old->
                if(canonical(old)!=canonical(record)) {
                    require(table=="team_members" && (old.getJSONObject("body").keys().asSequence()-sequenceOf("active")).all{canonical(old.getJSONObject("body").get(it))==canonical(body.get(it))}){"CLOUD_RECORD_CONFLICT"}
                }
            }
        }
        // Imported historical wallets may belong to members disabled now. Validate their final history below.
        for(t in BusinessSchemaV4.guards)db.execSQL("DROP TRIGGER $t")
        fun insert(table:String,select:(JSONObject)->Boolean={true}) {
            val rows=incoming.values.filter{it.getString("table")==table && select(it.getJSONObject("body"))}.sortedWith(compareBy<JSONObject>{it.getJSONObject("body").optLong("created_at")}.thenBy{it.getString("id")})
            for(record in rows){
                val key=key(record);val body=record.getJSONObject("body")
                if(local.containsKey(key)){
                    if(table=="team_members" && canonical(local[key])!=canonical(record))db.execSQL("UPDATE team_members SET active=? WHERE organization_id=? AND branch_id=? AND id=?",arrayOf(body.getInt("active"),s.organizationId,s.branchId,body.getString("id")))
                    continue
                }
                if(table=="audit"){
                    val clean=JSONObject(body.toString());clean.remove("detail")
                    val seq=db.insertOrThrow(table,null,values(clean))
                    if(!body.isNull("detail"))db.insertOrThrow("audit_details",null,values(body.getJSONObject("detail")).apply{put("sequence",seq)})
                    db.execSQL("INSERT INTO cloud_audit_ids VALUES(?,?)",arrayOf(seq,record.getString("id")))
                }else {
                    db.insertOrThrow(table,null,values(body))
                    if(table=="network_jobs")db.execSQL("INSERT INTO network_results(job_id,state,created_at) VALUES(?,'REVIEW',?)",arrayOf(body.getString("id"),System.currentTimeMillis()))
                };added++
            }
        }
        for(t in listOf("organizations","branches","subscribers","plans","team_members"))insert(t)
        insert("ledger"){it.isNull("reversal_of")}
        for(t in listOf("invoices","sales","payment_details","router_bindings","network_jobs","import_batches"))insert(t)
        insert("reseller_entries"){it.isNull("reversal_of") && it.getLong("amount_minor")>0}
        insert("reseller_entries"){it.isNull("reversal_of") && it.getLong("amount_minor")<0}
        insert("reseller_entries"){!it.isNull("reversal_of")}
        insert("invoice_voids");insert("sale_voids")
        insert("ledger"){!it.isNull("reversal_of")}
        insert("expenses"){it.isNull("reversal_of")};insert("expenses"){!it.isNull("reversal_of")}
        insert("audit")
        db.rawQuery("PRAGMA foreign_key_check",null).use{require(!it.moveToFirst()){"CLOUD_REFERENCE_CONFLICT"}}
        db.rawQuery("SELECT member_id,SUM(amount_minor) FROM reseller_entries GROUP BY member_id",null).use{while(it.moveToNext())require(it.getLong(1) in 0..BusinessMoney.MAX_MINOR){"CLOUD_WALLET_CONFLICT"}}
        db.rawQuery("""SELECT 1 FROM reseller_entries e JOIN team_members m ON m.id=e.member_id LEFT JOIN sales s ON s.id=e.sale_id WHERE m.role!='RESELLER' OR
            (e.kind='COMMISSION' AND (s.id IS NULL OR s.organization_id!=e.organization_id OR s.branch_id!=e.branch_id OR s.currency!=m.currency OR e.amount_minor!=(s.paid_minor*m.commission_bps)/10000 OR
            (EXISTS(SELECT 1 FROM sale_voids v WHERE v.sale_id=s.id) AND NOT EXISTS(SELECT 1 FROM reseller_entries r WHERE r.reversal_of=e.id)))) LIMIT 1""",null).use{require(!it.moveToFirst()){"CLOUD_COMMISSION_CONFLICT"}}
        for((table,foreign) in listOf("invoices" to "invoice_id","sales" to "sale_id")){
            val voids=if(table=="invoices")"invoice_voids" else "sale_voids"
            db.rawQuery("""SELECT 1 FROM $table i WHERE
                (EXISTS(SELECT 1 FROM $voids v WHERE v.$foreign=i.id) AND (NOT EXISTS(SELECT 1 FROM ledger r WHERE r.reversal_of=i.charge_id) OR (i.payment_id IS NOT NULL AND NOT EXISTS(SELECT 1 FROM ledger r WHERE r.reversal_of=i.payment_id)))) OR
                (NOT EXISTS(SELECT 1 FROM $voids v WHERE v.$foreign=i.id) AND EXISTS(SELECT 1 FROM ledger r WHERE r.reversal_of=i.charge_id OR r.reversal_of=i.payment_id)) LIMIT 1""",null).use{require(!it.moveToFirst()){"CLOUD_PARTIAL_VOID"}}
        }
        db.rawQuery("SELECT items_json,amount_minor FROM sales",null).use{while(it.moveToNext()){
            val lines=BusinessSales.decode(it.getString(0));require(lines.size in 1..30)
            val sum=lines.fold(0L){sum,l->businessText(l.name,120,true);require(l.quantity in 1..10000 && l.unitMinor in 1..BusinessMoney.MAX_MINOR);Math.addExact(sum,Math.multiplyExact(l.unitMinor,l.quantity.toLong()))};require(sum==it.getLong(1)){"CLOUD_SALE_CONFLICT"}
        }}
        BusinessSchemaV4.createGuards(db)
        for((key,record) in incoming)db.execSQL("INSERT OR REPLACE INTO cloud_baseline VALUES(?,?,?,?,?)",arrayOf(origin,s.organizationId,s.branchId,key,record.toString()))
        added
    }
}
