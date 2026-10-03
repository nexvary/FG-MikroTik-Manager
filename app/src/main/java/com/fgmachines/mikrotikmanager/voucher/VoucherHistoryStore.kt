package com.fgmachines.mikrotikmanager.voucher

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

data class SavedVoucherBatch(
    val id: String,
    val createdAtEpochMs: Long,
    val batch: VoucherBatch
)

class VoucherHistoryStore(context: Context, prefsName: String = PREFS_NAME) {
    private val appContext=context.applicationContext
    private fun authorize() {
        com.fgmachines.mikrotikmanager.business.BusinessStore(com.fgmachines.mikrotikmanager.business.BusinessDatabase(appContext)).use {
            it.authorize(null,com.fgmachines.mikrotikmanager.business.BusinessPermission.VOUCHERS)
        }
    }
    private val prefs = context.getSharedPreferences(prefsName, Context.MODE_PRIVATE)
    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    suspend fun save(batch: VoucherBatch): String = withContext(Dispatchers.IO) {
        authorize()
        val timestamp = System.currentTimeMillis()
        val id = VoucherHistoryIndex.newId(timestamp)
        val plaintext = json.encodeToString(batch).toByteArray(Charsets.UTF_8)
        synchronized(WRITE_LOCK) {
            val encrypted = try { encrypt(plaintext) } finally { plaintext.fill(0) }
            val index = prefs.getStringSet(KEY_INDEX, emptySet()).orEmpty().toMutableSet()
            index += id
            check(prefs.edit()
                .putString(KEY_PREFIX + id, encrypted)
                .putStringSet(KEY_INDEX, index)
                .commit()) { "Could not save encrypted voucher history" }
        }

        id
    }

    suspend fun count(): Int = withContext(Dispatchers.IO) {
        authorize()
        prefs.getStringSet(KEY_INDEX, emptySet()).orEmpty().size
    }

    suspend fun recent(limit: Int = 20): List<SavedVoucherBatch> = page(limit.coerceIn(1, 100))

    /** Decrypt only this bounded page, never the entire archive. beforeId is the previous page cursor. */
    suspend fun page(limit: Int = 20, beforeId: String? = null): List<SavedVoucherBatch> =
        withContext(Dispatchers.IO) {
        authorize()
            VoucherHistoryIndex.page(prefs.getStringSet(KEY_INDEX, emptySet()).orEmpty(), limit, beforeId)
                .mapNotNull { id ->
                    val encoded = prefs.getString(KEY_PREFIX + id, null) ?: return@mapNotNull null
                    runCatching {
                        val batch = json.decodeFromString<VoucherBatch>(
                            decrypt(encoded).toString(Charsets.UTF_8)
                        )
                        SavedVoucherBatch(
                            id = id,
                            createdAtEpochMs = VoucherHistoryIndex.timestamp(id),
                            batch = batch
                        )
                    }.getOrNull()
                }

        }

    /** Fail closed if any legacy batch cannot be decrypted; never export a silently incomplete archive. */
    suspend fun exportPortable(password: CharArray): ByteArray = withContext(Dispatchers.IO) {
        authorize()
        val clear=synchronized(WRITE_LOCK) {
            val rows=org.json.JSONArray();var size=0L
            val ids=prefs.getStringSet(KEY_INDEX,emptySet()).orEmpty().sorted()
            require(ids.size<=10000) { "ARCHIVE_LIMIT" }
            for(id in ids) {
                val encoded=prefs.getString(KEY_PREFIX+id,null) ?: error("ARCHIVE_UNREADABLE")
                val bytes=decrypt(encoded)
                val batch=try { json.decodeFromString<VoucherBatch>(String(bytes,Charsets.UTF_8)) } finally { bytes.fill(0) }
                val row=org.json.JSONObject().put("id",id).put("batch",json.encodeToString(batch))
                size+=row.toString().toByteArray(Charsets.UTF_8).size
                require(size<com.fgmachines.mikrotikmanager.business.BusinessBackupCipher.MAX_BYTES-4096) { "FILE_TOO_LARGE" }
                rows.put(row)
            }
            org.json.JSONObject().put("format","FG-MTM-vouchers").put("schema",1).put("batches",rows).toString().toByteArray(Charsets.UTF_8)
        }
        try { com.fgmachines.mikrotikmanager.business.BusinessBackupCipher.encrypt(clear,password) } finally { clear.fill(0) }
    }

