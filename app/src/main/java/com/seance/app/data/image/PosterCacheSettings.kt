package com.seance.app.data.image

/**
 * Synchronous mirror of [com.seance.app.data.settings.SettingsRepository.posterCachingEnabled],
 * kept up to date by a coroutine in SeanceApplication. Coil's [Interceptor] runs on a hot path
 * that can't suspend on a DataStore read, so it reads this instead.
 */
object PosterCacheSettings {
    @Volatile
    var cachingEnabled: Boolean = true
}
