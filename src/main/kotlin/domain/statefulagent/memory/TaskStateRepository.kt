package domain.statefulagent.memory

import domain.statefulagent.model.TaskState

interface TaskStateRepository {

    suspend fun getTaskState(): TaskState

    suspend fun saveTaskState(taskState: TaskState)

    suspend fun clear()
}