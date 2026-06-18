package domain.statefulagent.model

enum class ExpectedAction {
    USER_MESSAGE,   // ждем обычное сообщение пользователя
    APPROVE_PLAN,   // ждем подтверждение плана
    AUTO_CONTINUE,  // оркестратор может идти дальше сам
    NONE,           // задача завершена или действие не требуется
}