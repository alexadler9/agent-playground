package data.memory

import domain.memory.SessionHistoryRepository
import domain.model.ChatMessage
import domain.model.ChatSession
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

class InMemorySessionHistoryRepository : SessionHistoryRepository {

    private val mutex = Mutex()
    private val messagesBySessionId = mutableMapOf<String, MutableList<ChatMessage>>()

    override suspend fun appendMessage(
        session: ChatSession,
        message: ChatMessage,
    ) {
        mutex.withLock {
            val messages = messagesBySessionId.getOrPut(session.id) {
                mutableListOf()
            }

            messages += message
        }
    }

    override suspend fun getMessages(session: ChatSession): List<ChatMessage> {
        return mutex.withLock {
            messagesBySessionId[session.id]
                ?.toList()
                .orEmpty()
        }
    }

    override suspend fun clear(session: ChatSession) {
        mutex.withLock {
            messagesBySessionId.remove(session.id)
        }
    }
}