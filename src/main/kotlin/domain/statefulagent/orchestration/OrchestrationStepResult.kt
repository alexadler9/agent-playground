package domain.statefulagent.orchestration

import domain.statefulagent.model.StageAgentResult
import domain.statefulagent.model.TaskStage
import domain.statefulagent.model.TaskState

/**
 * Результат одного шага orchestration loop.
 *
 * Один шаг = запуск текущего stage, сохранение artifact,
 * расчёт и сохранение следующего TaskState.
 */
data class OrchestrationStepResult(
    /** Stage, который был запущен на этом шаге. */
    val startedStage: TaskStage,

    /** Результат работы stage-agent-а. */
    val stageResult: StageAgentResult,

    /** Новое состояние задачи после обработки результата. */
    val nextTaskState: TaskState,
)