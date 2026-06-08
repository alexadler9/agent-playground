package domain.llm

import domain.model.AgentConfig
import domain.model.ChatMessage
import domain.model.AgentReply

interface LlmGateway {

    suspend fun sendMessages(
        config: AgentConfig,
        messages: List<ChatMessage>,
    ): AgentReply
}