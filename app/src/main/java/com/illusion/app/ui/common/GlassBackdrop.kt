package com.illusion.app.ui.common

import android.graphics.RenderEffect
import android.graphics.RuntimeShader
import android.graphics.Shader
import android.os.Build
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.asComposeRenderEffect
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.layer.GraphicsLayer
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalGraphicsContext

/**
 * Backdrop-blur/"glass" effect for a small floating element (e.g. the library's scroll-to-top
 * FAB) sitting over ordinary Compose content (NOT a video surface - see this file's own
 * limitation note below).
 *
 * Real per-pixel refraction (the actual "Liquid Glass" look, not just blur) needs
 * `android.graphics.RuntimeShader`, only available API 33+ (see project memory
 * "Liquid Glass API requirements") - this hand-rolls a small AGSL shader rather than depending on
 * a third-party library, since the two candidates checked (Material 3 Expressive, chrisbanes/haze
 * `haze-glass`) both only ship real refraction in unstable alpha/beta releases as of this session.
 * Degrades gracefully: plain `RenderEffect` blur on API 31-32, no visual effect at all below 31
 * (just whatever solid/semi-transparent background the caller already draws).
 *
 * IMPORTANT LIMITATION: this can only ever "see" ordinary Compose-drawn content. A `SurfaceView`
 * (this app's own video player - see PlayerView/SmbDataSource) is composited by SurfaceFlinger
 * directly to the display, entirely outside the app's own View/Compose render tree - no
 * Compose-level blur/RenderEffect/GraphicsLayer technique, this one included, can capture or blur
 * what a SurfaceView draws. Do not reuse this over the player's own video surface.
 *
 * NOT YET VERIFIED ON A REAL DEVICE - the AGSL shader source below is hand-written against AGSL's
 * documented syntax (a GLSL ES subset with `uniform shader`/`.eval()` for image inputs), not
 * checked against real on-device rendering. A RuntimeShader with a syntax error throws at the
 * point its uniforms are set, not at compile time - if the glass FAB doesn't render correctly
 * (or crashes) on-device, that shader is the first place to look.
 */

/** Off by default (SettingsRepository.glassEffectEnabled) - set at the composition root in IllusionNavHost, same pattern as LocalEconomicalMode. Callers using [glassSurface] should check this first and fall back to a plain Modifier when off, rather than paying for the capture/blur/shader work for an effect nobody asked to see. */
val LocalGlassEffectEnabled = compositionLocalOf { false }

/** Shared capture target - create one per screen/section that wants a glass element floating over it, via [rememberGlassBackdropState]. */
class GlassBackdropState internal constructor(internal val layer: GraphicsLayer) {
    internal var sourcePositionInRoot by mutableStateOf(Offset.Zero)
}

@Composable
fun rememberGlassBackdropState(): GlassBackdropState {
    val graphicsContext = LocalGraphicsContext.current
    val layer = remember { graphicsContext.createGraphicsLayer() }
    DisposableEffect(layer) {
        onDispose { graphicsContext.releaseGraphicsLayer(layer) }
    }
    return remember(layer) { GlassBackdropState(layer) }
}

/** Apply to the scrollable/background content a [glassSurface] elsewhere should show a frosted view of - draws it normally (unaffected) and additionally records the same pixels for later replay. */
fun Modifier.glassBackdropSource(state: GlassBackdropState): Modifier =
    this
        .onGloballyPositioned { coordinates -> state.sourcePositionInRoot = coordinates.positionInRoot() }
        .drawWithContent {
            state.layer.record { this@drawWithContent.drawContent() }
            drawContent()
        }

/**
 * Apply to the small floating element itself (a FAB, a chip, ...). Shows a blurred/refracted
 * replay of whatever [state]'s source content sits directly behind this element's own bounds,
 * clipped to [shape] - the caller still draws its own content (icon, etc.) on top as normal,
 * this only affects the background. No-ops below API 31 (returns [this] unchanged) - the
 * caller's own flat/semi-transparent background shows through as-is on those devices.
 */
