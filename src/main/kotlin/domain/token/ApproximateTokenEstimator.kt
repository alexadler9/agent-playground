package domain.token

import domain.model.ChatMessage
import kotlin.math.ceil

class ApproximateTokenEstimator : TokenEstimator {

    override fun estimateTextTokens(text: String): Int {
        if (text.isBlank()) {
            return 0
        }

        return ceil(text.length / APPROX_CHARS_PER_TOKEN).toInt()
            .coerceAtLeast(1)
    }

    override fun estimateMessagesTokens(messages: List<ChatMessage>): Int {
        return messages.sumOf { message ->
            estimateTextTokens(message.content) + MESSAGE_OVERHEAD_TOKENS
        }
    }

    private companion object {

        const val APPROX_CHARS_PER_TOKEN = 3.0
        const val MESSAGE_OVERHEAD_TOKENS = 4
    }
}