package com.seance.app.ui.person

import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.seance.app.R
import com.seance.app.data.repository.LibraryRepository
import com.seance.app.ui.common.PosterCard
import com.seance.app.ui.common.posterGridColumns

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PersonScreen(
    name: String,
    libraryRepository: LibraryRepository,
    onOpenItem: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val viewModel: PersonViewModel = viewModel(
        key = name,
        factory = PersonViewModel.factory(name, libraryRepository)
    )
    val items by viewModel.items.collectAsState()

    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                windowInsets = com.seance.app.ui.common.rememberLatchedStatusBarsInsets(),
                title = { Text(stringResource(R.string.person_title, name)) }
            )
        }
    ) { innerPadding ->
        LazyVerticalGrid(
            columns = posterGridColumns(),
            modifier = Modifier.fillMaxSize().padding(innerPadding).focusGroup(),
            contentPadding = PaddingValues(8.dp)
        ) {
            items(items, key = { it.stableId }) { item ->
                PosterCard(item = item, onClick = { onOpenItem(item.stableId) }, modifier = Modifier.padding(4.dp))
            }
        }
    }
}