fun Modifier.glassSurface(
    state: GlassBackdropState,
    shape: Shape = CircleShape,
    blurRadiusPx: Float = 36f,
    refractionStrength: Float = 0.35f
): Modifier = composed {
    if (Build.VERSION.SDK_INT < 31) return@composed this@glassSurface

    // A RenderEffect set on a GraphicsLayer runs in THAT layer's own coordinate space. state.layer
    // is the shared, screen-sized backdrop capture - setting the (FAB-sized) blur/refraction effect
    // directly on it would evaluate the shader's "center"/"size" math against screen-sized
    // coordinates using FAB-sized uniforms, producing nonsense far from the layer's origin. So this
    // element gets its own small layer: record just the translated backdrop slice into it (sized to
    // this element's own bounds), THEN apply the correctly-scoped effect to that.
    val graphicsContext = LocalGraphicsContext.current
    val localLayer = remember { graphicsContext.createGraphicsLayer() }
    DisposableEffect(localLayer) {
        onDispose { graphicsContext.releaseGraphicsLayer(localLayer) }
    }

    var myPositionInRoot by remember { mutableStateOf(Offset.Zero) }
    this@glassSurface
        .clip(shape)
        .onGloballyPositioned { coordinates ->
            myPositionInRoot = coordinates.positionInRoot()
            val size = coordinates.size
            if (size.width > 0 && size.height > 0) {
                localLayer.renderEffect = buildGlassRenderEffect(
                    size.width.toFloat(),
                    size.height.toFloat(),
                    blurRadiusPx,
                    refractionStrength
                )
            }
        }
        .drawWithContent {
            val offset = state.sourcePositionInRoot - myPositionInRoot
            localLayer.record {
                translate(offset.x, offset.y) {
                    drawLayer(state.layer)
                }
            }
            drawLayer(localLayer)
            drawContent()
        }
}

@androidx.annotation.RequiresApi(31)
private fun buildGlassRenderEffect(
    widthPx: Float,
    heightPx: Float,
    blurRadiusPx: Float,
    refractionStrength: Float
): androidx.compose.ui.graphics.RenderEffect {
    val blur = RenderEffect.createBlurEffect(blurRadiusPx, blurRadiusPx, Shader.TileMode.CLAMP)
    if (Build.VERSION.SDK_INT < 33) return blur.asComposeRenderEffect()
    val shader = RuntimeShader(GLASS_REFRACTION_AGSL)
    shader.setFloatUniform("size", widthPx, heightPx)
    shader.setFloatUniform("refractionStrength", refractionStrength)
    val refraction = RenderEffect.createRuntimeShaderEffect(shader, "content")
    // createChainEffect(outer, inner) runs inner FIRST, then outer on its result - refraction must
    // be inner (bending the still-sharp backdrop) with blur outer (softening afterwards), not the
    // reverse. Chaining it the other way around (blur first) was the original bug here: by the time
    // refraction ran, blur had already erased the edges/text it was supposed to visibly bend, so the
    // whole effect read as "just blur" with a barely-visible warp.
    return RenderEffect.createChainEffect(blur, refraction).asComposeRenderEffect()
}

/**
 * Fake convex-lens bulge: pulls sampled pixels toward the surface's own center, strongest right
 * at the rim and ~0 at dead center - a cheap approximation of light bending through a curved
 * glass surface (no real depth/normal map, unlike a "proper" liquid-glass shader). Plus a soft
 * rim highlight (fake specular) near the edge.
 */
private const val GLASS_REFRACTION_AGSL = """
uniform shader content;
uniform float2 size;
uniform float refractionStrength;

half4 main(float2 coord) {
    float2 center = size * 0.5;
    float2 fromCenter = coord - center;
    float2 halfSize = size * 0.5;
    float2 normalized = fromCenter / halfSize;
    float dist = length(normalized);
    float bend = smoothstep(0.0, 1.0, dist) * refractionStrength;
    float2 dir = dist > 0.001 ? normalize(fromCenter) : float2(0.0, 0.0);
    float2 offset = -dir * bend * size.x * 0.11;
    half4 color = content.eval(coord + offset);
    float rim = smoothstep(0.7, 1.0, dist) * 0.22;
    return half4(color.rgb + rim, color.a);
}
"""
