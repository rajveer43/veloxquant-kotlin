/**
 * `veloxquant-monitor` — `VeloxQuantClient.monitor()` (`Flow<Metrics>`), the coroutines
 * analogue of Go's `Subscribe` callback.
 *
 * **Android-compatibility status: Android-safe.** The per-request-metrics half (tokens/sec,
 * TTFT) depends only on `veloxquant-core` and works identically on Android; the
 * process-RSS-sampling half is JVM-desktop-only and lives behind an optional hook that Android
 * builds never wire in — this module declares no dependency on `veloxquant-runtime`. See plan
 * §2, §3.11.
 */
package io.github.rajveer43.veloxquant.monitor

/** Placeholder pending Phase 6 (`monitor()`, `Metrics`). */
internal object MonitorPlaceholder
