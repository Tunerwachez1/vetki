plugins {
    id("com.android.library")
    kotlin("android")
}

android {
    namespace = "dev.vetki.i18n"
    compileSdk = 35
    defaultConfig { minSdk = 26 }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_21
        targetCompatibility = JavaVersion.VERSION_21
    }
}
