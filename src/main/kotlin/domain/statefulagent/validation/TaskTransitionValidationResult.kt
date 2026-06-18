package domain.statefulagent.validation

sealed interface TaskTransitionValidationResult {

    data object Valid : TaskTransitionValidationResult

    data class Invalid(
        val reason: String,
    ) : TaskTransitionValidationResult
}