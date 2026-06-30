package domain.rag

import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.writeText

/**
 * Пишет отчет по контрольному набору вопросов.
 *
 * Автоматический judge здесь не используем: отчет фиксирует ожидания,
 * ответы без RAG, ответы с RAG и найденные источники для ручного сравнения.
 */
class RagEvaluationReportWriter {

    fun write(
        items: List<RagEvaluationItem>,
        outputPath: Path,
    ) {
        outputPath.parent?.let { parent ->
            Files.createDirectories(parent)
        }

        outputPath.writeText(
            text = buildReport(items),
            charset = Charsets.UTF_8,
        )
    }

    private fun buildReport(
        items: List<RagEvaluationItem>,
    ): String {
        return buildString {
            appendLine("# Контрольные вопросы")
            appendLine()
            appendLine("Цель отчета - сравнить ответы модели без локального RAG-контекста и с RAG-контекстом")
            appendLine()
            appendLine("Для каждого вопроса зафиксированы:")
            appendLine()
            appendLine("- ожидание от ответа;")
            appendLine("- ожидаемые источники;")
            appendLine("- ответ без RAG;")
            appendLine("- ответ с RAG;")
            appendLine("- chunks, найденные retrieval-слоем.")
            appendLine()

            appendLine("## Сводка")
            appendLine()
            appendLine("| # | ID | Expected sources | Retrieved chunks |")
            appendLine("|---:|---|---|---:|")

            items.forEachIndexed { index, item ->
                appendLine(
                    "| ${index + 1} | `${item.case.id}` | ${item.case.expectedSources.joinToString()} | ${item.comparison.ragAnswer.selectedChunks.size} |",
                )
            }

            appendLine()

            items.forEachIndexed { index, item ->
                appendItem(
                    index = index + 1,
                    item = item,
                )
            }
        }
    }

    private fun StringBuilder.appendItem(
        index: Int,
        item: RagEvaluationItem,
    ) {
        appendLine("## $index. ${item.case.id}")
        appendLine()

        appendLine("### Вопрос")
        appendLine()
        appendLine("```text")
        appendLine(item.case.question)
        appendLine("```")
        appendLine()

        appendLine("### Ожидание")
        appendLine()
        appendLine(item.case.expectedAnswer)
        appendLine()

        appendLine("### Ожидаемые источники")
        appendLine()
        item.case.expectedSources.forEach { source ->
            appendLine("- `$source`")
        }
        appendLine()

        appendAnswer(
            title = "Ответ без RAG",
            answer = item.comparison.noRagAnswer,
        )

        appendAnswer(
            title = "Ответ с RAG",
            answer = item.comparison.ragAnswer,
        )

        appendLine("### Найденные chunks")
        appendLine()

        if (item.comparison.ragAnswer.selectedChunks.isEmpty()) {
            appendLine("Retrieval не нашёл chunks выше заданного порога релевантности")
            appendLine()
        } else {
            item.comparison.ragAnswer.selectedChunks.forEachIndexed { chunkIndex, chunk ->
                appendLine("#### ${chunkIndex + 1}. `${chunk.chunkId}`")
                appendLine()
                appendLine("- score: `${"%.4f".format(chunk.score)}`")
                appendLine("- source: `${chunk.source}`")
                appendLine("- title: `${chunk.title}`")
                appendLine("- section: `${chunk.section}`")
                appendLine()
                appendLine("```text")
                appendLine(chunk.text.take(1_500))
                appendLine("```")
                appendLine()
            }
        }
    }

    private fun StringBuilder.appendAnswer(
        title: String,
        answer: RagAnswerResult,
    ) {
        appendLine("### $title")
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