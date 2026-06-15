package presentation.common

import java.io.BufferedReader
import java.io.InputStreamReader

class ConsoleInput {

    private val reader = BufferedReader(InputStreamReader(System.`in`))

    fun readLine(prompt: String): String? {
        print(prompt)
        System.out.flush()
        return reader.readLine()?.trim()
    }
}