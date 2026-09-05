package io.github.rajveer43.veloxquant.runtime

import io.github.rajveer43.veloxquant.core.VeloxQuantClient
import io.github.rajveer43.veloxquant.core.VeloxQuantException
import io.github.rajveer43.veloxquant.memory.WorkloadSpec
import io.github.rajveer43.veloxquant.optimize.OptimizationGoal.EVERYDAY
import io.github.rajveer43.veloxquant.optimize.RecommendBackend
import io.github.rajveer43.veloxquant.optimize.RecommendationCliResult
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respondOk
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

private val workload = WorkloadSpec(seqLen = 4096, nLayers = 32, nKvHeads = 8, headDim = 128)
private val client = VeloxQuantClient.create(baseUrl = "http://127.0.0.1:8000", engine = MockEngine { respondOk() })

private fun noOpCliShellOut(stdout: String = ""): CliShellOut =
    CliShellOut(processRunner = { FakeProcess(stdout = stdout) }, pathResolver = { true })

private fun fittingResult(method: String = "turboquant_rvq") =
    RecommendationCliResult(
        method = method,
        bits = 4,
        knobs = emptyMap(),
        keyAccountingRatio = 0.25,
        residentSavingsLikely = true,
        kvFp16Bytes = 1_000L,
        kvCompressedEstimateBytes = 250L,
        warnings = emptyList(),
        rationale = "fits comfortably",
    )

private fun wontFitResult() =
    RecommendationCliResult(
        method = "turboquant_rvq",
        bits = 4,
        knobs = emptyMap(),
        keyAccountingRatio = 0.25,
        residentSavingsLikely = false,
        kvFp16Bytes = 1_000L,
        kvCompressedEstimateBytes = 900L,
        warnings = listOf("estimated usage will not fit in available memory"),
        rationale = "tight fit",
    )

private fun request(force: Boolean = false) =
    AutoPilotRequest(modelClass = "7b", workload = workload, goal = EVERYDAY, force = force)

class AutoPilotTest {
    @Test
    fun `tryStart returns Started when the recommendation fits`() {
        val autoPilot = AutoPilot(client, RecommendBackend { _, _ -> fittingResult() }, noOpCliShellOut())

        val outcome = autoPilot.tryStart(request())

        assertTrue(outcome is AutoPilotOutcome.Started)
    }

    @Test
    fun `tryStart returns WontFit when residentSavingsLikely is false`() {
        val autoPilot = AutoPilot(client, RecommendBackend { _, _ -> wontFitResult() }, noOpCliShellOut())

        val outcome = autoPilot.tryStart(request())

        assertTrue(outcome is AutoPilotOutcome.WontFit)
        val error = (outcome as AutoPilotOutcome.WontFit).error
        assertEquals(wontFitResult().warnings, error.warnings)
    }

    @Test
    fun `tryStart returns Started even when it wouldn't fit if force is true`() {
        val autoPilot = AutoPilot(client, RecommendBackend { _, _ -> wontFitResult() }, noOpCliShellOut())

        val outcome = autoPilot.tryStart(request(force = true))

        assertTrue(outcome is AutoPilotOutcome.Started)
    }

    @Test
    fun `start throws AutopilotWontFit with the same fields tryStart's WontFit carries`() {
        val autoPilot = AutoPilot(client, RecommendBackend { _, _ -> wontFitResult() }, noOpCliShellOut())

        val outcome = autoPilot.tryStart(request()) as AutoPilotOutcome.WontFit
        val thrown =
            assertThrows(VeloxQuantException.AutopilotWontFit::class.java) {
                autoPilot.start(request())
            }

        assertEquals(outcome.error.warnings, thrown.warnings)
        assertEquals(outcome.error.method, thrown.method)
        assertEquals(outcome.error.bits, thrown.bits)
    }

    @Test
    fun `start returns a session when the recommendation fits`() {
        val autoPilot = AutoPilot(client, RecommendBackend { _, _ -> fittingResult() }, noOpCliShellOut())

        val session = autoPilot.start(request())

        assertEquals("turboquant_rvq", session.recommendation.method)
        assertFalse(session.recommendation.isOfflineEstimate)
    }

    @Test
    fun `falls back to auto-config's serve-safe pool when the recommended method isn't servable`() {
        val autoConfigStdout =
            """
            {"workload":{},"hardware":{},"config":{"method":"kivi","bits":2,"knobs":{}},
            "reason":"fell back to serve-safe pool"}
            """.trimIndent()
        val autoPilot =
            AutoPilot(
                client,
                RecommendBackend { _, _ -> fittingResult(method = "not_a_servable_method") },
                noOpCliShellOut(stdout = autoConfigStdout),
            )

        val session = autoPilot.start(request())

        assertEquals("kivi", session.recommendation.method)
        assertEquals("fell back to serve-safe pool", session.recommendation.rationale)
    }
}
