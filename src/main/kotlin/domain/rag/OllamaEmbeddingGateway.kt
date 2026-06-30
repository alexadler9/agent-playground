package domain.rag

import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Duration

class OllamaEmbeddingGateway(
    private val model: String = "nomic-embed-text",
    private val baseUrl: String = "http://localhost:11434",
    private val json: Json,
) : EmbeddingGateway {

    private val client = HttpClient.newBuilder()
        .connectTimeout(Duration.ofSeconds(10))
        .build()

    override val modelName: String = "ollama-$model"

    override suspend fun embed(text: String): List<Float> {
        val requestBody = json.encodeToString(
            OllamaEmbedRequest(
                model = model,
                input = text,
                truncate = true,
            ),
        )

        val request = HttpRequest.newBuilder()
            .uri(URI.create("$baseUrl/api/embed"))
            .timeout(Duration.ofSeconds(120))
            .header("Content-Type", "application/json")
            .POST(HttpRequest.BodyPublishers.ofString(requestBody))
            .build()

        val response = client.send(
            request,
            HttpResponse.BodyHandlers.ofString(),
        )

        if (response.statusCode() !in 200..299) {
            error(
                "Ollama embedding request failed. " +
                        "Status: ${response.statusCode()}, body: ${response.body()}",
            )
        }

        val body = json.decodeFromString<OllamaEmbedResponse>(response.body())

        return body.embeddings.firstOrNull()
            ?: error("Ollama response does not contain embeddings")
    }
}

@Serializable
private data class OllamaEmbedRequest(
    val model: String,
    val input: String,
    val truncate: Boolean = true,
)

@Serializable
private data class OllamaEmbedResponse(
    val model: String? = null,
    val embeddings: List<List<Float>> = emptyList(),
)