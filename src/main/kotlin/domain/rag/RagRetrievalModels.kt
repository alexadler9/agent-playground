package domain.rag

/**
 * Настройки второго этапа retrieval.
 *
 * topKBefore — сколько кандидатов достаём первичным embedding search.
 * topKAfter — сколько chunks оставляем после фильтрации и reranking.
 */
data class RagRetrievalSettings(
    val topKBefore: Int = 12,
    val topKAfter: Int = 5,
    val minSimilarityScore: Double = 0.35,
    val relativeScoreDrop: Double = 0.10,
) {
    init {
        require(topKBefore > 0) { "topKBefore must be positive" }
        require(topKAfter > 0) { "topKAfter must be positive" }
        require(topKAfter <= topKBefore) { "topKAfter must be <= topKBefore" }
        require(minSimilarityScore >= 0.0) { "minSimilarityScore must not be negative" }
        require(relativeScoreDrop >= 0.0) { "relativeScoreDrop must not be negative" }
    }
}

data class RagRerankedCandidate(
    val chunk: IndexedChunk,
    val similarityScore: Double,
    val keywordScore: Double,
    val finalScore: Double,
    val passedFilter: Boolean,
)

data class RagImprovedRetrievalResult(
    val question: String,
    val rewrittenQuery: String,
    val settings: RagRetrievalSettings,
    val baselineChunks: List<RagSearchResult>,
    val candidatesBeforeFilter: List<RagRerankedCandidate>,
    val selectedAfterFilter: List<RagRerankedCandidate>,
)