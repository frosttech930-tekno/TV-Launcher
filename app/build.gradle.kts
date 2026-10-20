plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "dev.projector.launcher"
    compileSdk = 35

    defaultConfig {
        applicationId = "dev.projector.launcher"
        minSdk = 21
        targetSdk = 35
        versionCode = 1
        versionName = "1.0"
    }

    signingConfigs {
        // A committed debug key, so every CI build has the same signature and can install over
        // the previous one. CI runners otherwise generate a fresh debug key on every run.
        getByName("debug") {
            storeFile = file("debug.keystore")
            storePassword = "android"
            keyAlias = "androiddebugkey"
            keyPassword = "android"
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            // Signed with the debug key so a sideloadable release APK comes out of the box.
            // Swap in your own signingConfig before distributing it anywhere.
            signingConfig = signingConfigs.getByName("debug")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }
}

dependencies {
    implementation("androidx.recyclerview:recyclerview:1.3.2")
    // adb client, used by the phone remote to press keys on this device.
    implementation("dev.mobile:dadb:1.2.10")
    // QR code that opens the phone remote.
    implementation("com.google.zxing:core:3.5.3")
}
