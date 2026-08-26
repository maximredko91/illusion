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
val ffmpegExtensionAar = localProperties.getProperty("illusion.ffmpegExtension.aarPath")
    ?.let { file(it) }
    ?.takeIf { it.exists() }

// TMDB API key for the developer-only "add media" scraper (data/tmdb/TmdbClient.kt) - a free key
// from https://www.themoviedb.org/settings/api, kept out of version control the same way as the
// ffmpeg extension path above. Empty string if unset; TmdbClient treats that as "feature disabled."
val tmdbApiKey = localProperties.getProperty("illusion.tmdb.apiKey") ?: ""

// Fixed developer password for the "add media" gate (data/security/DevAccessStore.kt) - kept out
// of version control the same way as the keys above, so it survives an app uninstall/data-clear
// (the in-app-generated password does not, since it lives in this install's EncryptedSharedPreferences).
// Empty string if unset; DevAccessStore falls back to its normal generate-and-show-once behavior.
val devAccessPassword = localProperties.getProperty("illusion.devAccess.password") ?: ""

// Release signing keystore - generated once via keytool, kept out of version control (app/*.jks
// is gitignored) the same way as the secrets above. Losing this file or these values means any
// future release build can no longer update an install already on a signed build (Android refuses
// to install an update signed with a different key over an existing one without a full uninstall,
// which would wipe a beta tester's SMB sources/downloads/watch history) - back it up.
val signingStoreFile = localProperties.getProperty("illusion.signing.storeFile")?.let { file(it) }
    ?.takeIf { it.exists() }
val signingStorePassword = localProperties.getProperty("illusion.signing.storePassword")
val signingKeyAlias = localProperties.getProperty("illusion.signing.keyAlias")
val signingKeyPassword = localProperties.getProperty("illusion.signing.keyPassword")

android {
    namespace = "com.illusion.app"
    compileSdk {
        version = release(37)
    }

    defaultConfig {
        applicationId = "com.illusion.app"
        minSdk = 26
        targetSdk = 37
        // Bump versionCode with every build installed for testing, and versionName's betaN
        // suffix when it's a meaningfully new build - lets Settings show which exact build is on
        // a device instead of guessing from install timestamps. Switched alphaN -> betaN
        // 2026-08-25 once the repo went public and beta testers started using the in-app updater -
        // restarted the counter at beta1 rather than continuing the alpha sequence's number, since
        // jumping straight to "beta73" would misleadingly imply 72 prior beta builds existed.
        versionCode = 81
        versionName = "0.1.0-beta8"

        // The real bulk of a universal APK's size turned out to be native .so libs bundled per-ABI
        // (ML Kit's on-device tag-translation library alone is ~17MB *per architecture*) - R8
        // shrinking can't touch precompiled native code at all. Distribution here is a flat APK via
        // GitHub Releases, not Play Store's automatic per-device App Bundle splitting, so this app
        // has to pick its own ABI(s) - both real targets (the developer's phone, the Xiaomi TV Box)
        // are arm64. armeabi-v7a/x86/x86_64 were only ever relevant for very old devices or the
        // emulator, and this project has never actually tested on an emulator (always the real
        // on-device workflow - see CLAUDE.md's own testing conventions).
        ndk {
            abiFilters += "arm64-v8a"
        }

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        buildConfigField("String", "TMDB_API_KEY", "\"$tmdbApiKey\"")
        buildConfigField("String", "DEV_ACCESS_PASSWORD", "\"$devAccessPassword\"")
    }

    signingConfigs {
        if (signingStoreFile != null) {
            create("release") {
                storeFile = signingStoreFile
                storePassword = signingStorePassword
                keyAlias = signingKeyAlias
                keyPassword = signingKeyPassword
            }
        }
    }
    buildTypes {
        release {
            // AGP 9's declarative DSL - no minifyEnabled/proguardFiles here, keep rules live under
            // optimization.keepRules instead. Room/Compose/Media3/kotlinx.serialization all ship
            // their own consumer-rules.pro bundled in their AARs (that's what those are for), so
            // this app only needs to add rules for things that actually break - verified by reading
            // R8's own real output, not guessed upfront.
            optimization {
                enable = true
                keepRules {
                    files.add(file("proguard-rules.pro"))
                }
            }
            // Falls back to no signingConfig (Android Studio then signs "release" builds with the
            // debug key) on a fresh checkout that hasn't generated app/illusion-release.jks yet -
            // keeps `./gradlew assembleRelease` from hard-failing for a first-time contributor.
            if (signingStoreFile != null) {
                signingConfig = signingConfigs.getByName("release")
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
    implementation(libs.androidx.core.splashscreen)
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
    implementation(libs.okhttp)

    // On-device EN->RU translation for freeform .nfo <tag> values (data/translation/TagTranslator.kt) -
    // model downloads once (needs network), then runs fully offline, same one-time-network shape
    // as this app's only other online touchpoint (TmdbClient).
    implementation("com.google.mlkit:translate:17.0.3")

    ffmpegExtensionAar?.let { implementation(files(it)) }

    testImplementation(libs.junit)
    // NfoParser uses org.xmlpull.v1.XmlPullParserFactory.newInstance(), which needs a real
    // provider on the JVM unit-test classpath (Android's built-in impl isn't available there).
    testImplementation("net.sf.kxml:kxml2:2.3.0")
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation("androidx.room:room-testing:${libs.versions.room.get()}")
    debugImplementation(libs.androidx.compose.ui.test.manifest)
    debugImplementation(libs.androidx.compose.ui.tooling)
}