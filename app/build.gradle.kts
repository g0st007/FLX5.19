plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

// ---- AdMob: IDs de prueba de Google por defecto; los reales se activan desde gradle.properties ----
fun gp(name: String): String = (project.findProperty(name) as String?)?.trim().orEmpty()
val testAppId = "ca-app-pub-3940256099942544~3347511713"
val testRewarded = "ca-app-pub-3940256099942544/5224354917"
val testBanner = "ca-app-pub-3940256099942544/6300978111"
val appVersionName = "0.5.19"
// Con admob.testVersion definido, los IDs reales solo se usan si la versión coincide (la que está en "Test apps" de AdMob)
val admobVersionOk = gp("admob.testVersion").isEmpty() || gp("admob.testVersion") == appVersionName
val admobReal = gp("admob.real") == "true" && gp("admob.appId").isNotEmpty() && admobVersionOk
fun admobId(prop: String, test: String) = if (admobReal) gp(prop).ifEmpty { test } else test

android {
    namespace = "app.flux.downloader"
    compileSdk = 34

    defaultConfig {
        applicationId = "app.flux.downloader"
        minSdk = 29
        targetSdk = 34
        versionCode = 28
        versionName = appVersionName
        manifestPlaceholders["admobAppId"] = if (admobReal) gp("admob.appId") else testAppId
        buildConfigField("boolean", "ADMOB_REAL", "$admobReal")
        buildConfigField("String", "ADMOB_APP_ID", "\"${if (admobReal) gp("admob.appId") else testAppId}\"")
        buildConfigField("String", "ADMOB_REWARDED_DOWNLOAD", "\"${admobId("admob.rewardedDownload", testRewarded)}\"")
        buildConfigField("String", "ADMOB_REWARDED_VAULT_UNLOCK", "\"${admobId("admob.rewardedVaultUnlock", testRewarded)}\"")
        buildConfigField("String", "ADMOB_REWARDED_VAULT_SLOT", "\"${admobId("admob.rewardedVaultSlot", testRewarded)}\"")
        buildConfigField("String", "ADMOB_BANNER_HOME", "\"${admobId("admob.bannerHome", testBanner)}\"")
        buildConfigField("String", "ADMOB_BANNER_PROGRESS", "\"${admobId("admob.bannerProgress", testBanner)}\"")
        buildConfigField("String", "ADMOB_TEST_DEVICES", "\"${gp("admob.testDevices")}\"")
        ndk { abiFilters += listOf("arm64-v8a", "armeabi-v7a", "x86_64") }
    }

    buildTypes {
        release {
            // La versión "release" es mucho más rápida que la debug (en Compose la diferencia es enorme).
            // Si algo se rompiera al abrirla, poner isMinifyEnabled = false como salida de emergencia.
            isMinifyEnabled = false
            isShrinkResources = false
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            // Firmada con la clave debug para poder instalarla directamente mientras es versión en progreso.
            signingConfig = signingConfigs.getByName("debug")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
    buildFeatures {
        compose = true
        buildConfig = true
    }

    // Necesario para que yt-dlp/ffmpeg (binarios nativos) se extraigan al instalar
    packaging { jniLibs { useLegacyPackaging = true } }
}

dependencies {
    implementation(platform("androidx.compose:compose-bom:2024.09.00"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.activity:activity-compose:1.9.2")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.6")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.6")
    implementation("io.coil-kt:coil-compose:2.7.0")
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.webkit:webkit:1.11.0")

    // Reproductor propio
    implementation("androidx.media3:media3-exoplayer:1.4.1")
    implementation("androidx.media3:media3-ui:1.4.1")
    implementation("androidx.media3:media3-exoplayer-hls:1.4.1")
    implementation("androidx.media3:media3-exoplayer-dash:1.4.1")

    // Anuncios: AdMob (incluye el SDK de consentimiento UMP). Se queda en 23.x: la 24+ exige Kotlin 2.1 y compileSdk 35
    implementation("com.google.android.gms:play-services-ads:23.6.0")

    // Motor de descargas: yt-dlp + FFmpeg empaquetados para Android
    implementation("io.github.junkfood02.youtubedl-android:library:0.17.2")
    implementation("io.github.junkfood02.youtubedl-android:ffmpeg:0.17.2")
}
