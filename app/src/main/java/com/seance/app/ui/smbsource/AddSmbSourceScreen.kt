package com.seance.app.ui.smbsource

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.viewmodel.compose.viewModel
import com.seance.app.R
import com.seance.app.data.repository.SmbSourceRepository
import com.seance.app.work.WorkScheduler

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddSmbSourceScreen(
    smbSourceRepository: SmbSourceRepository,
    onSaved: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val viewModel: SmbSourceFormViewModel = viewModel(factory = SmbSourceFormViewModel.factory(smbSourceRepository))
    val state by viewModel.state.collectAsState()
    val requestLocalNetwork = rememberLocalNetworkPermissionGate(onDenied = viewModel::reportLocalNetworkPermissionDenied)

    Scaffold(
        modifier = modifier,
        topBar = { TopAppBar(title = { Text(stringResource(R.string.add_source_title)) }) }
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
                            WorkScheduler.enqueueOneTimeScan(context)
                            onSaved()
                        }
                    }
                },
                saveLabel = stringResource(R.string.onboarding_save)
            )
        }
    }
}
