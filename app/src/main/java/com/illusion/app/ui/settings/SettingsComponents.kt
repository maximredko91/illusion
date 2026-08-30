package com.illusion.app.ui.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/**
 * Title (+ optional description) spanning the full row width, with action button(s) on their own
 * row below - replaces the old `ListItem(headlineContent = ..., trailingContent = button)` pattern
 * used throughout Settings, which squeezed a wrapping title/description into a narrow left column
 * next to a small button pinned to the right edge - read as cramped, especially with a longer
 * description, per feedback. [actions] is typically one full-width `TvAwareOutlinedButton`, or a
 * Row of two weighted ones (e.g. Export/Import) - same "full-width pill(s) below the text" shape
 * as Details' own Play/Download action row.
 */
@Composable
fun SettingsActionCard(
    title: String,
    description: String? = null,
    modifier: Modifier = Modifier,
    actions: @Composable () -> Unit
) {
    Column(modifier = modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp)) {
        Text(title, style = MaterialTheme.typography.bodyLarge)
        description?.let {
            Text(
                it,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 2.dp)
            )
        }
        Column(modifier = Modifier.fillMaxWidth().padding(top = 12.dp)) { actions() }
    }
}
