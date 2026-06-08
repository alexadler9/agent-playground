package data.llm

import data.llm.dto.ChatMessageDto
import domain.model.ChatMessage
import domain.model.ChatRole

fun ChatMessage.toDto(): ChatMessageDto {
    return ChatMessageDto(
        role = role.toApiRole(),
        content = content,
    )
}

private fun ChatRole.toApiRole(): String {
    return when (this) {
        ChatRole.SYSTEM -> "system"
        ChatRole.USER -> "user"
        ChatRole.ASSISTANT -> "assistant"
    }
}