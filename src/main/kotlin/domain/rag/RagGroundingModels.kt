package domain.rag

data class RagGroundedAnswerResult(
    val question: String,
    val rewrittenQuery: String,
    val isKnown: Boolean,
    val answer: String,
    val sources: List<RagGroundedSource>,
    val quotes: List<RagGroundedQuote>,
    val selectedChunks: List<RagContextChunk>,
    val validation: RagGroundingValidation,
    val skippedLlm: Boolean,
    val unknownReason: String? = null,
)

data class RagGroundedSource(
    val source: String,
    val section: String,
    val chunkId: String,
    val score: Double,
)

data class RagGroundedQuote(
    val source: String,
    val section: String,
    val chunkId: String,
    val text: String,
)

data class RagGroundingValidation(
    val parseSucceeded: Boolean,
    val hasSources: Boolean,
    val hasQuotes: Boolean,
    val sourcesExistInSelectedChunks: Boolean,
    val quotesExistInChunks: Boolean,
    val isValid: Boolean,
    val errors: List<String>,
)