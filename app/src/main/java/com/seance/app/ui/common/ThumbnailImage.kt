package com.seance.app.ui.common

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import coil3.compose.AsyncImagePainter
import coil3.compose.rememberAsyncImagePainter

/**
 * Small poster/fanart thumbnail with a shimmer placeholder while Coil is still loading - for list
 * rows (History, Downloads) that show a small image next to text rather than a full [PosterCard].
 * Fills whatever sized+backgrounded [Box] the caller already wraps it in.
 */
@Composable
fun ThumbnailImage(model: Any?, contentDescription: String?) {
    if (model == null) return
    val painter = rememberAsyncImagePainter(model = model, contentScale = ContentScale.Crop)
    val state by painter.state.collectAsState()
    Image(
        painter = painter,
        contentDescription = contentDescription,
        contentScale = ContentScale.Crop,
        modifier = Modifier.fillMaxSize()
    )
    if (state is AsyncImagePainter.State.Loading) {
        Box(modifier = Modifier.fillMaxSize().shimmer())
    }
}
