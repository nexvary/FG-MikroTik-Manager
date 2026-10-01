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

class VoucherHistoryStore(context: Context) {
    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    suspend fun save(batch: VoucherBatch): String = withContext(Dispatchers.IO) {
        val timestamp = System.currentTimeMillis()
        val id = timestamp.toString()
        val plaintext = json.encodeToString(batch).toByteArray(Charsets.UTF_8)
        val encrypted = encrypt(plaintext)

        synchronized(this@VoucherHistoryStore) {
            val index = prefs.getStringSet(KEY_INDEX, emptySet()).orEmpty().toMutableSet()
            index += id
            prefs.edit()
                .putString(KEY_PREFIX + id, encrypted)
                .putStringSet(KEY_INDEX, index)
                .apply()
        }

        id
    }

    suspend fun count(): Int = withContext(Dispatchers.IO) {
        prefs.getStringSet(KEY_INDEX, emptySet()).orEmpty().size
    }

    suspend fun recent(limit: Int = 20): List<SavedVoucherBatch> =
        withContext(Dispatchers.IO) {
            prefs.getStringSet(KEY_INDEX, emptySet())
                .orEmpty()
                .mapNotNull { id ->
                    val encoded = prefs.getString(KEY_PREFIX + id, null) ?: return@mapNotNull null
                    runCatching {
                        val batch = json.decodeFromString<VoucherBatch>(
                            decrypt(encoded).toString(Charsets.UTF_8)
                        )
                        SavedVoucherBatch(
                            id = id,
                            createdAtEpochMs = id.toLongOrNull() ?: 0L,
                            batch = batch
                        )
                    }.getOrNull()
                }
                .sortedByDescending { it.createdAtEpochMs }
                .take(limit.coerceIn(1, 100))
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
        const val PREFS_NAME = "fg_voucher_history"
        const val KEY_INDEX = "batch_index"
        const val KEY_PREFIX = "batch_"
        const val KEY_ALIAS = "fg_mikrotik_voucher_history_v1"
        const val ANDROID_KEYSTORE = "AndroidKeyStore"
        const val TRANSFORMATION = "AES/GCM/NoPadding"
    }
}
