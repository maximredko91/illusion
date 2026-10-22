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
    // create() только открывает файл, но ничего не расшифровывает, поэтому просроченный ключ
    // Keystore вылезает не здесь, а на первом чтении. Значит, единственная настоящая проверка -
    // прочитать все записи: без неё проба ниже проходила, а следующее же обращение бросало
    // исключение прямо из Application.onCreate() - ровно тот краш, ради которого этот файл и есть.
    fun openReadable(fileName: String): SharedPreferences? = try {
        open(fileName).also { it.all }
    } catch (e: Exception) {
        Log.w("EncryptedPreferences", "Не открылось хранилище $fileName", e)
        null
    }
    // Сбой Keystore бывает временным (так было после обновления 2026-09-23: пароль NAS «пропал»,
    // хотя исходный файл цел). Поэтому исходное хранилище пробуем всегда, даже после перехода
    // на восстановленное, и возвращаемся к нему, если оно снова открывается и в нём есть данные.
    val recovered = recovery.getBoolean(name, false)
    val original = openReadable(name)
    if (original != null && (!recovered || original.all.isNotEmpty())) {
        if (recovered) recovery.edit { remove(name) }
        return original
    }
    // См. BackupManager: нужен результат commit(), который KTX-шный edit {} не отдаёт.
    @Suppress("UseKtx")
    fun rememberRecovery() {
        if (!recovered) check(recovery.edit().putBoolean(name, true).commit()) { "Не удалось сохранить восстановление доступа" }
    }
    val fallbackName = "${name}_recovered"
    openReadable(fallbackName)?.let {
        rememberRecovery()
        return it
    }
    // Нечитаемым оказалось и восстановленное хранилище. Расшифровать его всё равно нечем, так что
    // терять в нём нечего, а другого способа вернуть рабочий SharedPreferences нет: либо снести
    // файл и начать заново, либо уронить запуск. Второе этот файл и призван не допускать.
    Log.w("EncryptedPreferences", "Хранилище $fallbackName тоже не читается, создаём заново")
    context.deleteSharedPreferences(fallbackName)
    val fresh = open(fallbackName)
    rememberRecovery()
    return fresh
}
