package data.local

class OllamaLocalLlmClient(
    private val api: OllamaApi,
    private val model: String,
) {

    suspend fun ask(
        prompt: String,
        system: String,
        temperature: Double,
        maxTokens: Int,
    ): OllamaGenerateResponse {
        return api.generate(
            OllamaGenerateRequest(
                model = model,
                prompt = prompt,
                system = system,
                stream = false,
                options = OllamaGenerateOptions(
                    temperature = temperature,
                    maxTokens = maxTokens,
                ),
            ),
        )
    }
}