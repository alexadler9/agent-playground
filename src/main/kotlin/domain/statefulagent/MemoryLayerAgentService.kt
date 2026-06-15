package domain.statefulagent

import domain.llm.LlmGateway
import domain.memory.SessionHistoryRepository
import domain.model.AgentConfig
import domain.model.AgentReply
import domain.model.ChatMessage
import domain.model.ChatRole
import domain.model.ChatSession
import domain.statefulagent.memory.LongTermMemoryRepository
import domain.statefulagent.memory.TaskContextRepository
import domain.statefulagent.memory.TaskContextUpdater
import domain.statefulagent.model.AssistantMemory
import kotlin.time.measureTimedValue

class MemoryLayerAgentService(
    private val session: ChatSession,
    private val config: AgentConfig,
    private val llmGateway: LlmGateway,
    private val sessionHistoryRepository: SessionHistoryRepository,
    private val taskContextRepository: TaskContextRepository,
    private val longTermMemoryRepository: LongTermMemoryRepository,
    private val taskContextUpdater: TaskContextUpdater,
    private val promptBuilder: MemoryLayerPromptBuilder,
) {

    suspend fun sendMessage(text: String): AgentReply {
        val previousHistory = sessionHistoryRepository.getMessages(session)

        val currentTaskContext = taskContextRepository.getTaskContext()

        val updatedTaskContext = taskContextUpdater.updateTaskContext(
            currentContext = currentTaskContext,
            userMessage = text,
        )

        taskContextRepository.saveTaskContext(updatedTaskContext)

        val memory = AssistantMemory(
            shortTermMemory = previousHistory,
            workingMemory = updatedTaskContext,
            longTermMemory = longTermMemoryRepository.getLongTermMemory(),
        )

        val prompt = promptBuilder.buildPrompt(
            config = config,
            memory = memory,
            userMessage = text,
        )

        val measuredReply = measureTimedValue {
            llmGateway.sendMessages(
                messages = prompt,
                config = config,
            )
        }

        val reply = measuredReply.value.copy(
            responseTimeMs = measuredReply.duration.inWholeMilliseconds,
        )

        val userMessage = ChatMessage(
            role = ChatRole.USER,
            content = text,
        )

        sessionHistoryRepository.appendMessage(
            session = session,
            message = userMessage,
        )

        sessionHistoryRepository.appendMessage(
            session = session,
            message = reply.message,
        )

        return reply
    }

    suspend fun getMemory(): AssistantMemory {
        return AssistantMemory(
            shortTermMemory = sessionHistoryRepository.getMessages(session),
            workingMemory = taskContextRepository.getTaskContext(),
            longTermMemory = longTermMemoryRepository.getLongTermMemory(),
        )
    }

    suspend fun getHistory(): List<ChatMessage> {
        return sessionHistoryRepository.getMessages(session)
    }

    suspend fun clearShortTermMemory() {
        sessionHistoryRepository.clear(session)
    }

    suspend fun clearWorkingMemory() {
        taskContextRepository.clear()
    }
}