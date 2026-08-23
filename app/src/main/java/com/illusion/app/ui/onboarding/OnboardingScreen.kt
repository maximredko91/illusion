package com.illusion.app.ui.onboarding

import androidx.compose.foundation.interaction.MutableInteractionSource
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
import com.illusion.app.R
import com.illusion.app.data.repository.SmbSourceRepository
import com.illusion.app.data.settings.SettingsRepository
import com.illusion.app.domain.model.UiMode
import com.illusion.app.ui.common.focusHighlight
import com.illusion.app.ui.smbsource.SmbSourceFormFields
import com.illusion.app.ui.smbsource.SmbSourceFormViewModel
import com.illusion.app.ui.smbsource.rememberLocalNetworkPermissionGate
import com.illusion.app.work.WorkScheduler
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
    val hostSuggestions by viewModel.hostSuggestions.collectAsState()
    val shareSuggestions by viewModel.shareSuggestions.collectAsState()
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
                saveLabel = stringResource(R.string.onboarding_finish),
                hostSuggestions = hostSuggestions,
                shareSuggestions = shareSuggestions
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
            val phoneSource = remember { MutableInteractionSource() }
            Button(
                onClick = { choose(UiMode.PHONE) },
                interactionSource = phoneSource,
                modifier = Modifier.fillMaxWidth().focusHighlight(phoneSource)
            ) {
                Icon(Icons.Default.PhoneAndroid, contentDescription = null)
                Text(stringResource(R.string.onboarding_ui_mode_phone), modifier = Modifier.padding(start = 8.dp))
            }
            val tvSource = remember { MutableInteractionSource() }
            Button(
                onClick = { choose(UiMode.TV) },
                interactionSource = tvSource,
                modifier = Modifier.fillMaxWidth().focusHighlight(tvSource)
            ) {
                Icon(Icons.Default.Tv, contentDescription = null)
                Text(stringResource(R.string.onboarding_ui_mode_tv), modifier = Modifier.padding(start = 8.dp))
            }
        }
    }
}
