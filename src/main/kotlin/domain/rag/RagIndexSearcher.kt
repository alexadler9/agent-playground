package domain.rag

import kotlin.math.sqrt

class RagIndexSearcher(
    private val embeddingGateway: EmbeddingGateway,
) {

    suspend fun search(
        index: RagIndex,
        query: String,
        topK: Int = 5,
    ): List<RagSearchResult> {
        require(query.isNotBlank()) { "query must not be blank" }
        require(topK > 0) { "topK must be positive" }

        val queryEmbedding = embeddingGateway.embed(query)

        return index.chunks
            .map { chunk ->
                RagSearchResult(
                    chunk = chunk,
                    score = cosineSimilarity(
                        first = queryEmbedding,
                        second = chunk.embedding,
                    ),
                )
            }
            .sortedByDescending { result -> result.score }
            .take(topK)
    }

    private fun cosineSimilarity(
        first: List<Float>,
        second: List<Float>,
    ): Double {
        if (first.isEmpty() || second.isEmpty()) {
            return 0.0
        }

        val size = minOf(first.size, second.size)

        var dot = 0.0
        var firstNorm = 0.0
        var secondNorm = 0.0

        for (index in 0 until size) {
            val firstValue = first[index].toDouble()
            val secondValue = second[index].toDouble()

            dot += firstValue * secondValue
            firstNorm += firstValue * firstValue
            secondNorm += secondValue * secondValue
        }

        if (firstNorm == 0.0 || secondNorm == 0.0) {
            return 0.0
        }

        return dot / (sqrt(firstNorm) * sqrt(secondNorm))
    }
}

data class RagSearchResult(
    val chunk: IndexedChunk,
    val score: Double,
)