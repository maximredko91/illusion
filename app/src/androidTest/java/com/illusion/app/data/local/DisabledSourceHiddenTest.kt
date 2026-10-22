package com.illusion.app.data.local

import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.illusion.app.data.local.entity.MediaItemEntity
import com.illusion.app.data.local.entity.SmbSourceEntity
import com.illusion.app.domain.model.Category
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Отключённый источник прячет свои фильмы со всех экранов библиотеки. До этого «Отключён» влияло
 * только на сканирование: карточки оставались на виду и открывались через свой источник, то есть
 * у пользователя с одним SSD, подключённым в двух местах к разным роутерам, половина библиотеки в
 * любой момент времени была видна, но не воспроизводилась.
 *
 * Воспроизведение по прямой ссылке (getById) намеренно НЕ фильтруется - см. getVisibleById.
 */
@RunWith(AndroidJUnit4::class)
class DisabledSourceHiddenTest {

    private lateinit var db: AppDatabase

    private fun source(id: Long, enabled: Boolean) = SmbSourceEntity(
        id = id,
        displayName = "source-$id",
        host = "192.168.$id.1",
        share = "Movies",
        rootPath = "",
        domain = "",
        username = "",
        enabled = enabled
    )

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
        actors = listOf("Иван Иванов"),
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

    @Before
    fun setUp() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).build()
        runBlocking {
            db.smbSourceDao().upsert(source(1L, enabled = true))
            db.smbSourceDao().upsert(source(2L, enabled = false))
            db.mediaItemDao().upsertAll(listOf(item("visible", sourceId = 1L), item("hidden", sourceId = 2L)))
        }
    }

    @After
    fun tearDown() = db.close()

    @Test
    fun categoryListingSkipsDisabledSource() = runBlocking {
        val shown = db.mediaItemDao().getByCategory(Category.MOVIES).map { it.stableId }
        assertEquals(listOf("visible"), shown)
    }

    @Test
    fun searchSkipsDisabledSource() = runBlocking {
        val found = db.mediaItemDao().search("i", translatedGenre = null).first().map { it.stableId }
        assertEquals(listOf("visible"), found)
    }

    @Test
    fun filmographySkipsDisabledSource() = runBlocking {
        val found = db.mediaItemDao().getFilmography("Иван Иванов").map { it.stableId }
        assertEquals(listOf("visible"), found)
    }

    @Test
    fun visibleLookupHidesItButDirectLookupStillPlaysIt() = runBlocking {
        assertNull(db.mediaItemDao().getVisibleById("hidden"))
        // Воспроизведение не должно ломаться: элемент, уже открытый пользователем, прятать поздно.
        assertNotNull(db.mediaItemDao().getById("hidden"))
    }

    @Test
    fun enablingTheSourceBringsItemsBack() = runBlocking {
        db.smbSourceDao().setEnabled(2L, true)

        val shown = db.mediaItemDao().getByCategory(Category.MOVIES).map { it.stableId }.sorted()
        assertEquals(listOf("hidden", "visible"), shown)
    }
}
