package domain.privatechat

import data.local.OllamaChatClient
import data.local.OllamaChatMessage
import kotlin.system.measureTimeMillis

class PrivateChatService(
    private val client: OllamaChatClient,
    private val model: String,
) {

    private val maxMessages = 20
    private val maxMessageChars = 2_000
    private val maxTotalContextChars = 12_000

    suspend fun chat(
        request: PrivateChatRequest,
    ): PrivateChatResponse {
        val messages = request.messages
            .map { it.copy(content = it.content.trim()) }
            .filter { it.content.isNotBlank() }
            .takeLast(maxMessages)

        validateMessages(messages)

        val ollamaMessages = buildList {
            add(
                OllamaChatMessage(
                    role = "system",
                    content = buildSystemPrompt(),
                ),
            )

            messages.forEach { message ->
                add(
                    OllamaChatMessage(
                        role = message.role,
                        content = message.content,
                    ),
                )
            }
        }

        var answer = ""

        val durationMs = measureTimeMillis {
            val response = client.chat(
                messages = ollamaMessages,
                temperature = 0.3,
                maxTokens = 700,
                contextWindow = 4096,
            )

            answer = response.message?.content.orEmpty().trim()
        }

        return PrivateChatResponse(
            answer = answer.ifBlank { "Модель вернула пустой ответ." },
            model = model,
            durationMs = durationMs,
            usedMessages = messages.size,
        )
    }

    private fun validateMessages(
        messages: List<PrivateChatMessage>,
    ) {
        if (messages.isEmpty()) {
            throw IllegalArgumentException("messages must not be empty")
        }

        messages.forEach { message ->
            if (message.role != "user" && message.role != "assistant") {
                throw IllegalArgumentException("message role must be user or assistant")
            }

            if (message.content.length > maxMessageChars) {
                throw IllegalArgumentException("single message is too long")
            }
        }

        val totalChars = messages.sumOf { it.content.length }

        if (totalChars > maxTotalContextChars) {
            throw IllegalArgumentException("context is too long")
        }
    }

    private fun buildSystemPrompt(): String {
        return """
            Ты приватный локальный AI-ассистент.
            Отвечай на языке пользователя.
            Пиши понятно, полезно и без лишней воды.
            Не выдумывай факты, если не уверен.
            Если вопрос требует уточнения, задай короткий уточняющий вопрос.
        """.trimIndent()
    }
}