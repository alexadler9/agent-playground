package domain.model

data class EstimatedTokenStats(
    val currentRequestTokens: Int,
    val storedHistoryTokens: Int,       // вся сохраненная история без system prompt
    val fullContextTokens: Int,         // сколько было бы при FullHistoryContextBuilder
    val actualContextTokens: Int,       // сколько реально отправили сейчас
    val savedContextTokens: Int,        // примерная экономия
    val contextMessageCount: Int,
    val summarizedMessageCount: Int,    // сколько сообщений покрыто summary
)