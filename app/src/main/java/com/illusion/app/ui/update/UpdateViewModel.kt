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
import com.illusion.app.data.update.UpdateChecker
import com.illusion.app.data.update.UpdateInfo
import com.illusion.app.data.update.UpdateInstaller
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
    private val settingsRepository: SettingsRepository
) : ViewModel() {
    private val _state = MutableStateFlow(UpdateUiState())
    val state: StateFlow<UpdateUiState> = _state.asStateFlow()

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
                        val path = info.outputData.getString(UpdateDownloadWorker.KEY_FILE_PATH)
                        _state.update { it.copy(isDownloading = false, downloadProgress = 1f, downloadedFile = path?.let(::File)) }
                    }
                    WorkInfo.State.FAILED, WorkInfo.State.CANCELLED -> {
                        _state.update { it.copy(isDownloading = false, error = "Не удалось скачать обновление") }
                    }
                    else -> Unit
                }
            }
        }
    }

    /** [force] bypasses both the once-a-day throttle and a previously-skipped versionCode - used by Settings' manual check button; the automatic on-launch check (MainActivity) always passes false. */
    fun checkForUpdate(force: Boolean = false) {
        viewModelScope.launch {
            if (!force) {
                val lastCheckedAt = settingsRepository.lastUpdateCheckAtMs.first()
                if (System.currentTimeMillis() - lastCheckedAt < AUTO_CHECK_INTERVAL_MS) return@launch
            }
            settingsRepository.setLastUpdateCheckAtMs(System.currentTimeMillis())
            val info = updateChecker.checkForUpdate(BuildConfig.VERSION_CODE)
            if (info == null) {
                if (force) _upToDateMessage.value = "У вас последняя версия"
                return@launch
            }
            if (!force && settingsRepository.skippedUpdateVersionCode.first() == info.versionCode) return@launch
            _state.update { it.copy(update = info) }
        }
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
        WorkScheduler.enqueueUpdateDownload(appContext, update.apkDownloadUrl, update.versionCode)
        _state.update { it.copy(isDownloading = true, downloadProgress = 0f, error = null) }
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
        private const val AUTO_CHECK_INTERVAL_MS = 24 * 60 * 60 * 1000L

        fun factory(context: Context, updateChecker: UpdateChecker, settingsRepository: SettingsRepository) = viewModelFactory {
            initializer { UpdateViewModel(context.applicationContext, updateChecker, settingsRepository) }
        }
    }
}
