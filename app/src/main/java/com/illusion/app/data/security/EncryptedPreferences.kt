package com.illusion.app.data.security

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import androidx.core.content.edit
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
    // Сбой Keystore бывает временным (так было после обновления 2026-09-23: пароль NAS «пропал»,
    // хотя исходный файл цел). Поэтому исходное хранилище пробуем всегда, даже после перехода
    // на восстановленное, и возвращаемся к нему, если оно снова открывается и в нём есть данные.
    val recovered = recovery.getBoolean(name, false)
    val original = try {
        open(name)
    } catch (e: Exception) {
        Log.w("EncryptedPreferences", "Не открылось хранилище $name", e)
        null
    }
    if (original != null && (!recovered || original.all.isNotEmpty())) {
        if (recovered) recovery.edit { remove(name) }
        return original
    }
    if (recovered) return open("${name}_recovered")
    val fresh = open("${name}_recovered")
    // См. BackupManager: нужен результат commit(), который KTX-шный edit {} не отдаёт.
    @Suppress("UseKtx")
    check(recovery.edit().putBoolean(name, true).commit()) { "Не удалось сохранить восстановление доступа" }
    return fresh
}
