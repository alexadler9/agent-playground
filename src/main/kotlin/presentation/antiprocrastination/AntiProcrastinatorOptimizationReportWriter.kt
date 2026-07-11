package presentation.antiprocrastination

import domain.antiprocrastination.AntiProcrastinatorBatchComparisonResult
import domain.antiprocrastination.AntiProcrastinatorComparisonResult
import domain.antiprocrastination.AntiProcrastinatorRunResult
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.writeText

/**
 * Пишет отчёт.
 *
 * В отчёте фиксируем качество, скорость и стабильность:
 * baseline и optimized ответы на одни и те же входы.
 */
class AntiProcrastinatorOptimizationReportWriter {

    fun write(
        result: AntiProcrastinatorBatchComparisonResult,
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
        result: AntiProcrastinatorBatchComparisonResult,
    ): String {
        return buildString {
            appendLine("# Оптимизация локальной LLM")
            appendLine()
            appendLine("## Сервис")
            appendLine()
            appendLine("Оптимизируется локальный сервис **Анти-прокрастинатор**.")
            appendLine()
            appendLine("Задача сервиса — превращать мутную или неприятную задачу в минимальный первый шаг и короткий реалистичный план на доступное время")
            appendLine()
            appendLine("Модель: `${result.model}`")
            appendLine()

            appendLine("## Что сравнивается")
            appendLine()
            appendLine("| Режим | Prompt | Temperature | Max tokens |")
            appendLine("|---|---|---:|---:|")
            appendLine("| baseline | generic-help-prompt | 0.8 | 1000 |")
            appendLine("| optimized | anti-procrastinator-compact-action-prompt | 0.25 | 350 |")
            appendLine()

            appendLine("## Сводка по скорости")
            appendLine()
            appendLine("| # | Baseline ms | Optimized ms | Baseline tokens | Optimized tokens |")
            appendLine("|---:|---:|---:|---:|---:|")

            result.items.forEachIndexed { index, item ->
                appendLine(
                    "| ${index + 1} | ${item.baseline.durationMs} | ${item.optimized.durationMs} | ${item.baseline.outputTokens ?: 0} | ${item.optimized.outputTokens ?: 0} |",
                )
            }

            appendLine()

            result.items.forEachIndexed { index, item ->
                appendComparison(
                    index = index + 1,
                    item = item,
                )
            }
        }
    }

    private fun StringBuilder.appendComparison(
        index: Int,
        item: AntiProcrastinatorComparisonResult,
    ) {
        appendLine("## Кейс $index")
        appendLine()

        appendLine("### Вход")
        appendLine()
        appendLine("- Задача: ${item.request.task}")
        appendLine("- Блокер: ${item.request.blocker}")
        appendLine("- Доступное время: ${item.request.availableMinutes} минут")
        appendLine("- Энергия: ${item.request.energyLevel}")
        appendLine()

        appendRun("Baseline", item.baseline)
        appendRun("Optimized", item.optimized)
    }

    private fun StringBuilder.appendRun(
        title: String,
        run: AntiProcrastinatorRunResult,
    ) {
        appendLine("### $title")
        appendLine()
        appendLine("- mode: `${run.mode}`")
        appendLine("- temperature: `${run.settings.temperature}`")
        appendLine("- maxTokens: `${run.settings.maxTokens}`")
        appendLine("- durationMs: `${run.durationMs}`")
        appendLine("- promptTokens: `${run.promptTokens ?: "-"}`")
        appendLine("- outputTokens: `${run.outputTokens ?: "-"}`")
        appendLine()
        appendLine("```text")
        appendLine(run.answer)
        appendLine("```")
        appendLine()
    }
}