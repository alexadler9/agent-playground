package domain.rag

class RagIndexBuilder(
    private val embeddingGateway: EmbeddingGateway,
) {

    suspend fun build(
        documents: List<SourceDocument>,
        chunkingStrategy: ChunkingStrategy,
    ): RagIndex {
        val chunks = documents.flatMap { document ->
            chunkingStrategy.chunk(document)
        }

        val indexedChunks = chunks.mapIndexed { index, chunk ->
            println("Embedding chunk ${index + 1}/${chunks.size}: ${chunk.chunkId}")

            IndexedChunk(
                chunkId = chunk.chunkId,
                source = chunk.source,
                title = chunk.title,
                section = chunk.section,
                strategy = chunk.strategy,
                startChar = chunk.startChar,
                endChar = chunk.endChar,
                text = chunk.text,
                embedding = embeddingGateway.embed(chunk.text),
            )
        }

        return RagIndex(
            strategy = chunkingStrategy.name,
            documentsCount = documents.size,
            chunksCount = indexedChunks.size,
            embeddingModel = embeddingGateway.modelName,
            chunks = indexedChunks,
        )
    }
}