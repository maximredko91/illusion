package com.illusion.app.domain.model

enum class SortOrder {
    YEAR,
    TITLE,
    RATING,
    DATE_ADDED
}

/** TITLE reads naturally A-Z; year/rating/date added read naturally newest/highest-first. Single source of truth shared by the ViewModel (switching order resets to this) and the sort menu (previewing the direction switching would land on). */
val SortOrder.defaultAscending: Boolean
    get() = this == SortOrder.TITLE
