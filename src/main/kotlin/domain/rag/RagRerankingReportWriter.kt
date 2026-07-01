package domain.rag

import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.writeText

/**
 * Сохраняет сравнение baseline retrieval и improved retrieval.
 */
class RagRerankingReportWriter {

    fun write(
        result: RagImprovedRetrievalResult,
        outputPath: Path,
    ) {
        outputPath.parent?.let { parent ->
            Files.createDirectories(parent)
        }

        outputPath.writeText(
            text = buildReport(result),
            charset = Charsets.UTF_8,
        )
    }

    private fun buildReport(
        result: RagImprovedRetrievalResult,
    ): String {
        return buildString {
            appendLine("# RAG reranking и фильтрация")
            appendLine()

            appendLine("## Вопрос")
            appendLine()
            appendLine("```text")
            appendLine(result.question)
            appendLine("```")
            appendLine()

            appendLine("## Rewritten query")
            appendLine()
            appendLine("```text")
            appendLine(result.rewrittenQuery)
            appendLine("```")
            appendLine()

            appendLine("## Settings")
            appendLine()
            appendLine("- topKBefore: `${result.settings.topKBefore}`")
            appendLine("- topKAfter: `${result.settings.topKAfter}`")
            appendLine("- minSimilarityScore: `${result.settings.minSimilarityScore}`")
            appendLine("- relativeScoreDrop: `${result.settings.relativeScoreDrop}`")
            appendLine()

            appendLine("## Baseline retrieval")
            appendLine()
            appendLine("Режим без query rewrite, фильтрации и reranking")
            appendLine()
            appendLine("| # | Similarity | Source | Section | Chunk ID |")
            appendLine("|---:|---:|---|---|---|")

            result.baselineChunks.forEachIndexed { index, item ->
                appendLine(
                    "| ${index + 1} | ${"%.4f".format(item.score)} | `${item.chunk.source}` | `${item.chunk.section}` | `${item.chunk.chunkId}` |",
                )
            }

            appendLine()
            appendLine("## Improved candidates before filter")
            appendLine()
            appendLine("Режим после query rewrite, но до отсечения нерелевантных chunks")
            appendLine()
            appendLine("| # | Similarity | Keyword bonus | Final score | Passed filter | Source | Section | Chunk ID |")
            appendLine("|---:|---:|---:|---:|---|---|---|---|")

            result.candidatesBeforeFilter.forEachIndexed { index, item ->
                appendLine(
                    "| ${index + 1} | ${"%.4f".format(item.similarityScore)} | ${"%.4f".format(item.keywordScore)} | ${"%.4f".format(item.finalScore)} | `${item.passedFilter}` | `${item.chunk.source}` | `${item.chunk.section}` | `${item.chunk.chunkId}` |",
                )
            }

            appendLine()
            appendLine("## Improved selected chunks")
            appendLine()
            appendLine("Chunks после threshold filter и heuristic reranking")
            appendLine()
            appendLine("| # | Similarity | Keyword bonus | Final score | Source | Section | Chunk ID |")
            appendLine("|---:|---:|---:|---:|---|---|---|")

            if (result.selectedAfterFilter.isEmpty()) {
                appendLine("| - | - | - | - | - | - | Релевантные chunks не найдены |")
            } else {
                result.selectedAfterFilter.forEachIndexed { index, item ->
                    appendLine(
                        "| ${index + 1} | ${"%.4f".format(item.similarityScore)} | ${"%.4f".format(item.keywordScore)} | ${"%.4f".format(item.finalScore)} | `${item.chunk.source}` | `${item.chunk.section}` | `${item.chunk.chunkId}` |",
                    )
                }
            }

            appendLine()
            appendLine("## Вывод")
            appendLine()
            appendLine("Baseline retrieval берет top-K только по embedding similarity исходного вопроса")
            appendLine()
            appendLine("Improved retrieval сначала переписывает запрос под смешанный русско-английский корпус, затем берет больше кандидатов, отсекает слабые результаты и пересортировывает оставшиеся chunks с учетом keyword-сигналов в source/title/section/text")
        }
    }
}