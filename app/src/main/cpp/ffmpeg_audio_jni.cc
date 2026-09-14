// JNI bridge between FfmpegAudioDecoder (Media3 SimpleDecoder) and libavcodec - WMA from .wmv files,
// AC3/E-AC3, DTS and TrueHD from .mkv remuxes (no platform decoder for any of them on the phone).
//
// Follows Media3's decoder_ffmpeg JNI (ffmpeg_jni.cc, 1.11.0) with two differences:
//  - no libswresample (the app's FFmpeg build leaves it out): WMA decoders emit planar float, and
//    interleaving that into the float or 16-bit PCM AudioSink wants is a plain loop;
//  - a packet is decoded into a native buffer first and its size returned, then copied out by a
//    second call - Kotlin sizes the output buffer exactly, no callback from native code into Java
//    (which R8 would also have to be told about, see proguard-rules.pro for why that bites).

#include <android/log.h>
#include <jni.h>

#include <cmath>
#include <cstdlib>
#include <cstring>

extern "C" {
#include <libavcodec/avcodec.h>
#include <libavutil/channel_layout.h>
#include <libavutil/error.h>
#include <libavutil/samplefmt.h>
}

#define LOG_TAG "ffmpeg_audio_jni"
#define LOGE(...) ((void)__android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__))
#define LOGW(...) ((void)__android_log_print(ANDROID_LOG_WARN, LOG_TAG, __VA_ARGS__))

// Must match FfmpegAudioDecoder.
static const int kInvalidData = -1;
static const int kFatal = -2;

namespace {

struct AudioContext {
    AVCodecContext* codec = nullptr;
    AVPacket* packet = nullptr;
    AVFrame* frame = nullptr;
    bool outputFloat = false;
    uint8_t* input = nullptr;
    size_t inputCapacity = 0;
    uint8_t* output = nullptr;
    size_t outputCapacity = 0;
    size_t outputSize = 0;
    // Kept to rebuild the codec context from scratch, see openCodec() and nativeFlush.
    int sampleRate = 0;
    int channelCount = 0;
    int bitRate = 0;
    int blockAlign = 0;
    int bitsPerSample = 0;
    uint8_t* extraData = nullptr;
    int extraDataSize = 0;
};

bool ensureCapacity(uint8_t** buffer, size_t* capacity, size_t required) {
    if (*capacity >= required) return true;
    size_t grown = required + required / 2;
    uint8_t* resized = static_cast<uint8_t*>(realloc(*buffer, grown));
    if (!resized) return false;
    *buffer = resized;
    *capacity = grown;
    return true;
}

void logAvError(const char* what, int error) {
    char message[AV_ERROR_MAX_STRING_SIZE];
    av_strerror(error, message, sizeof(message));
    LOGW("%s: %s", what, message);
}

bool isSupportedSampleFormat(int format) {
    switch (format) {
        case AV_SAMPLE_FMT_FLTP:
        case AV_SAMPLE_FMT_FLT:
        case AV_SAMPLE_FMT_S16P:
        case AV_SAMPLE_FMT_S16:
        case AV_SAMPLE_FMT_S32P:
        case AV_SAMPLE_FMT_S32:
        case AV_SAMPLE_FMT_DBLP:
        case AV_SAMPLE_FMT_DBL:
            return true;
        default:
            return false;
    }
}

float sampleAt(const AVFrame* frame, int channel, int index, int channels) {
    int interleaved = index * channels + channel;
    switch (frame->format) {
        case AV_SAMPLE_FMT_FLTP:
            return reinterpret_cast<float*>(frame->extended_data[channel])[index];
        case AV_SAMPLE_FMT_FLT:
            return reinterpret_cast<float*>(frame->data[0])[interleaved];
        case AV_SAMPLE_FMT_S16P:
            return reinterpret_cast<int16_t*>(frame->extended_data[channel])[index] / 32768.0f;
        case AV_SAMPLE_FMT_S16:
            return reinterpret_cast<int16_t*>(frame->data[0])[interleaved] / 32768.0f;
        case AV_SAMPLE_FMT_S32P:
            return reinterpret_cast<int32_t*>(frame->extended_data[channel])[index] / 2147483648.0f;
        case AV_SAMPLE_FMT_S32:
            return reinterpret_cast<int32_t*>(frame->data[0])[interleaved] / 2147483648.0f;
        case AV_SAMPLE_FMT_DBLP:
            return static_cast<float>(reinterpret_cast<double*>(frame->extended_data[channel])[index]);
        case AV_SAMPLE_FMT_DBL:
            return static_cast<float>(reinterpret_cast<double*>(frame->data[0])[interleaved]);
        default:
            return 0.0f;
    }
}

bool appendFrame(AudioContext* ctx, const AVFrame* frame) {
    int channels = frame->ch_layout.nb_channels;
    int samples = frame->nb_samples;
    if (channels <= 0 || samples <= 0) return true;
    if (!isSupportedSampleFormat(frame->format)) {
        LOGE("Unsupported sample format %d", frame->format);
        return false;
    }
    size_t bytesPerSample = ctx->outputFloat ? sizeof(float) : sizeof(int16_t);
    size_t required = ctx->outputSize + static_cast<size_t>(samples) * channels * bytesPerSample;
    if (!ensureCapacity(&ctx->output, &ctx->outputCapacity, required)) return false;
    uint8_t* out = ctx->output + ctx->outputSize;
    for (int s = 0; s < samples; s++) {
        for (int c = 0; c < channels; c++) {
            float value = sampleAt(frame, c, s, channels);
            if (ctx->outputFloat) {
                memcpy(out, &value, sizeof(float));
                out += sizeof(float);
            } else {
                float clamped = value > 1.0f ? 1.0f : (value < -1.0f ? -1.0f : value);
                int16_t pcm = static_cast<int16_t>(lrintf(clamped * 32767.0f));
                memcpy(out, &pcm, sizeof(int16_t));
                out += sizeof(int16_t);
            }
        }
    }
    ctx->outputSize = required;
    return true;
}

// Allocates and opens ctx->codec from the parameters saved in ctx.
bool openCodec(AudioContext* ctx, const AVCodec* codec) {
    ctx->codec = avcodec_alloc_context3(codec);
    if (!ctx->codec) return false;
    // WMA decoders read these from the container's WAVEFORMATEX, not the bitstream; the others ignore them.
    ctx->codec->sample_rate = ctx->sampleRate;
    if (ctx->channelCount > 0) av_channel_layout_default(&ctx->codec->ch_layout, ctx->channelCount);
    ctx->codec->bit_rate = ctx->bitRate;
    ctx->codec->block_align = ctx->blockAlign;
    ctx->codec->bits_per_coded_sample = ctx->bitsPerSample;
    if (ctx->extraData) {
        ctx->codec->extradata =
            static_cast<uint8_t*>(av_mallocz(ctx->extraDataSize + AV_INPUT_BUFFER_PADDING_SIZE));
        if (!ctx->codec->extradata) return false;
        memcpy(ctx->codec->extradata, ctx->extraData, ctx->extraDataSize);
        ctx->codec->extradata_size = ctx->extraDataSize;
    }
    int result = avcodec_open2(ctx->codec, codec, nullptr);
    if (result < 0) {
        logAvError("avcodec_open2", result);
        return false;
    }
    return true;
}

void freeContext(AudioContext* ctx) {
    free(ctx->extraData);
    av_frame_free(&ctx->frame);
    av_packet_free(&ctx->packet);
    avcodec_free_context(&ctx->codec);
    free(ctx->input);
    free(ctx->output);
    delete ctx;
}

}  // namespace

