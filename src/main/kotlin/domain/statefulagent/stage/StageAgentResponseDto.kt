package domain.statefulagent.stage

import domain.statefulagent.model.ExpectedAction
import domain.statefulagent.model.StageAgentResult
import domain.statefulagent.model.TaskStage
import kotlinx.serialization.Serializable

@Serializable
data class StageAgentResponseDto(
    val answer: String,
    val suggestedNextStage: TaskStage? = null,
    val nextCurrentStep: String,
    val nextExpectedAction: ExpectedAction,
)

fun StageAgentResponseDto.toDomain(): StageAgentResult {
    return StageAgentResult(
        answer = answer,
        suggestedNextStage = suggestedNextStage,
        nextCurrentStep = nextCurrentStep,
        nextExpectedAction = nextExpectedAction,
    )
}