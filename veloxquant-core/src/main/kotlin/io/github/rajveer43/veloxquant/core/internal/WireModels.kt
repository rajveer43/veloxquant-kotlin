/**
 * Wire-shaped DTOs matching `mlx_lm/server.py`'s actual JSON field names (snake_case,
 * investigation §1.2/§1.3/§1.4) — kept separate from the public, idiomatic-Kotlin types in
 * `ChatModels.kt` so the public API can stay stable independent of wire-shape details.
 */
package io.github.rajveer43.veloxquant.core.internal

import io.github.rajveer43.veloxquant.core.AssistantMessage
import io.github.rajveer43.veloxquant.core.ChatChunk
import io.github.rajveer43.veloxquant.core.ChatRequest
import io.github.rajveer43.veloxquant.core.ChatResponse
import io.github.rajveer43.veloxquant.core.Choice
import io.github.rajveer43.veloxquant.core.Delta
import io.github.rajveer43.veloxquant.core.FinishReason
import io.github.rajveer43.veloxquant.core.Message
import io.github.rajveer43.veloxquant.core.ToolCall
import io.github.rajveer43.veloxquant.core.ToolDefinition
import io.github.rajveer43.veloxquant.core.Usage
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
internal data class WireToolCallFunction(val name: String, val arguments: String)

@Serializable
internal data class WireToolCall(val id: String, val type: String = "function", val function: WireToolCallFunction)

@Serializable
internal data class WireToolDefinitionFunction(
    val name: String,
    val description: String? = null,
    val parameters: kotlinx.serialization.json.JsonObject? = null,
)

@Serializable
internal data class WireToolDefinition(val type: String = "function", val function: WireToolDefinitionFunction)

@Serializable
internal data class WireMessage(
    val role: String,
    val content: String,
    @SerialName("tool_calls") val toolCalls: List<WireToolCall>? = null,
    @SerialName("tool_call_id") val toolCallId: String? = null,
)

@Serializable
internal data class WireChatRequest(
    val model: String? = null,
    val messages: List<WireMessage>,
    val stream: Boolean = false,
    @SerialName("stream_options") val streamOptions: WireStreamOptions? = null,
    @SerialName("max_tokens") val maxTokens: Int? = null,
    val temperature: Double? = null,
    @SerialName("top_p") val topP: Double? = null,
    @SerialName("top_k") val topK: Int? = null,
    val stop: List<String>? = null,
    val tools: List<WireToolDefinition>? = null,
    @SerialName("response_format") val responseFormat: kotlinx.serialization.json.JsonElement? = null,
    val seed: Int? = null,
)

@Serializable
internal data class WireStreamOptions(
    @SerialName("include_usage") val includeUsage: Boolean,
)

@Serializable
internal data class WireUsage(
    @SerialName("prompt_tokens") val promptTokens: Int,
    @SerialName("completion_tokens") val completionTokens: Int,
    @SerialName("total_tokens") val totalTokens: Int,
    @SerialName("prompt_tokens_details") val promptTokensDetails: WirePromptTokensDetails? = null,
)

@Serializable
internal data class WirePromptTokensDetails(
    @SerialName("cached_tokens") val cachedTokens: Int? = null,
)

@Serializable
internal data class WireChoiceMessage(
    val role: String = "assistant",
    val content: String,
    @SerialName("tool_calls") val toolCalls: List<WireToolCall>? = null,
)

@Serializable
internal data class WireChoice(
    val index: Int,
    @SerialName("finish_reason") val finishReason: String? = null,
    val message: WireChoiceMessage? = null,
)

@Serializable
internal data class WireChatResponse(
    val id: String,
    val model: String,
    val choices: List<WireChoice>,
    val usage: WireUsage,
)

@Serializable
internal data class WireDelta(
    val role: String? = null,
    val content: String? = null,
    @SerialName("tool_calls") val toolCalls: List<WireToolCall>? = null,
)

@Serializable
internal data class WireChunkChoice(
    val index: Int,
    @SerialName("finish_reason") val finishReason: String? = null,
    val delta: WireDelta = WireDelta(),
)

@Serializable
internal data class WireChatChunk(
    val id: String,
    val model: String,
    val choices: List<WireChunkChoice> = emptyList(),
    val usage: WireUsage? = null,
)

@Serializable
internal data class WireErrorBody(val error: String)

internal fun ChatRequest.toWire(defaultModel: String?): WireChatRequest =
    WireChatRequest(
        model = model ?: defaultModel,
        messages = messages.map { it.toWire() },
        stream = stream,
        streamOptions = if (streamIncludeUsage) WireStreamOptions(includeUsage = true) else null,
        maxTokens = maxTokens,
        temperature = temperature,
        topP = topP,
        topK = topK,
        stop = stop,
        tools = tools?.map { it.toWire() },
        responseFormat = null,
        seed = seed,
    )

private fun Message.toWire(): WireMessage =
    when (this) {
        is Message.System -> WireMessage(role = "system", content = content)
        is Message.User -> WireMessage(role = "user", content = content)
        is Message.Assistant ->
            WireMessage(role = "assistant", content = content, toolCalls = toolCalls?.map { it.toWire() })
        is Message.Tool -> WireMessage(role = "tool", content = content, toolCallId = toolCallId)
    }

private fun ToolCall.toWire(): WireToolCall {
    val function = WireToolCallFunction(name = name, arguments = argumentsJson)
    return WireToolCall(id = id, function = function)
}

private fun WireToolCall.toPublic(): ToolCall =
    ToolCall(id = id, name = function.name, argumentsJson = function.arguments)

private fun ToolDefinition.toWire(): WireToolDefinition =
    WireToolDefinition(
        type = type,
        function =
            WireToolDefinitionFunction(
                name = function.name,
                description = function.description,
                parameters = function.parameters,
            ),
    )

internal fun WireChatResponse.toPublic(): ChatResponse {
    val wireChoice =
        choices.firstOrNull()
            ?: error("chat completion response had no choices — this indicates a malformed server response")
    val wireMessage =
        wireChoice.message
            ?: error("chat completion response's choice had no message")
    return ChatResponse(
        id = id,
        model = model,
        choice =
            Choice(
                index = wireChoice.index,
                message =
                    AssistantMessage(
                        role = wireMessage.role,
                        content = wireMessage.content,
                        toolCalls = wireMessage.toolCalls?.map { it.toPublic() },
                    ),
                finishReason = wireChoice.finishReason?.toFinishReason(),
            ),
        usage = usage.toPublic(),
    )
}

internal fun WireChatChunk.toPublic(): ChatChunk {
    val wireChoice = choices.firstOrNull()
    return ChatChunk(
        id = id,
        delta =
            Delta(
                role = wireChoice?.delta?.role,
                content = wireChoice?.delta?.content,
                toolCalls = wireChoice?.delta?.toolCalls?.map { it.toPublic() },
            ),
        finishReason = wireChoice?.finishReason?.toFinishReason(),
        usage = usage?.toPublic(),
    )
}

internal fun WireUsage.toPublic(): Usage =
    Usage(
        promptTokens = promptTokens,
        completionTokens = completionTokens,
        totalTokens = totalTokens,
        cachedTokens = promptTokensDetails?.cachedTokens,
    )

private fun String.toFinishReason(): FinishReason? =
    when (this) {
        "stop" -> FinishReason.STOP
        "length" -> FinishReason.LENGTH
        "tool_calls" -> FinishReason.TOOL_CALLS
        else -> null
    }
