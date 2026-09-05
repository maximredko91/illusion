package com.illusion.app.data.security

import android.content.Context
import androidx.security.crypto.EncryptedSharedPreferences
import java.security.MessageDigest
import kotlin.random.Random

/**
 * Gates the developer-only "add media" scraper behind a password only the developer knows - not
 * real security (a decompiled APK could bypass the check), just enough to keep the feature out of
 * reach of anyone else using this same install. First-ever unlock attempt (no password set yet)
 * generates and shows a random password instead of asking the developer to invent/remember one;
 * from then on only its SHA-256 hash is kept, never the password itself.
 *
 * [buildTimePassword], if set via `local.properties`' `illusion.devAccess.password` (see
 * `app/build.gradle.kts`), is an alternative fixed password that survives an app
 * uninstall/data-clear - unlike the in-app-generated one, which lives in this install's
 * [EncryptedSharedPreferences] and resets with it. Deliberately plaintext-compiled into the APK
 * (like [com.illusion.app.data.tmdb.TmdbClient]'s local.properties fallback) rather than encrypted
 * there, since any on-device decryption key would have to ship in the same APK and so wouldn't
 * raise the real bar against a decompiled APK - see the KDoc above.
 */
class DevAccessStore(context: Context, private val buildTimePassword: String? = null) {
    private val prefs = com.illusion.app.data.security.openEncryptedPreferences(context, "dev_access")


    val hasPassword: Boolean get() = prefs.contains(KEY_HASH) || !buildTimePassword.isNullOrBlank()

    /**
     * True once the developer has verified the password on this device at least once - lets the
     * "add media" entry point skip re-prompting for it every time. Backed by the same
     * [EncryptedSharedPreferences] as the password hash itself (Keystore-backed AES256-GCM), not a
     * plaintext flag - clearing app data or uninstalling resets it, same as the password hash.
     */
    var isRemembered: Boolean
        get() = prefs.getBoolean(KEY_REMEMBERED, false)
        set(value) = prefs.edit().putBoolean(KEY_REMEMBERED, value).apply()

    /** Generates and stores a new random password, returning it in plaintext exactly once so the caller can show it to the developer. */
    fun generatePassword(): String {
        val password = (1..PASSWORD_LENGTH)
            .map { PASSWORD_ALPHABET[Random.nextInt(PASSWORD_ALPHABET.length)] }
            .joinToString("")
        prefs.edit().putString(KEY_HASH, hash(password)).apply()
        return password
    }

    fun verify(password: String): Boolean =
        prefs.getString(KEY_HASH, null) == hash(password) || (!buildTimePassword.isNullOrBlank() && password == buildTimePassword)

    /**
     * The TMDB API key, entered in-app (AddMediaScreen) rather than rebuilt in via
     * local.properties - stored encrypted here alongside the password hash since both are the
     * same "developer-only" secret area. Null if never set; [com.illusion.app.data.tmdb.TmdbClient]
     * falls back to the build-time `illusion.tmdb.apiKey` local.properties key when this is unset,
     * so either path keeps working.
     */
    var tmdbApiKey: String?
        get() = prefs.getString(KEY_TMDB_API_KEY, null)
        set(value) {
            prefs.edit().putString(KEY_TMDB_API_KEY, value?.takeIf { it.isNotBlank() }).apply()
        }

    /** Full factory reset - clears the password hash, remembered-device flag, and TMDB key together, same EncryptedSharedPreferences an uninstall/data-clear would already wipe. */
    fun clearAll() = prefs.edit().clear().apply()

    private fun hash(password: String): String =
        MessageDigest.getInstance("SHA-256")
            .digest(password.toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it) }

    companion object {
        private const val KEY_HASH = "password_hash"
        private const val KEY_REMEMBERED = "remembered"
        private const val KEY_TMDB_API_KEY = "tmdb_api_key"
        private const val PASSWORD_LENGTH = 10
        private const val PASSWORD_ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZabcdefghijkmnpqrstuvwxyz23456789"
    }
}
