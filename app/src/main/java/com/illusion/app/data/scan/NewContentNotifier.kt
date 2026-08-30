package com.illusion.app.data.scan

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Backs [com.illusion.app.ui.home.HomeScreen]'s "доступны новые файлы" banner - a process-wide
 * (not persisted) flag set by a cheap [LibraryScanner.hasNewContent] check run once per app launch
 * (see the Splash destination in IllusionNavHost), and cleared once the user acts on it (starts a
 * rescan from the banner) or any rescan completes by any other means (Settings' own "Пересканировать
 * сейчас", so the banner doesn't keep nagging about content a manual rescan already picked up).
 */
object NewContentNotifier {
    private val _hasNewContent = MutableStateFlow(false)
    val hasNewContent: StateFlow<Boolean> = _hasNewContent.asStateFlow()

    suspend fun check(scanner: LibraryScanner) {
        if (scanner.hasNewContent()) _hasNewContent.value = true
    }

    fun clear() {
        _hasNewContent.value = false
    }
}
