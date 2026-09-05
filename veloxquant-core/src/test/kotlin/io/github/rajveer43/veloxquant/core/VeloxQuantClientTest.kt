package io.github.rajveer43.veloxquant.core

import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.engine.mock.respondError
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.io.IOException

class VeloxQuantClientTest {
    private val testJson = Json { ignoreUnknownKeys = true }

    private fun clientWith(engine: MockEngine): VeloxQuantClient {
        val httpClient =
            HttpClient(engine) {
                install(ContentNegotiation) { json(testJson) }
            }
        return VeloxQuantClient.createWithClient("http://127.0.0.1:8000", httpClient)
    }

    private fun request() = ChatRequest(messages = listOf(Message.User("hello")))

    private fun sseFrame(json: String) = "data: $json\n\n"

    @Test
    fun `chat() parses a normal non-streaming response`() =
        runTest {
            val engine =
                MockEngine { _ ->
                    respond(
                        content =
                            """
                            {"id":"chatcmpl-1","object":"chat.completion","model":"m","created":1,
                             "choices":[{"index":0,"finish_reason":"stop",
                               "message":{"role":"assistant","content":"hi there"}}],
                             "usage":{"prompt_tokens":5,"completion_tokens":2,"total_tokens":7}}
                            """.trimIndent(),
                        status = HttpStatusCode.OK,
                        headers = headersOf(HttpHeaders.ContentType, "application/json"),
                    )
                }

            val response = clientWith(engine).chat(request())

            assertEquals("chatcmpl-1", response.id)
            assertEquals("hi there", response.choice.message.content)
            assertEquals(FinishReason.STOP, response.choice.finishReason)
            assertEquals(7, response.usage.totalTokens)
        }

    @Test
    fun `chatStream() parses chunks and skips keepalive comment lines`() =
        runTest {
            val chunk1 =
                """{"id":"c1","object":"chat.completion.chunk","model":"m",""" +
                    """"choices":[{"index":0,"finish_reason":null,"delta":{"role":"assistant","content":"He"}}]}"""
            val chunk2 =
                """{"id":"c1","object":"chat.completion.chunk","model":"m",""" +
                    """"choices":[{"index":0,"finish_reason":"stop","delta":{"content":"llo"}}]}"""
            val sse =
                buildString {
                    append(": keepalive 1/10\n\n")
                    append(sseFrame(chunk1))
                    append(": keepalive 5/10\n\n")
                    append(sseFrame(chunk2))
                    append("data: [DONE]\n\n")
                }
            val engine =
                MockEngine { _ ->
                    respond(
                        content = sse,
                        status = HttpStatusCode.OK,
                        headers = headersOf(HttpHeaders.ContentType, "text/event-stream"),
                    )
                }

            val chunks = clientWith(engine).chatStream(request()).toList()

            assertEquals(2, chunks.size)
            assertEquals("He", chunks[0].delta.content)
            assertNull(chunks[0].finishReason)
            assertEquals("llo", chunks[1].delta.content)
            assertEquals(FinishReason.STOP, chunks[1].finishReason)
        }

    @Test
    fun `chatStream() populates usage on the final usage-only frame`() =
        runTest {
            val chunk =
                """{"id":"c1","object":"chat.completion.chunk","model":"m",""" +
                    """"choices":[{"index":0,"finish_reason":"stop","delta":{"content":"hi"}}]}"""
            val usageFrame =
                """{"id":"c1","object":"chat.completion","model":"m","choices":[],""" +
                    """"usage":{"prompt_tokens":3,"completion_tokens":1,"total_tokens":4}}"""
            val sse =
                buildString {
                    append(sseFrame(chunk))
                    append(sseFrame(usageFrame))
                    append("data: [DONE]\n\n")
                }
            val engine =
                MockEngine { _ ->
                    respond(
                        content = sse,
                        status = HttpStatusCode.OK,
                        headers = headersOf(HttpHeaders.ContentType, "text/event-stream"),
                    )
                }

            val chunks = clientWith(engine).chatStream(request()).toList()

            assertEquals(2, chunks.size)
            assertNull(chunks[0].usage)
            assertEquals(4, chunks[1].usage?.totalTokens)
        }

    @Test
    fun `404 with JSON error body maps to GenerationFailed`() =
        runTest {
            val engine =
                MockEngine { _ ->
                    respond(
                        content = """{"error":"model failed to load"}""",
                        status = HttpStatusCode.NotFound,
                        headers = headersOf(HttpHeaders.ContentType, "application/json"),
                    )
                }

            val exception = assertThrows<VeloxQuantException.GenerationFailed> { clientWith(engine).chat(request()) }
            assertEquals("model failed to load", exception.serverMessage)
        }

    @Test
    fun `404 with plain-text Not Found maps to UnexpectedRoute`() =
        runTest {
            val engine =
                MockEngine { _ ->
                    respond(
                        content = "Not Found",
                        status = HttpStatusCode.NotFound,
                        headers = headersOf(HttpHeaders.ContentType, "text/plain"),
                    )
                }

            assertThrows<VeloxQuantException.UnexpectedRoute> { clientWith(engine).chat(request()) }
        }

    @Test
    fun `404 with malformed non-JSON body maps to MalformedErrorResponse`() =
        runTest {
            val engine =
                MockEngine { _ ->
                    respond(
                        content = "<html>whoops</html>",
                        status = HttpStatusCode.NotFound,
                        headers = headersOf(HttpHeaders.ContentType, "text/html"),
                    )
                }

            val exception =
                assertThrows<VeloxQuantException.MalformedErrorResponse> { clientWith(engine).chat(request()) }
            assertEquals(404, exception.statusCode)
        }

    @Test
    fun `non-404 non-JSON error body maps to MalformedErrorResponse`() =
        runTest {
            val engine =
                MockEngine { _ ->
                    respondError(HttpStatusCode.InternalServerError, "connection reset mid-response")
                }

            val exception =
                assertThrows<VeloxQuantException.MalformedErrorResponse> { clientWith(engine).chat(request()) }
            assertEquals(500, exception.statusCode)
        }

    @Test
    fun `500 with JSON error body maps to GenerationFailed`() =
        runTest {
            val engine =
                MockEngine { _ ->
                    respond(
                        content = """{"error":"boom"}""",
                        status = HttpStatusCode.InternalServerError,
                        headers = headersOf(HttpHeaders.ContentType, "application/json"),
                    )
                }

            val exception = assertThrows<VeloxQuantException.GenerationFailed> { clientWith(engine).chat(request()) }
            assertEquals("boom", exception.serverMessage)
        }

    @Test
    fun `connection failure maps to RuntimeUnreachable`() =
        runTest {
            val engine =
                MockEngine { _ ->
                    throw IOException("connection refused")
                }

            val exception =
                assertThrows<VeloxQuantException.RuntimeUnreachable> { clientWith(engine).chat(request()) }
            assertTrue(exception.message!!.contains("http://127.0.0.1:8000"))
        }
}
