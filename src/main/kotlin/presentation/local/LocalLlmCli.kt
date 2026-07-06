package presentation.local

import data.local.OllamaLocalLlmClient
import presentation.cli.CliConsole

/**
 * CLI-режим для проверки локальной LLM.
 */
class LocalLlmCli(
    private val client: OllamaLocalLlmClient,
    private val model: String,
    private val console: CliConsole = CliConsole(),
) {

    suspend fun askOnce(
        prompt: String,
    ) {
        console.println("Локальная LLM")
        console.println("Модель: $model")
        console.println()

        console.println("Запрос:")
        console.println(prompt)
        console.println()

        val response = client.ask(
            prompt = prompt,
            system = "Отвечай на русском языке. Отвечай ясно и по делу",
        )

        printResponse(response.response)
        printStats(response)
    }

    suspend fun runDemo() {
        console.println("Демо локальной LLM")
        console.println("Модель: $model")
        console.println()

        val prompts = listOf(
            "Ответь одним абзацем: что такое локальная LLM?",
            "Объясни простыми словами, чем локальная LLM отличается от облачной. Дай 3 пункта",
            "Предложи архитектуру Android-приложения, которое использует локальную LLM для суммаризации заметок без интернета. Укажи основные компоненты и ограничения",
        )

        prompts.forEachIndexed { index, prompt ->
            console.println("========================================")
            console.println("Запрос ${index + 1}/${prompts.size}")
            console.println("========================================")
            console.println(prompt)
            console.println()

            val response = client.ask(
                prompt = prompt,
                system = "Отвечай на русском языке. Объясняй понятно для разработчика",
            )

            printResponse(response.response)
            printStats(response)
            console.println()
        }
    }

    private fun printResponse(
        text: String,
    ) {
        console.println("Ответ:")
        console.println(text.ifBlank { "<пустой ответ>" })
        console.println()
    }

    private fun printStats(
        response: data.local.OllamaGenerateResponse,
    ) {
        console.println("Статистика:")
        console.println("done=${response.done}, doneReason=${response.doneReason ?: "-"}")
        console.println("promptTokens=${response.promptEvalCount ?: "-"}, outputTokens=${response.evalCount ?: "-"}")
        console.println()
    }
}