package domain.memory

import domain.model.ChatSession
import domain.model.FactMemory

interface FactMemoryRepository {

    suspend fun getFacts(session: ChatSession): FactMemory

    suspend fun saveFacts(
        session: ChatSession,
        facts: FactMemory,
    )

    suspend fun clear(session: ChatSession)
}