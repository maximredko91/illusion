package com.seance.app.ui.common

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil3.compose.rememberAsyncImagePainter
import com.seance.app.R

private const val MIN_SCALE = 1f
private const val MAX_SCALE = 5f
private const val DOUBLE_TAP_SCALE = 2.5f

/**
 * Full-screen pinch-to-zoom/pan viewer for a poster or fanart image, e.g. from a details screen.
 * Double-tap toggles between fit and [DOUBLE_TAP_SCALE]; panning is clamped to identity once back
 * at 1x so the image doesn't drift off-screen while still zoomed out.
 */
@Composable
fun ZoomableImageViewer(model: Any, contentDescription: String?, onDismiss: () -> Unit) {
    Dialog(
        onDismissRequest = onDismiss,
        // decorFitsSystemWindows defaults to true, which gives this dialog its own separate,
        // non-edge-to-edge window - the Activity's enableEdgeToEdge() only applies to the main
        // window, not to a Dialog's. With the default, this dialog's own black background never
        // reaches the true status bar area, so the transparent system status bar shows whatever's
        // in the Activity window behind it (the Details screen's fanart) instead of this dialog's
        // black backdrop. False here (paired with usePlatformDefaultWidth=false, per this
        // property's own KDoc recommendation) makes the dialog genuinely edge-to-edge too.
        properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)
    ) {
        var scale by remember { mutableFloatStateOf(MIN_SCALE) }
        var offset by remember { mutableStateOf(Offset.Zero) }

        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black)
        ) {
            val painter = rememberAsyncImagePainter(model = model)
            Image(
                painter = painter,
                contentDescription = contentDescription,
                contentScale = ContentScale.Fit,
                modifier = Modifier
                    .fillMaxSize()
                    .pointerInput(Unit) {
                        detectTransformGestures { _, pan, zoom, _ ->
                            val newScale = (scale * zoom).coerceIn(MIN_SCALE, MAX_SCALE)
                            scale = newScale
                            offset = if (newScale <= MIN_SCALE) Offset.Zero else offset + pan
                        }
                    }
                    .pointerInput(Unit) {
                        detectTapGestures(
                            onDoubleTap = {
                                if (scale > MIN_SCALE) {
                                    scale = MIN_SCALE
                                    offset = Offset.Zero
                                } else {
                                    scale = DOUBLE_TAP_SCALE
                                }
                            }
                        )
                    }
                    .graphicsLayer(
                        scaleX = scale,
                        scaleY = scale,
                        translationX = offset.x,
                        translationY = offset.y
                    )
            )
            val closeSource = remember { MutableInteractionSource() }
            IconButton(
                onClick = onDismiss,
                interactionSource = closeSource,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .statusBarsPadding()
                    .padding(8.dp)
                    .focusHighlight(closeSource, color = Color.White)
            ) {
                Icon(
                    Icons.Default.Close,
                    contentDescription = stringResource(R.string.details_image_viewer_close),
                    tint = Color.White
                )
            }
        }
    }
}
