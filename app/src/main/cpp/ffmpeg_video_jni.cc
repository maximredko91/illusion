// JNI bridge between FfmpegVideoDecoder (Media3 SimpleDecoder) and libavcodec.
//
// Structure follows Media3's own decoder_vp9 extension (vpx_jni.cc, 1.11.0): the decoder thread
// calls nativeDecode/nativeGetFrame, the playback thread calls nativeRenderFrame to copy a decoded
// frame into the output Surface as YV12. Unlike libvpx, libavcodec reference-counts its frames, so
// instead of vpx's hand-rolled buffer pool each handed-out frame is simply an av_frame_clone() whose
// pointer rides along in VideoDecoderOutputBuffer.decoderPrivate until nativeReleaseFrame frees it.

#include <android/log.h>
#include <android/native_window.h>
#include <android/native_window_jni.h>
#include <jni.h>

#include <atomic>
#include <cstdlib>
#include <cstring>

extern "C" {
#include <libavcodec/avcodec.h>
#include <libavutil/avutil.h>
#include <libavutil/error.h>
#include <libswscale/swscale.h>
}

#define LOG_TAG "ffmpeg_video_jni"
#define LOGE(...) ((void)__android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__))
#define LOGW(...) ((void)__android_log_print(ANDROID_LOG_WARN, LOG_TAG, __VA_ARGS__))

// Must match FfmpegVideoDecoder's constants.
static const int kFrameReady = 0;
static const int kNoFrame = 1;
static const int kError = -1;

// Must match androidx.media3.common.C.VIDEO_OUTPUT_MODE_* and VideoDecoderOutputBuffer.COLORSPACE_*.
static const int kOutputModeYuv = 0;
static const int kOutputModeSurfaceYuv = 1;
static const int kColorspaceBt601 = 1;

static const int kImageFormatYv12 = 0x32315659;

static jmethodID initForYuvFrameMethod;
static jmethodID initForPrivateFrameMethod;
static jfieldID dataField;
static jfieldID decoderPrivateField;

