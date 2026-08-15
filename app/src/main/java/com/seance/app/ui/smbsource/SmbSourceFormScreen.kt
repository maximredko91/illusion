package com.seance.app.ui.smbsource

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.seance.app.R
import com.seance.app.ui.common.focusHighlight

@Composable
fun SmbSourceFormFields(
    state: SmbSourceFormState,
    onDisplayNameChange: (String) -> Unit,
    onHostChange: (String) -> Unit,
    onShareChange: (String) -> Unit,
    onRootPathChange: (String) -> Unit,
    onDomainChange: (String) -> Unit,
    onUsernameChange: (String) -> Unit,
    onPasswordChange: (String) -> Unit,
    onTestConnection: () -> Unit,
    onSave: () -> Unit,
    saveLabel: String,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        OutlinedTextField(
            value = state.displayName,
            onValueChange = onDisplayNameChange,
            label = { Text(stringResource(R.string.onboarding_display_name)) },
            supportingText = { Text(stringResource(R.string.onboarding_display_name_help)) },
            modifier = Modifier.fillMaxWidth()
        )
        OutlinedTextField(
            value = state.host,
            onValueChange = onHostChange,
            label = { Text(stringResource(R.string.onboarding_smb_address)) },
            supportingText = { Text(stringResource(R.string.onboarding_smb_address_help)) },
            modifier = Modifier.fillMaxWidth()
        )
        OutlinedTextField(
            value = state.share,
            onValueChange = onShareChange,
            label = { Text(stringResource(R.string.onboarding_share)) },
            supportingText = { Text(stringResource(R.string.onboarding_share_help)) },
            modifier = Modifier.fillMaxWidth()
        )
        OutlinedTextField(
            value = state.rootPath,
            onValueChange = onRootPathChange,
            label = { Text(stringResource(R.string.onboarding_root_path)) },
            supportingText = { Text(stringResource(R.string.onboarding_root_path_help)) },
            modifier = Modifier.fillMaxWidth()
        )
        OutlinedTextField(
            value = state.domain,
            onValueChange = onDomainChange,
            label = { Text(stringResource(R.string.onboarding_domain)) },
            supportingText = { Text(stringResource(R.string.onboarding_domain_help)) },
            modifier = Modifier.fillMaxWidth()
        )
        OutlinedTextField(
            value = state.username,
            onValueChange = onUsernameChange,
            label = { Text(stringResource(R.string.onboarding_username)) },
            supportingText = { Text(stringResource(R.string.onboarding_username_help)) },
            modifier = Modifier.fillMaxWidth()
        )
        OutlinedTextField(
            value = state.password,
            onValueChange = onPasswordChange,
            label = { Text(stringResource(R.string.onboarding_password)) },
            visualTransformation = PasswordVisualTransformation(),
            modifier = Modifier.fillMaxWidth()
        )

        if (state.host.isNotBlank() || state.share.isNotBlank()) {
            Text(
                stringResource(R.string.onboarding_path_preview, buildPathPreview(state)),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        val testConnectionSource = remember { MutableInteractionSource() }
        OutlinedButton(
            onClick = onTestConnection,
            enabled = state.testState != TestConnectionState.Testing,
            interactionSource = testConnectionSource,
            modifier = Modifier.fillMaxWidth().focusHighlight(testConnectionSource)
        ) {
            if (state.testState == TestConnectionState.Testing) {
                CircularProgressIndicator(modifier = Modifier.size(18.dp).padding(end = 8.dp))
            }
            Text(stringResource(R.string.onboarding_test_connection))
        }

        when (val testState = state.testState) {
            TestConnectionState.Testing -> Row(verticalAlignment = Alignment.CenterVertically) {
                CircularProgressIndicator(modifier = Modifier.size(16.dp))
                Text(
                    stringResource(R.string.onboarding_test_testing),
                    modifier = Modifier.padding(start = 8.dp)
                )
            }
            TestConnectionState.Success -> Text(
                stringResource(R.string.onboarding_test_success),
                color = MaterialTheme.colorScheme.primary
            )
            is TestConnectionState.Failure -> Text(
                testState.message,
                color = MaterialTheme.colorScheme.error
            )
            TestConnectionState.Idle -> Unit
        }

        val saveSource = remember { MutableInteractionSource() }
        Button(
            onClick = onSave,
            enabled = state.canSave,
            interactionSource = saveSource,
            modifier = Modifier.fillMaxWidth().focusHighlight(saveSource)
        ) {
            if (state.isSaving) {
                CircularProgressIndicator(modifier = Modifier.padding(end = 8.dp))
            }
            Text(saveLabel)
        }
    }
}

private fun buildPathPreview(state: SmbSourceFormState): String {
    val host = state.host.ifBlank { "?" }
    val share = state.share.ifBlank { "?" }
    val root = state.rootPath.trim('\\', '/')
    return if (root.isBlank()) "\\\\$host\\$share" else "\\\\$host\\$share\\$root"
}
