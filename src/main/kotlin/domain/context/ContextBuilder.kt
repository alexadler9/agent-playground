package domain.context

import domain.model.AgentConfig
import domain.model.ChatMessage
import domain.model.FactMemory
import domain.model.SummaryState

interface ContextBuilder {

    fun buildContext(
        config: AgentConfig,
        history: List<ChatMessage>,
        summary: SummaryState = SummaryState.Empty,
        facts: FactMemory = FactMemory.Empty,
    ): List<ChatMessage>
}