extern "C" {

JNIEXPORT jlong JNICALL Java_com_illusion_app_data_player_ffmpeg_FfmpegAudioDecoder_nativeInit(
    JNIEnv* env, jobject, jstring jCodecName, jbyteArray jExtraData, jint sampleRate,
    jint channelCount, jint bitRate, jint blockAlign, jint bitsPerSample, jboolean outputFloat) {
    const char* codecName = env->GetStringUTFChars(jCodecName, nullptr);
    const AVCodec* codec = avcodec_find_decoder_by_name(codecName);
    if (!codec) LOGE("Decoder %s is not in this FFmpeg build", codecName);
    env->ReleaseStringUTFChars(jCodecName, codecName);
    if (!codec) return 0;

    AudioContext* ctx = new AudioContext();
    ctx->outputFloat = outputFloat;
    ctx->sampleRate = sampleRate;
    ctx->channelCount = channelCount;
    ctx->bitRate = bitRate;
    ctx->blockAlign = blockAlign;
    ctx->bitsPerSample = bitsPerSample;
    if (jExtraData) {
        ctx->extraDataSize = env->GetArrayLength(jExtraData);
        ctx->extraData = static_cast<uint8_t*>(malloc(ctx->extraDataSize > 0 ? ctx->extraDataSize : 1));
        if (ctx->extraData) {
            env->GetByteArrayRegion(jExtraData, 0, ctx->extraDataSize,
                                    reinterpret_cast<jbyte*>(ctx->extraData));
        }
    }
    ctx->packet = av_packet_alloc();
    ctx->frame = av_frame_alloc();
    if (!ctx->packet || !ctx->frame || (jExtraData && !ctx->extraData) || !openCodec(ctx, codec)) {
        LOGE("Failed to set up %s decoder", codec->name);
        freeContext(ctx);
        return 0;
    }
    return reinterpret_cast<jlong>(ctx);
}

JNIEXPORT jint JNICALL Java_com_illusion_app_data_player_ffmpeg_FfmpegAudioDecoder_nativeDecode(
    JNIEnv* env, jobject, jlong jContext, jobject jData, jint size) {
    AudioContext* ctx = reinterpret_cast<AudioContext*>(jContext);
    ctx->outputSize = 0;
    if (!ctx->codec) return kFatal;  // A TrueHD reopen after reset failed.
    const uint8_t* data = static_cast<const uint8_t*>(env->GetDirectBufferAddress(jData));
    size_t required = static_cast<size_t>(size) + AV_INPUT_BUFFER_PADDING_SIZE;
    if (!data || !ensureCapacity(&ctx->input, &ctx->inputCapacity, required)) return kFatal;
    memcpy(ctx->input, data, size);
    memset(ctx->input + size, 0, AV_INPUT_BUFFER_PADDING_SIZE);

    av_packet_unref(ctx->packet);
    ctx->packet->data = ctx->input;
    ctx->packet->size = size;
    int result = avcodec_send_packet(ctx->codec, ctx->packet);
    if (result < 0) {
        // A damaged packet costs its own few milliseconds of audio, not the whole file.
        logAvError("avcodec_send_packet", result);
        return result == AVERROR(ENOMEM) ? kFatal : kInvalidData;
    }
    while (true) {
        result = avcodec_receive_frame(ctx->codec, ctx->frame);
        if (result == AVERROR(EAGAIN) || result == AVERROR_EOF) break;
        if (result < 0) {
            logAvError("avcodec_receive_frame", result);
            break;
        }
        bool appended = appendFrame(ctx, ctx->frame);
        av_frame_unref(ctx->frame);
        if (!appended) return kFatal;
    }
    return static_cast<jint>(ctx->outputSize);
}

JNIEXPORT void JNICALL
Java_com_illusion_app_data_player_ffmpeg_FfmpegAudioDecoder_nativeReadOutput(
    JNIEnv* env, jobject, jlong jContext, jobject jOutput, jint size) {
    AudioContext* ctx = reinterpret_cast<AudioContext*>(jContext);
    uint8_t* output = static_cast<uint8_t*>(env->GetDirectBufferAddress(jOutput));
    if (!output) return;
    size_t count = static_cast<size_t>(size) < ctx->outputSize ? static_cast<size_t>(size) : ctx->outputSize;
    memcpy(output, ctx->output, count);
}

JNIEXPORT jint JNICALL
Java_com_illusion_app_data_player_ffmpeg_FfmpegAudioDecoder_nativeGetChannelCount(JNIEnv*, jobject,
                                                                                  jlong jContext) {
    AudioContext* ctx = reinterpret_cast<AudioContext*>(jContext);
    return ctx->codec ? ctx->codec->ch_layout.nb_channels : 0;
}

JNIEXPORT jint JNICALL
Java_com_illusion_app_data_player_ffmpeg_FfmpegAudioDecoder_nativeGetSampleRate(JNIEnv*, jobject,
                                                                                jlong jContext) {
    AudioContext* ctx = reinterpret_cast<AudioContext*>(jContext);
    return ctx->codec ? ctx->codec->sample_rate : 0;
}

JNIEXPORT void JNICALL Java_com_illusion_app_data_player_ffmpeg_FfmpegAudioDecoder_nativeFlush(
    JNIEnv*, jobject, jlong jContext) {
    AudioContext* ctx = reinterpret_cast<AudioContext*>(jContext);
    ctx->outputSize = 0;
    // Media3's own decoder_ffmpeg rebuilds the TrueHD context on reset instead of flushing it: the
    // decoder keeps state avcodec_flush_buffers() doesn't clear, which breaks audio after a seek.
    if (ctx->codec && ctx->codec->codec_id == AV_CODEC_ID_TRUEHD) {
        const AVCodec* codec = ctx->codec->codec;
        avcodec_free_context(&ctx->codec);
        if (!openCodec(ctx, codec)) LOGE("Failed to reopen TrueHD decoder after reset");
        return;
    }
    avcodec_flush_buffers(ctx->codec);
}

JNIEXPORT void JNICALL Java_com_illusion_app_data_player_ffmpeg_FfmpegAudioDecoder_nativeRelease(
    JNIEnv*, jobject, jlong jContext) {
    freeContext(reinterpret_cast<AudioContext*>(jContext));
}

}  // extern "C"
