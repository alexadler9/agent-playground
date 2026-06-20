package domain.statefulagent.orchestration

import domain.memory.SessionHistoryRepository
import domain.model.ChatMessage
import domain.model.ChatRole
import domain.model.ChatSession
import domain.statefulagent.memory.InvariantRepository
import domain.statefulagent.memory.LongTermMemoryRepository
import domain.statefulagent.memory.TaskContextRepository
import domain.statefulagent.memory.TaskContextUpdater
import domain.statefulagent.model.AssistantMemory

/**
 * Подготавливает данные для одного запуска StatefulAgentService.
 *
 * Отвечает за чтение истории, обновление рабочей памяти,
 * загрузку долговременной памяти и проектных инвариантов.
 *
 * Не запускает stage-agent-ов и не меняет TaskState.
 */
class OrchestrationContextPreparer(
    private val session: ChatSession,
    private val sessionHistoryRepository: SessionHistoryRepository,
    private val taskContextRepository: TaskContextRepository,
    private val longTermMemoryRepository: LongTermMemoryRepository,
    private val taskContextUpdater: TaskContextUpdater,
    private val invariantRepository: InvariantRepository,
) {

    /**
     * Подготавливает memory/invariants и сохраняет входящее сообщение пользователя в историю.
     */
    suspend fun prepare(
        userText: String,
    ): PreparedOrchestrationContext {
        val previousHistory = sessionHistoryRepository.getMessages(session)
        val invariants = invariantRepository.getInvariants()

        val currentTaskContext = taskContextRepository.getTaskContext()
        val updatedTaskContext = taskContextUpdater.updateTaskContext(
            currentContext = currentTaskContext,
            userMessage = userText,
            invariants = invariants,
        )

        taskContextRepository.saveTaskContext(updatedTaskContext)

        val memory = AssistantMemory(
            shortTermMemory = previousHistory,
            workingMemory = updatedTaskContext,
            longTermMemory = longTermMemoryRepository.getLongTermMemory(),
        )

        val userMessage = ChatMessage(
            role = ChatRole.USER,
            content = userText,
        )

        sessionHistoryRepository.appendMessage(
            session = session,
            message = userMessage,
        )

        return PreparedOrchestrationContext(
            memory = memory,
            invariants = invariants,
        )
    }
}