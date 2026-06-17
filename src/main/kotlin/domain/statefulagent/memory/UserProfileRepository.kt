package domain.statefulagent.memory

interface UserProfileRepository {

    suspend fun getActiveProfileName(): String

    suspend fun getActiveProfileContent(): String

    suspend fun listProfiles(): List<String>

    suspend fun switchProfile(profileName: String)
}