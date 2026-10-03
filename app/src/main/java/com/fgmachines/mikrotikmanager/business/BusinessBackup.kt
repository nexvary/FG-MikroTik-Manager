package com.fgmachines.mikrotikmanager.business

import android.content.ContentValues
import android.database.Cursor
import org.json.JSONArray
import org.json.JSONObject
import java.io.InputStream
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

/** Password-portable business snapshot only. Voucher Keystore secrets/router passwords are excluded. */
object BusinessBackupCipher {
    const val MAX_BYTES=20*1024*1024
    private val magic="FGMTMB02".toByteArray(Charsets.US_ASCII)
    private fun key(password: CharArray,salt: ByteArray): ByteArray {
        val spec=PBEKeySpec(password,salt,210000,256)
        return try { SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).encoded } finally { spec.clearPassword() }
    }
    fun encrypt(bytes: ByteArray,password: CharArray): ByteArray {
        require(password.size>=12) { "PASSWORD_SHORT" };require(bytes.size<=MAX_BYTES) { "FILE_TOO_LARGE" }
        val salt=ByteArray(16);val iv=ByteArray(12);SecureRandom().apply { nextBytes(salt);nextBytes(iv) }
        val header=magic+salt+iv;val derived=key(password,salt)
        return try { val c=Cipher.getInstance("AES/GCM/NoPadding");c.init(Cipher.ENCRYPT_MODE,SecretKeySpec(derived,"AES"),GCMParameterSpec(128,iv));c.updateAAD(header);header+c.doFinal(bytes) } finally { derived.fill(0) }
    }
    fun decrypt(bytes: ByteArray,password: CharArray): ByteArray {
        require(bytes.size in 52..MAX_BYTES+52 && bytes.copyOfRange(0,8).contentEquals(magic)) { "INVALID_BACKUP" }
        val derived=key(password,bytes.copyOfRange(8,24))
        return try { val c=Cipher.getInstance("AES/GCM/NoPadding");c.init(Cipher.DECRYPT_MODE,SecretKeySpec(derived,"AES"),GCMParameterSpec(128,bytes.copyOfRange(24,36)));c.updateAAD(bytes.copyOfRange(0,36));c.doFinal(bytes,36,bytes.size-36) } finally { derived.fill(0) }
    }
    fun readBounded(input: InputStream,max: Int=MAX_BYTES+52): ByteArray {
        val out=java.io.ByteArrayOutputStream();val buffer=ByteArray(8192)
        while(true) { val n=input.read(buffer);if(n<0) break;require(out.size()+n<=max) { "FILE_TOO_LARGE" };out.write(buffer,0,n) };return out.toByteArray()
    }
}
class BusinessBackup(private val store: BusinessStore) {
    fun export(password: CharArray): ByteArray {
        val clear=store.transaction { db ->
            val root=JSONObject().put("format","FG-MTM-business").put("schema",2);val tables=JSONObject();var budget=0L
            for(table in BusinessSchemaV2.tables) {
                val rows=JSONArray()
                db.rawQuery("SELECT * FROM $table ORDER BY rowid",null).use { c -> while(c.moveToNext()) {
                    val row=JSONObject()
                    for(i in 0 until c.columnCount) row.put(c.getColumnName(i),when(c.getType(i)) { Cursor.FIELD_TYPE_NULL->JSONObject.NULL; Cursor.FIELD_TYPE_INTEGER->c.getLong(i); else->c.getString(i) })
                    budget+=row.toString().toByteArray(Charsets.UTF_8).size+1;require(budget<=BusinessBackupCipher.MAX_BYTES-4096) { "FILE_TOO_LARGE" };rows.put(row)
                } };tables.put(table,rows)
            };root.put("tables",tables).toString().toByteArray(Charsets.UTF_8)
        }
        return try { BusinessBackupCipher.encrypt(clear,password) } finally { clear.fill(0) }
    }
    /** Restore is allowed only into an unused business store. Existing data is never overwritten. */
    fun restore(bytes: ByteArray,password: CharArray) {
        val clear=BusinessBackupCipher.decrypt(bytes,password)
        val root=try { JSONObject(String(clear,Charsets.UTF_8)) } finally { clear.fill(0) }
        require(root.getString("format")=="FG-MTM-business" && root.getInt("schema")==2) { "INVALID_BACKUP" }
        val tables=root.getJSONObject("tables")
        require(tables.keys().asSequence().toSet()==BusinessSchemaV2.tables.toSet()) { "INVALID_BACKUP" }
        store.transaction { db ->
            for(t in listOf("subscribers","ledger","plans","invoices","expenses","audit","import_batches")) db.rawQuery("SELECT COUNT(*) FROM $t",null).use { it.moveToFirst();require(it.getLong(0)==0L) { "RESTORE_NEEDS_EMPTY_STORE" } }
            require(db.rawQuery("SELECT COUNT(*) FROM branches",null).use { it.moveToFirst();it.getInt(0) }==1) { "RESTORE_NEEDS_EMPTY_STORE" }
            // All DDL and data writes are in this transaction. Rollback restores triggers and initial settings on any error.
            for(t in BusinessSchemaV2.auditedTables) db.execSQL("DROP TRIGGER audit_$t")
            db.execSQL("DELETE FROM settings");db.execSQL("DELETE FROM branches");db.execSQL("DELETE FROM organizations")
            for(t in BusinessSchemaV2.tables) {
                val columns=db.rawQuery("SELECT * FROM $t LIMIT 0",null).use { it.columnNames.toSet() }
                val rows=tables.getJSONArray(t)
                for(i in 0 until rows.length()) {
                    val row=rows.getJSONObject(i);require(row.keys().asSequence().toSet()==columns) { "INVALID_BACKUP" }
                    val v=ContentValues()
                    for(column in columns) {
                        val value=row.get(column)
                        when(value) { JSONObject.NULL -> v.putNull(column);is Number -> { require(value is Long || value is Int) { "INVALID_BACKUP" };v.put(column,value.toLong()) };is String -> v.put(column,value);else -> error("INVALID_BACKUP") }
                    };db.insertOrThrow(t,null,v)
                }
            }
            db.rawQuery("PRAGMA foreign_key_check",null).use { require(!it.moveToFirst()) { "INVALID_BACKUP" } }
            require(db.rawQuery("SELECT COUNT(*) FROM settings",null).use { it.moveToFirst();it.getInt(0) }==1) { "INVALID_BACKUP" }
            // A canceled invoice must have both inverse financial entries; do not accept forged partial cancellations.
            db.rawQuery("""SELECT 1 FROM invoice_voids v JOIN invoices i ON i.id=v.invoice_id WHERE
                NOT EXISTS(SELECT 1 FROM ledger r WHERE r.reversal_of=i.charge_id)
                OR (i.payment_id IS NOT NULL AND NOT EXISTS(SELECT 1 FROM ledger r WHERE r.reversal_of=i.payment_id)) LIMIT 1""",null).use { require(!it.moveToFirst()) { "INVALID_BACKUP" } }
            db.rawQuery("""SELECT 1 FROM invoices i WHERE NOT EXISTS(SELECT 1 FROM invoice_voids v WHERE v.invoice_id=i.id)
                AND EXISTS(SELECT 1 FROM ledger r WHERE r.reversal_of=i.charge_id OR r.reversal_of=i.payment_id) LIMIT 1""",null).use { require(!it.moveToFirst()) { "INVALID_BACKUP" } }
            BusinessSchemaV2.createAuditTriggers(db)
            val s=store.defaultScope()
            db.execSQL("INSERT INTO audit(organization_id,branch_id,entity,entity_id,action,created_at) VALUES(?,?,'backup','portable','RESTORE',?)",arrayOf(s.organizationId,s.branchId,System.currentTimeMillis()))
        }
    }
}
