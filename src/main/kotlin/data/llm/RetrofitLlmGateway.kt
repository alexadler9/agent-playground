package data.llm

import data.llm.api.ChatCompletionApi
import data.llm.dto.ChatCompletionRequestDto
import data.llm.dto.ChatCompletionResponseDto
import data.llm.dto.OpenRouterPluginDto
import domain.llm.LlmGateway
import domain.model.AgentConfig
import domain.model.AgentReply
import domain.model.ChatMessage
import domain.model.ChatRole
import domain.model.TokenUsage
import kotlin.system.measureTimeMillis

enum class OpenRouterContextCompressionMode {
    DISABLED,
    ENABLED,
    NOT_CONFIGURED,
}

class RetrofitLlmGateway(
    private val api: ChatCompletionApi,
    private val apiKey: String,
    private val openRouterContextCompressionMode: OpenRouterContextCompressionMode =
        OpenRouterContextCompressionMode.DISABLED,
) : LlmGateway {

    override suspend fun sendMessages(
        config: AgentConfig,
        messages: List<ChatMessage>,
    ): AgentReply {
        var responseBody: ChatCompletionResponseDto? = null

        val responseTimeMs = measureTimeMillis {
            val httpResponse = api.createChatCompletion(
                authorization = "Bearer ${apiKey.trim()}",
                request = ChatCompletionRequestDto(
                    model = config.model,
                    messages = messages.map { it.toDto() },
                    maxTokens = config.maxTokens,
                    temperature = config.temperature,
                    transforms =  when (openRouterContextCompressionMode) {
                        OpenRouterContextCompressionMode.DISABLED -> emptyList()
                        OpenRouterContextCompressionMode.ENABLED -> null
                        OpenRouterContextCompressionMode.NOT_CONFIGURED -> null
                    },
                    plugins = when (openRouterContextCompressionMode) {
                        OpenRouterContextCompressionMode.DISABLED -> {
                            listOf(
                                OpenRouterPluginDto(
                                    id = "context-compression",
                                    enabled = false,
                                )
                            )
                        }

                        OpenRouterContextCompressionMode.ENABLED -> {
                            listOf(
                                OpenRouterPluginDto(
                                    id = "context-compression",
                                    enabled = true,
                                )
                            )
                        }

                        OpenRouterContextCompressionMode.NOT_CONFIGURED -> null
                    },
                ),
            )

            if (!httpResponse.isSuccessful) {
                val errorBody = httpResponse.errorBody()?.string()
                error("HTTP ${httpResponse.code()}: ${errorBody.orEmpty()}")
            }

            responseBody = httpResponse.body()
                ?: error("Empty response body")
        }

        val response = responseBody
            ?: error("Empty response body")

        val assistantContent = response.choices
            .firstOrNull()
            ?.message
            ?.content
            ?: error("Response does not contain assistant message")

        return AgentReply(
            message = ChatMessage(
                role = ChatRole.ASSISTANT,
                content = assistantContent,
            ),
            tokenUsage = response.usage?.let { usage ->
                TokenUsage(
                    promptTokens = usage.promptTokens,
                    completionTokens = usage.completionTokens,
                    totalTokens = usage.totalTokens,
                )
            },
            responseTimeMs = responseTimeMs,
        )
    }
}