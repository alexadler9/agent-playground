package domain.rag

class FixedSizeChunker(
    private val chunkSize: Int = 1_000,
    private val overlap: Int = 150,
) : ChunkingStrategy {

    override val name: String = "fixed-size"

    init {
        require(chunkSize > 0) { "chunkSize must be positive" }
        require(overlap >= 0) { "overlap must not be negative" }
        require(overlap < chunkSize) { "overlap must be smaller than chunkSize" }
    }

    override fun chunk(document: SourceDocument): List<DocumentChunk> {
        if (document.text.isBlank()) {
            return emptyList()
        }

        val chunks = mutableListOf<DocumentChunk>()
        var start = 0
        var index = 1

        while (start < document.text.length) {
            val end = minOf(start + chunkSize, document.text.length)
            val text = document.text.substring(start, end).trim()

            if (text.isNotBlank()) {
                chunks += DocumentChunk(
                    chunkId = buildChunkId(document, index),
                    source = document.source,
                    title = document.title,
                    section = findSection(document, start),
                    strategy = name,
                    startChar = start,
                    endChar = end,
                    text = text,
                )
            }

            if (end == document.text.length) {
                break
            }

            start = end - overlap
            index++
        }

        return chunks
    }

    private fun buildChunkId(
        document: SourceDocument,
        index: Int,
    ): String {
        val sourcePart = document.source
            .replace(Regex("[^A-Za-z0-9]+"), "_")
            .trim('_')
            .lowercase()

        return "${name.replace("-", "_")}_${sourcePart}_${index.toString().padStart(4, '0')}"
    }

    private fun findSection(
        document: SourceDocument,
        startChar: Int,
    ): String {
        return when (document.type) {
            SourceDocumentType.MARKDOWN -> {
                document.text
                    .take(startChar + 1)
                    .lineSequence()
                    .map { line -> line.trim() }
                    .lastOrNull { line -> markdownHeadingRegex.matches(line) }
                    ?.replace(Regex("^#+\\s*"), "")
                    ?.trim()
                    ?.takeIf { section -> section.isNotBlank() }
                    ?: document.title
            }

            SourceDocumentType.KOTLIN -> {
                kotlinDeclarationRegex
                    .findAll(document.text)
                    .takeWhile { match -> match.range.first <= startChar }
                    .lastOrNull()
                    ?.value
                    ?.trim()
                    ?: document.title
            }

            SourceDocumentType.TEXT -> document.title
        }
    }

    private companion object {
        val markdownHeadingRegex = Regex("^#{1,6}\\s+.+")
        val kotlinDeclarationRegex = Regex(
            pattern = """(?m)^\s*(class|object|interface|fun)\s+[A-Za-z0-9_]+""",
        )
    }
}