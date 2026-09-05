/**
 * Multi-turn conversation state built on [VeloxQuantClient] (plan §3.7).
 *
 * **Adopts Go's better-specified contract over TS's**: a failed turn leaves [history]
 * unchanged, so a retry starts from clean state. [send]/[sendStream] only append the user
 * message and assistant reply to [history] after the call completes successfully — on any
 * exception or mid-stream failure, [history] is left exactly as it was before the call.
 */
package io.github.rajveer43.veloxquant.core

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

/** A multi-turn conversation. Construct via [VeloxQuantClient.conversation] or directly. */
public class Conversation internal constructor(
    private val client: VeloxQuantClient,
    private val system: String?,
) {
    private val _history = mutableListOf<Message>()

    /** Messages exchanged so far, oldest first. Never mutated in place — a new snapshot list on every read. */
    public val history: List<Message>
        get() = _history.toList()

    /**
     * Sends [userMessage], appending both it and the assistant's reply to [history] only after
     * the call succeeds. On any exception, [history] is left byte-for-byte unchanged.
     */
    public suspend fun send(userMessage: String): ChatResponse {
        val requestMessages = buildRequestMessages(userMessage)
        val response = client.chat(ChatRequest(messages = requestMessages))
        _history.add(Message.User(userMessage))
        _history.add(Message.Assistant(response.choice.message.content, response.choice.message.toolCalls))
        return response
    }

    /**
     * Streams [userMessage]'s reply, appending both the user message and the fully-assembled
     * assistant reply to [history] only after the stream completes without error. On any
     * mid-stream failure, [history] is left byte-for-byte unchanged — no partial reply is ever
     * appended.
     */
    public fun sendStream(userMessage: String): Flow<ChatChunk> =
        flow {
            val requestMessages = buildRequestMessages(userMessage)
            val assembled = StringBuilder()
            var toolCalls: List<ToolCall>? = null

            client.chatStream(ChatRequest(messages = requestMessages)).collect { chunk ->
                chunk.delta.content?.let { assembled.append(it) }
                chunk.delta.toolCalls?.let { toolCalls = it }
                emit(chunk)
            }

            _history.add(Message.User(userMessage))
            _history.add(Message.Assistant(assembled.toString(), toolCalls))
        }

    private fun buildRequestMessages(userMessage: String): List<Message> {
        val messages = mutableListOf<Message>()
        system?.let { messages.add(Message.System(it)) }
        messages.addAll(_history)
        messages.add(Message.User(userMessage))
        return messages
    }
}

/** Starts a new [Conversation] scoped to this client, optionally seeded with a system prompt. */
public fun VeloxQuantClient.conversation(system: String? = null): Conversation = Conversation(this, system)
