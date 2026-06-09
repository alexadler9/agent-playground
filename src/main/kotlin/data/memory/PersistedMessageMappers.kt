package data.memory

import data.memory.dto.PersistedChatMessageDto
import domain.model.ChatMessage
import domain.model.ChatRole
import java.time.Instant

fun ChatMessage.toPersistedDto(): PersistedChatMessageDto {
    return PersistedChatMessageDto(
        id = id,
        role = role.name,
        content = content,
        createdAt = createdAt.toString(),
    )
}

fun PersistedChatMessageDto.toDomain(): ChatMessage {
    return ChatMessage(
        id = id,
        role = ChatRole.valueOf(role),
        content = content,
        createdAt = Instant.parse(createdAt),
    )
}