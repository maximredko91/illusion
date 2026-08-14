package com.seance.app.ui.smbsource

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import com.seance.app.data.smb.LocalNetworkPermission

/**
 * Wraps an SMB connection attempt (test/save) with a runtime permission request, so a denial
 * surfaces as a clear message instead of a silent socket timeout minutes later.
 */
@Composable
fun rememberLocalNetworkPermissionGate(onDenied: () -> Unit): (() -> Unit) -> Unit {
    val context = LocalContext.current
    var pendingAction by remember { mutableStateOf<(() -> Unit)?>(null) }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        val action = pendingAction
        pendingAction = null
        if (granted) action?.invoke() else onDenied()
    }
    return { action ->
        if (LocalNetworkPermission.isGranted(context)) {
            action()
        } else {
            pendingAction = action
            launcher.launch(LocalNetworkPermission.PERMISSION)
        }
    }
}
