import com.jakewharton.retrofit2.converter.kotlinx.serialization.asConverterFactory
import config.AppConfig
import data.llm.RetrofitLlmGateway
import data.llm.api.ChatCompletionApi
import data.memory.JsonSessionHistoryRepository
import data.statefulagent.memory.*
import domain.mcp.RemoteMultiMcpToolAgent
import domain.model.AgentConfig
import domain.model.ChatSession
import domain.rag.ChunkingComparisonReporter
import domain.rag.DeterministicEmbeddingGateway
import domain.rag.DocumentLoader
import domain.rag.FixedSizeChunker
import domain.rag.OllamaEmbeddingGateway
import domain.rag.RagIndexBuilder
import domain.rag.RagIndexReader
import domain.rag.RagIndexSearcher
import domain.rag.RagIndexWriter
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
import presentation.statefulagent.StatefulAgentCli
import retrofit2.Retrofit
import java.nio.file.Files
import java.nio.file.Path
import java.time.Duration

fun main(args: Array<String>) = runBlocking {
    if (args.firstOrNull() == "rag-search") {
        val indexPath = args.getOrNull(1)
        val embeddingProvider = args.getOrNull(2) ?: "ollama"
        val embeddingModel = args.getOrNull(3) ?: "nomic-embed-text"
        val query = args.drop(4).joinToString(" ")

        if (indexPath.isNullOrBlank() || query.isBlank()) {
            println("Usage:")
            println("""  .\gradlew.bat --console=plain -q run --args="rag-search <index-path> [deterministic|ollama] [embedding-model] <query>"""")
            return@runBlocking
        }

        val json = Json {
            prettyPrint = true
            explicitNulls = false
            ignoreUnknownKeys = true
        }

        val embeddingGateway = when (embeddingProvider) {
            "deterministic" -> DeterministicEmbeddingGateway()
            "ollama" -> OllamaEmbeddingGateway(
                model = embeddingModel,
                json = json,
            )
            else -> error("Unsupported embedding provider: $embeddingProvider")
        }

        val index = RagIndexReader(json).read(Path.of(indexPath))

        if (index.embeddingModel != embeddingGateway.modelName) {
            println("Warning: index embedding model is ${index.embeddingModel}, query embedding model is ${embeddingGateway.modelName}")
            println("Search quality may be incorrect if embedding models are different.")
            println()
        }

        val results = RagIndexSearcher(
            embeddingGateway = embeddingGateway,
        ).search(
            index = index,
            query = query,
            topK = 5,
        )

        println("RAG search completed.")
        println("Index: $indexPath")
        println("Strategy: ${index.strategy}")
        println("Embedding model: ${index.embeddingModel}")
        println("Query: $query")
        println()

        results.forEachIndexed { resultIndex, result ->
            val chunk = result.chunk

            println("${resultIndex + 1}. score=${"%.4f".format(result.score)}")
            println("   chunk_id: ${chunk.chunkId}")
            println("   source: ${chunk.source}")
            println("   title: ${chunk.title}")
            println("   section: ${chunk.section}")
            println("   chars: ${chunk.text.length}")
            println("   preview: ${chunk.text.take(400).replace("\n", " ")}")
            println()
        }

        return@runBlocking
    }

    if (args.firstOrNull() == "build-rag-index") {
        val documentsRoot = args.getOrNull(1)
        val outputRoot = args.getOrNull(2) ?: "rag-index"

        if (documentsRoot.isNullOrBlank()) {
            println("Usage:")
            println("""  .\gradlew.bat --console=plain -q run --args="build-rag-index <documents-root> [output-root] [deterministic|ollama] [embedding-model]"""")
            return@runBlocking
        }

        val json = Json {
            prettyPrint = true
            explicitNulls = false
            ignoreUnknownKeys = true
        }

        val documents = DocumentLoader().load(Path.of(documentsRoot))

        val embeddingProvider = args.getOrNull(3) ?: "deterministic"
        val embeddingModel = args.getOrNull(4) ?: "nomic-embed-text"

        val embeddingGateway = when (embeddingProvider) {
            "deterministic" -> DeterministicEmbeddingGateway()
            "ollama" -> OllamaEmbeddingGateway(
                model = embeddingModel,
                json = json,
            )
            else -> error("Unsupported embedding provider: $embeddingProvider")
        }

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

        return@runBlocking
    }

    if (args.firstOrNull() == "rag-compare-chunking") {
        val documentsRoot = args.getOrNull(1)
        val outputRoot = args.getOrNull(2) ?: "rag-index"

        if (documentsRoot.isNullOrBlank()) {
            println("Usage:")
            println("""  .\gradlew.bat --console=plain -q run --args="rag-compare-chunking <documents-root> [output-root]"""")
            return@runBlocking
        }

        val documents = DocumentLoader().load(Path.of(documentsRoot))

        val strategies = listOf(
            FixedSizeChunker(),
            StructureAwareChunker(),
        )

        val report = ChunkingComparisonReporter().buildReport(
            documents = documents,
            strategies = strategies,
        )

        val outputDirectory = Path.of(outputRoot)
        Files.createDirectories(outputDirectory)

        val reportPath = outputDirectory.resolve("chunking-comparison.md")
        Files.writeString(reportPath, report)

        println("Comparison report saved to: $reportPath")

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
