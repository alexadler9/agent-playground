import com.jakewharton.retrofit2.converter.kotlinx.serialization.asConverterFactory
import config.AppConfig
import data.llm.RetrofitLlmGateway
import data.llm.api.ChatCompletionApi
import data.memory.JsonSessionHistoryRepository
import data.statefulagent.memory.*
import domain.mcp.RemoteGithubCommitsMcpRunner
import domain.mcp.RemoteMcpToolAgent
import domain.mcp.RemotePriceWatchMcpRunner
import domain.model.AgentConfig
import domain.model.ChatSession
import domain.statefulagent.StatefulAgentService
import domain.statefulagent.memory.LlmTaskContextUpdater
import domain.statefulagent.stage.ExecutionStageAgent
import domain.statefulagent.stage.PlanningStageAgent
import domain.statefulagent.stage.ValidationStageAgent
import domain.statefulagent.validation.TaskTransitionValidator
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import presentation.statefulagent.StatefulAgentCli
import retrofit2.Retrofit
import java.nio.file.Path
import java.time.Duration

fun main(args: Array<String>) = runBlocking {
    if (args.firstOrNull() == "mcp-github-commits") {
        val mcpUrl = args.getOrNull(1)
        val owner = args.getOrNull(2)
        val repo = args.getOrNull(3)
        val limit = args.getOrNull(4)?.toIntOrNull() ?: 3

        if (mcpUrl == null || owner == null || repo == null) {
            println("Usage:")
            println("""  .\gradlew.bat run --args="mcp-github-commits <mcp-url> <owner> <repo> [limit]"""")
            println()
            println("Example:")
            println("""  .\gradlew.bat run --args="mcp-github-commits https://your-service.onrender.com/mcp JetBrains kotlin 3"""")
            return@runBlocking
        }

        println("Remote MCP server: $mcpUrl")
        println("Tool: get_recent_commits")
        println("Repository: $owner/$repo")
        println("Limit: $limit")
        println()

        val result = RemoteGithubCommitsMcpRunner().callRecentCommits(
            mcpUrl = mcpUrl,
            owner = owner,
            repo = repo,
            limit = limit,
        )

        println("Connection: established")
        println("Available tools: ${result.availableTools.joinToString()}")
        println()
        println("Agent result:")
        println()
        println(result.resultText)

        return@runBlocking
    }

    if (args.firstOrNull() == "mcp-price-watch") {
        val mcpUrl = args.getOrNull(1)
        val symbol = args.getOrNull(2) ?: "BTCUSDT"
        val intervalSeconds = args.getOrNull(3)?.toIntOrNull() ?: 10
        val waitSeconds = args.getOrNull(4)?.toIntOrNull() ?: 35
        val stopAfterSummary = args.getOrNull(5)?.toBooleanStrictOrNull() ?: true

        if (mcpUrl == null) {
            println("Usage:")
            println("""  .\gradlew.bat run --args="mcp-price-watch <mcp-url> [symbol] [intervalSeconds] [waitSeconds] [stopAfterSummary]"""")
            println()
            println("Example:")
            println("""  .\gradlew.bat run --args="mcp-price-watch https://your-service.onrender.com/mcp BTCUSDT 10 35 true"""")
            return@runBlocking
        }

        println("Remote MCP server: $mcpUrl")
        println("Tool flow: start_price_watch -> wait -> get_price_watch_summary")
        println("Symbol: $symbol")
        println("Interval: $intervalSeconds seconds")
        println("Wait before summary: $waitSeconds seconds")
        println()

        val result = RemotePriceWatchMcpRunner().runPriceWatchDemo(
            mcpUrl = mcpUrl,
            symbol = symbol,
            intervalSeconds = intervalSeconds,
            waitSeconds = waitSeconds,
            stopAfterSummary = stopAfterSummary,
            onProgress = { elapsedSeconds, totalSeconds ->
                println("Waiting for price snapshots... $elapsedSeconds/$totalSeconds seconds")
            },
        )

        println("Connection: established")
        println("Available tools: ${result.availableTools.joinToString()}")
        println()
        println("Start result:")
        println(result.startText)
        println()
        println("Agent summary:")
        println(result.summaryText)

        result.stopText?.let { stopText ->
            println()
            println("Stop result:")
            println(stopText)
        }

        return@runBlocking
    }

    if (args.firstOrNull() == "mcp-agent-request") {
        val mcpUrl = args.getOrNull(1)
        val userRequest = args.drop(2).joinToString(" ")

        if (mcpUrl == null || userRequest.isBlank()) {
            println("Usage:")
            println("""  .\gradlew.bat --console=plain -q run --args="mcp-agent-request <mcp-url> <user-request>"""")
            println()
            println("Example:")
            println("""  .\gradlew.bat --console=plain -q run --args="mcp-agent-request https://your-service.onrender.com/mcp Find last 5 commits in JetBrains/kotlin, summarize them and save to file"""")
            return@runBlocking
        }

        println("User request:")
        println(userRequest)
        println()

        val okHttpClient = OkHttpClient.Builder()
            .connectTimeout(Duration.ofSeconds(30))
            .readTimeout(Duration.ofSeconds(120))
            .writeTimeout(Duration.ofSeconds(60))
            .callTimeout(Duration.ofSeconds(180))
            .build()

        val json = Json {
            ignoreUnknownKeys = true
            explicitNulls = false
        }

        val retrofit = Retrofit.Builder()
            .baseUrl(AppConfig.BASE_URL)
            .client(okHttpClient)
            .addConverterFactory(
                json.asConverterFactory("application/json".toMediaType())
            )
            .build()

        val api = retrofit.create(ChatCompletionApi::class.java)

        val llmGateway = RetrofitLlmGateway(
            api = api,
            apiKey = AppConfig.apiKey,
        )

        val result = try {
            RemoteMcpToolAgent(
                llmGateway = llmGateway,
                agentConfig = AgentConfig(
                    model = AppConfig.MODEL,
                    maxTokens = 1_000,
                    temperature = 0.0,
                ),
                json = json,
            ).handleUserRequest(
                mcpUrl = mcpUrl,
                userRequest = userRequest,
                onStep = { step -> println(step) },
            )
        } finally {
            okHttpClient.dispatcher.executorService.shutdown()
            okHttpClient.connectionPool.evictAll()
        }

        if (result.toolHistory.isNotEmpty()) {
            println("Executed MCP tool calls:")
            result.toolHistory.forEachIndexed { index, item ->
                println("${index + 1}. ${item.toolName} -> ${item.saveAs}")
            }
            println()
        }

        println("Agent answer:")
        println(result.finalAnswer)

        return@runBlocking
    }

    val okHttpClient = OkHttpClient.Builder()
        .connectTimeout(Duration.ofSeconds(30))
        .readTimeout(Duration.ofSeconds(120))
        .writeTimeout(Duration.ofSeconds(60))
        .callTimeout(Duration.ofSeconds(180))
        .build()

    val json = Json {
        ignoreUnknownKeys = true
        explicitNulls = false
    }

    val retrofit = Retrofit.Builder()
        .baseUrl(AppConfig.BASE_URL)
        .client(okHttpClient)
        .addConverterFactory(
            json.asConverterFactory("application/json".toMediaType())
        )
        .build()

    val api = retrofit.create(ChatCompletionApi::class.java)

    val llmGateway = RetrofitLlmGateway(
        api = api,
        apiKey = AppConfig.apiKey,
    )

    val storageRoot = Path.of(
        "storage",
        "stateful-agent",
    )

    val taskContextRepository = JsonTaskContextRepository(
        storageFile = storageRoot.resolve("task-context.json"),
        json = json,
    )

    val userProfileRepository = MarkdownUserProfileRepository(
        profilesDirectory = storageRoot.resolve("profiles"),
        activeProfileFile = storageRoot.resolve("active-profile.txt"),
    )

    val longTermMemoryRepository = MarkdownLongTermMemoryRepository(
        memoryDirectory = storageRoot.resolve("long-term-memory"),
        userProfileRepository = userProfileRepository,
    )

    val taskContextUpdater = LlmTaskContextUpdater(
        llmGateway = llmGateway,
        config = AgentConfig(
            model = AppConfig.MODEL,
            maxTokens = 1000,
            temperature = 0.0,
        ),
        json = json,
    )

    val stageAgentConfig = AgentConfig(
        model = AppConfig.MODEL,
        maxTokens = 1_200,
        temperature = 0.2,
    )

    val agentService = StatefulAgentService(
        session = ChatSession(id = "stateful-agent-session"),
        sessionHistoryRepository = JsonSessionHistoryRepository(
            storageFile = Path.of("storage", "session-history.json"),
            json = json,
        ),
        taskContextRepository = taskContextRepository,
        longTermMemoryRepository = longTermMemoryRepository,
        taskContextUpdater = taskContextUpdater,
        taskStateRepository = JsonTaskStateRepository(
            storageFile = storageRoot.resolve("task-state.json"),
            json = json,
        ),
        taskArtifactRepository = JsonTaskArtifactRepository(
            storageFile = storageRoot.resolve("task-artifacts.json"),
            json = json,
        ),
        invariantRepository = MarkdownInvariantRepository(
            storageFile = storageRoot.resolve("invariants.md"),
        ),
        transitionValidator = TaskTransitionValidator(),
        stageAgents = listOf(
            PlanningStageAgent(
                llmGateway = llmGateway,
                config = stageAgentConfig,
                json = json,
            ),
            ExecutionStageAgent(
                llmGateway = llmGateway,
                config = stageAgentConfig,
                json = json,
            ),
            ValidationStageAgent(
                llmGateway = llmGateway,
                config = stageAgentConfig,
                json = json,
            ),
        ),
    )

    try {
        StatefulAgentCli(
            agentService = agentService,
            userProfileRepository = userProfileRepository,
        ).start()
    } finally {
        okHttpClient.dispatcher.executorService.shutdown()
        okHttpClient.connectionPool.evictAll()
    }
}
