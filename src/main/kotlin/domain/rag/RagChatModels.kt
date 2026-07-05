package domain.rag

data class RagChatSessionState(
    val history: List<RagChatMessage> = emptyList(),
    val taskMemory: RagChatTaskMemory = RagChatTaskMemory(),
)

data class RagChatMessage(
    val role: RagChatRole,
    val content: String,
)

enum class RagChatRole {
    USER,
    ASSISTANT,
}

data class RagChatTaskMemory(
    val goal: String = "",
    val clarifications: List<String> = emptyList(),
    val constraints: List<String> = emptyList(),
    val terms: List<String> = emptyList(),
)

data class RagChatTurnResult(
    val answer: RagGroundedAnswerResult,
    val state: RagChatSessionState,
)