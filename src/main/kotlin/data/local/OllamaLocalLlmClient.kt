package data.local

/**
 * Минимальный клиент для локальной LLM через Ollama.
 *
 * Здесь не используем облачный gateway: запрос идёт в локальный HTTP API
 * на localhost, поэтому API-ключ не нужен.
 */
class OllamaLocalLlmClient(
    private val api: OllamaApi,
    private val model: String,
) {

    suspend fun ask(
        prompt: String,
        system: String? = null,
        temperature: Double = 0.2,
        maxTokens: Int = 700,
    ): OllamaGenerateResponse {
        return api.generate(
            request = OllamaGenerateRequest(
                model = model,
                prompt = prompt,
                stream = false,
                system = system,
                options = OllamaGenerateOptions(
                    temperature = temperature,
                    maxTokens = maxTokens,
                ),
            ),
        )
    }
}