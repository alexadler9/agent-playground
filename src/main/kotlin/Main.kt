import com.jakewharton.retrofit2.converter.kotlinx.serialization.asConverterFactory
import config.AppConfig
import data.llm.RetrofitLlmGateway
import data.llm.api.ChatCompletionApi
import data.local.OllamaApi
import data.local.OllamaChatApi
import data.local.OllamaChatLlmGateway
import data.local.OllamaLocalLlmClient
import data.memory.JsonSessionHistoryRepository
import domain.llm.LlmGateway
import data.statefulagent.memory.JsonTaskArtifactRepository
import data.statefulagent.memory.JsonTaskContextRepository
import data.statefulagent.memory.JsonTaskStateRepository
import data.statefulagent.memory.MarkdownInvariantRepository
import data.statefulagent.memory.MarkdownLongTermMemoryRepository
import data.statefulagent.memory.MarkdownUserProfileRepository
import domain.model.AgentConfig
import domain.model.ChatSession
import domain.rag.DeterministicEmbeddingGateway
import domain.rag.DocumentLoader
import domain.rag.EmbeddingGateway
import domain.rag.FixedSizeChunker
import domain.rag.OllamaEmbeddingGateway
import domain.rag.RagChatAgent
import domain.rag.RagChatBackendResult
import domain.rag.RagChatComparisonReportWriter
import domain.rag.RagChatComparisonResult
import presentation.rag.RagChatCli
import domain.rag.RagChatTaskMemoryUpdater
import domain.rag.RagIndexBuilder
import domain.rag.RagIndexReader
import domain.rag.RagIndexWriter
import domain.rag.RagQueryRewriter
import domain.rag.RagRetrievalSettings
import domain.rag.StructureAwareChunker
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
import presentation.local.LocalLlmCli
import presentation.statefulagent.StatefulAgentCli
import retrofit2.Retrofit
import java.nio.file.Files
import java.nio.file.Path
import java.time.Duration
import kotlin.system.measureTimeMillis

