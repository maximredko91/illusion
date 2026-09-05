package com.illusion.app.data.security

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey

/** Android can restore ciphertext without its Keystore key. Preserve that file and start a
 * separate credential store, instead of crashing or clearing the library on startup.
 *
 * A stale/invalidated Keystore key doesn't always surface as [java.security.GeneralSecurityException] -
 * on API 31+ it can come back as [android.security.KeyStoreException], which is a plain
 * [Exception], not a [java.security.GeneralSecurityException] subtype. Catching only the latter
 * left that case uncaught, crashing Application.onCreate() outright (observed via dropbox:
 * "Unable to create application ... Caused by: android.security.KeyStoreException: Signature/MAC
 * verification failed"). Catch broadly here since any failure opening/reading the encrypted store
 * should fall through to the same recovery path. */
internal fun openEncryptedPreferences(context: Context, name: String): SharedPreferences {
    val masterKey = MasterKey.Builder(context).setKeyScheme(MasterKey.KeyScheme.AES256_GCM).build()
    val recovery = context.getSharedPreferences("credential_recovery", Context.MODE_PRIVATE)
    fun open(fileName: String) = EncryptedSharedPreferences.create(
        context, fileName, masterKey,
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
    )
    if (recovery.getBoolean(name, false)) return open("${name}_recovered")
    return try {
        open(name)
    } catch (e: Exception) {
        val fresh = open("${name}_recovered")
        check(recovery.edit().putBoolean(name, true).commit()) { "Не удалось сохранить восстановление доступа" }
        fresh
    }
}
