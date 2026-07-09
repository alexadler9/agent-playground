package domain.rag

data class RagChatBackendResult(
    val provider: String,
    val model: String,
    val durationMs: Long,
    val answer: RagGroundedAnswerResult,
)

data class RagChatComparisonResult(
    val question: String,
    val indexPath: String,
    val embeddingProvider: String,
    val embeddingModel: String,
    val localModel: String,
    val cloudModel: String,
    val localResult: RagChatBackendResult,
    val cloudResult: RagChatBackendResult,
)