fun main(args: Array<String>) = runBlocking {
    when (val command = args.firstOrNull()) {
        "local-llm" -> {
            runLocalLlm(args)
            return@runBlocking
        }

        "local-llm-demo" -> {
            runLocalLlmDemo(args)
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

        "rag-chat-compare" -> {
            runRagChatCompare(args)
            return@runBlocking
        }

        "stateful-agent", null -> {
            runStatefulAgent()
            return@runBlocking
        }

        else -> {
            println("Unknown command: $command")
            println("Available commands:")
            println("  local-llm [model] <prompt>")
            println("  local-llm-demo [model]")
            println("  build-rag-index <documents-root> [output-root] [deterministic|ollama] [embedding-model]")
            println("  rag-chat [index-path] [deterministic|ollama] [embedding-model] [cloud|local] [local-llm-model]")
            println("  rag-chat-compare <question>")
            println("  stateful-agent")
            return@runBlocking
        }
    }
}

private suspend fun runLocalLlm(args: Array<String>) {
    val model = args.getOrNull(1) ?: "qwen2.5:3b"
    val prompt = args.drop(2).joinToString(" ")

    if (prompt.isBlank()) {
        println("Usage:")
        println("""  .\gradlew.bat --console=plain -q run --args="local-llm [model] <prompt>"""")
        println()
        println("Example:")
        println("""  .\gradlew.bat --console=plain -q run --args="local-llm qwen2.5:3b Что такое локальная LLM?"""")
        return
    }

    val json = createJson()
    val okHttpClient = createOkHttpClient()

    val retrofit = Retrofit.Builder()
        .baseUrl("http://localhost:11434/")
        .client(okHttpClient)
        .addConverterFactory(
            json.asConverterFactory("application/json".toMediaType()),
        )
        .build()

    val client = OllamaLocalLlmClient(
        api = retrofit.create(OllamaApi::class.java),
        model = model,
    )

    try {
        LocalLlmCli(
            client = client,
            model = model,
        ).askOnce(prompt)
    } finally {
        okHttpClient.dispatcher.executorService.shutdown()
        okHttpClient.connectionPool.evictAll()
    }
}

private suspend fun runLocalLlmDemo(args: Array<String>) {
    val model = args.getOrNull(1) ?: "qwen2.5:3b"

    val json = createJson()
    val okHttpClient = createOkHttpClient()

    val retrofit = Retrofit.Builder()
        .baseUrl("http://localhost:11434/")
        .client(okHttpClient)
        .addConverterFactory(
            json.asConverterFactory("application/json".toMediaType()),
        )
        .build()

    val client = OllamaLocalLlmClient(
        api = retrofit.create(OllamaApi::class.java),
        model = model,
    )

    try {
        LocalLlmCli(
            client = client,
            model = model,
        ).runDemo()
    } finally {
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
    val llmProvider = args.getOrNull(4) ?: "cloud"
    val localLlmModel = args.getOrNull(5) ?: "qwen2.5:3b"

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
    println("LLM provider: $llmProvider")
    if (llmProvider == "local") {
        println("Локальная LLM: $localLlmModel")
    }
    println()

    val json = createJson()
    val embeddingGateway = createEmbeddingGateway(
        provider = embeddingProvider,
        model = embeddingModel,
        json = json,
    )

    val ragIndex = RagIndexReader(json).read(indexFile)

    val okHttpClient = createOkHttpClient()
    val llmModel = if (llmProvider == "local") localLlmModel else AppConfig.MODEL
    val llmGateway = createRagLlmGateway(
        provider = llmProvider,
        localModel = localLlmModel,
        json = json,
        okHttpClient = okHttpClient,
    )

    val answerConfig = AgentConfig(
        model = llmModel,
        maxTokens = 1_500,
        temperature = 0.0,
    )

    val shortConfig = AgentConfig(
        model = llmModel,
        maxTokens = 600,
        temperature = 0.0,
    )

    val agent = createRagChatAgent(
        llmGateway = llmGateway,
        answerConfig = answerConfig,
        shortConfig = shortConfig,
        embeddingGateway = embeddingGateway,
        json = json,
    )

    try {
        RagChatCli(
            index = ragIndex,
            agent = agent,
            settings = defaultRagRetrievalSettings(),
        ).start()
    } finally {
        okHttpClient.dispatcher.executorService.shutdown()
        okHttpClient.connectionPool.evictAll()
    }
}

private suspend fun runRagChatCompare(args: Array<String>) {
    val question = args.drop(1).joinToString(" ").ifBlank {
        "Объясни, как в проекте устроен RAG-пайплайн и где используются источники"
    }

    val indexPath = Path.of("rag-index", "structure-index.json").toString()
    val embeddingProvider = "ollama"
    val embeddingModel = "bge-m3"
    val localLlmModel = "qwen2.5:3b"
    val cloudModel = AppConfig.MODEL
    val indexFile = Path.of(indexPath)

    if (!Files.exists(indexFile)) {
        println("Не найден RAG-индекс: $indexFile")
        println("Сначала соберите индекс:")
        println("""  .\gradlew.bat --console=plain -q run --args="build-rag-index rag-documents rag-index ollama bge-m3"""")
        return
    }

    println("Сравниваю локальный и облачный RAG.")
    println("Вопрос: $question")
    println("Индекс: $indexFile")
    println("Локальная модель: $localLlmModel")
    println("Облачная модель: $cloudModel")
    println()

    val json = createJson()
    val embeddingGateway = createEmbeddingGateway(
        provider = embeddingProvider,
        model = embeddingModel,
        json = json,
    )
    val ragIndex = RagIndexReader(json).read(indexFile)
    val settings = defaultRagRetrievalSettings()

    val localHttpClient = createOkHttpClient()
    val cloudHttpClient = createOkHttpClient()

    try {
        val localGateway = createRagLlmGateway(
            provider = "local",
            localModel = localLlmModel,
            json = json,
            okHttpClient = localHttpClient,
        )
        val cloudGateway = createRagLlmGateway(
            provider = "cloud",
            localModel = localLlmModel,
            json = json,
            okHttpClient = cloudHttpClient,
        )

        val localAgent = createRagChatAgent(
            llmGateway = localGateway,
            answerConfig = AgentConfig(
                model = localLlmModel,
                maxTokens = 1_500,
                temperature = 0.0,
            ),
            shortConfig = AgentConfig(
                model = localLlmModel,
                maxTokens = 600,
                temperature = 0.0,
            ),
            embeddingGateway = embeddingGateway,
            json = json,
        )

        val cloudAgent = createRagChatAgent(
            llmGateway = cloudGateway,
            answerConfig = AgentConfig(
                model = cloudModel,
                maxTokens = 1_500,
                temperature = 0.0,
            ),
            shortConfig = AgentConfig(
                model = cloudModel,
                maxTokens = 600,
                temperature = 0.0,
            ),
            embeddingGateway = embeddingGateway,
            json = json,
        )

        lateinit var localTurn: domain.rag.RagChatTurnResult
        val localDurationMs = measureTimeMillis {
            localTurn = localAgent.handleUserMessage(
                index = ragIndex,
                state = domain.rag.RagChatSessionState(),
                userMessage = question,
                settings = settings,
            )
        }

        lateinit var cloudTurn: domain.rag.RagChatTurnResult
        val cloudDurationMs = measureTimeMillis {
            cloudTurn = cloudAgent.handleUserMessage(
                index = ragIndex,
                state = domain.rag.RagChatSessionState(),
                userMessage = question,
                settings = settings,
            )
        }

        val result = RagChatComparisonResult(
            question = question,
            indexPath = indexPath,
            embeddingProvider = embeddingProvider,
            embeddingModel = embeddingModel,
            localModel = localLlmModel,
            cloudModel = cloudModel,
            localResult = RagChatBackendResult(
                provider = "local",
                model = localLlmModel,
                durationMs = localDurationMs,
                answer = localTurn.answer,
            ),
            cloudResult = RagChatBackendResult(
                provider = "cloud",
                model = cloudModel,
                durationMs = cloudDurationMs,
                answer = cloudTurn.answer,
            ),
        )

        val outputPath = Path.of("reports", "day-28-local-rag-comparison.md")
        RagChatComparisonReportWriter().write(
            result = result,
            outputPath = outputPath,
        )

        println("Сравнительный отчет сохранен: $outputPath")
        println("Local: ${localDurationMs}ms, valid=${localTurn.answer.validation.isValid}")
        println("Cloud: ${cloudDurationMs}ms, valid=${cloudTurn.answer.validation.isValid}")
    } finally {
        localHttpClient.dispatcher.executorService.shutdown()
        localHttpClient.connectionPool.evictAll()
        cloudHttpClient.dispatcher.executorService.shutdown()
        cloudHttpClient.connectionPool.evictAll()
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

private fun createRagLlmGateway(
    provider: String,
    localModel: String,
    json: Json,
    okHttpClient: OkHttpClient,
): LlmGateway {
    return when (provider) {
        "cloud" -> createLlmGateway(
            json = json,
            okHttpClient = okHttpClient,
        )

        "local" -> {
            val retrofit = Retrofit.Builder()
                .baseUrl("http://localhost:11434/")
                .client(okHttpClient)
                .addConverterFactory(
                    json.asConverterFactory("application/json".toMediaType()),
                )
                .build()

            OllamaChatLlmGateway(
                api = retrofit.create(OllamaChatApi::class.java),
                defaultModel = localModel,
            )
        }

        else -> error("Unsupported LLM provider: $provider")
    }
}

private fun createRagChatAgent(
    llmGateway: LlmGateway,
    answerConfig: AgentConfig,
    shortConfig: AgentConfig,
    embeddingGateway: EmbeddingGateway,
    json: Json,
): RagChatAgent {
    return RagChatAgent(
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
}

private fun defaultRagRetrievalSettings(): RagRetrievalSettings {
    return RagRetrievalSettings(
        topKBefore = 12,
        topKAfter = 5,
        minSimilarityScore = 0.35,
        relativeScoreDrop = 0.10,
    )
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
