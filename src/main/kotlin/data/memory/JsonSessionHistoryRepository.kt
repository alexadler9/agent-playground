package data.memory

import data.memory.dto.PersistedSessionDto
import domain.memory.SessionHistoryRepository
import domain.model.ChatMessage
import domain.model.ChatSession
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.createDirectories
import kotlin.io.path.exists
import kotlin.io.path.readText
import kotlin.io.path.writeText

class JsonSessionHistoryRepository(
    private val storageFile: Path,
    private val json: Json,
) : SessionHistoryRepository {

    private val mutex = Mutex()

    override suspend fun appendMessage(
        session: ChatSession,
        message: ChatMessage,
    ) {
        mutex.withLock {
            val currentMessages = readMessages()
            val updatedMessages = currentMessages + message
            writeMessages(updatedMessages)
        }
    }

    override suspend fun getMessages(session: ChatSession): List<ChatMessage> {
        return mutex.withLock {
            readMessages()
        }
    }

    override suspend fun clear(session: ChatSession) {
        mutex.withLock {
            writeMessages(emptyList())
        }
    }

    private fun readMessages(): List<ChatMessage> {
        if (!storageFile.exists()) {
            return emptyList()
        }

        val rawText = storageFile.readText()

        if (rawText.isBlank()) {
            return emptyList()
        }

        val persistedSession = json.decodeFromString<PersistedSessionDto>(rawText)

        return persistedSession.messages.map { messageDto ->
            messageDto.toDomain()
        }
    }

    private fun writeMessages(messages: List<ChatMessage>) {
        val parent = storageFile.parent

        if (parent != null && !parent.exists()) {
            parent.createDirectories()
        }

        val persistedSession = PersistedSessionDto(
            messages = messages.map { message ->
                message.toPersistedDto()
            },
        )

        val rawText = json.encodeToString(persistedSession)

        storageFile.writeText(rawText)
    }
}