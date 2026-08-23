package com.illusion.app.data.smb

import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat

/**
 * Android 17+ (SDK 37) blocks raw local-network sockets - exactly how smbj connects - unless
 * this runtime permission is granted; below that level the permission doesn't exist and access
 * is implicit. The literal string is used instead of a Manifest.permission constant since this
 * permission is too new to safely assume the compileSdk's platform stub exposes it by name.
 */
object LocalNetworkPermission {
    const val PERMISSION = "android.permission.ACCESS_LOCAL_NETWORK"

    val isRequired: Boolean
        get() = Build.VERSION.SDK_INT >= 37

    fun isGranted(context: Context): Boolean =
        !isRequired || ContextCompat.checkSelfPermission(context, PERMISSION) == PackageManager.PERMISSION_GRANTED
}
