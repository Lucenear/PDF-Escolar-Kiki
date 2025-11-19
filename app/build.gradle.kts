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
        versionCode = 7
        versionName = "1.1.0"
    }

    buildFeatures {
        viewBinding = true
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
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.appcompat:appcompat:1.6.1")
    implementation("androidx.constraintlayout:constraintlayout:2.1.4")
    implementation("com.github.mhiew:android-pdf-viewer:3.2.0-beta.1")
    implementation("com.google.android.material:material:1.13.0")

    implementation("androidx.activity:activity-ktx:1.8.0")
    implementation("androidx.fragment:fragment-ktx:1.6.1")
}
