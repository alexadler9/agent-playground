package data.memory.dto

import kotlinx.serialization.Serializable

@Serializable
data class PersistedFactMemoryDto(
    val facts: Map<String, String> = emptyMap(),
)