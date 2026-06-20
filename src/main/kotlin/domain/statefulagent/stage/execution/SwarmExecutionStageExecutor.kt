package domain.statefulagent.stage.execution

import domain.statefulagent.model.StageAgentResult
import domain.statefulagent.stage.StageRunRequest

/**
 * Будущая swarm-реализация EXECUTION stage.
 *
 * Предполагаемый pipeline:
 * DeveloperAgent -> ReviewerAgent -> QAAgent -> SynthesizerAgent.
 *
 * Пока не используется. Нужна как архитектурная точка расширения.
 */
class SwarmExecutionStageExecutor : ExecutionStageExecutor {

    override suspend fun execute(
        request: StageRunRequest,
    ): StageAgentResult {
        error("SwarmExecutionStageExecutor ещё не реализован")
    }
}