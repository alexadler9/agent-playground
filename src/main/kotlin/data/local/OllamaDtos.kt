package data.local

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class OllamaGenerateRequest(
    val model: String,
    val prompt: String,
    val system: String? = null,
    val stream: Boolean,
    val options: OllamaGenerateOptions? = null,
)

@Serializable
data class OllamaGenerateOptions(
    val temperature: Double? = null,

    @SerialName("num_predict")
    val maxTokens: Int? = null,
)

@Serializable
data class OllamaGenerateResponse(
    val response: String = "",

    @SerialName("prompt_eval_count")
    val promptEvalCount: Int? = null,

    @SerialName("eval_count")
    val evalCount: Int? = null,
)