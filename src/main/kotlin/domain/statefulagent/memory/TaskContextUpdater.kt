package domain.statefulagent.memory

import domain.statefulagent.model.InvariantSet
import domain.statefulagent.model.TaskContext

interface TaskContextUpdater {

    suspend fun updateTaskContext(
        currentContext: TaskContext,
        userMessage: String,
        invariants: InvariantSet,
    ): TaskContext
}