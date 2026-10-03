package com.fgmachines.mikrotikmanager.business

import android.database.sqlite.SQLiteDatabase

/** Additive identity migration; credential hashes are deliberately excluded from portable business backups. */
internal object BusinessSchemaV5 {
    fun install(db: SQLiteDatabase) {
        db.execSQL("""CREATE TABLE local_accounts(id TEXT PRIMARY KEY NOT NULL, username TEXT NOT NULL UNIQUE,
            organization_id TEXT NOT NULL, branch_id TEXT NOT NULL, member_id TEXT UNIQUE REFERENCES team_members(id),
            owner INTEGER NOT NULL CHECK(owner IN (0,1)), salt TEXT NOT NULL, verifier TEXT NOT NULL, revision INTEGER NOT NULL DEFAULT 1,
            CHECK((owner=1 AND member_id IS NULL) OR (owner=0 AND member_id IS NOT NULL)),
            FOREIGN KEY(organization_id,branch_id) REFERENCES branches(organization_id,id))""")
        db.execSQL("CREATE UNIQUE INDEX one_local_owner ON local_accounts(owner) WHERE owner=1")
        db.execSQL("CREATE TABLE login_throttle(id INTEGER PRIMARY KEY CHECK(id=1), failures INTEGER NOT NULL, until_ms INTEGER NOT NULL)")
        db.execSQL("INSERT INTO login_throttle VALUES(1,0,0)")
        db.execSQL("CREATE TABLE audit_context(id INTEGER PRIMARY KEY CHECK(id=1), actor TEXT NOT NULL)")
        db.execSQL("INSERT INTO audit_context VALUES(1,'local-app')")
        db.execSQL("CREATE TRIGGER local_owner_fixed BEFORE UPDATE ON local_accounts WHEN NEW.id!=OLD.id OR NEW.username!=OLD.username OR NEW.organization_id!=OLD.organization_id OR NEW.branch_id!=OLD.branch_id OR NEW.member_id IS NOT OLD.member_id OR NEW.owner!=OLD.owner BEGIN SELECT RAISE(ABORT,'IMMUTABLE_IDENTITY'); END")
        db.execSQL("CREATE TRIGGER local_account_no_delete BEFORE DELETE ON local_accounts BEGIN SELECT RAISE(ABORT,'IMMUTABLE_IDENTITY'); END")
        rebuildAudit(db)
    }
    fun rebuildAudit(db: SQLiteDatabase) {
        for(t in BusinessSchemaV2.auditedTables+BusinessSchemaV3.audited+BusinessSchemaV4.tables) {
            db.execSQL("DROP TRIGGER IF EXISTS audit_$t")
            db.execSQL("""CREATE TRIGGER audit_$t AFTER INSERT ON $t BEGIN INSERT INTO audit(organization_id,branch_id,entity,entity_id,action,actor,created_at)
                VALUES(NEW.organization_id,NEW.branch_id,'$t',NEW.id,'CREATE',(SELECT actor FROM audit_context WHERE id=1),NEW.created_at); END""")
        }
        db.execSQL("DROP TRIGGER IF EXISTS audit_team_update")
        db.execSQL("""CREATE TRIGGER audit_team_update AFTER UPDATE ON team_members BEGIN INSERT INTO audit(organization_id,branch_id,entity,entity_id,action,actor,created_at)
            VALUES(NEW.organization_id,NEW.branch_id,'team_members',NEW.id,CASE WHEN NEW.active=1 THEN 'ENABLE' ELSE 'DISABLE' END,(SELECT actor FROM audit_context WHERE id=1),CAST(strftime('%s','now') AS INTEGER)*1000); END""")
    }
}
