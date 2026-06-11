package domain.memory

import domain.llm.LlmGateway
import domain.model.AgentConfig
import domain.model.ChatMessage
import domain.model.ChatRole

class LlmHistorySummarizer(
    private val llmGateway: LlmGateway,
    private val config: AgentConfig,
) : HistorySummarizer {

    override suspend fun summarize(
        previousSummary: String,
        messagesToSummarize: List<ChatMessage>,
    ): String {
        val messages = buildList {
            add(
                ChatMessage(
                    role = ChatRole.SYSTEM,
                    content = SUMMARY_SYSTEM_PROMPT,
                )
            )

            add(
                ChatMessage(
                    role = ChatRole.USER,
                    content = buildUserPrompt(
                        previousSummary = previousSummary,
                        messagesToSummarize = messagesToSummarize,
                    ),
                )
            )
        }

        val reply = llmGateway.sendMessages(
            config = config.copy(
                maxTokens = SUMMARY_MAX_TOKENS,
                temperature = SUMMARY_TEMPERATURE,
            ),
            messages = messages,
        )

        return reply.message.content.trim()
    }

    private fun buildUserPrompt(
        previousSummary: String,
        messagesToSummarize: List<ChatMessage>,
    ): String {
        val formattedMessages = messagesToSummarize.joinToString(
            separator = "\n\n",
        ) { message ->
            "${message.role}: ${message.content}"
        }

        return """
            Previous summary:
            ${previousSummary.ifBlank { "No previous summary." }}

            New messages to merge into the summary:
            $formattedMessages

            Update the summary so it preserves:
            - important user preferences
            - project decisions
            - implementation details
            - unresolved problems
            - facts that may be needed later

            Keep it compact, structured, and useful for continuing the conversation.
            Do not add information that is not present in the messages.
        """.trimIndent()
    }

    private companion object {
        const val SUMMARY_MAX_TOKENS = 700
        const val SUMMARY_TEMPERATURE = 0.1

        const val SUMMARY_SYSTEM_PROMPT =
            "You are an internal summarization component for an AI agent. " +
                    "Your task is to compress conversation history into a concise summary. " +
                    "Return only the updated summary, without greetings or extra commentary"
    }
}