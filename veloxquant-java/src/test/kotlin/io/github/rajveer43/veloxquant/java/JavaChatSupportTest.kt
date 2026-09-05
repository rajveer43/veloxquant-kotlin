package io.github.rajveer43.veloxquant.java

import io.github.rajveer43.veloxquant.core.ChatRequest
import io.github.rajveer43.veloxquant.core.Message
import io.github.rajveer43.veloxquant.core.VeloxQuantClient
import io.github.rajveer43.veloxquant.core.VeloxQuantException
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

class JavaChatSupportTest {
    // MockEngine is itself a valid HttpClientEngine, so the public create(engine = ...)
    // factory is enough — no need to reach veloxquant-core's internal test constructor.
    private fun clientWith(engine: MockEngine): VeloxQuantClient =
        VeloxQuantClient.create(baseUrl = "http://127.0.0.1:8000", engine = engine)

    private fun request() = ChatRequest(messages = listOf(Message.User("hello")))

    @Test
    fun `streamBlocking delivers chunks then onComplete, never onError, on a clean stream`() {
        val chunk =
            """{"id":"c1","object":"chat.completion.chunk","model":"m",""" +
                """"choices":[{"index":0,"finish_reason":null,"delta":{"content":"Hi"}}]}"""
        val sse =
            buildString {
                append("data: $chunk\n\n")
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

        val chunkCount = AtomicInteger(0)
        val completeLatch = CountDownLatch(1)
        val errorSeen = AtomicInteger(0)

        clientWith(engine).streamBlocking(
            request(),
            onChunk = { chunkCount.incrementAndGet() },
            onComplete = { completeLatch.countDown() },
            onError = { errorSeen.incrementAndGet() },
        )

        assertTrue(completeLatch.await(5, TimeUnit.SECONDS), "expected onComplete to fire")
        assertEquals(1, chunkCount.get())
        assertEquals(0, errorSeen.get())
    }

    @Test
    fun `streamBlocking calls onError, never onComplete, when the connection fails`() {
        val engine = MockEngine { _ -> throw java.io.IOException("refused") }

        val errorLatch = CountDownLatch(1)
        val completeCalled = AtomicInteger(0)
        var capturedError: Throwable? = null

        clientWith(engine).streamBlocking(
            request(),
            onChunk = { },
            onComplete = { completeCalled.incrementAndGet() },
            onError = {
                capturedError = it
                errorLatch.countDown()
            },
        )

        assertTrue(errorLatch.await(5, TimeUnit.SECONDS), "expected onError to fire")
        assertEquals(0, completeCalled.get())
        assertTrue(capturedError is VeloxQuantException.RuntimeUnreachable)
    }

    @Test
    fun `Cancellable actually cancels the underlying job`() {
        val engine =
            MockEngine { _ ->
                respond(
                    content = "data: [DONE]\n\n",
                    status = HttpStatusCode.OK,
                    headers = headersOf(HttpHeaders.ContentType, "text/event-stream"),
                )
            }

        val cancellable =
            clientWith(engine).streamBlocking(
                request(),
                onChunk = { },
                onComplete = { },
                onError = { },
            )
        cancellable.cancel()

        assertFalse(cancellable.isActive)
    }
}
