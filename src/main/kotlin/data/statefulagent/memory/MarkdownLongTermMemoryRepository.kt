package data.statefulagent.memory

import domain.statefulagent.memory.LongTermMemoryRepository
import domain.statefulagent.model.LongTermMemory
import java.nio.file.Path
import kotlin.io.path.createDirectories
import kotlin.io.path.exists
import kotlin.io.path.readText
import kotlin.io.path.writeText

class MarkdownLongTermMemoryRepository(
    private val memoryDirectory: Path,
) : LongTermMemoryRepository {

    override suspend fun getLongTermMemory(): LongTermMemory {
        ensureFilesExist()

        return LongTermMemory(
            profile = profileFile.readText(),
            decisions = decisionsFile.readText(),
            knowledge = knowledgeFile.readText(),
        )
    }

    private fun ensureFilesExist() {
        if (!memoryDirectory.exists()) {
            memoryDirectory.createDirectories()
        }

        if (!profileFile.exists()) {
            profileFile.writeText(
                """
            # Профиль пользователя
            
            Здесь хранятся устойчивые сведения о пользователе, его предпочтениях, стиле общения и долгосрочных настройках.
            """.trimIndent()
            )
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

    private val profileFile: Path
        get() = memoryDirectory.resolve("profile.md")

    private val decisionsFile: Path
        get() = memoryDirectory.resolve("decisions.md")

    private val knowledgeFile: Path
        get() = memoryDirectory.resolve("knowledge.md")
}