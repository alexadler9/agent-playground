package data.memory

import data.memory.dto.PersistedFactMemoryDto
import domain.memory.FactMemoryRepository
import domain.model.ChatSession
import domain.model.FactMemory
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.Json
import java.nio.file.Path
import kotlin.io.path.createDirectories
import kotlin.io.path.exists
import kotlin.io.path.readText
import kotlin.io.path.writeText

class JsonFactMemoryRepository(
    private val storageFile: Path,
    private val json: Json,
) : FactMemoryRepository {

    private val mutex = Mutex()

    override suspend fun getFacts(session: ChatSession): FactMemory {
        return mutex.withLock {
            readFacts()
        }
    }

    override suspend fun saveFacts(
        session: ChatSession,
        facts: FactMemory,
    ) {
        mutex.withLock {
            writeFacts(facts)
        }
    }

    override suspend fun clear(session: ChatSession) {
        mutex.withLock {
            writeFacts(FactMemory.Empty)
        }
    }

    private fun readFacts(): FactMemory {
        if (!storageFile.exists()) {
            return FactMemory.Empty
        }

        val rawText = storageFile.readText()

        if (rawText.isBlank()) {
            return FactMemory.Empty
        }

        val persistedFacts = json.decodeFromString<PersistedFactMemoryDto>(rawText)

        return persistedFacts.toDomain()
    }

    private fun writeFacts(facts: FactMemory) {
        val parent = storageFile.parent

        if (parent != null && !parent.exists()) {
            parent.createDirectories()
        }

        val persistedFacts = facts.toPersistedDto()
        val rawText = json.encodeToString(persistedFacts)

        storageFile.writeText(rawText)
    }
}