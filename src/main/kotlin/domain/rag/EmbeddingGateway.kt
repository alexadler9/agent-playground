package domain.rag

interface EmbeddingGateway {

    val modelName: String

    suspend fun embed(text: String): List<Float>
}