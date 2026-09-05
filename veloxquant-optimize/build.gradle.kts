plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.maven.publish)
}

// Android-safe module (plan §2.1). recommendOffline() (Phase 2) depends only on
// veloxquant-memory's pure math. recommend() (Phase 3+, CLI shell-out via veloxquant-runtime)
// is JVM-desktop-only in practice, but this module itself stays Android-safe at the Gradle
// level — veloxquant-runtime is a Phase 3 dependency addition, not present in Phase 0/1/2.

group = "io.github.rajveer43"
version = "0.1.0-SNAPSHOT"

kotlin {
    jvmToolchain(17)
}

dependencies {
    implementation(project(":veloxquant-core"))
    implementation(project(":veloxquant-memory"))
    implementation(libs.kotlinx.serialization.json)

    testImplementation(libs.junit.jupiter)
    testImplementation(libs.mockk)
}

tasks.test {
    useJUnitPlatform()
}
