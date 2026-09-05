/**
 * Public chat/streaming request, response, and message types (plan §3.1). Every field on
 * [ChatRequest] traces to investigation §1.2's request schema, read directly off
 * `mlx_lm/server.py`'s `do_POST` field extraction — the authoritative request shape, since
 * nothing publishes it as a spec.
 */
package io.github.rajveer43.veloxquant.core

import kotlinx.serialization.Serializable

/** A single chat message. Mirrors the wire's `{role, content, ...}` shape (investigation §1.2). */
@Serializable
public sealed interface Message {
    /** A system-role message (instructions/context set before the conversation). */
    @Serializable
    public data class System(val content: String) : Message

    /** A user-role message. */
    @Serializable
    public data class User(val content: String) : Message

    /** An assistant-role message, optionally carrying tool calls the model requested. */
    @Serializable
    public data class Assistant(val content: String, val toolCalls: List<ToolCall>? = null) : Message

    /** A tool-role message returning a tool call's result, keyed by [toolCallId]. */
    @Serializable
    public data class Tool(val toolCallId: String, val content: String) : Message
}

/** A tool call the assistant requested, per OpenAI's `tool_calls` shape. */
@Serializable
public data class ToolCall(
    val id: String,
    val name: String,
    val argumentsJson: String,
)

/** A tool definition offered to the model, per the wire's `tools` array (investigation §1.2). */
@Serializable
public data class ToolDefinition(
    val type: String = "function",
    val function: FunctionDefinition,
)

/** The name/description/JSON-schema-parameters of a callable tool, per OpenAI's `function` shape. */
@Serializable
public data class FunctionDefinition(
    val name: String,
    val description: String? = null,
    val parameters: kotlinx.serialization.json.JsonObject? = null,
)

/** Reported completion stop reason. */
public enum class FinishReason { STOP, LENGTH, TOOL_CALLS }

/** Token usage accounting, present on both non-streaming responses and the final SSE frame. */
@Serializable
public data class Usage(
    val promptTokens: Int,
    val completionTokens: Int,
    val totalTokens: Int,
    val cachedTokens: Int? = null,
)

/**
 * Chat completion request. Every field traces to investigation §1.2's request schema —
 * fields not yet exposed here (e.g. `logit_bias`, `chat_template_kwargs`, `role_mapping`,
 * `draft_model`, `num_draft_tokens`, penalty/context-size knobs) are intentionally still
 * absent pending later phases' wire-serialization work, not silently dropped by design; do
 * not add a field here that investigation §1.2 does not list.
 */
@Serializable
public data class ChatRequest(
    val messages: List<Message>,
    val model: String? = null,
    val maxTokens: Int? = null,
    val temperature: Double? = null,
    val topP: Double? = null,
    val topK: Int? = null,
    val stop: List<String>? = null,
    val tools: List<ToolDefinition>? = null,
    val responseFormat: ResponseFormat? = null,
    val seed: Int? = null,
    val stream: Boolean = false,
    val streamIncludeUsage: Boolean = false,
)

/** A single choice in a non-streaming [ChatResponse]. */
@Serializable
public data class Choice(
    val index: Int,
    val message: AssistantMessage,
    val finishReason: FinishReason?,
)

/** The assistant's reply content within a [Choice] or [Delta]. */
@Serializable
public data class AssistantMessage(
    val role: String = "assistant",
    val content: String,
    val toolCalls: List<ToolCall>? = null,
)

/** Non-streaming chat completion response (investigation §1.3). */
@Serializable
public data class ChatResponse(
    val id: String,
    val model: String,
    val choice: Choice,
    val usage: Usage,
)

/** Incremental content within one [ChatChunk]. */
@Serializable
public data class Delta(
    val role: String? = null,
    val content: String? = null,
    val toolCalls: List<ToolCall>? = null,
)

/**
 * One SSE-streamed chat completion chunk (investigation §1.4). [usage] is populated only on
 * the final usage-only frame (`stream_options.include_usage`) — every other chunk leaves it
 * `null` rather than defaulting to a zeroed [Usage], so a caller cannot mistake "not reported
 * yet" for "zero tokens."
 */
@Serializable
public data class ChatChunk(
    val id: String,
    val delta: Delta,
    val finishReason: FinishReason?,
    val usage: Usage? = null,
)
