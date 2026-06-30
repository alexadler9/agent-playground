package domain.rag

import domain.model.ChatMessage
import domain.model.ChatRole

/**
 * Собирает prompt-ы для двух режимов:
 * обычный ответ модели и ответ строго по найденному RAG-контексту.
 */
class RagPromptBuilder {

    fun buildNoRagMessages(
        question: String,
    ): List<ChatMessage> {
        return listOf(
            ChatMessage(
                role = ChatRole.SYSTEM,
                content = """
                    Ты отвечаешь на вопрос пользователя как обычная LLM.
                    Локальный RAG-контекст в этом режиме не используется.
                    Если вопрос касается внутренних деталей проекта, не выдумывай факты.
                    Отвечай на русском языке.
                """.trimIndent(),
            ),
            ChatMessage(
                role = ChatRole.USER,
                content = question,
            ),
        )
    }

    fun buildRagMessages(
        question: String,
        chunks: List<RagContextChunk>,
    ): List<ChatMessage> {
        return listOf(
            ChatMessage(
                role = ChatRole.SYSTEM,
                content = """
                    Ты отвечаешь на вопрос пользователя только по CONTEXT.

                    Правила:
                    - Не используй внешние знания модели, если факта нет в CONTEXT.
                    - Не додумывай детали проекта.
                    - Если CONTEXT недостаточно, прямо скажи, что в локальном индексе недостаточно данных.
                    - В конце ответа укажи использованные источники: source, section, chunk_id.
                    - Отвечай на русском языке.
                """.trimIndent(),
            ),
            ChatMessage(
                role = ChatRole.USER,
                content = buildString {
                    appendLine("QUESTION:")
                    appendLine(question)
                    appendLine()
                    appendLine("CONTEXT:")
                    appendLine(formatChunks(chunks))
                },
            ),
        )
    }

    private fun formatChunks(
        chunks: List<RagContextChunk>,
    ): String {
        return chunks.joinToString(separator = "\n\n") { chunk ->
            """
            [chunk_id: ${chunk.chunkId}]
            source: ${chunk.source}
            title: ${chunk.title}
            section: ${chunk.section}
            score: ${"%.4f".format(chunk.score)}

            ${chunk.text}
            """.trimIndent()
        }
    }
}