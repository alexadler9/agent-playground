package domain.statefulagent.stage

import domain.statefulagent.model.AssistantMemory
import domain.statefulagent.model.InvariantSet
import domain.statefulagent.model.StageAgentResult
import domain.statefulagent.model.TaskArtifact
import domain.statefulagent.model.TaskStage
import domain.statefulagent.model.TaskState

interface StageAgent {

    val stage: TaskStage

    suspend fun handle(
        memory: AssistantMemory,
        taskState: TaskState,
        artifacts: Map<TaskStage, TaskArtifact>,
        invariants: InvariantSet,
        userMessage: String,
    ): StageAgentResult
}