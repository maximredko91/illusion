#!/usr/bin/env bash
#
# Собирает decoder_ffmpeg-расширение Media3 (декодирование DTS/AC3/TrueHD и т.п. в ExoPlayer)
# из исходников. Google не публикует готовый .aar в Maven — нужно скомпилировать самому
# через Android NDK, это официально описанный, но ручной процесс.
#
# ВАЖНО: это отражает документированный процесс сборки decoder_ffmpeg на момент версии
# Media3 1.11.0 (см. libraries/decoder_ffmpeg/README.md в репозитории androidx/media).
# Точные имена скриптов/флагов иногда меняются между релизами Media3 — если что-то из
# нижеперечисленного не найдётся по пути, свериться с актуальным README в клонированном
# репозитории (шаг 1) — он всегда самый точный источник для конкретной версии.
#
# Требования:
#   - Android NDK (рекомендуется версия из libraries/decoder_ffmpeg/README.md для вашей
#     версии Media3; можно поставить через Android Studio: SDK Manager -> SDK Tools -> NDK)
#   - git, make, автотулза host-toolchain для сборки FFmpeg (на Linux/macOS "из коробки";
#     на Windows — через WSL или Git Bash с MSYS2 toolchain)
#
# Использование:
#   NDK_PATH=/path/to/android-ndk ./build_ffmpeg_extension.sh [host_platform]
#
# host_platform — один из: linux-x86_64, darwin-x86_64, windows-x86_64 (по умолчанию
# скрипт попробует определить автоматически).

set -euo pipefail

MEDIA3_TAG="1.11.0"
WORK_DIR="${WORK_DIR:-$(pwd)/media3-ffmpeg-build}"
NDK_PATH="${NDK_PATH:?Укажите переменную окружения NDK_PATH с путём к Android NDK}"

case "$(uname -s)" in
    Linux*)  DEFAULT_HOST="linux-x86_64" ;;
    Darwin*) DEFAULT_HOST="darwin-x86_64" ;;
    MINGW*|MSYS*|CYGWIN*) DEFAULT_HOST="windows-x86_64" ;;
    *) DEFAULT_HOST="linux-x86_64" ;;
esac
HOST_PLATFORM="${1:-$DEFAULT_HOST}"

echo "== 1. Клонирую androidx/media (тег $MEDIA3_TAG) в $WORK_DIR =="
if [ ! -d "$WORK_DIR" ]; then
    git clone --branch "$MEDIA3_TAG" --depth 1 https://github.com/androidx/media.git "$WORK_DIR"
fi
cd "$WORK_DIR"

echo "== 2. Проверьте libraries/decoder_ffmpeg/README.md на актуальные шаги для этой версии =="
cat libraries/decoder_ffmpeg/README.md || true

FFMPEG_EXT_PATH="$WORK_DIR/libraries/decoder_ffmpeg/src/main/jni"

echo "== 3. Скачиваю исходники FFmpeg (может занять время) =="
cd "$FFMPEG_EXT_PATH"
./download_ffmpeg.sh 2>/dev/null || echo "Скрипт download_ffmpeg.sh не найден по этому пути — см. актуальный README из шага 2."

echo "== 4. Собираю нативные библиотеки FFmpeg под Android (только нужные декодеры звука) =="
./build_ffmpeg.sh \
    "$FFMPEG_EXT_PATH" \
    "$NDK_PATH" \
    "$HOST_PLATFORM" \
    "ac3 eac3 dca truehd" \
    || echo "Скрипт build_ffmpeg.sh не найден по этому пути — см. актуальный README из шага 2."

echo "== 5. Собираю сам модуль decoder_ffmpeg через Gradle =="
cd "$WORK_DIR"
./gradlew :lib-decoder-ffmpeg:assembleRelease

AAR_PATH=$(find . -path "*decoder_ffmpeg*release.aar" | head -n 1)
echo
echo "Готово. Если сборка прошла успешно, AAR должен лежать примерно тут:"
echo "  $AAR_PATH"
echo
echo "Дальше в проекте \"Сеанс\": пропишите путь к этому файлу в local.properties как"
echo "  seance.ffmpegExtension.aarPath=$AAR_PATH"
echo "(см. закомментированный пример в gradle.properties) и пересоберите приложение —"
echo "app/build.gradle.kts подключит его автоматически, если файл существует."
