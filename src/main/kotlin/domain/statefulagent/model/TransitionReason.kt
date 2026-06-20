package domain.statefulagent.model

enum class TransitionReason {
    AGENT_SUGGESTED,
    USER_APPROVED_PLAN,
    VALIDATION_ACCEPTED,
    VALIDATION_REJECTED,
}