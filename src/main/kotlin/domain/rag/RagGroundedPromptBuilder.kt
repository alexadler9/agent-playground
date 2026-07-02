package domain.rag

import domain.model.ChatMessage
import domain.model.ChatRole

/**
 * Prompt для ответа с обязательными sources и quotes.
 *
 * Модель выбирает цитаты сама, но Kotlin потом проверяет,
 * что эти цитаты реально есть в selected chunks.
 */
class RagGroundedPromptBuilder {

    fun buildMessages(
        question: String,
        chunks: List<RagContextChunk>,
    ): List<ChatMessage> {
        return listOf(
            ChatMessage(
                role = ChatRole.SYSTEM,
                content = """
                    Ты отвечаешь на вопрос пользователя только по локальному RAG-контексту.

                    Верни строго JSON без markdown и без дополнительного текста.

                    Формат:
                    {
                      "answer": "ответ на русском языке",
                      "sources": [
                        {
                          "source": "source из CONTEXT",
                          "section": "section из CONTEXT",
                          "chunk_id": "chunk_id из CONTEXT"
                        }
                      ],
                      "quotes": [
                        {
                          "source": "source из CONTEXT",
                          "section": "section из CONTEXT",
                          "chunk_id": "chunk_id из CONTEXT",
                          "text": "точная непрерывная цитата из chunk text"
                        }
                      ]
                    }

                    Правила:
                    - Используй только факты из CONTEXT.
                    - Не добавляй внешние знания и не угадывай недостающие детали.
                    - Каждый source должен ссылаться на chunk_id из CONTEXT.
                    - Каждая quote должна быть точным непрерывным фрагментом из chunk text.
                    - Не перефразируй quote: скопируй фрагмент как есть.
                    - Не используй слишком длинные цитаты; достаточно 1-2 коротких фрагментов.
                    - Если CONTEXT недостаточен для ответа, верни:
                      {
                        "answer": "Не знаю: в локальном контексте недостаточно данных для надежного ответа. Пожалуйста, уточните вопрос или добавьте документы в базу знаний.",
                        "sources": [],
                        "quotes": []
                      }
                    - Сохраняй точные имена сущностей из CONTEXT.
                    - Не считай две похожие сущности одинаковыми, если в CONTEXT явно не сказано, что это одно и то же.
                """.trimIndent(),
            ),
            ChatMessage(
                role = ChatRole.USER,
                content = buildUserMessage(
                    question = question,
                    chunks = chunks,
                ),
            ),
        )
    }

    private fun buildUserMessage(
        question: String,
        chunks: List<RagContextChunk>,
    ): String {
        return buildString {
            appendLine("QUESTION:")
            appendLine(question)
            appendLine()
            appendLine("CONTEXT:")

            chunks.forEachIndexed { index, chunk ->
                appendLine()
                appendLine("[${index + 1}]")
                appendLine("source: ${chunk.source}")
                appendLine("section: ${chunk.section}")
                appendLine("chunk_id: ${chunk.chunkId}")
                appendLine("score: ${"%.4f".format(chunk.score)}")
                appendLine("text:")
                appendLine(chunk.text)
            }
        }
    }
}