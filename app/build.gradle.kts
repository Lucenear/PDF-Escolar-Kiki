import org.gradle.api.JavaVersion

plugins {
    id("com.android.application")
    kotlin("android")
}

android {
    namespace = "com.kikipdf"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.kikipdf"
        minSdk = 28
        targetSdk = 35
        versionCode = 2
        versionName = "0.3"
    }

    buildFeatures {
        viewBinding = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_1_8
        targetCompatibility = JavaVersion.VERSION_1_8
    }

    kotlinOptions {
        jvmTarget = "1.8"
    }
}

dependencies {
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.appcompat:appcompat:1.6.1")
    implementation("androidx.constraintlayout:constraintlayout:2.1.4")
    implementation("com.github.mhiew:android-pdf-viewer:3.2.0-beta.1")
    implementation("com.google.android.material:material:1.13.0")
}
