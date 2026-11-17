import java.io.File

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
    id("org.jetbrains.kotlin.plugin.serialization")
}

android {
    namespace = "dev.lukat.briefing"
    compileSdk = 35

    signingConfigs {
        create("release") {
            // The release key never lives in the repo: ~/.briefing first, the old Hermes home as fallback.
            // Without it the release build is unsigned, so a fresh checkout still compiles.
            val home = System.getProperty("user.home")
            val dir = listOf(File(home, ".briefing"), File(home, "AppData/Local/hermes"))
                .firstOrNull { File(it, "briefing-release.jks").exists() } ?: File(home, ".briefing")
            val store = File(dir, "briefing-release.jks")
            val pass = File(dir, ".briefing_keystore_pass")
            if (store.exists() && pass.exists()) {
                storeFile = store
                storePassword = pass.readText().trim()
                keyAlias = "briefing"
                keyPassword = pass.readText().trim()
            }
        }
    }

    defaultConfig {
        applicationId = "dev.lukat.briefing"
        minSdk = 31
        targetSdk = 35
        versionCode = 41
        versionName = "0.41"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            signingConfig = signingConfigs.getByName("release")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    // Screenshot tests (Roborazzi + Robolectric) render real screens to PNG on the JVM:
    //   gradle testDebugUnitTest --tests "*Screenshots*"  ->  app/build/screenshots/*.png
    testOptions {
        unitTests {
            isIncludeAndroidResources = true
            all { it.systemProperty("roborazzi.test.record", "true") }
        }
    }
}

dependencies {
    val composeBom = platform("androidx.compose:compose-bom:2024.10.01")
    implementation(composeBom)
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.activity:activity-compose:1.9.3")
    implementation("androidx.activity:activity-ktx:1.9.3")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.7")

    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.7.3")
    implementation("io.coil-kt:coil-compose:2.7.0")
    implementation("androidx.glance:glance-appwidget:1.1.1")
    implementation("androidx.window:window:1.3.0")
    implementation("androidx.work:work-runtime-ktx:2.9.1")
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.media3:media3-exoplayer:1.4.1")
    implementation("androidx.media3:media3-session:1.4.1")

    debugImplementation("androidx.compose.ui:ui-tooling")
    debugImplementation("androidx.compose.ui:ui-test-manifest")

    testImplementation("junit:junit:4.13.2")
    testImplementation("org.robolectric:robolectric:4.14.1")
    testImplementation("io.github.takahirom.roborazzi:roborazzi:1.36.0")
    testImplementation("io.github.takahirom.roborazzi:roborazzi-compose:1.36.0")
    testImplementation(composeBom)
    testImplementation("androidx.compose.ui:ui-test-junit4")
}
