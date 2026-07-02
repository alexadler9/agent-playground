package domain.rag

/**
 * Проверяет grounding результата.
 *
 * Важно: мы не просто верим модели, что она дала цитаты.
 * Мы проверяем, что source/chunk_id существуют среди selected chunks,
 * а текст цитаты действительно встречается внутри chunk text.
 */
class RagGroundingValidator {

    fun validate(
        response: RagGroundedLlmResponseDto,
        selectedChunks: List<RagContextChunk>,
    ): RagGroundingValidation {
        val errors = mutableListOf<String>()
        val chunksById = selectedChunks.associateBy { chunk -> chunk.chunkId }

        val hasSources = response.sources.isNotEmpty()
        val hasQuotes = response.quotes.isNotEmpty()

        if (!hasSources) {
            errors += "Response does not contain sources."
        }

        if (!hasQuotes) {
            errors += "Response does not contain quotes."
        }

        val sourcesExistInSelectedChunks = response.sources.all { source ->
            val chunk = chunksById[source.chunkId]

            chunk != null &&
                    chunk.source == source.source &&
                    chunk.section == source.section
        }

        if (!sourcesExistInSelectedChunks) {
            errors += "Some sources do not match selected chunks."
        }

        val quotesExistInChunks = response.quotes.all { quote ->
            val chunk = chunksById[quote.chunkId]
            chunk != null && containsQuote(
                chunkText = chunk.text,
                quoteText = quote.text,
            )
        }

        if (!quotesExistInChunks) {
            errors += "Some quotes are not exact fragments of selected chunks."
        }

        return RagGroundingValidation(
            parseSucceeded = true,
            hasSources = hasSources,
            hasQuotes = hasQuotes,
            sourcesExistInSelectedChunks = sourcesExistInSelectedChunks,
            quotesExistInChunks = quotesExistInChunks,
            isValid = errors.isEmpty(),
            errors = errors,
        )
    }

    fun parseFailed(errorMessage: String): RagGroundingValidation {
        return RagGroundingValidation(
            parseSucceeded = false,
            hasSources = false,
            hasQuotes = false,
            sourcesExistInSelectedChunks = false,
            quotesExistInChunks = false,
            isValid = false,
            errors = listOf(errorMessage),
        )
    }

    private fun containsQuote(
        chunkText: String,
        quoteText: String,
    ): Boolean {
        if (quoteText.isBlank()) {
            return false
        }

        return normalize(chunkText).contains(normalize(quoteText))
    }

    private fun normalize(text: String): String {
        return text
            .lowercase()
            .replace(whitespaceRegex, " ")
            .trim()
    }

    private companion object {
        val whitespaceRegex = Regex("""\s+""")
    }
}