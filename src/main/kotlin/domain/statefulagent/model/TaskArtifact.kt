package domain.statefulagent.model

/**
 * Сохранённый результат отдельного stage.
 *
 * Artifact отделён от TaskState, чтобы состояние lifecycle-а
 * не разрасталось текстами планов, реализаций и проверок.
 */
data class TaskArtifact(
    /** Stage, который создал artifact. */
    val stage: TaskStage,

    /** Основное содержимое результата stage. */
    val content: String,

    /** Время создания artifact. */
    val createdAtMillis: Long,
)