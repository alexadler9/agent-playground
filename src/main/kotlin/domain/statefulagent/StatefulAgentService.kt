package domain.statefulagent

import domain.memory.SessionHistoryRepository
import domain.model.ChatMessage
import domain.model.ChatRole
import domain.model.ChatSession
import domain.statefulagent.memory.InvariantRepository
import domain.statefulagent.memory.LongTermMemoryRepository
import domain.statefulagent.memory.TaskArtifactRepository
import domain.statefulagent.memory.TaskContextRepository
import domain.statefulagent.memory.TaskContextUpdater
import domain.statefulagent.memory.TaskStateRepository
import domain.statefulagent.model.AssistantMemory
import domain.statefulagent.model.ExpectedAction
import domain.statefulagent.model.InvariantSet
import domain.statefulagent.model.OrchestrationEvent
import domain.statefulagent.model.OrchestrationResult
import domain.statefulagent.model.StageAgentResult
import domain.statefulagent.model.StageReport
import domain.statefulagent.model.TaskArtifact
import domain.statefulagent.model.TaskStage
import domain.statefulagent.model.TaskState
import domain.statefulagent.stage.StageAgent
import domain.statefulagent.stage.StageAgentResultNormalizer
import domain.statefulagent.state.TaskStateResolver
import domain.statefulagent.validation.TaskTransitionValidationResult
import domain.statefulagent.validation.TaskTransitionValidator

