package domain.model

data class AgentReply(
    val message: ChatMessage,
    val tokenUsage: TokenUsage?,
    val responseTimeMs: Long?,
)