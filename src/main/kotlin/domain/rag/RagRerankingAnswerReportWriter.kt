package domain.rag

import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.writeText

/**
 * Пишет компактный отчёт сравнения baseline RAG и improved RAG.
 *
 * Полный текст chunks не вставляем, чтобы отчёт оставался читаемым.
 */
class RagRerankingAnswerReportWriter {

    fun write(
        comparison: RagRerankingAnswerComparison,
        outputPath: Path,
    ) {
        outputPath.parent?.let { parent ->
            Files.createDirectories(parent)
        }

        outputPath.writeText(
            text = buildReport(comparison),
            charset = Charsets.UTF_8,
        )
    }

    private fun buildReport(
        comparison: RagRerankingAnswerComparison,
    ): String {
        val retrieval = comparison.retrieval

        return buildString {
            appendLine("# Baseline RAG vs Improved RAG")
            appendLine()

            appendLine("## Вопрос")
            appendLine()
            appendLine("```text")
            appendLine(comparison.question)
            appendLine("```")
            appendLine()

            appendLine("## Rewritten query")
            appendLine()
            appendLine("```text")
            appendLine(retrieval.rewrittenQuery)
            appendLine("```")
            appendLine()

            appendLine("## Settings")
            appendLine()
            appendLine("- topKBefore: `${retrieval.settings.topKBefore}`")
            appendLine("- topKAfter: `${retrieval.settings.topKAfter}`")
            appendLine("- minSimilarityScore: `${retrieval.settings.minSimilarityScore}`")
            appendLine("- relativeScoreDrop: `${retrieval.settings.relativeScoreDrop}`")
            appendLine()

            appendLine("## Baseline retrieval")
            appendLine()
            appendLine("Без query rewrite, фильтрации и reranking")
            appendLine()
            appendBaselineTable(retrieval)

            appendLine("## Improved candidates before filter")
            appendLine()
            appendLine("После query rewrite, но до финального отсечения")
            appendLine()
            appendCandidatesTable(retrieval.candidatesBeforeFilter)

            appendLine("## Improved selected chunks")
            appendLine()
            appendLine("После threshold filter и heuristic reranking")
            appendLine()
            appendSelectedTable(retrieval.selectedAfterFilter)

            appendAnswer(
                title = "Baseline RAG answer",
                answer = comparison.baselineAnswer,
            )

            appendAnswer(
                title = "Improved RAG answer",
                answer = comparison.improvedAnswer,
            )

            appendLine("## Вывод")
            appendLine()
            appendLine("Baseline RAG использует исходный вопрос и передает в LLM top-K chunks только по embedding similarity")
            appendLine()
            appendLine("Improved RAG сначала переписывает запрос под смешанный русско-английский корпус, затем берет больше кандидатов, отсекает слабые chunks и пересортировывает оставшиеся результаты по similarity + keyword-сигналам")
        }
    }

    private fun StringBuilder.appendBaselineTable(
        retrieval: RagImprovedRetrievalResult,
    ) {
        appendLine("| # | Similarity | Source | Section | Chunk ID |")
        appendLine("|---:|---:|---|---|---|")

        retrieval.baselineChunks.forEachIndexed { index, item ->
            appendLine(
                "| ${index + 1} | ${"%.4f".format(item.score)} | `${item.chunk.source}` | `${item.chunk.section}` | `${item.chunk.chunkId}` |",
            )
        }

        appendLine()
    }

    private fun StringBuilder.appendCandidatesTable(
        candidates: List<RagRerankedCandidate>,
    ) {
        appendLine("| # | Similarity | Keyword bonus | Final score | Passed filter | Source | Section | Chunk ID |")
        appendLine("|---:|---:|---:|---:|---|---|---|---|")

        candidates.forEachIndexed { index, item ->
            appendLine(
                "| ${index + 1} | ${"%.4f".format(item.similarityScore)} | ${"%.4f".format(item.keywordScore)} | ${"%.4f".format(item.finalScore)} | `${item.passedFilter}` | `${item.chunk.source}` | `${item.chunk.section}` | `${item.chunk.chunkId}` |",
            )
        }

        appendLine()
    }

    private fun StringBuilder.appendSelectedTable(
        candidates: List<RagRerankedCandidate>,
    ) {
        appendLine("| # | Similarity | Keyword bonus | Final score | Source | Section | Chunk ID |")
        appendLine("|---:|---:|---:|---:|---|---|---|")

        if (candidates.isEmpty()) {
            appendLine("| - | - | - | - | - | - | Релевантные chunks не найдены |")
        } else {
            candidates.forEachIndexed { index, item ->
                appendLine(
                    "| ${index + 1} | ${"%.4f".format(item.similarityScore)} | ${"%.4f".format(item.keywordScore)} | ${"%.4f".format(item.finalScore)} | `${item.chunk.source}` | `${item.chunk.section}` | `${item.chunk.chunkId}` |",
                )
            }
        }

        appendLine()
    }

    private fun StringBuilder.appendAnswer(
        title: String,
        answer: RagAnswerResult,
    ) {
        appendLine("## $title")
        appendLine()
        appendLine("- mode: `${answer.mode}`")
        appendLine("- skippedLlm: `${answer.skippedLlm}`")
        appendLine("- selectedChunks: `${answer.selectedChunks.size}`")
        appendLine()
        appendLine("```text")
        appendLine(answer.answer)
        appendLine("```")
        appendLine()
    }
}