package com.fgmachines.mikrotikmanager.business

import android.content.ContentValues
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import java.time.LocalDate
import java.util.UUID

// Prices and service periods are snapshotted on invoices. No RouterOS side effect is implied.
data class BusinessPlan(val id: String,val name: String,val service: String,val currency: String,val price: Long,val days: Int)
data class BusinessInvoice(val sequence: Long,val id: String,val subscriberId: String,val customer: String,val plan: String,val amount: Long,val currency: String,val paid: Long,val start: Long,val end: Long,val voided: Boolean)
data class BusinessExpense(val sequence: Long,val id: String,val category: String,val amount: Long,val currency: String,val note: String,val reversed: Boolean,val reversalOf: String?)
data class BusinessBranch(val id: String,val name: String)
data class BusinessAudit(val sequence: Long,val entity: String,val id: String,val action: String,val at: Long)
data class BusinessTotals(val currency: String,val charges: Long,val receipts: Long,val expenses: Long,val balance: Long)

class BusinessOperations(private val store: BusinessStore) {
    private val db get()=store.helper.readableDatabase
    private fun args(s: BusinessScope)=arrayOf(s.organizationId,s.branchId)
    private fun values(s: BusinessScope,id: String)=ContentValues().apply {
        require(id.isNotBlank() && id.length<=80) { "INVALID_ID" }
        put("id",id);put("organization_id",s.organizationId);put("branch_id",s.branchId);put("created_at",System.currentTimeMillis())
    }
    fun branches(s: BusinessScope): List<BusinessBranch> = db.rawQuery("SELECT id,name FROM branches WHERE organization_id=? ORDER BY name,id",arrayOf(s.organizationId)).use { c -> buildList { while(c.moveToNext()) add(BusinessBranch(c.getString(0),c.getString(1))) } }
    fun addBranch(s: BusinessScope,id: String,name: String) = store.transaction { d ->
        val clean=businessText(name,120,true)
        require(id.isNotBlank() && id.length<=80)
        d.rawQuery("SELECT name FROM branches WHERE organization_id=? AND id=?",arrayOf(s.organizationId,id)).use { c ->
            if(c.moveToFirst()) { require(c.getString(0)==clean) { "IDEMPOTENCY_CONFLICT" }; return@transaction }
        }
        require(branches(s).none { it.name.equals(clean,true) }) { "DUPLICATE_BRANCH" }
        d.execSQL("INSERT INTO branches(id,organization_id,name) VALUES(?,?,?)",arrayOf(id,s.organizationId,clean))
        d.execSQL("INSERT INTO audit(organization_id,branch_id,entity,entity_id,action,created_at) VALUES(?,?,'branches',?,'CREATE',?)",arrayOf(s.organizationId,id,id,System.currentTimeMillis()))
    }
    fun selectBranch(s: BusinessScope,id: String) = store.transaction { d ->
        require(branches(s).any { it.id==id }) { "SCOPE_MISMATCH" }
        d.execSQL("UPDATE settings SET branch_id=? WHERE id=1 AND organization_id=?",arrayOf(id,s.organizationId))
    }
    fun addPlan(s: BusinessScope,id: String,name: String,service: String,currency: String,price: Long,days: Int): BusinessPlan = store.transaction { d ->
        require(price in 1..BusinessMoney.MAX_MINOR && days in 1..3660) { "INVALID_AMOUNT" }
        require(service in listOf("HOTSPOT","PPPOE","OTHER") && currency in BusinessMoney.currencies)
        val p=BusinessPlan(id,businessText(name,120,true),service,currency,price,days)
        planOrNull(s,id)?.let { require(it==p) { "IDEMPOTENCY_CONFLICT" }; return@transaction it }
        d.insertOrThrow("plans",null,values(s,id).apply { put("name",p.name);put("service",service);put("currency",currency);put("price_minor",price);put("days",days) }); p
    }
    private fun Cursor.plan()=BusinessPlan(getString(0),getString(1),getString(2),getString(3),getLong(4),getInt(5))
    private fun planOrNull(s: BusinessScope,id: String)=db.rawQuery("SELECT id,name,service,currency,price_minor,days FROM plans WHERE organization_id=? AND branch_id=? AND id=?",args(s)+id).use { if(it.moveToFirst()) it.plan() else null }
    fun plans(s: BusinessScope,offset: Int=0): BusinessPage<BusinessPlan> {
        require(offset>=0)
        val rows=db.rawQuery("SELECT id,name,service,currency,price_minor,days FROM plans WHERE organization_id=? AND branch_id=? ORDER BY name,id LIMIT 31 OFFSET ?",args(s)+offset.toString()).use { c -> buildList { while(c.moveToNext()) add(c.plan()) } }
        return BusinessPage(rows.take(30),rows.size>30)
    }
    /** One atomic local renewal: invoice + receivable + optional cash receipt. Replay never extends twice. */
    fun renew(s: BusinessScope,id: String,subscriberId: String,planId: String,paid: Long,today: Long=LocalDate.now().toEpochDay(),method: PaymentMethod=PaymentMethod.CASH,reference: String=""): BusinessInvoice = store.transaction { d ->
        require(id.length<=64 && id.isNotBlank())
        val sub=store.subscriber(s,subscriberId); val plan=planOrNull(s,planId) ?: error("PLAN_NOT_FOUND")
        require(sub.currency==plan.currency && sub.service==plan.service) { "PLAN_MISMATCH" }
        require(paid in 0..plan.price) { "INVALID_AMOUNT" }
        invoice(s,id)?.let { old ->
            val matches=d.rawQuery("SELECT plan_id FROM invoices WHERE id=?",arrayOf(id)).use { it.moveToFirst();it.getString(0)==planId }
            require(old.subscriberId==subscriberId && old.paid==paid && matches) { "IDEMPOTENCY_CONFLICT" }; if(paid>0) PaymentDetails.requireMatch(d,"$id:p",method,reference); return@transaction old
        }
        require(today in 0..365241780000L) { "INVALID_DATE" }
        val start=maxOf(today,subscriptionEnd(s,subscriberId) ?: today)
        val end=Math.addExact(start,plan.days.toLong()); LocalDate.ofEpochDay(end)
        store.post(s,sub.id,"$id:c",LedgerKind.CHARGE,plan.price,"Subscription / اشتراك: ${plan.name}")
        if(paid>0) store.post(s,sub.id,"$id:p",LedgerKind.PAYMENT,paid,"Invoice receipt / تحصيل فاتورة: $id",method,reference)
        d.insertOrThrow("invoices",null,values(s,id).apply {
            put("subscriber_id",sub.id);put("plan_id",plan.id);put("customer_name",sub.name);put("plan_name",plan.name)
            put("amount_minor",plan.price);put("currency",plan.currency);put("days",plan.days);put("starts_day",start);put("ends_day",end)
            put("paid_minor",paid);put("charge_id","$id:c");if(paid>0) put("payment_id","$id:p") else putNull("payment_id")
        }); invoice(s,id)!!
    }
    fun subscriptionEnd(s: BusinessScope,sub: String): Long? = db.rawQuery("""SELECT MAX(ends_day) FROM invoices i WHERE organization_id=? AND branch_id=? AND subscriber_id=?
        AND NOT EXISTS(SELECT 1 FROM invoice_voids v WHERE v.invoice_id=i.id)""",args(s)+sub).use { it.moveToFirst();if(it.isNull(0)) null else it.getLong(0) }
    private val invoiceColumns="i.sequence,i.id,i.subscriber_id,i.customer_name,i.plan_name,i.amount_minor,i.currency,i.paid_minor,i.starts_day,i.ends_day,EXISTS(SELECT 1 FROM invoice_voids v WHERE v.invoice_id=i.id)"
    private fun Cursor.invoice()=BusinessInvoice(getLong(0),getString(1),getString(2),getString(3),getString(4),getLong(5),getString(6),getLong(7),getLong(8),getLong(9),getInt(10)!=0)
    fun invoice(s: BusinessScope,id: String): BusinessInvoice?=db.rawQuery("SELECT $invoiceColumns FROM invoices i WHERE organization_id=? AND branch_id=? AND id=?",args(s)+id).use { if(it.moveToFirst()) it.invoice() else null }
    fun invoices(s: BusinessScope,before: Long=Long.MAX_VALUE): BusinessPage<BusinessInvoice> {
        val rows=db.rawQuery("SELECT $invoiceColumns FROM invoices i WHERE organization_id=? AND branch_id=? AND sequence<? ORDER BY sequence DESC LIMIT 31",args(s)+before.toString()).use { c -> buildList { while(c.moveToNext()) add(c.invoice()) } };return BusinessPage(rows.take(30),rows.size>30)
    }
    fun cancelInvoice(s: BusinessScope,invoiceId: String,id: String,reason: String) = store.transaction { d ->
        val clean=businessText(reason,500,true); require(id.length<=64 && id.isNotBlank())
        val inv=invoice(s,invoiceId) ?: error("INVOICE_NOT_FOUND")
        d.rawQuery("SELECT invoice_id,reason FROM invoice_voids WHERE organization_id=? AND branch_id=? AND id=?",args(s)+id).use { c ->
            if(c.moveToFirst()) { require(c.getString(0)==invoiceId && c.getString(1)==clean) { "IDEMPOTENCY_CONFLICT" };return@transaction }
        }
        require(!inv.voided) { "ALREADY_REVERSED" }
        val network=BusinessNetworkStore(store).job(s,invoiceId)
        require(network==null || network.state=="SUSPENDED") { "NETWORK_INVOICE_LOCKED" }
        // Only cancel the latest active period; preserve an unambiguous subscription timeline.
        require(subscriptionEnd(s,inv.subscriberId)==inv.end) { "CANCEL_LATEST_FIRST" }
        d.insertOrThrow("invoice_voids",null,values(s,id).apply { put("invoice_id",invoiceId);put("reason",clean) })
        store.reverse(s,inv.subscriberId,"$invoiceId:c","$id:c",clean)
        if(inv.paid>0) store.reverse(s,inv.subscriberId,"$invoiceId:p","$id:p",clean)
    }
    fun expense(s: BusinessScope,id: String,category: String,amount: Long,currency: String,note: String) = store.transaction { d ->
        require(amount in 1..BusinessMoney.MAX_MINOR && currency in BusinessMoney.currencies) { "INVALID_AMOUNT" }
        insertExpense(d,s,id,businessText(category,80,true),amount,currency,businessText(note,500,true),null)
    }
    private fun insertExpense(d: SQLiteDatabase,s: BusinessScope,id: String,category: String,amount: Long,currency: String,note: String,reversal: String?) {
        expenseById(s,id)?.let { require(it.category==category && it.amount==amount && it.currency==currency && it.note==note && it.reversalOf==reversal) { "IDEMPOTENCY_CONFLICT" }; return }
        d.insertOrThrow("expenses",null,values(s,id).apply { put("category",category);put("amount_minor",amount);put("currency",currency);put("note",note);put("reversal_of",reversal) })
    }
    private val expenseColumns="e.sequence,e.id,e.category,e.amount_minor,e.currency,e.note,EXISTS(SELECT 1 FROM expenses r WHERE r.reversal_of=e.id),e.reversal_of"
    private fun Cursor.expense()=BusinessExpense(getLong(0),getString(1),getString(2),getLong(3),getString(4),getString(5),getInt(6)!=0,if(isNull(7)) null else getString(7))
    private fun expenseById(s: BusinessScope,id: String)=db.rawQuery("SELECT $expenseColumns FROM expenses e WHERE organization_id=? AND branch_id=? AND id=?",args(s)+id).use { if(it.moveToFirst()) it.expense() else null }
    fun reverseExpense(s: BusinessScope,entry: String,id: String,reason: String)=store.transaction { d ->
        val old=expenseById(s,entry) ?: error("ENTRY_NOT_FOUND");require(old.reversalOf==null) { "INVALID_REVERSAL" }
        insertExpense(d,s,id,old.category,-old.amount,old.currency,businessText(reason,500,true),old.id)
    }
    fun expenses(s: BusinessScope,before: Long=Long.MAX_VALUE): BusinessPage<BusinessExpense> {
        val rows=db.rawQuery("SELECT $expenseColumns FROM expenses e WHERE organization_id=? AND branch_id=? AND sequence<? ORDER BY sequence DESC LIMIT 31",args(s)+before.toString()).use { c -> buildList { while(c.moveToNext()) add(c.expense()) } };return BusinessPage(rows.take(30),rows.size>30)
    }
    /** Period movements; balance is all-time and labeled separately. Reversals count on their posting date. */
    fun totals(s: BusinessScope,from: Long,until: Long): List<BusinessTotals> = store.transaction { d ->
        require(from<until) { "INVALID_DATE" }
        BusinessMoney.currencies.map { currency ->
            var charges=0L;var receipts=0L
            d.rawQuery("""SELECT COALESCE(SUM(CASE WHEN COALESCE(o.kind,l.kind)='CHARGE' THEN l.amount_minor ELSE 0 END),0),
                COALESCE(SUM(CASE WHEN COALESCE(o.kind,l.kind)='PAYMENT' THEN -l.amount_minor ELSE 0 END),0)
                FROM ledger l LEFT JOIN ledger o ON o.id=l.reversal_of WHERE l.organization_id=? AND l.branch_id=? AND l.currency=? AND l.created_at>=? AND l.created_at<?""",args(s)+arrayOf(currency,from.toString(),until.toString())).use { it.moveToFirst();charges=it.getLong(0);receipts=it.getLong(1) }
            val expenses=d.rawQuery("SELECT COALESCE(SUM(amount_minor),0) FROM expenses WHERE organization_id=? AND branch_id=? AND currency=? AND created_at>=? AND created_at<?",args(s)+arrayOf(currency,from.toString(),until.toString())).use { it.moveToFirst();it.getLong(0) }
            val balance=d.rawQuery("SELECT COALESCE(SUM(amount_minor),0) FROM ledger WHERE organization_id=? AND branch_id=? AND currency=?",args(s)+currency).use { it.moveToFirst();it.getLong(0) }
            BusinessTotals(currency,charges,receipts,expenses,balance)
        }
    }
    fun audit(s: BusinessScope,before: Long=Long.MAX_VALUE): BusinessPage<BusinessAudit> {
        val rows=db.rawQuery("SELECT sequence,entity,entity_id,action,created_at FROM audit WHERE organization_id=? AND branch_id=? AND sequence<? ORDER BY sequence DESC LIMIT 31",args(s)+before.toString()).use { c -> buildList { while(c.moveToNext()) add(BusinessAudit(c.getLong(0),c.getString(1),c.getString(2),c.getString(3),c.getLong(4))) } };return BusinessPage(rows.take(30),rows.size>30)
    }
}
