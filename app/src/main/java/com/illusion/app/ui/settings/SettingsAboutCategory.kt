package com.illusion.app.ui.settings

import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.foundation.LocalIndication
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.foundation.clickable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.foundation.layout.Row
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.material.icons.filled.Check
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.material3.ListItem
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.material3.ListItemDefaults
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.res.stringResource
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.unit.dp
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.core.net.toUri
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import com.illusion.app.R
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import com.illusion.app.data.local.entity.SmbSourceEntity
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import com.illusion.app.ui.common.TvAwareButton
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import com.illusion.app.ui.common.TvAwareOutlinedButton
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import com.illusion.app.ui.common.focusHighlight
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import com.illusion.app.ui.common.segmentTick
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import com.illusion.app.ui.common.tick
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import kotlinx.coroutines.flow.Flow
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.foundation.layout.ColumnScope

/** Категория «О приложении»: версия, обновления, ссылки на проект. */
@Composable
internal fun ColumnScope.SettingsAboutCategory(
    effectiveDarkTheme: Boolean,
    sources: List<SmbSourceEntity>,
    upToDateMessage: String?,
    onDismissUpToDateMessage: () -> Unit,
    onCheckForUpdates: () -> Unit,
    updateCheckIntervalHours: Flow<Int>,
    onUpdateCheckIntervalChange: (Int) -> Unit,
    updateSource: Flow<com.illusion.app.domain.model.UpdateSource>,
    onUpdateSourceChange: (com.illusion.app.domain.model.UpdateSource) -> Unit,
    localUpdateSourceId: Flow<Long?>,
    onLocalUpdateSourceIdChange: (Long) -> Unit,
    onOpenCategory: (String) -> Unit
) {
    val haptics = LocalHapticFeedback.current
    AboutLayout(
        header = {
            AboutBrandHeader()
            var buildNumberTapCount by remember { mutableStateOf(0) }
            var lastBuildNumberTapAt by remember { mutableStateOf(0L) }
            val eggContext = LocalContext.current
            val eggResources = androidx.compose.ui.platform.LocalResources.current
            // A fresh Toast.makeText().show() per tap QUEUES on Android instead of
            // replacing the previous one - confirmed on-device: tapping through the
            // 4/5/6 countdown fired 3 separate ~2s toasts back to back, so the real
            // punchline on tap 7 only appeared after several seconds of stacked
            // wait, and the system's rapid-toast rate limiting on top of that
            // visibly truncated/overlapped the text. Cancelling the previous Toast
            // before showing the next one collapses that into a single, instantly-
            // updating toast, same as a live countdown should look.
            var activeEggToast by remember { mutableStateOf<android.widget.Toast?>(null) }
            // The punchline (unlike the short countdown hints) is a full sentence -
            // MIUI's own Toast rendering (this device's OEM skin) truncates longer
            // toast text to one line with an ellipsis rather than wrapping it, no
            // matter LENGTH_LONG - confirmed on-device even after fixing the
            // queueing above. A real dialog is never OEM-truncated like that.
            var showEggDialog by remember { mutableStateOf(false) }
            val buildNumberSource = remember { MutableInteractionSource() }
            ListItem(
                headlineContent = { Text(stringResource(R.string.settings_about_build, com.illusion.app.BuildConfig.VERSION_NAME, com.illusion.app.BuildConfig.VERSION_CODE), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = androidx.compose.ui.text.style.TextAlign.Center, modifier = Modifier.fillMaxWidth()) },
                colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                modifier = Modifier
                    .fillMaxWidth()
                    .focusHighlight(buildNumberSource)
                    .clickable(interactionSource = buildNumberSource, indication = LocalIndication.current) {
                        val now = System.currentTimeMillis()
                        buildNumberTapCount = if (now - lastBuildNumberTapAt > 1500) 1 else buildNumberTapCount + 1
                        lastBuildNumberTapAt = now
                        when {
                            buildNumberTapCount in 4..6 -> {
                                haptics.segmentTick()
                                activeEggToast?.cancel()
                                activeEggToast = android.widget.Toast.makeText(
                                    eggContext,
                                    eggResources.getString(R.string.settings_easter_egg_countdown, 7 - buildNumberTapCount),
                                    android.widget.Toast.LENGTH_SHORT
                                ).also { it.show() }
                            }
                            buildNumberTapCount >= 7 -> {
                                haptics.tick()
                                buildNumberTapCount = 0
                                activeEggToast?.cancel()
                                showEggDialog = true
                            }
                        }
                    }
            )
            if (showEggDialog) {
                SecretCinemaDialog(onDismiss = { showEggDialog = false })
            }
        },
        content = {
            SettingsSectionLabel(stringResource(R.string.settings_about_updates))
            SettingsGroup {
                Column(Modifier.fillMaxWidth().padding(16.dp)) {
                    TvAwareButton(
                        onClick = { onDismissUpToDateMessage(); onCheckForUpdates() },
                        modifier = Modifier.fillMaxWidth()
                    ) { Text(stringResource(R.string.settings_about_check_updates)) }
                    if (upToDateMessage != null) {
                        AboutUpdateStatus(upToDateMessage, effectiveDarkTheme)
                    }
                }
                SettingsDivider()
                // Разрешение «установка неизвестных приложений» раньше нигде не
                // показывалось: о нём узнавали только в момент установки, когда
                // приложение внезапно уводило в системные настройки. Теперь его
                // состояние видно заранее и включается отсюда же.
                val installContext = LocalContext.current
                var canInstallUpdates by remember { mutableStateOf(com.illusion.app.data.update.UpdateInstaller.canInstallPackages(installContext)) }
                val installLifecycle = androidx.lifecycle.compose.LocalLifecycleOwner.current
                // Разрешение выдаётся в системных настройках, то есть за
                // пределами приложения - пересчитываем его на каждом возврате.
                DisposableEffect(installLifecycle) {
                    val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
                        if (event == androidx.lifecycle.Lifecycle.Event.ON_RESUME) {
                            canInstallUpdates = com.illusion.app.data.update.UpdateInstaller.canInstallPackages(installContext)
                        }
                    }
                    installLifecycle.lifecycle.addObserver(observer)
                    onDispose { installLifecycle.lifecycle.removeObserver(observer) }
                }
                SettingsActionCard(
                    title = stringResource(R.string.settings_install_permission),
                    description = stringResource(
                        if (canInstallUpdates) R.string.settings_install_permission_granted
                        else R.string.settings_install_permission_missing
                    )
                ) {
                    if (canInstallUpdates) {
                        // Разрешение выдано, а проверка при установке всё равно
                        // показывается - её включает прошивка (у Xiaomi это
                        // «Проверка безопасности»), и выключить её из приложения
                        // нельзя: публичного API нет. Всё, что тут можно честно
                        // сделать - открыть нужный системный экран.
                        Text(
                            stringResource(R.string.settings_install_scan_hint),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(bottom = 8.dp)
                        )
                        com.illusion.app.ui.common.TvAwareOutlinedButton(
                            onClick = {
                                // По порядку: экран самой Play Защиты (он и
                                // ругается на редкие APK), затем Play Маркет,
                                // затем системная безопасность - на устройстве
                                // без сервисов Google первых двух просто нет.
                                val intents = listOf(
                                    android.content.Intent().setComponent(
                                        android.content.ComponentName(
                                            "com.google.android.gms",
                                            "com.google.android.gms.security.settings.VerifyAppsSettingsActivity"
                                        )
                                    ),
                                    android.content.Intent(
                                        android.content.Intent.ACTION_VIEW,
                                        "https://play.google.com/store/apps/details?id=com.google.android.gms".toUri()
                                    ),
                                    android.content.Intent(android.provider.Settings.ACTION_SECURITY_SETTINGS),
                                    android.content.Intent(android.provider.Settings.ACTION_SETTINGS)
                                )
                                intents.firstOrNull { intent ->
                                    intent.resolveActivity(installContext.packageManager) != null &&
                                        runCatching {
                                            installContext.startActivity(
                                                intent.addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
                                            )
                                        }.isSuccess
                                }
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) { Text(stringResource(R.string.settings_install_scan_action)) }
                    }
                    if (!canInstallUpdates) {
                        TvAwareButton(
                            onClick = {
                                runCatching {
                                    installContext.startActivity(
                                        com.illusion.app.data.update.UpdateInstaller.installPermissionSettingsIntent(installContext)
                                    )
                                }
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) { Text(stringResource(R.string.settings_install_permission_action)) }
                    }
                }
                SettingsDivider()
                val currentUpdateCheckIntervalHours by updateCheckIntervalHours.collectAsState(initial = 24)
                SettingsActionCard(title = stringResource(R.string.settings_update_check_interval)) {
                    UpdateCheckIntervalMenu(currentUpdateCheckIntervalHours, onUpdateCheckIntervalChange, modifier = Modifier.fillMaxWidth())
                }
                SettingsDivider()
                val currentUpdateSource by updateSource.collectAsState(initial = com.illusion.app.domain.model.UpdateSource.GITHUB)
                SettingsActionCard(
                    title = stringResource(R.string.settings_update_source),
                    description = stringResource(R.string.settings_update_source_hint)
                ) {
                    UpdateSourceMenu(currentUpdateSource, onUpdateSourceChange, modifier = Modifier.fillMaxWidth())
                }
                if (currentUpdateSource == com.illusion.app.domain.model.UpdateSource.LOCAL) {
                    SettingsDivider()
                    val currentLocalUpdateSourceId by localUpdateSourceId.collectAsState(initial = null)
                    // Switching the row above to "Локально" without also picking a
                    // specific source here (an easy thing to miss - two separate
                    // dropdowns, only the first one is obviously "the switch") left
                    // localUpdateSourceId null, and the actual check silently failed
                    // with "не выбран источник" - confirmed on-device. Auto-picks the
                    // first configured source instead of requiring that second tap
                    // whenever there's an unambiguous default (exactly one, or none
                    // chosen yet) to pick.
                    LaunchedEffect(currentUpdateSource, sources) {
                        if (currentLocalUpdateSourceId == null && sources.isNotEmpty()) {
                            onLocalUpdateSourceIdChange(sources.first().id)
                        }
                    }
                    SettingsActionCard(
                        title = stringResource(R.string.settings_local_update_source),
                        description = if (sources.isEmpty()) {
                            stringResource(R.string.settings_local_update_source_none)
                        } else {
                            stringResource(R.string.settings_local_update_source_path_hint)
                        }
                    ) {
                        if (sources.isNotEmpty()) {
                            LocalUpdateSourceMenu(sources, currentLocalUpdateSourceId, onLocalUpdateSourceIdChange, modifier = Modifier.fillMaxWidth())
                        }
                    }
                }
            }
            SettingsSectionLabel(stringResource(R.string.settings_about_project))
            SettingsGroup {
                AboutLinkRow(stringResource(R.string.settings_about_developer), "maximredko91",
                    url = "https://github.com/maximredko91")
                SettingsDivider()
                AboutLinkRow(stringResource(R.string.settings_about_source_code), "GitHub · Illusion",
                    url = "https://github.com/maximredko91/illusion")
                SettingsDivider()
                AboutLinkRow(stringResource(R.string.settings_about_license), stringResource(R.string.settings_about_license_value),
                    url = "https://github.com/maximredko91/illusion/blob/main/LICENSE")
                SettingsDivider()
                AboutLinkRow(stringResource(R.string.settings_about_libraries),
                    stringResource(R.string.settings_about_libraries_hint),
                    onClick = { onOpenCategory("libraries") })
            }
        }
    )
}
