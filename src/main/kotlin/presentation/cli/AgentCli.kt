package presentation.cli

import domain.agent.AgentService
import domain.context.ContextBuilderProvider
import domain.context.ContextStrategyType
import domain.memory.BranchManager
import domain.model.ChatRole
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class AgentCli(
    private val agentService: AgentService,
    private val contextBuilderProvider: ContextBuilderProvider,
    private val branchManager: BranchManager,
    private val consoleInput: ConsoleInput = ConsoleInput(),
    private val stressTextBuilder: ProjectFilesStressTextBuilder = ProjectFilesStressTextBuilder(),
) {

    suspend fun start() {
        printHeader()

        while (true) {
            val input = consoleInput.readLine("Вы: ")
                ?: return

            when {
                input.isBlank() -> {
                    println("Сообщение пустое. Введите текст или команду")
                }

                input == EXIT_COMMAND -> {
                    println("Выход из Agent Playground")
                    return
                }

                input == HISTORY_COMMAND -> {
                    printHistory()
                }

                input == CLEAR_COMMAND -> {
                    agentService.clearHistory()
                    println("История текущей сессии очищена")
                }

                input.startsWith(STRESS_CONTEXT_COMMAND) -> {
                    sendStressContext(input)
                }

                input.startsWith(STRATEGY_COMMAND) -> {
                    handleStrategyCommand(input)
                }

                input.startsWith(BRANCH_COMMAND) -> {
                    handleBranchCommand(input)
                }

                else -> {
                    sendMessage(input)
                }
            }

            println()
        }
    }

    private suspend fun sendMessage(input: String) = coroutineScope {
        var thinkingJob: Job? = null

        try {
            thinkingJob = launchThinkingIndicator()

            val reply = agentService.sendMessage(input)

            thinkingJob.cancel()
            clearThinkingLine()

            println()
            println("Агент:")
            println(reply.message.content)

            println()
            println(
                "State: strategy=${contextBuilderProvider.currentStrategy.toDisplayName()}, " +
                        "branch=${branchManager.currentBranch.name}"
            )

            reply.estimatedTokenStats?.let { stats ->
                val contextDeltaLabel = if (stats.savedContextTokens >= 0) {
                    "saved"
                } else {
                    "overhead"
                }

                val contextDelta = kotlin.math.abs(stats.savedContextTokens)

                println(
                    "Context: ${stats.actualContextTokens} tokens, " +
                            "$contextDeltaLabel: $contextDelta, " +
                            "messages: ${stats.contextMessageCount}"
                )
            }

            reply.tokenUsage?.let { usage ->
                println(
                    "API: prompt=${usage.promptTokens ?: "?"}, " +
                            "completion=${usage.completionTokens ?: "?"}, " +
                            "total=${usage.totalTokens ?: "?"}"
                )
            }

            reply.responseTimeMs?.let { responseTimeMs ->
                println("Time: ${responseTimeMs}ms")
            }
        } catch (e: Exception) {
            thinkingJob?.cancel()
            clearThinkingLine()

            println()
            println("Ошибка:")
            println(e.message ?: e::class.simpleName)
        }
    }

    private suspend fun printHistory() {
        val history = agentService.getHistory()

        if (history.isEmpty()) {
            println("История текущей сессии пуста")
            return
        }

        println()
        println("История текущей сессии:")

        history.forEachIndexed { index, message ->
            println()
            println("${index + 1}. ${message.role.toDisplayName()}:")
            println(message.content)
        }
    }

    private fun printHeader() {
        println("=== Agent Playground ===")
        println("Это простой CLI-агент с session memory")
        println()
        println("Команды:")
        println("$HISTORY_COMMAND — показать историю текущей сессии")
        println("$CLEAR_COMMAND   — очистить историю текущей сессии")
        println("$STRESS_CONTEXT_COMMAND [repeat] — отправить файлы проекта для stress-test контекста")
        println("$STRATEGY_COMMAND [current|full|sliding|facts] — управление стратегией контекста")
        println("$BRANCH_COMMAND [current|list|create <name>|switch <name>] — управление ветками")
        println("$EXIT_COMMAND    — выйти")
        println()
    }

    private suspend fun sendStressContext(input: String) {
        val repeatCount = input
            .substringAfter(" ", missingDelimiterValue = "3")
            .trim()
            .toIntOrNull()
            ?.coerceIn(1, 100)
            ?: 3

        val stressText = stressTextBuilder.build(repeatCount)

        println("Stress context payload prepared")
        println("Repeat count: $repeatCount")
        println("Payload chars: ${stressText.length}")

        sendMessage(stressText)
    }

    private fun handleStrategyCommand(input: String) {
        val strategyName = input
            .substringAfter(" ", missingDelimiterValue = "")
            .trim()
            .lowercase()

        if (strategyName.isBlank() || strategyName == "current") {
            println(
                "Текущая стратегия контекста: " +
                        contextBuilderProvider.currentStrategy.toDisplayName()
            )
            return
        }

        val strategy = when (strategyName) {
            "full" -> ContextStrategyType.FULL_HISTORY
            "sliding" -> ContextStrategyType.SLIDING_WINDOW
            "facts" -> ContextStrategyType.STICKY_FACTS
            else -> null
        }

        if (strategy == null) {
            println("Неизвестная стратегия: $strategyName")
            println("Доступные стратегии: full, sliding, facts")
            return
        }

        contextBuilderProvider.switchStrategy(strategy)

        println(
            "Стратегия контекста переключена на: " +
                    strategy.toDisplayName()
        )
    }

    private fun handleBranchCommand(input: String) {
        val parts = input
            .split(" ")
            .filter { part -> part.isNotBlank() }

        val action = parts.getOrNull(1)?.lowercase()

        when (action) {
            null, "current" -> {
                println("Текущая ветка: ${branchManager.currentBranch.name}")
            }

            "list" -> {
                val branches = branchManager.listBranches()

                println("Ветки:")
                branches.forEach { branch ->
                    val marker = if (branch.id == branchManager.currentBranch.id) {
                        "*"
                    } else {
                        " "
                    }

                    println("$marker ${branch.name}")
                }
            }

            "create" -> {
                val branchName = parts.getOrNull(2)

                if (branchName.isNullOrBlank()) {
                    println("Укажите имя ветки: $BRANCH_COMMAND create <name>")
                    return
                }

                try {
                    val branch = branchManager.createBranch(branchName)
                    println("Создана ветка: ${branch.name}")
                    println("Она содержит checkpoint текущей истории.")
                } catch (e: IllegalArgumentException) {
                    println(e.message)
                }
            }

            "switch" -> {
                val branchName = parts.getOrNull(2)

                if (branchName.isNullOrBlank()) {
                    println("Укажите имя ветки: $BRANCH_COMMAND switch <name>")
                    return
                }

                try {
                    val branch = branchManager.switchBranch(branchName)
                    println("Переключились на ветку: ${branch.name}")
                } catch (e: IllegalArgumentException) {
                    println(e.message)
                }
            }

            else -> {
                println("Неизвестная команда ветки: $action")
                println("Доступно: current, list, create <name>, switch <name>")
            }
        }
    }

    private fun CoroutineScope.launchThinkingIndicator(): Job {
        return launch {
            var dotCount = 1

            while (true) {
                val dots = ".".repeat(dotCount)
                print("\rАгент думает$dots   ")
                System.out.flush()

                dotCount = if (dotCount == 3) {
                    1
                } else {
                    dotCount + 1
                }

                delay(400)
            }
        }
    }

    private fun clearThinkingLine() {
        print("\r${" ".repeat(40)}\r")
        System.out.flush()
    }

    private fun ChatRole.toDisplayName(): String {
        return when (this) {
            ChatRole.SYSTEM -> "System"
            ChatRole.USER -> "User"
            ChatRole.ASSISTANT -> "Assistant"
        }
    }

    private fun ContextStrategyType.toDisplayName(): String {
        return when (this) {
            ContextStrategyType.FULL_HISTORY -> "Full History"
            ContextStrategyType.SLIDING_WINDOW -> "Sliding Window"
            ContextStrategyType.STICKY_FACTS -> "Sticky Facts"
        }
    }

    private companion object {
        const val HISTORY_COMMAND = "/history"
        const val CLEAR_COMMAND = "/clear"
        const val STRESS_CONTEXT_COMMAND = "/stress-context"
        const val STRATEGY_COMMAND = "/strategy"
        const val BRANCH_COMMAND = "/branch"
        const val EXIT_COMMAND = "/exit"
    }
}