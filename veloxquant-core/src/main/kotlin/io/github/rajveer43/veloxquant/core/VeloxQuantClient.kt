/**
 * HTTP client for a running `veloxquant serve` process (plan §3.0/§3.1). Talks the
 * OpenAI-chat-completions-compatible wire protocol documented in investigation §1.
 */
package io.github.rajveer43.veloxquant.core

import io.github.rajveer43.veloxquant.core.internal.WireChatChunk
import io.github.rajveer43.veloxquant.core.internal.WireChatResponse
import io.github.rajveer43.veloxquant.core.internal.WireErrorBody
import io.github.rajveer43.veloxquant.core.internal.toPublic
import io.github.rajveer43.veloxquant.core.internal.toWire
import io.ktor.client.HttpClient
import io.ktor.client.HttpClientConfig
import io.ktor.client.call.body
import io.ktor.client.engine.HttpClientEngine
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.header
import io.ktor.client.request.preparePost
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsChannel
import io.ktor.client.statement.bodyAsText
import io.ktor.client.statement.request
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.http.isSuccess
import io.ktor.serialization.kotlinx.json.json
import io.ktor.utils.io.readUTF8Line
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.serialization.json.Json
import java.io.IOException
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

/**
 * Client for a running `veloxquant serve` instance. Construct via [VeloxQuantClient.create].
 *
 * Android-safe: no `ProcessBuilder`/shell APIs, only HTTP. See plan §2.1.
 */
