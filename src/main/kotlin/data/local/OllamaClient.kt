package data.local

class OllamaChatClient(
    private val api: OllamaChatApi,
    private val model: String,
) {

    suspend fun chat(
        messages: List<OllamaChatMessage>,
        temperature: Double,
        maxTokens: Int,
        contextWindow: Int,
    ): OllamaChatResponse {
        return api.chat(
            OllamaChatRequest(
                model = model,
                messages = messages,
                stream = false,
                options = OllamaChatOptions(
                    temperature = temperature,
                    maxTokens = maxTokens,
                    contextWindow = contextWindow,
                ),
            ),
        )
    }
}