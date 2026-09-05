plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.android)
}

// Phase 0 item 5 (build-prompt): throwaway scratch module, //TODO Phase 7 — proves at build
// time that veloxquant-core/veloxquant-memory resolve cleanly as Android dependencies without
// pulling in veloxquant-runtime/veloxquant-system transitively. The real
// veloxquant-android-smoke (connectedAndroidTest instrumented suite) is built out in Phase 7;
// this module is not published and carries no feature code.

android {
    namespace = "io.github.rajveer43.veloxquant.androidsmoke"
    compileSdk = 35

    defaultConfig {
        minSdk = 24
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_11)
    }
}

dependencies {
    implementation(project(":veloxquant-core"))
    implementation(project(":veloxquant-memory"))
}
