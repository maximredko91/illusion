package com.seance.app.data.security

import android.content.Context
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import java.security.MessageDigest
import kotlin.random.Random

/**
 * Gates the developer-only "add media" scraper behind a password only the developer knows - not
 * real security (a decompiled APK could bypass the check), just enough to keep the feature out of
 * reach of anyone else using this same install. First-ever unlock attempt (no password set yet)
 * generates and shows a random password instead of asking the developer to invent/remember one;
 * from then on only its SHA-256 hash is kept, never the password itself.
 */
class DevAccessStore(context: Context) {
    private val masterKey = MasterKey.Builder(context)
        .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
        .build()

    private val prefs = EncryptedSharedPreferences.create(
        context,
        "dev_access",
        masterKey,
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
    )

    val hasPassword: Boolean get() = prefs.contains(KEY_HASH)

    /** Generates and stores a new random password, returning it in plaintext exactly once so the caller can show it to the developer. */
    fun generatePassword(): String {
        val password = (1..PASSWORD_LENGTH)
            .map { PASSWORD_ALPHABET[Random.nextInt(PASSWORD_ALPHABET.length)] }
            .joinToString("")
        prefs.edit().putString(KEY_HASH, hash(password)).apply()
        return password
    }

    fun verify(password: String): Boolean = prefs.getString(KEY_HASH, null) == hash(password)

    private fun hash(password: String): String =
        MessageDigest.getInstance("SHA-256")
            .digest(password.toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it) }

    companion object {
        private const val KEY_HASH = "password_hash"
        private const val PASSWORD_LENGTH = 10
        private const val PASSWORD_ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZabcdefghijkmnpqrstuvwxyz23456789"
    }
}
