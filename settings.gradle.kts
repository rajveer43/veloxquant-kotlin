rootProject.name = "veloxquant-kotlin"

pluginManagement {
    repositories {
        google()
        gradlePluginPortal()
        mavenCentral()
    }
}

dependencyResolutionManagement {
    repositories {
        google()
        mavenCentral()
    }
}

include(
    ":veloxquant-bom",
    ":veloxquant-core",
    ":veloxquant-java",
    ":veloxquant-memory",
    ":veloxquant-optimize",
    ":veloxquant-runtime",
    ":veloxquant-system",
    ":veloxquant-monitor",
    ":veloxquant-android-smoke",
)

// veloxquant-langchain4j intentionally not included yet — Phase 8 stretch, do not add speculatively.
