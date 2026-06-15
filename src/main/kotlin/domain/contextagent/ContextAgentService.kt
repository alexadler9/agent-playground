package domain.contextagent

import domain.context.ContextBuilder
import domain.llm.LlmGateway
import domain.memory.BranchManager
import domain.memory.FactMemoryRepository
import domain.memory.FactMemoryUpdater
import domain.memory.HistoryCompressionManager
import domain.memory.SessionHistoryRepository
import domain.memory.SessionSummaryRepository
import domain.model.AgentConfig
import domain.model.AgentReply
import domain.model.ChatMessage
import domain.model.ChatRole
import domain.model.ChatSession
import domain.model.EstimatedTokenStats
import domain.model.FactMemory
import domain.model.SummaryState
import domain.token.TokenEstimator

class ContextAgentService(
    private val session: ChatSession,
    private val config: AgentConfig,
    private val historyRepository: SessionHistoryRepository,
    private val summaryRepository: SessionSummaryRepository? = null,
    private val contextBuilder: ContextBuilder,
    private val llmGateway: LlmGateway,
    private val tokenEstimator: TokenEstimator? = null,
    private val historyCompressionManager: HistoryCompressionManager? = null,
    private val factMemoryRepository: FactMemoryRepository? = null,
    private val factMemoryUpdater: FactMemoryUpdater? = null,
    private val branchManager: BranchManager? = null,
) {

    suspend fun sendMessage(text: String): AgentReply {
        val userMessage = ChatMessage(
            role = ChatRole.USER,
            content = text,
        )

        appendMessageToCurrentHistory(userMessage)

        val history = getCurrentHistory()

        val summary = summaryRepository?.getSummary(session)
            ?: SummaryState.Empty

        val facts = factMemoryRepository?.getFacts(session)
            ?: FactMemory.Empty

        val context = contextBuilder.buildContext(
            config = config,
            history = history,
            summary = summary,
            facts = facts,
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

        appendMessageToCurrentHistory(replyWithStats.message)

        updateFactsIfNeeded(text)

        val updatedHistory = getCurrentHistory()

        historyCompressionManager?.compressIfNeeded(
            session = session,
            history = updatedHistory,
        )

        return replyWithStats
    }

    suspend fun getHistory(): List<ChatMessage> {
        return getCurrentHistory()
    }

    suspend fun clearHistory() {
        clearCurrentHistory()
        summaryRepository?.clear(session)
        factMemoryRepository?.clear(session)
    }

    private suspend fun updateFactsIfNeeded(userMessage: String) {
        val repository = factMemoryRepository ?: return
        val updater = factMemoryUpdater ?: return

        val currentFacts = repository.getFacts(session)

        val updatedFacts = updater.updateFacts(
            currentFacts = currentFacts,
            userMessage = userMessage,
        )

        repository.saveFacts(
            session = session,
            facts = updatedFacts,
        )
    }

    private suspend fun appendMessageToCurrentHistory(message: ChatMessage) {
        if (branchManager != null) {
            val updatedHistory = branchManager.getCurrentHistory() + message
            branchManager.replaceCurrentHistory(updatedHistory)
        } else {
            historyRepository.appendMessage(
                session = session,
                message = message,
            )
        }
    }

    private suspend fun getCurrentHistory(): List<ChatMessage> {
        return branchManager?.getCurrentHistory()
            ?: historyRepository.getMessages(session)
    }

    private suspend fun clearCurrentHistory() {
        if (branchManager != null) {
            branchManager.clearCurrentBranch()
        } else {
            historyRepository.clear(session)
        }
    }
}