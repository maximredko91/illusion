package com.illusion.app.ui.smbsource

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import com.illusion.app.R
import com.illusion.app.ui.common.focusHighlight

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
    modifier: Modifier = Modifier,
    hostSuggestions: List<String> = emptyList(),
    shareSuggestions: List<String> = emptyList()
) {
    Column(
        modifier = modifier
            .verticalScroll(rememberScrollState())
            .imePadding()
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
        SuggestibleTextField(
            value = state.host,
            onValueChange = onHostChange,
            label = stringResource(R.string.onboarding_smb_address),
            supportingText = stringResource(R.string.onboarding_smb_address_help),
            suggestions = hostSuggestions,
            modifier = Modifier.fillMaxWidth()
        )
        SuggestibleTextField(
            value = state.share,
            onValueChange = onShareChange,
            label = stringResource(R.string.onboarding_share),
            supportingText = stringResource(R.string.onboarding_share_help),
            suggestions = shareSuggestions,
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
        var passwordVisible by remember { mutableStateOf(false) }
        OutlinedTextField(
            value = state.password,
            onValueChange = onPasswordChange,
            label = { Text(stringResource(R.string.onboarding_password)) },
            visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
            trailingIcon = {
                IconButton(onClick = { passwordVisible = !passwordVisible }) {
                    Icon(
                        imageVector = if (passwordVisible) Icons.Filled.VisibilityOff else Icons.Filled.Visibility,
                        contentDescription = stringResource(
                            if (passwordVisible) R.string.onboarding_password_hide else R.string.onboarding_password_show
                        )
                    )
                }
            },
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

/**
 * A text field that offers previously-entered values (from other SMB sources already saved on
 * this device) as a tap-to-fill dropdown - convenience for repeatedly testing against the same
 * NAS host/share rather than retyping them every time. Not validated history, just "what's
 * already been typed before".
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SuggestibleTextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    supportingText: String,
    suggestions: List<String>,
    modifier: Modifier = Modifier
) {
    var expanded by remember { mutableStateOf(false) }
    val filtered = remember(value, suggestions) {
        suggestions.filter { it != value && it.contains(value, ignoreCase = true) }
    }
    val menuVisible = expanded && filtered.isNotEmpty()

    ExposedDropdownMenuBox(
        expanded = menuVisible,
        onExpandedChange = { expanded = it },
        modifier = modifier
    ) {
        OutlinedTextField(
            value = value,
            onValueChange = {
                onValueChange(it)
                expanded = true
            },
            label = { Text(label) },
            supportingText = { Text(supportingText) },
            singleLine = true,
            modifier = Modifier
                .fillMaxWidth()
                .menuAnchor(ExposedDropdownMenuAnchorType.PrimaryEditable)
                .onFocusChanged { if (it.isFocused && suggestions.isNotEmpty()) expanded = true }
        )
        ExposedDropdownMenu(expanded = menuVisible, onDismissRequest = { expanded = false }) {
            filtered.forEach { suggestion ->
                DropdownMenuItem(
                    text = { Text(suggestion) },
                    onClick = {
                        onValueChange(suggestion)
                        expanded = false
                    }
                )
            }
        }
    }
}

private fun buildPathPreview(state: SmbSourceFormState): String {
    val host = state.host.ifBlank { "?" }
    val share = state.share.ifBlank { "?" }
    val root = state.rootPath.trim('\\', '/')
    return if (root.isBlank()) "\\\\$host\\$share" else "\\\\$host\\$share\\$root"
}
