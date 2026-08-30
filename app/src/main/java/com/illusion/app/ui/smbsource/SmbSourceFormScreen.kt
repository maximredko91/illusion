package com.illusion.app.ui.smbsource

import androidx.compose.foundation.focusGroup
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
import com.illusion.app.ui.common.dpadFieldNavigation
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
    shareSuggestions: List<String> = emptyList(),
    rootPathSuggestions: List<String> = emptyList(),
    displayNameSuggestions: List<String> = emptyList(),
    usernameSuggestions: List<String> = emptyList()
) {
    Column(
        modifier = modifier
            .verticalScroll(rememberScrollState())
            .imePadding()
            .padding(24.dp)
            .focusGroup(),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Every field below now has its own InteractionSource + focusHighlight, matching the
        // pattern already used everywhere else in the app - this form only had it on its two
        // bottom buttons. Confirmed on a real Android TV: without a visible focus indicator on
        // each field, D-pad focus was still technically moving between them (Compose's default
        // focus search doesn't need focusHighlight for that - see that modifier's own KDoc, it's
        // purely a visual border/scale reacting to the same InteractionSource a control already
        // has), but with nothing on screen to show WHERE focus currently was, it read as the
        // remote doing nothing at all rather than a legible cursor moving through the form.
        SuggestibleTextField(
            value = state.displayName,
            onValueChange = onDisplayNameChange,
            label = stringResource(R.string.onboarding_display_name),
            supportingText = stringResource(R.string.onboarding_display_name_help),
            suggestions = displayNameSuggestions,
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
        // Collapsed by default everywhere (phone and TV alike, per feedback) - these four fields
        // are all optional-in-practice (root path only matters when videos aren't at the share's
        // own root; domain/username/password only for a non-guest share), and most setups never
        // touch them. Was expanded-by-default on phone until now - collapsed-by-default on TV
        // was already the case (see historical reasoning: on a real Android TV every field is a
        // full trip through the on-screen keyboard even to skip past it).
        var advancedExpanded by remember { mutableStateOf(false) }
        // Declared here (not next to the password field itself, further down) so both that field
        // and the Save button below can reset it back to hidden.
        var passwordVisible by remember { mutableStateOf(false) }
        val advancedToggleSource = remember { MutableInteractionSource() }
        androidx.compose.material3.TextButton(
            onClick = { advancedExpanded = !advancedExpanded },
            interactionSource = advancedToggleSource,
            modifier = Modifier.focusHighlight(advancedToggleSource)
        ) {
            Text(
                stringResource(
                    if (advancedExpanded) R.string.onboarding_advanced_hide else R.string.onboarding_advanced_show
                )
            )
        }
        androidx.compose.animation.AnimatedVisibility(visible = advancedExpanded) {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                SuggestibleTextField(
                    value = state.rootPath,
                    onValueChange = onRootPathChange,
                    label = stringResource(R.string.onboarding_root_path),
                    supportingText = stringResource(R.string.onboarding_root_path_help),
                    suggestions = rootPathSuggestions,
                    modifier = Modifier.fillMaxWidth()
                )
                val domainSource = remember { MutableInteractionSource() }
                OutlinedTextField(
                    value = state.domain,
                    onValueChange = onDomainChange,
                    label = { Text(stringResource(R.string.onboarding_domain)) },
                    supportingText = { Text(stringResource(R.string.onboarding_domain_help)) },
                    interactionSource = domainSource,
                    modifier = Modifier.fillMaxWidth().focusHighlight(domainSource).dpadFieldNavigation()
                )
                SuggestibleTextField(
                    value = state.username,
                    onValueChange = onUsernameChange,
                    label = stringResource(R.string.onboarding_username),
                    supportingText = stringResource(R.string.onboarding_username_help),
                    suggestions = usernameSuggestions,
                    modifier = Modifier.fillMaxWidth()
                )
        val passwordSource = remember { MutableInteractionSource() }
        val passwordVisibilitySource = remember { MutableInteractionSource() }
        OutlinedTextField(
            value = state.password,
            onValueChange = onPasswordChange,
            label = { Text(stringResource(R.string.onboarding_password)) },
            visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
            trailingIcon = {
                com.illusion.app.ui.common.TvAwareIconButton(
                    onClick = { passwordVisible = !passwordVisible }
                ) {
                    Icon(
                        imageVector = if (passwordVisible) Icons.Filled.VisibilityOff else Icons.Filled.Visibility,
                        contentDescription = stringResource(
                            if (passwordVisible) R.string.onboarding_password_hide else R.string.onboarding_password_show
                        )
                    )
                }
            },
            interactionSource = passwordSource,
            modifier = Modifier.fillMaxWidth().focusHighlight(passwordSource).dpadFieldNavigation()
        )
        // Revealing the password to double-check what was typed used to stay revealed
        // indefinitely afterwards - nothing ever re-hid it, so a successful test (the form stays
        // open afterwards, unlike a successful save which navigates away) left the actual
        // password sitting in plain text on screen for anyone glancing at the device. Re-hides on
        // a successful test, the moment there's no longer any real reason left to have it visible.
        androidx.compose.runtime.LaunchedEffect(state.testState) {
            if (state.testState == TestConnectionState.Success) passwordVisible = false
        }
            }
        }

        if (state.host.isNotBlank() || state.share.isNotBlank()) {
            Text(
                stringResource(R.string.onboarding_path_preview, buildPathPreview(state)),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        com.illusion.app.ui.common.TvAwareOutlinedButton(
            onClick = onTestConnection,
            enabled = state.testState != TestConnectionState.Testing,
            modifier = Modifier.fillMaxWidth()
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

        com.illusion.app.ui.common.TvAwareButton(
            // Also re-hides here (belt and suspenders alongside the successful-test case above) -
            // a failed save (validation, etc.) keeps this screen open with the password still
            // revealed otherwise.
            onClick = { passwordVisible = false; onSave() },
            enabled = state.canSave,
            modifier = Modifier.fillMaxWidth()
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
        val fieldSource = remember { MutableInteractionSource() }
        OutlinedTextField(
            value = value,
            onValueChange = {
                onValueChange(it)
                expanded = true
            },
            label = { Text(label) },
            supportingText = { Text(supportingText) },
            singleLine = true,
            interactionSource = fieldSource,
            modifier = Modifier
                .fillMaxWidth()
                .menuAnchor(ExposedDropdownMenuAnchorType.PrimaryEditable)
                .onFocusChanged { if (it.isFocused && suggestions.isNotEmpty()) expanded = true }
                .focusHighlight(fieldSource)
                // dpadFieldNavigation() unconditionally hijacks DirectionDown to jump to the next
                // field - fine normally, but with the suggestion dropdown open that meant Down
                // NEVER reached the dropdown itself: it always skipped straight past the
                // suggestions to whatever field came after (confirmed on-device: D-pad couldn't
                // select a suggestion at all). Only attach it while there's no dropdown to navigate
                // into - with the dropdown open, Down should do its normal job of moving focus
                // down into the popup's first item instead.
                .let { if (!menuVisible) it.dpadFieldNavigation() else it }
        )
        ExposedDropdownMenu(expanded = menuVisible, onDismissRequest = { expanded = false }) {
            filtered.forEach { suggestion ->
                val itemSource = remember { MutableInteractionSource() }
                DropdownMenuItem(
                    text = { Text(suggestion) },
                    onClick = {
                        onValueChange(suggestion)
                        expanded = false
                    },
                    interactionSource = itemSource,
                    modifier = Modifier.focusHighlight(itemSource)
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
