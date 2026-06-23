package domain.mcp

import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO
import io.ktor.client.plugins.sse.SSE
import io.modelcontextprotocol.kotlin.sdk.client.Client
import io.modelcontextprotocol.kotlin.sdk.client.StreamableHttpClientTransport
import io.modelcontextprotocol.kotlin.sdk.types.Implementation
import io.modelcontextprotocol.kotlin.sdk.types.TextContent

/**
 * Вызывает удалённый MCP-инструмент get_recent_commits.
 */
class RemoteGithubCommitsMcpRunner {

    suspend fun callRecentCommits(
        mcpUrl: String,
        owner: String,
        repo: String,
        limit: Int,
    ): GithubCommitsMcpResult {
        require(mcpUrl.isNotBlank()) { "MCP URL must not be blank" }
        require(owner.isNotBlank()) { "Repository owner must not be blank" }
        require(repo.isNotBlank()) { "Repository name must not be blank" }
        require(limit in 1..10) { "Limit must be from 1 to 10" }

        val httpClient = HttpClient(CIO) {
            install(SSE)
        }

        val client = Client(
            clientInfo = Implementation(
                name = "agent-playground-remote-mcp-client",
                version = "1.0.0",
            ),
        )

        val transport = StreamableHttpClientTransport(
            client = httpClient,
            url = mcpUrl,
        )

        return try {
            client.connect(transport)

            val availableTools = client.listTools()
                .tools
                .map { tool -> tool.name }

            require("get_recent_commits" in availableTools) {
                "MCP server does not expose get_recent_commits. Available tools: ${availableTools.joinToString()}"
            }

            val toolResult = client.callTool(
                name = "get_recent_commits",
                arguments = mapOf(
                    "owner" to owner,
                    "repo" to repo,
                    "limit" to limit,
                ),
            )

            val resultText = toolResult.content.joinToString(separator = "\n\n") { content ->
                when (content) {
                    is TextContent -> content.text
                    else -> content.toString()
                }
            }

            if (toolResult.isError == true) {
                error("MCP tool returned an error: $resultText")
            }

            GithubCommitsMcpResult(
                availableTools = availableTools,
                resultText = resultText,
            )
        } finally {
            runCatching { client.close() }
            httpClient.close()
        }
    }
}

/**
 * Результат вызова MCP-инструмента, который можно показать в CLI.
 */
data class GithubCommitsMcpResult(
    val availableTools: List<String>,
    val resultText: String,
)