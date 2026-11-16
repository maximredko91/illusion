package com.illusion.app.ui.common

import java.time.LocalDate

/**
 * A day of the year the splash screen marks - an easter egg, deliberately undocumented outside
 * this file and CLAUDE.md. Two dates: 28 December, when the Lumière brothers held the first paid
 * public screening in 1895 (the usual "birthday of cinema"), and 27 August, Russian Cinema Day.
 * On either one [AppSplashOverlay] turns monochrome, lingers a little and shows a line in the
 * manner of a silent-film intertitle.
 */
sealed interface CinemaOccasion {
    /** [years] since the first screening - the number the intertitle shows. */
    data class Birthday(val years: Int) : CinemaOccasion

    data object RussianCinemaDay : CinemaOccasion
}

fun cinemaOccasion(today: LocalDate): CinemaOccasion? = when {
    today.monthValue == 12 && today.dayOfMonth == 28 -> CinemaOccasion.Birthday(today.year - FIRST_SCREENING_YEAR)
    today.monthValue == 8 && today.dayOfMonth == 27 -> CinemaOccasion.RussianCinemaDay
    else -> null
}

private const val FIRST_SCREENING_YEAR = 1895
