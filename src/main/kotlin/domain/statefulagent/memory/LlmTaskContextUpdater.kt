package domain.statefulagent.memory

import domain.llm.LlmGateway
import domain.model.AgentConfig
import domain.model.ChatMessage
import domain.model.ChatRole
import domain.statefulagent.model.TaskContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

class LlmTaskContextUpdater(
    private val llmGateway: LlmGateway,
    private val config: AgentConfig,
    private val json: Json,
) : TaskContextUpdater {

    override suspend fun updateTaskContext(
        currentContext: TaskContext,
        userMessage: String,
    ): TaskContext {
        val messages = listOf(
            ChatMessage(
                role = ChatRole.SYSTEM,
                content = TASK_CONTEXT_SYSTEM_PROMPT,
            ),
            ChatMessage(
                role = ChatRole.USER,
                content = buildUserPrompt(
                    currentContext = currentContext,
                    userMessage = userMessage,
                ),
            ),
        )

        val reply = llmGateway.sendMessages(
            messages = messages,
            config = config,
        )

        return parseTaskContext(reply.message.content)
    }

    private fun buildUserPrompt(
        currentContext: TaskContext,
        userMessage: String,
    ): String {
        val currentContextJson = json.encodeToString(currentContext.toDto())

        return """
            Текущий TaskContext:
            $currentContextJson
            
            Новое сообщение пользователя:
            $userMessage
            
            Обнови только рабочую память текущей задачи.
            
            Правила:
            - Сохраняй только информацию, которая относится к текущей задаче.
            - Не сохраняй обычный диалог, приветствия, технический шум и случайные временные фразы.
            - Не сохраняй долговременный профиль пользователя.
            - Не сохраняй общие знания, которые должны лежать в long-term memory.
            - Если сообщение содержит цель задачи, обнови goal.
            - Если сообщение содержит название задачи, обнови taskName.
            - Если сообщение содержит текущий этап, обнови currentStep.
            - Если сообщение содержит решение по задаче, добавь его в decisions.
            - Если сообщение содержит ограничение задачи, добавь его в constraints.
            - Если сообщение содержит выполненный пункт, добавь его в completedItems.
            - Если сообщение содержит оставшийся пункт, добавь его в pendingItems.
            - Если ничего важного для текущей задачи нет, верни текущий TaskContext без изменений.
            
            Верни только JSON-объект строго в таком формате:
            {
              "taskName": string или null,
              "goal": string или null,
              "currentStep": string или null,
              "completedItems": ["..."],
              "pendingItems": ["..."],
              "decisions": ["..."],
              "constraints": ["..."]
            }
        """.trimIndent()
    }

    private fun parseTaskContext(rawText: String): TaskContext {
        val cleanedText = rawText
            .trim()
            .removePrefix("```json")
            .removePrefix("```")
            .removeSuffix("```")
            .trim()

        val dto = json.decodeFromString<PersistedTaskContextDto>(cleanedText)

        return dto.toDomain()
    }

    private fun TaskContext.toDto(): PersistedTaskContextDto {
        return PersistedTaskContextDto(
            taskName = taskName,
            goal = goal,
            currentStep = currentStep,
            completedItems = completedItems,
            pendingItems = pendingItems,
            decisions = decisions,
            constraints = constraints,
        )
    }

    private fun PersistedTaskContextDto.toDomain(): TaskContext {
        return TaskContext(
            taskName = taskName,
            goal = goal,
            currentStep = currentStep,
            completedItems = completedItems,
            pendingItems = pendingItems,
            decisions = decisions,
            constraints = constraints,
        )
    }

    @Serializable
    private data class PersistedTaskContextDto(
        val taskName: String? = null,
        val goal: String? = null,
        val currentStep: String? = null,
        val completedItems: List<String> = emptyList(),
        val pendingItems: List<String> = emptyList(),
        val decisions: List<String> = emptyList(),
        val constraints: List<String> = emptyList(),
    )

    private companion object {
        const val TASK_CONTEXT_SYSTEM_PROMPT = """
            Ты обновляешь рабочую память ассистента.
            
            Рабочая память — это не история диалога и не долговременный профиль.
            Рабочая память хранит только состояние текущей задачи:
            цель, название задачи, текущий этап, выполненные пункты, оставшиеся пункты, решения и ограничения.
            
            Верни только валидный JSON.
            Не добавляй пояснения.
            Не используй markdown.
            Не добавляй поля вне заданной схемы.
        """
    }
}