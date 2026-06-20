package domain.statefulagent.stage

import domain.statefulagent.memory.TaskArtifactRepository
import domain.statefulagent.model.StageAgentResult
import domain.statefulagent.model.TaskArtifact
import domain.statefulagent.model.TaskStage

/**
 * Сохраняет результат stage-agent-а как artifact текущего stage.
 *
 * Не решает, можно ли переходить в следующий stage.
 * Только превращает StageAgentResult.answer в TaskArtifact, если это разрешено.
 */
class StageArtifactSaver(
    private val taskArtifactRepository: TaskArtifactRepository,
) {

    /**
     * Сохраняет answer как artifact текущего stage, если stageResult этого требует.
     */
    suspend fun saveIfNeeded(
        currentStage: TaskStage,
        stageResult: StageAgentResult,
    ) {
        if (!stageResult.shouldSaveArtifact) {
            return
        }

        taskArtifactRepository.saveArtifact(
            TaskArtifact(
                stage = currentStage,
                content = stageResult.answer,
                createdAtMillis = System.currentTimeMillis(),
            ),
        )
    }
}