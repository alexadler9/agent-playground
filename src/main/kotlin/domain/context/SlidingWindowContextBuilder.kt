package domain.context

import domain.model.AgentConfig
import domain.model.ChatMessage
import domain.model.ChatRole
import domain.model.FactMemory
import domain.model.SummaryState

class SlidingWindowContextBuilder(
    private val recentMessagesCount: Int,
) : ContextBuilder {

    override fun buildContext(
        config: AgentConfig,
        history: List<ChatMessage>,
        summary: SummaryState,
        facts: FactMemory,
    ): List<ChatMessage> {
        val systemMessage = ChatMessage(
            role = ChatRole.SYSTEM,
            content = config.systemPrompt,
        )

        val recentMessages = history.takeLast(recentMessagesCount)

        return listOf(systemMessage) + recentMessages
    }
}