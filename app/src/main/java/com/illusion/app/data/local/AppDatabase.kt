package com.illusion.app.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.illusion.app.data.local.dao.AudioTrackDao
import com.illusion.app.data.local.dao.DownloadDao
import com.illusion.app.data.local.dao.FavoriteDao
import com.illusion.app.data.local.dao.MediaItemDao
import com.illusion.app.data.local.dao.SmbSourceDao
import com.illusion.app.data.local.dao.ThumbnailSpriteDao
import com.illusion.app.data.local.dao.WatchProgressDao
import com.illusion.app.data.local.entity.AudioTrackEntity
import com.illusion.app.data.local.entity.DownloadEntity
import com.illusion.app.data.local.entity.FavoriteEntity
import com.illusion.app.data.local.entity.MediaItemEntity
import com.illusion.app.data.local.entity.SmbSourceEntity
import com.illusion.app.data.local.entity.ThumbnailSpriteEntity
import com.illusion.app.data.local.entity.WatchProgressEntity

@Database(
    entities = [
        SmbSourceEntity::class,
        MediaItemEntity::class,
        WatchProgressEntity::class,
        FavoriteEntity::class,
        ThumbnailSpriteEntity::class,
        DownloadEntity::class,
        AudioTrackEntity::class
    ],
    version = 19
)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun smbSourceDao(): SmbSourceDao
    abstract fun mediaItemDao(): MediaItemDao
    abstract fun watchProgressDao(): WatchProgressDao
    abstract fun favoriteDao(): FavoriteDao
    abstract fun thumbnailSpriteDao(): ThumbnailSpriteDao
    abstract fun downloadDao(): DownloadDao
    abstract fun audioTrackDao(): AudioTrackDao

    companion object {
        @Volatile
        private var instance: AppDatabase? = null

        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `downloads` (
                        `stableId` TEXT NOT NULL,
                        `localPath` TEXT NOT NULL,
                        `status` TEXT NOT NULL,
                        `totalBytes` INTEGER NOT NULL,
                        `downloadedBytes` INTEGER NOT NULL,
                        `updatedAt` INTEGER NOT NULL,
                        `errorMessage` TEXT,
                        PRIMARY KEY(`stableId`)
                    )
                    """.trimIndent()
                )
            }
        }

        // Downloads moved from an app-private file path to a content:// Uri (public Downloads/Illusion
        // or a user-picked SAF folder) - old rows point at a representation that no longer applies,
        // so this recreates the table empty rather than trying to translate them. Nothing else
        // (library, favorites, watch history) is touched.
        private val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("DROP TABLE IF EXISTS `downloads`")
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `downloads` (
                        `stableId` TEXT NOT NULL,
                        `contentUri` TEXT NOT NULL,
                        `subtitles` TEXT NOT NULL,
                        `status` TEXT NOT NULL,
                        `totalBytes` INTEGER NOT NULL,
                        `downloadedBytes` INTEGER NOT NULL,
                        `updatedAt` INTEGER NOT NULL,
                        `errorMessage` TEXT,
                        PRIMARY KEY(`stableId`)
                    )
                    """.trimIndent()
                )
            }
        }

        private val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `audio_tracks` (
                        `stableId` TEXT NOT NULL,
                        `tracks` TEXT NOT NULL,
                        `probedAt` INTEGER NOT NULL,
                        PRIMARY KEY(`stableId`)
                    )
                    """.trimIndent()
                )
            }
        }

        private val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE `media_items` ADD COLUMN `mpaa` TEXT")
                db.execSQL("ALTER TABLE `media_items` ADD COLUMN `tagline` TEXT")
                db.execSQL("ALTER TABLE `media_items` ADD COLUMN `studio` TEXT")
                db.execSQL("ALTER TABLE `media_items` ADD COLUMN `premiered` TEXT")
                db.execSQL("ALTER TABLE `media_items` ADD COLUMN `imdbId` TEXT")
                db.execSQL("ALTER TABLE `media_items` ADD COLUMN `tmdbId` TEXT")
            }
        }

        private val MIGRATION_5_6 = object : Migration(5, 6) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE `media_items` ADD COLUMN `actorRoles` TEXT NOT NULL DEFAULT '[]'")
            }
        }

        private val MIGRATION_6_7 = object : Migration(6, 7) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE `media_items` ADD COLUMN `episodeThumbPath` TEXT")
            }
        }

        private val MIGRATION_7_8 = object : Migration(7, 8) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE `media_items` ADD COLUMN `trailerPath` TEXT")
                db.execSQL("ALTER TABLE `media_items` ADD COLUMN `trailerSizeBytes` INTEGER")
            }
        }

        // lastModified defaults to 0 for pre-existing rows, which never matches a real SMB
        // last-write-time - the "unchanged since last scan" fast path in LibraryScanner naturally
        // falls through to a full re-scan for every row exactly once (the first scan after
        // upgrading), then benefits from the fast path on every scan after that.
        private val MIGRATION_8_9 = object : Migration(8, 9) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE `media_items` ADD COLUMN `lastModified` INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE `media_items` ADD COLUMN `nfoLastModified` INTEGER")
            }
        }

        // MediaItemDao filters on category/seriesStableId/collectionName/sourceId constantly
        // (observeByCategory, observeSeriesGroupedByCategory, observeByCollection, source lookups)
        // but the table never had indices for any of them - full-table scans on a 3000+ row table
        // since the first commit. Adding the indices Room's schema now expects (see MediaItemEntity).
        // Kodi's separate freeform <tag> field (distinct from <genre>) was never read/stored at
        // all until now - default '[]' matches what the JSON Converters would encode for an empty
        // list, so every pre-existing row reads back as "no tags" until the next rescan fills it in.
        private val MIGRATION_11_12 = object : Migration(11, 12) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE `media_items` ADD COLUMN `tags` TEXT NOT NULL DEFAULT '[]'")
            }
        }

        // Persisted tag translations (TagTranslationRepository) - see TagTranslationEntity's own
        // KDoc for why this needs a `source` column, not just tag->translation.
        private val MIGRATION_12_13 = object : Migration(12, 13) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `tag_translations` (
                        `tag` TEXT NOT NULL,
                        `translation` TEXT NOT NULL,
                        `source` TEXT NOT NULL,
                        PRIMARY KEY(`tag`)
                    )
                    """
                )
            }
        }

        // Backs DownloadRepository.recoverOrphanedDownloads - a synthetic row for a downloaded
        // video file found with no matching library row (app data cleared/reinstalled while the
        // file itself survived). '0' default means every pre-existing row reads as "not orphaned",
        // which is correct - this flag only ever gets set true by the recovery pass itself, never
        // retroactively on a real scanned row.
        private val MIGRATION_13_14 = object : Migration(13, 14) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE `media_items` ADD COLUMN `isOrphanedDownload` INTEGER NOT NULL DEFAULT 0")
            }
        }

        // Resolution badge on Details - both null until the next rescan re-reads each video's
        // container header (LibraryScanner.extractVideoFormat); a normal (non-forced) rescan
        // never revisits an unchanged file, so existing rows stay null until a real file edit or a
        // forced rescan ("Полное пересканирование" in Settings) touches them.
        private val MIGRATION_14_15 = object : Migration(14, 15) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE `media_items` ADD COLUMN `videoWidth` INTEGER")
                db.execSQL("ALTER TABLE `media_items` ADD COLUMN `videoHeight` INTEGER")
            }
        }

        /** Mirrors introStartMs/introEndMs's own column addition (MIGRATION_?_?) - see MediaItemEntity.outroStartMs's KDoc for what this drives. */
        private val MIGRATION_15_16 = object : Migration(15, 16) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE `media_items` ADD COLUMN `outroStartMs` INTEGER")
            }
        }

        /** .nfo <edition> (Театральная/Режиссёрская/...) - see MediaItemEntity.edition's KDoc. */
        private val MIGRATION_16_17 = object : Migration(16, 17) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE `media_items` ADD COLUMN `edition` TEXT")
            }
        }

        /** folderCollectionName - see MediaItemEntity's own KDoc for why this is a separate column from collectionName rather than reusing it. */
        private val MIGRATION_17_18 = object : Migration(17, 18) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE `media_items` ADD COLUMN `folderCollectionName` TEXT")
            }
        }

        // In-app tag translation (TagTranslator/DeepLClient) removed - tags are now translated
        // upstream, into the .nfo files themselves, by the user's own separate script before this
        // app ever scans them. Same lesson as MIGRATION_10_11's own KDoc: a removed @Entity needs
        // a real migration dropping the table, not just deleting the Kotlin class - Room's schema
        // validator checks the live table set against the expected one on every launch.
        private val MIGRATION_18_19 = object : Migration(18, 19) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("DROP TABLE IF EXISTS `tag_translations`")
            }
        }

        private val MIGRATION_9_10 = object : Migration(9, 10) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_media_items_category` ON `media_items` (`category`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_media_items_seriesStableId` ON `media_items` (`seriesStableId`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_media_items_collectionName` ON `media_items` (`collectionName`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_media_items_sourceId` ON `media_items` (`sourceId`)")
            }
        }

        // MediaItemEntity.hasNfo was written during scan but never read anywhere - removing the
        // Kotlin field alone (without also dropping the physical column) makes Room's schema
        // validation fail on every existing install with IllegalStateException("Migration didn't
        // properly handle: media_items...") the moment it's opened, since Room compares the full
        // expected column set against what's actually in the table, not just "are the entity's
        // columns present". SQLite's ALTER TABLE DROP COLUMN needs a newer SQLite than this app's
        // minSdk 26 can guarantee, so this recreates the table instead (standard Room pattern):
        // new table without hasNfo, copy every other column across, drop the old table, rename.
        private val MIGRATION_10_11 = object : Migration(10, 11) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE `media_items_new` (
                        `stableId` TEXT NOT NULL,
                        `sourceId` INTEGER NOT NULL,
                        `filePath` TEXT NOT NULL,
                        `category` TEXT NOT NULL,
                        `title` TEXT NOT NULL,
                        `originalTitle` TEXT,
                        `year` INTEGER,
                        `genres` TEXT NOT NULL,
                        `rating` REAL,
                        `country` TEXT,
                        `runtimeMinutes` INTEGER,
                        `plot` TEXT,
                        `director` TEXT NOT NULL,
                        `actors` TEXT NOT NULL,
                        `actorRoles` TEXT NOT NULL,
                        `collectionName` TEXT,
                        `posterPath` TEXT,
                        `fanartPath` TEXT,
                        `episodeThumbPath` TEXT,
                        `seasonNumber` INTEGER,
                        `episodeNumber` INTEGER,
                        `seriesStableId` TEXT,
                        `dateAdded` INTEGER NOT NULL,
                        `sizeBytes` INTEGER NOT NULL,
                        `subtitlePaths` TEXT NOT NULL,
                        `trailerPath` TEXT,
                        `trailerSizeBytes` INTEGER,
                        `lastModified` INTEGER NOT NULL,
                        `nfoLastModified` INTEGER,
                        `introStartMs` INTEGER,
                        `introEndMs` INTEGER,
                        `mpaa` TEXT,
                        `tagline` TEXT,
                        `studio` TEXT,
                        `premiered` TEXT,
                        `imdbId` TEXT,
                        `tmdbId` TEXT,
                        PRIMARY KEY(`stableId`)
                    )
                    """.trimIndent()
                )
                db.execSQL(
                    """
                    INSERT INTO `media_items_new` (
                        stableId, sourceId, filePath, category, title, originalTitle, year, genres,
                        rating, country, runtimeMinutes, plot, director, actors, actorRoles,
                        collectionName, posterPath, fanartPath, episodeThumbPath, seasonNumber,
                        episodeNumber, seriesStableId, dateAdded, sizeBytes, subtitlePaths, trailerPath,
                        trailerSizeBytes, lastModified, nfoLastModified, introStartMs, introEndMs,
                        mpaa, tagline, studio, premiered, imdbId, tmdbId
                    )
                    SELECT
                        stableId, sourceId, filePath, category, title, originalTitle, year, genres,
                        rating, country, runtimeMinutes, plot, director, actors, actorRoles,
                        collectionName, posterPath, fanartPath, episodeThumbPath, seasonNumber,
                        episodeNumber, seriesStableId, dateAdded, sizeBytes, subtitlePaths, trailerPath,
                        trailerSizeBytes, lastModified, nfoLastModified, introStartMs, introEndMs,
                        mpaa, tagline, studio, premiered, imdbId, tmdbId
                    FROM `media_items`
                    """.trimIndent()
                )
                db.execSQL("DROP TABLE `media_items`")
                db.execSQL("ALTER TABLE `media_items_new` RENAME TO `media_items`")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_media_items_category` ON `media_items` (`category`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_media_items_seriesStableId` ON `media_items` (`seriesStableId`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_media_items_collectionName` ON `media_items` (`collectionName`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_media_items_sourceId` ON `media_items` (`sourceId`)")
            }
        }

        fun getInstance(context: Context): AppDatabase =
            instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "illusion.db"
                ).addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5, MIGRATION_5_6, MIGRATION_6_7, MIGRATION_7_8, MIGRATION_8_9, MIGRATION_9_10, MIGRATION_10_11, MIGRATION_11_12, MIGRATION_12_13, MIGRATION_13_14, MIGRATION_14_15, MIGRATION_15_16, MIGRATION_16_17, MIGRATION_17_18, MIGRATION_18_19).build().also { instance = it }
            }
    }
}
