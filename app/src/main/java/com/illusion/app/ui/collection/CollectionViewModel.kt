package com.illusion.app.ui.collection

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.illusion.app.data.local.entity.MediaItemEntity
import com.illusion.app.data.repository.LibraryRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

class CollectionViewModel(
    name: String,
    libraryRepository: LibraryRepository
) : ViewModel() {
    /** Папочная коллекция («<Название> (Коллекция)» на NAS) - тот же источник, что и у ряда «Коллекции» на главной. */
    val items: StateFlow<List<MediaItemEntity>> = libraryRepository
        .observeByFolderCollection(name)
        .map { list -> list.sortedBy { it.year ?: Int.MAX_VALUE } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    companion object {
        fun factory(name: String, libraryRepository: LibraryRepository) = viewModelFactory {
            initializer { CollectionViewModel(name, libraryRepository) }
        }
    }
}
