package com.fgmachines.mikrotikmanager.business

import android.content.ContentValues
import org.json.JSONArray
import org.json.JSONObject

enum class PaymentMethod { CASH,VODAFONE_CASH,INSTAPAY,BANK,OTHER }
data class SaleLine(val name: String,val quantity: Int,val unitMinor: Long)
data class BusinessSale(val sequence: Long,val id: String,val subscriber: String,val customer: String,val lines: List<SaleLine>,val total: Long,val paid: Long,val currency: String,val voided: Boolean)
class BusinessSales(private val store: BusinessStore) {
    private val db get()=store.helper.readableDatabase
    fun sell(s: BusinessScope,id: String,sub: String,lines: List<SaleLine>,paid: Long,method: PaymentMethod,reference: String): BusinessSale=store.transaction { d ->
        store.authorize(s,BusinessPermission.POST)

        require(id.isNotBlank() && id.length<=64 && lines.size in 1..30)
        val normalized=lines.map { it.copy(name=businessText(it.name,120,true)) }
        val total=normalized.fold(0L) { sum,l -> require(l.quantity in 1..10000 && l.unitMinor in 1..BusinessMoney.MAX_MINOR);Math.addExact(sum,Math.multiplyExact(l.unitMinor,l.quantity.toLong())) }
        require(total in 1..BusinessMoney.MAX_MINOR && paid in 0..total) { "INVALID_AMOUNT" }
        sale(s,id)?.let { require(it.subscriber==sub && it.lines==normalized && it.paid==paid) { "IDEMPOTENCY_CONFLICT" }
            if(paid>0) PaymentDetails.requireMatch(d,"$id:p",method,reference);return@transaction it }
        val customer=store.subscriber(s,sub)
        store.post(s,sub,"$id:c",LedgerKind.CHARGE,total,"Sale / بيع: $id")
        if(paid>0) store.post(s,sub,"$id:p",LedgerKind.PAYMENT,paid,"Sale receipt / تحصيل بيع: $id",method,reference)
        d.insertOrThrow("sales",null,ContentValues().apply {put("id",id);put("organization_id",s.organizationId);put("branch_id",s.branchId);put("subscriber_id",sub);put("customer",customer.name);put("items_json",encode(normalized));put("amount_minor",total);put("paid_minor",paid);put("currency",customer.currency);put("charge_id","$id:c");if(paid>0)put("payment_id","$id:p") else putNull("payment_id");put("created_at",System.currentTimeMillis())});sale(s,id)!!
    }
    private val columns="s.sequence,s.id,s.subscriber_id,s.customer,s.items_json,s.amount_minor,s.paid_minor,s.currency,EXISTS(SELECT 1 FROM sale_voids v WHERE v.sale_id=s.id)"
    private fun row(c: android.database.Cursor)=BusinessSale(c.getLong(0),c.getString(1),c.getString(2),c.getString(3),decode(c.getString(4)),c.getLong(5),c.getLong(6),c.getString(7),c.getInt(8)!=0)
    fun sale(s: BusinessScope,id: String): BusinessSale?=store.guarded(s,BusinessPermission.READ) { db.rawQuery("SELECT $columns FROM sales s WHERE organization_id=? AND branch_id=? AND id=?",arrayOf(s.organizationId,s.branchId,id)).use { if(it.moveToFirst()) row(it) else null } }
    fun page(s: BusinessScope,before: Long=Long.MAX_VALUE): BusinessPage<BusinessSale> {
        store.authorize(s,BusinessPermission.READ)

        val rows=db.rawQuery("SELECT $columns FROM sales s WHERE organization_id=? AND branch_id=? AND sequence<? ORDER BY sequence DESC LIMIT 31",arrayOf(s.organizationId,s.branchId,before.toString())).use { c->buildList { while(c.moveToNext()) add(row(c)) } };return BusinessPage(rows.take(30),rows.size>30)
    }
    fun cancel(s: BusinessScope,saleId: String,id: String,reason: String)=store.transaction { d ->
        store.authorize(s,BusinessPermission.REVERSE)

        val sale=sale(s,saleId) ?: error("SALE_NOT_FOUND");val clean=businessText(reason,500,true);require(id.length<=64 && id.isNotBlank())
        d.rawQuery("SELECT sale_id,reason FROM sale_voids WHERE id=?",arrayOf(id)).use { if(it.moveToFirst()){ require(it.getString(0)==saleId && it.getString(1)==clean) { "IDEMPOTENCY_CONFLICT" };return@transaction } }
        require(!sale.voided) { "ALREADY_REVERSED" }
        d.rawQuery("SELECT 1 FROM reseller_entries e WHERE e.sale_id=? AND NOT EXISTS(SELECT 1 FROM reseller_entries r WHERE r.reversal_of=e.id) LIMIT 1",arrayOf(saleId)).use { require(!it.moveToFirst()) { "REVERSE_COMMISSION_FIRST" } }
        d.execSQL("INSERT INTO sale_voids VALUES(?,?,?,?,?,?)",arrayOf(id,saleId,s.organizationId,s.branchId,clean,System.currentTimeMillis()))
        store.reverse(s,sale.subscriber,"$saleId:c","$id:c",clean)
        if(sale.paid>0)store.reverse(s,sale.subscriber,"$saleId:p","$id:p",clean)
    }
    companion object {
        fun encode(lines: List<SaleLine>)=JSONArray().apply { lines.forEach { put(JSONObject().put("name",it.name).put("quantity",it.quantity).put("unitMinor",it.unitMinor)) } }.toString()
        fun decode(text: String): List<SaleLine> { val a=JSONArray(text);return (0 until a.length()).map { a.getJSONObject(it).let { o->SaleLine(o.getString("name"),o.getInt("quantity"),o.getLong("unitMinor")) } } }
    }
}
internal object PaymentDetails {
    fun requireMatch(db: android.database.sqlite.SQLiteDatabase,id: String,method: PaymentMethod,reference: String) {
        val ref=businessText(reference,120)
        val found=db.rawQuery("SELECT method,reference FROM payment_details WHERE ledger_id=?",arrayOf(id)).use { if(it.moveToFirst()) it.getString(0) to it.getString(1) else null }
        if(found==null) db.execSQL("INSERT INTO payment_details VALUES(?,?,?)",arrayOf(id,method.name,ref))
        else require(found==method.name to ref) { "IDEMPOTENCY_CONFLICT" }
    }
}
