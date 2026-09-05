package com.illusion.app.data.backup

import android.content.Context
import android.content.ContextWrapper
import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.illusion.app.data.local.AppDatabase
import com.illusion.app.data.local.entity.*
import com.illusion.app.data.repository.*
import com.illusion.app.data.smb.*
import com.illusion.app.domain.model.Category
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.flow.first
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class BackupRestoreTest {
    @Test fun restoresAfterSourceIdChangesAndProcessRestarts() = runBlocking {
        val base = InstrumentationRegistry.getInstrumentation().targetContext
        val prefix = "backup_test_${java.util.UUID.randomUUID()}_"
        val context = object : ContextWrapper(base) {
            override fun getSharedPreferences(name: String, mode: Int) = base.getSharedPreferences(prefix + name, mode)
        }
        val db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).build()
        try {
            val sources = SmbSourceRepository(db.smbSourceDao(), SmbCredentialStore(context), SmbClient())
            val progress = WatchProgressRepository(db.watchProgressDao(), db.favoriteDao())
            val library = LibraryRepository(db.mediaItemDao())
            fun manager() = BackupManager(context, sources, progress, library)
            val source = SmbSourceEntity(7, "NAS", "nas", "media", "Movies", "", "reader")
            db.smbSourceDao().upsert(source)
            val oldItem = MediaItemEntity(
                stableId = "old-id", sourceId = 7, filePath = "Movies/Film.mkv", category = Category.MOVIES,
                title = "Film", originalTitle = null, year = null, genres = emptyList(), rating = null,
                country = null, runtimeMinutes = null, plot = null, director = emptyList(), actors = emptyList(),
                collectionName = null, posterPath = null, fanartPath = null, seasonNumber = null,
                episodeNumber = null, seriesStableId = null, dateAdded = 0, sizeBytes = 1234, subtitlePaths = emptyList()
            )
            library.upsertAll(listOf(oldItem))
            progress.setFavorite("old-id", true, 1)
            progress.updateProgress("old-id", 50, 100, false, 2)
            val payload = manager().buildPayload()
            assertNotNull(payload.favorites.single().media)
            library.clearAll()
            progress.clearFavorites()
            progress.clearHistory()
            db.smbSourceDao().delete(source)
            val summary = manager().restoreLocalData(payload)
            assertEquals(1, summary.pendingSources.size)
            assertTrue(progress.observeFavorites().first().isEmpty())
            db.smbSourceDao().upsert(source.copy(id = 42))
            library.upsertAll(listOf(oldItem.copy(stableId = "new-id", sourceId = 42)))
            manager().applyPendingRestore()
            assertEquals("new-id", progress.observeFavorites().first().single().mediaItemStableId)
            assertEquals(50L, progress.getProgress("new-id")?.positionMs)
            // Repeated scans must not re-apply a consumed backup over newer user actions.
            progress.setFavorite("new-id", false, 3)
            manager().applyPendingRestore()
            assertTrue(progress.observeFavorites().first().isEmpty())
        } finally {
            db.close()
            base.deleteSharedPreferences(prefix + "pending_backup_restore")
            base.deleteSharedPreferences(prefix + "smb_credentials")
        }
    }
}
