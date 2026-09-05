/**
 * Offline (Android-safe) and CLI-backed (JVM-desktop only, Phase 3+) recommendation entry
 * points (plan §3.5). `recommendOffline()` is a `veloxquant-memory`-backed approximation, never
 * a silent substitute for the CLI-backed `recommend()` — see [Recommendation.isOfflineEstimate].
 */
package io.github.rajveer43.veloxquant.optimize

import io.github.rajveer43.veloxquant.memory.CompressionConfig
import io.github.rajveer43.veloxquant.memory.MemoryEstimator
import io.github.rajveer43.veloxquant.memory.WorkloadSpec

/**
 * The subset of `veloxquant-system`'s `HardwareInfo` that [Optimizer.recommendOffline] needs.
 *
 * **Deviation from plan §3.5's code sketch, flagged explicitly rather than silently
 * resolved:** §3.5 types `recommendOffline`'s `hardware` parameter as `HardwareInfo` directly,
 * but that type lives in `veloxquant-system`, which plan §3.2 itself documents as JVM-desktop
 * only and explicitly absent from any Android-safe module — an Android app is said to obtain
 * the *paired Mac's* hardware facts over HTTP or from AutoPilot's plan, "never by local
 * detection." Taking a real dependency on `veloxquant-system` here would make all of
 * `veloxquant-optimize` JVM-desktop-only, contradicting both its Android-safe classification
 * (plan §2.1) and Phase 0's already-built/verified module boundary. This local, dependency-free
 * type carries the two fields the offline estimate actually needs; a JVM-desktop caller holding
 * a real `HardwareInfo` constructs one inline (`AvailableHardware(hw.availableMemoryBytes,
 * hw.chip?.name)`) with no loss of information relevant to this function.
 */
public data class AvailableHardware(
    val availableMemoryBytes: Long,
    val chipName: String? = null,
)

/**
 * Optimization objective, matching the real `veloxquant recommend --goal` CLI values
 * (investigation §2.1's `recommend` row) verbatim so a caller can move between
 * [Optimizer.recommendOffline] and the Phase 3+ CLI-backed `recommend()` without remapping.
 */
public enum class OptimizationGoal {
    EVERYDAY,
    MAX_KEY_ACCOUNTING,
    MAX_CONTEXT,
    BEST_QUALITY,
    CONSTANT_MEMORY,
}

/**
 * A compression method + configuration recommendation. [isOfflineEstimate] distinguishes
 * [Optimizer.recommendOffline]'s pure-computation output (`true`) from the real,
 * server-ruleset-backed `recommend()` (Phase 3+, `isOfflineEstimate = false`) — callers must
 * never be left unable to tell which path produced a given [Recommendation].
 *
 * Field names mirror `recommend --json`'s `recommendation` object (investigation §2.1) so this
 * type stays a faithful Kotlin projection of the real CLI shape once Phase 3 wires it up.
 */
public data class Recommendation(
    val method: String,
    val bits: Int,
    val knobs: Map<String, String>,
    val keyAccountingRatio: Double,
    val residentSavingsLikely: Boolean,
    val kvFp16Bytes: Long,
    val kvCompressedEstimateBytes: Long,
    val warnings: List<String>,
    val rationale: String,
    val isOfflineEstimate: Boolean,
)

/**
 * Offline and (Phase 3+) CLI-backed compression recommendation.
 *
 * Android-safe at the Gradle level (plan §2.1): [recommendOffline] has zero dependency on
 * `veloxquant-runtime`. The CLI-shell-out `recommend()` is deferred to Phase 3, when
 * `veloxquant-runtime` exists — it is deliberately not stubbed as a function that throws here,
 * since an absent function is a clearer "not yet available" signal at compile time than a
 * runtime `UnsupportedPlatform` exception would be for a capability that simply hasn't shipped.
 */
public object Optimizer {
    private const val EVERYDAY_BITS = 4
    private const val MAX_KEY_ACCOUNTING_BITS = 2
    private const val MAX_CONTEXT_BITS = 2
    private const val BEST_QUALITY_BITS = 8
    private const val CONSTANT_MEMORY_BITS = 3
    private const val DEFAULT_METHOD = "turboquant_rvq"
    private const val LOW_BITS_METHOD = "kivi"

    /**
     * Pure-computation fallback recommendation, usable fully offline (Android-safe, no network
     * or process access) — see plan §3.5. Picks a bit width from [goal] and defers the actual
     * byte-count math to [MemoryEstimator], so the two never drift on the arithmetic itself.
     */
    public fun recommendOffline(
        workload: WorkloadSpec,
        hardware: AvailableHardware,
        goal: OptimizationGoal,
    ): Recommendation {
        val bits =
            when (goal) {
                OptimizationGoal.EVERYDAY -> EVERYDAY_BITS
                OptimizationGoal.MAX_KEY_ACCOUNTING -> MAX_KEY_ACCOUNTING_BITS
                OptimizationGoal.MAX_CONTEXT -> MAX_CONTEXT_BITS
                OptimizationGoal.BEST_QUALITY -> BEST_QUALITY_BITS
                OptimizationGoal.CONSTANT_MEMORY -> CONSTANT_MEMORY_BITS
            }
        val method = if (bits <= MAX_KEY_ACCOUNTING_BITS) LOW_BITS_METHOD else DEFAULT_METHOD

        val estimate = MemoryEstimator.estimate(workload, CompressionConfig(method = method, bits = bits))
        val fits = estimate.fitsInBytes(hardware.availableMemoryBytes)
        val warnings =
            if (fits) {
                emptyList()
            } else {
                listOf(
                    "Estimated compressed KV-cache (${estimate.kvCompressedEstimateBytes} bytes) plus " +
                        "runtime overhead will not fit in available memory (${hardware.availableMemoryBytes} " +
                        "bytes). This is an offline estimate and may not reflect the real server's ruleset.",
                )
            }

        return Recommendation(
            method = method,
            bits = bits,
            knobs = emptyMap(),
            keyAccountingRatio = bits.toDouble() / Byte.SIZE_BITS,
            residentSavingsLikely = fits,
            kvFp16Bytes = estimate.kvFp16Bytes,
            kvCompressedEstimateBytes = estimate.kvCompressedEstimateBytes,
            warnings = warnings,
            rationale =
                "Offline estimate for goal $goal: selected $method at $bits bits based on a static " +
                    "bit-width table, not the live server's ruleset.",
            isOfflineEstimate = true,
        )
    }
}
