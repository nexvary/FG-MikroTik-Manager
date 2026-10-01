package com.fgmachines.mikrotikmanager.advanced

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

@Serializable
data class RouterChange(val at: Long, val ar: String, val en: String, val success: Boolean, val backup: String = "")
@Serializable
private data class VaultContents(val backups: Map<String, String> = emptyMap(), val changes: List<RouterChange> = emptyList())

/** Backup unlock secrets and audit history are authenticated-encrypted with an Android Keystore key. */
class RouterChangeVault(context: Context, routerKey: String) {
    private val prefs = context.getSharedPreferences("fg_router_changes", Context.MODE_PRIVATE)
    private val slot = "router-" + java.security.MessageDigest.getInstance("SHA-256").digest(routerKey.toByteArray()).joinToString("") { "%02x".format(it) }
    private val alias = "fg-mtm-router-backups-v1"
    private fun key(): SecretKey {
        val store = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (store.getKey(alias, null) as? SecretKey)?.let { return it }
        return KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore").apply {
            init(KeyGenParameterSpec.Builder(alias, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT).setBlockModes(KeyProperties.BLOCK_MODE_GCM).setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE).build())
        }.generateKey()
    }
    private fun contents(): VaultContents {
        val value = prefs.getString(slot, null) ?: return VaultContents()
        val bytes = Base64.decode(value, Base64.NO_WRAP)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(128, bytes.copyOfRange(0, 12)))
        return Json.decodeFromString(String(cipher.doFinal(bytes.copyOfRange(12, bytes.size)), Charsets.UTF_8))
    }
    private fun save(value: VaultContents) {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding").apply { init(Cipher.ENCRYPT_MODE, key()) }
        val bytes = cipher.iv + cipher.doFinal(Json.encodeToString(value).toByteArray())
        check(prefs.edit().putString(slot, Base64.encodeToString(bytes, Base64.NO_WRAP)).commit()) { "Could not protect backup secrets on this phone" }
    }
    fun password(file: String): String? = contents().backups[file]
    fun changes(): List<RouterChange> = contents().changes
    fun rememberBackup(file: String, password: String) { val value = contents(); save(value.copy(backups = value.backups + (file to password))) }
    fun record(ar: String, en: String, success: Boolean, backup: String = "") { val value = contents(); save(value.copy(changes = (listOf(RouterChange(System.currentTimeMillis(), ar, en, success, backup)) + value.changes).take(100))) }
}
