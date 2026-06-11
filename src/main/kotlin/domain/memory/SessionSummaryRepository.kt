package domain.memory

import domain.model.ChatSession
import domain.model.SummaryState

interface SessionSummaryRepository {

    suspend fun getSummary(session: ChatSession): SummaryState

    suspend fun saveSummary(
        session: ChatSession,
        summary: SummaryState,
    )

    suspend fun clear(session: ChatSession)
}