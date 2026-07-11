import com.jakewharton.retrofit2.converter.kotlinx.serialization.asConverterFactory
import config.AppConfig
import data.llm.RetrofitLlmGateway
import data.llm.api.ChatCompletionApi
import data.local.OllamaChatApi
import data.local.OllamaChatClient
import data.memory.JsonSessionHistoryRepository
import data.statefulagent.memory.*
import domain.model.AgentConfig
import domain.model.ChatSession
import domain.privatechat.PrivateChatService
import domain.rag.*
import domain.statefulagent.StatefulAgentService
import domain.statefulagent.memory.LlmTaskContextUpdater
import domain.statefulagent.stage.ExecutionStageAgent
import domain.statefulagent.stage.PlanningStageAgent
import domain.statefulagent.stage.ValidationStageAgent
import domain.statefulagent.validation.TaskTransitionValidator
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import presentation.privatechat.PrivateChatHttpServer
import presentation.rag.RagChatCli
import presentation.statefulagent.StatefulAgentCli
import retrofit2.Retrofit
import java.nio.file.Files
import java.nio.file.Path
import java.time.Duration

fun main(args: Array<String>) = runBlocking {
    when (val command = args.firstOrNull()) {
        "private-ai-service" -> {
            runPrivateAiService(args)
            return@runBlocking
        }

        "build-rag-index" -> {
            buildRagIndex(args)
            return@runBlocking
        }

        "rag-chat" -> {
            runRagChat(args)
            return@runBlocking
        }

        "stateful-agent", null -> {
            runStatefulAgent()
            return@runBlocking
        }

        else -> {
            println("  private-ai-service [port] [model]")
            println("Unknown command: $command")
            println("Available commands:")
            println("  build-rag-index <documents-root> [output-root] [deterministic|ollama] [embedding-model]")
            println("  rag-chat [index-path] [deterministic|ollama] [embedding-model]")
            println("  stateful-agent")
            return@runBlocking
        }
    }
}

private suspend fun runPrivateAiService(args: Array<String>) {
    val port = args.getOrNull(1)?.toIntOrNull() ?: 8080
    val model = args.getOrNull(2) ?: "qwen2.5:3b"
    val host = "0.0.0.0"

    val expectedToken = System.getenv("PRIVATE_AI_TOKEN")
        ?: "local-dev-token"

    val json = createJson()
    val okHttpClient = createOkHttpClient()

    val retrofit = Retrofit.Builder()
        .baseUrl("http://localhost:11434/")
        .client(okHttpClient)
        .addConverterFactory(
            json.asConverterFactory("application/json".toMediaType()),
        )
        .build()

    val chatClient = OllamaChatClient(
        api = retrofit.create(OllamaChatApi::class.java),
        model = model,
    )

    val chatService = PrivateChatService(
        client = chatClient,
        model = model,
    )

    val server = PrivateChatHttpServer(
        host = host,
        port = port,
        expectedToken = expectedToken,
        json = json,
        chatService = chatService,
    )

    server.start()

    println("Private AI service started.")
    println("URL: http://localhost:$port")
    println("Host binding: $host:$port")
    println("Model: $model")
    println("Token source: ${if (System.getenv("PRIVATE_AI_TOKEN") == null) "default local-dev-token" else "PRIVATE_AI_TOKEN env"}")
    println("Press Ctrl+C to stop.")

    try {
        awaitCancellation()
    } finally {
        server.stop()
        okHttpClient.dispatcher.executorService.shutdown()
        okHttpClient.connectionPool.evictAll()
    }
}

