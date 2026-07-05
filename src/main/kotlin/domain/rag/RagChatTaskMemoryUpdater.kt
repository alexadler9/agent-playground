package domain.rag

import domain.llm.LlmGateway
import domain.model.AgentConfig
import domain.model.ChatMessage
import domain.model.ChatRole
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * Обновляет память задачи по ходу диалога.
 *
 * В память пишем не знания из RAG-базы, а только состояние текущего диалога:
 * цель, уточнения пользователя, ограничения и важные термины.
 */
class RagChatTaskMemoryUpdater(
    private val llmGateway: LlmGateway,
    private val config: AgentConfig,
    private val json: Json,
) {

    suspend fun update(
        currentMemory: RagChatTaskMemory,
        recentHistory: List<RagChatMessage>,
        userMessage: String,
    ): RagChatTaskMemory {
        val rawResponse = llmGateway.sendMessages(
            config = config,
            messages = listOf(
                ChatMessage(
                    role = ChatRole.SYSTEM,
                    content = """
                        Ты обновляешь task memory для RAG-чата.

                        Нужно сохранить только устойчивое состояние текущего диалога:
                        - цель пользователя;
                        - что пользователь уже уточнил;
                        - ограничения, которые нужно учитывать дальше;
                        - важные термины/названия, зафиксированные в диалоге.

                        Не добавляй факты из внешних знаний.
                        Не добавляй факты из RAG-документов.
                        Не выдумывай цель, если пользователь её не обозначил.
                        Сохраняй коротко.

                        Верни строго JSON без markdown:
                        {
                          "goal": "...",
                          "clarifications": ["..."],
                          "constraints": ["..."],
                          "terms": ["..."]
                        }
                    """.trimIndent(),
                ),
                ChatMessage(
                    role = ChatRole.USER,
                    content = buildUserMessage(
                        currentMemory = currentMemory,
                        recentHistory = recentHistory,
                        userMessage = userMessage,
                    ),
                ),
            ),
        ).message.content.trim()

        return try {
            val dto = json.decodeFromString<RagChatTaskMemoryDto>(
                extractJsonObject(rawResponse),
            )

            RagChatTaskMemory(
                goal = dto.goal.trim(),
                clarifications = dto.clarifications.map { it.trim() }.filter { it.isNotBlank() }.distinct(),
                constraints = dto.constraints.map { it.trim() }.filter { it.isNotBlank() }.distinct(),
                terms = dto.terms.map { it.trim() }.filter { it.isNotBlank() }.distinct(),
            )
        } catch (_: Exception) {
            currentMemory
        }
    }

    private fun buildUserMessage(
        currentMemory: RagChatTaskMemory,
        recentHistory: List<RagChatMessage>,
        userMessage: String,
    ): String {
        return buildString {
            appendLine("CURRENT_MEMORY:")
            appendLine("goal: ${currentMemory.goal}")
            appendLine("clarifications:")
            currentMemory.clarifications.forEach { appendLine("- $it") }
            appendLine("constraints:")
            currentMemory.constraints.forEach { appendLine("- $it") }
            appendLine("terms:")
            currentMemory.terms.forEach { appendLine("- $it") }

            appendLine()
            appendLine("RECENT_HISTORY:")
            recentHistory.takeLast(8).forEach { message ->
                appendLine("${message.role}: ${message.content}")
            }

            appendLine()
            appendLine("NEW_USER_MESSAGE:")
            appendLine(userMessage)
        }
    }

    private fun extractJsonObject(rawResponse: String): String {
        val start = rawResponse.indexOf('{')
        val end = rawResponse.lastIndexOf('}')

        require(start in 0..<end) {
            "LLM response does not contain JSON object."
        }

        return rawResponse.substring(start, end + 1)
    }
}

@Serializable
private data class RagChatTaskMemoryDto(
    val goal: String = "",
    val clarifications: List<String> = emptyList(),
    val constraints: List<String> = emptyList(),
    val terms: List<String> = emptyList(),
)