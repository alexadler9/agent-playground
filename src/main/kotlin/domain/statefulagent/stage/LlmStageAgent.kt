package domain.statefulagent.stage

import domain.llm.LlmGateway
import domain.model.AgentConfig
import domain.model.ChatMessage
import domain.model.ChatRole
import domain.statefulagent.model.AssistantMemory
import domain.statefulagent.model.StageAgentResult
import domain.statefulagent.model.TaskArtifact
import domain.statefulagent.model.TaskStage
import domain.statefulagent.model.TaskState
import kotlinx.serialization.json.Json

abstract class LlmStageAgent(
    private val llmGateway: LlmGateway,
    private val config: AgentConfig,
    private val json: Json,
) : StageAgent {

    protected abstract val stageSystemPrompt: String

    override suspend fun handle(
        memory: AssistantMemory,
        taskState: TaskState,
        artifacts: Map<TaskStage, TaskArtifact>,
        userMessage: String,
    ): StageAgentResult {
        val messages = listOf(
            ChatMessage(
                role = ChatRole.SYSTEM,
                content = buildSystemPrompt(
                    memory = memory,
                    taskState = taskState,
                    artifacts = artifacts,
                ),
            ),
            ChatMessage(
                role = ChatRole.USER,
                content = userMessage,
            ),
        )

        val reply = llmGateway.sendMessages(
            messages = messages,
            config = config,
        )

        return parseResult(reply.message.content)
    }

    private fun buildSystemPrompt(
        memory: AssistantMemory,
        taskState: TaskState,
        artifacts: Map<TaskStage, TaskArtifact>,
    ): String {
        return """
            $stageSystemPrompt
            
            Текущее состояние задачи:
            - stage: ${taskState.stage}
            - currentStep: ${taskState.currentStep}
            - expectedAction: ${taskState.expectedAction}
            
            Артефакты этапов:
            ${buildArtifactsBlock(artifacts)}
    
            Рабочая память задачи:
            ${buildWorkingMemoryBlock(memory)}
            
            Долговременная память:
            ${buildLongTermMemoryBlock(memory)}
            
            Верни только валидный JSON без markdown и без пояснений.
            Формат:
            {
              "answer": "текст ответа пользователю",
              "suggestedNextStage": "PLANNING | EXECUTION | VALIDATION | DONE | null",
              "nextCurrentStep": "следующий текущий шаг",
              "nextExpectedAction": "USER_MESSAGE | APPROVE_PLAN | AUTO_CONTINUE | NONE"
            }
        """.trimIndent()
    }

    private fun buildWorkingMemoryBlock(memory: AssistantMemory): String {
        val taskContext = memory.workingMemory

        return buildString {
            if (taskContext.isEmpty) {
                appendLine("Рабочая память пока пустая.")
                return@buildString
            }

            taskContext.taskName?.let { appendLine("Название задачи: $it") }
            taskContext.goal?.let { appendLine("Цель: $it") }
            taskContext.currentStep?.let { appendLine("Текущий этап: $it") }

            appendList("Выполнено", taskContext.completedItems)
            appendList("Осталось сделать", taskContext.pendingItems)
            appendList("Решения", taskContext.decisions)
            appendList("Ограничения", taskContext.constraints)
        }
    }

    private fun buildLongTermMemoryBlock(memory: AssistantMemory): String {
        val longTermMemory = memory.longTermMemory

        return buildString {
            appendLine("Профиль:")
            appendLine(longTermMemory.profile.ifBlank { "Профиль пуст." })
            appendLine()
            appendLine("Устойчивые решения:")
            appendLine(longTermMemory.decisions.ifBlank { "Нет устойчивых решений." })
            appendLine()
            appendLine("Знания:")
            appendLine(longTermMemory.knowledge.ifBlank { "Нет сохранённых знаний." })
        }
    }

    private fun buildArtifactsBlock(
        artifacts: Map<TaskStage, TaskArtifact>,
    ): String {
        if (artifacts.isEmpty()) {
            return "Артефактов пока нет."
        }

        return buildString {
            TaskStage.entries.forEach { stage ->
                val artifact = artifacts[stage]

                if (artifact != null) {
                    appendLine("[$stage]")
                    appendLine(artifact.content)
                    appendLine()
                }
            }
        }.trim()
    }

    private fun StringBuilder.appendList(
        title: String,
        items: List<String>,
    ) {
        appendLine("$title:")

        if (items.isEmpty()) {
            appendLine("- нет")
        } else {
            items.forEach { item ->
                appendLine("- $item")
            }
        }
    }

    private fun parseResult(rawText: String): StageAgentResult {
        val cleanedText = rawText
            .trim()
            .removePrefix("```json")
            .removePrefix("```")
            .removeSuffix("```")
            .trim()

        val dto = json.decodeFromString<StageAgentResponseDto>(cleanedText)

        return dto.toDomain()
    }
}