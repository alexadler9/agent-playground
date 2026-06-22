package domain.mcp

import io.modelcontextprotocol.kotlin.sdk.client.Client
import io.modelcontextprotocol.kotlin.sdk.client.StdioClientTransport
import io.modelcontextprotocol.kotlin.sdk.types.Implementation
import kotlinx.io.asSink
import kotlinx.io.asSource
import kotlinx.io.buffered

/**
 * Подключается к MCP-серверу через stdio и получает список доступных tools.
 */
class McpToolsListRunner {

    suspend fun loadTools(config: McpServerConfig): List<McpToolInfo> {
        val process = ProcessBuilder(listOf(config.command) + config.args)
            .also { builder ->
                if (config.env.isNotEmpty()) {
                    builder.environment().putAll(config.env)
                }
            }
            .start()

        val client = Client(
            clientInfo = Implementation(
                name = "agent-playground-mcp-demo",
                version = "1.0.0",
            ),
        )

        val transport = StdioClientTransport(
            input = process.inputStream.asSource().buffered(),
            output = process.outputStream.asSink().buffered(),
            error = process.errorStream.asSource().buffered(),
        ) {
            // Для demo stderr не считаем ошибкой протокола.
            StdioClientTransport.StderrSeverity.DEBUG
        }

        return try {
            client.connect(transport)

            client.listTools()
                .tools
                .map { tool ->
                    McpToolInfo(
                        name = tool.name,
                        description = tool.description ?: "Без описания",
                    )
                }
        } finally {
            runCatching { client.close() }

            process.destroy()
            if (process.isAlive) {
                process.destroyForcibly()
            }
        }
    }
}