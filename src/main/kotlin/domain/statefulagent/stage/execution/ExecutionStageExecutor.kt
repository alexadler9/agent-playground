package domain.statefulagent.stage.execution

import domain.statefulagent.model.StageAgentResult
import domain.statefulagent.stage.StageRunRequest

/**
 * Выполняет логику EXECUTION stage.
 *
 * Сейчас реализация может быть одиночным LLM-agent-ом.
 * Позже сюда можно подставить swarm: Developer -> Reviewer -> QA -> Synthesizer.
 */
interface ExecutionStageExecutor {

    /**
     * Выполняет утверждённый план или исправляет результат по замечаниям validation.
     */
    suspend fun execute(
        request: StageRunRequest,
    ): StageAgentResult
}