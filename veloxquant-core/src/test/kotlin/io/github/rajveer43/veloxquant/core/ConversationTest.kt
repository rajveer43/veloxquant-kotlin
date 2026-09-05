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
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

private fun clientWith(engine: MockEngine): VeloxQuantClient {
    val testJson = Json { ignoreUnknownKeys = true }
    val httpClient =
        HttpClient(engine) {
            install(ContentNegotiation) { json(testJson) }
        }
    return VeloxQuantClient.createWithClient("http://127.0.0.1:8000", httpClient)
}

private fun chatResponseBody(content: String) =
    """
    {"id":"chatcmpl-1","object":"chat.completion","model":"m","created":1,
     "choices":[{"index":0,"finish_reason":"stop","message":{"role":"assistant","content":"$content"}}],
     "usage":{"prompt_tokens":5,"completion_tokens":2,"total_tokens":7}}
    """.trimIndent()

class ConversationTest {
    @Test
    fun `send() appends the user message and assistant reply to history on success`() =
        runTest {
            val engine =
                MockEngine { _ ->
                    respond(
                        content = chatResponseBody("hi there"),
                        status = HttpStatusCode.OK,
                        headers = headersOf(HttpHeaders.ContentType, "application/json"),
                    )
                }
            val conversation = clientWith(engine).conversation()

            conversation.send("hello")

            assertEquals(2, conversation.history.size)
            assertEquals(Message.User("hello"), conversation.history[0])
            assertEquals("hi there", (conversation.history[1] as Message.Assistant).content)
        }

    @Test
    fun `a failed send() leaves history byte-for-byte unchanged`() =
        runTest {
            val engine =
                MockEngine { _ ->
                    respondError(HttpStatusCode.InternalServerError, "boom")
                }
            val conversation = clientWith(engine).conversation()
            val historyBefore = conversation.history

            assertThrows<VeloxQuantException> { conversation.send("hello") }

            assertEquals(historyBefore, conversation.history)
            assertTrue(conversation.history.isEmpty())
        }

    @Test
    fun `a second failed turn after a successful one leaves history at the prior successful state`() =
        runTest {
            var callCount = 0
            val engine =
                MockEngine { _ ->
                    callCount++
                    if (callCount == 1) {
                        respond(
                            content = chatResponseBody("first reply"),
                            status = HttpStatusCode.OK,
                            headers = headersOf(HttpHeaders.ContentType, "application/json"),
                        )
                    } else {
                        respondError(HttpStatusCode.InternalServerError, "boom")
                    }
                }
            val conversation = clientWith(engine).conversation()
            conversation.send("first message")
            val historyAfterFirstSuccess = conversation.history

            assertThrows<VeloxQuantException> { conversation.send("second message") }

            assertEquals(historyAfterFirstSuccess, conversation.history)
            assertEquals(2, conversation.history.size)
        }

    @Test
    fun `sendStream() appends the assembled reply to history only after a clean completion`() =
        runTest {
            val chunk1 =
                """{"id":"c1","object":"chat.completion.chunk","model":"m",""" +
                    """"choices":[{"index":0,"finish_reason":null,"delta":{"role":"assistant","content":"He"}}]}"""
            val chunk2 =
                """{"id":"c1","object":"chat.completion.chunk","model":"m",""" +
                    """"choices":[{"index":0,"finish_reason":"stop","delta":{"content":"llo"}}]}"""
            val sse = "data: $chunk1\n\ndata: $chunk2\n\ndata: [DONE]\n\n"
            val engine =
                MockEngine { _ ->
                    respond(
                        content = sse,
                        status = HttpStatusCode.OK,
                        headers = headersOf(HttpHeaders.ContentType, "text/event-stream"),
                    )
                }
            val conversation = clientWith(engine).conversation()

            conversation.sendStream("hi").toList()

            assertEquals(2, conversation.history.size)
            assertEquals("Hello", (conversation.history[1] as Message.Assistant).content)
        }

    @Test
    fun `a mid-stream failure in sendStream() leaves history byte-for-byte unchanged`() =
        runTest {
            val engine =
                MockEngine { _ ->
                    respondError(HttpStatusCode.InternalServerError, "boom")
                }
            val conversation = clientWith(engine).conversation()
            val historyBefore = conversation.history

            assertThrows<VeloxQuantException> {
                conversation.sendStream("hi").catch { throw it }.toList()
            }

            assertEquals(historyBefore, conversation.history)
            assertTrue(conversation.history.isEmpty())
        }
}
