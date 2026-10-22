package com.illusion.app.ui.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.illusion.app.R
import com.illusion.app.data.local.entity.SmbSourceEntity
import com.illusion.app.data.repository.SourceMergePlan
import com.illusion.app.ui.common.TvAwareTextButton

/** Что именно объединяем - пара источников плюс посчитанная сводка. */
data class SourceMergeState(
    val from: SmbSourceEntity,
    val into: SmbSourceEntity,
    val plan: SourceMergePlan
)

/**
 * Подтверждение объединения. Показывает числа ДО удаления: объединение необратимо, и пользователь
 * должен видеть, сколько отметок переедет и сколько элементов пропадёт без двойника. Если
 * совпадений не нашлось вовсе, объединять нечего - кнопка не предлагается, потому что это верный
 * признак, что источники указывают на разные шары.
 */
@Composable
internal fun SourceMergeDialog(
    state: SourceMergeState,
    onConfirm: (SourceMergeState) -> Unit,
    onDismiss: () -> Unit
) {
    val plan = state.plan
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.settings_merge_title)) },
        text = {
            Column {
                Text(stringResource(R.string.settings_merge_summary, state.from.displayName, state.into.displayName))
                Spacer(Modifier.height(12.dp))
                if (plan.matched == 0) {
                    Text(
                        stringResource(R.string.settings_merge_no_matches),
                        color = MaterialTheme.colorScheme.error
                    )
                } else {
                    Text(pluralStringResource(R.plurals.settings_merge_matched, plan.matched, plan.matched))
                    Text(pluralStringResource(R.plurals.settings_merge_progress, plan.progressToTransfer, plan.progressToTransfer))
                    Text(pluralStringResource(R.plurals.settings_merge_favorites, plan.favoritesToTransfer, plan.favoritesToTransfer))
                    if (plan.unmatched > 0) {
                        Spacer(Modifier.height(12.dp))
                        Text(
                            pluralStringResource(R.plurals.settings_merge_unmatched, plan.unmatched, plan.unmatched),
                            color = MaterialTheme.colorScheme.error
                        )
                    }
                }
            }
        },
        confirmButton = {
            if (plan.matched > 0) {
                TvAwareTextButton(onClick = { onConfirm(state) }) {
                    Text(stringResource(R.string.settings_merge_confirm))
                }
            }
        },
        dismissButton = {
            TvAwareTextButton(onClick = onDismiss) {
                Text(stringResource(R.string.action_cancel))
            }
        }
    )
}

/**
 * Выбор источника, который останется. Предлагается только при ровно двух источниках: при большем
 * числе «объединить» перестаёт быть одним понятным действием, а случай, ради которого это сделано
 * - один диск, заведённый дважды из разных сетей, - всегда про два.
 */
@Composable
internal fun SourceMergePickerDialog(
    sources: List<SmbSourceEntity>,
    onPick: (keep: SmbSourceEntity, remove: SmbSourceEntity) -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.settings_merge_title)) },
        text = { Text(stringResource(R.string.settings_merge_pick)) },
        confirmButton = {
            Column {
                sources.forEach { keep ->
                    val remove = sources.first { it.id != keep.id }
                    TvAwareTextButton(onClick = { onPick(keep, remove) }) {
                        Text(stringResource(R.string.settings_merge_keep, keep.displayName))
                    }
                }
            }
        },
        dismissButton = {
            TvAwareTextButton(onClick = onDismiss) {
                Text(stringResource(R.string.action_cancel))
            }
        }
    )
}
