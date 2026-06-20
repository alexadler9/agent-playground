package domain.statefulagent.stage

import domain.statefulagent.model.StageAgentResult
import domain.statefulagent.model.TaskStage
import domain.statefulagent.stage.execution.ExecutionStageExecutor

/**
 * Запускает stage-agent для текущего состояния задачи.
 *
 * Отвечает только за выбор stage-agent-а и нормализацию результата.
 * Не сохраняет state, artifacts и историю.
 */
class StageRunner(
    private val stageAgents: List<StageAgent>,
    private val stageAgentResultNormalizer: StageAgentResultNormalizer,
    private val executionStageExecutor: ExecutionStageExecutor,
) {

    suspend fun run(
        request: StageRunRequest,
    ): StageAgentResult {
        val systemResult = stageAgentResultNormalizer.buildSystemResultOrNull(
            taskState = request.taskState,
            userMessage = request.userMessage,
        )

        if (systemResult != null) {
            return systemResult
        }

        val stageResult = if (request.taskState.stage == TaskStage.EXECUTION) {
            executionStageExecutor.execute(request)
        } else {
            val stageAgent = getStageAgent(request.taskState.stage)

            stageAgent.handle(
                memory = request.memory,
                taskState = request.taskState,
                artifacts = request.artifacts,
                invariants = request.invariants,
                userMessage = request.userMessage,
            )
        }

        return stageAgentResultNormalizer.normalize(
            taskState = request.taskState,
            stageResult = stageResult,
        )
    }

    private fun getStageAgent(stage: TaskStage): StageAgent {
        return stageAgents.firstOrNull { agent -> agent.stage == stage }
            ?: error("StageAgent для этапа $stage не найден")
    }
}