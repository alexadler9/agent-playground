package domain.antiprocrastination

data class AntiProcrastinatorRequest(
    val task: String,
    val blocker: String,
    val availableMinutes: Int,
    val energyLevel: String,
    val previousContext: String = "",
)

data class AntiProcrastinatorResult(
    val answer: String,
    val model: String,
    val durationMs: Long,
)