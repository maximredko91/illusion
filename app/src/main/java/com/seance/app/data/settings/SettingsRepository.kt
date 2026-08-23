package com.seance.app.data.settings

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.seance.app.domain.model.AccentColor
import com.seance.app.domain.model.PlayerMode
import com.seance.app.domain.model.SortOrder
import com.seance.app.domain.model.UiMode
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

private val Context.dataStore by preferencesDataStore(name = "settings")

private const val SUBTITLE_TEXT_COLOR_DEFAULT = -0x1 // android.graphics.Color.WHITE, opaque

/** Matches [com.seance.app.ui.player.SharpenEffect]'s own default `amount` param - kept in sync manually since the effect class can't depend on this module. */
const val SHARPEN_AMOUNT_DEFAULT = 0.4f

/** Default poster/fanart disk cache ceiling, in MB - read once at process start by SeanceApplication.newImageLoader(). 1GB comfortably fits a large library's posters+fanarts without relying on Coil's own 250MB default cap (too small - see the cache-growth fix this setting was added alongside). */
const val IMAGE_CACHE_LIMIT_MB_DEFAULT = 1024

class SettingsRepository(private val context: Context) {
    private object Keys {
        val REQUIRE_CHARGING_FOR_HEAVY_TASKS = booleanPreferencesKey("require_charging_for_heavy_tasks")
        val RESCAN_INTERVAL_HOURS = intPreferencesKey("rescan_interval_hours")
        val DEFAULT_SORT_ORDER = stringPreferencesKey("default_sort_order")
        val SHARPEN_ENABLED = booleanPreferencesKey("player_sharpen_enabled")
        val SHARPEN_AMOUNT = floatPreferencesKey("player_sharpen_amount")
        val SEEK_DURATION_SECONDS = intPreferencesKey("player_seek_duration_seconds")
        val POSTER_CACHING_ENABLED = booleanPreferencesKey("poster_caching_enabled")
        val IMAGE_CACHE_LIMIT_MB = intPreferencesKey("image_cache_limit_mb")
        val DOWNLOADS_FOLDER_URI = stringPreferencesKey("downloads_folder_uri")
        val UI_MODE = stringPreferencesKey("ui_mode")
        val HAPTICS_ENABLED = booleanPreferencesKey("haptics_enabled")
        val ACCENT_COLOR = stringPreferencesKey("accent_color")
        val PLAYER_MODE = stringPreferencesKey("player_mode")
        val PREDICTIVE_BACK_ENABLED = booleanPreferencesKey("predictive_back_enabled")
        val RECENT_SEARCHES = stringPreferencesKey("recent_searches")
        val DOUBLE_TAP_SEEK_ENABLED = booleanPreferencesKey("player_double_tap_seek_enabled")
        val SWIPE_SEEK_ENABLED = booleanPreferencesKey("player_swipe_seek_enabled")
        val HOLD_TO_SEEK_ENABLED = booleanPreferencesKey("player_hold_to_seek_enabled")
        val SHOW_TECHNICAL_INFO = booleanPreferencesKey("player_show_technical_info")
        val SUBTITLE_TEXT_COLOR = intPreferencesKey("player_subtitle_text_color")
        val SUBTITLE_BACKGROUND_OPACITY = intPreferencesKey("player_subtitle_background_opacity")
        val SUBTITLE_TEXT_SIZE_PERCENT = intPreferencesKey("player_subtitle_text_size_percent")
    }

    val requireChargingForHeavyTasks: Flow<Boolean> = context.dataStore.data.map {
        it[Keys.REQUIRE_CHARGING_FOR_HEAVY_TASKS] ?: true
    }

