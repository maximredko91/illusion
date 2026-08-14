import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.ksp)
}

// Опциональное подключение локально собранного media3 decoder_ffmpeg .aar (DTS/AC3/TrueHD) -
// см. scripts/build_ffmpeg_extension.sh. Путь задаётся в local.properties (не в системе контроля
// версий, т.к. специфичен для машины) и берётся отсюда, а не из project properties, потому что
// local.properties не пробрасывается в Gradle property API автоматически.
val localProperties = Properties().apply {
    val file = rootProject.file("local.properties")
    if (file.exists()) file.inputStream().use { load(it) }
}
val ffmpegExtensionAar = localProperties.getProperty("seance.ffmpegExtension.aarPath")
    ?.let { file(it) }
    ?.takeIf { it.exists() }

android {
    namespace = "com.seance.app"
    compileSdk {
        version = release(37)
    }

    defaultConfig {
        applicationId = "com.seance.app"
        minSdk = 26
        targetSdk = 37
        // Bump versionCode with every build installed for testing, and versionName's alphaN
        // suffix when it's a meaningfully new build - lets Settings show which exact build is on
        // a device instead of guessing from install timestamps.
        versionCode = 12
        versionName = "0.1.0-alpha11"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        release {
            optimization {
                enable = false
            }
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }
}

configurations.all {
    resolutionStrategy {
        force("org.jetbrains.kotlin:kotlin-stdlib:${libs.versions.kotlin.get()}")
    }
}

dependencies {
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons.core)
    implementation(libs.androidx.compose.material.icons.extended)
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.navigation.compose)

    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)

    implementation(libs.androidx.work.runtime.ktx)
    implementation(libs.androidx.datastore.preferences)

    implementation(libs.coil.compose)
    implementation(libs.coil.network.okhttp)

    implementation(libs.kotlinx.serialization.json)

    implementation(libs.androidx.media3.exoplayer)
    implementation(libs.androidx.media3.ui)
    implementation(libs.androidx.media3.session)
    implementation(libs.androidx.media3.cast)
    implementation(libs.androidx.media3.effect)
    implementation(libs.androidx.media3.datasource)

    implementation(libs.smbj)
    implementation(libs.androidx.security.crypto)

    ffmpegExtensionAar?.let { implementation(files(it)) }

    testImplementation(libs.junit)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(libs.androidx.junit)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
    debugImplementation(libs.androidx.compose.ui.tooling)
}