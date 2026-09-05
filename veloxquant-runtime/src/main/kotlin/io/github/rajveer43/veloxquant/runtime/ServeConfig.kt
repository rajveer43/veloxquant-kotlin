/** `veloxquant serve` launch configuration (plan §3.12). */
package io.github.rajveer43.veloxquant.runtime

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

/**
 * Configuration for launching `veloxquant serve`. [port] defaults to 8000, matching both
 * `serve.py`'s own default and [io.github.rajveer43.veloxquant.core.VeloxQuantClient]'s
 * default `baseUrl` port — no split-default bug, unlike Go's SDK (investigation §1.10).
 */
public data class ServeConfig(
    val model: String,
    val method: String? = null,
    val bits: Int = 2,
    val host: String = "127.0.0.1",
    val port: Int = 8000,
    val readyTimeout: Duration = 5.minutes,
    val maxTokens: Int = 512,
    val temperature: Double = 0.0,
    val topP: Double = 1.0,
    val promptCacheSize: Int = 10,
    /**
     * Surfaced for forward-compat with a possible future CLI flag. **Documented inert**:
     * the real `veloxquant` CLI does not currently read or act on this value in any way
     * (investigation §1.2/§4.3) — setting it has no effect on the launched process.
     */
    val promptCacheBytes: Long? = null,
    val setOverrides: Map<String, String> = emptyMap(),
) {
    /** Default shutdown grace period before escalating from `SIGINT` to a forced kill. */
    public companion object {
        public val DEFAULT_GRACE_PERIOD: Duration = 10.seconds
    }
}

/**
 * The `VELOXQUANT_READY ` stdout handshake payload (investigation §1.7's exact schema). This
 * line, not the priming request's own response, is the authoritative "ready" signal — see
 * [VeloxQuantProcess]'s KDoc for why both are raced together.
 */
@Serializable
public data class ReadyHandshake(
    @SerialName("schema_version") val schemaVersion: Int,
    val model: String,
    val method: String?,
    val bits: Int?,
    val host: String,
    val port: Int,
    @SerialName("layer_caches") val layerCaches: Int? = null,
    val endpoints: Map<String, String> = emptyMap(),
    @SerialName("accounting_only") val accountingOnly: Boolean = true,
    @SerialName("accounting_note") val accountingNote: String? = null,
)
