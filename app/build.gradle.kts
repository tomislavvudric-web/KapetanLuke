plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "hr.kapetanluke.game"
    compileSdk = 35

    defaultConfig {
        applicationId = "hr.kapetanluke.game"
        minSdk = 26
        targetSdk = 35
        versionCode = 11
        versionName = "0.8.3"
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions { jvmTarget = "17" }
}
