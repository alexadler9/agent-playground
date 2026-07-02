package domain.rag

import domain.llm.LlmGateway
import domain.model.AgentConfig
import kotlinx.serialization.json.Json

/**
 * Генерирует RAG-ответ с обязательными источниками и цитатами.
 *
 * Если retrieval не нашёл достаточно релевантного контекста,
 * LLM не вызывается: сразу возвращаем режим "не знаю".
 */
class RagGroundedAnswerAgent(
    private val llmGateway: LlmGateway,
    private val agentConfig: AgentConfig,
    private val embeddingGateway: EmbeddingGateway,
    private val queryRewriter: RagQueryRewriter,
    json: Json,
) {

    private val promptBuilder = RagGroundedPromptBuilder()
    private val parser = RagGroundedAnswerParser(json)
    private val validator = RagGroundingValidator()

    suspend fun answer(
        index: RagIndex,
        question: String,
        settings: RagRetrievalSettings = RagRetrievalSettings(),
    ): RagGroundedAnswerResult {
        val rewrittenQuery = queryRewriter.rewrite(question)

        val retrieval = RagImprovedRetriever(
            embeddingGateway = embeddingGateway,
        ).retrieve(
            index = index,
            question = question,
            rewrittenQuery = rewrittenQuery,
            settings = settings,
        )

        val selectedChunks = retrieval.selectedAfterFilter.map { candidate ->
            candidate.chunk.toContextChunk(score = candidate.finalScore)
        }

        val topSimilarityScore = retrieval.selectedAfterFilter
            .maxOfOrNull { candidate -> candidate.similarityScore }
            ?: 0.0

        if (selectedChunks.isEmpty() || topSimilarityScore < settings.minSimilarityScore) {
            return unknownResult(
                question = question,
                rewrittenQuery = rewrittenQuery,
                selectedChunks = selectedChunks,
                reason = "No selected chunks above min similarity score ${settings.minSimilarityScore}.",
            )
        }

        val rawResponse = llmGateway.sendMessages(
            config = agentConfig,
            messages = promptBuilder.buildMessages(
                question = question,
                chunks = selectedChunks,
            ),
        ).message.content.trim()

        val parsedResponse = try {
            parser.parse(rawResponse)
        } catch (error: Exception) {
            return RagGroundedAnswerResult(
                question = question,
                rewrittenQuery = rewrittenQuery,
                isKnown = false,
                answer = rawResponse,
                sources = emptyList(),
                quotes = emptyList(),
                selectedChunks = selectedChunks,
                validation = validator.parseFailed(
                    errorMessage = error.message ?: "Failed to parse grounded answer JSON.",
                ),
                skippedLlm = false,
                unknownReason = "Failed to parse LLM JSON response.",
            )
        }

        val validation = validator.validate(
            response = parsedResponse,
            selectedChunks = selectedChunks,
        )

        return RagGroundedAnswerResult(
            question = question,
            rewrittenQuery = rewrittenQuery,
            isKnown = validation.isValid,
            answer = parsedResponse.answer,
            sources = parsedResponse.sources.map { source ->
                source.toGroundedSource(selectedChunks)
            },
            quotes = parsedResponse.quotes.map { quote ->
                quote.toGroundedQuote()
            },
            selectedChunks = selectedChunks,
            validation = validation,
            skippedLlm = false,
            unknownReason = null,
        )
    }

    private fun unknownResult(
        question: String,
        rewrittenQuery: String,
        selectedChunks: List<RagContextChunk>,
        reason: String,
    ): RagGroundedAnswerResult {
        return RagGroundedAnswerResult(
            question = question,
            rewrittenQuery = rewrittenQuery,
            isKnown = false,
            answer = "Не знаю: в локальном индексе недостаточно релевантного контекста для надежного ответа. Пожалуйста, уточните вопрос или добавьте документы в базу знаний",
            sources = emptyList(),
            quotes = emptyList(),
            selectedChunks = selectedChunks,
            validation = RagGroundingValidation(
                parseSucceeded = true,
                hasSources = false,
                hasQuotes = false,
                sourcesExistInSelectedChunks = false,
                quotesExistInChunks = false,
                isValid = false,
                errors = listOf(reason),
            ),
            skippedLlm = true,
            unknownReason = reason,
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

    private fun RagGroundedSourceDto.toGroundedSource(
        selectedChunks: List<RagContextChunk>,
    ): RagGroundedSource {
        val score = selectedChunks
            .firstOrNull { chunk -> chunk.chunkId == chunkId }
            ?.score
            ?: 0.0

        return RagGroundedSource(
            source = source,
            section = section,
            chunkId = chunkId,
            score = score,
        )
    }

    private fun RagGroundedQuoteDto.toGroundedQuote(): RagGroundedQuote {
        return RagGroundedQuote(
            source = source,
            section = section,
            chunkId = chunkId,
            text = text,
        )
    }
}