package domain.statefulagent.model

/**
 * Этап жизненного цикла задачи.
 *
 * Оркестратор двигает задачу только по разрешённым переходам:
 * PLANNING -> EXECUTION -> VALIDATION -> DONE.
 */
enum class TaskStage {
    /** Сбор требований и согласование плана. */
    PLANNING,

    /** Выполнение утверждённого плана. */
    EXECUTION,

    /** Проверка результата выполнения. */
    VALIDATION,

    /** Финальное состояние: задача завершена. */
    DONE,
}