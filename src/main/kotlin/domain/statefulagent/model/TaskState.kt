package domain.statefulagent.model

/**
 * Текущее lifecycle-состояние задачи.
 *
 * Хранит только минимальное состояние оркестратора:
 * stage, текущий шаг и ожидаемое действие.
 */
data class TaskState(
    /** Текущий этап жизненного цикла задачи. */
    val stage: TaskStage = TaskStage.PLANNING,

    /** Человекочитаемое описание текущего шага. */
    val currentStep: String = "Сбор и согласование плана",

    /** Что должно произойти дальше: сообщение пользователя, approval или auto-continue. */
    val expectedAction: ExpectedAction = ExpectedAction.USER_MESSAGE,
) {
    companion object {
        val Initial = TaskState()
    }
}