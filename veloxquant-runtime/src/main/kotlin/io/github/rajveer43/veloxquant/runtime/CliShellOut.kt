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
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject

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

/**
 * Mirrors `methods --json`'s per-method entry. Field names follow the same
 * `snake_case`-on-the-wire convention as `recommend`/`auto-config`'s payloads.
 *
 * **Gap, flagged rather than silently guessed at:** neither the implementation plan nor the
 * build prompt records `methods --json`'s exact wire schema (unlike `recommend`/`auto-config`,
 * which investigation §2.1 documents field-for-field) — only the target Kotlin shape
 * (`CompressionMethod` in `veloxquant-optimize`) is specified. This type is this module's
 * best-defensible mapping of that target shape onto a plausible `snake_case` wire payload;
 * verify it against a real `veloxquant methods --json` run before relying on it in production,
 * and correct the `@SerialName`s here (not `CompressionMethod` itself) if they don't match.
 */
@Serializable
public data class MethodCliPayload(
    val name: String,
    val family: String,
    val servable: Boolean,
    @SerialName("telemetry_coverage") val telemetryCoverage: String,
)

/**
 * Mirrors `profile`'s always-JSON output. **Same schema gap as [MethodCliPayload]**: the build
 * prompt lists `profile` as a shell-out to add in Phase 4 but names no consuming Kotlin type
 * or exact field set — this is a minimal, best-effort passthrough of the raw JSON object
 * rather than a guessed, overly-specific data class, so it degrades gracefully (an unknown or
 * changed field is simply not surfaced, instead of a deserialization failure).
 */
public data class ProfileResult(val raw: kotlinx.serialization.json.JsonObject)

/**
 * One row of `benchmark`'s plain stdout table (no `--json` flag exists for this subcommand —
 * investigation §2.1). Kept in a KV-cache-microbenchmark-specific type, never named
 * `BenchmarkResult` (that name is reserved for Phase 6's `benchmarkServing()`, per plan §3.10's
 * explicit collision warning against the Python CLI's own `benchmark` command).
 *
 * **Schema gap, flagged rather than guessed at:** the exact column set of `benchmark`'s plain
 * table is not recorded in the implementation plan or build prompt (plan §9 item 6 itself
 * flags that `cmd/vq/benchmark.go` was never read in full). [rawTableLines] is the verified,
 * always-correct fallback; [columns] is a best-effort whitespace-split parse of the header
 * row, provided for convenience but not guaranteed complete — verify against a real
 * `veloxquant benchmark` run before depending on specific column names.
 */
public data class KvCacheMicrobenchmarkResult(
    val rawTableLines: List<String>,
    val columns: List<String>,
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

    /** Runs `veloxquant methods --json`, parsing the list of available compression methods. */
    public fun methods(): List<MethodCliPayload> {
        val stdout = runAndCaptureStdout(listOf(binaryName, "methods", "--json"))
        return json.decodeFromString(ListSerializer(MethodCliPayload.serializer()), stdout)
    }

    /** Runs `veloxquant profile` (always-JSON), returning the raw parsed object. */
    public fun profile(): ProfileResult {
        val stdout = runAndCaptureStdout(listOf(binaryName, "profile"))
        return ProfileResult(json.parseToJsonElement(stdout).jsonObject)
    }

    /**
     * Runs `veloxquant precompute` for [workload]. This subcommand writes files and has no
     * `--json` output (investigation §2.1) — only exit-code/stderr handling is meaningful
     * here; a successful call returns normally, a non-zero exit throws
     * [io.github.rajveer43.veloxquant.core.VeloxQuantException.CliCommandFailed]. Uses
     * snake_case flags, unlike `recommend`/`auto-config`'s kebab-case (the CLI's own
     * documented inconsistency — see this file's header KDoc).
     */
    public fun precompute(workload: WorkloadCliArgs) {
        runAndCaptureStdout(
            listOf(
                binaryName, "precompute",
                "--seq_len", workload.seqLen.toString(),
                "--n_layers", workload.nLayers.toString(),
                "--n_kv_heads", workload.nKvHeads.toString(),
                "--head_dim", workload.headDim.toString(),
                "--batch_size", workload.batchSize.toString(),
            ),
        )
    }

    /**
     * Runs `veloxquant benchmark` for [workload] and parses its plain stdout table. No
     * `--json` flag exists for this subcommand (investigation §2.1); see
     * [KvCacheMicrobenchmarkResult]'s KDoc for the parsing caveat. Named
     * `runKvCacheMicrobenchmark`, deliberately distinct from Phase 6's `benchmarkServing()`
     * (plan §3.10's collision warning). Uses snake_case flags, matching `precompute`.
     */
    public fun runKvCacheMicrobenchmark(workload: WorkloadCliArgs): KvCacheMicrobenchmarkResult {
        val command =
            listOf(
                binaryName, "benchmark",
                "--seq_len", workload.seqLen.toString(),
                "--n_layers", workload.nLayers.toString(),
                "--n_kv_heads", workload.nKvHeads.toString(),
                "--head_dim", workload.headDim.toString(),
                "--batch_size", workload.batchSize.toString(),
            )
        val stdout = runAndCaptureStdout(command)
        val lines = stdout.lines().filter { it.isNotBlank() }
        val columns = lines.firstOrNull()?.trim()?.split(Regex("\\s+")) ?: emptyList()
        return KvCacheMicrobenchmarkResult(rawTableLines = lines, columns = columns)
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
