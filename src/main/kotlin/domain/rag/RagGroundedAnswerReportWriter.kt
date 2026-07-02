package domain.rag

import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.writeText

/**
 * Пишет отчёт по grounded RAG-ответу.
 *
 * Здесь видно не только answer, но и проверку:
 * есть ли источники, есть ли цитаты, существуют ли они в selected chunks.
 */
class RagGroundedAnswerReportWriter {

    fun write(
        result: RagGroundedAnswerResult,
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
        result: RagGroundedAnswerResult,
    ): String {
        return buildString {
            appendLine("# Grounded RAG answer")
            appendLine()

            appendLine("## Question")
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

            appendLine("## Answer")
            appendLine()
            appendLine("- isKnown: `${result.isKnown}`")
            appendLine("- skippedLlm: `${result.skippedLlm}`")
            appendLine("- unknownReason: `${result.unknownReason ?: "-"}`")
            appendLine()
            appendLine("```text")
            appendLine(result.answer)
            appendLine("```")
            appendLine()

            appendValidation(result.validation)
            appendSources(result.sources)
            appendQuotes(result.quotes)
            appendSelectedChunks(result.selectedChunks)

            appendLine("## Manual meaning check")
            appendLine()
            appendLine("TODO: вручную проверить, что смысл ответа действительно подтверждается приведенными цитатами")
            appendLine()
        }
    }

    private fun StringBuilder.appendValidation(
        validation: RagGroundingValidation,
    ) {
        appendLine("## Validation")
        appendLine()
        appendLine("| Check | Result |")
        appendLine("|---|---:|")
        appendLine("| parseSucceeded | `${validation.parseSucceeded}` |")
        appendLine("| hasSources | `${validation.hasSources}` |")
        appendLine("| hasQuotes | `${validation.hasQuotes}` |")
        appendLine("| sourcesExistInSelectedChunks | `${validation.sourcesExistInSelectedChunks}` |")
        appendLine("| quotesExistInChunks | `${validation.quotesExistInChunks}` |")
        appendLine("| isValid | `${validation.isValid}` |")
        appendLine()

        if (validation.errors.isNotEmpty()) {
            appendLine("### Validation errors")
            appendLine()
            validation.errors.forEach { error ->
                appendLine("- $error")
            }
            appendLine()
        }
    }

    private fun StringBuilder.appendSources(
        sources: List<RagGroundedSource>,
    ) {
        appendLine("## Sources")
        appendLine()

        if (sources.isEmpty()) {
            appendLine("No sources.")
            appendLine()
            return
        }

        appendLine("| # | Score | Source | Section | Chunk ID |")
        appendLine("|---:|---:|---|---|---|")

        sources.forEachIndexed { index, source ->
            appendLine(
                "| ${index + 1} | ${"%.4f".format(source.score)} | `${source.source}` | `${source.section}` | `${source.chunkId}` |",
            )
        }

        appendLine()
    }

    private fun StringBuilder.appendQuotes(
        quotes: List<RagGroundedQuote>,
    ) {
        appendLine("## Quotes")
        appendLine()

        if (quotes.isEmpty()) {
            appendLine("No quotes.")
            appendLine()
            return
        }

        quotes.forEachIndexed { index, quote ->
            appendLine("### ${index + 1}. `${quote.chunkId}`")
            appendLine()
            appendLine("- source: `${quote.source}`")
            appendLine("- section: `${quote.section}`")
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
        appendLine("## Selected chunks")
        appendLine()

        if (chunks.isEmpty()) {
            appendLine("No selected chunks.")
            appendLine()
            return
        }

        appendLine("| # | Score | Source | Section | Chunk ID |")
        appendLine("|---:|---:|---|---|---|")

        chunks.forEachIndexed { index, chunk ->
            appendLine(
                "| ${index + 1} | ${"%.4f".format(chunk.score)} | `${chunk.source}` | `${chunk.section}` | `${chunk.chunkId}` |",
            )
        }

        appendLine()
    }
}