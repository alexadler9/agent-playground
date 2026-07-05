package domain.rag


import domain.llm.LlmGateway
import domain.model.AgentConfig
import kotlinx.serialization.json.Json

/**
 * Мини-чат с RAG и памятью задачи.
 *
 * На каждый ход:
 * - обновляет task memory;
 * - переписывает query для смешанного корпуса;
 * - ищет chunks в RAG index;
 * - фильтрует/rerank-ит результаты;
 * - генерирует grounded answer с sources и quotes.
 */
class RagChatAgent(
    private val llmGateway: LlmGateway,
    private val answerConfig: AgentConfig,
    private val embeddingGateway: EmbeddingGateway,
    private val queryRewriter: RagQueryRewriter,
    private val taskMemoryUpdater: RagChatTaskMemoryUpdater,
    json: Json,
) {

    private val promptBuilder = RagChatPromptBuilder()
    private val parser = RagGroundedAnswerParser(json)
    private val validator = RagGroundingValidator()

    suspend fun handleUserMessage(
        index: RagIndex,
        state: RagChatSessionState,
        userMessage: String,
        settings: RagRetrievalSettings = RagRetrievalSettings(),
    ): RagChatTurnResult {
        val updatedMemory = taskMemoryUpdater.update(
            currentMemory = state.taskMemory,
            recentHistory = state.history,
            userMessage = userMessage,
        )

        val retrievalQuery = buildRetrievalQuery(
            userMessage = userMessage,
            taskMemory = updatedMemory,
        )

        val rewrittenQuery = queryRewriter.rewrite(retrievalQuery)

        val retrieval = RagImprovedRetriever(
            embeddingGateway = embeddingGateway,
        ).retrieve(
            index = index,
            question = userMessage,
            rewrittenQuery = rewrittenQuery,
            settings = settings,
        )

        val selectedChunks = retrieval.selectedAfterFilter.map { candidate ->
            candidate.chunk.toContextChunk(score = candidate.finalScore)
        }

        val topSimilarityScore = retrieval.selectedAfterFilter
            .maxOfOrNull { candidate -> candidate.similarityScore }
            ?: 0.0

        val answer = if (selectedChunks.isEmpty() || topSimilarityScore < settings.minSimilarityScore) {
            unknownAnswer(
                question = userMessage,
                rewrittenQuery = rewrittenQuery,
                selectedChunks = selectedChunks,
                reason = "No selected chunks above min similarity score ${settings.minSimilarityScore}.",
            )
        } else {
            generateAnswer(
                question = userMessage,
                rewrittenQuery = rewrittenQuery,
                taskMemory = updatedMemory,
                history = state.history,
                selectedChunks = selectedChunks,
            )
        }

        val nextHistory = state.history + listOf(
            RagChatMessage(
                role = RagChatRole.USER,
                content = userMessage,
            ),
            RagChatMessage(
                role = RagChatRole.ASSISTANT,
                content = answer.answer,
            ),
        )

        return RagChatTurnResult(
            answer = answer,
            state = RagChatSessionState(
                history = nextHistory,
                taskMemory = updatedMemory,
            ),
        )
    }

    private suspend fun generateAnswer(
        question: String,
        rewrittenQuery: String,
        taskMemory: RagChatTaskMemory,
        history: List<RagChatMessage>,
        selectedChunks: List<RagContextChunk>,
    ): RagGroundedAnswerResult {
        val rawResponse = llmGateway.sendMessages(
            config = answerConfig,
            messages = promptBuilder.buildMessages(
                userMessage = question,
                taskMemory = taskMemory,
                history = history,
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

    private fun unknownAnswer(
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

    private fun buildRetrievalQuery(
        userMessage: String,
        taskMemory: RagChatTaskMemory,
    ): String {
        return buildString {
            appendLine(userMessage)

            if (taskMemory.goal.isNotBlank()) {
                appendLine()
                appendLine("Goal: ${taskMemory.goal}")
            }

            if (taskMemory.terms.isNotEmpty()) {
                appendLine()
                appendLine("Important terms:")
                taskMemory.terms.forEach { term ->
                    appendLine("- $term")
                }
            }

            if (taskMemory.constraints.isNotEmpty()) {
                appendLine()
                appendLine("Constraints:")
                taskMemory.constraints.forEach { constraint ->
                    appendLine("- $constraint")
                }
            }
        }
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