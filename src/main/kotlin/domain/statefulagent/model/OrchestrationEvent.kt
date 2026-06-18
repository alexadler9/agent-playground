package domain.statefulagent.model

sealed interface OrchestrationEvent {

    data class StageStarted(
        val stage: TaskStage,
    ) : OrchestrationEvent

    data class StageFinished(
        val stage: TaskStage,
        val answer: String,
        val nextState: TaskState,
    ) : OrchestrationEvent

    data class Finished(
        val finalState: TaskState,
    ) : OrchestrationEvent
}