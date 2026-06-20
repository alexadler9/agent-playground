package domain.statefulagent.stage

import domain.statefulagent.model.StageAgentResult
import domain.statefulagent.model.TaskStage

/**
 * Запускает stage-agent для текущего состояния задачи.
 *
 * Отвечает только за выбор stage-agent-а и нормализацию результата.
 * Не сохраняет state, artifacts и историю.
 */
class StageRunner(
    private val stageAgents: List<StageAgent>,
    private val stageAgentResultNormalizer: StageAgentResultNormalizer,
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

        val stageAgent = getStageAgent(request.taskState.stage)

        val stageResult = stageAgent.handle(
            memory = request.memory,
            taskState = request.taskState,
            artifacts = request.artifacts,
            invariants = request.invariants,
            userMessage = request.userMessage,
        )

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