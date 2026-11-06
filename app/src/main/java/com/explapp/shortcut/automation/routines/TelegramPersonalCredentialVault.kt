package com.explapp.shortcut.automation.routines

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.nio.charset.StandardCharsets
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/** Device-bound credentials for reconnecting the opt-in TDLib user session after process death. */
internal object TelegramPersonalCredentialVault {
    private const val ALIAS = "shortcut_tdlib_user_api_credentials_v1"
    private const val PREF = "telegram_personal_vault"
    private const val KEY_IV = "iv"
    private const val KEY_DATA = "data"

    private fun secret(): SecretKey {
        val store = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (store.getKey(ALIAS, null) as? SecretKey)?.let { return it }
        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
        generator.init(
            KeyGenParameterSpec.Builder(ALIAS, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .build(),
        )
        return generator.generateKey()
    }

    fun save(context: Context, apiId: Int, apiHash: String): Boolean = runCatching {
        require(apiId > 0 && apiHash.isNotBlank())
        val cipher = Cipher.getInstance("AES/GCM/NoPadding").apply { init(Cipher.ENCRYPT_MODE, secret()) }
        val payload = "$apiId\n$apiHash".toByteArray(StandardCharsets.UTF_8)
        val encoded = cipher.doFinal(payload)
        context.getSharedPreferences(PREF, Context.MODE_PRIVATE).edit()
            .putString(KEY_IV, Base64.encodeToString(cipher.iv, Base64.NO_WRAP))
            .putString(KEY_DATA, Base64.encodeToString(encoded, Base64.NO_WRAP))
            .commit()
    }.getOrDefault(false)

    fun load(context: Context): Pair<Int, String>? = runCatching {
        val pref = context.getSharedPreferences(PREF, Context.MODE_PRIVATE)
        val iv = Base64.decode(pref.getString(KEY_IV, null) ?: return@runCatching null, Base64.NO_WRAP)
        val data = Base64.decode(pref.getString(KEY_DATA, null) ?: return null, Base64.NO_WRAP)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding").apply {
            init(Cipher.DECRYPT_MODE, secret(), GCMParameterSpec(128, iv))
        }
        val decrypted = String(cipher.doFinal(data), StandardCharsets.UTF_8).split("\n", limit = 2)
        val id = decrypted.getOrNull(0)?.toIntOrNull() ?: return null
        val hash = decrypted.getOrNull(1)?.takeIf { it.isNotBlank() } ?: return null
        id to hash
    }.getOrNull()

    fun clear(context: Context) {
        context.getSharedPreferences(PREF, Context.MODE_PRIVATE).edit().clear().apply()
        runCatching {
            KeyStore.getInstance("AndroidKeyStore").apply { load(null) }.deleteEntry(ALIAS)
        }
    }
}
