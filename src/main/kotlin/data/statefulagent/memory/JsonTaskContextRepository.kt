package data.statefulagent.memory

import domain.statefulagent.memory.TaskContextRepository
import domain.statefulagent.model.TaskContext
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.nio.file.Path
import kotlin.io.path.createDirectories
import kotlin.io.path.exists
import kotlin.io.path.readText
import kotlin.io.path.writeText

class JsonTaskContextRepository(
    private val storageFile: Path,
    private val json: Json,
) : TaskContextRepository {

    private val mutex = Mutex()

    override suspend fun getTaskContext(): TaskContext {
        return mutex.withLock {
            readTaskContext()
        }
    }

    override suspend fun saveTaskContext(taskContext: TaskContext) {
        mutex.withLock {
            writeTaskContext(taskContext)
        }
    }

    override suspend fun clear() {
        mutex.withLock {
            writeTaskContext(TaskContext.Empty)
        }
    }

    private fun readTaskContext(): TaskContext {
        if (!storageFile.exists()) return TaskContext.Empty

        val rawText = storageFile.readText()
        if (rawText.isBlank()) return TaskContext.Empty

        val dto = json.decodeFromString<PersistedTaskContextDto>(rawText)

        return dto.toDomain()
    }

    private fun writeTaskContext(taskContext: TaskContext) {
        val parent = storageFile.parent
        if (parent != null && !parent.exists()) {
            parent.createDirectories()
        }

        val dto = taskContext.toPersistedDto()
        val rawText = json.encodeToString(dto)

        storageFile.writeText(rawText)
    }
}

@Serializable
private data class PersistedTaskContextDto(
    val taskName: String? = null,
    val goal: String? = null,
    val currentStep: String? = null,
    val completedItems: List<String> = emptyList(),
    val pendingItems: List<String> = emptyList(),
    val decisions: List<String> = emptyList(),
    val constraints: List<String> = emptyList(),
)

private fun PersistedTaskContextDto.toDomain(): TaskContext {
    return TaskContext(
        taskName = taskName,
        goal = goal,
        currentStep = currentStep,
        completedItems = completedItems,
        pendingItems = pendingItems,
        decisions = decisions,
        constraints = constraints,
    )
}

private fun TaskContext.toPersistedDto(): PersistedTaskContextDto {
    return PersistedTaskContextDto(
        taskName = taskName,
        goal = goal,
        currentStep = currentStep,
        completedItems = completedItems,
        pendingItems = pendingItems,
        decisions = decisions,
        constraints = constraints,
    )
}