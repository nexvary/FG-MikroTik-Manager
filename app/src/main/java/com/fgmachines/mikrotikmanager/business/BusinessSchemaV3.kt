package com.fgmachines.mikrotikmanager.business

import android.database.sqlite.SQLiteDatabase

internal object BusinessSchemaV3 {
    val tables=listOf("payment_details","router_bindings","network_jobs","network_results","sales","sale_voids")
    fun install(db: SQLiteDatabase) {
        db.execSQL("""CREATE TABLE payment_details(ledger_id TEXT PRIMARY KEY NOT NULL REFERENCES ledger(id), method TEXT NOT NULL
            CHECK(method IN ('CASH','VODAFONE_CASH','INSTAPAY','BANK','OTHER')), reference TEXT NOT NULL)""")
        db.execSQL("""CREATE TABLE router_bindings(id TEXT PRIMARY KEY NOT NULL,organization_id TEXT NOT NULL,branch_id TEXT NOT NULL,
            subscriber_id TEXT NOT NULL UNIQUE,fingerprint TEXT NOT NULL,label TEXT NOT NULL,account_id TEXT NOT NULL,account TEXT NOT NULL,
            service TEXT NOT NULL CHECK(service IN ('HOTSPOT','PPPOE')),profile TEXT NOT NULL,created_at INTEGER NOT NULL,
            UNIQUE(organization_id,fingerprint,service,account),
            FOREIGN KEY(organization_id,branch_id,subscriber_id) REFERENCES subscribers(organization_id,branch_id,id))""")
        db.execSQL("""CREATE TABLE network_jobs(id TEXT PRIMARY KEY NOT NULL,organization_id TEXT NOT NULL,branch_id TEXT NOT NULL,
            invoice_id TEXT NOT NULL UNIQUE REFERENCES invoices(id),binding_id TEXT NOT NULL REFERENCES router_bindings(id),
            profile TEXT NOT NULL,end_day INTEGER NOT NULL,byte_limit INTEGER NOT NULL CHECK(byte_limit>=0),created_at INTEGER NOT NULL,
            FOREIGN KEY(organization_id,branch_id) REFERENCES branches(organization_id,id))""")
        db.execSQL("""CREATE TABLE network_results(sequence INTEGER PRIMARY KEY AUTOINCREMENT,job_id TEXT NOT NULL REFERENCES network_jobs(id),
            state TEXT NOT NULL CHECK(state IN ('REVIEW','VERIFIED','SUSPENDED')),created_at INTEGER NOT NULL)""")
        db.execSQL("CREATE INDEX network_result_job ON network_results(job_id,sequence DESC)")
        db.execSQL("""CREATE TABLE sales(sequence INTEGER PRIMARY KEY AUTOINCREMENT,id TEXT NOT NULL UNIQUE,organization_id TEXT NOT NULL,branch_id TEXT NOT NULL,
            subscriber_id TEXT NOT NULL,customer TEXT NOT NULL,items_json TEXT NOT NULL,amount_minor INTEGER NOT NULL CHECK(amount_minor BETWEEN 1 AND 999999999999),
            currency TEXT NOT NULL,paid_minor INTEGER NOT NULL CHECK(paid_minor>=0 AND paid_minor<=amount_minor),
            charge_id TEXT NOT NULL UNIQUE REFERENCES ledger(id),payment_id TEXT UNIQUE REFERENCES ledger(id),created_at INTEGER NOT NULL,
            CHECK((paid_minor=0 AND payment_id IS NULL) OR (paid_minor>0 AND payment_id IS NOT NULL)),
            FOREIGN KEY(organization_id,branch_id,subscriber_id) REFERENCES subscribers(organization_id,branch_id,id))""")
        db.execSQL("CREATE INDEX sales_scope ON sales(organization_id,branch_id,sequence DESC)")
        db.execSQL("""CREATE TABLE sale_voids(id TEXT PRIMARY KEY NOT NULL,sale_id TEXT NOT NULL UNIQUE REFERENCES sales(id),organization_id TEXT NOT NULL,
            branch_id TEXT NOT NULL,reason TEXT NOT NULL CHECK(length(reason)>0),created_at INTEGER NOT NULL,
            FOREIGN KEY(organization_id,branch_id) REFERENCES branches(organization_id,id))""")
        db.execSQL("""CREATE TRIGGER sale_valid BEFORE INSERT ON sales WHEN NOT EXISTS(SELECT 1 FROM ledger WHERE id=NEW.charge_id
            AND organization_id=NEW.organization_id AND branch_id=NEW.branch_id AND subscriber_id=NEW.subscriber_id
            AND kind='CHARGE' AND amount_minor=NEW.amount_minor AND currency=NEW.currency) OR
            (NEW.paid_minor>0 AND NOT EXISTS(SELECT 1 FROM ledger WHERE id=NEW.payment_id AND organization_id=NEW.organization_id
            AND branch_id=NEW.branch_id AND subscriber_id=NEW.subscriber_id AND kind='PAYMENT' AND amount_minor=-NEW.paid_minor AND currency=NEW.currency))
            BEGIN SELECT RAISE(ABORT,'INVALID_SALE'); END""")
        db.execSQL("""CREATE TRIGGER sale_void_scope BEFORE INSERT ON sale_voids WHEN NOT EXISTS(SELECT 1 FROM sales WHERE id=NEW.sale_id
            AND organization_id=NEW.organization_id AND branch_id=NEW.branch_id) BEGIN SELECT RAISE(ABORT,'SCOPE_MISMATCH'); END""")
        db.execSQL("""CREATE TRIGGER sale_reversal_guard BEFORE INSERT ON ledger WHEN NEW.kind='REVERSAL' AND EXISTS(SELECT 1 FROM sales s
            WHERE (s.charge_id=NEW.reversal_of OR s.payment_id=NEW.reversal_of) AND NOT EXISTS(SELECT 1 FROM sale_voids v WHERE v.sale_id=s.id))
            BEGIN SELECT RAISE(ABORT,'CANCEL_SALE_FIRST'); END""")
        for(t in tables) {
            db.execSQL("CREATE TRIGGER ${t}_no_update BEFORE UPDATE ON $t BEGIN SELECT RAISE(ABORT,'IMMUTABLE_RECORD'); END")
            db.execSQL("CREATE TRIGGER ${t}_no_delete BEFORE DELETE ON $t BEGIN SELECT RAISE(ABORT,'IMMUTABLE_RECORD'); END")
        }
        db.execSQL("""CREATE TRIGGER payment_kind BEFORE INSERT ON payment_details WHEN NOT EXISTS(SELECT 1 FROM ledger WHERE id=NEW.ledger_id AND kind='PAYMENT')
            BEGIN SELECT RAISE(ABORT,'INVALID_PAYMENT'); END""")
        db.execSQL("""CREATE TRIGGER binding_valid BEFORE INSERT ON router_bindings WHEN NOT EXISTS(SELECT 1 FROM subscribers WHERE id=NEW.subscriber_id
            AND organization_id=NEW.organization_id AND branch_id=NEW.branch_id AND service=NEW.service AND account=NEW.account)
            BEGIN SELECT RAISE(ABORT,'INVALID_BINDING'); END""")
        db.execSQL("""CREATE TRIGGER network_job_scope BEFORE INSERT ON network_jobs WHEN NOT EXISTS(SELECT 1 FROM invoices i JOIN router_bindings b ON b.subscriber_id=i.subscriber_id
            WHERE i.id=NEW.invoice_id AND b.id=NEW.binding_id AND i.organization_id=NEW.organization_id AND i.branch_id=NEW.branch_id
            AND b.organization_id=NEW.organization_id AND b.branch_id=NEW.branch_id AND i.ends_day=NEW.end_day)
            BEGIN SELECT RAISE(ABORT,'INVALID_NETWORK_JOB'); END""")
        createAudit(db)
    }
    val audited=listOf("router_bindings","network_jobs","sales","sale_voids")
    fun createAudit(db: SQLiteDatabase) { for(t in audited) db.execSQL("""CREATE TRIGGER audit_$t AFTER INSERT ON $t BEGIN
        INSERT INTO audit(organization_id,branch_id,entity,entity_id,action,created_at) VALUES(NEW.organization_id,NEW.branch_id,'$t',NEW.id,'CREATE',NEW.created_at); END""") }
}
