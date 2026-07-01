package domain.rag

import domain.llm.LlmGateway
import domain.model.AgentConfig
import domain.model.ChatMessage
import domain.model.ChatRole

/**
 * Переписывает пользовательский вопрос в форму, удобную для retrieval.
 *
 * Для смешанного корпуса важно сохранить смысл вопроса
 * и добавить точные технические термины на английском, если они уместны.
 */
class RagQueryRewriter(
    private val llmGateway: LlmGateway,
    private val agentConfig: AgentConfig,
) {

    suspend fun rewrite(question: String): String {
        require(question.isNotBlank()) { "question must not be blank" }

        val reply = llmGateway.sendMessages(
            config = agentConfig,
            messages = listOf(
                ChatMessage(
                    role = ChatRole.SYSTEM,
                    content = """
                        Ты переписываешь вопрос для поиска по локальному RAG-индексу.

                        Корпус смешанный:
                        - русская документация;
                        - английские технические термины;
                        - Kotlin identifiers;
                        - названия MCP tools, классов, стадий и файлов.

                        Верни только один поисковый запрос без markdown.
                        Сохрани исходный смысл вопроса.
                        Добавь важные технические термины, если они уже есть в вопросе или явно подразумеваются.
                        Не выдумывай факты, которых нет в вопросе.
                    """.trimIndent(),
                ),
                ChatMessage(
                    role = ChatRole.USER,
                    content = question,
                ),
            ),
        )

        return reply.message.content
            .trim()
            .removePrefix("\"")
            .removeSuffix("\"")
            .takeIf { rewritten -> rewritten.isNotBlank() }
            ?: question
    }
}