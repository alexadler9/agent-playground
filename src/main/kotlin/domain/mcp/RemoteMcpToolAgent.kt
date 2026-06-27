package domain.mcp

import domain.llm.LlmGateway
import domain.model.AgentConfig
import domain.model.ChatMessage
import domain.model.ChatRole
import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO
import io.ktor.client.plugins.sse.SSE
import io.modelcontextprotocol.kotlin.sdk.client.Client
import io.modelcontextprotocol.kotlin.sdk.client.StreamableHttpClientTransport
import io.modelcontextprotocol.kotlin.sdk.types.CallToolResult
import io.modelcontextprotocol.kotlin.sdk.types.Implementation
import io.modelcontextprotocol.kotlin.sdk.types.TextContent
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/**
 * Обычный агент с доступом к MCP tools.
 *
 * Агент не знает заранее конкретный пайплайн.
 * На каждом шаге он решает: ответить напрямую или вызвать один MCP tool.
 */
class RemoteMcpToolAgent(
    private val llmGateway: LlmGateway,
    private val agentConfig: AgentConfig,
    private val json: Json,
) {

    suspend fun handleUserRequest(
        mcpUrl: String,
        userRequest: String,
        onStep: (String) -> Unit = {},
    ): McpToolAgentResult {
        require(mcpUrl.isNotBlank()) { "MCP URL must not be blank" }
        require(userRequest.isNotBlank()) { "User request must not be blank" }

        val httpClient = HttpClient(CIO) {
            install(SSE)
        }

        val client = Client(
            clientInfo = Implementation(
                name = "agent-playground-mcp-tool-agent",
                version = "1.0.0",
            ),
        )

        val transport = StreamableHttpClientTransport(
            client = httpClient,
            url = mcpUrl,
        )

        return try {
            client.connect(transport)

            val tools = client.listTools().tools
            val toolNames = tools.map { tool -> tool.name }
            val availableToolsText = tools.joinToString(separator = "\n\n") { tool ->
                """
                Tool: ${tool.name}
                Description: ${tool.description ?: "No description"}
                Input schema: ${tool.inputSchema}
                """.trimIndent()
            }

            val toolHistory = mutableListOf<ToolHistoryItem>()
            val maxSteps = 6

            repeat(maxSteps) { stepIndex ->
                onStep("Step ${stepIndex + 1}: choosing next action")

                val action = askNextAction(
                    userRequest = userRequest,
                    availableToolsText = availableToolsText,
                    toolHistory = toolHistory,
                )

                when (action) {
                    is AgentAction.FinalAnswer -> {
                        onStep("Agent returned final answer")

                        return McpToolAgentResult(
                            userRequest = userRequest,
                            availableTools = toolNames,
                            toolHistory = toolHistory,
                            finalAnswer = action.answer,
                        )
                    }

                    is AgentAction.ToolCall -> {
                        require(action.toolName in toolNames) {
                            "Agent selected unavailable MCP tool: ${action.toolName}. Available tools: ${toolNames.joinToString()}"
                        }

                        onStep("Calling MCP tool: ${action.toolName}")

                        val arguments = action.arguments
                            .mapValues { (_, value) -> value.toPlainValue(toolHistory) }

                        val toolResult = client.callTool(
                            name = action.toolName,
                            arguments = arguments,
                        )

                        val toolText = toolResult.asText()

                        if (toolResult.isError == true) {
                            error("${action.toolName} returned an error: $toolText")
                        }

                        val saveAs = action.saveAs
                            ?: action.toolName
                                .replace("_", "")
                                .ifBlank { "step${stepIndex + 1}" }

                        toolHistory += ToolHistoryItem(
                            toolName = action.toolName,
                            saveAs = saveAs,
                            resultText = toolText,
                        )

                        onStep("Tool result saved as: $saveAs")
                        onStep("")
                    }
                }
            }

            val fallbackAnswer = buildString {
                appendLine("Agent reached the maximum number of tool steps.")
                appendLine()
                appendLine("Last tool result:")
                appendLine(toolHistory.lastOrNull()?.resultText.orEmpty())
            }

            McpToolAgentResult(
                userRequest = userRequest,
                availableTools = toolNames,
                toolHistory = toolHistory,
                finalAnswer = fallbackAnswer,
            )
        } finally {
            runCatching { client.close() }
            httpClient.close()
        }
    }

    private suspend fun askNextAction(
        userRequest: String,
        availableToolsText: String,
        toolHistory: List<ToolHistoryItem>,
    ): AgentAction {
        val reply = llmGateway.sendMessages(
            config = agentConfig,
            messages = listOf(
                ChatMessage(
                    role = ChatRole.SYSTEM,
                    content = buildSystemPrompt(availableToolsText),
                ),
                ChatMessage(
                    role = ChatRole.USER,
                    content = buildDecisionContext(
                        userRequest = userRequest,
                        toolHistory = toolHistory,
                    ),
                ),
            ),
        )

        return parseAction(reply.message.content)
    }

    private fun buildSystemPrompt(availableToolsText: String): String {
        return """
            You are a general-purpose assistant with access to MCP tools.

            You are not required to use tools.
            If the user's request can be answered directly, return final_answer.
            If the request needs action that an available MCP tool can perform, call exactly one MCP tool.

            Available MCP tools:
            $availableToolsText

            On each step, return exactly one valid JSON object.
            Do not use Markdown.
            Do not add explanations outside JSON.

            Option 1 — answer the user:
            {
              "type": "final_answer",
              "answer": "answer text"
            }

            Option 2 — call one MCP tool:
            {
              "type": "tool_call",
              "toolName": "tool_name",
              "arguments": {
                "arg1": "value"
              },
              "saveAs": "short_result_name"
            }

            Rules:
            - Use only tools from the available MCP tools list.
            - Do not call tools if they are not needed.
            - Choose tools based on their names, descriptions, and schemas.
            - To pass a previous tool result into another tool, use a placeholder: ${'$'}{resultName.text}.
            - To refer to the most recent tool result, use: ${'$'}{last.text}.
            - Use short stable saveAs names, for example: commits, summary, savedNote.
        """.trimIndent()
    }

    private fun buildDecisionContext(
        userRequest: String,
        toolHistory: List<ToolHistoryItem>,
    ): String {
        return buildString {
            appendLine("User request:")
            appendLine(userRequest)

            if (toolHistory.isNotEmpty()) {
                appendLine()
                appendLine("Already executed MCP tool calls:")

                toolHistory.forEachIndexed { index, item ->
                    appendLine()
                    appendLine("${index + 1}. ${item.toolName}, saved as ${item.saveAs}")
                    appendLine("Result:")
                    appendLine(item.resultText.take(maxToolResultChars))
                }
            }

            appendLine()
            appendLine("Choose the next action")
        }
    }

    private fun parseAction(rawContent: String): AgentAction {
        val rawJson = rawContent.extractJsonObject()
        val root = json.parseToJsonElement(rawJson).jsonObject

        return when (val type = root["type"]?.jsonPrimitive?.content) {
            "final_answer" -> {
                AgentAction.FinalAnswer(
                    answer = root["answer"]
                        ?.jsonPrimitive
                        ?.content
                        ?: error("final_answer is missing answer"),
                )
            }

            "tool_call" -> {
                AgentAction.ToolCall(
                    toolName = root["toolName"]
                        ?.jsonPrimitive
                        ?.content
                        ?: error("tool_call is missing toolName"),
                    arguments = root["arguments"]
                        ?.jsonObject
                        ?: JsonObject(emptyMap()),
                    saveAs = root["saveAs"]
                        ?.jsonPrimitive
                        ?.content,
                )
            }

            else -> error("Unknown agent action type: $type. LLM response: $rawJson")
        }
    }

    private companion object {
        const val maxToolResultChars = 8_000
    }
}

