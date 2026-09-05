# Changelog

All notable changes to this project will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.0.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html)
(pending maintainer confirmation of independent-semver policy — see the build
prompt's "final note on flagged judgment calls").

## [Unreleased]

### Added
- Repo bootstrap: Gradle multi-module layout, version catalog, ktlint/detekt, CI
  workflow skeletons.
- Phase 0: `veloxquant-bom`, `veloxquant-system`'s `SystemInfo.detect()` hardware
  detection, Android/JVM-desktop module boundary proof.
- Phase 1: `VeloxQuantClient` with `chat()`/`chatStream()`, the full
  `VeloxQuantException` sealed error hierarchy, 404-dispatch logic
  (GenerationFailed/UnexpectedRoute/MalformedErrorResponse), and
  `veloxquant-java`'s `chatAsync()`/`streamBlocking()` Java interop.
- Phase 2: `veloxquant-memory`'s `MemoryEstimator.estimate()` (pure-computation KV-cache
  byte accounting, with `accountingOnly`/`accountingNote` as real fields on
  `MemoryEstimate`) and `veloxquant-optimize`'s `Optimizer.recommendOffline()` (Android-safe
  offline recommendation, `Recommendation.isOfflineEstimate = true`). CLI-backed
  `Optimizer.recommend()` deferred to Phase 3, when `veloxquant-runtime` exists.

### Changed
- `Optimizer.recommendOffline()` takes a new `AvailableHardware` type instead of
  `veloxquant-system`'s `HardwareInfo` — a deliberate deviation from the implementation
  plan's §3.5 code sketch, documented in `Optimizer.kt`'s KDoc: depending on
  `veloxquant-system` (JVM-desktop only) from `veloxquant-optimize` would have broken the
  Android-safe module boundary established in Phase 0.
