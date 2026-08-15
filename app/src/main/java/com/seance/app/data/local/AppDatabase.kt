package com.seance.app.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.seance.app.data.local.dao.AudioTrackDao
import com.seance.app.data.local.dao.DownloadDao
import com.seance.app.data.local.dao.FavoriteDao
import com.seance.app.data.local.dao.MediaItemDao
import com.seance.app.data.local.dao.SmbSourceDao
import com.seance.app.data.local.dao.ThumbnailSpriteDao
import com.seance.app.data.local.dao.WatchProgressDao
import com.seance.app.data.local.entity.AudioTrackEntity
import com.seance.app.data.local.entity.DownloadEntity
import com.seance.app.data.local.entity.FavoriteEntity
import com.seance.app.data.local.entity.MediaItemEntity
import com.seance.app.data.local.entity.SmbSourceEntity
import com.seance.app.data.local.entity.ThumbnailSpriteEntity
import com.seance.app.data.local.entity.WatchProgressEntity

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
    version = 6
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

        // Downloads moved from an app-private file path to a content:// Uri (public Downloads/Seans
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

        fun getInstance(context: Context): AppDatabase =
            instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "seance.db"
                ).addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5, MIGRATION_5_6).build().also { instance = it }
            }
    }
}
