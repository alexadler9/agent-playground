package data.memory.dto

import kotlinx.serialization.Serializable

@Serializable
data class PersistedSessionDto(
    val messages: List<PersistedChatMessageDto> = emptyList(),
)

@Serializable
data class PersistedChatMessageDto(
    val id: String,
    val role: String,
    val content: String,
    val createdAt: String,
)