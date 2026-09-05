plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.maven.publish)
}

// Android-safe module (plan §2.1): no java.desktop, no ProcessBuilder/shell APIs, no
// reflection-heavy serialization config — compile-time kotlinx.serialization codegen only.
// Ships as a plain JAR; Android apps consume it directly with no AAR/manifest/resources.
// minSdk 24 is the effective floor for consumers (plan §6.4) — enforced by JVM target 11
// bytecode, not by an Android Gradle variant, since this module has no Android dependency.

group = "io.github.rajveer43"
version = "0.1.0-SNAPSHOT"

kotlin {
    jvmToolchain(17)
}

dependencies {
    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.ktor.client.core)
    implementation(libs.ktor.client.okhttp)
    implementation(libs.ktor.client.content.negotiation)
    implementation(libs.ktor.serialization.kotlinx.json)

    testImplementation(libs.junit.jupiter)
    testImplementation(libs.mockk)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.ktor.client.mock)
}

tasks.test {
    useJUnitPlatform()
}
