package presentation.statefulagent

import domain.statefulagent.StatefulAgentService
import domain.statefulagent.memory.UserProfileRepository
import domain.statefulagent.model.OrchestrationEvent
import domain.statefulagent.model.TaskStage
import kotlinx.coroutines.runBlocking
import presentation.common.ConsoleInput
import presentation.common.clearThinkingLine
import presentation.common.launchThinkingIndicator

class StatefulAgentCli(
    private val agentService: StatefulAgentService,
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

                input.startsWith(PROFILE_COMMAND) -> {
                    handleProfileCommand(input)
                }

                input.startsWith(CLEAR_COMMAND) -> {
                    handleClearCommand(input)
                }

                else -> {
                    val thinkingJob = launchThinkingIndicator()

                    try {
                        var firstEventReceived = false
                        var hasRenderedEvent = false

                        agentService.run(
                            text = input,
                            onEvent = { event ->
                                if (!firstEventReceived) {
                                    thinkingJob.cancel()
                                    clearThinkingLine()
                                    firstEventReceived = true
                                }

                                renderEvent(
                                    event = event,
                                    printTopSpacing = hasRenderedEvent,
                                )

                                hasRenderedEvent = true
                            },
                        )

                        if (!firstEventReceived) {
                            thinkingJob.cancel()
                            clearThinkingLine()
                        }
                    } catch (e: Exception) {
                        thinkingJob.cancel()
                        clearThinkingLine()
                        println("Ошибка: ${e.message}")
                    }

                    println()
                }
            }
        }
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

    private suspend fun handleClearCommand(input: String) {
        val parts = input
            .split(" ")
            .filter { part -> part.isNotBlank() }

        val action = parts.getOrNull(1)?.lowercase() ?: "all"

        when (action) {
            "short" -> {
                agentService.clearShortTermMemory()
                println("Краткосрочная память очищена")
            }

            "work" -> {
                agentService.clearWorkingMemory()
                println("Рабочая память очищена")
            }

            "task" -> {
                agentService.resetTaskState()
                println("Состояние задачи и артефакты очищены")
            }

            "all" -> {
                agentService.clearShortTermMemory()
                agentService.clearWorkingMemory()
                agentService.resetTaskState()
                println("Краткосрочная память, рабочая память, состояние задачи и артефакты очищены")
            }

            else -> {
                println("Неизвестная команда очистки: $action")
                println("Доступно: short, work, task, all")
            }
        }

        println()
    }

    private fun printHeader() {
        println("Stateful Agent")
        println("Команды:")
        println("$PROFILE_COMMAND [current|list|show|switch <name>] — управление профилями")
        println("$CLEAR_COMMAND [short|work|task|all] — очистка памяти/состояния")
        println("$EXIT_COMMAND — выйти")
        println()
    }

    private fun renderEvent(
        event: OrchestrationEvent,
        printTopSpacing: Boolean,
    ) {
        when (event) {
            is OrchestrationEvent.StageStarted -> {
                if (printTopSpacing) {
                    println()
                }

                println("${ConsoleColor.GRAY}Этап: ${event.stage}${ConsoleColor.RESET}")
                println()
            }

            is OrchestrationEvent.StageFinished -> {
                println("${stageColor(event.stage)}${stageName(event.stage)}:${ConsoleColor.RESET}")
                println(event.answer.trim())
                println()
                println(
                    "${ConsoleColor.GRAY}Состояние: ${event.nextState.stage} | " +
                            "${event.nextState.currentStep} | " +
                            "${event.nextState.expectedAction}" +
                            ConsoleColor.RESET
                )
            }

            is OrchestrationEvent.AutoStepLimitReached -> {
                println("${ConsoleColor.RED}Оркестратор: достигнут лимит автоматических шагов.${ConsoleColor.RESET}")
            }

            is OrchestrationEvent.Finished -> Unit
        }
    }

    private companion object {
        const val USER_INPUT_PREFIX = "Вы: "
        const val PROFILE_COMMAND = "/profile"
        const val CLEAR_COMMAND = "/clear"
        const val EXIT_COMMAND = "/exit"
    }
}

private object ConsoleColor {
    const val RESET = "\u001B[0m"
    const val GRAY = "\u001B[90m"
    const val CYAN = "\u001B[36m"
    const val YELLOW = "\u001B[33m"
    const val GREEN = "\u001B[32m"
    const val RED = "\u001B[31m"
}

private fun stageName(stage: TaskStage): String {
    return when (stage) {
        TaskStage.PLANNING -> "PlanningAgent"
        TaskStage.EXECUTION -> "ExecutionAgent"
        TaskStage.VALIDATION -> "ValidationAgent"
        TaskStage.DONE -> "Done"
    }
}

private fun stageColor(stage: TaskStage): String {
    return when (stage) {
        TaskStage.PLANNING -> ConsoleColor.YELLOW
        TaskStage.EXECUTION -> ConsoleColor.CYAN
        TaskStage.VALIDATION -> ConsoleColor.GREEN
        TaskStage.DONE -> ConsoleColor.GREEN
    }
}