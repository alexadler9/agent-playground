package domain.rag

class ChunkingComparisonReporter {

    fun buildReport(
        documents: List<SourceDocument>,
        strategies: List<ChunkingStrategy>,
    ): String {
        val reports = strategies.map { strategy ->
            val chunks = documents.flatMap { document -> strategy.chunk(document) }
            buildStrategyReport(
                strategy = strategy,
                chunks = chunks,
            )
        }

        return buildString {
            appendLine("# Сравнение стратегий chunking")
            appendLine()
            appendLine("Документов в корпусе: ${documents.size}")
            appendLine()

            appendLine("## Сводная таблица")
            appendLine()
            appendLine("| Стратегия | Количество чанков | Средний размер | Минимальный размер | Максимальный размер |")
            appendLine("|---|---:|---:|---:|---:|")

            reports.forEach { report ->
                appendLine(
                    "| ${report.strategyName} | ${report.chunksCount} | ${report.averageSize} | ${report.minSize} | ${report.maxSize} |",
                )
            }

            appendLine()

            reports.forEach { report ->
                appendLine("## Стратегия: ${report.strategyName}")
                appendLine()
                appendLine("Количество чанков: ${report.chunksCount}")
                appendLine("Средний размер чанка: ${report.averageSize} символов")
                appendLine("Минимальный размер чанка: ${report.minSize} символов")
                appendLine("Максимальный размер чанка: ${report.maxSize} символов")
                appendLine()

                appendLine("### Количество чанков по источникам")
                appendLine()

                report.chunksBySource.forEach { sourceReport ->
                    appendLine("- `${sourceReport.source}`: ${sourceReport.chunksCount}")
                }

                appendLine()
                appendLine("### Примеры чанков")
                appendLine()

                report.examples.forEachIndexed { index, chunk ->
                    appendLine("#### Пример ${index + 1}")
                    appendLine()
                    appendLine("- chunk_id: `${chunk.chunkId}`")
                    appendLine("- source: `${chunk.source}`")
                    appendLine("- title: `${chunk.title}`")
                    appendLine("- section: `${chunk.section}`")
                    appendLine("- размер: ${chunk.text.length} символов")
                    appendLine()
                    appendLine("```text")
                    appendLine(chunk.text.take(600))
                    appendLine("```")
                    appendLine()
                }
            }

            appendLine("## Вывод")
            appendLine()
            appendLine("Стратегия fixed-size создает предсказуемые чанки примерно одинакового размера. Ее удобно реализовывать и отлаживать, но она может разрывать один смысловой раздел на несколько частей или склеивать фрагменты из разных разделов")
            appendLine()
            appendLine("Стратегия structure-aware старается сохранять естественные границы документа: markdown-заголовки, Kotlin-объявления и текстовые абзацы. Такие чанки обычно лучше подходят для RAG, потому что внутри одного чанка чаще остается цельный смысловой блок. Минус в том, что размеры чанков получаются менее равномерными")
            appendLine()
            appendLine("Для проектной документации, отчетов и кода structure-aware chunking обычно предпочтительнее. Fixed-size chunking можно оставить как базовую стратегию и fallback для документов без понятной структуры")
        }
    }

    private fun buildStrategyReport(
        strategy: ChunkingStrategy,
        chunks: List<DocumentChunk>,
    ): StrategyReport {
        val sizes = chunks.map { chunk -> chunk.text.length }

        return StrategyReport(
            strategyName = strategy.name,
            chunksCount = chunks.size,
            averageSize = sizes.averageOrZero(),
            minSize = sizes.minOrNull() ?: 0,
            maxSize = sizes.maxOrNull() ?: 0,
            chunksBySource = chunks
                .groupBy { chunk -> chunk.source }
                .toSortedMap()
                .map { (source, sourceChunks) ->
                    SourceChunkReport(
                        source = source,
                        chunksCount = sourceChunks.size,
                    )
                },
            examples = pickExamples(chunks),
        )
    }

    private fun pickExamples(
        chunks: List<DocumentChunk>,
    ): List<DocumentChunk> {
        if (chunks.size <= 3) {
            return chunks
        }

        return listOfNotNull(
            chunks.firstOrNull(),
            chunks.getOrNull(chunks.size / 2),
            chunks.lastOrNull(),
        )
    }

    private fun List<Int>.averageOrZero(): Int {
        if (isEmpty()) {
            return 0
        }

        return average().toInt()
    }
}

private data class StrategyReport(
    val strategyName: String,
    val chunksCount: Int,
    val averageSize: Int,
    val minSize: Int,
    val maxSize: Int,
    val chunksBySource: List<SourceChunkReport>,
    val examples: List<DocumentChunk>,
)

private data class SourceChunkReport(
    val source: String,
    val chunksCount: Int,
)