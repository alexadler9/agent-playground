package domain.statefulagent.stage.context

import domain.statefulagent.model.AssistantMemory
import domain.statefulagent.model.InvariantSet
import domain.statefulagent.model.TaskArtifact
import domain.statefulagent.model.TaskStage
import domain.statefulagent.model.TaskState

/**
 * Данные, из которых собирается prompt для конкретного stage-agent-а.
 */
data class StageContextRequest(
    /** Текущий stage, для которого собирается контекст. */
    val stage: TaskStage,

    /** Ролевой system prompt конкретного stage-agent-а. */
    val stageSystemPrompt: String,

    /** Память ассистента: краткосрочная, рабочая и долговременная. */
    val memory: AssistantMemory,

    /** Текущее lifecycle-состояние задачи. */
    val taskState: TaskState,

    /** Сохранённые результаты предыдущих этапов. */
    val artifacts: Map<TaskStage, TaskArtifact>,

    /** Проектные инварианты. */
    val invariants: InvariantSet,
)