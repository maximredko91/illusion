package com.illusion.app.data.smb

import android.content.Context
import androidx.core.content.edit
import androidx.security.crypto.EncryptedSharedPreferences

class SmbCredentialStore(context: Context) {
    private val prefs = com.illusion.app.data.security.openEncryptedPreferences(context, "smb_credentials")


    fun getPassword(sourceId: Long): String? = prefs.getString(key(sourceId), null)

    fun setPassword(sourceId: Long, password: String) {
        prefs.edit { putString(key(sourceId), password) }
    }

    fun removePassword(sourceId: Long) {
        prefs.edit { remove(key(sourceId)) }
    }

    private fun key(sourceId: Long) = "source_$sourceId"
}
