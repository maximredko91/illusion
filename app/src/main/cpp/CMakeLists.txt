cmake_minimum_required(VERSION 3.22.1)
project(illusion_ffmpeg C CXX)

# Prebuilt LGPL FFmpeg shared libraries, produced by .github/workflows/ffmpeg-android.yml
# (scripts/build_ffmpeg_android.sh) and committed per ABI. AGP packages IMPORTED shared libraries
# linked into a target automatically, so they must NOT also live under src/main/jniLibs - that
# would put two copies of each .so into the APK and fail the merge.
set(FFMPEG_DIR ${CMAKE_CURRENT_SOURCE_DIR}/ffmpeg/${ANDROID_ABI})

foreach(lib avutil swscale avcodec)
    add_library(${lib} SHARED IMPORTED)
    set_target_properties(${lib} PROPERTIES IMPORTED_LOCATION ${FFMPEG_DIR}/lib/lib${lib}.so)
endforeach()

add_library(illusion_ffmpeg SHARED ffmpeg_video_jni.cc ffmpeg_audio_jni.cc)
target_include_directories(illusion_ffmpeg PRIVATE ${FFMPEG_DIR}/include)
target_compile_options(illusion_ffmpeg PRIVATE -O2 -Wall)
# 16 KB page alignment - devices shipping 16 KB kernels refuse to load 4 KB-aligned libraries.
target_link_options(illusion_ffmpeg PRIVATE "-Wl,-z,max-page-size=16384")
target_link_libraries(illusion_ffmpeg avcodec swscale avutil android log)
