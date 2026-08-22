package com.seance.app.data.local

import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.seance.app.data.local.entity.MediaItemEntity
import com.seance.app.domain.model.Category
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Real Room instance on-device - verifies MediaItemDao.search actually ranks title/originalTitle
 * matches ahead of incidental plot/actor substring matches (CLAUDE.md 2026-08-18: "престиж" matching
 * "престижные" inside an unrelated plot used to rank identically to a real title hit).
 */
@RunWith(AndroidJUnit4::class)
class MediaItemDaoSearchTest {

    private lateinit var db: AppDatabase
    private lateinit var dao: com.seance.app.data.local.dao.MediaItemDao

    private fun item(
        stableId: String,
        title: String,
        originalTitle: String? = null,
        plot: String? = null,
        actors: List<String> = emptyList()
    ) = MediaItemEntity(
        stableId = stableId,
        sourceId = 1L,
        filePath = "\\$stableId.mp4",
        category = Category.MOVIES,
        title = title,
        originalTitle = originalTitle,
        year = 2020,
        genres = emptyList(),
        rating = null,
        country = null,
        runtimeMinutes = null,
        plot = plot,
        director = emptyList(),
        actors = actors,
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
        dao = db.mediaItemDao()
    }

    @After
    fun tearDown() {
        db.close()
    }

    @Test
    fun titleMatchIsRankedAheadOfPlotMatch() = runBlocking {
        dao.upsertAll(
            listOf(
                item("plot-hit", title = "Unrelated Movie", plot = "Много престижные казино в этом городе"),
                item("title-hit", title = "Престиж")
            )
        )

        val results = dao.search("престиж").first()

        assertEquals(2, results.size)
        assertEquals("title-hit", results.first().stableId)
    }

    @Test
    fun originalTitleMatchIsRankedAsHighAsTitleMatch() = runBlocking {
        dao.upsertAll(
            listOf(
                item("plot-hit", title = "Unrelated", plot = "mentions inception in passing"),
                item("orig-title-hit", title = "Начало", originalTitle = "Inception")
            )
        )

        val results = dao.search("inception").first()

        assertEquals("orig-title-hit", results.first().stableId)
    }

    @Test
    fun withinSameRankResultsAreAlphabetical() = runBlocking {
        dao.upsertAll(
            listOf(
                item("b", title = "Брат 2"),
                item("a", title = "Брат")
            )
        )

        val results = dao.search("брат").first()

        assertEquals(listOf("a", "b"), results.map { it.stableId })
    }

    @Test
    fun noMatchReturnsEmptyList() = runBlocking {
        dao.upsertAll(listOf(item("x", title = "Something")))

        val results = dao.search("zzz-nonexistent").first()

        assertEquals(emptyList<MediaItemEntity>(), results)
    }
}
