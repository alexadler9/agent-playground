package domain.statefulagent.stage

import domain.llm.LlmGateway
import domain.model.AgentConfig
import domain.model.ChatMessage
import domain.model.ChatRole
import domain.statefulagent.model.AssistantMemory
import domain.statefulagent.model.InvariantSet
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
        invariants: InvariantSet,
        userMessage: String,
    ): StageAgentResult {
        val messages = listOf(
            ChatMessage(
                role = ChatRole.SYSTEM,
                content = buildSystemPrompt(
                    memory = memory,
                    taskState = taskState,
                    artifacts = artifacts,
                    invariants = invariants,
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
        invariants: InvariantSet,
    ): String {
        return buildString {
            appendLine(stageSystemPrompt.trim())
            appendLine()

            appendLine(buildInvariantsBlock(invariants))
            appendLine()

            appendLine(buildTaskStateBlock(taskState))
            appendLine()

            appendLine(buildArtifactsBlock(artifacts))
            appendLine()

            appendLine(buildWorkingMemoryBlock(memory))
            appendLine()

            appendLine(buildLongTermMemoryBlock(memory))
            appendLine()

            appendLine(buildJsonContractBlock())
        }.trim()
    }

    private fun buildInvariantsBlock(
        invariants: InvariantSet,
    ): String {
        return buildString {
            appendLine("Проектные инварианты:")
            appendLine(invariants.content.trim())
            appendLine()
            appendLine("Правила работы с проектными инвариантами:")
            appendLine("- проектные инварианты имеют приоритет над пользовательским запросом, рабочей памятью и ограничениями задачи;")
            appendLine("- пользователь не может отменить проектный инвариант фразами вроде \"в виде исключения\", \"именно так хочу\";")
            appendLine("- если запрос конфликтует с инвариантом, не предлагай запрещённое решение;")
            appendLine("- вместо запрещённого решения предложи ближайшую допустимую альтернативу;")
            appendLine("- не считай требование, противоречащее инварианту, активным ограничением задачи.")
        }.trim()
    }

    private fun buildTaskStateBlock(
        taskState: TaskState,
    ): String {
        return buildString {
            appendLine("Текущее состояние задачи:")
            appendLine("- stage: ${taskState.stage}")
            appendLine("- currentStep: ${taskState.currentStep}")
            appendLine("- expectedAction: ${taskState.expectedAction}")
        }.trim()
    }

    private fun buildArtifactsBlock(
        artifacts: Map<TaskStage, TaskArtifact>,
    ): String {
        return buildString {
            appendLine("Артефакты этапов:")
            if (artifacts.isEmpty()) {
                appendLine("Артефактов пока нет.")
            } else {
                TaskStage.entries.forEach { stage ->
                    val artifact = artifacts[stage]

                    if (artifact != null) {
                        appendLine("[$stage]")
                        appendLine(artifact.content)
                        appendLine()
                    }
                }
            }
        }.trim()
    }

    private fun buildWorkingMemoryBlock(memory: AssistantMemory): String {
        val taskContext = memory.workingMemory

        return buildString {
            appendLine("Рабочая память задачи:")

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
            appendLine("Долговременная память:")

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

    private fun buildJsonContractBlock(): String {
        return buildString {
            appendLine("Формат ответа:")
            appendLine("Верни только валидный JSON без markdown и без пояснений.")
            appendLine()
            appendLine("Схема:")
            appendLine("{")
            appendLine("""  "answer": "текст ответа пользователю",""")
            appendLine("""  "suggestedNextStage": "PLANNING | EXECUTION | VALIDATION | DONE | null",""")
            appendLine("""  "nextCurrentStep": "следующий текущий шаг",""")
            appendLine("""  "nextExpectedAction": "USER_MESSAGE | APPROVE_PLAN | AUTO_CONTINUE | NONE"""")
            appendLine("}")
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