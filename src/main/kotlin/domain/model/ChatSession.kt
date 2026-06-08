package domain.model

import java.util.UUID

data class ChatSession(
    val id: String = UUID.randomUUID().toString(),
)