package domain.rag

import domain.llm.LlmGateway
import domain.model.AgentConfig

/**
 * Сравнивает обычный RAG и улучшенный RAG.
 *
 * Baseline берёт top-K по исходному вопросу.
 * Improved сначала переписывает query, потом фильтрует и rerank-ит кандидатов.
 */
class RagRerankingAnswerAgent(
    private val llmGateway: LlmGateway,
    private val agentConfig: AgentConfig,
    private val embeddingGateway: EmbeddingGateway,
    private val queryRewriter: RagQueryRewriter,
) {

    private val promptBuilder = RagPromptBuilder()

    suspend fun compare(
        index: RagIndex,
        question: String,
        settings: RagRetrievalSettings = RagRetrievalSettings(),
    ): RagRerankingAnswerComparison {
        val rewrittenQuery = queryRewriter.rewrite(question)

        val retrieval = RagImprovedRetriever(
            embeddingGateway = embeddingGateway,
        ).retrieve(
            index = index,
            question = question,
            rewrittenQuery = rewrittenQuery,
            settings = settings,
        )

        val baselineContext = retrieval.baselineChunks.map { result ->
            result.chunk.toContextChunk(score = result.score)
        }

        val improvedContext = retrieval.selectedAfterFilter.map { candidate ->
            candidate.chunk.toContextChunk(score = candidate.finalScore)
        }

        val baselineAnswer = answerWithChunks(
            question = question,
            mode = "baseline-rag",
            chunks = baselineContext,
            emptyAnswer = "Baseline retrieval не нашёл chunks для ответа.",
        )

        val improvedAnswer = answerWithChunks(
            question = question,
            mode = "improved-rag",
            chunks = improvedContext,
            emptyAnswer = "Improved retrieval не нашёл chunks выше порога релевантности.",
        )

        return RagRerankingAnswerComparison(
            question = question,
            retrieval = retrieval,
            baselineAnswer = baselineAnswer,
            improvedAnswer = improvedAnswer,
        )
    }

    private suspend fun answerWithChunks(
        question: String,
        mode: String,
        chunks: List<RagContextChunk>,
        emptyAnswer: String,
    ): RagAnswerResult {
        if (chunks.isEmpty()) {
            return RagAnswerResult(
                question = question,
                mode = mode,
                answer = emptyAnswer,
                usedRag = true,
                skippedLlm = true,
                selectedChunks = emptyList(),
            )
        }

        val reply = llmGateway.sendMessages(
            config = agentConfig,
            messages = promptBuilder.buildRagMessages(
                question = question,
                chunks = chunks,
            ),
        )

        return RagAnswerResult(
            question = question,
            mode = mode,
            answer = reply.message.content.trim(),
            usedRag = true,
            skippedLlm = false,
            selectedChunks = chunks,
        )
    }

    private fun IndexedChunk.toContextChunk(
        score: Double,
    ): RagContextChunk {
        return RagContextChunk(
            chunkId = chunkId,
            source = source,
            title = title,
            section = section,
            score = score,
            text = text,
        )
    }
}