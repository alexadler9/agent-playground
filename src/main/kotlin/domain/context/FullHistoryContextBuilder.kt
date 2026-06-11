package domain.context

import domain.model.AgentConfig
import domain.model.ChatMessage
import domain.model.ChatRole
import domain.model.SummaryState

class FullHistoryContextBuilder : ContextBuilder {

    override fun buildContext(
        config: AgentConfig,
        history: List<ChatMessage>,
        summary: SummaryState,
    ): List<ChatMessage> {
        val systemMessage = ChatMessage(
            role = ChatRole.SYSTEM,
            content = config.systemPrompt,
        )

        return listOf(systemMessage) + history
    }
}