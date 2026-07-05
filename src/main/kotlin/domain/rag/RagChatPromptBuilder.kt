package domain.rag

import domain.model.ChatMessage
import domain.model.ChatRole

/**
 * Prompt для production-like RAG-чата.
 *
 * В ответе модель обязана вернуть JSON с answer, sources и quotes.
 * История и task memory помогают не терять цель диалога,
 * а фактические утверждения должны опираться на RAG context.
 */
class RagChatPromptBuilder {

    fun buildMessages(
        userMessage: String,
        taskMemory: RagChatTaskMemory,
        history: List<RagChatMessage>,
        chunks: List<RagContextChunk>,
    ): List<ChatMessage> {
        return listOf(
            ChatMessage(
                role = ChatRole.SYSTEM,
                content = """
                    Ты — RAG-ассистент внутри CLI-чата.

                    Верни строго JSON без markdown и без дополнительного текста.

                    Формат:
                    {
                      "answer": "ответ на русском языке",
                      "sources": [
                        {
                          "source": "source из RAG_CONTEXT",
                          "section": "section из RAG_CONTEXT",
                          "chunk_id": "chunk_id из RAG_CONTEXT"
                        }
                      ],
                      "quotes": [
                        {
                          "source": "source из RAG_CONTEXT",
                          "section": "section из RAG_CONTEXT",
                          "chunk_id": "chunk_id из RAG_CONTEXT",
                          "text": "точная непрерывная цитата из chunk text"
                        }
                      ]
                    }

                    Правила:
                    - Отвечай с учетом цели диалога, task memory и последних сообщений.
                    - Факты о документах, проекте, решениях, коде и требованиях бери только из RAG_CONTEXT.
                    - Каждый source должен ссылаться на chunk_id из RAG_CONTEXT.
                    - Каждая quote должна быть точной непрерывной цитатой из chunk text.
                    - Не перефразируй quote: скопируй фрагмент как есть.
                    - Не считай похожие сущности одинаковыми, если в RAG_CONTEXT явно не сказано, что это одно и то же.
                    - Если RAG_CONTEXT недостаточен для ответа, верни:
                      {
                        "answer": "Не знаю: в локальном контексте недостаточно данных для надежного ответа. Пожалуйста, уточните вопрос или добавьте документы в базу знаний",
                        "sources": [],
                        "quotes": []
                      }
                """.trimIndent(),
            ),
            ChatMessage(
                role = ChatRole.USER,
                content = buildUserMessage(
                    userMessage = userMessage,
                    taskMemory = taskMemory,
                    history = history,
                    chunks = chunks,
                ),
            ),
        )
    }

    private fun buildUserMessage(
        userMessage: String,
        taskMemory: RagChatTaskMemory,
        history: List<RagChatMessage>,
        chunks: List<RagContextChunk>,
    ): String {
        return buildString {
            appendLine("TASK_MEMORY:")
            appendLine("goal: ${taskMemory.goal.ifBlank { "-" }}")
            appendLine("clarifications:")
            taskMemory.clarifications.forEach { appendLine("- $it") }
            appendLine("constraints:")
            taskMemory.constraints.forEach { appendLine("- $it") }
            appendLine("terms:")
            taskMemory.terms.forEach { appendLine("- $it") }

            appendLine()
            appendLine("RECENT_DIALOG_HISTORY:")
            history.takeLast(10).forEach { message ->
                appendLine("${message.role}: ${message.content}")
            }

            appendLine()
            appendLine("CURRENT_USER_MESSAGE:")
            appendLine(userMessage)

            appendLine()
            appendLine("RAG_CONTEXT:")
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