plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.maven.publish)
}

// Android-safe module (plan §2.1). The per-request-metrics half (tokens/sec, TTFT) works
// identically on Android since it's derived from response timing already on the wire
// (depends only on veloxquant-core). The process-RSS-sampling half is JVM-desktop-only and
// lives behind an optional hook — Android builds simply never wire veloxquant-runtime in, so
// this module itself declares no dependency on veloxquant-runtime at the Gradle level.

group = "io.github.rajveer43"
version = "0.1.0-SNAPSHOT"

kotlin {
    jvmToolchain(17)
}

dependencies {
    implementation(project(":veloxquant-core"))
    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.kotlinx.serialization.json)

    testImplementation(libs.junit.jupiter)
    testImplementation(libs.mockk)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.ktor.client.mock)
}

tasks.test {
    useJUnitPlatform()
}
