package com.illusion.app.ui.navigation

import com.illusion.app.domain.model.Category
import kotlinx.serialization.Serializable

sealed interface Destination {
    @Serializable
    data object Splash : Destination

    /**
     * Hosts Home + all three Library categories as one NavHost entry, switching between them via
     * local state (a Crossfade) instead of separate backstack entries - see the comment in
     * IllusionNavHost.TabsHost for why: NavController's popUpTo+saveState+restoreState round trip
     * (needed to preserve scroll position across tab visits) made Navigation Compose's
     * AnimatedContent treat every tab switch as a full backstack replace, hard-cutting to a blank
     * frame instead of running any transition.
     */
    @Serializable
    data object Tabs : Destination

    @Serializable
    data class Search(val initialQuery: String? = null) : Destination

    /** Full, sortable/filterable tag browser - Search's own inline chip row only surfaces the top N most common tags (a large library can have thousands of distinct freeform <tag> values, too many to dump unranked into a chip row). Reached from Search, picking a tag navigates back to Search with it pre-filled. */
    @Serializable
    data object Tags : Destination

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
    /** [allowDismiss] is false for the very first scan (straight out of onboarding) - the library is still empty at that point, so "watch while it scans" has nothing to offer yet. Manual rescans from Settings always allow it (there's already a library to browse). */
    data class ScanProgress(val workId: String, val allowDismiss: Boolean = true) : Destination

    /** Developer-only "add media" scraper - only reachable via the hidden password gate in Settings, see DevAccessStore. */
    @Serializable
    data object AddMedia : Destination
}
