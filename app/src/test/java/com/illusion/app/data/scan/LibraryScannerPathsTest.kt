package com.illusion.app.data.scan

import com.illusion.app.data.scan.LibraryScanner.Companion.FANART_NAMES
import com.illusion.app.data.scan.LibraryScanner.Companion.POSTER_NAMES
import com.illusion.app.data.scan.LibraryScanner.Companion.categorize
import com.illusion.app.data.scan.LibraryScanner.Companion.collectionFolderName
import com.illusion.app.data.scan.LibraryScanner.Companion.findImage
import com.illusion.app.data.scan.LibraryScanner.Companion.resolveTrailer
import com.illusion.app.data.scan.LibraryScanner.Companion.showFolderOf
import com.illusion.app.data.smb.SmbFileRef
import com.illusion.app.domain.model.Category
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class LibraryScannerPathsTest {

    private fun ref(path: String) = SmbFileRef(path, path.substringAfterLast('\\'), 1L, 0L)

    @Test
    fun categoryFromAnySegment() {
        assertEquals(Category.MOVIES, categorize("Фильмы\\Престиж (2006)\\Престиж.mkv").category)
        assertEquals(Category.TV_SHOWS, categorize("Media\\Сериалы\\Клиника\\S01\\e.avi").category)
        assertEquals(Category.TV_SHOWS, categorize("TV\\Show\\e.mkv").category)
        assertEquals(Category.CARTOONS, categorize("Мультфильмы\\Шрек\\s.mkv").category)
        // "мультсериал" contains "сериал" - must not be read as a plain TV show.
        val cartoonSeries = categorize("Media\\Мультсериалы\\Футурама\\e.mkv")
        assertEquals(Category.CARTOON_SERIES, cartoonSeries.category)
        assertEquals(1, cartoonSeries.categoryFolderIndex)
    }

    @Test
    fun showFolderSpansSeasonSubfolders() {
        val season1 = "Media\\Сериалы\\Клиника\\Сезон 1\\e01.avi"
        val season2 = "Media\\Сериалы\\Клиника\\Сезон 2\\e01.avi"
        assertEquals("Media\\Сериалы\\Клиника", showFolderOf(season1, categorize(season1)))
        assertEquals(showFolderOf(season1, categorize(season1)), showFolderOf(season2, categorize(season2)))
        // A video lying directly in the category folder has no show folder.
        val loose = "Сериалы\\e01.avi"
        assertNull(showFolderOf(loose, categorize(loose)))
        val movie = "Фильмы\\Престиж\\Престиж.mkv"
        assertNull(showFolderOf(movie, categorize(movie)))
    }

    @Test
    fun collectionFolder() {
        assertEquals(
            "Люди в чёрном",
            collectionFolderName("Фильмы\\Люди в чёрном (Коллекция)\\Люди в чёрном (1997)\\m.mp4")
        )
        assertNull(collectionFolderName("Фильмы\\Люди в чёрном (1997)\\m.mp4"))
    }

    @Test
    fun imagesNextToVideoWinOverShowRoot() {
        val images = listOf(
            ref("Сериалы\\Клиника\\poster.jpg"),
            ref("Сериалы\\Клиника\\S01\\folder.jpg"),
            ref("Сериалы\\Клиника\\fanart.jpg")
        )
        val folders = listOf("Сериалы\\Клиника\\S01", "Сериалы\\Клиника")
        assertEquals("Сериалы\\Клиника\\S01\\folder.jpg", findImage(images, folders, "e01", POSTER_NAMES)?.path)
        assertEquals("Сериалы\\Клиника\\fanart.jpg", findImage(images, folders, "e01", FANART_NAMES)?.path)
    }

    @Test
    fun imageNamingVariants() {
        val folder = listOf("Фильмы\\Престиж")
        assertEquals(
            "Фильмы\\Престиж\\Престиж-fanart.jpg",
            findImage(listOf(ref("Фильмы\\Престиж\\Престиж-fanart.jpg")), folder, "Престиж", FANART_NAMES)?.path
        )
        // Numbered extrafanart: first alphabetically, and "fanartist.jpg" is not a fanart.
        val numbered = listOf(ref("Фильмы\\Престиж\\fanart2.jpg"), ref("Фильмы\\Престиж\\fanart-1.jpg"))
        assertEquals("Фильмы\\Престиж\\fanart-1.jpg", findImage(numbered, folder, "Престиж", FANART_NAMES)?.path)
        assertNull(findImage(listOf(ref("Фильмы\\Престиж\\fanartist.jpg")), folder, "Престиж", FANART_NAMES))
    }

    @Test
    fun trailerPriority() {
        val video = "Сериалы\\Клиника\\S01"
        val show = "Сериалы\\Клиника"
        val own = ref("$video\\Clinic.S01E02-trailer.mp4")
        val seasonAtRoot = ref("$show\\Клиника-S1-trailer.mp4")
        val otherSeason = ref("$show\\Клиника-S2-trailer.mp4")
        val bare = ref("$video\\trailer.mp4")
        val all = listOf(bare, otherSeason, seasonAtRoot, own)

        assertEquals(own, resolveTrailer(all, video, show, "Clinic.S01E02", null))
        // Season taken from "S01E03" in the name when there's no .nfo value.
        assertEquals(seasonAtRoot, resolveTrailer(all, video, show, "Clinic.S01E03", null))
        // An .nfo season wins over the file name.
        assertEquals(otherSeason, resolveTrailer(all, video, show, "Clinic.S01E03", 2))
        assertEquals(bare, resolveTrailer(all, video, show, "Clinic.S03E01", null))
        val movieTrailer = ref("Фильмы\\Престиж\\trailer-02.mkv")
        assertEquals(movieTrailer, resolveTrailer(listOf(bare, movieTrailer), "Фильмы\\Престиж", null, "Престиж", null))
        // Another video's own trailer in the same folder is not ours.
        assertNull(resolveTrailer(listOf(ref("Фильмы\\Престиж\\Другой-trailer.mp4")), "Фильмы\\Престиж", null, "Престиж", null))
    }
}
