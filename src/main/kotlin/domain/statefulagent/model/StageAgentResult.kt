package domain.statefulagent.model

data class StageAgentResult(
    val answer: String,
    val suggestedNextStage: TaskStage?,
    val nextCurrentStep: String,
    val nextExpectedAction: ExpectedAction,
    val shouldSaveArtifact: Boolean = true,
)