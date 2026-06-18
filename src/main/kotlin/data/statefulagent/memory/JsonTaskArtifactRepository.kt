package data.statefulagent.memory

import domain.statefulagent.memory.TaskArtifactRepository
import domain.statefulagent.model.TaskArtifact
import domain.statefulagent.model.TaskStage
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.nio.file.Path
import kotlin.io.path.createDirectories
import kotlin.io.path.exists
import kotlin.io.path.readText
import kotlin.io.path.writeText

class JsonTaskArtifactRepository(
    private val storageFile: Path,
    private val json: Json,
) : TaskArtifactRepository {

    private val mutex = Mutex()

    override suspend fun getLatestArtifact(stage: TaskStage): TaskArtifact? {
        return mutex.withLock {
            readArtifacts()[stage]
        }
    }

    override suspend fun getArtifacts(): Map<TaskStage, TaskArtifact> {
        return mutex.withLock {
            readArtifacts()
        }
    }

    override suspend fun saveArtifact(artifact: TaskArtifact) {
        mutex.withLock {
            val artifacts = readArtifacts().toMutableMap()
            artifacts[artifact.stage] = artifact
            writeArtifacts(artifacts)
        }
    }

    override suspend fun removeArtifactsFromStage(stage: TaskStage) {
        mutex.withLock {
            val artifacts = readArtifacts().toMutableMap()
            val stagesToRemove = TaskStage.entries
                .filter { existingStage ->
                    existingStage.order >= stage.order
                }

            stagesToRemove.forEach { stageToRemove ->
                artifacts.remove(stageToRemove)
            }

            writeArtifacts(artifacts)
        }
    }

    override suspend fun clear() {
        mutex.withLock {
            writeArtifacts(emptyMap())
        }
    }

    private fun readArtifacts(): Map<TaskStage, TaskArtifact> {
        if (!storageFile.exists()) return emptyMap()

        val rawText = storageFile.readText()
        if (rawText.isBlank()) return emptyMap()

        val dto = json.decodeFromString<PersistedTaskArtifactsDto>(rawText)

        return dto.artifacts
            .mapKeys { entry -> TaskStage.valueOf(entry.key) }
            .mapValues { entry -> entry.value.toDomain() }
    }

    private fun writeArtifacts(artifacts: Map<TaskStage, TaskArtifact>) {
        val parent = storageFile.parent

        if (parent != null && !parent.exists()) {
            parent.createDirectories()
        }

        val dto = PersistedTaskArtifactsDto(
            artifacts = artifacts.mapKeys { entry -> entry.key.name }
                .mapValues { entry -> entry.value.toPersistedDto() },
        )

        storageFile.writeText(json.encodeToString(dto))
    }

    private val TaskStage.order: Int
        get() = when (this) {
            TaskStage.PLANNING -> 0
            TaskStage.EXECUTION -> 1
            TaskStage.VALIDATION -> 2
            TaskStage.DONE -> 3
        }
}

@Serializable
private data class PersistedTaskArtifactsDto(
    val artifacts: Map<String, PersistedTaskArtifactDto> = emptyMap(),
)

@Serializable
private data class PersistedTaskArtifactDto(
    val stage: TaskStage,
    val content: String,
    val createdAtMillis: Long,
)

private fun PersistedTaskArtifactDto.toDomain(): TaskArtifact {
    return TaskArtifact(
        stage = stage,
        content = content,
        createdAtMillis = createdAtMillis,
    )
}

private fun TaskArtifact.toPersistedDto(): PersistedTaskArtifactDto {
    return PersistedTaskArtifactDto(
        stage = stage,
        content = content,
        createdAtMillis = createdAtMillis,
    )
}