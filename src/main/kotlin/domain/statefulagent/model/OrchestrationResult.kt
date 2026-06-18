package domain.statefulagent.model

data class OrchestrationResult(
    val answer: String,
    val finalState: TaskState,
    val stageReports: List<StageReport>,
)

data class StageReport(
    val stage: TaskStage,
    val currentStep: String,
    val expectedAction: ExpectedAction,
)