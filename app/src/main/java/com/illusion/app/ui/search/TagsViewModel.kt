package com.illusion.app.ui.search

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.illusion.app.data.repository.LibraryRepository
import com.illusion.app.data.translation.TagTranslationRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

enum class TagSortOrder { COUNT, ALPHABETICAL }

/** [label] is the Russian translation once resolved, or [tag] itself while still pending/untranslatable - see TagTranslationRepository. */
data class TagCount(val tag: String, val count: Int, val label: String = tag)

/** Full tag browser backing TagsScreen - unlike Search's own inline top-N chip row, this loads every distinct <tag> in the library (a large one can have thousands - see SearchViewModel's own comment), so sort/filter are the only way to make that actually navigable. */
class TagsViewModel(
    private val libraryRepository: LibraryRepository,
    private val translationRepository: TagTranslationRepository
) : ViewModel() {
    private val _allTags = MutableStateFlow<List<TagCount>>(emptyList())
    private val _sortOrder = MutableStateFlow(TagSortOrder.COUNT)
    val sortOrder: StateFlow<TagSortOrder> = _sortOrder.asStateFlow()

    private val _filter = MutableStateFlow("")
    val filter: StateFlow<String> = _filter.asStateFlow()

    private val _isLoading = MutableStateFlow(true)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _tags = MutableStateFlow<List<TagCount>>(emptyList())
    val tags: StateFlow<List<TagCount>> = _tags.asStateFlow()

    init {
        viewModelScope.launch {
            val counts = withContext(Dispatchers.Default) {
                libraryRepository.getAll()
                    .flatMap { it.tags }
                    .groupingBy { it }
                    .eachCount()
                    .map { (tag, count) -> TagCount(tag, count) }
            }
            // Already-translated tags (from a prior ML Kit lookup or a Settings DeepL upgrade)
            // show their real label immediately - only genuinely new tags fall back to the raw
            // English text below while they resolve one at a time.
            val cachedLabels = translationRepository.getCached()
            _allTags.value = counts.map { it.copy(label = cachedLabels[it.tag] ?: it.tag) }
            _isLoading.value = false
            recompute()

            // Lazy, on-device (ML Kit) - a large library can have thousands of never-before-seen
            // tags on first visit, and one launch{} per tag would fire that many concurrent
            // translator calls at once. Sequential instead: still fills in the list live (each
            // result updates the UI as soon as it resolves) without hammering ML Kit's client
            // with thousands of simultaneous requests.
            withContext(Dispatchers.Default) {
                counts.filter { it.tag !in cachedLabels }.forEach { tagCount ->
                    val label = translationRepository.translateLazily(tagCount.tag)
                    _allTags.update { list -> list.map { if (it.tag == tagCount.tag) it.copy(label = label) else it } }
                    recompute()
                }
            }
        }
    }

    fun setSortOrder(order: TagSortOrder) {
        _sortOrder.value = order
        recompute()
    }

    fun setFilter(value: String) {
        _filter.value = value
        recompute()
    }

    private fun recompute() {
        val filtered = _allTags.value.filter {
            _filter.value.isBlank() || it.label.contains(_filter.value, ignoreCase = true) || it.tag.contains(_filter.value, ignoreCase = true)
        }
        _tags.value = when (_sortOrder.value) {
            TagSortOrder.COUNT -> filtered.sortedWith(compareByDescending<TagCount> { it.count }.thenBy { it.label })
            TagSortOrder.ALPHABETICAL -> filtered.sortedBy { it.label }
        }
    }

    companion object {
        fun factory(libraryRepository: LibraryRepository, translationRepository: TagTranslationRepository) = viewModelFactory {
            initializer { TagsViewModel(libraryRepository, translationRepository) }
        }
    }
}
