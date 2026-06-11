package domain.agent

import domain.context.ContextBuilder
import domain.llm.LlmGateway
import domain.memory.HistoryCompressionManager
import domain.memory.SessionHistoryRepository
import domain.memory.SessionSummaryRepository
import domain.model.AgentConfig
import domain.model.AgentReply
import domain.model.ChatMessage
import domain.model.ChatRole
import domain.model.ChatSession
import domain.model.EstimatedTokenStats
import domain.model.SummaryState
import domain.token.TokenEstimator

class AgentService(
    private val session: ChatSession,
    private val config: AgentConfig,
    private val historyRepository: SessionHistoryRepository,
    private val summaryRepository: SessionSummaryRepository? = null,
    private val contextBuilder: ContextBuilder,
    private val llmGateway: LlmGateway,
    private val tokenEstimator: TokenEstimator? = null,
    private val historyCompressionManager: HistoryCompressionManager? = null,
) {

    suspend fun sendMessage(text: String): AgentReply {
        val userMessage = ChatMessage(
            role = ChatRole.USER,
            content = text,
        )

        historyRepository.appendMessage(
            session = session,
            message = userMessage,
        )

        val history = historyRepository.getMessages(session)

        val summary = summaryRepository?.getSummary(session)
            ?: SummaryState.Empty

        val context = contextBuilder.buildContext(
            config = config,
            history = history,
            summary = summary,
        )

        val estimatedTokenStats = tokenEstimator?.let { estimator ->
            val systemMessage = ChatMessage(
                role = ChatRole.SYSTEM,
                content = config.systemPrompt,
            )

            val fullContextMessages = listOf(systemMessage) + history

            val fullContextTokens = estimator.estimateMessagesTokens(fullContextMessages)
            val actualContextTokens = estimator.estimateMessagesTokens(context)

            EstimatedTokenStats(
                currentRequestTokens = estimator.estimateTextTokens(text),
                storedHistoryTokens = estimator.estimateMessagesTokens(history),
                fullContextTokens = fullContextTokens,
                actualContextTokens = actualContextTokens,
                savedContextTokens = fullContextTokens - actualContextTokens,
                contextMessageCount = context.size,
                summarizedMessageCount = summary.summarizedMessageCount,
            )
        }

        val reply = llmGateway.sendMessages(
            config = config,
            messages = context,
        )

        val replyWithStats = reply.copy(
            estimatedTokenStats = estimatedTokenStats,
        )

        historyRepository.appendMessage(
            session = session,
            message = reply.message,
        )

        val updatedHistory = historyRepository.getMessages(session)

        historyCompressionManager?.compressIfNeeded(
            session = session,
            history = updatedHistory,
        )

        return replyWithStats
    }

    suspend fun getHistory(): List<ChatMessage> {
        return historyRepository.getMessages(session)
    }

    suspend fun clearHistory() {
        historyRepository.clear(session)
        summaryRepository?.clear(session)
    }
}