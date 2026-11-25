plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.fayed.routerctl"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.fayed.routerctl"
        minSdk = 24
        targetSdk = 34
        versionCode = 4
        versionName = "3.0"
    }

    signingConfigs {
        create("release") {
            storeFile = rootProject.file("keystore/release.jks")
            storePassword = "routerctl2026"
            keyAlias = "routerctl"
            keyPassword = "routerctl2026"
        }
    }

    buildTypes {
        debug {
            isMinifyEnabled = false
        }
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
}
