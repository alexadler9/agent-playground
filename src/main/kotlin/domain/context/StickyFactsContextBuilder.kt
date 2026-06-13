package domain.context

import domain.model.AgentConfig
import domain.model.ChatMessage
import domain.model.ChatRole
import domain.model.FactMemory
import domain.model.SummaryState

class StickyFactsContextBuilder(
    private val recentMessagesCount: Int,
) : ContextBuilder {

    override fun buildContext(
        config: AgentConfig,
        history: List<ChatMessage>,
        summary: SummaryState,
        facts: FactMemory,
    ): List<ChatMessage> {
        val systemMessage = ChatMessage(
            role = ChatRole.SYSTEM,
            content = config.systemPrompt,
        )

        val factsMessage = if (!facts.isEmpty) {
            ChatMessage(
                role = ChatRole.SYSTEM,
                content = buildFactsMessage(facts),
            )
        } else {
            null
        }

        val recentMessages = history.takeLast(recentMessagesCount)

        return buildList {
            add(systemMessage)

            if (factsMessage != null) {
                add(factsMessage)
            }

            addAll(recentMessages)
        }
    }

    private fun buildFactsMessage(facts: FactMemory): String {
        val factsText = facts.facts.entries.joinToString(
            separator = "\n",
        ) { (key, value) ->
            "- $key: $value"
        }

        return """
            Sticky facts memory:
            $factsText
            
            Use these facts as stable memory extracted from earlier user messages.
            Recent messages below may contain newer details and should have priority if there is a conflict.
        """.trimIndent()
    }
}