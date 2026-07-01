package domain.rag

data class RagRerankingAnswerComparison(
    val question: String,
    val retrieval: RagImprovedRetrievalResult,
    val baselineAnswer: RagAnswerResult,
    val improvedAnswer: RagAnswerResult,
)