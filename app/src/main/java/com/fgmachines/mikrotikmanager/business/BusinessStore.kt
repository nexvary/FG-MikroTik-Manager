package com.fgmachines.mikrotikmanager.business

import android.content.ContentValues
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase

/** Blocking IO API: callers must use Dispatchers.IO. Every write has a caller-stable idempotency ID. */
class BusinessStore(internal val helper: BusinessDatabase) : AutoCloseable {
    fun defaultScope(): BusinessScope = helper.readableDatabase.rawQuery(
        "SELECT organization_id,branch_id FROM settings WHERE id=1", null
    ).use { check(it.moveToFirst()); BusinessScope(it.getString(0),it.getString(1)) }

    fun addSubscriber(scope: BusinessScope, id: String, name: String, phone: String, service: String,
        account: String, currency: String): Subscriber {
        require(id.isNotBlank() && id.length <= 80) { "INVALID_ID" }
        require(service in listOf("HOTSPOT","PPPOE","OTHER")) { "INVALID_SERVICE" }
        require(currency in BusinessMoney.currencies) { "INVALID_CURRENCY" }
        val record = Subscriber(id,businessText(name,120,true),businessText(phone,40),service,businessText(account,120),currency)
        return transaction { db ->
            subscriberOrNull(db,scope,id)?.let { require(it == record) { "IDEMPOTENCY_CONFLICT" }; return@transaction it }
            db.insertOrThrow("subscribers",null,ContentValues().apply {
                put("id",id); put("organization_id",scope.organizationId); put("branch_id",scope.branchId)
                put("name",record.name); put("phone",record.phone); put("service",service); put("account",record.account)
                put("currency",currency); put("created_at",System.currentTimeMillis())
            })
            record
        }
    }
    fun subscriber(scope: BusinessScope, id: String): Subscriber = subscriberOrNull(helper.readableDatabase,scope,id)
        ?: throw IllegalArgumentException("SUBSCRIBER_NOT_FOUND")
    private fun subscriberOrNull(db: SQLiteDatabase, scope: BusinessScope, id: String): Subscriber? = db.rawQuery(
        "SELECT id,name,phone,service,account,currency FROM subscribers WHERE organization_id=? AND branch_id=? AND id=?",
        arrayOf(scope.organizationId,scope.branchId,id)
    ).use { if(it.moveToFirst()) it.subscriber() else null }

    fun subscribers(scope: BusinessScope, search: String = "", afterName: String? = null, afterId: String? = null, limit: Int = 30): BusinessPage<Subscriber> {
        require(limit in 1..100)
        val args = mutableListOf(scope.organizationId,scope.branchId)
        val query = StringBuilder("SELECT id,name,phone,service,account,currency FROM subscribers WHERE organization_id=? AND branch_id=?")
        if(search.isNotBlank()) {
            query.append(" AND (instr(lower(name),lower(?))>0 OR instr(phone,?)>0 OR instr(lower(account),lower(?))>0)")
            repeat(3) { args += search.trim().take(120) }
        }
        if(afterName != null && afterId != null) {
            query.append(" AND (name COLLATE NOCASE > ? OR (name COLLATE NOCASE = ? AND id > ?))")
            args += listOf(afterName,afterName,afterId)
        }
        query.append(" ORDER BY name COLLATE NOCASE,id LIMIT ?"); args += (limit+1).toString()
        val rows = helper.readableDatabase.rawQuery(query.toString(),args.toTypedArray()).use { c ->
            buildList { while(c.moveToNext()) add(c.subscriber()) }
        }
        return BusinessPage(rows.take(limit),rows.size>limit)
    }

