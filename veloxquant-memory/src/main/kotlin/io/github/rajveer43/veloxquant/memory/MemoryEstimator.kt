/**
 * Pure-computation KV-cache memory estimation (plan §3.3). Zero I/O, zero dependency on
 * `veloxquant-runtime`/`veloxquant-system` — every input arrives as a plain data parameter.
 */
package io.github.rajveer43.veloxquant.memory

/**
 * Text carried on [MemoryEstimate.accountingNote]. This SDK's own client-authored caveat for
 * an offline estimate — distinct from (but consistent in spirit with) the real
 * `accounting_note` string the Python CLI/server emits on the wire once a live process is
 * involved (investigation §4.1); this module never talks to a live process, so it never
 * receives that server-authored string to relay verbatim.
 */
public const val ACCOUNTING_WARNING_TEXT: String =
    "This is an offline, client-side estimate of KV-cache bytes under compression. It is " +
        "accounting-only: no real memory reduction is measured or guaranteed by this number " +
        "alone. Compression method behavior can only be confirmed by a live veloxquant server."

/** Shape of a single inference workload, independent of any specific compression method. */
public data class WorkloadSpec(
    val seqLen: Int,
    val nLayers: Int,
    val nKvHeads: Int,
    val headDim: Int,
    val batchSize: Int = 1,
)

/** A compression method and its configuration knobs, as would be passed to `veloxquant serve`. */
public data class CompressionConfig(
    val method: String,
    val bits: Int,
    val knobs: Map<String, String> = emptyMap(),
)

/**
 * Result of [MemoryEstimator.estimate]. [accountingOnly]/[accountingNote] are real fields, not
 * KDoc-only caveats (plan §3.3) — a caller who ignores them still gets a correct number; a
 * caller who checks them cannot misrepresent what the number means.
 */
public data class MemoryEstimate(
    val kvFp16Bytes: Long,
    val kvCompressedEstimateBytes: Long,
    val runtimeOverheadBytes: Long,
    val fitsInBytes: (available: Long) -> Boolean,
    val accountingOnly: Boolean = true,
    val accountingNote: String = ACCOUNTING_WARNING_TEXT,
)

/**
 * Estimates KV-cache memory for a workload, with and without compression applied.
 *
 * This module is the one deliberate exception to "never reimplement the Python engine" (plan
 * §3.3): the math below is closed-form arithmetic with no live-server dependency, unlike
 * AutoPilot's fit-check (§3.6), so client-side reimplementation is safe here. To guard against
 * drift, `veloxquant-optimize`'s CLI-shell-out path cross-checks this module's output against
 * real `veloxquant recommend --json` numbers once `veloxquant-runtime` exists (Phase 3/4).
 */
public object MemoryEstimator {
    private const val BYTES_PER_FP16_ELEMENT = 2L
    private const val KV_TENSORS_PER_LAYER = 2L // one for keys, one for values
    private const val RUNTIME_OVERHEAD_FRACTION = 0.05

    /**
     * Computes [MemoryEstimate] for [workload] under [config].
     *
     * An unrecognized [CompressionConfig.method] is not an error: this function has no way to
     * validate method names against the live server's registry (that list is only ever known
     * to a running `veloxquant serve`/`methods --json`, investigation §3.4), so any method name
     * paired with [CompressionConfig.bits] produces a best-effort bits-based estimate. Rejecting
     * unknown names here would force this offline, Android-safe path to embed a copy of the
     * server's method registry, which is exactly the kind of drift-prone duplication plan §3.3
     * exists to avoid.
     */
    public fun estimate(
        workload: WorkloadSpec,
        config: CompressionConfig,
    ): MemoryEstimate {
        val elementsPerLayer =
            workload.seqLen.toLong() * workload.nKvHeads.toLong() * workload.headDim.toLong() *
                workload.batchSize.toLong()
        val totalElements = elementsPerLayer * workload.nLayers.toLong() * KV_TENSORS_PER_LAYER

        val kvFp16Bytes = totalElements * BYTES_PER_FP16_ELEMENT
        val compressedFraction = config.bits.toDouble() / (BYTES_PER_FP16_ELEMENT * Byte.SIZE_BITS)
        val kvCompressedEstimateBytes = (kvFp16Bytes * compressedFraction).toLong()
        val runtimeOverheadBytes = (kvFp16Bytes * RUNTIME_OVERHEAD_FRACTION).toLong()

        return MemoryEstimate(
            kvFp16Bytes = kvFp16Bytes,
            kvCompressedEstimateBytes = kvCompressedEstimateBytes,
            runtimeOverheadBytes = runtimeOverheadBytes,
            fitsInBytes = { available -> kvCompressedEstimateBytes + runtimeOverheadBytes <= available },
        )
    }
}