namespace {

struct Context {
    AVCodecContext* codec = nullptr;
    AVPacket* packet = nullptr;
    // The most recently received frame, owned here until nativeGetFrame hands it over. Dropped at
    // the start of the next nativeDecode if nobody took it (e.g. a frame before the seek target).
    AVFrame* frame = nullptr;
    bool hasFrame = false;
    // libavcodec reads up to AV_INPUT_BUFFER_PADDING_SIZE bytes past the end of a packet, which a
    // Media3 input ByteBuffer doesn't guarantee to have - every packet is copied into this padded
    // scratch buffer first.
    uint8_t* input = nullptr;
    size_t inputCapacity = 0;
    SwsContext* sws = nullptr;
    // Touched by the playback thread (nativeRenderFrame) only.
    ANativeWindow* window = nullptr;
    jobject surface = nullptr;  // Global ref.
    int windowWidth = 0;
    int windowHeight = 0;
    // Written from the player's own thread (nativeSetSharpen), read by the playback thread on
    // every rendered frame. 0 means "copy the luma plane untouched", i.e. the pre-sharpen path.
    std::atomic<float> sharpen{0.0f};
};

bool ensureInputCapacity(Context* ctx, size_t size) {
    size_t required = size + AV_INPUT_BUFFER_PADDING_SIZE;
    if (ctx->inputCapacity >= required) return true;
    uint8_t* grown = static_cast<uint8_t*>(realloc(ctx->input, required));
    if (!grown) return false;
    ctx->input = grown;
    ctx->inputCapacity = required;
    return true;
}

void logAvError(const char* what, int error) {
    char message[AV_ERROR_MAX_STRING_SIZE];
    av_strerror(error, message, sizeof(message));
    LOGW("%s: %s", what, message);
}

// The Surface path copies YUV 4:2:0 planes straight into a YV12 window buffer. Every decoder in
// the build normally outputs YUV420P; anything else (4:2:2 MPEG-2 profiles, full-range variants)
// is converted once here so the render path only ever sees one layout.
AVFrame* toYuv420p(Context* ctx, AVFrame* src) {
    if (src->format == AV_PIX_FMT_YUV420P || src->format == AV_PIX_FMT_YUVJ420P) {
        return av_frame_clone(src);
    }
    ctx->sws = sws_getCachedContext(ctx->sws, src->width, src->height,
                                    static_cast<AVPixelFormat>(src->format), src->width,
                                    src->height, AV_PIX_FMT_YUV420P, SWS_BILINEAR, nullptr,
                                    nullptr, nullptr);
    if (!ctx->sws) {
        LOGE("No swscale conversion from pixel format %d", src->format);
        return nullptr;
    }
    AVFrame* dst = av_frame_alloc();
    if (!dst) return nullptr;
    dst->format = AV_PIX_FMT_YUV420P;
    dst->width = src->width;
    dst->height = src->height;
    if (av_frame_get_buffer(dst, 32) < 0) {
        av_frame_free(&dst);
        return nullptr;
    }
    sws_scale(ctx->sws, const_cast<const uint8_t* const*>(src->data), src->linesize, 0,
              src->height, dst->data, dst->linesize);
    dst->pts = src->pts;
    dst->best_effort_timestamp = src->best_effort_timestamp;
    return dst;
}

void copyPlane(uint8_t* dst, int dstStride, const uint8_t* src, int srcStride, int width,
               int height) {
    for (int y = 0; y < height; y++) {
        memcpy(dst, src, width);
        dst += dstStride;
        src += srcStride;
    }
}

// Media3's GL effects (and with them SharpenEffect) never see a frame this renderer decodes - they
// only run on MediaCodecVideoRenderer's VideoSink pipeline. Sharpening therefore happens here, on
// the luma plane, while the frame is copied into the window buffer: same unsharp-mask kernel as
// SharpenEffect's fragment shader (out = c + amount * (4c - left - right - up - down)), just in
// fixed-point 8.8 integers and on Y only - chroma carries no detail worth sharpening at 4:2:0.
// The outermost columns are copied through unchanged, rows clamp to themselves at the edges.
void sharpenPlane(uint8_t* dst, int dstStride, const uint8_t* src, int srcStride, int width,
                  int height, float amount) {
    const int scaled = static_cast<int>(amount * 256.0f + 0.5f);
    for (int y = 0; y < height; y++) {
        const uint8_t* row = src + static_cast<size_t>(y) * srcStride;
        const uint8_t* above = y > 0 ? row - srcStride : row;
        const uint8_t* below = y + 1 < height ? row + srcStride : row;
        uint8_t* out = dst + static_cast<size_t>(y) * dstStride;
        if (width <= 2) {
            memcpy(out, row, width);
            continue;
        }
        out[0] = row[0];
        for (int x = 1; x + 1 < width; x++) {
            const int center = row[x];
            const int highPass = 4 * center - row[x - 1] - row[x + 1] - above[x] - below[x];
            const int value = center + ((scaled * highPass) >> 8);
            out[x] = static_cast<uint8_t>(value < 0 ? 0 : (value > 255 ? 255 : value));
        }
        out[width - 1] = row[width - 1];
    }
}

void releaseWindow(JNIEnv* env, Context* ctx) {
    if (ctx->window) {
        ANativeWindow_release(ctx->window);
        ctx->window = nullptr;
    }
    if (ctx->surface) {
        env->DeleteGlobalRef(ctx->surface);
        ctx->surface = nullptr;
    }
    ctx->windowWidth = 0;
    ctx->windowHeight = 0;
}

void freeContext(Context* ctx) {
    sws_freeContext(ctx->sws);
    av_frame_free(&ctx->frame);
    av_packet_free(&ctx->packet);
    avcodec_free_context(&ctx->codec);
    free(ctx->input);
    delete ctx;
}

}  // namespace

