plugins {
    alias(libs.plugins.kotlin.jvm) apply false
    alias(libs.plugins.kotlin.android) apply false
    alias(libs.plugins.kotlin.serialization) apply false
    alias(libs.plugins.android.library) apply false
    alias(libs.plugins.ktlint) apply false
    alias(libs.plugins.detekt) apply false
    alias(libs.plugins.maven.publish) apply false
}

// Android-facing modules (Android-safe: plain JARs, no ProcessBuilder/shell APIs, no
// java.desktop, compile-time kotlinx.serialization codegen only). See each module's
// build.gradle.kts for the per-module rationale required by plan §2.1.
val androidSafeModules = setOf(
    "veloxquant-core",
    "veloxquant-java",
    "veloxquant-memory",
    "veloxquant-optimize",
    "veloxquant-monitor",
)

// JVM-desktop-only modules: process management, hardware detection via shelling to
// sysctl/vm_stat. Never Android-safe by design (MLX itself is Mac-only).
val jvmDesktopOnlyModules = setOf(
    "veloxquant-runtime",
    "veloxquant-system",
)

subprojects {
    if (name == "veloxquant-bom") return@subprojects

    apply(plugin = "org.jlleitschuh.gradle.ktlint")
    apply(plugin = "io.gitlab.arturbosch.detekt")

    extensions.configure<io.gitlab.arturbosch.detekt.extensions.DetektExtension> {
        buildUponDefaultConfig = true
        allRules = false
        config.setFrom(rootProject.file("config/detekt/detekt.yml"))
    }

    // Android-safe modules target JVM 11 bytecode for both Kotlin and Java compilation, so
    // compileJava/compileKotlin never disagree (plan §6.4). Configured centrally here rather
    // than duplicated per module.
    if (name in androidSafeModules) {
        plugins.withId("org.jetbrains.kotlin.jvm") {
            extensions.configure<org.jetbrains.kotlin.gradle.dsl.KotlinJvmProjectExtension> {
                compilerOptions {
                    jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_11)
                }
            }
            tasks.withType<JavaCompile>().configureEach {
                sourceCompatibility = JavaVersion.VERSION_11.toString()
                targetCompatibility = JavaVersion.VERSION_11.toString()
            }
        }
    }
}
