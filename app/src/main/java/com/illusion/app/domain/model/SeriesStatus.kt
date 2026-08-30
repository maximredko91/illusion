package com.illusion.app.domain.model

import com.illusion.app.data.local.entity.MediaItemEntity

/**
 * tvshow.nfo's <status> is a fixed set of scraper-written English values, not freeform text -
 * covers both the older TheTVDB-style wording ("Continuing"/"Ended") and TMDB's own TV status
 * enum ("Returning Series"/"Planned"/"In Production"/"Ended"/"Canceled"/"Pilot"), since different
 * scrapers/scraper versions write either. Same translate-with-raw-fallback pattern as
 * [editionLabel] - an unrecognized value still shows (untranslated) rather than being hidden.
 */
val MediaItemEntity.statusLabel: String?
    get() = status?.trim()?.takeIf { it.isNotEmpty() }?.let { raw ->
        when (raw.uppercase().replace(' ', '_')) {
            "CONTINUING", "RETURNING_SERIES" -> "Продолжается"
            "ENDED" -> "Завершён"
            "CANCELED", "CANCELLED" -> "Отменён"
            "IN_PRODUCTION" -> "В производстве"
            "PLANNED" -> "Запланирован"
            "PILOT" -> "Пилотный эпизод"
            else -> raw
        }
    }
