package com.seance.app.data.settings

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.seance.app.domain.model.SortOrder
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore by preferencesDataStore(name = "settings")

class SettingsRepository(private val context: Context) {
    private object Keys {
        val REQUIRE_CHARGING_FOR_HEAVY_TASKS = booleanPreferencesKey("require_charging_for_heavy_tasks")
        val RESCAN_INTERVAL_HOURS = intPreferencesKey("rescan_interval_hours")
        val DEFAULT_SORT_ORDER = stringPreferencesKey("default_sort_order")
        val SHARPEN_ENABLED = booleanPreferencesKey("player_sharpen_enabled")
        val SEEK_DURATION_SECONDS = intPreferencesKey("player_seek_duration_seconds")
        val POSTER_CACHING_ENABLED = booleanPreferencesKey("poster_caching_enabled")
        val DOWNLOADS_FOLDER_URI = stringPreferencesKey("downloads_folder_uri")
    }

    val requireChargingForHeavyTasks: Flow<Boolean> = context.dataStore.data.map {
        it[Keys.REQUIRE_CHARGING_FOR_HEAVY_TASKS] ?: true
    }

    val rescanIntervalHours: Flow<Int> = context.dataStore.data.map {
        it[Keys.RESCAN_INTERVAL_HOURS] ?: 24
    }

    val defaultSortOrder: Flow<SortOrder> = context.dataStore.data.map {
        it[Keys.DEFAULT_SORT_ORDER]?.let { name -> runCatching { SortOrder.valueOf(name) }.getOrNull() }
            ?: SortOrder.DATE_ADDED
    }

    /** GPU sharpen shader toggle in the player's settings sheet - off by default so it doesn't load the GPU for content that's already good quality. */
    val sharpenEnabled: Flow<Boolean> = context.dataStore.data.map { it[Keys.SHARPEN_ENABLED] ?: false }

    suspend fun setSharpenEnabled(value: Boolean) {
        context.dataStore.edit { it[Keys.SHARPEN_ENABLED] = value }
    }

    /** Double-tap seek step in the player, user-adjustable 5-30s in Settings. */
    val seekDurationSeconds: Flow<Int> = context.dataStore.data.map {
        it[Keys.SEEK_DURATION_SECONDS] ?: 10
    }

    suspend fun setSeekDurationSeconds(seconds: Int) {
        context.dataStore.edit { it[Keys.SEEK_DURATION_SECONDS] = seconds.coerceIn(5, 30) }
    }

    suspend fun setRequireChargingForHeavyTasks(value: Boolean) {
        context.dataStore.edit { it[Keys.REQUIRE_CHARGING_FOR_HEAVY_TASKS] = value }
    }

    suspend fun setRescanIntervalHours(hours: Int) {
        context.dataStore.edit { it[Keys.RESCAN_INTERVAL_HOURS] = hours }
    }

    suspend fun setDefaultSortOrder(order: SortOrder) {
        context.dataStore.edit { it[Keys.DEFAULT_SORT_ORDER] = order.name }
    }

    /** Whether posters/fanart are allowed to hit Coil's memory/disk cache - off skips both so nothing lingers on storage. */
    val posterCachingEnabled: Flow<Boolean> = context.dataStore.data.map {
        it[Keys.POSTER_CACHING_ENABLED] ?: true
    }

    suspend fun setPosterCachingEnabled(value: Boolean) {
        context.dataStore.edit { it[Keys.POSTER_CACHING_ENABLED] = value }
    }

    /** null = use the default public Downloads/Seans location (see [com.seance.app.data.download.DownloadStorage]). */
    val downloadsFolderUri: Flow<String?> = context.dataStore.data.map { it[Keys.DOWNLOADS_FOLDER_URI] }

    suspend fun setDownloadsFolderUri(uri: String?) {
        context.dataStore.edit {
            if (uri == null) it.remove(Keys.DOWNLOADS_FOLDER_URI) else it[Keys.DOWNLOADS_FOLDER_URI] = uri
        }
    }
}
