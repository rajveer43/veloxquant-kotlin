/**
 * `veloxquant-optimize` — `recommendOffline()` (pure computation, Android-safe) and
 * `recommend()` (CLI-backed via `veloxquant-runtime`, JVM-desktop only, added Phase 3+).
 *
 * **Android-compatibility status: Android-safe at the Gradle level.** `recommend()`'s
 * shell-out dependency on `veloxquant-runtime` is added in Phase 3, not present today — an
 * Android build never resolves `veloxquant-runtime` transitively through this module in
 * Phase 0/1/2. See plan §2.
 */
package io.github.rajveer43.veloxquant.optimize

/** Placeholder pending Phase 2 (`recommendOffline`) and Phase 3 (`recommend`). */
internal object OptimizePlaceholder
