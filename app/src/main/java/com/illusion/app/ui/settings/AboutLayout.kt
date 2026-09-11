package com.illusion.app.ui.settings

import android.content.Intent
import android.animation.ValueAnimator
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import android.widget.Toast
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import androidx.core.net.toUri
import com.illusion.app.R
import com.illusion.app.domain.model.UiMode
import com.illusion.app.ui.common.LocalUiMode
import com.illusion.app.ui.common.LocalEconomicalMode
import com.illusion.app.ui.common.PerforationStrip
import com.illusion.app.ui.common.focusHighlight

@Composable
internal fun AboutLayout(header: @Composable () -> Unit, content: @Composable () -> Unit) {
    BoxWithConstraints(Modifier.fillMaxWidth().padding(bottom = 20.dp)) {
        if (LocalUiMode.current == UiMode.TV && maxWidth >= 600.dp) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp), horizontalArrangement = Arrangement.spacedBy(20.dp)) {
                Column(Modifier.weight(0.38f).padding(top = 24.dp).focusGroup()) { header() }
                Column(Modifier.weight(0.62f).focusGroup()) { content() }
            }
        } else {
            Column(Modifier.fillMaxWidth()) { header(); content() }
        }
    }
}

@Composable
internal fun AboutBrandHeader() {
    val isTv = LocalUiMode.current == UiMode.TV
    val animate = !LocalEconomicalMode.current && remember { ValueAnimator.areAnimatorsEnabled() }
    var appeared by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { appeared = true }
    val opacity by animateFloatAsState(if (appeared || !animate) 1f else 0f, tween(220), label = "aboutHeader")
    Column(
        Modifier.fillMaxWidth().graphicsLayer { alpha = opacity }
            .padding(start = 24.dp, end = 24.dp, top = 28.dp, bottom = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(Modifier.size(if (isTv) 96.dp else 88.dp).clip(MaterialTheme.shapes.large)
            .background(colorResource(R.color.icon_bg)), contentAlignment = Alignment.Center) {
            Icon(painterResource(R.drawable.ic_mark), contentDescription = null,
                tint = Color.Unspecified, modifier = Modifier.requiredSize(if (isTv) 172.dp else 156.dp))
        }
        Spacer(Modifier.height(12.dp))
        Column(Modifier.width(IntrinsicSize.Min), horizontalAlignment = Alignment.CenterHorizontally) {
            PerforationStrip(holeColor = MaterialTheme.colorScheme.background, modifier = Modifier.fillMaxWidth().height(3.dp))
            Text(stringResource(R.string.app_name).uppercase(),
                modifier = Modifier.padding(vertical = 8.dp), fontFamily = FontFamily.Serif,
                fontWeight = FontWeight.Medium, fontSize = if (isTv) 26.sp else 24.sp,
                letterSpacing = 0.14.em, textAlign = TextAlign.Center)
            PerforationStrip(holeColor = MaterialTheme.colorScheme.background, modifier = Modifier.fillMaxWidth().height(3.dp))
        }
        Text(stringResource(R.string.settings_about_home_cinema),
            modifier = Modifier.padding(top = 14.dp), style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
    }
}

@Composable
internal fun AboutLibraries() {
    val libraries = remember {
        listOf(
            "Jetpack Compose · Room · WorkManager" to "https://github.com/androidx/androidx/blob/androidx-main/LICENSE.txt",
            "Media3 (ExoPlayer)" to "https://github.com/androidx/media/blob/release/LICENSE",
            "Coil" to "https://github.com/coil-kt/coil/blob/main/LICENSE.txt",
            "smbj" to "https://github.com/hierynomus/smbj/blob/master/LICENSE_HEADER",
            "OkHttp" to "https://github.com/square/okhttp/blob/master/LICENSE.txt",
            "kotlinx.serialization" to "https://github.com/Kotlin/kotlinx.serialization/blob/master/LICENSE.txt"
        )
    }
    SettingsGroup {
        libraries.forEachIndexed { index, (name, url) ->
            if (index > 0) SettingsDivider()
            AboutLinkRow(name, stringResource(R.string.settings_about_library_license), url = url)
        }
    }
}

@Composable
internal fun AboutUpdateStatus(message: String, darkTheme: Boolean) {
    val success = message.startsWith("У вас последняя версия!")
    val foreground = if (success) {
        if (darkTheme) Color(0xFFA5D6A7) else Color(0xFF1B5E20)
    } else MaterialTheme.colorScheme.onSurfaceVariant
    Text(message, style = MaterialTheme.typography.bodyMedium, color = foreground,
        modifier = Modifier.padding(top = 12.dp).fillMaxWidth()
            .background(if (success) foreground.copy(alpha = 0.12f) else MaterialTheme.colorScheme.surfaceContainerHigh,
                MaterialTheme.shapes.small).padding(12.dp))
}

@Composable
internal fun AboutLinkRow(title: String, subtitle: String, url: String? = null, onClick: (() -> Unit)? = null) {
    val source = remember { MutableInteractionSource() }
    val context = LocalContext.current
    val unavailable = stringResource(R.string.settings_about_link_unavailable)
    ListItem(
        headlineContent = { Text(title) },
        supportingContent = { Text(subtitle) },
        trailingContent = {
            Icon(if (url != null) Icons.AutoMirrored.Filled.OpenInNew else Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = if (url != null) stringResource(R.string.settings_about_external_link) else null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant)
        },
        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
        modifier = Modifier.fillMaxWidth().focusHighlight(source)
            .clickable(interactionSource = source, indication = LocalIndication.current) {
                if (url == null) onClick?.invoke()
                else runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, url.toUri())) }
                    .onFailure { Toast.makeText(context, unavailable, Toast.LENGTH_SHORT).show() }
            }
    )
}
