package domain.rag

import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.writeText

/**
 * Сохраняет результаты retrieval в markdown-файл.
 */
class RagSearchReportWriter {

    fun write(
        query: String,
        index: RagIndex,
        results: List<RagSearchResult>,
        outputPath: Path,
    ) {
        outputPath.parent?.let { parent ->
            Files.createDirectories(parent)
        }

        outputPath.writeText(
            text = buildReport(
                query = query,
                index = index,
                results = results,
            ),
            charset = Charsets.UTF_8,
        )
    }

    private fun buildReport(
        query: String,
        index: RagIndex,
        results: List<RagSearchResult>,
    ): String {
        return buildString {
            appendLine("# Результаты RAG-поиска")
            appendLine()
            appendLine("Запрос:")
            appendLine()
            appendLine("```text")
            appendLine(query)
            appendLine("```")
            appendLine()

            appendLine("## Индекс")
            appendLine()
            appendLine("- strategy: `${index.strategy}`")
            appendLine("- embedding model: `${index.embeddingModel}`")
            appendLine("- documents: ${index.documentsCount}")
            appendLine("- chunks: ${index.chunksCount}")
            appendLine()

            appendLine("## Top results")
            appendLine()

            results.forEachIndexed { resultIndex, result ->
                val chunk = result.chunk

                appendLine("### ${resultIndex + 1}. `${chunk.chunkId}`")
                appendLine()
                appendLine("- score: `${"%.4f".format(result.score)}`")
                appendLine("- source: `${chunk.source}`")
                appendLine("- title: `${chunk.title}`")
                appendLine("- section: `${chunk.section}`")
                appendLine("- chars: ${chunk.text.length}")
                appendLine()

                appendLine("```text")
                appendLine(chunk.text)
                appendLine("```")
                appendLine()
            }
        }
    }
}