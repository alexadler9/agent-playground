package domain.statefulagent.orchestration

import domain.statefulagent.memory.TaskArtifactRepository
import domain.statefulagent.memory.TaskStateRepository
import domain.statefulagent.model.AssistantMemory
import domain.statefulagent.model.InvariantSet
import domain.statefulagent.model.OrchestrationEvent
import domain.statefulagent.model.TaskState
import domain.statefulagent.stage.StageArtifactSaver
import domain.statefulagent.stage.StageRunRequest
import domain.statefulagent.stage.StageRunner
import domain.statefulagent.state.TaskStateResolver

/**
 * Выполняет один шаг orchestration loop.
 *
 * Не управляет всем циклом задачи.
 * Только запускает текущий stage и применяет его результат к TaskState.
 */
class OrchestrationStepRunner(
    private val taskArtifactRepository: TaskArtifactRepository,
    private val taskStateRepository: TaskStateRepository,
    private val stageRunner: StageRunner,
    private val stageArtifactSaver: StageArtifactSaver,
    private val taskStateResolver: TaskStateResolver,
) {

    /**
     * Запускает текущий stage и сохраняет результат шага.
     */
    suspend fun runStep(
        memory: AssistantMemory,
        taskState: TaskState,
        invariants: InvariantSet,
        userMessage: String,
        onEvent: suspend (OrchestrationEvent) -> Unit,
    ): OrchestrationStepResult {
        val artifacts = taskArtifactRepository.getArtifacts()

        onEvent(
            OrchestrationEvent.StageStarted(
                stage = taskState.stage,
            ),
        )

        val stageResult = stageRunner.run(
            StageRunRequest(
                memory = memory,
                taskState = taskState,
                artifacts = artifacts,
                invariants = invariants,
                userMessage = userMessage,
            ),
        )

        stageArtifactSaver.saveIfNeeded(
            currentStage = taskState.stage,
            stageResult = stageResult,
        )

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

        return OrchestrationStepResult(
            startedStage = taskState.stage,
            stageResult = stageResult,
            nextTaskState = nextTaskState,
        )
    }
}