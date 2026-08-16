package com.seance.app.ui.navigation

import com.seance.app.domain.model.Category
import kotlinx.serialization.Serializable

sealed interface Destination {
    @Serializable
    data object Splash : Destination

    @Serializable
    data object Home : Destination

    @Serializable
    data class Library(val category: Category) : Destination

    @Serializable
    data object Search : Destination

    @Serializable
    data class Details(val stableId: String) : Destination

    @Serializable
    data class Person(val name: String) : Destination

    @Serializable
    data object Favorites : Destination

    @Serializable
    data object History : Destination

    @Serializable
    data object Downloads : Destination

    @Serializable
    data class Player(val stableId: String, val trailer: Boolean = false) : Destination

    @Serializable
    data object Settings : Destination

    @Serializable
    data object Cache : Destination

    @Serializable
    data object Onboarding : Destination

    @Serializable
    data object AddSmbSource : Destination

    @Serializable
    data class EditSmbSource(val sourceId: Long) : Destination

    @Serializable
    data class ScanProgress(val workId: String) : Destination
}
