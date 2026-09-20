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
        versionCode = 12
        versionName = "0.8.4"
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions { jvmTarget = "17" }
}
