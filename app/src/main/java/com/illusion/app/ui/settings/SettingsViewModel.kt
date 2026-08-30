package com.illusion.app.ui.settings

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import coil3.SingletonImageLoader
import com.illusion.app.R
import com.illusion.app.data.backup.BackupManager
import com.illusion.app.data.backup.BackupSource
import com.illusion.app.data.local.entity.SmbSourceEntity
import com.illusion.app.data.repository.DownloadRepository
import com.illusion.app.data.repository.LibraryRepository
import com.illusion.app.data.repository.SmbSourceRepository
import com.illusion.app.data.repository.ThumbnailRepository
import com.illusion.app.data.repository.WatchProgressRepository
import com.illusion.app.data.security.DevAccessStore
import com.illusion.app.data.settings.SettingsRepository
import com.illusion.app.domain.model.PlayerMode
import com.illusion.app.domain.model.SortOrder
import com.illusion.app.domain.model.UiMode
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class SettingsViewModel(
    private val smbSourceRepository: SmbSourceRepository,
    private val settingsRepository: SettingsRepository,
    private val thumbnailRepository: ThumbnailRepository,
    private val downloadRepository: DownloadRepository,
    private val backupManager: BackupManager,
    private val devAccessStore: DevAccessStore,
    private val libraryRepository: LibraryRepository,
    private val watchProgressRepository: WatchProgressRepository
) : ViewModel() {
    fun hasDevPassword(): Boolean = devAccessStore.hasPassword
    fun generateDevPassword(): String = devAccessStore.generatePassword()
    fun verifyDevPassword(password: String): Boolean = devAccessStore.verify(password)
    fun isDevAccessRemembered(): Boolean = devAccessStore.isRemembered
    fun rememberDevAccess() { devAccessStore.isRemembered = true }
    fun forgetDevAccess() { devAccessStore.isRemembered = false }
    val sources: StateFlow<List<SmbSourceEntity>> = smbSourceRepository.observeSources()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val imageCacheLimitMb: Flow<Int> = settingsRepository.imageCacheLimitMb

    fun setImageCacheLimitMb(value: Int) {
        viewModelScope.launch { settingsRepository.setImageCacheLimitMb(value) }
    }
    val downloadsFolderUri: Flow<String?> = settingsRepository.downloadsFolderUri
    val uiMode: Flow<UiMode?> = settingsRepository.uiMode
    val tvOverscanMarginPercent: Flow<Int> = settingsRepository.tvOverscanMarginPercent

    fun setTvOverscanMarginPercent(percent: Int) {
        viewModelScope.launch { settingsRepository.setTvOverscanMarginPercent(percent) }
    }
    val defaultSortOrder: Flow<SortOrder> = settingsRepository.defaultSortOrder
    val hapticsEnabled: Flow<Boolean> = settingsRepository.hapticsEnabled
    val predictiveBackEnabled: Flow<Boolean> = settingsRepository.predictiveBackEnabled
    val glassEffectEnabled: Flow<Boolean> = settingsRepository.glassEffectEnabled
    val accentColor: Flow<com.illusion.app.domain.model.AccentColor> = settingsRepository.accentColor
    val themeMode: Flow<com.illusion.app.domain.model.ThemeMode> = settingsRepository.themeMode
    val playerMode: Flow<PlayerMode> = settingsRepository.playerMode

    fun setThemeMode(mode: com.illusion.app.domain.model.ThemeMode) {
        viewModelScope.launch { settingsRepository.setThemeMode(mode) }
    }

    fun setAccentColor(color: com.illusion.app.domain.model.AccentColor) {
        viewModelScope.launch { settingsRepository.setAccentColor(color) }
    }

    fun setPlayerMode(mode: PlayerMode) {
        viewModelScope.launch { settingsRepository.setPlayerMode(mode) }
    }

    val externalPlayerPackage: Flow<String?> = settingsRepository.externalPlayerPackage

    fun setExternalPlayerPackage(packageName: String?) {
        viewModelScope.launch { settingsRepository.setExternalPlayerPackage(packageName) }
    }

    val playerBufferSize: Flow<com.illusion.app.domain.model.PlayerBufferSize> = settingsRepository.playerBufferSize

    fun setPlayerBufferSize(size: com.illusion.app.domain.model.PlayerBufferSize) {
        viewModelScope.launch { settingsRepository.setPlayerBufferSize(size) }
    }

    val performanceMode: Flow<com.illusion.app.domain.model.PerformanceMode> = settingsRepository.performanceMode

    fun setPerformanceMode(mode: com.illusion.app.domain.model.PerformanceMode) {
        viewModelScope.launch { settingsRepository.setPerformanceMode(mode) }
    }

    fun setDefaultSortOrder(order: SortOrder) {
        viewModelScope.launch { settingsRepository.setDefaultSortOrder(order) }
    }

    fun setHapticsEnabled(enabled: Boolean) {
        viewModelScope.launch { settingsRepository.setHapticsEnabled(enabled) }
    }

    fun setPredictiveBackEnabled(enabled: Boolean) {
        viewModelScope.launch { settingsRepository.setPredictiveBackEnabled(enabled) }
    }

    fun setGlassEffectEnabled(enabled: Boolean) {
        viewModelScope.launch { settingsRepository.setGlassEffectEnabled(enabled) }
    }

    fun resetToDefaults() {
        viewModelScope.launch { settingsRepository.resetToDefaults() }
    }

    /**
     * True factory reset - unlike [resetToDefaults] (settings/preferences only), this wipes every
     * piece of app data: SMB sources + their stored credentials, the whole library index,
     * favorites/watch history, downloaded files, cached thumbnails/posters, the dev-access
     * password, and finally the settings themselves.
     */
    fun factoryReset(context: Context) {
        viewModelScope.launch {
            downloadRepository.removeAll()
            libraryRepository.clearAll()
            watchProgressRepository.clearHistory()
            watchProgressRepository.clearFavorites()
            thumbnailRepository.clearAll()
            smbSourceRepository.deleteAllSources()
            devAccessStore.clearAll()
            settingsRepository.resetToDefaults()
            withContext(Dispatchers.IO) { context.cacheDir.deleteRecursively() }
            refreshCacheSize(context)
            refreshDownloadsSize()
        }
    }

    private val _cacheSizeBytes = MutableStateFlow<Long?>(null)
    val cacheSizeBytes: StateFlow<Long?> = _cacheSizeBytes.asStateFlow()

    private val _fanartCacheSizeBytes = MutableStateFlow<Long?>(null)
    val fanartCacheSizeBytes: StateFlow<Long?> = _fanartCacheSizeBytes.asStateFlow()

    private val _posterCacheSizeBytes = MutableStateFlow<Long?>(null)
    val posterCacheSizeBytes: StateFlow<Long?> = _posterCacheSizeBytes.asStateFlow()

    private val _downloadsSizeBytes = MutableStateFlow<Long?>(null)
    val downloadsSizeBytes: StateFlow<Long?> = _downloadsSizeBytes.asStateFlow()

    fun setSourceEnabled(source: SmbSourceEntity, enabled: Boolean) {
        viewModelScope.launch { smbSourceRepository.setEnabled(source.id, enabled) }
    }

    fun deleteSource(source: SmbSourceEntity) {
        viewModelScope.launch { smbSourceRepository.deleteSource(source) }
    }

    fun setUiMode(mode: UiMode) {
        viewModelScope.launch { settingsRepository.setUiMode(mode) }
    }

    fun refreshCacheSize(context: Context) {
        viewModelScope.launch(Dispatchers.IO) {
            val size = context.cacheDir.walkBottomUp().filter { it.isFile }.sumOf { it.length() }
            _cacheSizeBytes.value = size
            fun subDirSize(name: String) = context.cacheDir.resolve(name).walkBottomUp().filter { it.isFile }.sumOf { it.length() }
            _fanartCacheSizeBytes.value = subDirSize(com.illusion.app.IllusionApplication.FANART_CACHE_DIR_NAME)
            _posterCacheSizeBytes.value = subDirSize(com.illusion.app.IllusionApplication.POSTER_CACHE_DIR_NAME)
        }
    }

    fun clearCache(context: Context) {
        viewModelScope.launch {
            kotlinx.coroutines.withContext(Dispatchers.IO) { context.cacheDir.deleteRecursively() }
            thumbnailRepository.clearAll()
            refreshCacheSize(context)
        }
    }

    /** Clears only the fanart disk cache (a subset of the total cache reported above) - lets someone tight on storage drop the bigger backdrop images without also losing every poster, which is what makes grids/carousels render instantly. */
    fun clearFanartCache(context: Context) {
        viewModelScope.launch {
            val fanartImageLoader = (context.applicationContext as com.illusion.app.IllusionApplication).fanartImageLoader
            withContext(Dispatchers.IO) { fanartImageLoader.diskCache?.clear() }
            fanartImageLoader.memoryCache?.clear()
            refreshCacheSize(context)
        }
    }

    /** Clears only the poster disk cache (a subset of the total cache reported above) - the fanart-only counterpart to [clearFanartCache]. */
    fun clearPosterCache(context: Context) {
        viewModelScope.launch {
            val imageLoader = SingletonImageLoader.get(context)
            withContext(Dispatchers.IO) { imageLoader.diskCache?.clear() }
            imageLoader.memoryCache?.clear()
            refreshCacheSize(context)
        }
    }

    private val _pendingImportSources = MutableStateFlow<List<BackupSource>>(emptyList())
    val pendingImportSources: StateFlow<List<BackupSource>> = _pendingImportSources.asStateFlow()

    private val _backupMessage = MutableStateFlow<String?>(null)
    val backupMessage: StateFlow<String?> = _backupMessage.asStateFlow()

    private var importedFavoritesCount = 0
    private var importedHistoryCount = 0
    private var importedSourcesCount = 0

    fun exportBackup(context: Context, uri: Uri) {
        viewModelScope.launch {
            val result = runCatching {
                val payload = backupManager.buildPayload()
                val text = backupManager.serialize(payload)
                withContext(Dispatchers.IO) {
                    context.contentResolver.openOutputStream(uri)?.use { it.write(text.toByteArray()) }
                        ?: error("no output stream")
                }
            }
            _backupMessage.value = context.getString(
                if (result.isSuccess) R.string.settings_backup_export_success
                else R.string.settings_backup_export_error
            )
        }
    }

    fun importBackup(context: Context, uri: Uri) {
        viewModelScope.launch {
            val text = runCatching {
                withContext(Dispatchers.IO) { context.contentResolver.openInputStream(uri)?.bufferedReader()?.readText() }
            }.getOrNull()
            if (text == null) {
                _backupMessage.value = context.getString(R.string.settings_backup_import_error)
                return@launch
            }
            val summary = runCatching { backupManager.restoreLocalData(backupManager.parse(text)) }.getOrNull()
            if (summary == null) {
                _backupMessage.value = context.getString(R.string.settings_backup_import_error)
                return@launch
            }
            importedFavoritesCount = summary.favoritesCount
            importedHistoryCount = summary.historyCount
            importedSourcesCount = 0
            if (summary.pendingSources.isEmpty()) {
                announceImportDone(context)
            } else {
                _pendingImportSources.value = summary.pendingSources
            }
        }
    }

    /** Adds the currently-queued source (front of [pendingImportSources]) with the password the user just entered for it, then moves on to the next one, or finishes. */
    fun confirmImportSource(context: Context, password: String) {
        val current = _pendingImportSources.value.firstOrNull() ?: return
        viewModelScope.launch {
            runCatching {
                smbSourceRepository.addSource(
                    SmbSourceEntity(
                        displayName = current.displayName,
                        host = current.host,
                        share = current.share,
                        rootPath = current.rootPath,
                        domain = current.domain,
                        username = current.username
                    ),
                    password
                )
            }.onSuccess { importedSourcesCount++ }
            advanceImportQueue(context)
        }
    }

    fun skipImportSource(context: Context) {
        viewModelScope.launch { advanceImportQueue(context) }
    }

    private fun advanceImportQueue(context: Context) {
        _pendingImportSources.update { it.drop(1) }
        if (_pendingImportSources.value.isEmpty()) announceImportDone(context)
    }

    private fun announceImportDone(context: Context) {
        _backupMessage.value = context.getString(
            R.string.settings_backup_import_success,
            importedSourcesCount,
            importedFavoritesCount,
            importedHistoryCount
        )
    }

    fun dismissBackupMessage() {
        _backupMessage.value = null
    }

    fun setDownloadsFolderUri(context: Context, uri: android.net.Uri?) {
        viewModelScope.launch {
            if (uri != null) {
                runCatching {
                    context.contentResolver.takePersistableUriPermission(
                        uri,
                        android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION or android.content.Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                    )
                }
            }
            settingsRepository.setDownloadsFolderUri(uri?.toString())
        }
    }

    fun refreshDownloadsSize() {
        viewModelScope.launch { _downloadsSizeBytes.value = downloadRepository.totalSizeBytes() }
    }

    private val _recoveredDownloadsCount = MutableStateFlow<Int?>(null)
    /** Null = no result to show yet; a value (including 0) is shown once, then dismissed. */
    val recoveredDownloadsCount: StateFlow<Int?> = _recoveredDownloadsCount.asStateFlow()

    /** User just picked a folder via the system picker in response to "Восстановить загрузки" - see DownloadRepository.recoverOrphanedDownloads's own KDoc for why this is manual rather than automatic. */
    fun recoverDownloads(treeUri: Uri) {
        viewModelScope.launch {
            _recoveredDownloadsCount.value = downloadRepository.recoverOrphanedDownloads(libraryRepository, treeUri)
            refreshDownloadsSize()
        }
    }

    fun dismissRecoveredDownloadsMessage() {
        _recoveredDownloadsCount.value = null
    }

    fun clearAllDownloads() {
        viewModelScope.launch {
            downloadRepository.removeAll()
            refreshDownloadsSize()
        }
    }

    companion object {
        fun factory(
            smbSourceRepository: SmbSourceRepository,
            settingsRepository: SettingsRepository,
            thumbnailRepository: ThumbnailRepository,
            downloadRepository: DownloadRepository,
            backupManager: BackupManager,
            devAccessStore: DevAccessStore,
            libraryRepository: LibraryRepository,
            watchProgressRepository: WatchProgressRepository
        ) = viewModelFactory {
            initializer {
                SettingsViewModel(
                    smbSourceRepository,
                    settingsRepository,
                    thumbnailRepository,
                    downloadRepository,
                    backupManager,
                    devAccessStore,
                    libraryRepository,
                    watchProgressRepository
                )
            }
        }
    }
}
