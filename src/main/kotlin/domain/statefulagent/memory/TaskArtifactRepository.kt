package domain.statefulagent.memory

import domain.statefulagent.model.TaskArtifact
import domain.statefulagent.model.TaskStage

interface TaskArtifactRepository {

    suspend fun getLatestArtifact(stage: TaskStage): TaskArtifact?

    suspend fun getArtifacts(): Map<TaskStage, TaskArtifact>

    suspend fun saveArtifact(artifact: TaskArtifact)

    suspend fun removeArtifactsFromStage(stage: TaskStage)

    suspend fun clear()
}