    /** Validate all rows before one atomic preference commit. Existing IDs must match exactly. */
    suspend fun importPortable(bytes: ByteArray,password: CharArray): Int = withContext(Dispatchers.IO) {
        authorize()
        val clear=com.fgmachines.mikrotikmanager.business.BusinessBackupCipher.decrypt(bytes,password)
        val root=try { org.json.JSONObject(String(clear,Charsets.UTF_8)) } finally { clear.fill(0) }
        require(root.getString("format")=="FG-MTM-vouchers" && root.getInt("schema")==1) { "INVALID_BACKUP" }
        val rows=root.getJSONArray("batches");require(rows.length()<=10000) { "ARCHIVE_LIMIT" }
        val batches=linkedMapOf<String,VoucherBatch>()
        for(i in 0 until rows.length()) {
            val row=rows.getJSONObject(i);val id=row.getString("id")
            require(id.matches(Regex("[0-9]{1,19}(-[a-fA-F0-9-]{36})?")) && VoucherHistoryIndex.timestamp(id)>0 && id !in batches) { "INVALID_BACKUP" }
            val batch=json.decodeFromString<VoucherBatch>(row.getString("batch"))
            require(batch.vouchers.size==batch.request.quantity && batch.vouchers.map { it.username }.toSet().size==batch.vouchers.size) { "INVALID_BACKUP" }
            require(batch.vouchers.all { it.username.isNotBlank() && it.username.length<=128 && it.password.length<=256 }) { "INVALID_BACKUP" }
            batches[id]=batch
        }
        synchronized(WRITE_LOCK) {
            val ids=prefs.getStringSet(KEY_INDEX,emptySet()).orEmpty().toMutableSet()
            require((ids+batches.keys).size<=10000) { "ARCHIVE_LIMIT" }
            val editor=prefs.edit();var added=0
            for((id,batch) in batches) {
                if(id in ids) {
                    val existing=prefs.getString(KEY_PREFIX+id,null) ?: error("ARCHIVE_UNREADABLE")
                    val decoded=decrypt(existing)
                    try { require(json.decodeFromString<VoucherBatch>(String(decoded,Charsets.UTF_8))==batch) { "ARCHIVE_CONFLICT" } } finally { decoded.fill(0) }
                } else {
                    val plain=json.encodeToString(batch).toByteArray(Charsets.UTF_8)
                    try { editor.putString(KEY_PREFIX+id,encrypt(plain)) } finally { plain.fill(0) }
                    ids+=id;added++
                }
            }
            if(added>0) check(editor.putStringSet(KEY_INDEX,ids).commit()) { "ARCHIVE_WRITE_FAILED" }
            added
        }
    }

    private fun encrypt(plaintext: ByteArray): String {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, getOrCreateKey())
        val encrypted = cipher.doFinal(plaintext)

        val payload = ByteArray(1 + cipher.iv.size + encrypted.size)
        payload[0] = cipher.iv.size.toByte()
        cipher.iv.copyInto(payload, destinationOffset = 1)
        encrypted.copyInto(payload, destinationOffset = 1 + cipher.iv.size)

        return Base64.encodeToString(payload, Base64.NO_WRAP)
    }

    private fun decrypt(encoded: String): ByteArray {
        val payload = Base64.decode(encoded, Base64.NO_WRAP)
        require(payload.isNotEmpty()) { "Encrypted voucher history is empty" }

        val ivLength = payload[0].toInt() and 0xFF
        require(ivLength in 12..32 && payload.size > 1 + ivLength) {
            "Encrypted voucher history is invalid"
        }

        val iv = payload.copyOfRange(1, 1 + ivLength)
        val encrypted = payload.copyOfRange(1 + ivLength, payload.size)

        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(
            Cipher.DECRYPT_MODE,
            getOrCreateKey(),
            GCMParameterSpec(128, iv)
        )
        return cipher.doFinal(encrypted)
    }

    private fun getOrCreateKey(): SecretKey {
        val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
        (keyStore.getKey(KEY_ALIAS, null) as? SecretKey)?.let { return it }

        val generator = KeyGenerator.getInstance(
            KeyProperties.KEY_ALGORITHM_AES,
            ANDROID_KEYSTORE
        )
        generator.init(
            KeyGenParameterSpec.Builder(
                KEY_ALIAS,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
            )
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setRandomizedEncryptionRequired(true)
                .build()
        )
        return generator.generateKey()
    }

    private companion object {
        val WRITE_LOCK = Any()
        const val PREFS_NAME = "fg_voucher_history"
        const val KEY_INDEX = "batch_index"
        const val KEY_PREFIX = "batch_"
        const val KEY_ALIAS = "fg_mikrotik_voucher_history_v1"
        const val ANDROID_KEYSTORE = "AndroidKeyStore"
        const val TRANSFORMATION = "AES/GCM/NoPadding"
    }
}
