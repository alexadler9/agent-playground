package domain.statefulagent.stage

import domain.statefulagent.model.AssistantMemory
import domain.statefulagent.model.InvariantSet
import domain.statefulagent.model.TaskArtifact
import domain.statefulagent.model.TaskStage
import domain.statefulagent.model.TaskState

/**
 * Входные данные для запуска одного stage.
 *
 * Нужны StageRunner-у, чтобы не протаскивать длинный список
 * аргументов между методами StatefulAgentService.
 */
data class StageRunRequest(
    /** Память, доступная агенту на текущем шаге. */
    val memory: AssistantMemory,

    /** Текущее lifecycle-состояние задачи. */
    val taskState: TaskState,

    /** Сохранённые результаты предыдущих stage. */
    val artifacts: Map<TaskStage, TaskArtifact>,

    /** Проектные инварианты. */
    val invariants: InvariantSet,

    /** Сообщение пользователя или auto-continue сообщение. */
    val userMessage: String,
)