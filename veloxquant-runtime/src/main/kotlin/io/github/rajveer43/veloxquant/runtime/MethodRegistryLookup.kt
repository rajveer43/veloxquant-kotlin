/**
 * Populates `veloxquant-core`'s [CompressionMethod]/[LocalModel] registry types (plan §3.4,
 * build prompt Phase 4 items 8/9).
 *
 * **Deviation from plan §3.4's code sketch, flagged explicitly:** the plan types these as
 * `suspend fun VeloxQuantClient.listMethods()`/`listLocalModels()` extensions in
 * `veloxquant-core`. But `listMethods()` is "sourced from `methods --json`" (a CLI shell-out,
 * per build prompt Phase 4 item 8 — no confirmed HTTP route exists) and `listLocalModels()` is
 * explicitly "HF cache scan — desktop-only" (plan §3.4's own comment) needing filesystem
 * access to the Mac's cache directory. An extension on `VeloxQuantClient` living in
 * `veloxquant-core` cannot shell out or scan the filesystem without `veloxquant-core` taking a
 * dependency on `veloxquant-runtime` — inverting the one-way module dependency graph the exact
 * way Phase 3's `AutoPilot`/`RecommendBackend` deviations were designed to avoid. Both
 * functions live here instead, as plain (non-extension) functions; a JVM-desktop caller
 * imports them from `veloxquant-runtime` directly, the same way it already must for
 * `AutoPilot`/`VeloxQuantProcess`.
 */
package io.github.rajveer43.veloxquant.runtime

import io.github.rajveer43.veloxquant.core.CompressionMethod
import io.github.rajveer43.veloxquant.core.LocalModel
import io.github.rajveer43.veloxquant.core.TelemetryCoverage
import java.io.File

/** Shells to `veloxquant methods --json` and maps the result to [CompressionMethod]. */
public fun listMethods(cliShellOut: CliShellOut = CliShellOut()): List<CompressionMethod> =
    cliShellOut.methods().map { payload ->
        CompressionMethod(
            name = payload.name,
            family = payload.family,
            servable = payload.servable,
            telemetryCoverage = payload.telemetryCoverage.toTelemetryCoverageOrNone(),
        )
    }

/**
 * Scans the local Hugging Face cache directory (`$HF_HOME/hub`, defaulting to
 * `~/.cache/huggingface/hub` per the HF client library's own convention) for locally-present
 * models. Desktop-only: needs filesystem access to the Mac's cache directory, which an
 * Android app never has for a paired Mac's cache.
 */
public fun listLocalModels(cacheDir: File = defaultHfCacheDir()): List<LocalModel> {
    if (!cacheDir.isDirectory) return emptyList()
    return cacheDir.listFiles { file -> file.isDirectory && file.name.startsWith("models--") }
        ?.map { modelDir ->
            LocalModel(
                repoId = modelDir.name.removePrefix("models--").replace("--", "/"),
                localPath = modelDir.absolutePath,
                sizeBytes = modelDir.walkTopDown().filter { it.isFile }.sumOf { it.length() },
            )
        }
        ?.sortedBy { it.repoId }
        ?: emptyList()
}

private fun defaultHfCacheDir(): File {
    val hfHome = System.getenv("HF_HOME")
    val base = if (hfHome != null) File(hfHome) else File(System.getProperty("user.home"), ".cache/huggingface")
    return File(base, "hub")
}

/**
 * Maps the wire's free-text `telemetry_coverage` string onto [TelemetryCoverage]. An
 * unrecognized value maps to [TelemetryCoverage.NONE] rather than throwing — per this
 * module's established "unknown value degrades to the most conservative interpretation rather
 * than failing the whole call" convention (mirrors `MemoryEstimator`'s unknown-method
 * handling, Phase 2).
 */
private fun String.toTelemetryCoverageOrNone(): TelemetryCoverage =
    when (uppercase()) {
        "FULL" -> TelemetryCoverage.FULL
        "KEYS_ONLY" -> TelemetryCoverage.KEYS_ONLY
        else -> TelemetryCoverage.NONE
    }
