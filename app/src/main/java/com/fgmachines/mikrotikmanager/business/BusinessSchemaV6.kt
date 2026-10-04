package com.fgmachines.mikrotikmanager.business

import android.database.sqlite.SQLiteDatabase
import java.util.UUID

/** Installation-scoped attribution and immutable snapshots; credentials are never included. */
internal object BusinessSchemaV6 {
    val tables=listOf("audit_details")
    private val sources=BusinessSchemaV2.auditedTables+BusinessSchemaV3.audited+BusinessSchemaV4.tables
    fun install(db:SQLiteDatabase) {
        db.execSQL("CREATE TABLE audit_installation(id INTEGER PRIMARY KEY CHECK(id=1), device TEXT NOT NULL)")
        db.execSQL("INSERT INTO audit_installation VALUES(1,?)",arrayOf(UUID.randomUUID().toString()))
        db.execSQL("""CREATE TABLE audit_details(sequence INTEGER PRIMARY KEY REFERENCES audit(sequence),device TEXT NOT NULL,
            before_value TEXT,after_value TEXT)""")
        db.execSQL("CREATE TRIGGER audit_details_no_update BEFORE UPDATE ON audit_details BEGIN SELECT RAISE(ABORT,'IMMUTABLE_AUDIT'); END")
        db.execSQL("CREATE TRIGGER audit_details_no_delete BEFORE DELETE ON audit_details BEGIN SELECT RAISE(ABORT,'IMMUTABLE_AUDIT'); END")
        rebuild(db)
    }
    private fun snapshot(db:SQLiteDatabase,table:String,prefix:String):String {
        val columns=db.rawQuery("PRAGMA table_info($table)",null).use { c->buildList{while(c.moveToNext())add(c.getString(1))} }
        return columns.joinToString(" || char(10) || "){ "'$it=' || quote($prefix.$it)" }
    }
    fun rebuild(db:SQLiteDatabase) {
        for(t in sources) {
            db.execSQL("DROP TRIGGER IF EXISTS audit_$t")
            db.execSQL("""CREATE TRIGGER audit_$t AFTER INSERT ON $t BEGIN
                INSERT INTO audit(organization_id,branch_id,entity,entity_id,action,actor,created_at)
                VALUES(NEW.organization_id,NEW.branch_id,'$t',NEW.id,'CREATE',(SELECT actor FROM audit_context WHERE id=1),NEW.created_at);
                INSERT INTO audit_details VALUES(last_insert_rowid(),(SELECT device FROM audit_installation WHERE id=1),NULL,${snapshot(db,t,"NEW")}); END""")
        }
        db.execSQL("DROP TRIGGER IF EXISTS audit_team_update")
        db.execSQL("""CREATE TRIGGER audit_team_update AFTER UPDATE ON team_members BEGIN
            INSERT INTO audit(organization_id,branch_id,entity,entity_id,action,actor,created_at)
            VALUES(NEW.organization_id,NEW.branch_id,'team_members',NEW.id,CASE WHEN NEW.active=1 THEN 'ENABLE' ELSE 'DISABLE' END,(SELECT actor FROM audit_context WHERE id=1),CAST(strftime('%s','now') AS INTEGER)*1000);
            INSERT INTO audit_details VALUES(last_insert_rowid(),(SELECT device FROM audit_installation WHERE id=1),${snapshot(db,"team_members","OLD")},${snapshot(db,"team_members","NEW")}); END""")
    }
}
