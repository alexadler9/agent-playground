package domain.memory

import domain.llm.LlmGateway
import domain.model.AgentConfig
import domain.model.ChatMessage
import domain.model.ChatRole
import domain.model.FactMemory
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject

class LlmFactMemoryUpdater(
    private val llmGateway: LlmGateway,
    private val config: AgentConfig,
    private val json: Json,
) : FactMemoryUpdater {

    override suspend fun updateFacts(
        currentFacts: FactMemory,
        userMessage: String,
    ): FactMemory {
        val messages = listOf(
            ChatMessage(
                role = ChatRole.SYSTEM,
                content = FACTS_SYSTEM_PROMPT,
            ),
            ChatMessage(
                role = ChatRole.USER,
                content = buildUserPrompt(
                    currentFacts = currentFacts,
                    userMessage = userMessage,
                ),
            ),
        )

        val reply = llmGateway.sendMessages(
            config = config.copy(
                maxTokens = 700,
                temperature = 0.1,
            ),
            messages = messages,
        )

        return parseFacts(reply.message.content)
    }

    private fun buildUserPrompt(
        currentFacts: FactMemory,
        userMessage: String,
    ): String {
        val currentFactsText = if (currentFacts.facts.isEmpty()) {
            "{}"
        } else {
            json.encodeToString(currentFacts.facts)
        }

        return """
            Current facts:
            $currentFactsText
        
            New user message:
            $userMessage
        
            Update facts.
            Allowed keys only: project_goal, requirements, constraints, decisions, user_preferences, test_facts.
            Use test_facts for special values used to verify memory behavior.
            Use constraints for limits and parameters.
            Use decisions for chosen implementation/architecture choices.
            Merge related info into existing keys.
            If nothing important changed, return current facts unchanged.
            Return only JSON object.
        """.trimIndent()
    }

    private fun parseFacts(rawText: String): FactMemory {
        val cleanedText = rawText
            .trim()
            .removePrefix("```json")
            .removePrefix("```")
            .removeSuffix("```")
            .trim()

        val jsonObject = json.parseToJsonElement(cleanedText).jsonObject

        val facts = jsonObject.mapValues { (_, value) ->
            when (value) {
                is JsonPrimitive -> value.content
                else -> value.toString()
            }
        }

        val filteredFacts = facts
            .filterKeys { key -> key in ALLOWED_KEYS }
            .filterValues { value ->
                value.isNotBlank() && value != "[]" && value != "{}"
            }
            .entries
            .take(MAX_FACTS)
            .associate { (key, value) -> key to value }

        return FactMemory(
            facts = filteredFacts,
        )
    }

    private companion object {
        const val FACTS_SYSTEM_PROMPT =
            "Update compact key-value memory. Return only valid JSON with these keys if useful: " +
                    "project_goal, requirements, constraints, decisions, user_preferences, test_facts. " +
                    "Store only stable information needed later. " +
                    "Do not store filler, examples, temporary checks, observations, or generic notes."

        val ALLOWED_KEYS = setOf(
            "project_goal",
            "requirements",
            "constraints",
            "decisions",
            "user_preferences",
            "test_facts"
        )

        const val MAX_FACTS = 6
    }
}