public class VeloxQuantClient private constructor(
    public val baseUrl: String,
    private val httpClient: HttpClient,
    public val defaultModel: String? = null,
    private val requestTimeout: Duration = 30.seconds,
) {
    public companion object {
        private val json =
            Json {
                ignoreUnknownKeys = true
                encodeDefaults = true
            }

        /**
         * Creates a [VeloxQuantClient]. `@JvmOverloads` gives Java/Android-Java callers a
         * usable overload set without a separate builder class (plan §3.0).
         *
         * Note: [Duration] is a Kotlin inline value class, which `@JvmOverloads` cannot mangle
         * an overload set around directly — `requestTimeoutMs` (a plain `Long`) is the
         * `@JvmOverloads`-compatible parameter Java callers see; [create] with a [Duration]
         * parameter remains available as the idiomatic Kotlin entry point.
         */
        @JvmOverloads
        @JvmStatic
        public fun create(
            baseUrl: String = "http://127.0.0.1:8000",
            defaultModel: String? = null,
            requestTimeoutMs: Long = 30.seconds.inWholeMilliseconds,
            engine: HttpClientEngine = OkHttp.create(),
            configureHttpClient: HttpClientConfig<*>.() -> Unit = {},
        ): VeloxQuantClient {
            val httpClient =
                HttpClient(engine) {
                    install(ContentNegotiation) { json(json) }
                    install(HttpTimeout) {
                        requestTimeoutMillis = requestTimeoutMs
                    }
                    configureHttpClient()
                }
            return VeloxQuantClient(
                baseUrl.trimEnd('/'),
                httpClient,
                defaultModel,
                requestTimeoutMs.milliseconds,
            )
        }

        /** Idiomatic Kotlin entry point accepting a [Duration] directly instead of raw millis. */
        public fun create(
            baseUrl: String = "http://127.0.0.1:8000",
            defaultModel: String? = null,
            requestTimeout: Duration,
            engine: HttpClientEngine = OkHttp.create(),
            configureHttpClient: HttpClientConfig<*>.() -> Unit = {},
        ): VeloxQuantClient {
            val requestTimeoutMs = requestTimeout.inWholeMilliseconds
            return create(baseUrl, defaultModel, requestTimeoutMs, engine, configureHttpClient)
        }

        /** Internal constructor for tests — accepts a pre-built [HttpClient] (e.g. MockEngine-backed). */
        internal fun createWithClient(
            baseUrl: String,
            httpClient: HttpClient,
            defaultModel: String? = null,
        ): VeloxQuantClient = VeloxQuantClient(baseUrl.trimEnd('/'), httpClient, defaultModel)
    }

    /** Sends a single (non-streaming) chat completion request to `/v1/chat/completions`. */
    public suspend fun chat(request: ChatRequest): ChatResponse {
        val wireRequest = request.copy(stream = false).toWire(defaultModel)
        val response =
            try {
                httpClient.preparePost("$baseUrl/v1/chat/completions") {
                    contentType(ContentType.Application.Json)
                    setBody(wireRequest)
                }.execute()
            } catch (e: IOException) {
                throw VeloxQuantException.RuntimeUnreachable(baseUrl, e)
            }

        if (!response.status.isSuccess()) {
            throw response.toException()
        }

        return response.body<WireChatResponse>().toPublic()
    }

    /**
     * Streams a chat completion from `/v1/chat/completions` as a [Flow] of [ChatChunk].
     *
     * Built with `channelFlow`, not `callbackFlow` — Ktor's [readUTF8Line] is itself
     * suspend-based, so a produce-style builder is the natural fit (investigation §5.4). No
     * manual `.close()`/cleanup API: collector cancellation closes the underlying connection
     * automatically via structured concurrency.
     */
    public fun chatStream(request: ChatRequest): Flow<ChatChunk> =
        channelFlow {
            val wireRequest = request.copy(stream = true).toWire(defaultModel)

            val statement =
                httpClient.preparePost("$baseUrl/v1/chat/completions") {
                    contentType(ContentType.Application.Json)
                    header("Accept", "text/event-stream")
                    setBody(wireRequest)
                }

            try {
                statement.execute { httpResponse ->
                    if (!httpResponse.status.isSuccess()) {
                        throw httpResponse.toException()
                    }

                    val channel = httpResponse.bodyAsChannel()
                    var line = channel.readUTF8Line()
                    while (line != null) {
                        val payload = line.toSsePayloadOrNull()
                        if (payload != null && payload != "[DONE]") {
                            val wireChunk = json.decodeFromString(WireChatChunk.serializer(), payload)
                            send(wireChunk.toPublic())
                        }
                        line = if (payload == "[DONE]") null else channel.readUTF8Line()
                    }
                }
            } catch (e: IOException) {
                throw VeloxQuantException.RuntimeUnreachable(baseUrl, e)
            }
        }

    /**
     * Extracts a `data:` frame's payload from one SSE line, or null if the line should be
     * skipped — blank lines, and comment lines starting with `:` such as `: keepalive N/M`
     * emitted during long prompt prefill (investigation §1.4), are not data frames.
     */
    private fun String.toSsePayloadOrNull(): String? {
        if (isBlank() || startsWith(":")) return null
        if (!startsWith("data:")) return null
        return removePrefix("data:").trim()
    }

    /**
     * Inspects the response body before deciding the error type, per investigation
     * §1.8/§1.10's "don't dispatch purely on status code" finding — a 404 can mean either a
     * genuine generation failure or an actually-wrong route, and other non-2xx bodies may not
     * be parseable JSON at all (the unhandled `ValueError` case, investigation §1.8).
     */
    private suspend fun HttpResponse.toException(): VeloxQuantException {
        val rawBody =
            try {
                bodyAsText()
            } catch (e: IOException) {
                return VeloxQuantException.MalformedErrorResponse(status.value, "<unreadable body: ${e.message}>")
            }

        if (status == HttpStatusCode.NotFound) {
            if (rawBody.trim() == "Not Found") {
                return VeloxQuantException.UnexpectedRoute(request.url.encodedPath)
            }
            val parsedError = runCatching { json.decodeFromString(WireErrorBody.serializer(), rawBody) }.getOrNull()
            if (parsedError != null) {
                return VeloxQuantException.GenerationFailed(parsedError.error)
            }
            return VeloxQuantException.MalformedErrorResponse(status.value, rawBody)
        }

        val parsedError = runCatching { json.decodeFromString(WireErrorBody.serializer(), rawBody) }.getOrNull()
        if (parsedError != null) {
            return VeloxQuantException.GenerationFailed(parsedError.error)
        }
        return VeloxQuantException.MalformedErrorResponse(status.value, rawBody)
    }

    /** Closes the underlying HTTP client's engine resources. */
    public fun close() {
        httpClient.close()
    }
}
