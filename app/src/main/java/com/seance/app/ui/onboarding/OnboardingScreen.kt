package com.seance.app.ui.onboarding

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material.icons.filled.Tv
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.seance.app.R
import com.seance.app.data.repository.SmbSourceRepository
import com.seance.app.data.settings.SettingsRepository
import com.seance.app.domain.model.UiMode
import com.seance.app.ui.smbsource.SmbSourceFormFields
import com.seance.app.ui.smbsource.SmbSourceFormViewModel
import com.seance.app.ui.smbsource.rememberLocalNetworkPermissionGate
import com.seance.app.work.WorkScheduler
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OnboardingScreen(
    smbSourceRepository: SmbSourceRepository,
    settingsRepository: SettingsRepository,
    onFinished: (workId: String) -> Unit,
    modifier: Modifier = Modifier
) {
    val currentUiMode by settingsRepository.uiMode.collectAsState(initial = null)
    // Ask once, up front, rather than guessing from android.software.leanback at runtime - the
    // user explicitly wants a manual choice they can also flip later in Settings, not detection.
    var uiModeChosen by remember { mutableStateOf(false) }
    if (currentUiMode == null && !uiModeChosen) {
        UiModeChoiceStep(
            settingsRepository = settingsRepository,
            onChoose = { uiModeChosen = true }
        )
        return
    }

    val context = LocalContext.current
    val viewModel: SmbSourceFormViewModel = viewModel(factory = SmbSourceFormViewModel.factory(smbSourceRepository))
    val state by viewModel.state.collectAsState()
    val requestLocalNetwork = rememberLocalNetworkPermissionGate(onDenied = viewModel::reportLocalNetworkPermissionDenied)

    Scaffold(
        modifier = modifier,
        topBar = { TopAppBar(title = { Text(stringResource(R.string.onboarding_title)) }) }
    ) { innerPadding ->
        Column(modifier = Modifier.fillMaxSize().padding(innerPadding)) {
            SmbSourceFormFields(
                state = state,
                onDisplayNameChange = viewModel::updateDisplayName,
                onHostChange = viewModel::updateHost,
                onShareChange = viewModel::updateShare,
                onRootPathChange = viewModel::updateRootPath,
                onDomainChange = viewModel::updateDomain,
                onUsernameChange = viewModel::updateUsername,
                onPasswordChange = viewModel::updatePassword,
                onTestConnection = { requestLocalNetwork(viewModel::testConnection) },
                onSave = {
                    requestLocalNetwork {
                        viewModel.save {
                            val workId = WorkScheduler.enqueueOneTimeScan(context)
                            onFinished(workId.toString())
                        }
                    }
                },
                saveLabel = stringResource(R.string.onboarding_finish)
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun UiModeChoiceStep(
    settingsRepository: SettingsRepository,
    onChoose: () -> Unit
) {
    val scope = rememberCoroutineScope()
    fun choose(mode: UiMode) {
        scope.launch { settingsRepository.setUiMode(mode) }
        onChoose()
    }
    Scaffold(topBar = { TopAppBar(title = { Text(stringResource(R.string.onboarding_ui_mode_title)) }) }) { innerPadding ->
        Column(
            modifier = Modifier.fillMaxSize().padding(innerPadding).padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text(stringResource(R.string.onboarding_ui_mode_description))
            Button(onClick = { choose(UiMode.PHONE) }, modifier = Modifier.fillMaxWidth()) {
                Icon(Icons.Default.PhoneAndroid, contentDescription = null)
                Text(stringResource(R.string.onboarding_ui_mode_phone), modifier = Modifier.padding(start = 8.dp))
            }
            Button(onClick = { choose(UiMode.TV) }, modifier = Modifier.fillMaxWidth()) {
                Icon(Icons.Default.Tv, contentDescription = null)
                Text(stringResource(R.string.onboarding_ui_mode_tv), modifier = Modifier.padding(start = 8.dp))
            }
        }
    }
}
