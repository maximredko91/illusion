package com.illusion.app.data.local

import androidx.room.testing.MigrationTestHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Migrations run against the schemas Room exports into `app/schemas` - the safety net this app went
 * 21 versions without. A migration that forgets a column doesn't fail at compile time and doesn't
 * fail on a fresh install either: it only fails on an existing install's next launch, which is the
 * one place it can't be caught by hand (this exact class of bug already bit once - see the
 * MIGRATION_10_11 note in CLAUDE.md).
 *
 * The helper opens a database at the older version, runs the real migration, and then validates
 * the result against the newer exported schema - a mismatch fails here rather than on a device.
 */
@RunWith(AndroidJUnit4::class)
class MigrationTest {

    @get:Rule
    val helper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        AppDatabase::class.java,
        emptyList(),
        FrameworkSQLiteOpenHelperFactory()
    )

    @Test
    fun migrate21To22_addsPostCreditsColumn_keepingExistingRows() {
        helper.createDatabase(TEST_DB, 21).use { db ->
            db.execSQL(
                """
                INSERT INTO media_items (
                    stableId, sourceId, filePath, category, title, originalTitle, year, genres,
                    rating, country, runtimeMinutes, plot, director, actors, actorRoles,
                    collectionName, posterPath, fanartPath, episodeThumbPath, seasonNumber,
                    episodeNumber, seriesStableId, dateAdded, sizeBytes, subtitlePaths,
                    trailerPath, trailerSizeBytes, lastModified, nfoLastModified, introStartMs,
                    introEndMs, mpaa, tagline, studio, premiered, status, imdbId, tmdbId, tags,
                    isOrphanedDownload, videoWidth, videoHeight, outroStartMs, edition,
                    folderCollectionName
                ) VALUES (
                    'id-1', 1, '\Movies\film.mkv', 'MOVIES', 'Фильм', NULL, 2024, '[]',
                    NULL, NULL, NULL, NULL, '[]', '[]', '[]',
                    NULL, NULL, NULL, NULL, NULL,
                    NULL, NULL, 0, 100, '[]',
                    NULL, NULL, 0, NULL, NULL,
                    NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, '[]',
                    0, NULL, NULL, NULL, NULL,
                    NULL
                )
                """.trimIndent()
            )
        }

        val migrated = helper.runMigrationsAndValidate(TEST_DB, 22, true, AppDatabase.MIGRATION_21_22)

        migrated.query("SELECT stableId, title, postCreditsStartMs FROM media_items").use { cursor ->
            assertTrue("миграция потеряла существующую строку", cursor.moveToFirst())
            assertEquals("id-1", cursor.getString(0))
            assertEquals("Фильм", cursor.getString(1))
            assertTrue("новая колонка должна быть пустой для старых строк", cursor.isNull(2))
        }
    }

    private companion object {
        const val TEST_DB = "migration-test"
    }
}
