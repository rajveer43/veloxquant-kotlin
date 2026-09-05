# veloxquant-kotlin

Kotlin/JVM SDK for [VeloxQuant](https://github.com/rajveer43/veloxquant-mlx) — the
Apple-Silicon-only MLX KV-cache compression engine. Sibling to the TypeScript, Go, and Rust
client SDKs.

**Status: pre-alpha, under active phased construction.** Not yet published to Maven Central.
See `CHANGELOG.md` for what's landed so far.

## Modules

| Module | Android-safe? | Purpose |
|---|---|---|
| `veloxquant-core` | Yes | Chat/streaming HTTP client, error model, structured output, embeddings, conversations |
| `veloxquant-java` | Yes | `.future()`/callback-based Java interop wrappers |
| `veloxquant-memory` | Yes | Pure-computation KV-cache memory estimation |
| `veloxquant-optimize` | Yes* | Offline + CLI-backed recommend/AutoPilot fit-checks |
| `veloxquant-monitor` | Yes* | Per-request metrics + optional process-RSS sampling |
| `veloxquant-system` | No (JVM-desktop only) | Local hardware detection via `sysctl`/`vm_stat` |
| `veloxquant-runtime` | No (JVM-desktop only) | `veloxquant` CLI process lifecycle + shell-outs |
| `veloxquant-bom` | — | Version-alignment BOM |

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
