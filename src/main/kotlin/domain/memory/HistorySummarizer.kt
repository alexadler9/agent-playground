package domain.memory

import domain.model.ChatMessage

interface HistorySummarizer {

    suspend fun summarize(
        previousSummary: String,
        messagesToSummarize: List<ChatMessage>,
    ): String
}