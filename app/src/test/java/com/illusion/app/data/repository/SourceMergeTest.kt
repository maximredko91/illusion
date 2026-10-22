package com.illusion.app.data.repository

import com.illusion.app.data.local.entity.FavoriteEntity
import com.illusion.app.data.local.entity.MediaItemEntity
import com.illusion.app.data.local.entity.WatchProgressEntity
import com.illusion.app.domain.model.Category
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Случай, ради которого объединение и сделано: один SSD, подключённый в двух местах к разным
 * роутерам, заведён двумя источниками, поэтому каждый фильм лежит в библиотеке дважды и с разными
 * идентификаторами (в stableId входит sourceId - см. StableIdGenerator).
 */
class SourceMergeTest {

    private fun item(stableId: String, sourceId: Long, path: String, size: Long = 100L) = MediaItemEntity(
        stableId = stableId,
        sourceId = sourceId,
        filePath = path,
        category = Category.MOVIES,
        title = path,
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
        sizeBytes = size,
        subtitlePaths = emptyList()
    )

    private fun progress(stableId: String, positionMs: Long, updatedAt: Long) =
        WatchProgressEntity(stableId, positionMs, durationMs = 1000L, watched = false, updatedAt = updatedAt)

    @Test
    fun sameFileUnderTwoSourcesIsPaired() {
        val pairs = pairItemsByFile(
            from = listOf(item("old", 2L, "\\Movies\\Batman.mkv")),
            into = listOf(item("new", 1L, "\\Movies\\Batman.mkv"))
        )
        assertEquals(mapOf("old" to "new"), pairs)
    }

    @Test
    fun pathCaseDoesNotBreakThePairing() {
        val pairs = pairItemsByFile(
            from = listOf(item("old", 2L, "\\movies\\batman.mkv")),
            into = listOf(item("new", 1L, "\\Movies\\Batman.mkv"))
        )
        assertEquals(mapOf("old" to "new"), pairs)
    }

    @Test
    fun differentSizeAtTheSamePathIsNotTheSameFile() {
        // Перезалили в другом качестве - переносить отметку просмотра на другой файл нельзя.
        val pairs = pairItemsByFile(
            from = listOf(item("old", 2L, "\\Movies\\Batman.mkv", size = 700L)),
            into = listOf(item("new", 1L, "\\Movies\\Batman.mkv", size = 900L))
        )
        assertTrue(pairs.toString(), pairs.isEmpty())
    }

    @Test
    fun newerProgressWins() {
        val pairs = mapOf("old" to "new")
        val byItem = mapOf(
            "old" to progress("old", positionMs = 900L, updatedAt = 200L),
            "new" to progress("new", positionMs = 100L, updatedAt = 100L)
        )
        val transfers = progressTransfers(pairs, byItem)
        assertEquals(1, transfers.size)
        assertEquals("new", transfers.single().mediaItemStableId)
        assertEquals(900L, transfers.single().positionMs)
    }

    @Test
    fun olderProgressDoesNotRollBackTheKeptOne() {
        val byItem = mapOf(
            "old" to progress("old", positionMs = 100L, updatedAt = 100L),
            "new" to progress("new", positionMs = 900L, updatedAt = 200L)
        )
        assertTrue(progressTransfers(mapOf("old" to "new"), byItem).isEmpty())
    }

    @Test
    fun favoriteMovesOverAndKeepsTheEarliestDate() {
        val transfers = favoriteTransfers(
            mapOf("old" to "new"),
            mapOf("old" to FavoriteEntity("old", addedAt = 50L))
        )
        assertEquals(listOf(FavoriteEntity("new", 50L)), transfers)
    }

    @Test
    fun planCountsWhatWillBeLost() {
        val plan = buildMergePlan(
            from = listOf(
                item("old-1", 2L, "\\Movies\\Batman.mkv"),
                item("old-2", 2L, "\\Movies\\OnlyHere.mkv")
            ),
            into = listOf(item("new-1", 1L, "\\Movies\\Batman.mkv")),
            progressByItem = mapOf("old-1" to progress("old-1", 900L, 200L)),
            favoritesByItem = emptyMap()
        )
        assertEquals(1, plan.matched)
        assertEquals(1, plan.unmatched)
        assertEquals(1, plan.progressToTransfer)
        assertEquals(0, plan.favoritesToTransfer)
    }
}
