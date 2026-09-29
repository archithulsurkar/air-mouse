import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    // Phone and watch apps must share applicationId and signing key for the Wearable Data Layer.
    namespace = "com.archi.airmouse"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.archi.airmouse"
        minSdk = 30
        targetSdk = 35
        versionCode = 2
        versionName = "1.1"
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_17)
    }
}

dependencies {
    implementation(project(":protocol"))
    implementation("com.google.android.gms:play-services-wearable:18.2.0")
}
