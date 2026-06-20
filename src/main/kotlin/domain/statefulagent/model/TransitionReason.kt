package domain.statefulagent.model

/**
 * Причина перехода между stage.
 *
 * Нужна, чтобы отличать обычное предложение LLM
 * от переходов, подтверждённых кодом оркестратора.
 */
enum class TransitionReason {
    /** Обычное предложение stage-agent-а. */
    AGENT_SUGGESTED,

    /** Пользователь явно подтвердил planning artifact. */
    USER_APPROVED_PLAN,

    /** Validation stage принял execution artifact. */
    VALIDATION_ACCEPTED,

    /** Validation stage отклонил результат и вернул задачу в execution. */
    VALIDATION_REJECTED,
}