package data.llm.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class ChatCompletionRequestDto(
    val model: String,
    val messages: List<ChatMessageDto>,

    @SerialName("max_tokens")
    val maxTokens: Int? = null,

    val temperature: Double? = null,
)

@Serializable
data class ChatMessageDto(
    val role: String,
    val content: String,
)

@Serializable
data class ChatCompletionResponseDto(
    val choices: List<ChoiceDto>,
    val usage: UsageDto? = null,
)

@Serializable
data class ChoiceDto(
    val message: ChatMessageDto,
)

@Serializable
data class UsageDto(
    @SerialName("prompt_tokens")
    val promptTokens: Int? = null,

    @SerialName("completion_tokens")
    val completionTokens: Int? = null,

    @SerialName("total_tokens")
    val totalTokens: Int? = null,
)