package domain.context

import domain.model.AgentConfig
import domain.model.ChatMessage
import domain.model.ChatRole
import domain.model.SummaryState

class SummaryContextBuilder : ContextBuilder {

    override fun buildContext(
        config: AgentConfig,
        history: List<ChatMessage>,
        summary: SummaryState,
    ): List<ChatMessage> {
        val systemMessage = ChatMessage(
            role = ChatRole.SYSTEM,
            content = config.systemPrompt,
        )

        val summaryMessage = if (summary.hasSummary) {
            ChatMessage(
                role = ChatRole.SYSTEM,
                content = buildSummaryMessage(summary),
            )
        } else {
            null
        }

        val rawUnsummarizedMessages = history.drop(summary.summarizedMessageCount)

        return buildList {
            add(systemMessage)

            if (summaryMessage != null) {
                add(summaryMessage)
            }

            addAll(rawUnsummarizedMessages)
        }
    }

    private fun buildSummaryMessage(summary: SummaryState): String {
        return """
            Summary of earlier conversation:
            ${summary.content}
            
            Use this summary as compressed context for older messages.
            The raw messages below are newer and more precise.
        """.trimIndent()
    }
}