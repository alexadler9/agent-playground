package domain.statefulagent.state

import domain.statefulagent.model.ExpectedAction
import domain.statefulagent.model.StageAgentResult
import domain.statefulagent.model.TaskArtifact
import domain.statefulagent.model.TaskStage
import domain.statefulagent.model.TaskState
import domain.statefulagent.validation.TaskTransitionValidationResult
import domain.statefulagent.validation.TaskTransitionValidator

/**
 * Рассчитывает следующий TaskState на основе результата stage-agent-а.
 *
 * Не запускает агентов и не сохраняет состояние.
 * Только применяет lifecycle-правила и transition validation.
 */
class TaskStateResolver(
    private val transitionValidator: TaskTransitionValidator,
) {

    /**
     * Возвращает следующий state.
     *
     * Если переход запрещён, задача остаётся на текущем stage,
     * а currentStep содержит причину отказа.
     */
    fun resolve(
        currentTaskState: TaskState,
        stageResult: StageAgentResult,
        artifacts: Map<TaskStage, TaskArtifact>,
    ): TaskState {
        val transitionValidationResult = transitionValidator.validate(
            currentState = currentTaskState,
            stageResult = stageResult,
            artifacts = artifacts,
        )

        if (transitionValidationResult is TaskTransitionValidationResult.Invalid) {
            return currentTaskState.copy(
                currentStep = transitionValidationResult.reason,
                expectedAction = ExpectedAction.USER_MESSAGE,
            )
        }

        val nextStage = stageResult.suggestedNextStage
            ?: currentTaskState.stage

        return TaskState(
            stage = nextStage,
            currentStep = stageResult.nextCurrentStep,
            expectedAction = stageResult.nextExpectedAction,
        )
    }
}