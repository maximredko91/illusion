package com.illusion.app.data.settings

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.illusion.app.domain.model.AccentColor
import com.illusion.app.domain.model.PlayerBufferSize
import com.illusion.app.domain.model.PlayerMode
import com.illusion.app.domain.model.SortOrder
import com.illusion.app.domain.model.UiMode
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

private val Context.dataStore by preferencesDataStore(name = "settings")

private const val SUBTITLE_TEXT_COLOR_DEFAULT = -0x1 // android.graphics.Color.WHITE, opaque

/** Matches [com.illusion.app.ui.player.SharpenEffect]'s own default `amount` param - kept in sync manually since the effect class can't depend on this module. */
const val SHARPEN_AMOUNT_DEFAULT = 0.4f

/** Default poster/fanart disk cache ceiling, in MB - read once at process start by IllusionApplication.newImageLoader(). 1GB comfortably fits a large library's posters+fanarts without relying on Coil's own 250MB default cap (too small - see the cache-growth fix this setting was added alongside). */
const val IMAGE_CACHE_LIMIT_MB_DEFAULT = 1024

class SettingsRepository(private val context: Context) {
    private object Keys {
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
        val THEME_MODE = stringPreferencesKey("theme_mode")
        val PLAYER_MODE = stringPreferencesKey("player_mode")
        val EXTERNAL_PLAYER_PACKAGE = stringPreferencesKey("external_player_package")
        val PREDICTIVE_BACK_ENABLED = booleanPreferencesKey("predictive_back_enabled")
        val RECENT_SEARCHES = stringPreferencesKey("recent_searches")
        val DOUBLE_TAP_SEEK_ENABLED = booleanPreferencesKey("player_double_tap_seek_enabled")
        val SWIPE_SEEK_ENABLED = booleanPreferencesKey("player_swipe_seek_enabled")
        val HOLD_TO_SEEK_ENABLED = booleanPreferencesKey("player_hold_to_seek_enabled")
        val SUBTITLE_TEXT_COLOR = intPreferencesKey("player_subtitle_text_color")
        val SUBTITLE_BACKGROUND_OPACITY = intPreferencesKey("player_subtitle_background_opacity")
        val SUBTITLE_TEXT_SIZE_PERCENT = intPreferencesKey("player_subtitle_text_size_percent")
        val SKIPPED_UPDATE_VERSION_CODE = intPreferencesKey("skipped_update_version_code")
        val LAST_UPDATE_CHECK_AT_MS = longPreferencesKey("last_update_check_at_ms")
        val UPDATE_CHECK_INTERVAL_HOURS = intPreferencesKey("update_check_interval_hours")
        val TV_OVERSCAN_MARGIN_PERCENT = intPreferencesKey("tv_overscan_margin_percent")
        val UPDATE_SOURCE = stringPreferencesKey("update_source")
        val LOCAL_UPDATE_SOURCE_ID = longPreferencesKey("local_update_source_id")
        val PLAYER_BUFFER_SIZE = stringPreferencesKey("player_buffer_size")
        val CUES_SEEK_WORKAROUND_STABLE_IDS = stringPreferencesKey("cues_seek_workaround_stable_ids")
        val PERFORMANCE_MODE = stringPreferencesKey("performance_mode")
    }

    val defaultSortOrder: Flow<SortOrder> = context.dataStore.data.map {
        it[Keys.DEFAULT_SORT_ORDER]?.let { name -> runCatching { SortOrder.valueOf(name) }.getOrNull() }
            ?: SortOrder.RATING
    }

    /** GPU sharpen shader toggle in the player's settings sheet - off by default so it doesn't load the GPU for content that's already good quality. */
    val sharpenEnabled: Flow<Boolean> = context.dataStore.data.map { it[Keys.SHARPEN_ENABLED] ?: false }

    suspend fun setSharpenEnabled(value: Boolean) {
        context.dataStore.edit { it[Keys.SHARPEN_ENABLED] = value }
    }

    /** Intensity of the GPU sharpen shader's unsharp-mask kernel - see [com.illusion.app.ui.player.SharpenEffect]'s own `amount` param. */
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

    suspend fun setDefaultSortOrder(order: SortOrder) {
        context.dataStore.edit { it[Keys.DEFAULT_SORT_ORDER] = order.name }
    }

    /** No UI toggle any more (Settings > Cache dropped it for a plain "clear" action) - kept readable-only since PosterCacheSettings/LibraryScanWorker still gate on it and always see the default. */
    val posterCachingEnabled: Flow<Boolean> = context.dataStore.data.map {
        it[Keys.POSTER_CACHING_ENABLED] ?: true
    }

    /** Ceiling for the poster/fanart disk cache, in MB - read once at process start (IllusionApplication.newImageLoader() is a synchronous Coil factory, not a suspend function), so a change here only takes effect after the app restarts. Exposed for users with limited device storage who'd rather cap this than let it grow toward the default 1GB. */
    val imageCacheLimitMb: Flow<Int> = context.dataStore.data.map { it[Keys.IMAGE_CACHE_LIMIT_MB] ?: IMAGE_CACHE_LIMIT_MB_DEFAULT }