    val rescanIntervalHours: Flow<Int> = context.dataStore.data.map {
        it[Keys.RESCAN_INTERVAL_HOURS] ?: 48
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

    /** Intensity of the GPU sharpen shader's unsharp-mask kernel - see [com.seance.app.ui.player.SharpenEffect]'s own `amount` param. */
    val sharpenAmount: Flow<Float> = context.dataStore.data.map { it[Keys.SHARPEN_AMOUNT] ?: SHARPEN_AMOUNT_DEFAULT }

    suspend fun setSharpenAmount(value: Float) {
        context.dataStore.edit { it[Keys.SHARPEN_AMOUNT] = value }
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

    /** Ceiling for the poster/fanart disk cache, in MB - read once at process start (SeanceApplication.newImageLoader() is a synchronous Coil factory, not a suspend function), so a change here only takes effect after the app restarts. Exposed for users with limited device storage who'd rather cap this than let it grow toward the default 1GB. */
    val imageCacheLimitMb: Flow<Int> = context.dataStore.data.map { it[Keys.IMAGE_CACHE_LIMIT_MB] ?: IMAGE_CACHE_LIMIT_MB_DEFAULT }

    suspend fun setImageCacheLimitMb(value: Int) {
        context.dataStore.edit { it[Keys.IMAGE_CACHE_LIMIT_MB] = value }
    }

    /** null = use the default public Downloads/Seans location (see [com.seance.app.data.download.DownloadStorage]). */
    val downloadsFolderUri: Flow<String?> = context.dataStore.data.map { it[Keys.DOWNLOADS_FOLDER_URI] }

    suspend fun setDownloadsFolderUri(uri: String?) {
        context.dataStore.edit {
            if (uri == null) it.remove(Keys.DOWNLOADS_FOLDER_URI) else it[Keys.DOWNLOADS_FOLDER_URI] = uri
        }
    }

    /** null = not chosen yet - onboarding asks explicitly (no runtime android.software.leanback detection, the user picks). */
    val uiMode: Flow<UiMode?> = context.dataStore.data.map {
        it[Keys.UI_MODE]?.let { name -> runCatching { UiMode.valueOf(name) }.getOrNull() }
    }

    suspend fun setUiMode(mode: UiMode) {
        context.dataStore.edit { it[Keys.UI_MODE] = mode.name }
    }

    /** Global switch for haptic feedback (SeanceNavHost overrides LocalHapticFeedback app-wide with this, so every existing call site respects it without individual changes). */
    val hapticsEnabled: Flow<Boolean> = context.dataStore.data.map { it[Keys.HAPTICS_ENABLED] ?: true }

    suspend fun setHapticsEnabled(value: Boolean) {
        context.dataStore.edit { it[Keys.HAPTICS_ENABLED] = value }
    }

    /** DEFAULT keeps Material You wallpaper-based dynamic color (or the original hardcoded purple scheme below API 31) - any other value overrides the theme with a fixed accent instead. */
    val accentColor: Flow<AccentColor> = context.dataStore.data.map {
        it[Keys.ACCENT_COLOR]?.let { name -> runCatching { AccentColor.valueOf(name) }.getOrNull() }
            ?: AccentColor.DEFAULT
    }

    suspend fun setAccentColor(color: AccentColor) {
        context.dataStore.edit { it[Keys.ACCENT_COLOR] = color.name }
    }

    /** Which player handles playback - was a one-off "open in external player" action button inside the player itself, moved here as a persistent default per user feedback (choose once, not every time). */
    val playerMode: Flow<PlayerMode> = context.dataStore.data.map {
        it[Keys.PLAYER_MODE]?.let { name -> runCatching { PlayerMode.valueOf(name) }.getOrNull() }
            ?: PlayerMode.INTERNAL
    }

    suspend fun setPlayerMode(mode: PlayerMode) {
        context.dataStore.edit { it[Keys.PLAYER_MODE] = mode.name }
    }

    /** Tap the left/right half of the video twice to skip back/forward by [seekDurationSeconds]. */
    val doubleTapSeekEnabled: Flow<Boolean> = context.dataStore.data.map { it[Keys.DOUBLE_TAP_SEEK_ENABLED] ?: true }

    suspend fun setDoubleTapSeekEnabled(value: Boolean) {
        context.dataStore.edit { it[Keys.DOUBLE_TAP_SEEK_ENABLED] = value }
    }

    /** Drag horizontally anywhere on the video to scrub. */
    val swipeSeekEnabled: Flow<Boolean> = context.dataStore.data.map { it[Keys.SWIPE_SEEK_ENABLED] ?: true }

    suspend fun setSwipeSeekEnabled(value: Boolean) {
        context.dataStore.edit { it[Keys.SWIPE_SEEK_ENABLED] = value }
    }

    /** Press and hold the left/right half of the video to seek continuously while held. */
    val holdToSeekEnabled: Flow<Boolean> = context.dataStore.data.map { it[Keys.HOLD_TO_SEEK_ENABLED] ?: true }

    suspend fun setHoldToSeekEnabled(value: Boolean) {
        context.dataStore.edit { it[Keys.HOLD_TO_SEEK_ENABLED] = value }
    }

    /** Codec/resolution/HDR diagnostic block in the player's settings sheet - off by default so it doesn't clutter the panel for viewers who never asked for it. */
    val showTechnicalInfo: Flow<Boolean> = context.dataStore.data.map { it[Keys.SHOW_TECHNICAL_INFO] ?: false }

    suspend fun setShowTechnicalInfo(value: Boolean) {
        context.dataStore.edit { it[Keys.SHOW_TECHNICAL_INFO] = value }
    }

    /** Subtitle text color, an ARGB Int (android.graphics.Color-style) - default opaque white. */
    val subtitleTextColor: Flow<Int> = context.dataStore.data.map { it[Keys.SUBTITLE_TEXT_COLOR] ?: SUBTITLE_TEXT_COLOR_DEFAULT }

    suspend fun setSubtitleTextColor(colorArgb: Int) {
        context.dataStore.edit { it[Keys.SUBTITLE_TEXT_COLOR] = colorArgb }
    }

    /** 0 (no backdrop) to 100 (opaque black backdrop) behind each subtitle line. */
    val subtitleBackgroundOpacity: Flow<Int> = context.dataStore.data.map {
        it[Keys.SUBTITLE_BACKGROUND_OPACITY] ?: 60
    }

    suspend fun setSubtitleBackgroundOpacity(percent: Int) {
        context.dataStore.edit { it[Keys.SUBTITLE_BACKGROUND_OPACITY] = percent.coerceIn(0, 100) }
    }

    /** 50 (half size) to 200 (double size), 100 = Media3's own default fractional text size. */
    val subtitleTextSizePercent: Flow<Int> = context.dataStore.data.map {
        it[Keys.SUBTITLE_TEXT_SIZE_PERCENT] ?: 100
    }

    suspend fun setSubtitleTextSizePercent(percent: Int) {
        context.dataStore.edit { it[Keys.SUBTITLE_TEXT_SIZE_PERCENT] = percent.coerceIn(50, 200) }
    }

    /** Resets just the subtitle style (color/backdrop/size) - a narrower reset than [resetToDefaults] for when the user has fiddled with the sliders and just wants back to how it looked originally, without touching every other preference too. */
    suspend fun resetSubtitleStyle() {
        context.dataStore.edit {
            it.remove(Keys.SUBTITLE_TEXT_COLOR)
            it.remove(Keys.SUBTITLE_BACKGROUND_OPACITY)
            it.remove(Keys.SUBTITLE_TEXT_SIZE_PERCENT)
        }
    }

    /** Off falls back to an instant (non-animated) transition when popping a destination via back gesture/button, instead of the slide+fade NavHost transition that scrubs live with Android 13+'s predictive-back swipe preview - an escape hatch for anyone who finds that live-scrubbed animation distracting or janky on their device. */
    val predictiveBackEnabled: Flow<Boolean> = context.dataStore.data.map { it[Keys.PREDICTIVE_BACK_ENABLED] ?: true }

    suspend fun setPredictiveBackEnabled(value: Boolean) {
        context.dataStore.edit { it[Keys.PREDICTIVE_BACK_ENABLED] = value }
    }

    private val MAX_RECENT_SEARCHES = 10

    val recentSearches: Flow<List<String>> = context.dataStore.data.map {
        val raw = it[Keys.RECENT_SEARCHES] ?: return@map emptyList()
        runCatching { Json.decodeFromString<List<String>>(raw) }.getOrDefault(emptyList())
    }

    /** Moves [query] to the front (case-insensitive dedupe against any existing entry), capped at [MAX_RECENT_SEARCHES]. */
    suspend fun addRecentSearch(query: String) {
        val trimmed = query.trim()
        if (trimmed.isEmpty()) return
        context.dataStore.edit {
            val current = it[Keys.RECENT_SEARCHES]?.let { raw -> runCatching { Json.decodeFromString<List<String>>(raw) }.getOrDefault(emptyList()) } ?: emptyList()
            val updated = (listOf(trimmed) + current.filterNot { existing -> existing.equals(trimmed, ignoreCase = true) }).take(MAX_RECENT_SEARCHES)
            it[Keys.RECENT_SEARCHES] = Json.encodeToString(updated)
        }
    }

    suspend fun removeRecentSearch(query: String) {
        context.dataStore.edit {
            val current = it[Keys.RECENT_SEARCHES]?.let { raw -> runCatching { Json.decodeFromString<List<String>>(raw) }.getOrDefault(emptyList()) } ?: emptyList()
            it[Keys.RECENT_SEARCHES] = Json.encodeToString(current.filterNot { existing -> existing == query })
        }
    }

    suspend fun clearRecentSearches() {
        context.dataStore.edit { it.remove(Keys.RECENT_SEARCHES) }
    }

    /** Clears every preference here (sort order, haptics, seek duration, sharpen, poster caching, rescan interval, charging requirement, downloads folder, UI mode, player mode, gesture toggles, technical info, subtitle style, predictive back) back to its default - does not touch SMB sources or the library index, only this DataStore. */
    suspend fun resetToDefaults() {
        context.dataStore.edit { it.clear() }
    }
}
