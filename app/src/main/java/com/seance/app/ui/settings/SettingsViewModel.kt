package com.seance.app.ui.settings

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import coil3.SingletonImageLoader
import com.seance.app.R
import com.seance.app.data.backup.BackupManager
import com.seance.app.data.backup.BackupSource
import com.seance.app.data.local.entity.SmbSourceEntity
import com.seance.app.data.repository.DownloadRepository
import com.seance.app.data.repository.SmbSourceRepository
import com.seance.app.data.repository.ThumbnailRepository
import com.seance.app.data.settings.SettingsRepository
import com.seance.app.work.WorkScheduler
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
    private val backupManager: BackupManager
) : ViewModel() {
    val sources: StateFlow<List<SmbSourceEntity>> = smbSourceRepository.observeSources()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val requireChargingForHeavyTasks: Flow<Boolean> = settingsRepository.requireChargingForHeavyTasks
    val rescanIntervalHours: Flow<Int> = settingsRepository.rescanIntervalHours
    val seekDurationSeconds: Flow<Int> = settingsRepository.seekDurationSeconds
    val posterCachingEnabled: Flow<Boolean> = settingsRepository.posterCachingEnabled
    val downloadsFolderUri: Flow<String?> = settingsRepository.downloadsFolderUri

    private val _cacheSizeBytes = MutableStateFlow<Long?>(null)
    val cacheSizeBytes: StateFlow<Long?> = _cacheSizeBytes.asStateFlow()

    private val _downloadsSizeBytes = MutableStateFlow<Long?>(null)
    val downloadsSizeBytes: StateFlow<Long?> = _downloadsSizeBytes.asStateFlow()

    fun deleteSource(source: SmbSourceEntity) {
        viewModelScope.launch { smbSourceRepository.deleteSource(source) }
    }

    fun setRequireChargingForHeavyTasks(context: Context, value: Boolean) {
        viewModelScope.launch {
            settingsRepository.setRequireChargingForHeavyTasks(value)
            rescheduleIfEnabled(context)
        }
    }

    /** [hours] <= 0 disables periodic rescanning entirely. */
    fun setRescanIntervalHours(context: Context, hours: Int) {
        viewModelScope.launch {
            settingsRepository.setRescanIntervalHours(hours)
            rescheduleIfEnabled(context)
        }
    }

    fun setSeekDurationSeconds(seconds: Int) {
        viewModelScope.launch { settingsRepository.setSeekDurationSeconds(seconds) }
    }

    private suspend fun rescheduleIfEnabled(context: Context) {
        val hours = settingsRepository.rescanIntervalHours.first()
        if (hours <= 0) {
            WorkScheduler.cancelPeriodicScan(context)
        } else {
            val requireCharging = settingsRepository.requireChargingForHeavyTasks.first()
            WorkScheduler.schedulePeriodicScan(context, hours, requireCharging)
        }
    }

    fun refreshCacheSize(context: Context) {
        viewModelScope.launch(Dispatchers.IO) {
            val size = context.cacheDir.walkBottomUp().filter { it.isFile }.sumOf { it.length() }
            _cacheSizeBytes.value = size
        }
    }

    fun clearCache(context: Context) {
        viewModelScope.launch {
            kotlinx.coroutines.withContext(Dispatchers.IO) { context.cacheDir.deleteRecursively() }
            thumbnailRepository.clearAll()
            refreshCacheSize(context)
        }
    }

    /** [enabled] = false clears every cached poster/fanart immediately, not just future ones - the caller must confirm with the user first, this is destructive. */
    fun setPosterCachingEnabled(context: Context, enabled: Boolean) {
        viewModelScope.launch {
            settingsRepository.setPosterCachingEnabled(enabled)
            if (enabled) {
                val requireCharging = settingsRepository.requireChargingForHeavyTasks.first()
                WorkScheduler.enqueuePosterPreload(context, requireCharging)
            } else {
                val imageLoader = SingletonImageLoader.get(context)
                withContext(Dispatchers.IO) { imageLoader.diskCache?.clear() }
                imageLoader.memoryCache?.clear()
                refreshCacheSize(context)
            }
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
            backupManager: BackupManager
        ) = viewModelFactory {
            initializer { SettingsViewModel(smbSourceRepository, settingsRepository, thumbnailRepository, downloadRepository, backupManager) }
        }
    }
}
