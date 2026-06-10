package domain.token

import domain.model.ChatMessage

interface TokenEstimator {

    fun estimateTextTokens(text: String): Int

    fun estimateMessagesTokens(messages: List<ChatMessage>): Int
}