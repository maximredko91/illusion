package com.illusion.app.ui.settings

import android.animation.ValueAnimator
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.illusion.app.R
import com.illusion.app.domain.model.UiMode
import com.illusion.app.ui.common.LocalEconomicalMode
import com.illusion.app.ui.common.LocalUiMode
import com.illusion.app.ui.common.PerforationStrip
import com.illusion.app.ui.common.TvAwareButton

/**
 * A souvenir cinema ticket; closing it simply returns to About.
 *
 * The seat on it is the viewer's own: the row is how many films they have watched to the end, the
 * seat how many episodes. Until there's anything to count it stays the original "row 7, seat 7".
 * After [GOLDEN_TICKET_FILMS] films the ticket turns gold.
 */
@Composable
internal fun SecretCinemaDialog(onDismiss: () -> Unit) {
    val isTv = LocalUiMode.current == UiMode.TV
    val economical = LocalEconomicalMode.current
    val motionEnabled = remember(economical) { !economical && ValueAnimator.areAnimatorsEnabled() }
    val reveal = remember { Animatable(if (motionEnabled) 0f else 1f) }
    val buttonFocus = remember { FocusRequester() }
    val app = androidx.compose.ui.platform.LocalContext.current.applicationContext as com.illusion.app.IllusionApplication
    // films to episodes; null until counted, so the ticket never flashes one seat and then another
    val watched = androidx.compose.runtime.produceState<Pair<Int, Int>?>(null) {
        value = app.watchProgressRepository.countWatchedFilms() to app.watchProgressRepository.countWatchedEpisodes()
    }.value
    val films = watched?.first ?: 0
    val episodes = watched?.second ?: 0
    val golden = films >= GOLDEN_TICKET_FILMS
    val paper = if (golden) Color(0xFFEFD58A) else Color(0xFFF4EDE4)
    val ink = Color(0xFF241B18)
    val crimson = Color(0xFFA13F3F)
    Dialog(onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)) {
        LaunchedEffect(Unit) {
            if (isTv) {
                withFrameNanos { }
                buttonFocus.requestFocus()
            }
            if (motionEnabled) reveal.animateTo(1f, tween(260))
        }
        Box(Modifier.fillMaxSize().safeDrawingPadding().padding(24.dp), contentAlignment = Alignment.Center) {
            Column(
                Modifier.widthIn(max = if (isTv) 520.dp else 380.dp).fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .graphicsLayer {
                        alpha = reveal.value
                        scaleX = 0.96f + 0.04f * reveal.value
                        scaleY = scaleX
                    },
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Surface(color = paper, contentColor = ink, shape = RoundedCornerShape(24.dp)) {
                    Column(Modifier.fillMaxWidth().padding(vertical = 24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        PerforationStrip(holeColor = paper, modifier = Modifier.fillMaxWidth().height(8.dp))
                        Column(Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 24.dp),
                            horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(stringResource(R.string.secret_cinema_title),
                                style = MaterialTheme.typography.labelLarge, color = crimson, textAlign = TextAlign.Center)
                            Spacer(Modifier.height(16.dp))
                            Text(stringResource(R.string.app_name).uppercase(), fontFamily = FontFamily.Serif,
                                fontSize = if (isTv) 30.sp else 26.sp, letterSpacing = 0.1.em,
                                fontWeight = FontWeight.Medium, textAlign = TextAlign.Center)
                            Text(stringResource(if (golden) R.string.secret_cinema_regular else R.string.secret_cinema_screening),
                                modifier = Modifier.padding(top = 8.dp), style = MaterialTheme.typography.labelLarge,
                                letterSpacing = 0.12.em, color = crimson, textAlign = TextAlign.Center)
                        }
                        Canvas(Modifier.fillMaxWidth().height(16.dp)) {
                            drawLine(crimson.copy(alpha = 0.5f), Offset(24.dp.toPx(), size.height / 2),
                                Offset(size.width - 24.dp.toPx(), size.height / 2), 1.dp.toPx(),
                                pathEffect = PathEffect.dashPathEffect(floatArrayOf(4.dp.toPx(), 4.dp.toPx())))
                        }
                        val counted = films > 0 || episodes > 0
                        Text(
                            if (counted) {
                                // A seat number of zero doesn't exist in any cinema.
                                stringResource(R.string.secret_cinema_seat_format, films.coerceAtLeast(1), episodes.coerceAtLeast(1))
                            } else {
                                stringResource(R.string.secret_cinema_seat)
                            },
                            modifier = Modifier.padding(start = 24.dp, end = 24.dp, top = 20.dp, bottom = if (counted) 4.dp else 20.dp),
                            style = MaterialTheme.typography.headlineSmall, fontFamily = FontFamily.Serif,
                            textAlign = TextAlign.Center)
                        if (counted) {
                            Text(stringResource(R.string.secret_cinema_seat_hint),
                                modifier = Modifier.padding(start = 24.dp, end = 24.dp, bottom = 16.dp),
                                style = MaterialTheme.typography.bodySmall, color = ink.copy(alpha = 0.6f),
                                textAlign = TextAlign.Center)
                        }
                        Text(stringResource(if (golden) R.string.secret_cinema_message_golden else R.string.secret_cinema_message),
                            modifier = Modifier.padding(start = 24.dp, end = 24.dp, bottom = 24.dp),
                            style = MaterialTheme.typography.bodyLarge, textAlign = TextAlign.Center)
                        PerforationStrip(holeColor = paper, modifier = Modifier.fillMaxWidth().height(8.dp))
                    }
                }
                Spacer(Modifier.height(20.dp))
                TvAwareButton(onClick = onDismiss, modifier = Modifier.fillMaxWidth().focusRequester(buttonFocus)) {
                    Text(stringResource(R.string.secret_cinema_enter))
                }
            }
        }
    }
}

private const val GOLDEN_TICKET_FILMS = 100
