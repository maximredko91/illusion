package com.illusion.app.ui.common

import android.animation.ValueAnimator
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.illusion.app.IllusionApplication
import com.illusion.app.R
import com.illusion.app.data.settings.DevicePerformance
import com.illusion.app.domain.model.PerformanceMode
import com.illusion.app.domain.model.UiMode
import kotlinx.coroutines.flow.first

/** One clock on the Compose side; the native splash's mark stays at the same size and position. */
@Composable
fun AppSplashOverlay(app: IllusionApplication) {
    var finished by rememberSaveable { mutableStateOf(false) }
    var isTv by remember { mutableStateOf(false) }
    var animate by remember { mutableStateOf(false) }
    val progress = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        if (finished) return@LaunchedEffect
        isTv = app.settingsRepository.uiMode.first() == UiMode.TV
        val performance = app.settingsRepository.performanceMode.first()
        val economical = performance == PerformanceMode.ECONOMICAL ||
            (performance == PerformanceMode.AUTO && DevicePerformance.isLowEndDevice(app))
        animate = !economical && ValueAnimator.areAnimatorsEnabled()
        if (animate) progress.animateTo(1f, tween(560, easing = LinearEasing))
        finished = true
    }
    if (finished) return

    val splashBackground = colorResource(R.color.splash_bg)
    val destinationBackground = MaterialTheme.colorScheme.background
    val ink = colorResource(R.color.illusion_ink_on_bg)
    val exit = ((progress.value - 0.66f) / 0.34f).coerceIn(0f, 1f)
    val background = lerp(splashBackground, destinationBackground, exit)
    BoxWithConstraints(
        Modifier.fillMaxSize().graphicsLayer { alpha = 1f - exit }.background(background),
        contentAlignment = Alignment.Center
    ) {
        // Keep the native 288dp canvas even on ATV: enlarging it at handoff causes a jump.
        Image(
            painterResource(R.drawable.ic_mark_splash), contentDescription = null,
            modifier = Modifier.size(288.dp)
                .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
                .drawWithContent {
                    drawContent()
                    if (animate) {
                        val sweep = (progress.value / 0.72f).coerceIn(0f, 1f)
                        val x = size.width * (-0.5f + 2f * sweep)
                        drawRect(
                            brush = Brush.linearGradient(
                                listOf(Color.Transparent, Color.White.copy(alpha = 0.28f), Color.Transparent),
                                start = Offset(x - size.width * 0.22f, 0f),
                                end = Offset(x + size.width * 0.22f, size.height * 0.25f)
                            ),
                            blendMode = BlendMode.SrcAtop
                        )
                    }
                }
        )
        // The visible mark is ~114dp tall inside its transparent canvas. Fit short landscape too.
        val titleOffset = minOf(if (isTv) 118.dp else 108.dp, maxHeight / 2 - 42.dp)
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.offset(y = titleOffset).width(IntrinsicSize.Min)
                .graphicsLayer { alpha = (progress.value / 0.3f).coerceIn(0f, 1f) }
        ) {
            PerforationStrip(holeColor = background, modifier = Modifier.fillMaxWidth().height(3.dp))
            Text(
                stringResource(R.string.app_name).uppercase(),
                modifier = Modifier.padding(vertical = 8.dp),
                color = ink, fontFamily = FontFamily.Serif, fontWeight = FontWeight.Medium,
                fontSize = if (isTv) 24.sp else 20.sp, letterSpacing = 0.18.em,
                maxLines = 1
            )
            PerforationStrip(holeColor = background, modifier = Modifier.fillMaxWidth().height(3.dp))
        }
    }
}
