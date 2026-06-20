package domain.statefulagent.model

data class StageAgentResult(
    val answer: String,
    val suggestedNextStage: TaskStage?,
    val nextCurrentStep: String,
    val nextExpectedAction: ExpectedAction,
    val transitionReason: TransitionReason = TransitionReason.AGENT_SUGGESTED,
    val shouldSaveArtifact: Boolean = true,
)