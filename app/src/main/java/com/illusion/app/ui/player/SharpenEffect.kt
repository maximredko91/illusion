package com.illusion.app.ui.player

import android.content.Context
import android.opengl.GLES20
import androidx.media3.common.util.GlProgram
import androidx.media3.common.util.GlUtil
import androidx.media3.common.util.Size
import androidx.media3.common.util.UnstableApi
import androidx.media3.effect.BaseGlShaderProgram
import androidx.media3.effect.GlEffect
import androidx.media3.effect.GlShaderProgram

/**
 * Real-time sharpening (a high-pass kernel added back onto the frame, i.e. an unsharp mask)
 * applied via Media3's GL effects pipeline. Meant for old, soft/heavily compressed rips - off
 * by default, toggled from the player's settings sheet.
 */
/**
 * [amountProvider] is read fresh on every drawn frame rather than captured once. Changing the
 * sharpen strength used to mean handing ExoPlayer a whole new effect via setVideoEffects() on a
 * live player - which rebuilds the GL effects pipeline underneath a playing video and reliably
 * froze playback on-device (playback state stayed PLAYING while the position stopped advancing,
 * plus "noteStopVideo ... refcount 0" in logcat). With a live provider the strength is just a
 * uniform that changes on the next frame: no pipeline rebuild, no freeze.
 */
@UnstableApi
class SharpenEffect(private val amountProvider: () -> Float) : GlEffect {
    constructor(amount: Float) : this({ amount })

    override fun toGlShaderProgram(context: Context, useHdr: Boolean): GlShaderProgram =
        SharpenShaderProgram(useHdr, amountProvider)
}

@UnstableApi
private class SharpenShaderProgram(
    useHdr: Boolean,
    private val amountProvider: () -> Float
) : BaseGlShaderProgram(useHdr, /* texturePoolCapacity= */ 1) {

    private val glProgram = GlProgram(VERTEX_SHADER, FRAGMENT_SHADER).apply {
        setBufferAttribute(
            "aFramePosition",
            GlUtil.getNormalizedCoordinateBounds(),
            GlUtil.HOMOGENEOUS_COORDINATE_VECTOR_SIZE
        )
        val identity = GlUtil.create4x4IdentityMatrix()
        setFloatsUniform("uTransformationMatrix", identity)
        setFloatsUniform("uTexTransformationMatrix", identity)
    }

    private var outputWidth = 1
    private var outputHeight = 1

    override fun configure(inputWidth: Int, inputHeight: Int): Size {
        outputWidth = inputWidth.coerceAtLeast(1)
        outputHeight = inputHeight.coerceAtLeast(1)
        return Size(inputWidth, inputHeight)
    }

    override fun drawFrame(inputTexId: Int, presentationTimeUs: Long) {
        glProgram.use()
        glProgram.setSamplerTexIdUniform("uTexSampler", inputTexId, /* texUnitIndex= */ 0)
        glProgram.setFloatsUniform("uTexelSize", floatArrayOf(1f / outputWidth, 1f / outputHeight))
        glProgram.setFloatUniform("uSharpenAmount", amountProvider())
        glProgram.bindAttributesAndUniforms()
        GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, 4)
        GlUtil.checkGlError()
    }

    override fun release() {
        super.release()
        glProgram.delete()
    }

    companion object {
        private const val VERTEX_SHADER = """
            attribute vec4 aFramePosition;
            uniform mat4 uTransformationMatrix;
            uniform mat4 uTexTransformationMatrix;
            varying vec2 vTexSamplingCoord;
            void main() {
              gl_Position = uTransformationMatrix * aFramePosition;
              vec4 texturePosition = vec4(aFramePosition.x * 0.5 + 0.5, aFramePosition.y * 0.5 + 0.5, 0.0, 1.0);
              vTexSamplingCoord = (uTexTransformationMatrix * texturePosition).xy;
            }
        """

        private const val FRAGMENT_SHADER = """
            precision mediump float;
            uniform sampler2D uTexSampler;
            uniform vec2 uTexelSize;
            uniform float uSharpenAmount;
            varying vec2 vTexSamplingCoord;
            void main() {
              vec3 center = texture2D(uTexSampler, vTexSamplingCoord).rgb;
              vec3 sum = vec3(0.0);
              sum += texture2D(uTexSampler, vTexSamplingCoord + vec2(uTexelSize.x, 0.0)).rgb;
              sum += texture2D(uTexSampler, vTexSamplingCoord - vec2(uTexelSize.x, 0.0)).rgb;
              sum += texture2D(uTexSampler, vTexSamplingCoord + vec2(0.0, uTexelSize.y)).rgb;
              sum += texture2D(uTexSampler, vTexSamplingCoord - vec2(0.0, uTexelSize.y)).rgb;
              vec3 sharpened = center + uSharpenAmount * (center * 4.0 - sum);
              gl_FragColor = vec4(clamp(sharpened, 0.0, 1.0), 1.0);
            }
        """
    }
}
