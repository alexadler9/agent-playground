package data.local

import domain.llm.LlmGateway
import domain.model.AgentConfig
import domain.model.AgentReply
import domain.model.ChatMessage
import domain.model.ChatRole
import domain.model.TokenUsage
import kotlin.system.measureTimeMillis

/**
 * LlmGateway поверх локальной Ollama chat model.
 *
 * RAG-агент работает с общим интерфейсом LlmGateway, поэтому один и тот же
 * pipeline можно запускать с облачной моделью или с локальной моделью.
 */
class OllamaChatLlmGateway(
    private val api: OllamaChatApi,
    private val defaultModel: String,
) : LlmGateway {

    override suspend fun sendMessages(
        config: AgentConfig,
        messages: List<ChatMessage>,
    ): AgentReply {
        var responseBody: OllamaChatResponse? = null

        val responseTimeMs = measureTimeMillis {
            responseBody = api.chat(
                request = OllamaChatRequest(
                    model = config.model.ifBlank { defaultModel },
                    messages = messages.map { message ->
                        OllamaChatMessageDto(
                            role = message.role.toOllamaRole(),
                            content = message.content,
                        )
                    },
                    stream = false,
                    options = OllamaChatOptions(
                        temperature = config.temperature,
                        maxTokens = config.maxTokens,
                    ),
                ),
            )
        }

        val response = responseBody ?: error("Empty Ollama response body")

        return AgentReply(
            message = ChatMessage(
                role = ChatRole.ASSISTANT,
                content = response.message.content,
            ),
            tokenUsage = TokenUsage(
                promptTokens = response.promptEvalCount,
                completionTokens = response.evalCount,
                totalTokens = response.promptEvalCount?.let { promptTokens ->
                    response.evalCount?.let { evalTokens ->
                        promptTokens + evalTokens
                    }
                },
            ),
            responseTimeMs = responseTimeMs,
        )
    }

    private fun ChatRole.toOllamaRole(): String {
        return when (this) {
            ChatRole.SYSTEM -> "system"
            ChatRole.USER -> "user"
            ChatRole.ASSISTANT -> "assistant"
        }
    }
}
