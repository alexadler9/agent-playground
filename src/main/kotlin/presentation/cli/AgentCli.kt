package presentation.cli

import domain.agent.AgentService
import domain.model.ChatRole
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class AgentCli(
    private val agentService: AgentService,
    private val consoleInput: ConsoleInput = ConsoleInput(),
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

            reply.tokenUsage?.totalTokens?.let { totalTokens ->
                println()
                println("Использовано токенов: $totalTokens")
            }

            reply.responseTimeMs?.let { responseTimeMs ->
                println("Время ответа: $responseTimeMs ms")
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
        println("$EXIT_COMMAND    — выйти")
        println()
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

    private companion object {
        const val HISTORY_COMMAND = "/history"
        const val CLEAR_COMMAND = "/clear"
        const val EXIT_COMMAND = "/exit"
    }
}