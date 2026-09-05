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
- Phase 3: `veloxquant-runtime`'s `CliShellOut` (one-shot `recommend --json`/`auto-config
  --json` shell-outs, kebab-case flag spelling, `CliNotInstalled`/`CliCommandFailed` typed
  failures, tested via a mockable `ProcessRunner`/`PathResolver` boundary with zero real
  subprocess dependency); `Optimizer.recommend()` (CLI-backed, `isOfflineEstimate = false`);
  `AutoPilot` with both `tryStart()` (sealed `AutoPilotOutcome`) and `start()`
  (exception-based) entry points, serve-safe method-pool fallback, and
  `autoPilotStartAsync()` Java interop; `Conversation` in `veloxquant-core` with Go's
  stricter contract (a failed turn leaves `history` byte-for-byte unchanged, verified by
  dedicated tests covering both `send()` and `sendStream()`).
- Phase 4: `veloxquant-runtime`'s `VeloxQuantProcess` (full `serve` process lifecycle) —
  launch via `ProcessBuilder`, readiness resolved by racing a real `max_tokens: 1` priming
  chat request (to force `mlx_lm.server`'s lazy model load) against a `VELOXQUANT_READY`
  stdout handshake scan, `SIGINT`-based shutdown (`kill -2`, degrading to `destroy()` on
  Windows) with a forced-kill grace-period fallback, and a JVM shutdown hook that force-stops
  every live process to avoid orphans. Tested via a fake `LiveProcess` that lets a test
  control exactly when the ready line, stderr, and process exit become visible, including the
  direct regression test for Go's confirmed readiness bug (ready line arrives only after the
  priming request would have fired; `start()` still resolves). Also: `CliShellOut` gained
  `methods`/`profile`/`precompute`/`runKvCacheMicrobenchmark` shell-outs (snake_case flags for
  the latter two, matching the CLI's own inconsistency); `listMethods()`/`listLocalModels()`
  populate `veloxquant-core`'s new `CompressionMethod`/`TelemetryCoverage`/`LocalModel` types.

### Changed
- `Optimizer.recommendOffline()` takes a new `AvailableHardware` type instead of
  `veloxquant-system`'s `HardwareInfo` — a deliberate deviation from the implementation
  plan's §3.5 code sketch, documented in `Optimizer.kt`'s KDoc: depending on
  `veloxquant-system` (JVM-desktop only) from `veloxquant-optimize` would have broken the
  Android-safe module boundary established in Phase 0.
- `VeloxQuantException.AutopilotWontFit.recommendation: Any` (Phase 1's provisional
  placeholder) is resolved to real flattened fields (`method`, `bits`, `rationale`) rather
  than a reference to `veloxquant-optimize`'s `Recommendation` type — referencing it directly
  would create a circular module dependency (`veloxquant-optimize` depends on
  `veloxquant-core`, not the reverse).
- `VeloxQuantException.ServeStartupTimeout.config: Any` (Phase 1's provisional placeholder) is
  similarly resolved to flattened `model`/`port` fields rather than a `ServeConfig` reference,
  for the same circular-dependency reason (`veloxquant-runtime` depends on `veloxquant-core`).
- **AutoPilot lives in `veloxquant-runtime`, not `veloxquant-core`** — a deviation from plan
  §3.6's "likely in `veloxquant-core`" phrasing, documented in `AutoPilot.kt`'s KDoc.
  AutoPilot needs both `veloxquant-optimize` and a real CLI shell-out; `veloxquant-core`
  cannot depend on either without inverting the established one-way module dependency graph,
  and `veloxquant-optimize` must stay free of a `veloxquant-runtime` dependency to remain
  Android-safe at the Gradle level. `veloxquant-runtime` is the only module already permitted
  to depend on both.
- **`Optimizer.recommend()`'s CLI shell-out is reached through a new `RecommendBackend`
  interface, owned by `veloxquant-optimize`, rather than a direct `veloxquant-runtime`
  dependency** — mirrors `veloxquant-monitor`'s existing "no dependency at the Gradle level"
  pattern for its own optional process-RSS hook, and is what the implementation plan's module
  diagram calls "gated behind an injected optional dependency" for this exact path.
  `veloxquant-runtime`'s `CliRecommendBackend` implements the interface; Android builds never
  wire it in.
- `.future()`-based Java interop for `AutoPilot` (`autoPilotStartAsync()`) lives in
  `veloxquant-runtime`, not `veloxquant-java` — `veloxquant-java` is Android-safe and
  `AutoPilot` is not, so wrapping it there would have pulled a JVM-desktop-only dependency
  into an otherwise Android-safe module.
- **`listMethods()`/`listLocalModels()` are plain functions in `veloxquant-runtime`, not
  `suspend fun VeloxQuantClient.listMethods()`/`listLocalModels()` extensions in
  `veloxquant-core`** as plan §3.4's code sketch types them — flagged explicitly in
  `MethodRegistryLookup.kt`'s KDoc. `listMethods()` is CLI shell-out-backed (no confirmed HTTP
  route) and `listLocalModels()` is an HF cache filesystem scan; both need JVM-desktop-only
  capabilities an extension on `VeloxQuantClient` living in `veloxquant-core` cannot have
  without inverting the module dependency graph, the same shape of issue `AutoPilot`'s
  placement resolved in Phase 3. The `CompressionMethod`/`TelemetryCoverage`/`LocalModel`
  *types* themselves stay in `veloxquant-core` (pure data, Android-safe); only the two
  populating functions moved.
- `MethodCliPayload`'s wire schema (`methods --json`) and `KvCacheMicrobenchmarkResult`'s
  column parsing (`benchmark`'s plain stdout table) are both flagged as best-effort mappings
  in their own KDoc — neither the implementation plan nor the build prompt records these two
  subcommands' exact output shape (unlike `recommend`/`auto-config`, which investigation §2.1
  documents field-for-field), so both should be verified against a real `veloxquant` CLI run
  before being relied on in production.
