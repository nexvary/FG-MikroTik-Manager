package com.fgmachines.mikrotikmanager.business

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import java.util.UUID

/** One versioned business database. Legacy encrypted voucher storage is deliberately preserved. */
class BusinessDatabase(context: Context, name: String = "fg_business.db") :
    SQLiteOpenHelper(context.applicationContext, name, null, 4) {
    init { setWriteAheadLoggingEnabled(true) }
    override fun onConfigure(db: SQLiteDatabase) { db.setForeignKeyConstraintsEnabled(true) }
    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL("CREATE TABLE organizations (id TEXT PRIMARY KEY NOT NULL, name TEXT NOT NULL)")
        db.execSQL("""CREATE TABLE branches (id TEXT PRIMARY KEY NOT NULL, organization_id TEXT NOT NULL REFERENCES organizations(id),
            name TEXT NOT NULL, UNIQUE(organization_id,id))""")
        db.execSQL("""CREATE TABLE settings (id INTEGER PRIMARY KEY CHECK(id=1), organization_id TEXT NOT NULL,
            branch_id TEXT NOT NULL, FOREIGN KEY(organization_id,branch_id) REFERENCES branches(organization_id,id))""")
        db.execSQL("""CREATE TABLE subscribers (id TEXT PRIMARY KEY NOT NULL, organization_id TEXT NOT NULL, branch_id TEXT NOT NULL,
            name TEXT NOT NULL CHECK(length(name)>0), phone TEXT NOT NULL, service TEXT NOT NULL CHECK(service IN ('HOTSPOT','PPPOE','OTHER')),
            account TEXT NOT NULL, currency TEXT NOT NULL CHECK(currency IN ('EGP','USD','EUR','SAR','AED','TRY')),
            created_at INTEGER NOT NULL, UNIQUE(organization_id,branch_id,id),
            FOREIGN KEY(organization_id,branch_id) REFERENCES branches(organization_id,id))""")
        db.execSQL("CREATE INDEX subscriber_scope_name ON subscribers(organization_id,branch_id,name COLLATE NOCASE,id)")
        db.execSQL("""CREATE TABLE ledger (sequence INTEGER PRIMARY KEY AUTOINCREMENT, id TEXT NOT NULL UNIQUE,
            organization_id TEXT NOT NULL, branch_id TEXT NOT NULL, subscriber_id TEXT NOT NULL,
            kind TEXT NOT NULL CHECK(kind IN ('CHARGE','PAYMENT','REVERSAL')),
            amount_minor INTEGER NOT NULL CHECK(typeof(amount_minor)='integer' AND amount_minor!=0 AND abs(amount_minor)<=999999999999),
            currency TEXT NOT NULL, note TEXT NOT NULL CHECK(length(note)>0), created_at INTEGER NOT NULL,
            reversal_of TEXT UNIQUE REFERENCES ledger(id),
            CHECK((kind='CHARGE' AND amount_minor>0 AND reversal_of IS NULL) OR
                  (kind='PAYMENT' AND amount_minor<0 AND reversal_of IS NULL) OR
                  (kind='REVERSAL' AND reversal_of IS NOT NULL)),
            FOREIGN KEY(organization_id,branch_id,subscriber_id) REFERENCES subscribers(organization_id,branch_id,id))""")
        db.execSQL("CREATE INDEX ledger_scope_subscriber ON ledger(organization_id,branch_id,subscriber_id,sequence DESC)")
        db.execSQL("""CREATE TRIGGER ledger_currency BEFORE INSERT ON ledger WHEN NOT EXISTS (
            SELECT 1 FROM subscribers WHERE id=NEW.subscriber_id AND organization_id=NEW.organization_id
            AND branch_id=NEW.branch_id AND currency=NEW.currency)
            BEGIN SELECT RAISE(ABORT,'CURRENCY_MISMATCH'); END""")
        db.execSQL("""CREATE TRIGGER ledger_reversal BEFORE INSERT ON ledger WHEN NEW.kind='REVERSAL' AND NOT EXISTS (
            SELECT 1 FROM ledger WHERE id=NEW.reversal_of AND kind IN ('CHARGE','PAYMENT')
            AND organization_id=NEW.organization_id AND branch_id=NEW.branch_id AND subscriber_id=NEW.subscriber_id
            AND currency=NEW.currency AND amount_minor=-NEW.amount_minor)
            BEGIN SELECT RAISE(ABORT,'INVALID_REVERSAL'); END""")
        db.execSQL("""CREATE TRIGGER subscriber_ledger_currency BEFORE UPDATE OF currency ON subscribers
            WHEN OLD.currency!=NEW.currency AND EXISTS(SELECT 1 FROM ledger WHERE subscriber_id=OLD.id)
            BEGIN SELECT RAISE(ABORT,'IMMUTABLE_CURRENCY'); END""")
        db.execSQL("CREATE TRIGGER ledger_no_update BEFORE UPDATE ON ledger BEGIN SELECT RAISE(ABORT,'IMMUTABLE_LEDGER'); END")
        db.execSQL("CREATE TRIGGER ledger_no_delete BEFORE DELETE ON ledger BEGIN SELECT RAISE(ABORT,'IMMUTABLE_LEDGER'); END")
        BusinessSchemaV2.install(db)
        BusinessSchemaV3.install(db)
        BusinessSchemaV4.install(db)
        val org = UUID.randomUUID().toString(); val branch = UUID.randomUUID().toString()
        db.execSQL("INSERT INTO organizations VALUES (?,?)", arrayOf(org,"Local business"))
        db.execSQL("INSERT INTO branches VALUES (?,?,?)", arrayOf(branch,org,"Main branch"))
        db.execSQL("INSERT INTO settings VALUES (1,?,?)", arrayOf(org,branch))
    }
    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        require(oldVersion in 1..3 && newVersion==4) { "Unsupported database migration" }
        if(oldVersion==1) BusinessSchemaV2.install(db)
        if(oldVersion<=2) BusinessSchemaV3.install(db)
        BusinessSchemaV4.install(db)
    }
}
