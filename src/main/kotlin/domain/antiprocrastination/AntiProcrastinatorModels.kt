package domain.antiprocrastination

data class AntiProcrastinatorRequest(
    val task: String,
    val blocker: String,
    val availableMinutes: Int,
    val energyLevel: String,
    val previousContext: String = "",
)

data class AntiProcrastinatorLlmSettings(
    val mode: String,
    val temperature: Double,
    val maxTokens: Int,
    val promptName: String,
)

data class AntiProcrastinatorRunResult(
    val mode: String,
    val model: String,
    val settings: AntiProcrastinatorLlmSettings,
    val prompt: String,
    val answer: String,
    val durationMs: Long,
    val promptTokens: Int?,
    val outputTokens: Int?,
)

data class AntiProcrastinatorComparisonResult(
    val request: AntiProcrastinatorRequest,
    val baseline: AntiProcrastinatorRunResult,
    val optimized: AntiProcrastinatorRunResult,
)

data class AntiProcrastinatorBatchComparisonResult(
    val model: String,
    val items: List<AntiProcrastinatorComparisonResult>,
)