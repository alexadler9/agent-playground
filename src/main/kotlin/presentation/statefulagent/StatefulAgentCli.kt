package presentation.statefulagent

import domain.model.ChatMessage
import domain.model.ChatRole
import domain.statefulagent.MemoryLayerAgentService
import domain.statefulagent.memory.UserProfileRepository
import domain.statefulagent.model.AssistantMemory
import domain.statefulagent.model.LongTermMemory
import domain.statefulagent.model.TaskContext
import kotlinx.coroutines.runBlocking
import presentation.common.ConsoleInput
import presentation.common.clearThinkingLine
import presentation.common.launchThinkingIndicator

class StatefulAgentCli(
    private val agentService: MemoryLayerAgentService,
    private val userProfileRepository: UserProfileRepository,
    private val consoleInput: ConsoleInput = ConsoleInput(),
) {

    fun start() = runBlocking {
        printHeader()

        while (true) {
            val input = consoleInput.readLine(USER_INPUT_PREFIX)
                ?.trim()
                .orEmpty()

            when {
                input.isBlank() -> Unit

                input == EXIT_COMMAND -> {
                    println("Завершаем работу")
                    return@runBlocking
                }

                input.startsWith(MEMORY_COMMAND) -> {
                    handleMemoryCommand(input)
                }

                input.startsWith(PROFILE_COMMAND) -> {
                    handleProfileCommand(input)
                }

                input == CLEAR_SHORT_COMMAND -> {
                    agentService.clearShortTermMemory()
                    println("Краткосрочная память очищена")
                    println()
                }

                input == CLEAR_WORK_COMMAND -> {
                    agentService.clearWorkingMemory()
                    println("Рабочая память очищена")
                    println()
                }

                else -> {
                    val thinkingJob = launchThinkingIndicator()

                    val reply = try {
                        agentService.sendMessage(input)
                    } finally {
                        thinkingJob.cancel()
                        clearThinkingLine()
                    }

                    println()
                    println("Агент:")
                    println(reply.message.content)

                    reply.responseTimeMs?.let { responseTimeMs ->
                        println()
                        println("Time: ${responseTimeMs}ms")
                    }

                    reply.tokenUsage?.let { usage ->
                        println(
                            "API: prompt=${usage.promptTokens ?: "?"}, " +
                                    "completion=${usage.completionTokens ?: "?"}, " +
                                    "total=${usage.totalTokens ?: "?"}"
                        )
                    }

                    println()
                }
            }
        }
    }

    private suspend fun handleMemoryCommand(input: String) {
        val argument = input
            .substringAfter(" ", missingDelimiterValue = "")
            .trim()
            .lowercase()

        val memory = agentService.getMemory()

        when (argument) {
            "", "all" -> printAllMemory(memory)
            "short" -> printShortTermMemory(memory.shortTermMemory)
            "work", "working" -> printWorkingMemory(memory.workingMemory)
            "long" -> printLongTermMemory(memory.longTermMemory)
            else -> {
                println("Неизвестный слой памяти: $argument")
                println("Доступно: /memory, /memory short, /memory work, /memory long")
            }
        }

        println()
    }

    private suspend fun handleProfileCommand(input: String) {
        val parts = input
            .split(" ")
            .filter { part -> part.isNotBlank() }

        val action = parts.getOrNull(1)?.lowercase() ?: "current"

        when (action) {
            "current" -> {
                val activeProfile = userProfileRepository.getActiveProfileName()
                println("Активный профиль: $activeProfile")
            }

            "list" -> {
                val activeProfile = userProfileRepository.getActiveProfileName()
                val profiles = userProfileRepository.listProfiles()

                println("Доступные профили:")

                if (profiles.isEmpty()) {
                    println("- профилей нет")
                } else {
                    profiles.forEach { profile ->
                        val marker = if (profile == activeProfile) "*" else " "
                        println("$marker $profile")
                    }
                }
            }

            "show" -> {
                val activeProfile = userProfileRepository.getActiveProfileName()
                val content = userProfileRepository.getActiveProfileContent()

                println("Активный профиль: $activeProfile")
                println()
                println(content)
            }

            "switch" -> {
                val profileName = parts.getOrNull(2)

                if (profileName.isNullOrBlank()) {
                    println("Укажите имя профиля: $PROFILE_COMMAND switch <name>")
                    return
                }

                try {
                    userProfileRepository.switchProfile(profileName)
                    println("Активный профиль переключён на: $profileName")
                } catch (e: IllegalArgumentException) {
                    println(e.message)
                }
            }

            else -> {
                println("Неизвестная команда профиля: $action")
                println("Доступно: current, list, show, switch <name>")
            }
        }

        println()
    }

    private fun printAllMemory(memory: AssistantMemory) {
        printShortTermMemory(memory.shortTermMemory)
        println()
        printWorkingMemory(memory.workingMemory)
        println()
        printLongTermMemory(memory.longTermMemory)
    }

    private fun printShortTermMemory(messages: List<ChatMessage>) {
        println("=== Краткосрочная память: текущий диалог ===")

        if (messages.isEmpty()) {
            println("Пусто")
            return
        }

        messages.forEachIndexed { index, message ->
            println("${index + 1}. ${message.role.toDisplayName()}: ${message.content}")
        }
    }

    private fun printWorkingMemory(taskContext: TaskContext) {
        println("=== Рабочая память: контекст текущей задачи ===")

        if (taskContext.isEmpty) {
            println("Пусто")
            return
        }

        taskContext.taskName?.let { println("Название задачи: $it") }
        taskContext.goal?.let { println("Цель: $it") }
        taskContext.currentStep?.let { println("Текущий этап: $it") }

        printList("Выполнено", taskContext.completedItems)
        printList("Осталось сделать", taskContext.pendingItems)
        printList("Решения", taskContext.decisions)
        printList("Ограничения", taskContext.constraints)
    }

    private fun printLongTermMemory(longTermMemory: LongTermMemory) {
        println("=== Долговременная память: профиль, решения, знания ===")

        if (longTermMemory.isEmpty) {
            println("Пусто")
            return
        }

        printMarkdownBlock("Профиль пользователя", longTermMemory.profile)
        printMarkdownBlock("Устойчивые решения", longTermMemory.decisions)
        printMarkdownBlock("Знания", longTermMemory.knowledge)
    }

    private fun printList(
        title: String,
        items: List<String>,
    ) {
        println("$title:")

        if (items.isEmpty()) {
            println("- нет")
        } else {
            items.forEach { item ->
                println("- $item")
            }
        }
    }

    private fun printMarkdownBlock(
        title: String,
        content: String,
    ) {
        println()
        println("### $title")

        if (content.isBlank()) {
            println("пусто")
        } else {
            println(content.trim())
        }
    }

    private fun ChatRole.toDisplayName(): String {
        return when (this) {
            ChatRole.SYSTEM -> "system"
            ChatRole.USER -> "user"
            ChatRole.ASSISTANT -> "assistant"
        }
    }

    private fun printHeader() {
        println("Stateful Agent")
        println("Команды:")
        println("$MEMORY_COMMAND [all|short|work|long] — показать слои памяти")
        println("$PROFILE_COMMAND [current|list|show|switch <name>] — управление профилями")
        println("$CLEAR_SHORT_COMMAND — очистить краткосрочную память")
        println("$CLEAR_WORK_COMMAND — очистить рабочую память")
        println("$EXIT_COMMAND — выйти")
        println()
    }

    private companion object {
        const val USER_INPUT_PREFIX = "Вы: "
        const val MEMORY_COMMAND = "/memory"
        const val PROFILE_COMMAND = "/profile"
        const val CLEAR_SHORT_COMMAND = "/clear-short"
        const val CLEAR_WORK_COMMAND = "/clear-work"
        const val EXIT_COMMAND = "/exit"
    }
}