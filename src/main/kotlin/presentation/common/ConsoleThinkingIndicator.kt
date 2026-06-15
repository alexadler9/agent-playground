package presentation.common

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

fun CoroutineScope.launchThinkingIndicator(): Job {
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

fun clearThinkingLine() {
    print("\r${" ".repeat(40)}\r")
    System.out.flush()
}