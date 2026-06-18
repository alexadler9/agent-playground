package domain.statefulagent.model

data class TaskArtifact(
    val stage: TaskStage,
    val content: String,
    val createdAtMillis: Long,
)