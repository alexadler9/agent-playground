package domain.rag

import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.writeText

/**
 * Сохраняет сравнение no-RAG и RAG-ответов в markdown.
 */
class RagAnswerReportWriter {

    fun writeComparison(
        result: RagComparisonResult,
        outputPath: Path,
    ) {
        outputPath.parent?.let { parent ->
            Files.createDirectories(parent)
        }

        outputPath.writeText(
            text = buildComparisonReport(result),
            charset = Charsets.UTF_8,
        )
    }

    private fun buildComparisonReport(
        result: RagComparisonResult,
    ): String {
        return buildString {
            appendLine("# Сравнение ответа без RAG и с RAG")
            appendLine()
            appendLine("## Вопрос")
            appendLine()
            appendLine("```text")
            appendLine(result.question)
            appendLine("```")
            appendLine()

            appendAnswerBlock(
                title = "Ответ без RAG",
                answer = result.noRagAnswer,
            )

            appendAnswerBlock(
                title = "Ответ с RAG",
                answer = result.ragAnswer,
            )

            appendLine("## Использованные RAG chunks")
            appendLine()

            if (result.ragAnswer.selectedChunks.isEmpty()) {
                appendLine("Релевантные chunks не найдены")
                appendLine()
            } else {
                result.ragAnswer.selectedChunks.forEachIndexed { index, chunk ->
                    appendLine("### ${index + 1}. `${chunk.chunkId}`")
                    appendLine()
                    appendLine("- score: `${"%.4f".format(chunk.score)}`")
                    appendLine("- source: `${chunk.source}`")
                    appendLine("- title: `${chunk.title}`")
                    appendLine("- section: `${chunk.section}`")
                    appendLine()
                    appendLine("```text")
                    appendLine(chunk.text)
                    appendLine("```")
                    appendLine()
                }
            }
        }
    }

    private fun StringBuilder.appendAnswerBlock(
        title: String,
        answer: RagAnswerResult,
    ) {
        appendLine("## $title")
        appendLine()
        appendLine("- mode: `${answer.mode}`")
        appendLine("- usedRag: `${answer.usedRag}`")
        appendLine("- skippedLlm: `${answer.skippedLlm}`")
        appendLine()
        appendLine("```text")
        appendLine(answer.answer)
        appendLine("```")
        appendLine()
    }
}