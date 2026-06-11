package domain.context

import domain.model.AgentConfig
import domain.model.ChatMessage
import domain.model.SummaryState

interface ContextBuilder {

    fun buildContext(
        config: AgentConfig,
        history: List<ChatMessage>,
        summary: SummaryState = SummaryState.Empty,
    ): List<ChatMessage>
}