package domain.rag

import domain.llm.LlmGateway
import domain.model.AgentConfig

/**
 * Первый RAG-answer agent.
 *
 * Он умеет сравнить два режима:
 * - обычный ответ модели без локального индекса;
 * - ответ модели после retrieval релевантных чанков.
 */
class RagAnswerAgent(
    private val llmGateway: LlmGateway,
    private val agentConfig: AgentConfig,
    private val embeddingGateway: EmbeddingGateway,
    private val promptBuilder: RagPromptBuilder = RagPromptBuilder(),
) {

    suspend fun compare(
        index: RagIndex,
        question: String,
    ): RagComparisonResult {
        return RagComparisonResult(
            question = question,
            noRagAnswer = answerWithoutRag(question),
            ragAnswer = answerWithRag(
                index = index,
                question = question,
            ),
        )
    }

    private suspend fun answerWithoutRag(
        question: String,
    ): RagAnswerResult {
        val reply = llmGateway.sendMessages(
            config = agentConfig,
            messages = promptBuilder.buildNoRagMessages(question),
        )

        return RagAnswerResult(
            question = question,
            mode = "no-rag",
            answer = reply.message.content,
            usedRag = false,
            skippedLlm = false,
            selectedChunks = emptyList(),
        )
    }

    private suspend fun answerWithRag(
        index: RagIndex,
        question: String,
        topK: Int = 5,
        minRelevantScore: Double = 0.25,
    ): RagAnswerResult {
        val searchResults = RagIndexSearcher(
            embeddingGateway = embeddingGateway,
        ).search(
            index = index,
            query = question,
            topK = topK,
        )

        val selectedChunks = searchResults
            .filter { result -> result.score >= minRelevantScore }
            .map { result ->
                RagContextChunk(
                    chunkId = result.chunk.chunkId,
                    source = result.chunk.source,
                    title = result.chunk.title,
                    section = result.chunk.section,
                    score = result.score,
                    text = result.chunk.text,
                )
            }

        // Если retrieval не нашёл достаточно близких чанков,
        // не отправляем пустой контекст в LLM и не провоцируем галлюцинации.
        if (selectedChunks.isEmpty()) {
            return RagAnswerResult(
                question = question,
                mode = "rag",
                answer = "В локальном индексе не найдено достаточно релевантных данных для ответа. Я не буду додумывать ответ без источников",
                usedRag = true,
                skippedLlm = true,
                selectedChunks = emptyList(),
            )
        }

        val reply = llmGateway.sendMessages(
            config = agentConfig,
            messages = promptBuilder.buildRagMessages(
                question = question,
                chunks = selectedChunks,
            ),
        )

        return RagAnswerResult(
            question = question,
            mode = "rag",
            answer = reply.message.content,
            usedRag = true,
            skippedLlm = false,
            selectedChunks = selectedChunks,
        )
    }
}