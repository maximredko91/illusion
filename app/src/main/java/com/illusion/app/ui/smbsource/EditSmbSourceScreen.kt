package com.illusion.app.ui.smbsource

import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.viewmodel.compose.viewModel
import com.illusion.app.R
import com.illusion.app.data.local.entity.SmbSourceEntity
import com.illusion.app.data.repository.SmbSourceRepository
import com.illusion.app.ui.common.focusHighlight
import com.illusion.app.work.WorkScheduler

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditSmbSourceScreen(
    sourceId: Long,
    smbSourceRepository: SmbSourceRepository,
    onSaved: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    var source by remember { mutableStateOf<SmbSourceEntity?>(null) }
    var loaded by remember { mutableStateOf(false) }
    LaunchedEffect(sourceId) {
        source = smbSourceRepository.getById(sourceId)
        loaded = true
    }

    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.edit_source_title)) },
                navigationIcon = {
                    val backSource = remember { MutableInteractionSource() }
                    IconButton(onClick = onBack, interactionSource = backSource, modifier = Modifier.focusHighlight(backSource)) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.details_back))
                    }
                }
            )
        }
    ) { innerPadding ->
        val currentSource = source
        if (!loaded || currentSource == null) {
            Box(modifier = Modifier.fillMaxSize().padding(innerPadding), contentAlignment = Alignment.Center) {
                if (loaded) Text(stringResource(R.string.edit_source_not_found)) else CircularProgressIndicator()
            }
        } else {
            EditSmbSourceForm(currentSource, smbSourceRepository, innerPadding, onSaved)
        }
    }
}

@Composable
private fun EditSmbSourceForm(
    source: SmbSourceEntity,
    smbSourceRepository: SmbSourceRepository,
    innerPadding: androidx.compose.foundation.layout.PaddingValues,
    onSaved: () -> Unit
) {
    val context = LocalContext.current
    val viewModel: SmbSourceFormViewModel = viewModel(
        key = "edit-${source.id}",
        factory = SmbSourceFormViewModel.factory(smbSourceRepository, source)
    )
    val state by viewModel.state.collectAsState()
    val hostSuggestions by viewModel.hostSuggestions.collectAsState()
    val shareSuggestions by viewModel.shareSuggestions.collectAsState()
    val requestLocalNetwork = rememberLocalNetworkPermissionGate(onDenied = viewModel::reportLocalNetworkPermissionDenied)

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
                        WorkScheduler.enqueueOneTimeScan(context)
                        onSaved()
                    }
                }
            },
            saveLabel = stringResource(R.string.onboarding_save),
            hostSuggestions = hostSuggestions,
            shareSuggestions = shareSuggestions
        )
    }
}
