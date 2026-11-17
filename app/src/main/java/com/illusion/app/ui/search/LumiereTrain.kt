package com.illusion.app.ui.search

import android.animation.ValueAnimator
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.illusion.app.R
import com.illusion.app.ui.common.LocalEconomicalMode
import kotlinx.coroutines.delay

/**
 * Easter egg: searching for the Lumières (or their train) sends a locomotive across the search
 * screen, with a caption naming the 1895 film audiences are said to have fled from. Deliberately
 * undocumented outside this file and CLAUDE.md.
 *
 * Drawn over the screen but takes no pointer input, so the search field and results underneath
 * stay fully usable while it passes. The train is drawn here ([drawTrain]) rather than typed as
 * emoji, whose look depends on the phone's font; it runs right to left, as it does in the film's
 * frame. With animations off (economical mode, or the system setting) it simply stands in the
 * middle for a moment instead.
 */
@Composable
internal fun BoxScope.LumiereTrain(query: String) {
    val summoned = remember(query) { isLumiereQuery(query) }
    val economical = LocalEconomicalMode.current
    val progress = remember { Animatable(0f) }
    var running by remember { mutableStateOf(false) }
    var moving by remember { mutableStateOf(false) }
    LaunchedEffect(summoned) {
        if (!summoned) return@LaunchedEffect
        moving = !economical && ValueAnimator.areAnimatorsEnabled()
        progress.snapTo(if (moving) 0f else 0.5f)
        running = true
        if (moving) progress.animateTo(1f, tween(TRAVEL_MS, easing = LinearEasing)) else delay(STANDING_MS)
        running = false
    }
    if (!running) return

    BoxWithConstraints(Modifier.matchParentSize()) {
        val width = constraints.maxWidth.toFloat()
        val trainWidth = with(LocalDensity.current) { TRAIN_WIDTH.dp.toPx() }
        val body = MaterialTheme.colorScheme.onSurface
        val accent = MaterialTheme.colorScheme.primary
        // From just past the right edge to just past the left one. At 0.5 (standing still) that
        // puts the train in the middle of the screen.
        val travel = width + trainWidth
        Canvas(
            Modifier
                .align(Alignment.CenterStart)
                .offset { IntOffset((width - progress.value * travel).toInt(), 0) }
                .size(TRAIN_WIDTH.dp, TRAIN_HEIGHT.dp)
        ) {
            drawTrain(
                body = body,
                accent = accent,
                rolledPx = progress.value * travel,
                seconds = if (moving) progress.value * TRAVEL_MS / 1000f else 0f
            )
        }
        Text(
            stringResource(R.string.search_lumiere_caption),
            color = Color.White,
            fontFamily = FontFamily.Serif,
            fontStyle = FontStyle.Italic,
            fontSize = 14.sp,
            textAlign = TextAlign.Center,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(horizontal = 24.dp, vertical = 32.dp)
                .graphicsLayer {
                    // In over the first tenth of the run, out over the last.
                    alpha = if (moving) (minOf(progress.value, 1f - progress.value) / 0.1f).coerceIn(0f, 1f) else 1f
                }
                .background(Color.Black.copy(alpha = 0.78f), RoundedCornerShape(8.dp))
                .padding(horizontal = 16.dp, vertical = 10.dp)
        )
    }
}

/**
 * A steam locomotive facing left with three carriages, as a flat silhouette. Coordinates are in
 * dp on a [TRAIN_WIDTH] x [TRAIN_HEIGHT] box. [rolledPx] is how far the train has travelled - it
 * turns the wheels and the coupling rod; [seconds] drives the smoke.
 */
