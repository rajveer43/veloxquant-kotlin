package io.github.rajveer43.veloxquant.runtime

import io.github.rajveer43.veloxquant.core.VeloxQuantException
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test

private val workload = WorkloadCliArgs(seqLen = 4096, nLayers = 32, nKvHeads = 8, headDim = 128)

class CliShellOutTest {
    @Test
    fun `recommend parses the recommendation object out of recommend --json's stdout`() {
        val stdout =
            """
            {"request":{},"recommendation":{"method":"turboquant_rvq","knobs":{},
            "key_accounting_ratio":0.25,"resident_savings_likely":true,"kv_fp16_mb":512.0,
            "kv_compressed_mb_estimate":128.0,"warnings":[],"rationale":"fits comfortably"}}
            """.trimIndent()
        val shellOut = CliShellOut(processRunner = { FakeProcess(stdout = stdout) }, pathResolver = { true })

        val recommendation = shellOut.recommend(workload, goal = "everyday")

        assertEquals("turboquant_rvq", recommendation.method)
        assertEquals(0.25, recommendation.keyAccountingRatio)
        assertEquals(true, recommendation.residentSavingsLikely)
    }

    @Test
    fun `recommend uses kebab-case flag spelling, not snake_case`() {
        var capturedCommand: List<String>? = null
        val stdout =
            """
            {"request":{},"recommendation":{"method":"kivi","key_accounting_ratio":0.125,
            "resident_savings_likely":true,"kv_fp16_mb":1.0,"kv_compressed_mb_estimate":1.0,
            "rationale":"r"}}
            """.trimIndent()
        val shellOut =
            CliShellOut(
                processRunner = {
                    capturedCommand = it
                    FakeProcess(stdout = stdout)
                },
                pathResolver = { true },
            )

        shellOut.recommend(workload, goal = "max_key_accounting")

        val command = requireNotNull(capturedCommand)
        assertEquals(true, command.contains("--seq-len"))
        assertEquals(true, command.contains("--head-dim"))
        assertEquals(false, command.any { it.contains("seq_len") || it.contains("head_dim") })
    }

    @Test
    fun `throws CliNotInstalled when the binary is not resolvable on PATH`() {
        val shellOut = CliShellOut(processRunner = { FakeProcess(stdout = "") }, pathResolver = { false })

        assertThrows(VeloxQuantException.CliNotInstalled::class.java) {
            shellOut.recommend(workload, goal = "everyday")
        }
    }

    @Test
    fun `throws CliCommandFailed when the subcommand exits non-zero`() {
        val shellOut =
            CliShellOut(
                processRunner = { FakeProcess(stdout = "", stderr = "bad workload", exitCode = 2) },
                pathResolver = { true },
            )

        val thrown =
            assertThrows(VeloxQuantException.CliCommandFailed::class.java) {
                shellOut.recommend(workload, goal = "everyday")
            }
        assertEquals(2, thrown.exitCode)
        assertEquals("bad workload", thrown.stderr)
    }

    @Test
    fun `autoConfig parses workload, hardware, config, and reason`() {
        val stdout =
            """
            {"workload":{},"hardware":{},"config":{"method":"gear","bits":4,"knobs":{}},
            "reason":"serve-safe fallback"}
            """.trimIndent()
        val shellOut = CliShellOut(processRunner = { FakeProcess(stdout = stdout) }, pathResolver = { true })

        val response = shellOut.autoConfig(workload)

        assertEquals("gear", response.config.method)
        assertEquals(4, response.config.bits)
        assertEquals("serve-safe fallback", response.reason)
    }

    @Test
    fun `autoConfig uses kebab-case flag spelling`() {
        var capturedCommand: List<String>? = null
        val stdout =
            """{"workload":{},"hardware":{},"config":{"method":"gear","bits":4},"reason":"r"}"""
        val shellOut =
            CliShellOut(
                processRunner = {
                    capturedCommand = it
                    FakeProcess(stdout = stdout)
                },
                pathResolver = { true },
            )

        shellOut.autoConfig(workload)

        val command = requireNotNull(capturedCommand)
        assertEquals(true, command.contains("--n-kv-heads"))
        assertEquals(false, command.any { it.contains("n_kv_heads") })
    }

    @Test
    fun `methods parses a list of compression methods`() {
        val stdout =
            """
            [{"name":"turboquant_rvq","family":"rvq","servable":true,"telemetry_coverage":"FULL"},
            {"name":"kivi","family":"kivi","servable":true,"telemetry_coverage":"KEYS_ONLY"}]
            """.trimIndent()
        val shellOut = CliShellOut(processRunner = { FakeProcess(stdout = stdout) }, pathResolver = { true })

        val methods = shellOut.methods()

        assertEquals(2, methods.size)
        assertEquals("turboquant_rvq", methods[0].name)
        assertEquals("KEYS_ONLY", methods[1].telemetryCoverage)
    }

    @Test
    fun `profile returns the raw parsed JSON object`() {
        val stdout = """{"chip":"M3 Max","cores":16}"""
        val shellOut = CliShellOut(processRunner = { FakeProcess(stdout = stdout) }, pathResolver = { true })

        val profile = shellOut.profile()

        assertEquals("M3 Max", profile.raw["chip"]?.toString()?.trim('"'))
    }

    @Test
    fun `precompute uses snake_case flag spelling, not kebab-case`() {
        var capturedCommand: List<String>? = null
        val shellOut =
            CliShellOut(
                processRunner = {
                    capturedCommand = it
                    FakeProcess(stdout = "")
                },
                pathResolver = { true },
            )

        shellOut.precompute(workload)

        val command = requireNotNull(capturedCommand)
        assertEquals(true, command.contains("--seq_len"))
        assertEquals(true, command.contains("--head_dim"))
        assertEquals(false, command.any { it.contains("--seq-len") || it.contains("--head-dim") })
    }

    @Test
    fun `precompute throws CliCommandFailed on non-zero exit`() {
        val shellOut =
            CliShellOut(
                processRunner = { FakeProcess(stdout = "", stderr = "disk full", exitCode = 1) },
                pathResolver = { true },
            )

        assertThrows(VeloxQuantException.CliCommandFailed::class.java) {
            shellOut.precompute(workload)
        }
    }

    @Test
    fun `runKvCacheMicrobenchmark parses the plain stdout table's header as columns`() {
        val stdout =
            """
            method  bits  tokens_per_sec  ttft_ms
            kivi    2     120.5           45.2
            """.trimIndent()
        val shellOut = CliShellOut(processRunner = { FakeProcess(stdout = stdout) }, pathResolver = { true })

        val result = shellOut.runKvCacheMicrobenchmark(workload)

        assertEquals(listOf("method", "bits", "tokens_per_sec", "ttft_ms"), result.columns)
        assertEquals(2, result.rawTableLines.size)
    }

    @Test
    fun `runKvCacheMicrobenchmark uses snake_case flag spelling`() {
        var capturedCommand: List<String>? = null
        val shellOut =
            CliShellOut(
                processRunner = {
                    capturedCommand = it
                    FakeProcess(stdout = "header\nrow")
                },
                pathResolver = { true },
            )

        shellOut.runKvCacheMicrobenchmark(workload)

        val command = requireNotNull(capturedCommand)
        assertEquals(true, command.contains("--batch_size"))
        assertEquals(false, command.any { it.contains("--batch-size") })
    }
}
