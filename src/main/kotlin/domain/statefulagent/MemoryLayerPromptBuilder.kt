package domain.statefulagent

import domain.model.AgentConfig
import domain.model.ChatMessage
import domain.model.ChatRole
import domain.statefulagent.model.AssistantMemory
import domain.statefulagent.model.LongTermMemory
import domain.statefulagent.model.TaskContext

class MemoryLayerPromptBuilder {

    fun buildPrompt(
        config: AgentConfig,
        memory: AssistantMemory,
        userMessage: String,
    ): List<ChatMessage> {
        val systemMessage = ChatMessage(
            role = ChatRole.SYSTEM,
            content = buildSystemPrompt(
                baseSystemPrompt = config.systemPrompt,
                workingMemory = memory.workingMemory,
                longTermMemory = memory.longTermMemory,
            ),
        )

        val currentUserMessage = ChatMessage(
            role = ChatRole.USER,
            content = userMessage,
        )

        return buildList {
            add(systemMessage)
            addAll(memory.shortTermMemory)
            add(currentUserMessage)
        }
    }

    private fun buildSystemPrompt(
        baseSystemPrompt: String,
        workingMemory: TaskContext,
        longTermMemory: LongTermMemory,
    ): String {
        return buildString {
            appendLine(baseSystemPrompt)
            appendLine()
            appendLine("Ты stateful-ассистент с явной моделью памяти.")
            appendLine("Используй слои памяти строго по их назначению:")
            appendLine("- Краткосрочная память: сообщения текущего диалога.")
            appendLine("- Рабочая память: контекст текущей задачи, прогресс, решения и ограничения.")
            appendLine("- Долговременная память: профиль пользователя, устойчивые проектные решения и переиспользуемые знания.")
            appendLine()
            appendLine("Не смешивай слои памяти по смыслу.")
            appendLine("Если пользователь спрашивает, что где сохранено, объясняй это на основе явных блоков памяти.")
            appendLine()

            appendWorkingMemory(workingMemory)
            appendLine()
            appendLongTermMemory(longTermMemory)
        }
    }

    private fun StringBuilder.appendWorkingMemory(taskContext: TaskContext) {
        appendLine("## Рабочая память / контекст текущей задачи")

        if (taskContext.isEmpty) {
            appendLine("Рабочая память пока пустая.")
            return
        }

        taskContext.taskName?.let { appendLine("Название задачи: $it") }
        taskContext.goal?.let { appendLine("Цель: $it") }
        taskContext.currentStep?.let { appendLine("Текущий этап: $it") }

        appendListBlock(
            title = "Выполнено",
            items = taskContext.completedItems,
        )

        appendListBlock(
            title = "Осталось сделать",
            items = taskContext.pendingItems,
        )

        appendListBlock(
            title = "Решения по задаче",
            items = taskContext.decisions,
        )

        appendListBlock(
            title = "Ограничения задачи",
            items = taskContext.constraints,
        )
    }

    private fun StringBuilder.appendLongTermMemory(longTermMemory: LongTermMemory) {
        appendLine("## Долговременная память")

        if (longTermMemory.isEmpty) {
            appendLine("Долговременная память пока пустая.")
            return
        }

        appendMarkdownBlock(
            title = "Профиль пользователя",
            content = longTermMemory.profile,
        )

        appendMarkdownBlock(
            title = "Устойчивые решения",
            content = longTermMemory.decisions,
        )

        appendMarkdownBlock(
            title = "Знания",
            content = longTermMemory.knowledge,
        )
    }

    private fun StringBuilder.appendListBlock(
        title: String,
        items: List<String>,
    ) {
        appendLine()
        appendLine("$title:")

        if (items.isEmpty()) {
            appendLine("- нет")
        } else {
            items.forEach { item ->
                appendLine("- $item")
            }
        }
    }

    private fun StringBuilder.appendMarkdownBlock(
        title: String,
        content: String,
    ) {
        appendLine()
        appendLine("### $title")

        if (content.isBlank()) {
            appendLine("пусто")
        } else {
            appendLine(content.trim())
        }
    }
}