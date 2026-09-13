#!/usr/bin/env bash
#
# Собирает минимальный LGPL-FFmpeg под Android для программного видеодекодера и демуксера
# Иллюзиона. Запускается в GitHub Actions (.github/workflows/ffmpeg-android.yml) на Linux:
# configure/make FFmpeg требуют POSIX-окружения, которого на Windows без MSYS2/WSL нет.
#
# Результат: $OUT_DIR/<abi>/lib/lib{avcodec,avformat,avutil,swscale}.so и $OUT_DIR/<abi>/include.
# Без --enable-gpl/--enable-nonfree — все включённые компоненты под LGPL 2.1+, линкуются
# динамически (отдельными .so в APK), текст лицензии кладётся рядом.
#
# Состав выбран под конкретные дыры в поддержке форматов на телефоне (проверено по
# media_codecs*.xml устройства — декодеров VC-1/WMV и WMA там нет вовсе, MPEG-4 только
# Simple Profile, MPEG-2 лишь софтовый c2.android.mpeg2):
#   видео  — MPEG-4 ASP (DivX/XviD), MS-MPEG4 v1-3 (DivX 3), H.263, WMV1-3/VC-1, MPEG-1/2
#   аудио  — WMA (звуковая дорожка .wmv; на устройстве декодера тоже нет)
#   демукс — Matroska (патологические Cues), ASF (.wmv — в Media3 такого экстрактора нет), AVI
set -euo pipefail

FFMPEG_TAG="${FFMPEG_TAG:-n8.1.2}"
API=26
NDK="${ANDROID_NDK_HOME:?Укажите ANDROID_NDK_HOME}"
SRC_DIR="${SRC_DIR:-$PWD/ffmpeg-src}"
OUT_DIR="${OUT_DIR:-$PWD/ffmpeg-android}"
TOOLCHAIN="$NDK/toolchains/llvm/prebuilt/linux-x86_64"

DECODERS="mpeg4,msmpeg4v1,msmpeg4v2,msmpeg4v3,h263,h263p,wmv1,wmv2,wmv3,vc1,mpeg1video,mpeg2video,wmav1,wmav2,wmapro"
PARSERS="mpeg4video,h263,vc1,mpegvideo"
DEMUXERS="matroska,asf,avi"
# DivX 5 "packed B-frames" (userdata вида DivX503b1393p) — без этого фильтра кадры приходят
# в неправильном порядке относительно временных меток контейнера.
BSFS="mpeg4_unpack_bframes"

[ -d "$SRC_DIR" ] || git clone --depth 1 --branch "$FFMPEG_TAG" https://github.com/FFmpeg/FFmpeg.git "$SRC_DIR"

build() {
    local abi=$1 arch=$2 cpu=$3 triple=$4
    shift 4
    local prefix="$OUT_DIR/$abi"
    local build_dir="$PWD/build-$abi"
    rm -rf "$build_dir"
    mkdir -p "$build_dir"
    pushd "$build_dir" >/dev/null

    "$SRC_DIR/configure" \
        --prefix="$prefix" \
        --target-os=android --arch="$arch" --cpu="$cpu" --enable-cross-compile \
        --cc="$TOOLCHAIN/bin/${triple}${API}-clang" \
        --cxx="$TOOLCHAIN/bin/${triple}${API}-clang++" \
        --ar="$TOOLCHAIN/bin/llvm-ar" \
        --nm="$TOOLCHAIN/bin/llvm-nm" \
        --ranlib="$TOOLCHAIN/bin/llvm-ranlib" \
        --strip="$TOOLCHAIN/bin/llvm-strip" \
        --enable-shared --disable-static --enable-pic \
        --disable-everything --disable-autodetect --disable-programs --disable-doc \
        --disable-avdevice --disable-avfilter --disable-swresample --disable-network --disable-debug \
        --enable-avcodec --enable-avformat --enable-avutil --enable-swscale \
        --enable-decoder="$DECODERS" \
        --enable-parser="$PARSERS" \
        --enable-demuxer="$DEMUXERS" \
        --enable-bsf="$BSFS" \
        --extra-ldflags="-Wl,-z,max-page-size=16384" \
        "$@"

    # Загрузчик Android ищет DT_NEEDED по точному имени файла, а в APK попадают только lib*.so —
    # версионные SONAME по умолчанию (libavutil.so.60) в рантайме никогда бы не нашлись.
    sed -i \
        -e 's/^SLIBNAME_WITH_MAJOR=.*/SLIBNAME_WITH_MAJOR=$(SLIBNAME)/' \
        -e 's/^SLIB_INSTALL_NAME=.*/SLIB_INSTALL_NAME=$(SLIBNAME)/' \
        -e 's/^SLIB_INSTALL_LINKS=.*/SLIB_INSTALL_LINKS=/' \
        ffbuild/config.mak

    make -j"$(nproc)"
    make install
    "$TOOLCHAIN/bin/llvm-strip" --strip-unneeded "$prefix"/lib/*.so
    rm -rf "$prefix/lib/pkgconfig" "$prefix/share"
    popd >/dev/null
}

build arm64-v8a aarch64 armv8-a aarch64-linux-android
build armeabi-v7a arm armv7-a armv7a-linux-androideabi --enable-neon

cp "$SRC_DIR/COPYING.LGPLv2.1" "$SRC_DIR/LICENSE.md" "$OUT_DIR/"
echo "$FFMPEG_TAG" > "$OUT_DIR/VERSION"

for abi in arm64-v8a armeabi-v7a; do
    echo "== $abi =="
    ls -la "$OUT_DIR/$abi/lib"
    "$TOOLCHAIN/bin/llvm-readelf" -d "$OUT_DIR/$abi/lib/libavcodec.so" | grep -E "SONAME|NEEDED"
done
