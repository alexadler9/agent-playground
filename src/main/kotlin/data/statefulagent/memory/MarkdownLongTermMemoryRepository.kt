package data.statefulagent.memory

import domain.statefulagent.memory.LongTermMemoryRepository
import domain.statefulagent.memory.UserProfileRepository
import domain.statefulagent.model.LongTermMemory
import java.nio.file.Path
import kotlin.io.path.createDirectories
import kotlin.io.path.exists
import kotlin.io.path.readText
import kotlin.io.path.writeText

class MarkdownLongTermMemoryRepository(
    private val memoryDirectory: Path,
    private val userProfileRepository: UserProfileRepository,
) : LongTermMemoryRepository {

    override suspend fun getLongTermMemory(): LongTermMemory {
        ensureFilesExist()

        return LongTermMemory(
            profile = userProfileRepository.getActiveProfileContent(),
            decisions = decisionsFile.readText(),
            knowledge = knowledgeFile.readText(),
        )
    }

    private fun ensureFilesExist() {
        if (!memoryDirectory.exists()) {
            memoryDirectory.createDirectories()
        }

        if (!decisionsFile.exists()) {
            decisionsFile.writeText(
                """
                # Устойчивые решения
            
                Здесь хранятся долгосрочные проектные или архитектурные решения, которые должны сохраняться между задачами.
                """.trimIndent()
            )
        }

        if (!knowledgeFile.exists()) {
            knowledgeFile.writeText(
                """
                # Знания
            
                Здесь хранятся долгосрочные заметки, доменные знания и переиспользуемая информация.
                """.trimIndent()
            )
        }
    }

    private val decisionsFile: Path
        get() = memoryDirectory.resolve("decisions.md")

    private val knowledgeFile: Path
        get() = memoryDirectory.resolve("knowledge.md")
}