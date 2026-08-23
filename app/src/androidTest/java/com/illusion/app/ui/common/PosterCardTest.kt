package com.illusion.app.ui.common

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.illusion.app.data.local.entity.MediaItemEntity
import com.illusion.app.domain.model.Category
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class PosterCardTest {

    @get:Rule
    val composeRule = createComposeRule()

    private fun item(
        title: String = "Начало",
        year: Int? = 2010,
        genres: List<String> = listOf("Фантастика"),
        rating: Double? = null
    ) = MediaItemEntity(
        stableId = "s1",
        sourceId = 1L,
        filePath = "\\movie.mp4",
        category = Category.MOVIES,
        title = title,
        originalTitle = null,
        year = year,
        genres = genres,
        rating = rating,
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

    @Test
    fun titleIsDisplayed() {
        composeRule.setContent {
            MaterialTheme {
                PosterCard(item = item(title = "Начало"), onClick = {})
            }
        }

        composeRule.onNodeWithText("Начало").assertExists()
    }

    @Test
    fun subtitleCombinesYearAndFirstGenre() {
        composeRule.setContent {
            MaterialTheme {
                PosterCard(item = item(year = 2010, genres = listOf("Фантастика", "Боевик")), onClick = {})
            }
        }

        composeRule.onNodeWithText("2010 · Фантастика").assertExists()
    }

    @Test
    fun clickingCardInvokesOnClick() {
        var clicked = false
        composeRule.setContent {
            MaterialTheme {
                PosterCard(item = item(), onClick = { clicked = true })
            }
        }

        composeRule.onNodeWithText("Начало").performClick()

        assertTrue(clicked)
    }

    @Test
    fun ratingBadgeShowsOnlyWhenRequestedAndRatingPresent() {
        composeRule.setContent {
            MaterialTheme {
                PosterCard(item = item(rating = 8.8), onClick = {}, showRatingBadge = true)
            }
        }

        composeRule.onNodeWithText("8.8").assertExists()
    }

    @Test
    fun ratingBadgeIsAbsentWhenNotRequested() {
        composeRule.setContent {
            MaterialTheme {
                PosterCard(item = item(rating = 8.8), onClick = {}, showRatingBadge = false)
            }
        }

        composeRule.onNodeWithText("8.8").assertDoesNotExist()
    }
}
