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
}
