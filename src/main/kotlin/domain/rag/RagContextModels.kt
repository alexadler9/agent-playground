package domain.rag

/**
 * Chunk, который уже выбран retrieval-слоем и готов к передаче в prompt.
 */
data class RagContextChunk(
    val chunkId: String,
    val source: String,
    val title: String,
    val section: String,
    val score: Double,
    val text: String,
)
