/**
 * One-shot `veloxquant` CLI subcommand shell-outs (plan §3.6, build prompt Phase 3 item 1).
 * JVM-desktop only — never reachable from Android, which has no shell/process access.
 *
 * **Flag spelling is per-subcommand, not uniform across the CLI**: `recommend`/`auto-config`
 * use kebab-case (`--head-dim`, `--seq-len`), while `precompute`/`benchmark` (Phase 4) use
 * snake_case (`--head_dim`, `--seq_len`). This is a real inconsistency in the ground-truth CLI
 * itself (investigation §2.1), reproduced here deliberately rather than "fixed" — do not
 * normalize the two conventions into one when Phase 4 adds the snake_case subcommands.
 */
package io.github.rajveer43.veloxquant.runtime

import io.github.rajveer43.veloxquant.core.VeloxQuantException
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

private val json = Json { ignoreUnknownKeys = true }

/** Request fields shared by `recommend` and `auto-config`, per investigation §2.1's CLI flags. */
public data class WorkloadCliArgs(
    val seqLen: Int,
    val nLayers: Int,
    val nKvHeads: Int,
    val headDim: Int,
    val batchSize: Int = 1,
)

@Serializable
internal data class RecommendCliResponse(
    val request: kotlinx.serialization.json.JsonElement,
    val recommendation: RecommendationCliPayload,
)

/** Mirrors `recommend --json`'s `recommendation` object verbatim (investigation §2.1). */
@Serializable
public data class RecommendationCliPayload(
    val method: String,
    val knobs: Map<String, String> = emptyMap(),
    @SerialName("key_accounting_ratio") val keyAccountingRatio: Double,
    @SerialName("resident_savings_likely") val residentSavingsLikely: Boolean,
    @SerialName("kv_fp16_mb") val kvFp16Mb: Double,
    @SerialName("kv_compressed_mb_estimate") val kvCompressedMbEstimate: Double,
    val warnings: List<String> = emptyList(),
    val rationale: String,
)

/** Mirrors `auto-config --json`'s top-level response shape verbatim (investigation §2.1). */
@Serializable
public data class AutoConfigCliResponse(
    val workload: kotlinx.serialization.json.JsonElement,
    val hardware: kotlinx.serialization.json.JsonElement,
    val config: AutoConfigPayload,
    val reason: String,
)

/** Mirrors `auto-config --json`'s `config` object verbatim (investigation §2.1). */
@Serializable
public data class AutoConfigPayload(
    val method: String,
    val bits: Int,
    val knobs: Map<String, String> = emptyMap(),
)

/** Shells out to `veloxquant recommend --json ...` and `veloxquant auto-config --json ...`. */
public class CliShellOut(
    private val processRunner: ProcessRunner = RealProcessRunner,
    private val pathResolver: PathResolver = RealPathResolver,
    private val binaryName: String = "veloxquant",
) {
    /** Runs `veloxquant recommend --json` for [workload]/[goal], parsing its stdout. */
    public fun recommend(
        workload: WorkloadCliArgs,
        goal: String,
    ): RecommendationCliPayload {
        val command =
            listOf(
                binaryName, "recommend", "--json",
                "--seq-len", workload.seqLen.toString(),
                "--n-layers", workload.nLayers.toString(),
                "--n-kv-heads", workload.nKvHeads.toString(),
                "--head-dim", workload.headDim.toString(),
                "--batch-size", workload.batchSize.toString(),
                "--goal", goal,
            )
        val stdout = runAndCaptureStdout(command)
        return json.decodeFromString(RecommendCliResponse.serializer(), stdout).recommendation
    }

    /** Runs `veloxquant auto-config --json` for [workload], parsing its stdout. */
    public fun autoConfig(workload: WorkloadCliArgs): AutoConfigCliResponse {
        val command =
            listOf(
                binaryName, "auto-config", "--json",
                "--seq-len", workload.seqLen.toString(),
                "--n-layers", workload.nLayers.toString(),
                "--n-kv-heads", workload.nKvHeads.toString(),
                "--head-dim", workload.headDim.toString(),
                "--batch-size", workload.batchSize.toString(),
            )
        val stdout = runAndCaptureStdout(command)
        return json.decodeFromString(AutoConfigCliResponse.serializer(), stdout)
    }

    private fun runAndCaptureStdout(command: List<String>): String {
        if (!pathResolver.resolve(binaryName)) {
            throw VeloxQuantException.CliNotInstalled(command.joinToString(" "))
        }
        val result = processRunner.run(command)
        if (result.exitCode != 0) {
            throw VeloxQuantException.CliCommandFailed(command.joinToString(" "), result.exitCode, result.stderr)
        }
        return result.stdout
    }
}
