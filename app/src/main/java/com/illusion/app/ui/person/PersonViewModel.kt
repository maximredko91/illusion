package com.illusion.app.ui.person

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.illusion.app.data.local.entity.MediaItemEntity
import com.illusion.app.data.repository.LibraryRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class PersonViewModel(
    name: String,
    libraryRepository: LibraryRepository
) : ViewModel() {
    private val _items = MutableStateFlow<List<MediaItemEntity>>(emptyList())
    val items: StateFlow<List<MediaItemEntity>> = _items.asStateFlow()

    init {
        viewModelScope.launch {
            _items.value = libraryRepository.getFilmography(name)
        }
    }

    companion object {
        fun factory(name: String, libraryRepository: LibraryRepository) = viewModelFactory {
            initializer { PersonViewModel(name, libraryRepository) }
        }
    }
}
