import com.jakewharton.retrofit2.converter.kotlinx.serialization.asConverterFactory
import config.AppConfig
import data.llm.RetrofitLlmGateway
import data.llm.api.ChatCompletionApi
import data.memory.JsonSessionHistoryRepository
import data.statefulagent.memory.JsonTaskArtifactRepository
import data.statefulagent.memory.JsonTaskContextRepository
import data.statefulagent.memory.JsonTaskStateRepository
import data.statefulagent.memory.MarkdownInvariantRepository
import data.statefulagent.memory.MarkdownLongTermMemoryRepository
import data.statefulagent.memory.MarkdownUserProfileRepository
import domain.model.AgentConfig
import domain.model.ChatSession
import domain.statefulagent.StatefulAgentService
import domain.statefulagent.memory.LlmTaskContextUpdater
import domain.statefulagent.memory.TaskStateRepository
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

fun main() = runBlocking {
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
