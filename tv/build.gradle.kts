import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    // Same applicationId as the phone app so both share one store listing; the TV talks to the
    // watch over the LAN, not the Data Layer, so it does not need the same signing key.
    namespace = "com.archi.airmouse.tv"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.archi.airmouse"
        minSdk = 26
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
    implementation(project(":receiver"))
}
