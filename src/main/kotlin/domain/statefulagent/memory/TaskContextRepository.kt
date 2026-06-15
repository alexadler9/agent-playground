package domain.statefulagent.memory

import domain.statefulagent.model.TaskContext

interface TaskContextRepository {

    suspend fun getTaskContext(): TaskContext

    suspend fun saveTaskContext(taskContext: TaskContext)

    suspend fun clear()
}