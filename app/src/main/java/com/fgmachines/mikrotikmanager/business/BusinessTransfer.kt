package com.fgmachines.mikrotikmanager.business

import android.content.ContentValues
import java.io.Writer
import java.security.MessageDigest
import java.util.UUID

data class SubscriberImportRow(val name: String,val phone: String,val service: String,val account: String,val currency: String)
data class SubscriberImportPreview(val digest: String,val rows: List<SubscriberImportRow>,val errors: List<String>)
class BusinessTransfer(private val store: BusinessStore) {
    fun preview(scope: BusinessScope,text: String): SubscriberImportPreview {
        store.authorize(scope,BusinessPermission.IMPORT)

        val digest=MessageDigest.getInstance("SHA-256").digest(text.toByteArray(Charsets.UTF_8)).joinToString("") { "%02x".format(it) }
        val parsed=BusinessCsv.parse(text)
        require(parsed.firstOrNull()==listOf("name","phone","service","account","currency")) { "CSV_HEADER" }
        require(parsed.size>1) { "EMPTY_IMPORT" }
        val rows=mutableListOf<SubscriberImportRow>();val errors=mutableListOf<String>();val accounts=mutableSetOf<String>()
        parsed.drop(1).forEachIndexed { index,c ->
            val row=runCatching {
                require(c.size==5)
                val r=SubscriberImportRow(businessText(c[0],120,true),businessText(c[1],40),c[2].trim(),businessText(c[3],120),c[4].trim())
                require(r.service in listOf("HOTSPOT","PPPOE","OTHER") && r.currency in BusinessMoney.currencies)
                if(r.account.isNotEmpty()) {
                    require(accounts.add(r.service+"\u0000"+r.account))
                    val exists=store.helper.readableDatabase.rawQuery("SELECT 1 FROM subscribers WHERE organization_id=? AND branch_id=? AND service=? AND account=? LIMIT 1",arrayOf(scope.organizationId,scope.branchId,r.service,r.account)).use { it.moveToFirst() }
                    require(!exists)
                };r
            }.getOrNull()
            if(row==null) errors+="${index+2}" else rows+=row
        }
        return SubscriberImportPreview(digest,rows,errors)
    }
    fun import(scope: BusinessScope,text: String): Int=store.transaction { db ->
        store.authorize(scope,BusinessPermission.IMPORT)

        val digest=MessageDigest.getInstance("SHA-256").digest(text.toByteArray(Charsets.UTF_8)).joinToString("") { "%02x".format(it) }
        val batch=UUID.nameUUIDFromBytes((scope.organizationId+scope.branchId+digest).toByteArray()).toString()
        db.rawQuery("SELECT row_count FROM import_batches WHERE id=? AND organization_id=? AND branch_id=?",arrayOf(batch,scope.organizationId,scope.branchId)).use { if(it.moveToFirst()) return@transaction it.getInt(0) }
        // Revalidate under the write transaction: preview cannot authorize stale conflicts.
        val p=preview(scope,text);require(p.errors.isEmpty()) { "IMPORT_CONFLICT" }
        p.rows.forEachIndexed { i,r -> store.addSubscriber(scope,UUID.nameUUIDFromBytes((batch+":"+i).toByteArray()).toString(),r.name,r.phone,r.service,r.account,r.currency) }
        db.insertOrThrow("import_batches",null,ContentValues().apply { put("id",batch);put("organization_id",scope.organizationId);put("branch_id",scope.branchId);put("digest",digest);put("row_count",p.rows.size);put("created_at",System.currentTimeMillis()) });p.rows.size
    }
    /** Stream from one SQLite snapshot; no full history materialization or mixed-currency sums. */
    fun export(scope: BusinessScope,from: Long,until: Long,writer: Writer) = store.transaction { db ->
        store.authorize(scope,BusinessPermission.EXPORT)

        require(from<until) { "INVALID_DATE" }
        writer.write("\uFEFF")
        writer.write(BusinessCsv.line(listOf("type","id","subscriber_or_category","kind","amount_minor","currency","description","created_at_ms","reversal_of","payment_method","payment_reference")))
        val sql="""SELECT 'ledger',l.id,s.name,l.kind,l.amount_minor,l.currency,l.note,l.created_at,l.reversal_of,p.method,p.reference FROM ledger l LEFT JOIN payment_details p ON p.ledger_id=COALESCE(l.reversal_of,l.id) JOIN subscribers s ON s.id=l.subscriber_id
            WHERE l.organization_id=? AND l.branch_id=? AND l.created_at>=? AND l.created_at<?
            UNION ALL SELECT 'expense',id,category,CASE WHEN reversal_of IS NULL THEN 'EXPENSE' ELSE 'REVERSAL' END,amount_minor,currency,note,created_at,reversal_of,NULL,NULL
            FROM expenses WHERE organization_id=? AND branch_id=? AND created_at>=? AND created_at<? ORDER BY 8,2"""
        val a=arrayOf(scope.organizationId,scope.branchId,from.toString(),until.toString())
        db.rawQuery(sql,a+a).use { c -> while(c.moveToNext()) writer.write(BusinessCsv.line((0 until c.columnCount).map { if(c.isNull(it)) "" else c.getString(it) },setOf(4,7))) }
        writer.flush()
    }
    companion object { const val TEMPLATE="name,phone,service,account,currency\r\nExample subscriber,,HOTSPOT,example-account,EGP\r\n" }
}
