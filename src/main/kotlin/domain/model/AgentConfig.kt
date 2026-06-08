package domain.model

data class AgentConfig(
    val model: String,
    val systemPrompt: String,
    val maxTokens: Int? = null,
    val temperature: Double? = null,
)