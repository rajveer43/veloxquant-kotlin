/**
 * `veloxquant-memory` — pure-computation KV-cache memory estimation (`MemoryEstimator`). No
 * I/O, no dependency on `veloxquant-runtime`/`veloxquant-system`.
 *
 * **Android-compatibility status: Android-safe.** Plain JAR; zero-dependency pure math. This
 * is the one deliberate exception to "never reimplement the Python engine" (plan §3.3),
 * justified because it's closed-form arithmetic with no live-server dependency. See plan §2.
 */
package io.github.rajveer43.veloxquant.memory

/** Placeholder pending Phase 2 (`MemoryEstimator.estimate`). */
internal object MemoryPlaceholder
