package config

object AppConfig {

    const val BASE_URL = "https://openrouter.ai/api/v1/"

    const val MODEL = "openai/gpt-oss-20b:free"

    val apiKey: String
        get() = System.getenv("OPENROUTER_API_KEY")
            ?: error(
                "OPENROUTER_API_KEY is not set. " +
                        "Please set it as an environment variable"
            )
}