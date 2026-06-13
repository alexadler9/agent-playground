package domain.context

enum class ContextStrategyType {
    FULL_HISTORY,       // Отправляется вся история, базовый режим для сравнения
    SLIDING_WINDOW,     // Отправляются только последние N сообщений
    STICKY_FACTS,       // Отправляются facts + последние N сообщений
}