package domain.memory

import domain.model.ChatMessage
import domain.model.ChatSession
import domain.model.SummaryState


class HistoryCompressionManager(
    private val summaryRepository: SessionSummaryRepository,
    private val historySummarizer: HistorySummarizer,
    private val recentMessagesCount: Int,
    private val summarizeBatchSize: Int,
) {

    suspend fun compressIfNeeded(
        session: ChatSession,
        history: List<ChatMessage>,
    ): SummaryState {
        val currentSummary = summaryRepository.getSummary(session)

        val unsummarizedMessages = history.drop(
            currentSummary.summarizedMessageCount
        )

        if (unsummarizedMessages.size <= recentMessagesCount + summarizeBatchSize) {
            return currentSummary
        }

        val messagesToSummarize = unsummarizedMessages.take(summarizeBatchSize)

        val updatedSummaryContent = historySummarizer.summarize(
            previousSummary = currentSummary.content,
            messagesToSummarize = messagesToSummarize,
        )

        val updatedSummary = SummaryState(
            content = updatedSummaryContent,
            summarizedMessageCount = currentSummary.summarizedMessageCount +
                    messagesToSummarize.size,
        )

        summaryRepository.saveSummary(
            session = session,
            summary = updatedSummary,
        )

        return updatedSummary
    }
}