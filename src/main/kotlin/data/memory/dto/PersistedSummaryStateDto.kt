package data.memory.dto

import kotlinx.serialization.Serializable

@Serializable
data class PersistedSummaryStateDto(
    val content: String = "",
    val summarizedMessageCount: Int = 0,
)