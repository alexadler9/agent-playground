package data.memory

import data.memory.dto.PersistedSummaryStateDto
import domain.memory.SessionSummaryRepository
import domain.model.ChatSession
import domain.model.SummaryState
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.Json
import java.nio.file.Path
import kotlin.io.path.createDirectories
import kotlin.io.path.exists
import kotlin.io.path.readText
import kotlin.io.path.writeText

class JsonSessionSummaryRepository(
    private val storageFile: Path,
    private val json: Json,
) : SessionSummaryRepository {

    private val mutex = Mutex()

    override suspend fun getSummary(session: ChatSession): SummaryState {
        return mutex.withLock {
            readSummary()
        }
    }

    override suspend fun saveSummary(
        session: ChatSession,
        summary: SummaryState,
    ) {
        mutex.withLock {
            writeSummary(summary)
        }
    }

    override suspend fun clear(session: ChatSession) {
        mutex.withLock {
            writeSummary(SummaryState.Empty)
        }
    }

    private fun readSummary(): SummaryState {
        if (!storageFile.exists()) {
            return SummaryState.Empty
        }

        val rawText = storageFile.readText()

        if (rawText.isBlank()) {
            return SummaryState.Empty
        }

        val persistedSummary = json.decodeFromString<PersistedSummaryStateDto>(rawText)

        return persistedSummary.toDomain()
    }

    private fun writeSummary(summary: SummaryState) {
        val parent = storageFile.parent

        if (parent != null && !parent.exists()) {
            parent.createDirectories()
        }

        val persistedSummary = summary.toPersistedDto()
        val rawText = json.encodeToString(persistedSummary)

        storageFile.writeText(rawText)
    }
}