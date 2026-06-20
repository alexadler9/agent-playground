package domain.statefulagent.stage.execution

import domain.statefulagent.model.StageAgentResult
import domain.statefulagent.stage.StageAgent
import domain.statefulagent.stage.StageRunRequest

/**
 * Обычная реализация EXECUTION через одного stage-agent-а.
 *
 * Это текущий режим: один ExecutionStageAgent получает контекст
 * и возвращает один StageAgentResult.
 */
class SingleExecutionStageExecutor(
    private val executionStageAgent: StageAgent,
) : ExecutionStageExecutor {

    override suspend fun execute(
        request: StageRunRequest,
    ): StageAgentResult {
        return executionStageAgent.handle(
            memory = request.memory,
            taskState = request.taskState,
            artifacts = request.artifacts,
            invariants = request.invariants,
            userMessage = request.userMessage,
        )
    }
}