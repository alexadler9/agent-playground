package data.local

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class OllamaChatRequest(
    val model: String,
    val messages: List<OllamaChatMessage>,
    val stream: Boolean,
    val options: OllamaChatOptions? = null,
)

@Serializable
data class OllamaChatMessage(
    val role: String,
    val content: String,
)

@Serializable
data class OllamaChatOptions(
    val temperature: Double? = null,

    @SerialName("num_predict")
    val maxTokens: Int? = null,

    @SerialName("num_ctx")
    val contextWindow: Int? = null,
)

@Serializable
data class OllamaChatResponse(
    val message: OllamaChatMessage? = null,

    @SerialName("prompt_eval_count")
    val promptEvalCount: Int? = null,

    @SerialName("eval_count")
    val evalCount: Int? = null,
)