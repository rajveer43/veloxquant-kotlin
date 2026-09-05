/**
 * AutoPilot — shell-out to the real `veloxquant` CLI, matching TS's architecture rather than
 * Go's from-scratch reimplementation (plan §3.6, build prompt Phase 3, maintainer-confirmed).
 *
 * **Lives in `veloxquant-runtime`, not `veloxquant-core` or `veloxquant-optimize`, deviating
 * from plan §3.6's "likely in `veloxquant-core`" phrasing — flagged explicitly:** AutoPilot
 * needs both `veloxquant-optimize` (for [io.github.rajveer43.veloxquant.optimize.Recommendation])
 * and a real CLI shell-out ([CliShellOut]). `veloxquant-core` cannot depend on either without
 * inverting the established one-way dependency graph (`optimize`/`runtime` depend on `core`,
 * never the reverse — Phase 0's enforced module boundary), and `veloxquant-optimize` must stay
 * free of a `veloxquant-runtime` dependency to remain Android-safe at the Gradle level (see
 * `RecommendBackend`'s KDoc). `veloxquant-runtime` is the only module already permitted to
 * depend on both, and is already JVM-desktop-only, which is what AutoPilot's shell-out
 * requirement demands anyway (plan §3.6's own "Android implication" paragraph). No public API
 * shape changes as a result — only the module a JVM-desktop caller imports it from.
 *
 * JVM-desktop only, full stop: unbuildable on Android, since there is no local `veloxquant`
 * CLI to shell to from an Android app.
 */
package io.github.rajveer43.veloxquant.runtime

import io.github.rajveer43.veloxquant.core.ChatChunk
import io.github.rajveer43.veloxquant.core.ChatRequest
import io.github.rajveer43.veloxquant.core.ChatResponse
import io.github.rajveer43.veloxquant.core.Conversation
import io.github.rajveer43.veloxquant.core.VeloxQuantClient
import io.github.rajveer43.veloxquant.core.VeloxQuantException
import io.github.rajveer43.veloxquant.memory.WorkloadSpec
import io.github.rajveer43.veloxquant.optimize.OptimizationGoal
import io.github.rajveer43.veloxquant.optimize.Optimizer
import io.github.rajveer43.veloxquant.optimize.RecommendBackend
import io.github.rajveer43.veloxquant.optimize.RecommendRequest
import io.github.rajveer43.veloxquant.optimize.Recommendation
import kotlinx.coroutines.flow.Flow
import io.github.rajveer43.veloxquant.core.conversation as coreConversation

/** The serve-safe compression method pool `auto-config` falls back into (investigation §2.1). */
private val SERVABLE_METHODS = setOf("turboquant_rvq", "kivi", "kvquant", "gear")

/**
 * Heuristic match for "this recommendation means the workload won't fit," mirroring TS's
 * `WONT_FIT_PATTERN` (investigation §3.1) since the wire has no structured severity field on
 * `recommend --json`'s `warnings` array — this is TS's own documented, self-aware limitation,
 * carried over here rather than inventing false structure the CLI doesn't provide.
 */
private val WONT_FIT_PATTERN = Regex("will not fit|short of any headroom", RegexOption.IGNORE_CASE)

/** Request to start an AutoPilot session (plan §3.6). */
public data class AutoPilotRequest(
    val modelClass: String,
    val workload: WorkloadSpec,
    val goal: OptimizationGoal,
    val force: Boolean = false,
)

/**
 * Sealed sum of AutoPilot's two possible outcomes — see [AutoPilot.tryStart]. [WontFit] wraps
 * the exact [VeloxQuantException.AutopilotWontFit] instance [AutoPilot.start] would throw for
 * the same input, rather than a second, separately-defined error type — this is what makes
 * "these two must never drift apart in the fields they carry" (build prompt Phase 3 item 6)
 * true by construction instead of by convention.
 */
public sealed interface AutoPilotOutcome {
    /** The workload fit (or `force = true` was set); [session] is ready to use. */
    public data class Started(val session: AutoPilotSession) : AutoPilotOutcome

    /** The workload likely won't fit; [error] carries the same payload [AutoPilot.start] throws. */
    public data class WontFit(val error: VeloxQuantException.AutopilotWontFit) : AutoPilotOutcome
}

/** An active AutoPilot session, scoped to the chosen model/compression method (plan §3.6). */
public class AutoPilotSession internal constructor(
    private val client: VeloxQuantClient,
    public val recommendation: Recommendation,
) {
    /** Sends a single chat completion using this session's client. */
    public suspend fun chat(request: ChatRequest): ChatResponse = client.chat(request)

    /** Streams a chat completion using this session's client. */
    public fun chatStream(request: ChatRequest): Flow<ChatChunk> = client.chatStream(request)

    /** Starts a new multi-turn [Conversation] scoped to this session's client. */
    public fun conversation(system: String? = null): Conversation = client.coreConversation(system)
}

/**
 * AutoPilot: picks a servable compression config for [AutoPilotRequest.modelClass]/`goal` via
 * [Optimizer.recommend], falls back to `auto-config`'s serve-safe pool if the picked method
 * isn't servable, and hands back a ready-to-use [AutoPilotSession] against [client].
 *
 * [client] must already point at a running `veloxquant serve` — AutoPilot itself does not
 * launch `serve` (that is Phase 4's `VeloxQuantProcess`); it only picks and validates a config.
 */
public class AutoPilot(
    private val client: VeloxQuantClient,
    private val backend: RecommendBackend = CliRecommendBackend(),
    private val cliShellOut: CliShellOut = CliShellOut(),
) {
    /** Sealed-result entry point — branches on "won't fit" as data, no try/catch needed. */
    public fun tryStart(request: AutoPilotRequest): AutoPilotOutcome {
        val recommendation = resolveRecommendation(request)
        val wontFit = !request.force && recommendationLooksLikeWontFit(recommendation)
        return if (wontFit) {
            AutoPilotOutcome.WontFit(
                VeloxQuantException.AutopilotWontFit(
                    warnings = recommendation.warnings,
                    method = recommendation.method,
                    bits = recommendation.bits,
                    rationale = recommendation.rationale,
                ),
            )
        } else {
            AutoPilotOutcome.Started(AutoPilotSession(client, recommendation))
        }
    }

    /** Exception-based entry point — throws [VeloxQuantException.AutopilotWontFit] unless it fits or `force = true`. */
    public fun start(request: AutoPilotRequest): AutoPilotSession =
        when (val outcome = tryStart(request)) {
            is AutoPilotOutcome.Started -> outcome.session
            is AutoPilotOutcome.WontFit -> throw outcome.error
        }

    private fun resolveRecommendation(request: AutoPilotRequest): Recommendation {
        val initial = Optimizer.recommend(RecommendRequest(request.workload, request.goal), backend)
        if (initial.method in SERVABLE_METHODS) return initial

        val autoConfig =
            cliShellOut.autoConfig(
                WorkloadCliArgs(
                    seqLen = request.workload.seqLen,
                    nLayers = request.workload.nLayers,
                    nKvHeads = request.workload.nKvHeads,
                    headDim = request.workload.headDim,
                    batchSize = request.workload.batchSize,
                ),
            )
        return initial.copy(
            method = autoConfig.config.method,
            bits = autoConfig.config.bits,
            knobs = autoConfig.config.knobs,
            rationale = autoConfig.reason,
        )
    }

    private fun recommendationLooksLikeWontFit(recommendation: Recommendation): Boolean =
        !recommendation.residentSavingsLikely ||
            recommendation.warnings.any { WONT_FIT_PATTERN.containsMatchIn(it) }
}
