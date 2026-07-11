package domain.privatechat

import kotlinx.serialization.Serializable

@Serializable
data class PrivateChatRequest(
    val messages: List<PrivateChatMessage>,
)

@Serializable
data class PrivateChatMessage(
    val role: String,
    val content: String,
)

@Serializable
data class PrivateChatResponse(
    val answer: String,
    val model: String,
    val durationMs: Long,
    val usedMessages: Int,
)

@Serializable
data class PrivateChatErrorResponse(
    val error: String,
)

@Serializable
data class PrivateChatHealthResponse(
    val status: String,
    val model: String,
    val maxMessages: Int,
    val maxTotalContextChars: Int,
    val maxRequests: Int,
)