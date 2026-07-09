package data.local

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class OllamaChatRequest(
    val model: String,
    val messages: List<OllamaChatMessageDto>,
    val stream: Boolean,
    val options: OllamaChatOptions? = null,
)

@Serializable
data class OllamaChatMessageDto(
    val role: String,
    val content: String,
)

@Serializable
data class OllamaChatOptions(
    val temperature: Double? = null,
    @SerialName("num_predict")
    val maxTokens: Int? = null,
)

@Serializable
data class OllamaChatResponse(
    val model: String = "",
    val message: OllamaChatMessageDto = OllamaChatMessageDto(
        role = "assistant",
        content = "",
    ),
    val done: Boolean = false,
    @SerialName("total_duration")
    val totalDuration: Long? = null,
    @SerialName("load_duration")
    val loadDuration: Long? = null,
    @SerialName("prompt_eval_count")
    val promptEvalCount: Int? = null,
    @SerialName("eval_count")
    val evalCount: Int? = null,
)