    suspend fun setImageCacheLimitMb(value: Int) {
        context.dataStore.edit { it[Keys.IMAGE_CACHE_LIMIT_MB] = value }
    }

    /** null = use the default public Downloads/Illusion location (see [com.illusion.app.data.download.DownloadStorage]). */
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

    /** TV-mode-only safe-area margin (0-10, percent of each screen dimension) - a real Android TV
     * box crops a slice off every edge that nothing this app draws is actually visible in, but
     * exactly how much varies by device/panel (confirmed two different real boxes needing two
     * different values, 8% not being enough for one and being way too much - "огромные черные
     * отступы" - on another that apparently barely crops at all). A single hardcoded guess can't
     * fit every device, so this is user-adjustable instead (Settings, TV mode only) rather than
     * re-guessed in code every time a new device is tested. Default 0 - safest starting point for
     * an unknown device is no margin at all rather than assuming a crop that isn't really there. */
    val tvOverscanMarginPercent: Flow<Int> = context.dataStore.data.map { it[Keys.TV_OVERSCAN_MARGIN_PERCENT] ?: 0 }

    suspend fun setTvOverscanMarginPercent(percent: Int) {
        context.dataStore.edit { it[Keys.TV_OVERSCAN_MARGIN_PERCENT] = percent.coerceIn(0, 10) }
    }

    /** Global switch for haptic feedback (IllusionNavHost overrides LocalHapticFeedback app-wide with this, so every existing call site respects it without individual changes). */
    val hapticsEnabled: Flow<Boolean> = context.dataStore.data.map { it[Keys.HAPTICS_ENABLED] ?: true }

    suspend fun setHapticsEnabled(value: Boolean) {
        context.dataStore.edit { it[Keys.HAPTICS_ENABLED] = value }
    }

    /** ILLUSION (the app's own brand crimson, matching the launcher icon/splash) is the actual out-of-box default - not AccentColor.DEFAULT, which would let Material You's wallpaper-based dynamic color override the brand on API 31+ and undercut the "one consistent style" the icon/splash rename was for. DEFAULT is still a real selectable option in Settings for anyone who wants dynamic color instead. */
    val accentColor: Flow<AccentColor> = context.dataStore.data.map {
        it[Keys.ACCENT_COLOR]?.let { name -> runCatching { AccentColor.valueOf(name) }.getOrNull() }
            ?: AccentColor.ILLUSION
    }

    suspend fun setAccentColor(color: AccentColor) {
        context.dataStore.edit { it[Keys.ACCENT_COLOR] = color.name }
    }

    val themeMode: Flow<com.illusion.app.domain.model.ThemeMode> = context.dataStore.data.map {
        it[Keys.THEME_MODE]?.let { name -> runCatching { com.illusion.app.domain.model.ThemeMode.valueOf(name) }.getOrNull() }
            ?: com.illusion.app.domain.model.ThemeMode.SYSTEM
    }

    suspend fun setThemeMode(mode: com.illusion.app.domain.model.ThemeMode) {
        context.dataStore.edit { it[Keys.THEME_MODE] = mode.name }
    }

    /** Which player handles playback - was a one-off "open in external player" action button inside the player itself, moved here as a persistent default per user feedback (choose once, not every time). */
    val playerMode: Flow<PlayerMode> = context.dataStore.data.map {
        it[Keys.PLAYER_MODE]?.let { name -> runCatching { PlayerMode.valueOf(name) }.getOrNull() }
            ?: PlayerMode.INTERNAL
    }

    suspend fun setPlayerMode(mode: PlayerMode) {
        context.dataStore.edit { it[Keys.PLAYER_MODE] = mode.name }
    }

    /** Defaults to INCREASED (not AUTO) - the bandwidth-adaptive default turned out too small for high-bitrate 4K remuxes on a real home Wi-Fi link, causing frequent rebuffering. */
    val playerBufferSize: Flow<PlayerBufferSize> = context.dataStore.data.map {
        it[Keys.PLAYER_BUFFER_SIZE]?.let { name -> runCatching { PlayerBufferSize.valueOf(name) }.getOrNull() }
            ?: PlayerBufferSize.INCREASED
    }

    suspend fun setPlayerBufferSize(size: PlayerBufferSize) {
        context.dataStore.edit { it[Keys.PLAYER_BUFFER_SIZE] = size.name }
    }

    /** Defaults to AUTO - see [com.illusion.app.domain.model.PerformanceMode]'s own KDoc for what this drives and how AUTO resolves. */
    val performanceMode: Flow<com.illusion.app.domain.model.PerformanceMode> = context.dataStore.data.map {
        it[Keys.PERFORMANCE_MODE]?.let { name -> runCatching { com.illusion.app.domain.model.PerformanceMode.valueOf(name) }.getOrNull() }
            ?: com.illusion.app.domain.model.PerformanceMode.AUTO
    }

    suspend fun setPerformanceMode(mode: com.illusion.app.domain.model.PerformanceMode) {
        context.dataStore.edit { it[Keys.PERFORMANCE_MODE] = mode.name }
    }

