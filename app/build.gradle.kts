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
        versionCode = 15
        versionName = "2.4.0"
    }

    packaging {
        jniLibs {
            useLegacyPackaging = false
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )


        }
        debug {
            isMinifyEnabled = false
        }
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

    implementation("com.github.chrisbanes:PhotoView:2.3.0")

    implementation("com.google.android.material:material:1.13.0")
    implementation("androidx.activity:activity-ktx:1.8.0")
    implementation("androidx.fragment:fragment-ktx:1.6.1")
    implementation("com.google.android.gms:play-services-mlkit-text-recognition:19.0.0")
}