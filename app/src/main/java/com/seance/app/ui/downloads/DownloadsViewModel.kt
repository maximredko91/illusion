package com.seance.app.ui.downloads

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.seance.app.data.local.entity.DownloadEntity
import com.seance.app.data.local.entity.MediaItemEntity
import com.seance.app.data.repository.DownloadRepository
import com.seance.app.data.repository.LibraryRepository
import com.seance.app.work.WorkScheduler
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.scan
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class DownloadEntry(
    val download: DownloadEntity,
    val item: MediaItemEntity,
    /** Derived from the delta between this and the previous emission for the same item - not stored, so it goes back to 0 whenever there's nothing new to diff against (e.g. right after the screen opens). */
    val bytesPerSecond: Long = 0
)

private typealias ProgressMark = Pair<Long, Long> // downloadedBytes to updatedAt

class DownloadsViewModel(
    private val downloadRepository: DownloadRepository,
    private val libraryRepository: LibraryRepository
) : ViewModel() {
    val entries: StateFlow<List<DownloadEntry>> = downloadRepository.observeAll()
        .scan(emptyMap<String, ProgressMark>() to emptyList<DownloadEntry>()) { (prevMarks, _), downloads ->
            val nextMarks = mutableMapOf<String, ProgressMark>()
            val list = downloads.mapNotNull { download ->
                val item = libraryRepository.getById(download.stableId) ?: return@mapNotNull null
                val prev = prevMarks[download.stableId]
                val speed = if (prev != null && download.updatedAt > prev.second) {
                    ((download.downloadedBytes - prev.first) * 1000 / (download.updatedAt - prev.second)).coerceAtLeast(0)
                } else {
                    0L
                }
                nextMarks[download.stableId] = download.downloadedBytes to download.updatedAt
                DownloadEntry(download, item, speed)
            }
            nextMarks to list
        }
        .map { it.second }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    /** Cancels an in-progress download, or deletes a finished/failed one - either way the row and any partial file are gone. */
    fun removeDownload(context: Context, stableId: String) {
        WorkScheduler.cancelDownload(context, stableId)
        viewModelScope.launch { downloadRepository.remove(stableId) }
    }

    companion object {
        fun factory(downloadRepository: DownloadRepository, libraryRepository: LibraryRepository) = viewModelFactory {
            initializer { DownloadsViewModel(downloadRepository, libraryRepository) }
        }
    }
}
