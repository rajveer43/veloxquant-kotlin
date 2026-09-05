# veloxquant-kotlin

Kotlin/JVM SDK for [VeloxQuant](https://github.com/rajveer43/veloxquant-mlx) — the
Apple-Silicon-only MLX KV-cache compression engine. Sibling to the TypeScript, Go, and Rust
client SDKs.

**Status: pre-alpha, under active phased construction.** Published via [JitPack](https://jitpack.io/)
today; not yet on Maven Central. See `CHANGELOG.md` for what's landed so far.

## Installation

Add the JitPack repository, then depend on individual modules by tag:

```kotlin
// settings.gradle.kts
dependencyResolutionManagement {
    repositories {
        google()
        mavenCentral()
        maven { url = uri("https://jitpack.io") }
    }
}
```

```kotlin
// app/build.gradle.kts
dependencies {
    implementation("com.github.rajveer43.veloxquant-kotlin:veloxquant-core:v0.1.0-alpha")
    implementation("com.github.rajveer43.veloxquant-kotlin:veloxquant-memory:v0.1.0-alpha")
    // add other modules (veloxquant-java, veloxquant-optimize, veloxquant-runtime,
    // veloxquant-system, veloxquant-monitor) the same way, as needed.
}
```

Replace `v0.1.0-alpha` with any tag, branch name, or commit hash from this repo — JitPack
builds it on first request (the first resolve after a new tag takes a minute or two while
JitPack builds; subsequent resolves are cached). Check build status for a given tag at
`https://jitpack.io/#rajveer43/veloxquant-kotlin`.

Android consumers should only add `veloxquant-core`/`veloxquant-java`/`veloxquant-memory`/
`veloxquant-optimize` (Android-safe modules — see the table below); `veloxquant-runtime` and
`veloxquant-system` are JVM-desktop only and will fail to resolve meaningfully on Android
even though JitPack will happily build them.

## Modules

| Module | Android-safe? | Purpose | Status |
|---|---|---|---|
| `veloxquant-core` | Yes | Chat/streaming HTTP client, error model, structured output, embeddings, conversations | Chat + streaming + error model shipped (Phase 1). `Conversation` shipped (Phase 3). Structured output/embeddings pending (Phase 5). |
| `veloxquant-java` | Yes | `.future()`/callback-based Java interop wrappers | `chatAsync()`/`streamBlocking()` shipped (Phase 1). |
| `veloxquant-memory` | Yes | Pure-computation KV-cache memory estimation | `MemoryEstimator.estimate()` shipped (Phase 2). |
| `veloxquant-optimize` | Yes* | Offline + CLI-backed recommend/AutoPilot fit-checks | `recommendOffline()` (Phase 2) and CLI-backed `recommend()` (Phase 3, via an injected `RecommendBackend`) shipped. |
| `veloxquant-monitor` | Yes* | Per-request metrics + optional process-RSS sampling | Pending (Phase 6). |
| `veloxquant-system` | No (JVM-desktop only) | Local hardware detection via `sysctl`/`vm_stat` | `SystemInfo.detect()` shipped (Phase 0). |
| `veloxquant-runtime` | No (JVM-desktop only) | `veloxquant` CLI process lifecycle + shell-outs + AutoPilot | `recommend`/`auto-config` shell-outs and `AutoPilot` shipped (Phase 3). `serve` process lifecycle pending (Phase 4). |
| `veloxquant-bom` | — | Version-alignment BOM | Shipped (Phase 0). |

\* Android-safe at the Gradle level; features requiring `veloxquant-runtime` are simply not
wired in on Android builds.

## Requirements

- JDK 17 to build. Android-facing modules target JVM 11 bytecode / minSdk 24.
- The `veloxquant` CLI installed and on `PATH` for any feature that shells out to it
  (AutoPilot, `serve` process management) — JVM-desktop only, never required for
  `veloxquant-core`/`veloxquant-memory` consumers.

## Building

```sh
./gradlew build
```

## License

MIT — see `LICENSE`.
