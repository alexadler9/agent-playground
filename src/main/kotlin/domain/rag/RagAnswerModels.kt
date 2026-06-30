package domain.rag

data class RagAnswerResult(
    val question: String,
    val mode: String,
    val answer: String,
    val usedRag: Boolean,
    val skippedLlm: Boolean,
    val selectedChunks: List<RagContextChunk>,
)

data class RagContextChunk(
    val chunkId: String,
    val source: String,
    val title: String,
    val section: String,
    val score: Double,
    val text: String,
)

data class RagComparisonResult(
    val question: String,
    val noRagAnswer: RagAnswerResult,
    val ragAnswer: RagAnswerResult,
)