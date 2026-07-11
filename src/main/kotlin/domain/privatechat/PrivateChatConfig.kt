package domain.privatechat

data class PrivateChatLimits(
    val maxMessages: Int = 20,
    val maxMessageChars: Int = 2_000,
    val maxTotalContextChars: Int = 12_000,
    val maxRequests: Int = 10,
    val rateLimitWindowMs: Long = 60_000,
)

data class PrivateChatLlmSettings(
    val temperature: Double = 0.3,
    val maxTokens: Int = 700,
    val contextWindow: Int = 4_096,
)