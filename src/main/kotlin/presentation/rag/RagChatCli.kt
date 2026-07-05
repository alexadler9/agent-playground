package presentation.rag

import domain.rag.RagChatAgent
import domain.rag.RagChatSessionState
import domain.rag.RagGroundedAnswerResult
import domain.rag.RagIndex
import domain.rag.RagRetrievalSettings
import presentation.cli.CliConsole

/**
 * Интерактивный CLI для RAG-чата.
 *
 * Команды:
 * /exit  — выйти
 * /memory — показать текущую память задачи
 */
class RagChatCli(
    private val index: RagIndex,
    private val agent: RagChatAgent,
    private val settings: RagRetrievalSettings = RagRetrievalSettings(),
    private val console: CliConsole = CliConsole(),
) {

    suspend fun start() {
        var state = RagChatSessionState()

        console.println("RAG-чат запущен.")
        console.println("Команды: /exit, /memory")
        console.println()

        while (true) {
            console.print("Вы> ")

            val input = console.readLine()
                ?.trim()
                .orEmpty()

            if (input.isBlank()) {
                continue
            }

            when (input.lowercase()) {
                "/выход", "/exit" -> {
                    console.println("RAG-чат завершен")
                    return
                }

                "/память", "/memory" -> {
                    printMemory(state)
                    continue
                }
            }

            val result = agent.handleUserMessage(
                index = index,
                state = state,
                userMessage = input,
                settings = settings,
            )

            state = result.state

            printAnswer(result.answer)
        }
    }

    private fun printMemory(
        state: RagChatSessionState,
    ) {
        val memory = state.taskMemory

        console.println()
        console.println("Память задачи")
        console.println("Цель: ${memory.goal.ifBlank { "-" }}")

        console.println("Уточнения:")
        printList(memory.clarifications)

        console.println("Ограничения:")
        printList(memory.constraints)

        console.println("Термины:")
        printList(memory.terms)

        console.println()
    }

    private fun printAnswer(
        result: RagGroundedAnswerResult,
    ) {
        console.println()
        console.println("Ассистент:")
        console.println(result.answer)
        console.println()

        console.println("Источники:")
        if (result.sources.isEmpty()) {
            console.println("-")
        } else {
            result.sources.forEachIndexed { index, source ->
                console.println(
                    "${index + 1}. ${source.source} | ${source.section} | ${source.chunkId} | score=${"%.4f".format(source.score)}",
                )
            }
        }

        console.println()
        console.println("Цитаты:")
        if (result.quotes.isEmpty()) {
            console.println("-")
        } else {
            result.quotes.forEachIndexed { index, quote ->
                console.println("${index + 1}. ${quote.chunkId}: \"${quote.text}\"")
            }
        }

        console.println()
        console.println(
            "Проверка: известно=${result.isKnown}, валидно=${result.validation.isValid}, llmПропущена=${result.skippedLlm}",
        )

        if (result.validation.errors.isNotEmpty()) {
            console.println("Ошибки проверки:")
            result.validation.errors.forEach { error ->
                console.println("- $error")
            }
        }

        console.println()
    }

    private fun printList(items: List<String>) {
        if (items.isEmpty()) {
            console.println("-")
        } else {
            items.forEach { item ->
                console.println("- $item")
            }
        }
    }
}