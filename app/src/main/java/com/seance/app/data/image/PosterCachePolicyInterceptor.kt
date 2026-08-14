package com.seance.app.data.image

import coil3.Uri
import coil3.intercept.Interceptor
import coil3.request.CachePolicy
import coil3.request.ImageResult

/** Forces memory+disk caching on or off for `smb-image://` requests based on the user's poster-caching setting. */
class PosterCachePolicyInterceptor : Interceptor {
    override suspend fun intercept(chain: Interceptor.Chain): ImageResult {
        val data = chain.request.data
        if (data !is Uri || data.scheme != SmbImageUri.SCHEME) return chain.proceed()

        val policy = if (PosterCacheSettings.cachingEnabled) CachePolicy.ENABLED else CachePolicy.DISABLED
        val request = chain.request.newBuilder()
            .memoryCachePolicy(policy)
            .diskCachePolicy(policy)
            .diskCacheKey(data.toString())
            .build()
        return chain.withRequest(request).proceed()
    }
}
