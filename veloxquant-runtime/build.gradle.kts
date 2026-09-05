plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.maven.publish)
}

// JVM-desktop-only: manages the veloxquant CLI subprocess lifecycle (ProcessBuilder, SIGINT
// shutdown) and one-shot CLI shell-outs (recommend/auto-config/methods/precompute/benchmark).
// Never Android-safe — there is no local process/shell access on Android, and MLX itself is
// Mac-only. See plan §2 module table.

group = "io.github.rajveer43"
version = "0.1.0-SNAPSHOT"

kotlin {
    jvmToolchain(17)
}

dependencies {
    implementation(project(":veloxquant-core"))
    implementation(project(":veloxquant-optimize"))
    implementation(project(":veloxquant-memory"))
    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.kotlinx.coroutines.jdk8)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.ktor.client.core)
    implementation(libs.ktor.client.okhttp)

    testImplementation(libs.junit.jupiter)
    testImplementation(libs.mockk)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.ktor.client.mock)
}

tasks.test {
    useJUnitPlatform()
}
