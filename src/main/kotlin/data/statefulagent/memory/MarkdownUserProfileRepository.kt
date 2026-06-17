package data.statefulagent.memory

import domain.statefulagent.memory.UserProfileRepository
import java.nio.file.Path
import kotlin.io.path.createDirectories
import kotlin.io.path.exists
import kotlin.io.path.listDirectoryEntries
import kotlin.io.path.nameWithoutExtension
import kotlin.io.path.readText
import kotlin.io.path.writeText

class MarkdownUserProfileRepository(
    private val profilesDirectory: Path,
    private val activeProfileFile: Path,
) : UserProfileRepository {

    override suspend fun getActiveProfileName(): String {
        ensureProfileStorageExists()

        val savedProfileName = activeProfileFile
            .readText()
            .trim()

        return if (savedProfileName.isNotBlank() && profileExists(savedProfileName)) {
            savedProfileName
        } else {
            DEFAULT_PROFILE_NAME
        }
    }

    override suspend fun getActiveProfileContent(): String {
        ensureProfileStorageExists()

        val activeProfileName = getActiveProfileName()
        val profileFile = profilesDirectory.resolve("$activeProfileName.md")

        return profileFile.readText()
    }

    override suspend fun listProfiles(): List<String> {
        ensureProfileStorageExists()

        return profilesDirectory
            .listDirectoryEntries("*.md")
            .map { path -> path.nameWithoutExtension }
            .sorted()
    }

    override suspend fun switchProfile(profileName: String) {
        ensureProfileStorageExists()

        val normalizedName = profileName.trim()

        require(normalizedName.isNotBlank()) {
            "Profile name must not be blank"
        }

        require(profileExists(normalizedName)) {
            "Profile does not exist: $normalizedName"
        }

        activeProfileFile.writeText(normalizedName)
    }

    private fun ensureProfileStorageExists() {
        if (!profilesDirectory.exists()) {
            profilesDirectory.createDirectories()
        }

        val defaultProfileFile = profilesDirectory.resolve("$DEFAULT_PROFILE_NAME.md")

        if (!defaultProfileFile.exists()) {
            defaultProfileFile.writeText(createDefaultProfileContent())
        }

        val parent = activeProfileFile.parent
        if (parent != null && !parent.exists()) {
            parent.createDirectories()
        }

        if (!activeProfileFile.exists()) {
            activeProfileFile.writeText(DEFAULT_PROFILE_NAME)
        }

        val activeProfileName = activeProfileFile
            .readText()
            .trim()

        if (activeProfileName.isBlank() || !profileExists(activeProfileName)) {
            activeProfileFile.writeText(DEFAULT_PROFILE_NAME)
        }
    }

    private fun profileExists(profileName: String): Boolean {
        return profilesDirectory
            .resolve("$profileName.md")
            .exists()
    }

    private fun createDefaultProfileContent(): String {
        return """
            # Профиль пользователя: default
            
            ## Роль и контекст
            
            Опишите роль пользователя, уровень подготовки и контекст работы.
            
            ## Стиль ответов
            
            Опишите желаемый тон, подробность и стиль объяснений.
            
            ## Формат ответов
            
            Опишите предпочтительный формат: кратко, пошагово, с кодом, без кода, с примерами.
            
            ## Ограничения
            
            Опишите запреты и обязательные правила: язык, стек, технологии, стиль кода.
            
            ## Доменные предпочтения
            
            Опишите, через какие примеры ассистенту лучше объяснять темы.
        """.trimIndent()
    }

    private companion object {
        const val DEFAULT_PROFILE_NAME = "default"
    }
}