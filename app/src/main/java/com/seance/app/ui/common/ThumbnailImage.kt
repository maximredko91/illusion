package com.seance.app.ui.common

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import coil3.compose.AsyncImage

/**
 * Small poster/fanart thumbnail with a shimmer placeholder while Coil is still loading - for list
 * rows (History, Downloads) that show a small image next to text rather than a full [PosterCard].
 * Fills whatever sized+backgrounded [Box] the caller already wraps it in.
 *
 * AsyncImage (not rememberAsyncImagePainter+Image) so Coil sizes the decode to this Box's actual
 * layout size instead of the source image's full original resolution - rememberAsyncImagePainter
 * has no layout size to read, so every thumbnail here was decoding at full poster/fanart size.
 */
@Composable
fun ThumbnailImage(model: Any?, contentDescription: String?) {
    if (model == null) return
    var isLoading by remember { mutableStateOf(true) }
    AsyncImage(
        model = model,
        contentDescription = contentDescription,
        contentScale = ContentScale.Crop,
        modifier = Modifier.fillMaxSize(),
        onLoading = { isLoading = true },
        onSuccess = { isLoading = false },
        onError = { isLoading = false }
    )
    if (isLoading) {
        Box(modifier = Modifier.fillMaxSize().shimmer())
    }
}
