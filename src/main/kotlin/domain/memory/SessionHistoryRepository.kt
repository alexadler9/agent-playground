package domain.memory

import domain.model.ChatMessage
import domain.model.ChatSession

interface SessionHistoryRepository {

    suspend fun appendMessage(
        session: ChatSession,
        message: ChatMessage,
    )

    suspend fun getMessages(session: ChatSession): List<ChatMessage>

    suspend fun clear(session: ChatSession)
}