package com.fgmachines.mikrotikmanager.business

import android.database.sqlite.SQLiteDatabase

/** Additive 1 -> 2 migration; the original subscriber/ledger tables and IDs are preserved. */
internal object BusinessSchemaV2 {
    val tables = listOf("organizations", "branches", "settings", "subscribers", "ledger", "plans", "invoices", "invoice_voids", "expenses", "import_batches", "audit")
    fun install(db: SQLiteDatabase) {
        db.execSQL("""CREATE TABLE plans(id TEXT PRIMARY KEY NOT NULL, organization_id TEXT NOT NULL, branch_id TEXT NOT NULL,
            name TEXT NOT NULL CHECK(length(name) BETWEEN 1 AND 120), service TEXT NOT NULL CHECK(service IN ('HOTSPOT','PPPOE','OTHER')),
            currency TEXT NOT NULL CHECK(currency IN ('EGP','USD','EUR','SAR','AED','TRY')),
            price_minor INTEGER NOT NULL CHECK(typeof(price_minor)='integer' AND price_minor BETWEEN 1 AND 999999999999),
            days INTEGER NOT NULL CHECK(days BETWEEN 1 AND 3660), created_at INTEGER NOT NULL,
            UNIQUE(organization_id,branch_id,id), FOREIGN KEY(organization_id,branch_id) REFERENCES branches(organization_id,id))""")
        db.execSQL("CREATE INDEX plans_scope ON plans(organization_id,branch_id,name,id)")
        db.execSQL("""CREATE TABLE invoices(sequence INTEGER PRIMARY KEY AUTOINCREMENT, id TEXT NOT NULL UNIQUE,
            organization_id TEXT NOT NULL, branch_id TEXT NOT NULL, subscriber_id TEXT NOT NULL, plan_id TEXT NOT NULL,
            customer_name TEXT NOT NULL, plan_name TEXT NOT NULL, amount_minor INTEGER NOT NULL CHECK(amount_minor BETWEEN 1 AND 999999999999),
            currency TEXT NOT NULL, days INTEGER NOT NULL CHECK(days BETWEEN 1 AND 3660), starts_day INTEGER NOT NULL,
            ends_day INTEGER NOT NULL CHECK(ends_day=starts_day+days), paid_minor INTEGER NOT NULL CHECK(paid_minor>=0 AND paid_minor<=amount_minor),
            charge_id TEXT NOT NULL UNIQUE REFERENCES ledger(id), payment_id TEXT UNIQUE REFERENCES ledger(id), created_at INTEGER NOT NULL,
            CHECK((paid_minor=0 AND payment_id IS NULL) OR (paid_minor>0 AND payment_id IS NOT NULL)),
            FOREIGN KEY(organization_id,branch_id,subscriber_id) REFERENCES subscribers(organization_id,branch_id,id),
            FOREIGN KEY(organization_id,branch_id,plan_id) REFERENCES plans(organization_id,branch_id,id))""")
        db.execSQL("CREATE INDEX invoice_scope ON invoices(organization_id,branch_id,sequence DESC)")
        db.execSQL("CREATE INDEX invoice_subscription ON invoices(organization_id,branch_id,subscriber_id,ends_day DESC)")
        db.execSQL("""CREATE TRIGGER invoice_valid BEFORE INSERT ON invoices WHEN NOT EXISTS (
            SELECT 1 FROM ledger l JOIN subscribers s ON s.id=l.subscriber_id JOIN plans p ON p.id=NEW.plan_id
            WHERE l.id=NEW.charge_id AND l.subscriber_id=NEW.subscriber_id AND l.organization_id=NEW.organization_id
            AND l.branch_id=NEW.branch_id AND l.kind='CHARGE' AND l.amount_minor=NEW.amount_minor AND l.currency=NEW.currency
            AND p.organization_id=NEW.organization_id AND p.branch_id=NEW.branch_id AND p.service=s.service
            AND p.currency=NEW.currency AND p.price_minor=NEW.amount_minor AND p.days=NEW.days
            AND p.name=NEW.plan_name AND s.name=NEW.customer_name)
            OR (NEW.paid_minor>0 AND NOT EXISTS(SELECT 1 FROM ledger WHERE id=NEW.payment_id AND subscriber_id=NEW.subscriber_id
            AND organization_id=NEW.organization_id AND branch_id=NEW.branch_id AND kind='PAYMENT' AND amount_minor=-NEW.paid_minor AND currency=NEW.currency))
            BEGIN SELECT RAISE(ABORT,'INVALID_INVOICE'); END""")
        db.execSQL("""CREATE TABLE invoice_voids(id TEXT PRIMARY KEY NOT NULL, invoice_id TEXT NOT NULL UNIQUE REFERENCES invoices(id),
            organization_id TEXT NOT NULL, branch_id TEXT NOT NULL, reason TEXT NOT NULL CHECK(length(reason)>0), created_at INTEGER NOT NULL,
            FOREIGN KEY(organization_id,branch_id) REFERENCES branches(organization_id,id))""")
        db.execSQL("""CREATE TRIGGER invoice_void_scope BEFORE INSERT ON invoice_voids WHEN NOT EXISTS(
            SELECT 1 FROM invoices WHERE id=NEW.invoice_id AND organization_id=NEW.organization_id AND branch_id=NEW.branch_id)
            BEGIN SELECT RAISE(ABORT,'SCOPE_MISMATCH'); END""")
        db.execSQL("""CREATE TRIGGER invoice_reversal_guard BEFORE INSERT ON ledger WHEN NEW.kind='REVERSAL' AND EXISTS(
            SELECT 1 FROM invoices i WHERE (i.charge_id=NEW.reversal_of OR i.payment_id=NEW.reversal_of)
            AND NOT EXISTS(SELECT 1 FROM invoice_voids v WHERE v.invoice_id=i.id))
            BEGIN SELECT RAISE(ABORT,'CANCEL_INVOICE_FIRST'); END""")
        db.execSQL("""CREATE TABLE expenses(sequence INTEGER PRIMARY KEY AUTOINCREMENT, id TEXT NOT NULL UNIQUE,
            organization_id TEXT NOT NULL, branch_id TEXT NOT NULL, category TEXT NOT NULL CHECK(length(category)>0),
            amount_minor INTEGER NOT NULL CHECK(typeof(amount_minor)='integer' AND amount_minor!=0 AND abs(amount_minor)<=999999999999),
            currency TEXT NOT NULL CHECK(currency IN ('EGP','USD','EUR','SAR','AED','TRY')), note TEXT NOT NULL CHECK(length(note)>0),
            reversal_of TEXT UNIQUE REFERENCES expenses(id), created_at INTEGER NOT NULL,
            CHECK((reversal_of IS NULL AND amount_minor>0) OR (reversal_of IS NOT NULL AND amount_minor<0)),
            FOREIGN KEY(organization_id,branch_id) REFERENCES branches(organization_id,id))""")
        db.execSQL("CREATE INDEX expenses_scope ON expenses(organization_id,branch_id,sequence DESC)")
        db.execSQL("""CREATE TRIGGER expense_reversal BEFORE INSERT ON expenses WHEN NEW.reversal_of IS NOT NULL AND NOT EXISTS(
            SELECT 1 FROM expenses WHERE id=NEW.reversal_of AND reversal_of IS NULL AND organization_id=NEW.organization_id
            AND branch_id=NEW.branch_id AND currency=NEW.currency AND amount_minor=-NEW.amount_minor AND category=NEW.category)
            BEGIN SELECT RAISE(ABORT,'INVALID_REVERSAL'); END""")
        db.execSQL("""CREATE TABLE import_batches(id TEXT PRIMARY KEY NOT NULL, organization_id TEXT NOT NULL, branch_id TEXT NOT NULL,
            digest TEXT NOT NULL, row_count INTEGER NOT NULL, created_at INTEGER NOT NULL,
            FOREIGN KEY(organization_id,branch_id) REFERENCES branches(organization_id,id))""")
        db.execSQL("""CREATE TABLE audit(sequence INTEGER PRIMARY KEY AUTOINCREMENT, organization_id TEXT NOT NULL, branch_id TEXT NOT NULL,
            entity TEXT NOT NULL, entity_id TEXT NOT NULL, action TEXT NOT NULL, actor TEXT NOT NULL DEFAULT 'local-app',
            created_at INTEGER NOT NULL, FOREIGN KEY(organization_id,branch_id) REFERENCES branches(organization_id,id))""")
        db.execSQL("CREATE INDEX audit_scope ON audit(organization_id,branch_id,sequence DESC)")
        for(table in listOf("plans","invoices","invoice_voids","expenses","import_batches","audit")) {
            db.execSQL("CREATE TRIGGER ${table}_no_update BEFORE UPDATE ON $table BEGIN SELECT RAISE(ABORT,'IMMUTABLE_RECORD'); END")
            db.execSQL("CREATE TRIGGER ${table}_no_delete BEFORE DELETE ON $table BEGIN SELECT RAISE(ABORT,'IMMUTABLE_RECORD'); END")
        }
        createAuditTriggers(db)
    }
    val auditedTables=listOf("subscribers","ledger","plans","invoices","invoice_voids","expenses","import_batches")
    fun createAuditTriggers(db: SQLiteDatabase) {
        for(table in auditedTables) db.execSQL("""CREATE TRIGGER audit_$table AFTER INSERT ON $table BEGIN
            INSERT INTO audit(organization_id,branch_id,entity,entity_id,action,created_at)
            VALUES(NEW.organization_id,NEW.branch_id,'$table',NEW.id,'CREATE',NEW.created_at); END""")
    }
}
