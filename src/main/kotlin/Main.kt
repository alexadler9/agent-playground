import com.jakewharton.retrofit2.converter.kotlinx.serialization.asConverterFactory
import config.AppConfig
import data.llm.RetrofitLlmGateway
import data.llm.api.ChatCompletionApi
import data.memory.InMemorySessionHistoryRepository
import domain.agent.AgentService
import domain.context.FullHistoryContextBuilder
import domain.model.AgentConfig
import domain.model.ChatSession
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import presentation.cli.AgentCli
import retrofit2.Retrofit
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

    val agentService = AgentService(
        session = ChatSession(),
        config = AgentConfig(
            model = "deepseek-chat",
            systemPrompt = "You are a helpful agent. Answer in English. Use the current session history",
            maxTokens = 1_000,
            temperature = 0.3,
        ),
        historyRepository = InMemorySessionHistoryRepository(),
        contextBuilder = FullHistoryContextBuilder(),
        llmGateway = llmGateway,
    )

    try {
        AgentCli(
            agentService = agentService,
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