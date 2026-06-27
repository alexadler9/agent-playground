package domain.mcp

import domain.llm.LlmGateway
import domain.model.AgentConfig
import domain.model.ChatMessage
import domain.model.ChatRole
import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO
import io.ktor.client.plugins.sse.SSE
import io.modelcontextprotocol.kotlin.sdk.client.Client
import io.modelcontextprotocol.kotlin.sdk.client.StdioClientTransport
import io.modelcontextprotocol.kotlin.sdk.client.StreamableHttpClientTransport
import io.modelcontextprotocol.kotlin.sdk.types.CallToolResult
import io.modelcontextprotocol.kotlin.sdk.types.Implementation
import io.modelcontextprotocol.kotlin.sdk.types.TextContent
import kotlinx.io.asSink
import kotlinx.io.asSource
import kotlinx.io.buffered
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
 * Агент, который работает сразу с несколькими MCP-серверами.
 *
 * Он не знает заранее конкретный флоу.
 * На каждом шаге LLM выбирает:
 * - ответить пользователю;
 * - или вызвать tool на одном из зарегистрированных MCP-серверов.
 */
class RemoteMultiMcpToolAgent(
    private val llmGateway: LlmGateway,
    private val agentConfig: AgentConfig,
    private val json: Json,
) {

    suspend fun handleUserRequest(
        githubMcpUrl: String,
        workspaceDirectory: String,
        userRequest: String,
        onStep: (String) -> Unit = {},
    ): MultiMcpToolAgentResult {
        require(githubMcpUrl.isNotBlank()) { "githubMcpUrl must not be blank" }
        require(workspaceDirectory.isNotBlank()) { "workspaceDirectory must not be blank" }
        require(userRequest.isNotBlank()) { "userRequest must not be blank" }

        val githubHttpClient = HttpClient(CIO) {
            install(SSE)
        }

        val githubClient = Client(
            clientInfo = Implementation(
                name = "agent-playground-github-data-client",
                version = "1.0.0",
            ),
        )

        val githubTransport = StreamableHttpClientTransport(
            client = githubHttpClient,
            url = githubMcpUrl,
        )

        val filesystemProcess = ProcessBuilder(
            "cmd",
            "/c",
            "npx",
            "-y",
            "@modelcontextprotocol/server-filesystem",
            workspaceDirectory,
        )
            .redirectError(ProcessBuilder.Redirect.INHERIT)
            .start()

        val filesystemClient = Client(
            clientInfo = Implementation(
                name = "agent-playground-workspace-files-client",
                version = "1.0.0",
            ),
        )

        val filesystemTransport = StdioClientTransport(
            input = filesystemProcess.inputStream.asSource().buffered(),
            output = filesystemProcess.outputStream.asSink().buffered(),
        )

        return try {
            githubClient.connect(githubTransport)
            filesystemClient.connect(filesystemTransport)

            val servers = listOf(
                ConnectedMcpServer(
                    name = "github-data",
                    description = "Custom HTTP MCP server with GitHub and text tools.",
                    client = githubClient,
                    extraContext = "Use this server for GitHub repository data and text processing.",
                ),
                ConnectedMcpServer(
                    name = "workspace-files",
                    description = "Local filesystem MCP server limited to the configured workspace directory.",
                    client = filesystemClient,
                    extraContext = "Workspace directory: $workspaceDirectory. Use absolute paths inside this directory when reading or writing files.",
                ),
            )

            val allowedToolsByServer = mapOf(
                "github-data" to setOf(
                    "get_recent_commits",
                    "search_recent_commits",
                    "summarize_text",
                ),
                "workspace-files" to setOf(
                    "read_text_file",
                    "write_file",
                    "list_directory",
                    "list_allowed_directories",
                ),
            )

            val registry = servers.flatMap { server ->
                val allowedToolNames = allowedToolsByServer.getValue(server.name)

                server.client.listTools().tools
                    .filter { tool -> tool.name in allowedToolNames }
                    .map { tool ->
                        RegisteredMcpTool(
                            serverName = server.name,
                            serverDescription = server.description,
                            serverExtraContext = server.extraContext,
                            toolName = tool.name,
                            rawToolInfo = tool.toString(),
                        )
                    }
            }

            onStep("Registered MCP servers:")
            servers.forEach { server -> onStep("- ${server.name}") }
            onStep("")

            onStep("Discovered MCP tools:")
            registry.forEach { tool ->
                onStep("- ${tool.serverName}.${tool.toolName}")
            }
            onStep("")

            val toolHistory = mutableListOf<MultiToolHistoryItem>()
            val maxSteps = 10

            repeat(maxSteps) { stepIndex ->
                onStep("Step ${stepIndex + 1}: agent chooses next action")

                val action = askNextAction(
                    userRequest = userRequest,
                    registeredTools = registry,
                    toolHistory = toolHistory,
                )

                when (action) {
                    is MultiMcpAgentAction.FinalAnswer -> {
                        onStep("Agent returned final answer.")

                        return MultiMcpToolAgentResult(
                            userRequest = userRequest,
                            registeredTools = registry,
                            toolHistory = toolHistory,
                            finalAnswer = action.answer,
                        )
                    }

                    is MultiMcpAgentAction.ToolCall -> {
                        val selectedTool = registry.firstOrNull { tool ->
                            tool.serverName == action.serverName &&
                                    tool.toolName == action.toolName
                        } ?: error(
                            "Agent selected unavailable tool: ${action.serverName}.${action.toolName}",
                        )

                        val selectedServer = servers.first { server ->
                            server.name == selectedTool.serverName
                        }

                        onStep("Calling MCP tool: ${action.serverName}.${action.toolName}")

                        val arguments = action.arguments.mapValues { (_, value) ->
                            value.toPlainValue(toolHistory)
                        }

                        val toolResult = selectedServer.client.callTool(
                            name = action.toolName,
                            arguments = arguments,
                        )

                        val toolText = toolResult.asText()

                        if (toolResult.isError == true) {
                            error("${action.serverName}.${action.toolName} returned an error: $toolText")
                        }

                        val saveAs = action.saveAs
                            ?: "${action.serverName}_${action.toolName}_${stepIndex + 1}"

                        toolHistory += MultiToolHistoryItem(
                            serverName = action.serverName,
                            toolName = action.toolName,
                            saveAs = saveAs,
                            resultText = toolText,
                        )

                        onStep("Result saved as: $saveAs")
                        onStep("")
                    }
                }
            }

            MultiMcpToolAgentResult(
                userRequest = userRequest,
                registeredTools = registry,
                toolHistory = toolHistory,
                finalAnswer = buildString {
                    appendLine("The agent reached the maximum number of steps.")
                    appendLine()
                    appendLine("Last tool result:")
                    appendLine(toolHistory.lastOrNull()?.resultText.orEmpty())
                },
            )
        } finally {
            runCatching { githubClient.close() }
            githubHttpClient.close()

            runCatching { filesystemClient.close() }
            filesystemProcess.destroy()
        }
    }

    private suspend fun askNextAction(
        userRequest: String,
        registeredTools: List<RegisteredMcpTool>,
        toolHistory: List<MultiToolHistoryItem>,
    ): MultiMcpAgentAction {
        val reply = llmGateway.sendMessages(
            config = agentConfig,
            messages = listOf(
                ChatMessage(
                    role = ChatRole.SYSTEM,
                    content = buildSystemPrompt(registeredTools),
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

    private fun buildSystemPrompt(
        registeredTools: List<RegisteredMcpTool>,
    ): String {
        return """
            You are a general-purpose assistant with access to multiple MCP servers.

            You are not required to use tools.
            Use tools only when they are useful for the user's request.
            If the request can be answered directly, return final_answer.
            If the request needs action that an available MCP tool can perform, call one suitable MCP tool.

            Available MCP tools:
            ${registeredTools.joinToString(separator = "\n\n") { tool ->
                """
                    Server: ${tool.serverName}
                    Server description: ${tool.serverDescription}
                    Server context: ${tool.serverExtraContext}
                    Tool: ${tool.toolName}
                    Tool metadata: ${tool.rawToolInfo}
                """.trimIndent()
            }}

            At each step return exactly one valid JSON object.
            Do not use Markdown.
            Do not add explanations outside JSON.

            Option 1 — answer the user:
            {
              "type": "final_answer",
              "answer": "your answer"
            }

            Option 2 — call one MCP tool:
            {
              "type": "tool_call",
              "serverName": "server_name",
              "toolName": "tool_name",
              "arguments": {
                "arg1": "value"
              },
              "saveAs": "short_result_name"
            }

            Rules:
            - Use only servers and tools from the available MCP tools list.
            - Choose tools by server name, tool name, metadata, and input schema.
            - Do not call tools when a direct answer is enough.
            - Do not return final_answer while the requested task is incomplete.
            - If you are about to say that you need to do another action, call the appropriate MCP tool instead.
            - Call only one tool per step.
            - After a tool result is available, decide whether another tool is needed or whether you can answer.
            - To pass a previous tool result into another tool, use a placeholder like ${'$'}{resultName.text}.
            - To refer to the latest tool result, use ${'$'}{last.text}.
            - Keep saveAs names short and stable, for example: template, styleGuide, kotlinCommits, kotlinSummary, savedReport, verifiedReport.
            - For filesystem operations, use paths inside the configured workspace directory.
        """.trimIndent()
    }

    private fun buildDecisionContext(
        userRequest: String,
        toolHistory: List<MultiToolHistoryItem>,
    ): String {
        return buildString {
            appendLine("User request:")
            appendLine(userRequest)

            if (toolHistory.isNotEmpty()) {
                appendLine()
                appendLine("Already executed MCP tool calls:")

                toolHistory.forEachIndexed { index, item ->
                    appendLine()
                    appendLine("${index + 1}. ${item.serverName}.${item.toolName}, saved as ${item.saveAs}")
                    appendLine("Result:")
                    appendLine(item.resultText.take(maxToolResultChars))
                }
            }

            appendLine()
            appendLine("Choose the next action.")
        }
    }

    private fun parseAction(rawContent: String): MultiMcpAgentAction {
        val rawJson = rawContent.extractJsonObject()
        val root = json.parseToJsonElement(rawJson).jsonObject

        return when (val type = root["type"]?.jsonPrimitive?.content) {
            "final_answer" -> {
                MultiMcpAgentAction.FinalAnswer(
                    answer = root["answer"]
                        ?.jsonPrimitive
                        ?.content
                        ?: error("final_answer does not contain answer"),
                )
            }

            "tool_call" -> {
                MultiMcpAgentAction.ToolCall(
                    serverName = root["serverName"]
                        ?.jsonPrimitive
                        ?.content
                        ?: error("tool_call does not contain serverName"),
                    toolName = root["toolName"]
                        ?.jsonPrimitive
                        ?.content
                        ?: error("tool_call does not contain toolName"),
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

data class MultiMcpToolAgentResult(
    val userRequest: String,
    val registeredTools: List<RegisteredMcpTool>,
    val toolHistory: List<MultiToolHistoryItem>,
    val finalAnswer: String,
)

data class RegisteredMcpTool(
    val serverName: String,
    val serverDescription: String,
    val serverExtraContext: String,
    val toolName: String,
    val rawToolInfo: String,
)

data class MultiToolHistoryItem(
    val serverName: String,
    val toolName: String,
    val saveAs: String,
    val resultText: String,
)

private data class ConnectedMcpServer(
    val name: String,
    val description: String,
    val client: Client,
    val extraContext: String,
)

private sealed interface MultiMcpAgentAction {

    data class FinalAnswer(
        val answer: String,
    ) : MultiMcpAgentAction

    data class ToolCall(
        val serverName: String,
        val toolName: String,
        val arguments: JsonObject,
        val saveAs: String?,
    ) : MultiMcpAgentAction
}

private val placeholderRegex = Regex("""\$\{([A-Za-z0-9_-]+)\.text}""")

private fun JsonElement.toPlainValue(
    toolHistory: List<MultiToolHistoryItem>,
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
    toolHistory: List<MultiToolHistoryItem>,
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

    require(start in 0..<end) {
        "LLM response does not contain JSON object: $this"
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