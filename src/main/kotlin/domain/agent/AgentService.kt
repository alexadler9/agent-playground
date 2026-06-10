package domain.agent

import domain.context.ContextBuilder
import domain.llm.LlmGateway
import domain.memory.SessionHistoryRepository
import domain.model.AgentConfig
import domain.model.AgentReply
import domain.model.ChatMessage
import domain.model.ChatRole
import domain.model.ChatSession
import domain.model.EstimatedTokenStats
import domain.token.TokenEstimator

class AgentService(
    private val session: ChatSession,
    private val config: AgentConfig,
    private val historyRepository: SessionHistoryRepository,
    private val contextBuilder: ContextBuilder,
    private val llmGateway: LlmGateway,
    private val tokenEstimator: TokenEstimator? = null,
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

        val context = contextBuilder.buildContext(
            config = config,
            history = history,
        )

        val estimatedTokenStats = tokenEstimator?.let { estimator ->
            EstimatedTokenStats(
                currentRequestTokens = estimator.estimateTextTokens(text),
                storedHistoryTokens = estimator.estimateMessagesTokens(history),
                contextTokens = estimator.estimateMessagesTokens(context),
                contextMessageCount = context.size,
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

        return replyWithStats
    }

    suspend fun getHistory(): List<ChatMessage> {
        return historyRepository.getMessages(session)
    }

    suspend fun clearHistory() {
        historyRepository.clear(session)
    }
}