package domain.model

data class EstimatedTokenStats(
    val currentRequestTokens: Int,
    val storedHistoryTokens: Int,
    val contextTokens: Int,
    val contextMessageCount: Int,
)