package domain.statefulagent.validation

import domain.statefulagent.model.ExpectedAction
import domain.statefulagent.model.StageAgentResult
import domain.statefulagent.model.TaskArtifact
import domain.statefulagent.model.TaskStage
import domain.statefulagent.model.TaskState
import domain.statefulagent.model.TransitionReason

class TaskTransitionValidator {

    fun validate(
        from: TaskStage,
        to: TaskStage,
    ): TaskTransitionValidationResult {
        val allowedStages = allowedTransitions[from].orEmpty()

        return if (to in allowedStages) {
            TaskTransitionValidationResult.Valid
        } else {
            TaskTransitionValidationResult.Invalid(
                reason = "Переход $from -> $to запрещён. Разрешённые переходы: ${allowedStages.joinToString()}",
            )
        }
    }

    fun validate(
        currentState: TaskState,
        stageResult: StageAgentResult,
        artifacts: Map<TaskStage, TaskArtifact>,
    ): TaskTransitionValidationResult {
        val nextStage = stageResult.suggestedNextStage
            ?: return TaskTransitionValidationResult.Valid

        if (nextStage == currentState.stage) {
            return TaskTransitionValidationResult.Valid
        }

        val graphValidation = validate(
            from = currentState.stage,
            to = nextStage,
        )

        if (graphValidation is TaskTransitionValidationResult.Invalid) {
            return graphValidation
        }

        return validatePreconditions(
            currentState = currentState,
            stageResult = stageResult,
            artifacts = artifacts,
        )
    }

    private fun validatePreconditions(
        currentState: TaskState,
        stageResult: StageAgentResult,
        artifacts: Map<TaskStage, TaskArtifact>,
    ): TaskTransitionValidationResult {
        val nextStage = stageResult.suggestedNextStage
            ?: return TaskTransitionValidationResult.Valid

        return when {
            currentState.stage == TaskStage.PLANNING &&
                    nextStage == TaskStage.EXECUTION -> {
                validatePlanningToExecution(
                    currentState = currentState,
                    stageResult = stageResult,
                    artifacts = artifacts,
                )
            }

            currentState.stage == TaskStage.EXECUTION &&
                    nextStage == TaskStage.VALIDATION -> {
                validateExecutionToValidation(
                    artifacts = artifacts,
                )
            }

            currentState.stage == TaskStage.VALIDATION &&
                    nextStage == TaskStage.DONE -> {
                validateValidationToDone(
                    stageResult = stageResult,
                )
            }

            else -> TaskTransitionValidationResult.Valid
        }
    }

    private fun validatePlanningToExecution(
        currentState: TaskState,
        stageResult: StageAgentResult,
        artifacts: Map<TaskStage, TaskArtifact>,
    ): TaskTransitionValidationResult {
        if (stageResult.transitionReason != TransitionReason.USER_APPROVED_PLAN) {
            return TaskTransitionValidationResult.Invalid(
                reason = "Переход PLANNING -> EXECUTION возможен только после явного подтверждения плана пользователем",
            )
        }

        if (currentState.expectedAction != ExpectedAction.APPROVE_PLAN) {
            return TaskTransitionValidationResult.Invalid(
                reason = "Переход PLANNING -> EXECUTION невозможен: текущий этап не ожидал подтверждения плана",
            )
        }

        if (artifacts[TaskStage.PLANNING] == null) {
            return TaskTransitionValidationResult.Invalid(
                reason = "Переход PLANNING -> EXECUTION невозможен: отсутствует сохранённый planning artifact",
            )
        }

        return TaskTransitionValidationResult.Valid
    }

    private fun validateExecutionToValidation(
        artifacts: Map<TaskStage, TaskArtifact>,
    ): TaskTransitionValidationResult {
        if (artifacts[TaskStage.EXECUTION] == null) {
            return TaskTransitionValidationResult.Invalid(
                reason = "Переход EXECUTION -> VALIDATION невозможен: отсутствует execution artifact",
            )
        }

        return TaskTransitionValidationResult.Valid
    }

    private fun validateValidationToDone(
        stageResult: StageAgentResult,
    ): TaskTransitionValidationResult {
        if (stageResult.transitionReason != TransitionReason.VALIDATION_ACCEPTED) {
            return TaskTransitionValidationResult.Invalid(
                reason = "Переход VALIDATION -> DONE возможен только после принятия результата validation-этапом",
            )
        }

        return TaskTransitionValidationResult.Valid
    }

    private companion object {
        val allowedTransitions: Map<TaskStage, Set<TaskStage>> = mapOf(
            TaskStage.PLANNING to setOf(TaskStage.EXECUTION),
            TaskStage.EXECUTION to setOf(TaskStage.VALIDATION),
            TaskStage.VALIDATION to setOf(
                TaskStage.DONE,
                TaskStage.EXECUTION,
            ),
            TaskStage.DONE to emptySet(),
        )
    }
}