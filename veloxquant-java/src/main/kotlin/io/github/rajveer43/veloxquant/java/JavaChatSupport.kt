/**
 * `.future()`-based and callback-based wrappers over [VeloxQuantClient]'s coroutines API, for
 * Java/Android-Java callers who don't want to touch Kotlin coroutines directly (plan §3.1).
 */
package io.github.rajveer43.veloxquant.java

import io.github.rajveer43.veloxquant.core.ChatChunk
import io.github.rajveer43.veloxquant.core.ChatRequest
import io.github.rajveer43.veloxquant.core.ChatResponse
import io.github.rajveer43.veloxquant.core.VeloxQuantClient
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onCompletion
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.future.future
import java.util.concurrent.CompletableFuture
import java.util.function.Consumer

/** `.future()`-based non-streaming chat completion for Java/Android-Java callers. */
public fun VeloxQuantClient.chatAsync(request: ChatRequest): CompletableFuture<ChatResponse> =
    CoroutineScope(Dispatchers.Default).future { chat(request) }

/**
 * Callback-based streaming chat completion for Java/Android-Java callers — a purpose-built
 * method rather than a `Flow`-to-Java reactive-interop shim (plan §3.1). The returned
 * [Cancellable] wraps the underlying [Job] so callers can cancel a stream explicitly.
 */
public fun VeloxQuantClient.streamBlocking(
    request: ChatRequest,
    onChunk: Consumer<ChatChunk>,
    onComplete: Runnable,
    onError: Consumer<Throwable>,
): Cancellable {
    // `catch` intercepts an upstream exception and lets the flow complete normally afterward,
    // so onCompletion alone can't distinguish "finished cleanly" from "finished after an
    // error" — track it explicitly instead of relying on onCompletion's cause parameter.
    var failed = false
    val job =
        chatStream(request)
            .onEach { onChunk.accept(it) }
            .catch { throwable ->
                failed = true
                onError.accept(throwable)
            }
            .onCompletion { cause -> if (cause == null && !failed) onComplete.run() }
            .launchIn(CoroutineScope(Dispatchers.Default))
    return Cancellable(job)
}