extern "C" {

JNIEXPORT jlong JNICALL Java_com_illusion_app_data_player_ffmpeg_FfmpegVideoDecoder_nativeInit(
    JNIEnv* env, jobject, jstring jCodecName, jbyteArray jExtraData, jint width, jint height,
    jint threads) {
    const char* codecName = env->GetStringUTFChars(jCodecName, nullptr);
    const AVCodec* codec = avcodec_find_decoder_by_name(codecName);
    if (!codec) LOGE("Decoder %s is not in this FFmpeg build", codecName);
    env->ReleaseStringUTFChars(jCodecName, codecName);
    if (!codec) return 0;

    // These members are only ever reached by name from here, so a shrinker that strips them (R8 did
    // in the first release build - see proguard-rules.pro) shows up as a failed lookup. Every lookup
    // is checked before the next JNI call: calling into JNI with that NoSuchMethodError still pending
    // is a hard ART abort of the whole app, instead of a decoder error Media3 can report.
    auto lookupFailed = [env](const char* member) {
        env->ExceptionClear();
        LOGE("VideoDecoderOutputBuffer.%s not found - stripped by the shrinker?", member);
        return static_cast<jlong>(0);
    };
    jclass outputBufferClass = env->FindClass("androidx/media3/decoder/VideoDecoderOutputBuffer");
    if (!outputBufferClass) return lookupFailed("<class>");
    initForYuvFrameMethod = env->GetMethodID(outputBufferClass, "initForYuvFrame", "(IIIII)Z");
    if (!initForYuvFrameMethod) return lookupFailed("initForYuvFrame");
    initForPrivateFrameMethod = env->GetMethodID(outputBufferClass, "initForPrivateFrame", "(II)V");
    if (!initForPrivateFrameMethod) return lookupFailed("initForPrivateFrame");
    dataField = env->GetFieldID(outputBufferClass, "data", "Ljava/nio/ByteBuffer;");
    if (!dataField) return lookupFailed("data");
    decoderPrivateField = env->GetFieldID(outputBufferClass, "decoderPrivate", "J");
    if (!decoderPrivateField) return lookupFailed("decoderPrivate");

    Context* ctx = new Context();
    ctx->codec = avcodec_alloc_context3(codec);
    ctx->packet = av_packet_alloc();
    ctx->frame = av_frame_alloc();
    if (!ctx->codec || !ctx->packet || !ctx->frame) {
        LOGE("Out of memory allocating decoder context");
        freeContext(ctx);
        return 0;
    }
    // Packet pts are Media3 microseconds; decoded frames carry them back out after reordering.
    ctx->codec->pkt_timebase = AVRational{1, 1000000};
    // MS-MPEG4 (DivX 3), H.263 and WMV carry no picture size in the bitstream - it has to come from
    // the container. Harmless for codecs that do parse it in-band.
    ctx->codec->width = width;
    ctx->codec->height = height;
    // Slice threading only: frame threading would add (threads - 1) frames of extra output delay,
    // which Media3's one-output-per-input SimpleDecoder model turns into dropped frames at every
    // seek and at end of stream.
    ctx->codec->thread_count = threads;
    ctx->codec->thread_type = FF_THREAD_SLICE;
    if (jExtraData) {
        jsize size = env->GetArrayLength(jExtraData);
        ctx->codec->extradata =
            static_cast<uint8_t*>(av_mallocz(size + AV_INPUT_BUFFER_PADDING_SIZE));
        if (ctx->codec->extradata) {
            env->GetByteArrayRegion(jExtraData, 0, size,
                                    reinterpret_cast<jbyte*>(ctx->codec->extradata));
            ctx->codec->extradata_size = size;
        }
    }
    int result = avcodec_open2(ctx->codec, codec, nullptr);
    if (result < 0) {
        logAvError("avcodec_open2", result);
        freeContext(ctx);
        return 0;
    }
    return reinterpret_cast<jlong>(ctx);
}

JNIEXPORT jint JNICALL Java_com_illusion_app_data_player_ffmpeg_FfmpegVideoDecoder_nativeDecode(
    JNIEnv* env, jobject, jlong jContext, jobject jData, jint size, jlong timeUs) {
    Context* ctx = reinterpret_cast<Context*>(jContext);
    if (ctx->hasFrame) {
        av_frame_unref(ctx->frame);
        ctx->hasFrame = false;
    }
    const uint8_t* data = static_cast<const uint8_t*>(env->GetDirectBufferAddress(jData));
    if (!data || !ensureInputCapacity(ctx, size)) return kError;
    memcpy(ctx->input, data, size);
    memset(ctx->input + size, 0, AV_INPUT_BUFFER_PADDING_SIZE);

    av_packet_unref(ctx->packet);
    ctx->packet->data = ctx->input;
    ctx->packet->size = size;
    ctx->packet->pts = timeUs;

    int result = avcodec_send_packet(ctx->codec, ctx->packet);
    if (result == AVERROR(EAGAIN)) {
        // Decoder is still holding output: take a frame first, then the packet fits.
        if (avcodec_receive_frame(ctx->codec, ctx->frame) == 0) {
            ctx->hasFrame = true;
            int retry = avcodec_send_packet(ctx->codec, ctx->packet);
            if (retry < 0) logAvError("avcodec_send_packet retry", retry);
            return kFrameReady;
        }
    } else if (result < 0 && result != AVERROR_EOF) {
        // Old rips routinely contain a damaged packet or two. Losing that one picture is far better
        // than failing playback of the whole file, so this is logged and decoding carries on.
        logAvError("avcodec_send_packet", result);
    }

    result = avcodec_receive_frame(ctx->codec, ctx->frame);
    if (result == 0) {
        ctx->hasFrame = true;
        return kFrameReady;
    }
    if (result != AVERROR(EAGAIN) && result != AVERROR_EOF) {
        logAvError("avcodec_receive_frame", result);
    }
    return kNoFrame;
}

JNIEXPORT jlong JNICALL
Java_com_illusion_app_data_player_ffmpeg_FfmpegVideoDecoder_nativeGetFrameTimeUs(JNIEnv*, jobject,
                                                                                 jlong jContext) {
    Context* ctx = reinterpret_cast<Context*>(jContext);
    int64_t timeUs = ctx->frame->best_effort_timestamp;
    if (timeUs == AV_NOPTS_VALUE) timeUs = ctx->frame->pts;
    return timeUs;
}

JNIEXPORT jint JNICALL Java_com_illusion_app_data_player_ffmpeg_FfmpegVideoDecoder_nativeGetFrame(
    JNIEnv* env, jobject, jlong jContext, jobject jOutputBuffer, jint outputMode) {
    Context* ctx = reinterpret_cast<Context*>(jContext);
    if (!ctx->hasFrame) return kError;
    AVFrame* frame = toYuv420p(ctx, ctx->frame);
    av_frame_unref(ctx->frame);
    ctx->hasFrame = false;
    if (!frame) return kError;

    if (outputMode == kOutputModeSurfaceYuv) {
        env->CallVoidMethod(jOutputBuffer, initForPrivateFrameMethod, frame->width, frame->height);
        if (env->ExceptionCheck()) {
            av_frame_free(&frame);
            return kError;
        }
        env->SetLongField(jOutputBuffer, decoderPrivateField, reinterpret_cast<jlong>(frame));
        return kFrameReady;
    }

    if (outputMode == kOutputModeYuv) {
        int uvHeight = (frame->height + 1) / 2;
        jboolean ok = env->CallBooleanMethod(jOutputBuffer, initForYuvFrameMethod, frame->width,
                                             frame->height, frame->linesize[0], frame->linesize[1],
                                             kColorspaceBt601);
        if (env->ExceptionCheck() || !ok) {
            av_frame_free(&frame);
            return kError;
        }
        jobject dataObject = env->GetObjectField(jOutputBuffer, dataField);
        uint8_t* data = static_cast<uint8_t*>(env->GetDirectBufferAddress(dataObject));
        size_t yLength = static_cast<size_t>(frame->linesize[0]) * frame->height;
        size_t uvLength = static_cast<size_t>(frame->linesize[1]) * uvHeight;
        copyPlane(data, frame->linesize[0], frame->data[0], frame->linesize[0], frame->linesize[0],
                  frame->height);
        copyPlane(data + yLength, frame->linesize[1], frame->data[1], frame->linesize[1],
                  frame->linesize[1], uvHeight);
        copyPlane(data + yLength + uvLength, frame->linesize[2], frame->data[2],
                  frame->linesize[2], frame->linesize[2], uvHeight);
        av_frame_free(&frame);
        return kFrameReady;
    }

    av_frame_free(&frame);
    return kError;
}

JNIEXPORT jint JNICALL
Java_com_illusion_app_data_player_ffmpeg_FfmpegVideoDecoder_nativeRenderFrame(
    JNIEnv* env, jobject, jlong jContext, jobject jSurface, jobject jOutputBuffer) {
    Context* ctx = reinterpret_cast<Context*>(jContext);
    AVFrame* frame =
        reinterpret_cast<AVFrame*>(env->GetLongField(jOutputBuffer, decoderPrivateField));
    if (!frame) return kError;

    if (!ctx->surface || !env->IsSameObject(ctx->surface, jSurface)) {
        releaseWindow(env, ctx);
        ctx->window = ANativeWindow_fromSurface(env, jSurface);
        if (!ctx->window) return kNoFrame;
        ctx->surface = env->NewGlobalRef(jSurface);
    }
    if (ctx->windowWidth != frame->width || ctx->windowHeight != frame->height) {
        ANativeWindow_setBuffersGeometry(ctx->window, frame->width, frame->height, kImageFormatYv12);
        ctx->windowWidth = frame->width;
        ctx->windowHeight = frame->height;
    }

    ANativeWindow_Buffer buffer;
    if (ANativeWindow_lock(ctx->window, &buffer, nullptr) != 0 || !buffer.bits) return kError;

    uint8_t* bits = static_cast<uint8_t*>(buffer.bits);
    int copyWidth = frame->width < buffer.width ? frame->width : buffer.width;
    int copyHeight = frame->height < buffer.height ? frame->height : buffer.height;
    const float sharpen = ctx->sharpen.load(std::memory_order_relaxed);
    if (sharpen > 0.0f) {
        sharpenPlane(bits, buffer.stride, frame->data[0], frame->linesize[0], copyWidth, copyHeight,
                     sharpen);
    } else {
        copyPlane(bits, buffer.stride, frame->data[0], frame->linesize[0], copyWidth, copyHeight);
    }

    // YV12: a full-size Y plane, then V, then U, each chroma plane at half size with its stride
    // rounded up to a multiple of 16 (the layout documented for HAL_PIXEL_FORMAT_YV12).
    int uvStride = ((buffer.stride / 2) + 15) & ~15;
    int bufferUvHeight = (buffer.height + 1) / 2;
    int uvWidth = (copyWidth + 1) / 2;
    int uvHeight = (copyHeight + 1) / 2;
    uint8_t* vPlane = bits + buffer.stride * buffer.height;
    uint8_t* uPlane = vPlane + uvStride * bufferUvHeight;
    copyPlane(vPlane, uvStride, frame->data[2], frame->linesize[2], uvWidth, uvHeight);
    copyPlane(uPlane, uvStride, frame->data[1], frame->linesize[1], uvWidth, uvHeight);

    return ANativeWindow_unlockAndPost(ctx->window) == 0 ? kFrameReady : kError;
}

// Deliberately independent of the decoder context: a frame can outlive the decoder that produced it
// (the renderer releases its last output buffer after it has already released the decoder).
JNIEXPORT void JNICALL
Java_com_illusion_app_data_player_ffmpeg_FfmpegVideoDecoder_nativeReleaseFrame(
    JNIEnv* env, jobject, jobject jOutputBuffer) {
    AVFrame* frame =
        reinterpret_cast<AVFrame*>(env->GetLongField(jOutputBuffer, decoderPrivateField));
    env->SetLongField(jOutputBuffer, decoderPrivateField, 0);
    if (frame) av_frame_free(&frame);
}

JNIEXPORT void JNICALL Java_com_illusion_app_data_player_ffmpeg_FfmpegVideoDecoder_nativeSetSharpen(
    JNIEnv*, jobject, jlong jContext, jfloat amount) {
    Context* ctx = reinterpret_cast<Context*>(jContext);
    ctx->sharpen.store(amount > 0.0f ? amount : 0.0f, std::memory_order_relaxed);
}

JNIEXPORT void JNICALL Java_com_illusion_app_data_player_ffmpeg_FfmpegVideoDecoder_nativeFlush(
    JNIEnv*, jobject, jlong jContext) {
    Context* ctx = reinterpret_cast<Context*>(jContext);
    avcodec_flush_buffers(ctx->codec);
    av_frame_unref(ctx->frame);
    ctx->hasFrame = false;
}

JNIEXPORT void JNICALL Java_com_illusion_app_data_player_ffmpeg_FfmpegVideoDecoder_nativeRelease(
    JNIEnv* env, jobject, jlong jContext) {
    Context* ctx = reinterpret_cast<Context*>(jContext);
    releaseWindow(env, ctx);
    freeContext(ctx);
}

JNIEXPORT jstring JNICALL Java_com_illusion_app_data_player_ffmpeg_FfmpegLibrary_nativeGetVersion(
    JNIEnv* env, jobject) {
    return env->NewStringUTF(av_version_info());
}

JNIEXPORT jboolean JNICALL Java_com_illusion_app_data_player_ffmpeg_FfmpegLibrary_nativeHasDecoder(
    JNIEnv* env, jobject, jstring jCodecName) {
    const char* codecName = env->GetStringUTFChars(jCodecName, nullptr);
    bool found = avcodec_find_decoder_by_name(codecName) != nullptr;
    env->ReleaseStringUTFChars(jCodecName, codecName);
    return found;
}

}  // extern "C"
