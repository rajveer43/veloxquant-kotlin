/**
 * The full VeloxQuant Kotlin SDK error hierarchy (plan §4). Defined in full in Phase 1 even
 * though several subtypes (AutopilotWontFit, CliNotInstalled, ServeStartupTimeout,
 * ServeProcessExited) aren't thrown by any code until Phase 3/4 — establishing the sealed
 * hierarchy once, rather than extending it piecemeal, keeps any exhaustive `when` written
 * against it in Phase 1 from breaking later.
 */
package io.github.rajveer43.veloxquant.core

import kotlinx.serialization.SerializationException
import kotlin.time.Duration

/** Root of the VeloxQuant SDK's sealed exception hierarchy. See plan §4. */
public sealed class VeloxQuantException(message: String, cause: Throwable? = null) : Exception(message, cause) {
    /** The HTTP server is not reachable at all (connection refused, DNS failure, timeout). */
    public class RuntimeUnreachable(public val baseUrl: String, cause: Throwable) :
        VeloxQuantException("VeloxQuant runtime unreachable at $baseUrl", cause)

    /**
     * A 404 whose body parsed as `{"error": ...}` JSON — a generation failure, not a wrong
     * route. Investigation §1.8/§1.10: `mlx_lm`'s server returns 404 for generation errors, a
     * real quirk in the vendored server, not a VeloxQuant design choice.
     */
    public class GenerationFailed(public val serverMessage: String) :
        VeloxQuantException("Generation failed: $serverMessage")

    /**
     * A 404 whose body was the literal plain-text `Not Found` — a genuinely wrong path,
     * distinct from [GenerationFailed].
     */
    public class UnexpectedRoute(public val path: String) :
        VeloxQuantException("No such route: $path")

    /**
     * A non-2xx response whose body didn't parse as the expected `{"error": ...}` shape at
     * all — covers the unhandled `ValueError` / raw internal-error case from investigation
     * §1.8, where a bad request can come back as a broken/partial response or connection
     * reset rather than a clean JSON error body.
     */
    public class MalformedErrorResponse(public val statusCode: Int, public val rawBody: String) :
        VeloxQuantException("Unexpected error response (status $statusCode): $rawBody")

    /**
     * AutoPilot determined the requested workload won't fit — see plan §3.6 (Phase 3). Carries
     * the same payload fields as
     * `io.github.rajveer43.veloxquant.optimize.AutopilotFitError` and must never drift from it.
     *
     * **Resolved typing note, replacing Phase 1's `Any` placeholder:** [recommendation] is a
     * flattened snapshot of `veloxquant-optimize`'s `Recommendation` fields, not a reference to
     * that type itself. `veloxquant-optimize` depends on `veloxquant-core` (not the reverse);
     * referencing `Recommendation` directly here would create an illegal circular module
     * dependency the moment `AutoPilot` (which lives in `veloxquant-optimize`, wired to
     * `veloxquant-runtime`) throws this exception. Every field on
     * [io.github.rajveer43.veloxquant.optimize.AutopilotFitError] is mirrored 1:1 here by name;
     * `veloxquant-optimize`'s `OptimizerTest`/`AutoPilotTest` cross-check that the two never
     * drift apart in field set, since Kotlin's type system cannot enforce that across the
     * dependency direction.
     */
    public class AutopilotWontFit(
        public val warnings: List<String>,
        public val method: String,
        public val bits: Int,
        public val rationale: String,
    ) : VeloxQuantException("AutoPilot: workload likely will not fit (${warnings.joinToString("; ")})")

    /** `veloxquant-runtime` tried to shell out but the `veloxquant` binary isn't on PATH. */
    public class CliNotInstalled(public val attemptedCommand: String) :
        VeloxQuantException(
            "`veloxquant` CLI not found (tried: $attemptedCommand). Install the VeloxQuant Python package.",
        )

    /**
     * A one-shot `veloxquant` CLI subcommand (`recommend`, `auto-config`, etc.) exited non-zero
     * (Phase 3). Distinct from [ServeProcessExited], which covers the long-running `serve`
     * process instead.
     */
    public class CliCommandFailed(public val command: String, public val exitCode: Int, public val stderr: String) :
        VeloxQuantException("`$command` exited with code $exitCode: $stderr")

    /**
     * A desktop-only API (process management, hardware detection) was called on Android or
     * another unsupported platform. This is a runtime check, not just compile-time module
     * absence, since a JVM-desktop-oriented code path could still be exercised in an
     * environment like Robolectric where the classes are present but the OS facilities aren't.
     */
    public class UnsupportedPlatform(public val feature: String, public val platform: String) :
        VeloxQuantException("$feature is not available on $platform")

    /**
     * Structured-output response failed to deserialize into the requested type.
     *
     * Note: [chatStructured] itself does **not** throw this — it returns
     * `StructuredResult.ParseFailed` (plan §3.8, Phase 5). This exception exists for
     * lower-level callers who bypass the sealed-result API and call a raw deserialization
     * helper directly.
     */
    public class MalformedStructuredOutput(public val raw: String, cause: SerializationException) :
        VeloxQuantException("Model output did not match the requested schema", cause)

    /**
     * The `serve` process failed to become ready within the configured timeout (Phase 4).
     *
     * **Resolved typing note, replacing Phase 1's `Any` placeholder:** [model]/[port] are a
     * flattened snapshot of `veloxquant-runtime`'s `ServeConfig`, not a reference to that type
     * itself, for the same circular-dependency reason documented on
     * [AutopilotWontFit]'s KDoc — `veloxquant-runtime` depends on `veloxquant-core`, not the
     * reverse. Only the two fields most useful for diagnosing a timeout are surfaced; the full
     * `ServeConfig` the caller passed to `VeloxQuantProcess.start` is already in their own
     * scope, so nothing is lost by not attaching the whole object here.
     */
    public class ServeStartupTimeout(public val model: String, public val port: Int, public val timeout: Duration) :
        VeloxQuantException("`veloxquant serve` (model=$model, port=$port) did not report ready within $timeout")

    /**
     * The `serve` process exited (`validate_method` failure, crash, etc.) before or during
     * startup (Phase 4).
     */
    public class ServeProcessExited(public val exitCode: Int, public val stderr: String) :
        VeloxQuantException("`veloxquant serve` exited with code $exitCode: $stderr")
}
