package domain.statefulagent.model

data class TaskContext(
    val taskName: String? = null,
    val goal: String? = null,
    val currentStep: String? = null,
    val completedItems: List<String> = emptyList(),
    val pendingItems: List<String> = emptyList(),
    val decisions: List<String> = emptyList(),
    val constraints: List<String> = emptyList(),
) {
    val isEmpty: Boolean
        get() = taskName == null &&
                goal == null &&
                currentStep == null &&
                completedItems.isEmpty() &&
                pendingItems.isEmpty() &&
                decisions.isEmpty() &&
                constraints.isEmpty()

    companion object {
        val Empty = TaskContext()
    }
}