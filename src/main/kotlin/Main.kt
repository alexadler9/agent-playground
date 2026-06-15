import com.jakewharton.retrofit2.converter.kotlinx.serialization.asConverterFactory
import config.AppConfig
import data.llm.RetrofitLlmGateway
import data.llm.api.ChatCompletionApi
import data.memory.JsonFactMemoryRepository
import data.memory.JsonSessionHistoryRepository
import data.memory.JsonSessionSummaryRepository
import domain.contextagent.ContextAgentService
import domain.context.ContextBuilderProvider
import domain.context.ContextStrategyType
import domain.context.FullHistoryContextBuilder
import domain.context.SlidingWindowContextBuilder
import domain.context.StickyFactsContextBuilder
import domain.context.SwitchableContextBuilder
import domain.memory.BranchManager
import domain.memory.HistoryCompressionManager
import domain.memory.LlmFactMemoryUpdater
import domain.memory.LlmHistorySummarizer
import domain.model.AgentConfig
import domain.model.ChatSession
import domain.token.ApproximateTokenEstimator
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import presentation.contextagent.ContextAgentCli
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

    val summaryRepository = JsonSessionSummaryRepository(
        storageFile = Path.of("storage", "session-summary.json"),
        json = json,
    )

    val agentConfig = AgentConfig(
        model = AppConfig.MODEL,
        systemPrompt = DEFAULT_SYSTEM_PROMPT,
        maxTokens = 1_000,
        temperature = 0.3,
    )

    val factMemoryUpdater = LlmFactMemoryUpdater(
        llmGateway = llmGateway,
        config = agentConfig,
        json = json,
    )

    val historySummarizer = LlmHistorySummarizer(
        llmGateway = llmGateway,
        config = agentConfig,
    )

    val historyCompressionManager = HistoryCompressionManager(
        summaryRepository = summaryRepository,
        historySummarizer = historySummarizer,
        recentMessagesCount = 10,
        summarizeBatchSize = 10,
    )

    val factMemoryRepository = JsonFactMemoryRepository(
        storageFile = Path.of("storage", "facts.json"),
        json = json,
    )

    val contextBuilderProvider = ContextBuilderProvider(
        initialStrategy = ContextStrategyType.SLIDING_WINDOW,
        fullHistoryContextBuilder = FullHistoryContextBuilder(),
        slidingWindowContextBuilder = SlidingWindowContextBuilder(
            recentMessagesCount = 10,
        ),
        stickyFactsContextBuilder = StickyFactsContextBuilder(
            recentMessagesCount = 10,
        ),
    )

    val branchManager = BranchManager()

    val agentService = ContextAgentService(
        session = ChatSession(),
        config = agentConfig,
        historyRepository = JsonSessionHistoryRepository(
            storageFile = Path.of("storage", "session-history.json"),
            json = json,
        ),
        contextBuilder = SwitchableContextBuilder(
            provider = contextBuilderProvider,
        ),
        llmGateway = llmGateway,
        tokenEstimator = ApproximateTokenEstimator(),
        summaryRepository = summaryRepository,
//        historyCompressionManager = historyCompressionManager,
        factMemoryRepository = factMemoryRepository,
        factMemoryUpdater = factMemoryUpdater,
        branchManager = branchManager,
    )

    try {
        ContextAgentCli(
            agentService = agentService,
            contextBuilderProvider = contextBuilderProvider,
            branchManager = branchManager,
        ).start()
    } finally {
        okHttpClient.dispatcher.executorService.shutdown()
        okHttpClient.connectionPool.evictAll()
    }
}

private const val DEFAULT_SYSTEM_PROMPT =
    "Ты полезный AI-агент. Отвечай на русском языке. " +
            "Учитывай историю текущей сессии. " +
            "Если пользователь спрашивает о данных, которые были в текущем диалоге, используй историю сообщений. " +
            "Если история была очищена или нужной информации нет в контексте, честно скажи, что не видишь этих данных"