/**
 * `.future()`-based Java interop for [AutoPilot] (build prompt Phase 3 item 7).
 *
 * **Lives in `veloxquant-runtime`, not `veloxquant-java`, deviating from the build prompt's
 * literal module naming — flagged explicitly:** `veloxquant-java` is Android-safe and depends
 * only on `veloxquant-core` (it is documented as "consumable from Android Java code too, not
 * JVM-only"). [AutoPilot] itself is JVM-desktop-only per its own KDoc, so a Java wrapper around
 * it must live in the same JVM-desktop-only module rather than pulling a `veloxquant-runtime`
 * dependency into the otherwise Android-safe `veloxquant-java`. A JVM-desktop Java caller
 * depends on `veloxquant-runtime` directly to reach this function, exactly as a Kotlin
 * JVM-desktop caller already must to reach [AutoPilot] itself.
 */
package io.github.rajveer43.veloxquant.runtime

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.future.future
import java.util.concurrent.CompletableFuture

/**
 * `.future()`-based wrapper over [AutoPilot.start] for Java/JVM-desktop callers. Completes
 * exceptionally with [io.github.rajveer43.veloxquant.core.VeloxQuantException.AutopilotWontFit]
 * when the workload won't fit and `request.force` wasn't set, mirroring [AutoPilot.start]'s
 * own exception-based contract.
 */
public fun AutoPilot.autoPilotStartAsync(request: AutoPilotRequest): CompletableFuture<AutoPilotSession> =
    CoroutineScope(Dispatchers.Default).future { start(request) }
