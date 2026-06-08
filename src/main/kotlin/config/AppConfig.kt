package config

object AppConfig {

    const val BASE_URL = "https://api.deepseek.com/"

    val apiKey: String
        get() = System.getenv("DEEPSEEK_API_KEY")
            ?: error(
                "DEEPSEEK_API_KEY is not set. " +
                        "Please set it as an environment variable"
            )
}