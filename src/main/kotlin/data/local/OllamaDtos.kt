package data.local

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class OllamaGenerateRequest(
    val model: String,
    val prompt: String,
    val stream: Boolean,
    val system: String? = null,
    val options: OllamaGenerateOptions? = null,
)

@Serializable
data class OllamaGenerateOptions(
    @SerialName("temperature")
    val temperature: Double? = null,
    @SerialName("num_predict")
    val maxTokens: Int? = null,
)

@Serializable
data class OllamaGenerateResponse(
    val model: String = "",
    val response: String = "",
    val done: Boolean = false,
    @SerialName("done_reason")
    val doneReason: String? = null,
    @SerialName("total_duration")
    val totalDuration: Long? = null,
    @SerialName("load_duration")
    val loadDuration: Long? = null,
    @SerialName("prompt_eval_count")
    val promptEvalCount: Int? = null,
    @SerialName("eval_count")
    val evalCount: Int? = null,
)