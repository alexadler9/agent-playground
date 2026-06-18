package domain.statefulagent.model

data class TaskState(
    val stage: TaskStage = TaskStage.PLANNING,
    val currentStep: String = "Сбор и согласование плана",
    val expectedAction: ExpectedAction = ExpectedAction.USER_MESSAGE,
) {
    companion object {
        val Initial = TaskState()
    }
}