private suspend fun buildRagIndex(args: Array<String>) {
    val documentsRoot = args.getOrNull(1)
    val outputRoot = args.getOrNull(2) ?: "rag-index"

    if (documentsRoot.isNullOrBlank()) {
        println("Usage:")
        println("""  .\gradlew.bat --console=plain -q run --args="build-rag-index <documents-root> [output-root] [deterministic|ollama] [embedding-model]"""")
        return
    }

    val json = createJson()
    val documents = DocumentLoader().load(Path.of(documentsRoot))

    val embeddingProvider = args.getOrNull(3) ?: "deterministic"
    val embeddingModel = args.getOrNull(4) ?: "nomic-embed-text"
    val embeddingGateway = createEmbeddingGateway(
        provider = embeddingProvider,
        model = embeddingModel,
        json = json,
    )

    val strategies = listOf(
        FixedSizeChunker(),
        StructureAwareChunker(),
    )

    val outputDirectory = Path.of(outputRoot)
    Files.createDirectories(outputDirectory)

    strategies.forEach { strategy ->
        println("Building RAG index for strategy: ${strategy.name}")

        val index = RagIndexBuilder(
            embeddingGateway = embeddingGateway,
        ).build(
            documents = documents,
            chunkingStrategy = strategy,
        )

        val outputPath = outputDirectory.resolve("${strategy.name}-index.json")

        RagIndexWriter(json).write(
            index = index,
            outputPath = outputPath,
        )

        println("Index saved to: $outputPath")
        println("Chunks: ${index.chunksCount}")
        println()
    }

    println("RAG index build completed.")
    println("Embedding model: ${embeddingGateway.modelName}")
    println("Output directory: $outputDirectory")
}

private fun createEmbeddingGateway(
    provider: String,
    model: String,
    json: Json,
): EmbeddingGateway {
    return when (provider) {
        "deterministic" -> DeterministicEmbeddingGateway()
        "ollama" -> OllamaEmbeddingGateway(
            model = model,
            json = json,
        )
        else -> error("Unsupported embedding provider: $provider")
    }
}

private suspend fun runRagChat(args: Array<String>) {
    val indexPath = args.getOrNull(1)
        ?.takeIf { it.isNotBlank() }
        ?: Path.of("rag-index", "structure-index.json").toString()

    val embeddingProvider = args.getOrNull(2) ?: "ollama"
    val embeddingModel = args.getOrNull(3) ?: "bge-m3"

    val indexFile = Path.of(indexPath)

    if (!Files.exists(indexFile)) {
        println("Не найден RAG-индекс: $indexFile")
        println("Сначала соберите индекс:")
        println("""  .\gradlew.bat --console=plain -q run --args="build-rag-index rag-documents rag-index ollama bge-m3"""")
        return
    }

    println("Запускаю RAG-чат.")
    println("Индекс: $indexFile")
    println("Провайдер эмбеддингов: $embeddingProvider")
    println("Модель эмбеддингов: $embeddingModel")
    println()

    val json = createJson()
    val embeddingGateway = createEmbeddingGateway(
        provider = embeddingProvider,
        model = embeddingModel,
        json = json,
    )

    val ragIndex = RagIndexReader(json).read(indexFile)

    val okHttpClient = createOkHttpClient()

    val llmGateway = createLlmGateway(
        json = json,
        okHttpClient = okHttpClient,
    )

    val answerConfig = AgentConfig(
        model = AppConfig.MODEL,
        maxTokens = 1_500,
        temperature = 0.0,
    )

    val shortConfig = AgentConfig(
        model = AppConfig.MODEL,
        maxTokens = 600,
        temperature = 0.0,
    )

    val agent = RagChatAgent(
        llmGateway = llmGateway,
        answerConfig = answerConfig,
        embeddingGateway = embeddingGateway,
        queryRewriter = RagQueryRewriter(
            llmGateway = llmGateway,
            agentConfig = shortConfig,
        ),
        taskMemoryUpdater = RagChatTaskMemoryUpdater(
            llmGateway = llmGateway,
            config = shortConfig,
            json = json,
        ),
        json = json,
    )

    try {
        RagChatCli(
            index = ragIndex,
            agent = agent,
            settings = RagRetrievalSettings(
                topKBefore = 12,
                topKAfter = 5,
                minSimilarityScore = 0.35,
                relativeScoreDrop = 0.10,
            ),
        ).start()
    } finally {
        okHttpClient.dispatcher.executorService.shutdown()
        okHttpClient.connectionPool.evictAll()
    }
}

private suspend fun runStatefulAgent() {
    val okHttpClient = createOkHttpClient()
    val json = createJson()

    val llmGateway = createLlmGateway(
        json = json,
        okHttpClient = okHttpClient,
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
            maxTokens = 1_000,
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

private fun createLlmGateway(
    json: Json,
    okHttpClient: OkHttpClient,
): RetrofitLlmGateway {
    val retrofit = Retrofit.Builder()
        .baseUrl(AppConfig.BASE_URL)
        .client(okHttpClient)
        .addConverterFactory(
            json.asConverterFactory("application/json".toMediaType()),
        )
        .build()

    return RetrofitLlmGateway(
        api = retrofit.create(ChatCompletionApi::class.java),
        apiKey = AppConfig.apiKey,
    )
}

private fun createOkHttpClient(): OkHttpClient {
    return OkHttpClient.Builder()
        .connectTimeout(Duration.ofSeconds(30))
        .readTimeout(Duration.ofSeconds(120))
        .writeTimeout(Duration.ofSeconds(60))
        .callTimeout(Duration.ofSeconds(180))
        .build()
}

private fun createJson(): Json {
    return Json {
        prettyPrint = true
        explicitNulls = false
        ignoreUnknownKeys = true
    }
}
