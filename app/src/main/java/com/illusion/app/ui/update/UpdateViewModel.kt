package com.illusion.app.ui.update

import android.content.Context
import android.content.Intent
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.work.WorkInfo
import com.illusion.app.BuildConfig
import com.illusion.app.data.settings.SettingsRepository
import com.illusion.app.data.update.LocalUpdateChecker
import com.illusion.app.data.update.UpdateCheckResult
import com.illusion.app.data.update.UpdateChecker
import com.illusion.app.data.update.UpdateInfo
import com.illusion.app.data.update.UpdateInstaller
import com.illusion.app.domain.model.UpdateSource
import com.illusion.app.work.UpdateDownloadWorker
import com.illusion.app.work.WorkScheduler
import java.io.File
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class UpdateUiState(
    /** Non-null shows the "what's new" dialog. */
    val update: UpdateInfo? = null,
    val isDownloading: Boolean = false,
    /** 0f..1f, or null while the server hasn't reported a Content-Length yet (rare, but possible). */
    val downloadProgress: Float? = null,
    val downloadedFile: File? = null,
    val error: String? = null
)

class UpdateViewModel(
    private val appContext: Context,
    private val updateChecker: UpdateChecker,
    private val localUpdateChecker: LocalUpdateChecker,
    private val settingsRepository: SettingsRepository
) : ViewModel() {
    private val _state = MutableStateFlow(UpdateUiState())
    val state: StateFlow<UpdateUiState> = _state.asStateFlow()

    val updateCheckIntervalHours: Flow<Int> = settingsRepository.updateCheckIntervalHours
    val updateSource: Flow<UpdateSource> = settingsRepository.updateSource
    val localUpdateSourceId: Flow<Long?> = settingsRepository.localUpdateSourceId

    fun setUpdateSource(source: UpdateSource) {
        viewModelScope.launch { settingsRepository.setUpdateSource(source) }
    }

    fun setLocalUpdateSourceId(sourceId: Long) {
        viewModelScope.launch { settingsRepository.setLocalUpdateSourceId(sourceId) }
    }

    /** Set when a manual check (Settings' "Проверить обновления" button) finds nothing newer - a one-shot toast-style message, not part of [state] since it has nothing to do with the update dialog itself. */
    private val _upToDateMessage = MutableStateFlow<String?>(null)
    val upToDateMessage: StateFlow<String?> = _upToDateMessage.asStateFlow()

    private val _installIntent = Channel<Intent>(Channel.BUFFERED)
    val installIntent: Flow<Intent> = _installIntent.receiveAsFlow()

    private val _permissionSettingsIntent = Channel<Intent>(Channel.BUFFERED)
    val permissionSettingsIntent: Flow<Intent> = _permissionSettingsIntent.receiveAsFlow()

    init {
        viewModelScope.launch {
            WorkScheduler.updateDownloadWorkInfo(appContext).collect { info ->
                when (info?.state) {
                    WorkInfo.State.RUNNING, WorkInfo.State.ENQUEUED -> {
                        val downloaded = info.progress.getLong(UpdateDownloadWorker.KEY_DOWNLOADED, 0L)
                        val total = info.progress.getLong(UpdateDownloadWorker.KEY_TOTAL, -1L)
                        _state.update {
                            it.copy(
                                isDownloading = true,
                                downloadProgress = if (total > 0) downloaded.toFloat() / total else null
                            )
                        }
                    }
                    WorkInfo.State.SUCCEEDED -> {
                        // WorkManager keeps a unique work's WorkInfo around indefinitely once it
                        // succeeds - REPLACE only affects a NEW enqueue, it doesn't clear the old
                        // record on its own. Without this check, every future launch (including
                        // ones running the very update that download was for) re-subscribed to
                        // that same stale SUCCEEDED result and re-showed "Обновление готово" for
                        // an install that already happened. The target versionCode is encoded
                        // right in the file name (UpdateDownloadWorker.apkDir's own naming) - only
                        // surface it if this process is still older than that.
                        val path = info.outputData.getString(UpdateDownloadWorker.KEY_FILE_PATH)
                        val file = path?.let(::File)
                        val downloadedVersionCode = file?.nameWithoutExtension?.substringAfterLast('-')?.toIntOrNull()
                        if (file != null && downloadedVersionCode != null && downloadedVersionCode > BuildConfig.VERSION_CODE) {
                            _state.update { it.copy(isDownloading = false, downloadProgress = 1f, downloadedFile = file) }
                        } else {
                            file?.delete()
                            _state.update { it.copy(isDownloading = false, downloadProgress = null, downloadedFile = null) }
                        }
                    }
                    // CANCELLED deliberately shows no error - it's always the user's own
                    // cancelDownload() action (see its own KDoc), which already reset this same
                    // state directly; setting an error message here too would just race that
                    // reset and flash a confusing "не удалось скачать" right after the user
                    // chose to cancel themselves.
                    WorkInfo.State.CANCELLED -> {
                        _state.update { it.copy(isDownloading = false) }
                    }
                    WorkInfo.State.FAILED -> {
                        val reason = info.outputData.getString(UpdateDownloadWorker.KEY_ERROR)
                        val message = if (reason != null) {
                            "Не удалось скачать обновление: $reason"
                        } else {
                            "Не удалось скачать обновление"
                        }
                        _state.update { it.copy(isDownloading = false, error = message) }
                    }
                    else -> Unit
                }
            }
        }
    }

    /**
     * [force] bypasses the configured interval/off setting and a previously-skipped versionCode -
     * used by Settings' manual check button; the automatic on-launch check (MainActivity) always
     * passes false, and respects [SettingsRepository.updateCheckIntervalHours] (0 = never
     * auto-check, only the manual button works).
     */
    fun checkForUpdate(force: Boolean = false) {
        viewModelScope.launch {
            if (!force) {
                val intervalHours = settingsRepository.updateCheckIntervalHours.first()
                if (intervalHours <= 0) return@launch
                val lastCheckedAt = settingsRepository.lastUpdateCheckAtMs.first()
                if (System.currentTimeMillis() - lastCheckedAt < intervalHours * 60 * 60 * 1000L) return@launch
            }
            settingsRepository.setLastUpdateCheckAtMs(System.currentTimeMillis())
            val source = settingsRepository.updateSource.first()
            val result = if (source == UpdateSource.LOCAL) {
                val sourceId = settingsRepository.localUpdateSourceId.first()
                if (sourceId == null) {
                    if (force) _upToDateMessage.value = "Не выбран источник для локальных обновлений"
                    return@launch
                }
                localUpdateChecker.checkForUpdate(sourceId, BuildConfig.VERSION_CODE)
            } else {
                updateChecker.checkForUpdate(BuildConfig.VERSION_CODE)
            }
            when (result) {
                is UpdateCheckResult.Failed -> {
                    if (force) {
                        _upToDateMessage.value = if (source == UpdateSource.LOCAL) {
                            "Не удалось проверить обновления: ${result.message}"
                        } else {
                            "Не удалось проверить обновления — нет подключения к интернету"
                        }
                    }
                }
                is UpdateCheckResult.UpToDate -> {
                    if (force) {
                        _upToDateMessage.value = if (result.checkedVersionInfo != null) {
                            "У вас последняя версия! (${result.checkedVersionInfo})"
                        } else {
                            "У вас последняя версия!"
                        }
                    }
                }
                is UpdateCheckResult.Available -> {
                    // A "skip this version" from before a release was (re-)marked mandatory
                    // should never be able to suppress it once it is - see UpdateInfo.mandatory's
                    // own KDoc for why a mandatory release must always reach the user.
                    val previouslySkipped = !force && !result.info.mandatory &&
                        settingsRepository.skippedUpdateVersionCode.first() == result.info.versionCode
                    if (previouslySkipped) return@launch
                    _state.update { it.copy(update = result.info) }
                }
            }
        }
    }

    fun setUpdateCheckIntervalHours(hours: Int) {
        viewModelScope.launch { settingsRepository.setUpdateCheckIntervalHours(hours) }
    }

    fun dismissUpToDateMessage() {
        _upToDateMessage.value = null
    }

    /** Closes the dialog for this session only - the same version will be offered again next check (app relaunch, or the next automatic interval). */
    fun dismissForNow() {
        _state.update { it.copy(update = null) }
    }

    fun skipThisVersion() {
        val versionCode = _state.value.update?.versionCode ?: return
        viewModelScope.launch { settingsRepository.setSkippedUpdateVersionCode(versionCode) }
        _state.update { it.copy(update = null) }
    }

    fun startDownload() {
        val update = _state.value.update ?: return
        // A previous attempt may have already downloaded this exact version - e.g. install()
        // failed silently (no package installer / no "install unknown apps" screen at all on some
        // Android TV boxes) and the user, seeing nothing happen, backed out and re-triggered the
        // whole flow from Settings. Re-downloading over what can be a slow home-network link for
        // no reason (WorkScheduler.enqueueUpdateDownload's REPLACE policy would otherwise always
        // restart from scratch) is exactly the "скачивалось заново" symptom reported on-device -
        // skip straight to the install-ready state if the file is already sitting there intact.
        val existing = File(UpdateDownloadWorker.apkDir(appContext), "illusion-${update.versionCode}.apk")
        if (existing.exists() && existing.length() > 0L) {
            _state.update { it.copy(isDownloading = false, downloadProgress = 1f, downloadedFile = existing, error = null) }
            return
        }
        WorkScheduler.enqueueUpdateDownload(appContext, update.apkDownloadUrl, update.versionCode)
        _state.update { it.copy(isDownloading = true, downloadProgress = 0f, error = null) }
    }

    /** Bails out of a stuck/too-slow download - see DownloadProgressDialog's own Cancel button. Resets state directly rather than waiting for the WorkInfo observer's own CANCELLED branch to catch up, so the user lands straight back on the "what's new" offer instead of a transient "не удалось скачать" error. */
    fun cancelDownload() {
        WorkScheduler.cancelUpdateDownload(appContext)
        _state.update { it.copy(isDownloading = false, downloadProgress = null, error = null) }
    }

    /** Was set but never actually shown anywhere - a failed download (e.g. the readTimeout that used to trip on a stalled-but-alive connection) silently fell through straight back to the "what's new" offer with zero explanation, which on-device read as "1%, hangs, then the update dialog just pops up again" on repeat with no visible error at all. */
    fun dismissError() {
        _state.update { it.copy(error = null) }
    }

    /** Either sends the ready-to-launch install Intent, or - the first time this app tries to install an update - redirects to the one-time "install unknown apps" toggle instead. See UpdateInstaller's own KDoc for why that gate can't be skipped. */
    fun install() {
        val file = _state.value.downloadedFile ?: return
        viewModelScope.launch {
            if (UpdateInstaller.canInstallPackages(appContext)) {
                _installIntent.send(UpdateInstaller.installIntent(appContext, file))
            } else {
                _permissionSettingsIntent.send(UpdateInstaller.installPermissionSettingsIntent(appContext))
            }
        }
    }

    companion object {
        fun factory(
            context: Context,
            updateChecker: UpdateChecker,
            localUpdateChecker: LocalUpdateChecker,
            settingsRepository: SettingsRepository
        ) = viewModelFactory {
            initializer { UpdateViewModel(context.applicationContext, updateChecker, localUpdateChecker, settingsRepository) }
        }
    }
}
