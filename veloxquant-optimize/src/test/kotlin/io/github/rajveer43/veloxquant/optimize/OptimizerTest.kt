package io.github.rajveer43.veloxquant.optimize

import io.github.rajveer43.veloxquant.memory.WorkloadSpec
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class OptimizerTest {
    private val workload = WorkloadSpec(seqLen = 4096, nLayers = 32, nKvHeads = 8, headDim = 128)
    private val ampleHardware = AvailableHardware(availableMemoryBytes = Long.MAX_VALUE / 2)

    @Test
    fun `recommendOffline is always marked as an offline estimate`() {
        val recommendation = Optimizer.recommendOffline(workload, ampleHardware, OptimizationGoal.EVERYDAY)

        assertTrue(recommendation.isOfflineEstimate)
    }

    @Test
    fun `maxKeyAccounting goal selects a lower bit width than bestQuality`() {
        val maxKeyAccounting =
            Optimizer.recommendOffline(workload, ampleHardware, OptimizationGoal.MAX_KEY_ACCOUNTING)
        val bestQuality = Optimizer.recommendOffline(workload, ampleHardware, OptimizationGoal.BEST_QUALITY)

        assertTrue(maxKeyAccounting.bits < bestQuality.bits)
    }

    @Test
    fun `residentSavingsLikely and warnings reflect whether the estimate fits in available memory`() {
        val fits = Optimizer.recommendOffline(workload, ampleHardware, OptimizationGoal.EVERYDAY)
        assertTrue(fits.residentSavingsLikely)
        assertTrue(fits.warnings.isEmpty())

        val tinyHardware = AvailableHardware(availableMemoryBytes = 1)
        val doesNotFit = Optimizer.recommendOffline(workload, tinyHardware, OptimizationGoal.EVERYDAY)
        assertFalse(doesNotFit.residentSavingsLikely)
        assertTrue(doesNotFit.warnings.isNotEmpty())
    }

    @Test
    fun `kv byte counts match MemoryEstimator's output for the same workload and bit width`() {
        val recommendation = Optimizer.recommendOffline(workload, ampleHardware, OptimizationGoal.EVERYDAY)

        val estimate =
            io.github.rajveer43.veloxquant.memory.MemoryEstimator.estimate(
                workload,
                io.github.rajveer43.veloxquant.memory.CompressionConfig(
                    method = recommendation.method,
                    bits = recommendation.bits,
                ),
            )

        assertEquals(estimate.kvFp16Bytes, recommendation.kvFp16Bytes)
        assertEquals(estimate.kvCompressedEstimateBytes, recommendation.kvCompressedEstimateBytes)
    }

    @Test
    fun `every OptimizationGoal produces a recommendation without throwing`() {
        for (goal in OptimizationGoal.entries) {
            val recommendation = Optimizer.recommendOffline(workload, ampleHardware, goal)
            assertTrue(recommendation.bits > 0)
        }
    }

    @Test
    fun `recommend delegates to the injected backend and marks isOfflineEstimate false`() {
        val backend =
            RecommendBackend { _, _ ->
                RecommendationCliResult(
                    method = "turboquant_rvq",
                    bits = 4,
                    knobs = emptyMap(),
                    keyAccountingRatio = 0.25,
                    residentSavingsLikely = true,
                    kvFp16Bytes = 1_000L,
                    kvCompressedEstimateBytes = 250L,
                    warnings = emptyList(),
                    rationale = "server-backed pick",
                )
            }

        val recommendation =
            Optimizer.recommend(RecommendRequest(workload, OptimizationGoal.EVERYDAY), backend)

        assertFalse(recommendation.isOfflineEstimate)
        assertEquals("turboquant_rvq", recommendation.method)
        assertEquals("server-backed pick", recommendation.rationale)
    }
}
