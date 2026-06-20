package domain.statefulagent.stage

import domain.statefulagent.model.ExpectedAction
import domain.statefulagent.model.StageAgentResult
import domain.statefulagent.model.TaskStage
import domain.statefulagent.model.TaskState
import domain.statefulagent.model.TransitionReason

/**
 * Приводит результат stage-agent-а к правилам оркестратора.
 *
 * LLM-agent может предложить следующий stage, но служебные причины перехода
 * выставляются кодом, чтобы lifecycle задачи не зависел от текста LLM.
 */
class StageAgentResultNormalizer {

    /**
     * Возвращает служебный результат без вызова LLM-agent-а,
     * если текущее сообщение должно обрабатываться оркестратором.
     */
    fun buildSystemResultOrNull(
        taskState: TaskState,
        userMessage: String,
    ): StageAgentResult? {
        val normalizedMessage = userMessage.trim().lowercase()

        if (
            taskState.stage == TaskStage.PLANNING &&
            taskState.expectedAction != ExpectedAction.APPROVE_PLAN &&
            normalizedMessage.isApprovalMessage()
        ) {
            return StageAgentResult(
                answer = "Сейчас нечего подтверждать: план ещё не был предложен. Пожалуйста, ответьте на уточняющие вопросы или опишите задачу подробнее.",
                suggestedNextStage = null,
                nextCurrentStep = taskState.currentStep,
                nextExpectedAction = taskState.expectedAction,
                shouldSaveArtifact = false,
            )
        }

        if (
            taskState.stage == TaskStage.PLANNING &&
            taskState.expectedAction == ExpectedAction.APPROVE_PLAN &&
            normalizedMessage.isApprovalMessage()
        ) {
            return StageAgentResult(
                answer = "План утверждён. Перехожу к этапу execution",
                suggestedNextStage = TaskStage.EXECUTION,
                nextCurrentStep = "Выполнение утверждённого плана",
                nextExpectedAction = ExpectedAction.AUTO_CONTINUE,
                shouldSaveArtifact = false,
                transitionReason = TransitionReason.USER_APPROVED_PLAN,
            )
        }

        if (
            taskState.stage == TaskStage.DONE &&
            normalizedMessage.contains("продолж")
        ) {
            return StageAgentResult(
                answer = "Задача уже находится в состоянии DONE. Можно начать новую задачу через сброс состояния",
                suggestedNextStage = null,
                nextCurrentStep = taskState.currentStep,
                nextExpectedAction = ExpectedAction.NONE,
                shouldSaveArtifact = false,
            )
        }

        return null
    }

    /**
     * Дополняет результат stage-agent-а служебными данными,
     * которые не должен выставлять сам LLM.
     */
    fun normalize(
        taskState: TaskState,
        stageResult: StageAgentResult,
    ): StageAgentResult {
        return when (taskState.stage) {
            TaskStage.VALIDATION -> stageResult.withValidationTransitionReason()
            else -> stageResult
        }
    }

    private fun StageAgentResult.withValidationTransitionReason(): StageAgentResult {
        return when (suggestedNextStage) {
            TaskStage.DONE -> copy(
                transitionReason = TransitionReason.VALIDATION_ACCEPTED,
            )

            TaskStage.EXECUTION -> copy(
                transitionReason = TransitionReason.VALIDATION_REJECTED,
            )

            else -> this
        }
    }

    private fun String.isApprovalMessage(): Boolean {
        val normalized = trim().lowercase()

        val negativeMarkers = listOf(
            "не подтверждаю",
            "не утверждаю",
            "не соглас",
            "пока не",
            "нет",
            "надо поправить",
            "нужно поправить",
            "измени",
            "поменяй",
            "добавь",
            "убери",
        )

        if (negativeMarkers.any { marker -> normalized.contains(marker) }) {
            return false
        }

        return normalized == "да" ||
                normalized == "ок" ||
                normalized == "окей" ||
                normalized == "ок, подтверждаю" ||
                normalized == "да, подтверждаю" ||
                normalized.contains("утверждаю") ||
                normalized.contains("подтверждаю") ||
                normalized.contains("план ок") ||
                normalized.contains("согласен") ||
                normalized.contains("согласна")
    }
}