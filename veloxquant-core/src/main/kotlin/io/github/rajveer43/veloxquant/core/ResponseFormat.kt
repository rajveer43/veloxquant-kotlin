/**
 * Response format hint for [ChatRequest.responseFormat] (plan §3.8, full `chatStructured()`
 * API lands in Phase 5). Defined now since [ChatRequest] references it from Phase 1.
 *
 * Sent on the wire for forward-compat, but confirmed inert today: investigation §4.2 found
 * `mlx_lm/server.py`'s `do_POST` does not read `response_format` off the request body at all.
 * This is a prompt-injection best-effort fallback, never a schema-conformance guarantee.
 */
package io.github.rajveer43.veloxquant.core

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement

/** Requested response shape for [ChatRequest.responseFormat]. */
@Serializable
public sealed interface ResponseFormat {
    /** Request a response conforming to a named JSON Schema. */
    @Serializable
    public data class JsonSchema(
        val name: String,
        val schema: JsonElement,
        val strict: Boolean = false,
    ) : ResponseFormat

    /** Request a response that is merely a JSON object, with no specific schema. */
    @Serializable
    public data object JsonObject : ResponseFormat
}
