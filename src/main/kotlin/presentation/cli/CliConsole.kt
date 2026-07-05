package presentation.cli

import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.io.PrintWriter
import java.nio.charset.StandardCharsets

/**
 * Единая обертка над консольным вводом/выводом.
 *
 * Нужна для Windows/Gradle CLI, где дефолтная кодировка
 * может ломать русский ввод и вывод.
 */
class CliConsole {

    private val input = BufferedReader(
        InputStreamReader(System.`in`, StandardCharsets.UTF_8),
    )

    private val output = PrintWriter(
        OutputStreamWriter(System.out, StandardCharsets.UTF_8),
        true,
    )

    fun print(text: String) {
        output.print(text)
        output.flush()
    }

    fun println(text: String = "") {
        output.println(text)
        output.flush()
    }

    fun readLine(): String? {
        return input.readLine()
    }
}