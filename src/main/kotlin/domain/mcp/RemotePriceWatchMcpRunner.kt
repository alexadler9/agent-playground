package domain.mcp

import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO
import io.ktor.client.plugins.sse.SSE
import io.modelcontextprotocol.kotlin.sdk.client.Client
import io.modelcontextprotocol.kotlin.sdk.client.StreamableHttpClientTransport
import io.modelcontextprotocol.kotlin.sdk.types.CallToolResult
import io.modelcontextprotocol.kotlin.sdk.types.Implementation
import io.modelcontextprotocol.kotlin.sdk.types.TextContent
import kotlinx.coroutines.delay

/**
 * Демонстрирует scheduled MCP tool:
 * запускает watch на сервере, ждёт накопления snapshots и запрашивает summary.
 */
class RemotePriceWatchMcpRunner {

    suspend fun runPriceWatchDemo(
        mcpUrl: String,
        symbol: String,
        intervalSeconds: Int,
        waitSeconds: Int,
        stopAfterSummary: Boolean,
        onProgress: (elapsedSeconds: Int, totalSeconds: Int) -> Unit = { _, _ -> },
    ): PriceWatchDemoResult {
        require(mcpUrl.isNotBlank()) { "MCP URL must not be blank" }
        require(symbol.isNotBlank()) { "Symbol must not be blank" }
        require(intervalSeconds in 10..3600) { "intervalSeconds must be from 10 to 3600" }
        require(waitSeconds >= intervalSeconds) { "waitSeconds must be >= intervalSeconds" }

        val httpClient = HttpClient(CIO) {
            install(SSE)
        }

        val client = Client(
            clientInfo = Implementation(
                name = "agent-playground-price-watch-client",
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

            require("start_price_watch" in availableTools) {
                "MCP server does not expose start_price_watch. Available tools: ${availableTools.joinToString()}"
            }
            require("get_price_watch_summary" in availableTools) {
                "MCP server does not expose get_price_watch_summary. Available tools: ${availableTools.joinToString()}"
            }

            val startResult = client.callTool(
                name = "start_price_watch",
                arguments = mapOf(
                    "symbol" to symbol,
                    "intervalSeconds" to intervalSeconds,
                ),
            )

            val startText = startResult.asText()
            if (startResult.isError == true) {
                error("start_price_watch returned an error: $startText")
            }

            var elapsedSeconds = 0
            val progressStepSeconds = 5

            while (elapsedSeconds < waitSeconds) {
                val nextStepSeconds = minOf(progressStepSeconds, waitSeconds - elapsedSeconds)

                delay(nextStepSeconds * 1000L)

                elapsedSeconds += nextStepSeconds
                onProgress(elapsedSeconds, waitSeconds)
            }

            val summaryResult = client.callTool(
                name = "get_price_watch_summary",
                arguments = mapOf(
                    "symbol" to symbol,
                ),
            )

            val summaryText = summaryResult.asText()
            if (summaryResult.isError == true) {
                error("get_price_watch_summary returned an error: $summaryText")
            }

            val stopText = if (stopAfterSummary && "stop_price_watch" in availableTools) {
                val stopResult = client.callTool(
                    name = "stop_price_watch",
                    arguments = mapOf(
                        "symbol" to symbol,
                    ),
                )

                stopResult.asText()
            } else {
                null
            }

            PriceWatchDemoResult(
                availableTools = availableTools,
                startText = startText,
                summaryText = summaryText,
                stopText = stopText,
            )
        } finally {
            runCatching { client.close() }
            httpClient.close()
        }
    }
}

data class PriceWatchDemoResult(
    val availableTools: List<String>,
    val startText: String,
    val summaryText: String,
    val stopText: String?,
)

private fun CallToolResult.asText(): String {
    return content.joinToString(separator = "\n\n") { content ->
        when (content) {
            is TextContent -> content.text
            else -> content.toString()
        }
    }
}