package com.illusion.app.ui.settings

import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.illusion.app.R
import com.illusion.app.data.scan.RemovedReport
import com.illusion.app.data.scan.RemovedReportStore
import com.illusion.app.ui.common.TvAwareOutlinedButton
import com.illusion.app.ui.common.TvAwareTextButton
import java.text.DateFormat
import java.util.Date
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Settings row for what the last scan removed from the library (see [RemovedReportStore]); draws
 * nothing until some scan has removed something. [refreshKey] changes when a scan starts or ends,
 * so the row shows up right after the scan that produced the report.
 */
@Composable
internal fun LastScanRemovalsCard(refreshKey: Any?) {
    val context = LocalContext.current.applicationContext
    val report by produceState<RemovedReport?>(null, refreshKey) {
        value = withContext(Dispatchers.IO) { RemovedReportStore(context).load() }
    }
    val current = report ?: return
    var showList by remember { mutableStateOf(false) }
    val date = remember(current.atMs) {
        DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT).format(Date(current.atMs))
    }

    SettingsDivider()
    SettingsActionCard(
        title = stringResource(R.string.settings_last_removed_title),
        description = stringResource(R.string.settings_last_removed_description, date, current.itemCount)
    ) {
        TvAwareOutlinedButton(onClick = { showList = true }, modifier = Modifier.fillMaxWidth()) {
            Text(stringResource(R.string.settings_last_removed_action))
        }
    }

    if (showList) {
        AlertDialog(
            onDismissRequest = { showList = false },
            title = { Text(stringResource(R.string.scan_progress_removed_title)) },
            text = {
                LazyColumn(modifier = Modifier.heightIn(max = 420.dp).focusGroup()) {
                    items(current.titles) { title ->
                        Text(
                            title,
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.padding(vertical = 4.dp)
                        )
                    }
                }
            },
            confirmButton = {
                TvAwareTextButton(onClick = { showList = false }) {
                    Text(stringResource(R.string.action_close))
                }
            }
        )
    }
}
