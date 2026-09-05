plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.maven.publish)
}

// Android-safe module (plan §2.1): pure closed-form arithmetic, zero I/O, zero dependency on
// veloxquant-runtime/veloxquant-system. This is the one deliberate exception to "never
// reimplement the Python engine" (plan §3.3) — justified specifically because it is
// offline/sub-millisecond math with no live-server dependency, unlike AutoPilot's fit-check.

group = "io.github.rajveer43"
version = "0.1.0-SNAPSHOT"

kotlin {
    jvmToolchain(17)
}

dependencies {
    // Deliberately zero dependency on veloxquant-system or veloxquant-runtime (plan §3.3:
    // "pure computation, no I/O at all"). Any hardware-derived input arrives as a plain data
    // parameter, not via a dependency on the module that shells out to detect it.
    implementation(libs.kotlinx.serialization.json)

    testImplementation(libs.junit.jupiter)
    testImplementation(libs.mockk)
}

tasks.test {
    useJUnitPlatform()
}
