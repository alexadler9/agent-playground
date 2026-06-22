package domain.mcp

/**
 * Описание MCP-сервера, к которому подключается demo-клиент.
 */
data class McpServerConfig(
    val name: String,
    val command: String,
    val args: List<String> = emptyList(),
    val env: Map<String, String> = emptyMap(),
) {
    companion object {
        fun everythingWindows(): McpServerConfig = McpServerConfig(
            name = "everything",
            command = "cmd",
            args = listOf(
                "/c",
                "npx",
                "-y",
                "@modelcontextprotocol/server-everything",
            ),
        )
    }
}