package data.statefulagent.memory

import domain.statefulagent.memory.TaskStateRepository
import domain.statefulagent.model.ExpectedAction
import domain.statefulagent.model.TaskStage
import domain.statefulagent.model.TaskState
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.nio.file.Path
import kotlin.io.path.createDirectories
import kotlin.io.path.exists
import kotlin.io.path.readText
import kotlin.io.path.writeText

class JsonTaskStateRepository(
    private val storageFile: Path,
    private val json: Json,
) : TaskStateRepository {

    private val mutex = Mutex()

    override suspend fun getTaskState(): TaskState {
        return mutex.withLock {
            readTaskState()
        }
    }

    override suspend fun saveTaskState(taskState: TaskState) {
        mutex.withLock {
            writeTaskState(taskState)
        }
    }

    override suspend fun clear() {
        mutex.withLock {
            writeTaskState(TaskState.Initial)
        }
    }

    private fun readTaskState(): TaskState {
        if (!storageFile.exists()) return TaskState.Initial

        val rawText = storageFile.readText()
        if (rawText.isBlank()) return TaskState.Initial

        val dto = json.decodeFromString<PersistedTaskStateDto>(rawText)

        return dto.toDomain()
    }

    private fun writeTaskState(taskState: TaskState) {
        val parent = storageFile.parent

        if (parent != null && !parent.exists()) {
            parent.createDirectories()
        }

        val dto = taskState.toPersistedDto()
        val rawText = json.encodeToString(dto)

        storageFile.writeText(rawText)
    }
}

@Serializable
private data class PersistedTaskStateDto(
    val stage: TaskStage = TaskStage.PLANNING,
    val currentStep: String = "Сбор и согласование плана",
    val expectedAction: ExpectedAction = ExpectedAction.USER_MESSAGE,
)

private fun PersistedTaskStateDto.toDomain(): TaskState {
    return TaskState(
        stage = stage,
        currentStep = currentStep,
        expectedAction = expectedAction,
    )
}

private fun TaskState.toPersistedDto(): PersistedTaskStateDto {
    return PersistedTaskStateDto(
        stage = stage,
        currentStep = currentStep,
        expectedAction = expectedAction,
    )
}