class StatefulAgentService(
    private val session: ChatSession,
    private val sessionHistoryRepository: SessionHistoryRepository,
    private val taskContextRepository: TaskContextRepository,
    private val longTermMemoryRepository: LongTermMemoryRepository,
    private val taskContextUpdater: TaskContextUpdater,
    private val taskStateRepository: TaskStateRepository,
    private val taskArtifactRepository: TaskArtifactRepository,
    private val invariantRepository: InvariantRepository,
    private val transitionValidator: TaskTransitionValidator,
    private val stageResultNormalizer: StageAgentResultNormalizer = StageAgentResultNormalizer(),
    private val taskStateResolver: TaskStateResolver = TaskStateResolver(
        transitionValidator = transitionValidator,
    ),
    private val stageAgents: List<StageAgent>,
) {

    suspend fun run(
        text: String,
        onEvent: suspend (OrchestrationEvent) -> Unit = {}
    ): OrchestrationResult {
        val previousHistory = sessionHistoryRepository.getMessages(session)
        val invariants = invariantRepository.getInvariants()

        val currentTaskContext = taskContextRepository.getTaskContext()
        val updatedTaskContext = taskContextUpdater.updateTaskContext(
            currentContext = currentTaskContext,
            userMessage = text,
            invariants = invariants
        )
        taskContextRepository.saveTaskContext(updatedTaskContext)

        val memory = AssistantMemory(
            shortTermMemory = previousHistory,
            workingMemory = updatedTaskContext,
            longTermMemory = longTermMemoryRepository.getLongTermMemory(),
        )

        val userMessage = ChatMessage(
            role = ChatRole.USER,
            content = text,
        )

        sessionHistoryRepository.appendMessage(
            session = session,
            message = userMessage,
        )

        val answers = mutableListOf<String>()
        val reports = mutableListOf<StageReport>()

        var taskState = taskStateRepository.getTaskState()
        var messageForStage = text
        var autoStep = 0

        while (autoStep < MAX_AUTO_STEPS) {
            val artifacts = taskArtifactRepository.getArtifacts()

            onEvent(
                OrchestrationEvent.StageStarted(
                    stage = taskState.stage,
                ),
            )

            val stageResult = handleByCurrentState(
                memory = memory,
                taskState = taskState,
                artifacts = artifacts,
                invariants = invariants,
                userMessage = messageForStage,
            )

            if (stageResult.shouldSaveArtifact) {
                taskArtifactRepository.saveArtifact(
                    TaskArtifact(
                        stage = taskState.stage,
                        content = stageResult.answer,
                        createdAtMillis = System.currentTimeMillis(),
                    ),
                )
            }

            val artifactsAfterStage = taskArtifactRepository.getArtifacts()

            val nextTaskState = taskStateResolver.resolve(
                currentTaskState = taskState,
                stageResult = stageResult,
                artifacts = artifactsAfterStage,
            )

            taskStateRepository.saveTaskState(nextTaskState)

            onEvent(
                OrchestrationEvent.StageFinished(
                    stage = taskState.stage,
                    answer = stageResult.answer,
                    nextState = nextTaskState,
                ),
            )

            reports += StageReport(
                stage = nextTaskState.stage,
                currentStep = nextTaskState.currentStep,
                expectedAction = nextTaskState.expectedAction,
            )

            answers += formatStageAnswer(
                stage = taskState.stage,
                answer = stageResult.answer,
                nextState = nextTaskState,
            )

            taskState = nextTaskState

            if (shouldStop(taskState)) {
                break
            }

            messageForStage = buildAutoContinueMessage(taskState)
            autoStep++
        }

        if (!shouldStop(taskState)) {
            onEvent(
                OrchestrationEvent.AutoStepLimitReached(
                    state = taskState,
                ),
            )
        }

        val fullAnswer = answers.joinToString(separator = "\n\n")

        val assistantMessage = ChatMessage(
            role = ChatRole.ASSISTANT,
            content = fullAnswer,
        )

        sessionHistoryRepository.appendMessage(
            session = session,
            message = assistantMessage,
        )

        onEvent(
            OrchestrationEvent.Finished(
                finalState = taskState,
            ),
        )

        return OrchestrationResult(
            answer = fullAnswer,
            finalState = taskState,
            stageReports = reports,
        )
    }

    private fun shouldStop(taskState: TaskState): Boolean {
        if (taskState.stage == TaskStage.DONE) return true

        return when (taskState.expectedAction) {
            ExpectedAction.USER_MESSAGE -> true

            ExpectedAction.APPROVE_PLAN -> true

            ExpectedAction.AUTO_CONTINUE -> false

            ExpectedAction.NONE -> true
        }
    }

    private fun buildAutoContinueMessage(taskState: TaskState): String {
        return when (taskState.stage) {
            TaskStage.PLANNING -> "Системное продолжение: продолжи этап планирования"
            TaskStage.EXECUTION -> "Системное продолжение: выполни этап execution"
            TaskStage.VALIDATION -> "Системное продолжение: выполни этап validation"
            TaskStage.DONE -> "Системное продолжение: задача завершена"
        }
    }

    private fun formatStageAnswer(
        stage: TaskStage,
        answer: String,
        nextState: TaskState,
    ): String {
        return buildString {
            appendLine("Этап: $stage | Текущий шаг: ${nextState.currentStep} | Ожидаемое действие: ${nextState.expectedAction}")
            appendLine()
            appendLine(answer.trim())
        }.trim()
    }

    suspend fun resetTaskState() {
        taskStateRepository.clear()
        taskArtifactRepository.clear()
    }

    suspend fun clearShortTermMemory() {
        sessionHistoryRepository.clear(session)
    }

    suspend fun clearWorkingMemory() {
        taskContextRepository.clear()
    }

    private suspend fun handleByCurrentState(
        memory: AssistantMemory,
        taskState: TaskState,
        artifacts: Map<TaskStage, TaskArtifact>,
        invariants: InvariantSet,
        userMessage: String,
    ): StageAgentResult {
        val systemResult = stageResultNormalizer.buildSystemResultOrNull(
            taskState = taskState,
            userMessage = userMessage,
        )

        if (systemResult != null) {
            return systemResult
        }

        val stageAgent = getStageAgent(taskState.stage)

        val stageResult = stageAgent.handle(
            memory = memory,
            taskState = taskState,
            artifacts = artifacts,
            invariants = invariants,
            userMessage = userMessage,
        )

        return stageResultNormalizer.normalize(
            taskState = taskState,
            stageResult = stageResult,
        )
    }

    private fun getStageAgent(stage: TaskStage): StageAgent {
        return stageAgents.firstOrNull { agent -> agent.stage == stage }
            ?: error("Stage agent is not registered for stage: $stage")
    }

    private companion object {
        const val MAX_AUTO_STEPS = 8
    }
}