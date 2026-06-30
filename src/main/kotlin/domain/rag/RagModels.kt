package domain.rag


data class SourceDocument(
    val source: String,
    val title: String,
    val type: SourceDocumentType,
    val text: String,
)

enum class SourceDocumentType {
    MARKDOWN,
    TEXT,
    KOTLIN,
}

data class DocumentChunk(
    val chunkId: String,
    val source: String,
    val title: String,
    val section: String,
    val strategy: String,
    val startChar: Int,
    val endChar: Int,
    val text: String,
)

interface ChunkingStrategy {
    val name: String

    fun chunk(document: SourceDocument): List<DocumentChunk>
}