package domain.rag

/**
 * Второй этап после обычного vector search.
 *
 * Сначала берём больше кандидатов, потом:
 * - отсекаем слабые результаты по similarity;
 * - отсекаем резкий хвост относительно top score;
 * - чуть пересортировываем по keyword-сигналам.
 */
class RagImprovedRetriever(
    private val embeddingGateway: EmbeddingGateway,
) {

    suspend fun retrieve(
        index: RagIndex,
        question: String,
        rewrittenQuery: String,
        settings: RagRetrievalSettings = RagRetrievalSettings(),
    ): RagImprovedRetrievalResult {
        val baselineChunks = RagIndexSearcher(
            embeddingGateway = embeddingGateway,
        ).search(
            index = index,
            query = question,
            topK = settings.topKAfter,
        )

        val initialCandidates = RagIndexSearcher(
            embeddingGateway = embeddingGateway,
        ).search(
            index = index,
            query = rewrittenQuery,
            topK = settings.topKBefore,
        )

        val topScore = initialCandidates.maxOfOrNull { result -> result.score } ?: 0.0
        val queryTerms = tokenize(rewrittenQuery)

        val candidates = initialCandidates.map { result ->
            val keywordScore = calculateKeywordScore(
                chunk = result.chunk,
                queryTerms = queryTerms,
            )

            val finalScore = result.score + keywordScore

            RagRerankedCandidate(
                chunk = result.chunk,
                similarityScore = result.score,
                keywordScore = keywordScore,
                finalScore = finalScore,
                passedFilter = result.score >= settings.minSimilarityScore &&
                        result.score >= topScore - settings.relativeScoreDrop,
            )
        }

        val selected = candidates
            .filter { candidate -> candidate.passedFilter }
            .sortedByDescending { candidate -> candidate.finalScore }
            .take(settings.topKAfter)

        return RagImprovedRetrievalResult(
            question = question,
            rewrittenQuery = rewrittenQuery,
            settings = settings,
            baselineChunks = baselineChunks,
            candidatesBeforeFilter = candidates,
            selectedAfterFilter = selected,
        )
    }

    private fun calculateKeywordScore(
        chunk: IndexedChunk,
        queryTerms: Set<String>,
    ): Double {
        if (queryTerms.isEmpty()) {
            return 0.0
        }

        val metadataText = buildString {
            append(chunk.source)
            append(' ')
            append(chunk.title)
            append(' ')
            append(chunk.section)
        }.lowercase()

        val bodyText = chunk.text.lowercase()

        var score = 0.0

        queryTerms.forEach { term ->
            if (term.length < 3) {
                return@forEach
            }

            if (metadataText.contains(term)) {
                score += 0.025
            }

            if (bodyText.contains(term)) {
                score += 0.015
            }
        }

        return score.coerceAtMost(0.15)
    }

    private fun tokenize(text: String): Set<String> {
        return text
            .lowercase()
            .split(nonWordRegex)
            .map { token -> token.trim() }
            .filter { token -> token.length >= 3 }
            .toSet()
    }

    private companion object {
        val nonWordRegex = Regex("""[^\p{L}\p{N}_-]+""")
    }
}