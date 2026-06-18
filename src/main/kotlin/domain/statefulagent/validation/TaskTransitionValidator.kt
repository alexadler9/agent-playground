package domain.statefulagent.validation

import domain.statefulagent.model.TaskStage

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

    fun canTransition(
        from: TaskStage,
        to: TaskStage,
    ): Boolean {
        return validate(from, to) is TaskTransitionValidationResult.Valid
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