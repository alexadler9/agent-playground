package domain.rag

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class RagGroundedLlmResponseDto(
    val answer: String = "",
    val sources: List<RagGroundedSourceDto> = emptyList(),
    val quotes: List<RagGroundedQuoteDto> = emptyList(),
)

@Serializable
data class RagGroundedSourceDto(
    val source: String = "",
    val section: String = "",
    @SerialName("chunk_id")
    val chunkId: String = "",
)

@Serializable
data class RagGroundedQuoteDto(
    val source: String = "",
    val section: String = "",
    @SerialName("chunk_id")
    val chunkId: String = "",
    val text: String = "",
)