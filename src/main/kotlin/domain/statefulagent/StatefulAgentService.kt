package domain.statefulagent

import domain.memory.SessionHistoryRepository
import domain.model.ChatMessage
import domain.model.ChatRole
import domain.model.ChatSession
import domain.statefulagent.memory.*
import domain.statefulagent.model.*
import domain.statefulagent.orchestration.OrchestrationContextPreparer
import domain.statefulagent.orchestration.OrchestrationStepRunner
import domain.statefulagent.stage.StageAgent
import domain.statefulagent.stage.StageAgentResultNormalizer
import domain.statefulagent.stage.StageArtifactSaver
import domain.statefulagent.stage.StageRunner
import domain.statefulagent.state.TaskStateResolver
import domain.statefulagent.validation.TaskTransitionValidator

class StatefulAgentService(
    private val session: ChatSession,
    private val sessionHistoryRepository: SessionHistoryRepository,
    private val taskContextRepository: TaskContextRepository,
    private val longTermMemoryRepository: LongTermMemoryRepository,
    private val taskContextUpdater: TaskContextUpdater,
    private val taskStateRepository: TaskStateRepository,
    private val taskArtifactRepository: TaskArtifactRepository,
    private val stageArtifactSaver: StageArtifactSaver = StageArtifactSaver(
        taskArtifactRepository = taskArtifactRepository,
    ),
    private val invariantRepository: InvariantRepository,
    private val orchestrationContextPreparer: OrchestrationContextPreparer = OrchestrationContextPreparer(
        session = session,
        sessionHistoryRepository = sessionHistoryRepository,
        taskContextRepository = taskContextRepository,
        longTermMemoryRepository = longTermMemoryRepository,
        taskContextUpdater = taskContextUpdater,
        invariantRepository = invariantRepository,
    ),
    private val transitionValidator: TaskTransitionValidator,
    private val stageResultNormalizer: StageAgentResultNormalizer = StageAgentResultNormalizer(),
    private val taskStateResolver: TaskStateResolver = TaskStateResolver(
        transitionValidator = transitionValidator,
    ),
    private val stageAgents: List<StageAgent>,
    private val stageRunner: StageRunner = StageRunner(
        stageAgents = stageAgents,
        stageAgentResultNormalizer = stageResultNormalizer,
    ),
    private val orchestrationStepRunner: OrchestrationStepRunner = OrchestrationStepRunner(
        taskArtifactRepository = taskArtifactRepository,
        taskStateRepository = taskStateRepository,
        stageRunner = stageRunner,
        stageArtifactSaver = stageArtifactSaver,
        taskStateResolver = taskStateResolver,
    ),
) {

    suspend fun run(
        text: String,
        onEvent: suspend (OrchestrationEvent) -> Unit = {}
    ): OrchestrationResult {
        val preparedContext = orchestrationContextPreparer.prepare(
            userText = text,
        )

        val memory = preparedContext.memory
        val invariants = preparedContext.invariants

        val answers = mutableListOf<String>()
        val reports = mutableListOf<StageReport>()

        var taskState = taskStateRepository.getTaskState()
        var messageForStage = text
        var autoStep = 0

        while (autoStep < MAX_AUTO_STEPS) {
            val stepResult = orchestrationStepRunner.runStep(
                memory = memory,
                taskState = taskState,
                invariants = invariants,
                userMessage = messageForStage,
                onEvent = onEvent,
            )

            reports += StageReport(
                stage = stepResult.nextTaskState.stage,
                currentStep = stepResult.nextTaskState.currentStep,
                expectedAction = stepResult.nextTaskState.expectedAction,
            )

            answers += formatStageAnswer(
                stage = stepResult.startedStage,
                answer = stepResult.stageResult.answer,
                nextState = stepResult.nextTaskState,
            )

            taskState = stepResult.nextTaskState

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

    private companion object {
        const val MAX_AUTO_STEPS = 8
    }
}