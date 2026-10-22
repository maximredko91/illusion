package com.illusion.app.data.local

import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.illusion.app.data.local.entity.FavoriteEntity
import com.illusion.app.data.local.entity.MediaItemEntity
import com.illusion.app.data.local.entity.ThumbnailSpriteEntity
import com.illusion.app.data.local.entity.WatchProgressEntity
import com.illusion.app.domain.model.Category
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Real Room instance on-device - проверяет, что удаление источника уносит и всё, что он внёс в
 * библиотеку. Раньше удалялась только строка источника, и его элементы оставались навсегда:
 * открыть их было нечем (sourceId больше никуда не ведёт), а вычистить - тем более, единственный
 * DELETE по media_items стирает таблицу целиком. Поймано на живом случае 2026-10-22, когда у
 * пользователя оказалось два источника на одну шару.
 */
@RunWith(AndroidJUnit4::class)
class DeleteSourceContentTest {

    private lateinit var db: AppDatabase

    private fun item(stableId: String, sourceId: Long) = MediaItemEntity(
        stableId = stableId,
        sourceId = sourceId,
        filePath = "\\$stableId.mkv",
        category = Category.MOVIES,
        title = stableId,
        originalTitle = null,
        year = 2020,
        genres = emptyList(),
        rating = null,
        country = null,
        runtimeMinutes = null,
        plot = null,
        director = emptyList(),
        actors = emptyList(),
        collectionName = null,
        posterPath = null,
        fanartPath = null,
        seasonNumber = null,
        episodeNumber = null,
        seriesStableId = null,
        dateAdded = 0L,
        sizeBytes = 0L,
        subtitlePaths = emptyList()
    )

    private fun sprite(stableId: String) = ThumbnailSpriteEntity(
        mediaItemStableId = stableId,
        filePath = "/cache/$stableId.jpg",
        intervalMs = 10_000L,
        columns = 10,
        rows = 10,
        frameWidth = 320,
        frameHeight = 180,
        frameCount = 100
    )

    @Before
    fun setUp() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).build()
    }

    @After
    fun tearDown() = db.close()

    @Test
    fun deletingASourceRemovesItsItemsAndLeavesTheOtherSourceAlone() = runBlocking {
        val items = db.mediaItemDao()
        items.upsertAll(listOf(item("old-1", sourceId = 1L), item("old-2", sourceId = 1L), item("kept", sourceId = 2L)))

        items.deleteSourceContent(1L)

        assertNull(items.getById("old-1"))
        assertNull(items.getById("old-2"))
        assertNotNull(items.getById("kept"))
        assertEquals(1, items.getAll().size)
    }

    @Test
    fun progressFavoritesAndSpritesGoWithTheItems() = runBlocking {
        val items = db.mediaItemDao()
        items.upsertAll(listOf(item("doomed", sourceId = 1L), item("kept", sourceId = 2L)))
        for (id in listOf("doomed", "kept")) {
            db.watchProgressDao().upsert(WatchProgressEntity(id, positionMs = 60_000L, durationMs = 120_000L, watched = false, updatedAt = 1L))
            db.favoriteDao().add(FavoriteEntity(id, addedAt = 1L))
            db.thumbnailSpriteDao().upsert(sprite(id))
        }

        items.deleteSourceContent(1L)

        // Эти строки адресуются по stableId, поэтому без своего элемента они недостижимы, а
        // повторное сканирование той же шары выдаст другие stableId - см. StableIdGenerator.
        assertNull(db.watchProgressDao().getForItem("doomed"))
        assertNull(db.thumbnailSpriteDao().getById("doomed"))
        assertNotNull(db.watchProgressDao().getForItem("kept"))
        assertNotNull(db.thumbnailSpriteDao().getById("kept"))
    }

    @Test
    fun deletingASourceWithNothingIndexedIsHarmless() = runBlocking {
        val items = db.mediaItemDao()
        items.upsertAll(listOf(item("kept", sourceId = 2L)))

        items.deleteSourceContent(99L)

        assertEquals(1, items.getAll().size)
    }
}