private fun DrawScope.drawTrain(body: Color, accent: Color, rolledPx: Float, seconds: Float) {
    val u = size.height / TRAIN_HEIGHT

    fun box(left: Float, top: Float, right: Float, bottom: Float, color: Color = body, radius: Float = 0f) {
        drawRoundRect(color, Offset(left * u, top * u), Size((right - left) * u, (bottom - top) * u), CornerRadius(radius * u))
    }

    // Rolling to the left turns a wheel anticlockwise.
    fun angleOf(radius: Float) = -rolledPx / (radius * u)

    fun wheel(cx: Float, cy: Float, radius: Float) {
        val center = Offset(cx * u, cy * u)
        drawCircle(body, radius * u, center)
        val angle = angleOf(radius)
        for (spoke in 0 until 3) {
            val a = angle + spoke * (Math.PI / 3).toFloat()
            val reach = Offset(kotlin.math.cos(a), kotlin.math.sin(a)) * (radius * 0.78f * u)
            drawLine(accent, center - reach, center + reach, strokeWidth = 1.3f * u)
        }
        drawCircle(accent, radius * 0.24f * u, center)
    }

    // Smoke: puffs leave the chimney, grow, thin out and fall behind (to the right).
    for (puff in 0 until 5) {
        val phase = (seconds * 0.9f + puff / 5f) % 1f
        drawCircle(
            body.copy(alpha = (1f - phase) * 0.4f),
            radius = (4f + phase * 9f) * u,
            center = Offset((23f + phase * 58f) * u, (6f - phase * 16f) * u)
        )
    }

    // Locomotive: cowcatcher, boiler, chimney, dome, cab.
    drawPath(
        Path().apply {
            moveTo(0f, 66f * u); lineTo(13f * u, 48f * u); lineTo(13f * u, 66f * u); close()
        },
        body
    )
    box(12f, 28f, 72f, 50f, radius = 9f)
    box(18f, 13f, 28f, 30f)
    box(15f, 9f, 31f, 14f, radius = 2f)
    drawCircle(body, 6.5f * u, Offset(47f * u, 28f * u))
    box(68f, 14f, 100f, 54f, radius = 2f)
    box(64f, 10f, 104f, 15f, radius = 2f)
    box(76f, 20f, 92f, 33f, color = accent, radius = 2f)
    box(12f, 49f, 100f, 55f)
    wheel(24f, 62f, 6f)
    wheel(40f, 62f, 6f)
    wheel(62f, 57f, 11f)
    wheel(87f, 57f, 11f)
    // Coupling rod between the two driving wheels, pinned off-centre so it goes round with them.
    val rodAngle = angleOf(11f)
    val pin = Offset(kotlin.math.cos(rodAngle), kotlin.math.sin(rodAngle)) * (6f * u)
    drawLine(accent, Offset(62f * u, 57f * u) + pin, Offset(87f * u, 57f * u) + pin, strokeWidth = 2.2f * u)

    // Carriages.
    for (index in 0 until CARRIAGES) {
        val x = LOCOMOTIVE_LENGTH + index * CARRIAGE_PITCH
        box(x - 8f, 48f, x, 51f)
        box(x, 22f, x + 64f, 55f, radius = 3f)
        box(x - 2f, 18f, x + 66f, 23f, radius = 2f)
        for (window in 0 until 4) {
            val wx = x + 6f + window * 14f
            box(wx, 28f, wx + 10f, 39f, color = accent, radius = 1.5f)
        }
        wheel(x + 14f, 62f, 6f)
        wheel(x + 50f, 62f, 6f)
    }
}

private const val TRAIN_HEIGHT = 72f
private const val LOCOMOTIVE_LENGTH = 112f
private const val CARRIAGE_PITCH = 72f
private const val CARRIAGES = 3
private const val TRAIN_WIDTH = LOCOMOTIVE_LENGTH + CARRIAGES * CARRIAGE_PITCH - 8f

/** Whether [query] is one of the spellings that summon the train - whole query, not a substring. */
internal fun isLumiereQuery(query: String): Boolean =
    query.trim().lowercase().replace('ё', 'е').replace('è', 'e') in LUMIERE_QUERIES

private val LUMIERE_QUERIES = setOf(
    "люмьер", "люмьеры", "братья люмьер", "прибытие поезда", "lumiere", "lumiere brothers"
)

private const val TRAVEL_MS = 4500
private const val STANDING_MS = 2500L
