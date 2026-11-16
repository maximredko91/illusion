package com.illusion.app.ui.common

import com.illusion.app.ui.search.isLumiereQuery
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class EasterEggsTest {

    @Test
    fun birthdayOfCinemaCountsFromTheFirstScreening() {
        assertEquals(CinemaOccasion.Birthday(131), cinemaOccasion(LocalDate.of(2026, 12, 28)))
        assertEquals(CinemaOccasion.Birthday(135), cinemaOccasion(LocalDate.of(2030, 12, 28)))
    }

    @Test
    fun russianCinemaDayIsTheOtherDate() {
        assertEquals(CinemaOccasion.RussianCinemaDay, cinemaOccasion(LocalDate.of(2027, 8, 27)))
    }

    @Test
    fun anOrdinaryDayIsJustADay() {
        assertNull(cinemaOccasion(LocalDate.of(2026, 12, 27)))
        assertNull(cinemaOccasion(LocalDate.of(2026, 12, 29)))
        assertNull(cinemaOccasion(LocalDate.of(2026, 11, 16)))
        // day and month the other way round
        assertNull(cinemaOccasion(LocalDate.of(2026, 8, 28)))
    }

    @Test
    fun theTrainComesOnlyForTheWholeQuery() {
        assertTrue(isLumiereQuery("Люмьер"))
        assertTrue(isLumiereQuery("  прибытие поезда "))
        assertTrue(isLumiereQuery("Lumière"))
        assertTrue(isLumiereQuery("БРАТЬЯ ЛЮМЬЕР"))
        // a title that merely contains the word must still be searchable in peace
        assertFalse(isLumiereQuery("люмьер и компания"))
        assertFalse(isLumiereQuery("поезд"))
        assertFalse(isLumiereQuery(""))
    }
}
