/**
 * Method registry / local model listing types (plan §3.4). Pure data — Android-safe. The
 * functions that populate these (`listMethods()`/`listLocalModels()`) are CLI shell-out-backed
 * and JVM-desktop only, so they live in `veloxquant-runtime`, not as extensions on
 * [VeloxQuantClient] here — see that module's `MethodRegistryLookup.kt` KDoc for why.
 */
package io.github.rajveer43.veloxquant.core

/**
 * A compression algorithm the server can use, sourced from the real `veloxquant methods
 * --json`. Deliberately a distinct type from [LocalModel] — investigation §3 flags that TS's
 * "methods" (compression algorithms) and Go's "models" (LLM catalog) collide confusingly when
 * merged into one "registry" concept; this SDK keeps them apart on purpose.
 */
public data class CompressionMethod(
    val name: String,
    val family: String,
    val servable: Boolean,
    val telemetryCoverage: TelemetryCoverage,
)

/**
 * How much telemetry a [CompressionMethod] reports, as a first-class enum rather than a
 * collapsed ratio (investigation §4.3: "a 'keys only' ratio is not a whole-cache ratio, and
 * 'not reported' is not zero" — collapsing this to a single number would misrepresent it).
 */
public enum class TelemetryCoverage { FULL, KEYS_ONLY, NONE }

/** A locally-cached model found on disk (Hugging Face cache scan), distinct from [CompressionMethod]. */
public data class LocalModel(
    val repoId: String,
    val localPath: String,
    val sizeBytes: Long,
)
