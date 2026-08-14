package com.seance.app

import android.app.PictureInPictureParams
import android.content.res.Configuration
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import com.seance.app.ui.navigation.SeanceNavHost
import com.seance.app.ui.player.PipController
import com.seance.app.ui.theme.SeanceTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val app = application as SeanceApplication
        setContent {
            SeanceTheme {
                SeanceNavHost(
                    app = app,
                    modifier = Modifier.fillMaxSize()
                )
            }
        }
    }

    override fun onUserLeaveHint() {
        super.onUserLeaveHint()
        if (PipController.isPlayerActive) {
            val params = PictureInPictureParams.Builder()
                .setAspectRatio(PipController.aspectRatio)
                .build()
            enterPictureInPictureMode(params)
        }
    }

    override fun onPictureInPictureModeChanged(isInPictureInPictureMode: Boolean, newConfig: Configuration) {
        super.onPictureInPictureModeChanged(isInPictureInPictureMode, newConfig)
        PipController.isInPipMode = isInPictureInPictureMode
    }
}
