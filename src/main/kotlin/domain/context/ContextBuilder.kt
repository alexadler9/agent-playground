package domain.context

import domain.model.AgentConfig
import domain.model.ChatMessage

interface ContextBuilder {

    fun buildContext(
        config: AgentConfig,
        history: List<ChatMessage>,
    ): List<ChatMessage>
}