    /**
     * stableIds of files whose MatroskaExtractor Cues table is pathological enough to hang the
     * player forever in BUFFERING (observed on one real 38GB/76-chapter file - see project memory
     * "Avatar infinite buffering bug"). Disabling Cues-based seeking fixes the hang but also makes
     * that one file entirely non-seekable (MatroskaExtractor falls back to SeekMap.Unseekable, not
     * a less-precise seek map), so it can't be a global setting - PlayerViewModel auto-detects the
     * hang once (a stall watchdog on first play) and remembers the file here so every later replay
     * skips straight to the workaround instead of hanging again first.
     */
    val cuesSeekWorkaroundStableIds: Flow<Set<String>> = context.dataStore.data.map {
        val raw = it[Keys.CUES_SEEK_WORKAROUND_STABLE_IDS] ?: return@map emptySet()
        runCatching { Json.decodeFromString<Set<String>>(raw) }.getOrDefault(emptySet())
    }

    suspend fun addCuesSeekWorkaroundStableId(stableId: String) {
        context.dataStore.edit {
            val current = it[Keys.CUES_SEEK_WORKAROUND_STABLE_IDS]?.let { raw ->
                runCatching { Json.decodeFromString<Set<String>>(raw) }.getOrDefault(emptySet())
            } ?: emptySet()
            it[Keys.CUES_SEEK_WORKAROUND_STABLE_IDS] = Json.encodeToString(current + stableId)
        }
    }

    /** Package name of the specific external player app to hand playback to, chosen from InstalledPlayerApps' scan - null means let Android decide (its own disambiguation dialog if more than one app matches, or launch the sole match directly), which was this setting's implicit default before it existed. */
    val externalPlayerPackage: Flow<String?> = context.dataStore.data.map { it[Keys.EXTERNAL_PLAYER_PACKAGE] }

    suspend fun setExternalPlayerPackage(packageName: String?) {
        context.dataStore.edit {
            if (packageName == null) it.remove(Keys.EXTERNAL_PLAYER_PACKAGE) else it[Keys.EXTERNAL_PLAYER_PACKAGE] = packageName
        }
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

    /** versionCode the user explicitly chose "skip this version" for on the update dialog - null once a newer release supersedes it, so skipping v70 doesn't also silently skip v71. */
    val skippedUpdateVersionCode: Flow<Int?> = context.dataStore.data.map { it[Keys.SKIPPED_UPDATE_VERSION_CODE] }

    suspend fun setSkippedUpdateVersionCode(versionCode: Int) {
        context.dataStore.edit { it[Keys.SKIPPED_UPDATE_VERSION_CODE] = versionCode }
    }

    /** Throttles the automatic on-launch update check (see MainActivity) - the manual "Проверить обновления" button in Settings bypasses this and always hits the network. */
    val lastUpdateCheckAtMs: Flow<Long> = context.dataStore.data.map { it[Keys.LAST_UPDATE_CHECK_AT_MS] ?: 0L }

    suspend fun setLastUpdateCheckAtMs(timestamp: Long) {
        context.dataStore.edit { it[Keys.LAST_UPDATE_CHECK_AT_MS] = timestamp }
    }

    /** How often MainActivity's automatic on-launch check is allowed to hit the network - 0 disables it entirely (manual "Проверить обновления" in Settings still always works). Defaults to once a month (720h), same "0 = off" convention as [rescanIntervalHours]. */
    val updateCheckIntervalHours: Flow<Int> = context.dataStore.data.map { it[Keys.UPDATE_CHECK_INTERVAL_HOURS] ?: 720 }

    suspend fun setUpdateCheckIntervalHours(hours: Int) {
        context.dataStore.edit { it[Keys.UPDATE_CHECK_INTERVAL_HOURS] = hours }
    }

    /** GitHub (default, needs internet) or a manifest+APKs published to a local SMB source (see
     * LocalUpdateChecker) - lets a device with flaky/no internet but a working connection to the
     * home NAS still get updates. */
    val updateSource: Flow<com.illusion.app.domain.model.UpdateSource> = context.dataStore.data.map {
        it[Keys.UPDATE_SOURCE]?.let { name -> runCatching { com.illusion.app.domain.model.UpdateSource.valueOf(name) }.getOrNull() }
            ?: com.illusion.app.domain.model.UpdateSource.GITHUB
    }

    suspend fun setUpdateSource(source: com.illusion.app.domain.model.UpdateSource) {
        context.dataStore.edit { it[Keys.UPDATE_SOURCE] = source.name }
    }

    /** Which configured SMB source hosts the update manifest, when [updateSource] is LOCAL - null until the user picks one in Settings. */
    val localUpdateSourceId: Flow<Long?> = context.dataStore.data.map { it[Keys.LOCAL_UPDATE_SOURCE_ID] }

    suspend fun setLocalUpdateSourceId(sourceId: Long) {
        context.dataStore.edit { it[Keys.LOCAL_UPDATE_SOURCE_ID] = sourceId }
    }
}
