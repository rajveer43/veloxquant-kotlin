/**
 * Adapts [CliShellOut] to [RecommendBackend] so `veloxquant-optimize`'s `Optimizer.recommend()`
 * can be driven without a Gradle dependency on this module (see `RecommendBackend`'s KDoc).
 */
package io.github.rajveer43.veloxquant.runtime

import io.github.rajveer43.veloxquant.memory.WorkloadSpec
import io.github.rajveer43.veloxquant.optimize.OptimizationGoal
import io.github.rajveer43.veloxquant.optimize.RecommendBackend
import io.github.rajveer43.veloxquant.optimize.RecommendationCliResult

private const val MB_TO_BYTES = 1024L * 1024L

/** [RecommendBackend] implementation that shells to `veloxquant recommend --json` via [cliShellOut]. */
public class CliRecommendBackend(private val cliShellOut: CliShellOut = CliShellOut()) : RecommendBackend {
    override fun recommend(workload: WorkloadSpec, goal: OptimizationGoal): RecommendationCliResult {
        val cliArgs =
            WorkloadCliArgs(
                seqLen = workload.seqLen,
                nLayers = workload.nLayers,
                nKvHeads = workload.nKvHeads,
                headDim = workload.headDim,
                batchSize = workload.batchSize,
            )
        val payload = cliShellOut.recommend(cliArgs, goal.name.lowercase())
        return RecommendationCliResult(
            method = payload.method,
            bits = bitsFromMethodKnobs(payload),
            knobs = payload.knobs,
            keyAccountingRatio = payload.keyAccountingRatio,
            residentSavingsLikely = payload.residentSavingsLikely,
            kvFp16Bytes = (payload.kvFp16Mb * MB_TO_BYTES).toLong(),
            kvCompressedEstimateBytes = (payload.kvCompressedMbEstimate * MB_TO_BYTES).toLong(),
            warnings = payload.warnings,
            rationale = payload.rationale,
        )
    }

    /**
     * `recommend --json`'s payload does not carry a top-level `bits` field (investigation
     * §2.1) — it is implied by `method`/`knobs`. Falls back to 0 (unknown) rather than
     * guessing, since inventing a bit width here would be exactly the kind of silent
     * drift-risk this module's shell-out design exists to avoid.
     */
    private fun bitsFromMethodKnobs(payload: RecommendationCliPayload): Int =
        payload.knobs["bits"]?.toIntOrNull() ?: 0
}