data class McpToolAgentResult(
    val userRequest: String,
    val availableTools: List<String>,
    val toolHistory: List<ToolHistoryItem>,
    val finalAnswer: String,
)

data class ToolHistoryItem(
    val toolName: String,
    val saveAs: String,
    val resultText: String,
)

private sealed interface AgentAction {

    data class FinalAnswer(
        val answer: String,
    ) : AgentAction

    data class ToolCall(
        val toolName: String,
        val arguments: JsonObject,
        val saveAs: String?,
    ) : AgentAction
}

private val placeholderRegex = Regex("""\$\{([A-Za-z0-9_-]+)\.text}""")

private fun JsonElement.toPlainValue(
    toolHistory: List<ToolHistoryItem>,
): Any? {
    return when (this) {
        JsonNull -> null

        is JsonPrimitive -> {
            if (isString) {
                content.resolvePlaceholders(toolHistory)
            } else {
                intOrNull ?: doubleOrNull ?: booleanOrNull ?: content
            }
        }

        is JsonObject -> {
            entries.associate { (key, value) ->
                key to value.toPlainValue(toolHistory)
            }
        }

        is JsonArray -> {
            map { element -> element.toPlainValue(toolHistory) }
        }
    }
}

private fun String.resolvePlaceholders(
    toolHistory: List<ToolHistoryItem>,
): String {
    return placeholderRegex.replace(this) { match ->
        val resultName = match.groupValues[1]

        if (resultName == "last") {
            return@replace toolHistory.lastOrNull()?.resultText
                ?: error("Placeholder ${match.value} was used before any tool call")
        }

        toolHistory.lastOrNull { item -> item.saveAs == resultName }?.resultText
            ?: error("No tool result found for placeholder: ${match.value}")
    }
}

private fun String.extractJsonObject(): String {
    val trimmed = trim()

    if (trimmed.startsWith("```")) {
        return trimmed
            .removePrefix("```json")
            .removePrefix("```")
            .removeSuffix("```")
            .trim()
    }

    val start = indexOf('{')
    val end = lastIndexOf('}')

    require(start >= 0 && end > start) {
        "LLM response does not contain a JSON object: $this"
    }

    return substring(start, end + 1)
}

private fun CallToolResult.asText(): String {
    return content.joinToString(separator = "\n\n") { content ->
        when (content) {
            is TextContent -> content.text
            else -> content.toString()
        }
    }
}
