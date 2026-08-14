package com.seance.app.data.smb

import android.content.Context
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey

class SmbCredentialStore(context: Context) {
    private val masterKey = MasterKey.Builder(context)
        .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
        .build()

    private val prefs = EncryptedSharedPreferences.create(
        context,
        "smb_credentials",
        masterKey,
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
    )

    fun getPassword(sourceId: Long): String? = prefs.getString(key(sourceId), null)

    fun setPassword(sourceId: Long, password: String) {
        prefs.edit().putString(key(sourceId), password).apply()
    }

    fun removePassword(sourceId: Long) {
        prefs.edit().remove(key(sourceId)).apply()
    }

    private fun key(sourceId: Long) = "source_$sourceId"
}
