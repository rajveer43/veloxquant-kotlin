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
     * the same warnings/recommendation payload as
     * `io.github.rajveer43.veloxquant.optimize.AutopilotFitError` and must never drift from it
     * in the fields it carries.
     *
     * **Provisional typing note:** [recommendation] is typed `Any` in Phase 1 only because the
     * real `Recommendation` type lives in `veloxquant-optimize`, which depends on
     * `veloxquant-core` (not the reverse) — `veloxquant-core` cannot reference it without an
     * illegal circular module dependency. This must be tightened to a real shared type (either
     * by moving `Recommendation` into `veloxquant-core` or introducing a small shared-types
     * module) no later than Phase 3, when `AutopilotWontFit` is first actually thrown — flag
     * this to the maintainer if Phase 3 arrives and this is still `Any`.
     */
    public class AutopilotWontFit(
        public val warnings: List<String>,
        public val recommendation: Any,
    ) : VeloxQuantException("AutoPilot: workload likely will not fit (${warnings.joinToString("; ")})")

    /** `veloxquant-runtime` tried to shell out but the `veloxquant` binary isn't on PATH. */
    public class CliNotInstalled(public val attemptedCommand: String) :
        VeloxQuantException(
            "`veloxquant` CLI not found (tried: $attemptedCommand). Install the VeloxQuant Python package.",
        )

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
     * **Provisional typing note:** [config] is typed `Any` in Phase 1 for the same
     * cross-module reason documented on [AutopilotWontFit.recommendation] — the real
     * `ServeConfig` type lives in `veloxquant-runtime`, downstream of `veloxquant-core`. Must
     * be tightened no later than Phase 4, when this exception is first actually thrown.
     */
    public class ServeStartupTimeout(public val config: Any, public val timeout: Duration) :
        VeloxQuantException("`veloxquant serve` did not report ready within $timeout")

    /**
     * The `serve` process exited (`validate_method` failure, crash, etc.) before or during
     * startup (Phase 4).
     */
    public class ServeProcessExited(public val exitCode: Int, public val stderr: String) :
        VeloxQuantException("`veloxquant serve` exited with code $exitCode: $stderr")
}
