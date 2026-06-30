package domain.rag

class StructureAwareChunker(
    private val maxChunkSize: Int = 2_000,
    private val overlap: Int = 150,
) : ChunkingStrategy {

    override val name: String = "structure"

    init {
        require(maxChunkSize > 0) { "maxChunkSize must be positive" }
        require(overlap >= 0) { "overlap must not be negative" }
        require(overlap < maxChunkSize) { "overlap must be smaller than maxChunkSize" }
    }

    override fun chunk(document: SourceDocument): List<DocumentChunk> {
        if (document.text.isBlank()) {
            return emptyList()
        }

        val blocks = when (document.type) {
            SourceDocumentType.MARKDOWN -> splitMarkdown(document)
            SourceDocumentType.KOTLIN -> splitKotlin(document)
            SourceDocumentType.TEXT -> splitText(document)
        }

        var chunkIndex = 1

        return blocks.flatMap { block ->
            splitLargeBlock(
                document = document,
                block = block,
            ).map { part ->
                DocumentChunk(
                    chunkId = buildChunkId(document, chunkIndex++),
                    source = document.source,
                    title = document.title,
                    section = part.section,
                    strategy = name,
                    startChar = part.startChar,
                    endChar = part.endChar,
                    text = part.text,
                )
            }
        }
    }

    private fun splitMarkdown(document: SourceDocument): List<StructureBlock> {
        val matches = markdownHeadingRegex
            .findAll(document.text)
            .toList()

        if (matches.isEmpty()) {
            return listOf(
                StructureBlock(
                    section = document.title,
                    startChar = 0,
                    endChar = document.text.length,
                    text = document.text.trim(),
                ),
            )
        }

        val blocks = mutableListOf<StructureBlock>()

        if (matches.first().range.first > 0) {
            val introText = document.text
                .substring(0, matches.first().range.first)
                .trim()

            if (introText.isNotBlank()) {
                blocks += StructureBlock(
                    section = document.title,
                    startChar = 0,
                    endChar = matches.first().range.first,
                    text = introText,
                )
            }
        }

        matches.forEachIndexed { index, match ->
            val start = match.range.first
            val end = matches
                .getOrNull(index + 1)
                ?.range
                ?.first
                ?: document.text.length

            val section = match.value
                .replace(Regex("^#+\\s*"), "")
                .trim()

            val text = document.text
                .substring(start, end)
                .trim()

            if (text.isNotBlank()) {
                blocks += StructureBlock(
                    section = section.ifBlank { document.title },
                    startChar = start,
                    endChar = end,
                    text = text,
                )
            }
        }

        return blocks
    }

    private fun splitKotlin(document: SourceDocument): List<StructureBlock> {
        val matches = kotlinDeclarationRegex
            .findAll(document.text)
            .toList()

        if (matches.isEmpty()) {
            return listOf(
                StructureBlock(
                    section = document.title,
                    startChar = 0,
                    endChar = document.text.length,
                    text = document.text.trim(),
                ),
            )
        }

        val blocks = mutableListOf<StructureBlock>()

        if (matches.first().range.first > 0) {
            val headerText = document.text
                .substring(0, matches.first().range.first)
                .trim()

            if (headerText.isNotBlank()) {
                blocks += StructureBlock(
                    section = "${document.title} imports",
                    startChar = 0,
                    endChar = matches.first().range.first,
                    text = headerText,
                )
            }
        }

        matches.forEachIndexed { index, match ->
            val start = match.range.first
            val end = matches
                .getOrNull(index + 1)
                ?.range
                ?.first
                ?: document.text.length

            val section = match.value
                .trim()
                .replace(Regex("\\s+"), " ")

            val text = document.text
                .substring(start, end)
                .trim()

            if (text.isNotBlank()) {
                blocks += StructureBlock(
                    section = section,
                    startChar = start,
                    endChar = end,
                    text = text,
                )
            }
        }

        return blocks
    }

    private fun splitText(document: SourceDocument): List<StructureBlock> {
        val paragraphs = paragraphRegex
            .findAll(document.text)
            .toList()

        if (paragraphs.isEmpty()) {
            return listOf(
                StructureBlock(
                    section = document.title,
                    startChar = 0,
                    endChar = document.text.length,
                    text = document.text.trim(),
                ),
            )
        }

        return paragraphs.mapIndexedNotNull { index, match ->
            val text = match.value.trim()

            if (text.isBlank()) {
                null
            } else {
                StructureBlock(
                    section = "${document.title} paragraph ${index + 1}",
                    startChar = match.range.first,
                    endChar = match.range.last + 1,
                    text = text,
                )
            }
        }
    }

    private fun splitLargeBlock(
        document: SourceDocument,
        block: StructureBlock,
    ): List<StructureBlock> {
        if (block.text.length <= maxChunkSize) {
            return listOf(block)
        }

        val result = mutableListOf<StructureBlock>()
        var localStart = 0
        var partIndex = 1

        while (localStart < block.text.length) {
            val localEnd = minOf(localStart + maxChunkSize, block.text.length)
            val text = block.text.substring(localStart, localEnd).trim()

            if (text.isNotBlank()) {
                result += StructureBlock(
                    section = "${block.section} / part $partIndex",
                    startChar = block.startChar + localStart,
                    endChar = block.startChar + localEnd,
                    text = text,
                )
            }

            if (localEnd == block.text.length) {
                break
            }

            localStart = localEnd - overlap
            partIndex++
        }

        return result
    }

    private fun buildChunkId(
        document: SourceDocument,
        index: Int,
    ): String {
        val sourcePart = document.source
            .replace(Regex("[^A-Za-z0-9]+"), "_")
            .trim('_')
            .lowercase()

        return "${name}_${sourcePart}_${index.toString().padStart(4, '0')}"
    }

    private data class StructureBlock(
        val section: String,
        val startChar: Int,
        val endChar: Int,
        val text: String,
    )

    private companion object {
        val markdownHeadingRegex = Regex("""(?m)^#{1,6}\s+.+$""")

        val kotlinDeclarationRegex = Regex(
            pattern = """(?m)^\s*(class|object|interface|data\s+class|sealed\s+interface|sealed\s+class|enum\s+class|fun)\s+[A-Za-z0-9_]+""",
        )

        val paragraphRegex = Regex("""(?s)(?:^|\n\s*\n)(.+?)(?=\n\s*\n|$)""")
    }
}