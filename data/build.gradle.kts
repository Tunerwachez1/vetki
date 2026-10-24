plugins {
    id("com.android.library")
    kotlin("android")
    id("com.google.devtools.ksp")
    id("androidx.room")
}

android {
    namespace = "dev.vetki.data"
    compileSdk = 35
    defaultConfig { minSdk = 26 }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_21
        targetCompatibility = JavaVersion.VERSION_21
    }
}

room { schemaDirectory("$projectDir/schemas") }

dependencies {
    implementation(project(":i18n"))
    api(project(":core"))
    implementation("androidx.core:core-ktx:1.16.0")
    api("androidx.room:room-runtime:2.7.2")
    api("androidx.room:room-ktx:2.7.2")
    ksp("androidx.room:room-compiler:2.7.2")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.10.2")
}
