package domain.rag

import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.writeText

/**
 * Пишет сравнительный отчет: локальная LLM + RAG против облачной LLM + RAG.
 */
class RagChatComparisonReportWriter {

    fun write(
        result: RagChatComparisonResult,
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
        result: RagChatComparisonResult,
    ): String {
        return buildString {
            appendLine("# Локальная LLM + RAG")
            appendLine()
            appendLine("## Setup")
            appendLine()
            appendLine("- Index: `${result.indexPath}`")
            appendLine("- Retrieval: local JSON index + `${result.embeddingProvider}` embeddings `${result.embeddingModel}`")
            appendLine("- Local generation model: `${result.localModel}`")
            appendLine("- Cloud generation model: `${result.cloudModel}`")
            appendLine()
            appendLine("## Question")
            appendLine()
            appendLine("```text")
            appendLine(result.question)
            appendLine("```")
            appendLine()

            appendLine("## Summary")
            appendLine()
            appendLine("| Mode | Model | Duration ms | Known | Valid | Sources | Quotes | Skipped LLM |")
            appendLine("|---|---|---:|---:|---:|---:|---:|---:|")
            appendSummaryRow(result.localResult)
            appendSummaryRow(result.cloudResult)
            appendLine()

            appendBackendResult(result.localResult)
            appendBackendResult(result.cloudResult)

            appendLine("## Manual evaluation")
            appendLine()
            appendLine("### Quality")
            appendLine()
            appendLine("- Local: TODO")
            appendLine("- Cloud: TODO")
            appendLine()
            appendLine("### Speed")
            appendLine()
            appendLine("- Local duration: `${result.localResult.durationMs}` ms")
            appendLine("- Cloud duration: `${result.cloudResult.durationMs}` ms")
            appendLine()
            appendLine("### Stability")
            appendLine()
            appendLine("Check JSON parsing, sources, quotes and validation flags above")
            appendLine()
        }
    }

    private fun StringBuilder.appendSummaryRow(
        result: RagChatBackendResult,
    ) {
        appendLine(
            "| ${result.provider} | `${result.model}` | ${result.durationMs} | `${result.answer.isKnown}` | `${result.answer.validation.isValid}` | ${result.answer.sources.size} | ${result.answer.quotes.size} | `${result.answer.skippedLlm}` |",
        )
    }

    private fun StringBuilder.appendBackendResult(
        result: RagChatBackendResult,
    ) {
        appendLine("## ${result.provider.replaceFirstChar { it.uppercase() }} result")
        appendLine()
        appendLine("### Answer")
        appendLine()
        appendLine("```text")
        appendLine(result.answer.answer)
        appendLine("```")
        appendLine()

        appendLine("### Validation")
        appendLine()
        appendLine("| Check | Result |")
        appendLine("|---|---:|")
        appendLine("| parseSucceeded | `${result.answer.validation.parseSucceeded}` |")
        appendLine("| hasSources | `${result.answer.validation.hasSources}` |")
        appendLine("| hasQuotes | `${result.answer.validation.hasQuotes}` |")
        appendLine("| sourcesExistInSelectedChunks | `${result.answer.validation.sourcesExistInSelectedChunks}` |")
        appendLine("| quotesExistInChunks | `${result.answer.validation.quotesExistInChunks}` |")
        appendLine("| isValid | `${result.answer.validation.isValid}` |")
        appendLine()

        if (result.answer.validation.errors.isNotEmpty()) {
            appendLine("Validation errors:")
            appendLine()
            result.answer.validation.errors.forEach { error ->
                appendLine("- $error")
            }
            appendLine()
        }

        appendSources(result.answer.sources)
        appendQuotes(result.answer.quotes)
        appendSelectedChunks(result.answer.selectedChunks)
    }

    private fun StringBuilder.appendSources(
        sources: List<RagGroundedSource>,
    ) {
        appendLine("### Sources")
        appendLine()

        if (sources.isEmpty()) {
            appendLine("No sources.")
            appendLine()
            return
        }

        appendLine("| # | Score | Source | Section | Chunk ID |")
        appendLine("|---:|---:|---|---|---|")
        sources.forEachIndexed { index, source ->
            appendLine("| ${index + 1} | ${"%.4f".format(source.score)} | `${source.source}` | `${source.section}` | `${source.chunkId}` |")
        }
        appendLine()
    }

    private fun StringBuilder.appendQuotes(
        quotes: List<RagGroundedQuote>,
    ) {
        appendLine("### Quotes")
        appendLine()

        if (quotes.isEmpty()) {
            appendLine("No quotes.")
            appendLine()
            return
        }

        quotes.forEachIndexed { index, quote ->
            appendLine("${index + 1}. `${quote.chunkId}`")
            appendLine()
            appendLine("```text")
            appendLine(quote.text)
            appendLine("```")
            appendLine()
        }
    }

    private fun StringBuilder.appendSelectedChunks(
        chunks: List<RagContextChunk>,
    ) {
        appendLine("### Selected chunks")
        appendLine()

        if (chunks.isEmpty()) {
            appendLine("No selected chunks.")
            appendLine()
            return
        }

        appendLine("| # | Score | Source | Section | Chunk ID |")
        appendLine("|---:|---:|---|---|---|")
        chunks.forEachIndexed { index, chunk ->
            appendLine("| ${index + 1} | ${"%.4f".format(chunk.score)} | `${chunk.source}` | `${chunk.section}` | `${chunk.chunkId}` |")
        }
        appendLine()
    }
}
