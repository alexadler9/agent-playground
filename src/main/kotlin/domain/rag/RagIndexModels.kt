package domain.rag

import kotlinx.serialization.Serializable

@Serializable
data class RagIndex(
    val strategy: String,
    val documentsCount: Int,
    val chunksCount: Int,
    val embeddingModel: String,
    val chunks: List<IndexedChunk>,
)

@Serializable
data class IndexedChunk(
    val chunkId: String,
    val source: String,
    val title: String,
    val section: String,
    val strategy: String,
    val startChar: Int,
    val endChar: Int,
    val text: String,
    val embedding: List<Float>,
)