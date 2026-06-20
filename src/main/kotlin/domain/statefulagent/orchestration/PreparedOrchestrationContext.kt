package domain.statefulagent.orchestration

import domain.statefulagent.model.AssistantMemory
import domain.statefulagent.model.InvariantSet

/**
 * Подготовленный контекст одного запуска оркестратора.
 *
 * Содержит данные, которые нужны основному orchestration loop:
 * память ассистента и проектные инварианты.
 */
data class PreparedOrchestrationContext(
    /** Память, доступная stage-agent-ам на текущем запуске. */
    val memory: AssistantMemory,

    /** Проектные инварианты, обязательные для всех stage-agent-ов. */
    val invariants: InvariantSet,
)