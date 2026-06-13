package domain.context

import domain.model.AgentConfig
import domain.model.ChatMessage
import domain.model.FactMemory
import domain.model.SummaryState

class SwitchableContextBuilder(
    private val provider: ContextBuilderProvider,
) : ContextBuilder {

    override fun buildContext(
        config: AgentConfig,
        history: List<ChatMessage>,
        summary: SummaryState,
        facts: FactMemory,
    ): List<ChatMessage> {
        return provider.getCurrentBuilder().buildContext(
            config = config,
            history = history,
            summary = summary,
            facts = facts,
        )
    }
}