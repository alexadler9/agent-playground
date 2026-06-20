package domain.statefulagent.model

/**
 * Результат работы одного stage-agent-а.
 *
 * Agent возвращает ответ пользователю и предлагает,
 * как должен измениться lifecycle задачи.
 */
data class StageAgentResult(
    /** Текст, который попадёт в ответ пользователя и, при необходимости, в artifact. */
    val answer: String,

    /** Предлагаемый следующий stage. null означает остаться на текущем stage. */
    val suggestedNextStage: TaskStage?,

    /** Описание следующего шага. */
    val nextCurrentStep: String,

    /** Какое действие ожидается после этого результата. */
    val nextExpectedAction: ExpectedAction,

    /** Служебная причина перехода. Используется validator-ом lifecycle-а. */
    val transitionReason: TransitionReason = TransitionReason.AGENT_SUGGESTED,

    /** Нужно ли сохранить answer как artifact текущего stage. */
    val shouldSaveArtifact: Boolean = true,
)