    fun post(scope: BusinessScope, subscriberId: String, id: String, kind: LedgerKind, amountMinor: Long, note: String, method: PaymentMethod = PaymentMethod.CASH, reference: String = ""): LedgerEntry {
        require(kind != LedgerKind.REVERSAL) { "INVALID_KIND" }
        require(amountMinor in 1..BusinessMoney.MAX_MINOR) { "INVALID_AMOUNT" }
        return transaction { db ->
            val sub = subscriberOrNull(db,scope,subscriberId) ?: throw IllegalArgumentException("SUBSCRIBER_NOT_FOUND")
            val entry=insert(db,scope,sub,id,kind,if(kind==LedgerKind.PAYMENT) -amountMinor else amountMinor,note,null)
            if(kind==LedgerKind.PAYMENT) PaymentDetails.requireMatch(db,id,method,reference)
            entry
        }
    }
    fun reverse(scope: BusinessScope, subscriberId: String, entryId: String, id: String, reason: String): LedgerEntry = transaction { db ->
        val original = entry(db,scope,subscriberId,entryId) ?: throw IllegalArgumentException("ENTRY_NOT_FOUND")
        require(original.kind != LedgerKind.REVERSAL) { "INVALID_REVERSAL" }
        val sub = subscriberOrNull(db,scope,subscriberId) ?: throw IllegalArgumentException("SUBSCRIBER_NOT_FOUND")
        insert(db,scope,sub,id,LedgerKind.REVERSAL,-original.amountMinor,reason,original.id)
    }
    private fun insert(db: SQLiteDatabase, scope: BusinessScope, sub: Subscriber, id: String, kind: LedgerKind,
        amount: Long, note: String, reversalOf: String?): LedgerEntry {
        require(id.isNotBlank() && id.length <= 80) { "INVALID_ID" }
        val cleanNote = businessText(note,500,true)
        entry(db,scope,sub.id,id)?.let {
            require(it.kind==kind && it.amountMinor==amount && it.note==cleanNote && it.reversalOf==reversalOf && it.currency==sub.currency) { "IDEMPOTENCY_CONFLICT" }
            return it
        }
        if(reversalOf != null) require(entry(db,scope,sub.id,reversalOf)?.reversed == false) { "ALREADY_REVERSED" }
        db.insertOrThrow("ledger",null,ContentValues().apply {
            put("id",id); put("organization_id",scope.organizationId); put("branch_id",scope.branchId); put("subscriber_id",sub.id)
            put("kind",kind.name); put("amount_minor",amount); put("currency",sub.currency); put("note",cleanNote)
            put("created_at",System.currentTimeMillis()); put("reversal_of",reversalOf)
        })
        return entry(db,scope,sub.id,id)!!
    }
    private val entryColumns = "l.sequence,l.id,l.subscriber_id,l.kind,l.amount_minor,l.currency,l.note,l.created_at,l.reversal_of,EXISTS(SELECT 1 FROM ledger r WHERE r.reversal_of=l.id)"
    private fun entry(db: SQLiteDatabase, scope: BusinessScope, sub: String, id: String): LedgerEntry? = db.rawQuery(
        "SELECT $entryColumns FROM ledger l WHERE l.organization_id=? AND l.branch_id=? AND l.subscriber_id=? AND l.id=?",
        arrayOf(scope.organizationId,scope.branchId,sub,id)
    ).use { if(it.moveToFirst()) it.entry() else null }
    fun ledger(scope: BusinessScope, sub: String, beforeSequence: Long? = null, limit: Int = 30): BusinessPage<LedgerEntry> {
        require(limit in 1..100)
        val args = mutableListOf(scope.organizationId,scope.branchId,sub)
        var query="SELECT $entryColumns FROM ledger l WHERE l.organization_id=? AND l.branch_id=? AND l.subscriber_id=?"
        if(beforeSequence != null) { query += " AND l.sequence<?"; args += beforeSequence.toString() }
        query += " ORDER BY l.sequence DESC LIMIT ?"; args += (limit+1).toString()
        val rows = helper.readableDatabase.rawQuery(query,args.toTypedArray()).use { c -> buildList { while(c.moveToNext()) add(c.entry()) } }
        return BusinessPage(rows.take(limit),rows.size>limit)
    }
    /** Positive means owed by subscriber, negative means credit. Never mix currencies across subscribers. */
    fun balance(scope: BusinessScope, sub: String): Long = helper.readableDatabase.rawQuery(
        "SELECT COALESCE(SUM(amount_minor),0) FROM ledger WHERE organization_id=? AND branch_id=? AND subscriber_id=?",
        arrayOf(scope.organizationId,scope.branchId,sub)
    ).use { it.moveToFirst(); it.getLong(0) }
    internal fun <T> transaction(block: (SQLiteDatabase)->T): T {
        val db=helper.writableDatabase; db.beginTransaction()
        try { val result=block(db); db.setTransactionSuccessful(); return result } finally { db.endTransaction() }
    }
    private fun Cursor.subscriber() = Subscriber(getString(0),getString(1),getString(2),getString(3),getString(4),getString(5))
    private fun Cursor.entry() = LedgerEntry(getLong(0),getString(1),getString(2),LedgerKind.valueOf(getString(3)),getLong(4),getString(5),getString(6),getLong(7),if(isNull(8)) null else getString(8),getInt(9)!=0)
    override fun close() = helper.close()
}
